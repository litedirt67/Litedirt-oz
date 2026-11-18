package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class ShareServerState(
    val isRunning: Boolean = false,
    val modeLabel: String = "VM Viewport",
    val streamUrl: String = "",
    val port: Int = 8765,
    val clientCount: Int = 0,
    val fps: Int = 0,
    val framesSent: Long = 0L,
    val latestFrameBytes: ByteArray? = null,
    val errorMessage: String? = null
)

object RealtimeShareServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val latestJpeg = AtomicReference<ByteArray?>(null)
    private val activeClients = AtomicInteger(0)
    private var frameCounter = 0L
    private var fpsWindowCount = 0
    private var fpsWindowStart = System.currentTimeMillis()

    private val _state = MutableStateFlow(ShareServerState())
    val state: StateFlow<ShareServerState> = _state.asStateFlow()

    fun start(context: Context, modeLabel: String, preferredPort: Int = 8765) {
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(modeLabel = modeLabel)
            return
        }

        acceptJob?.cancel()
        try {
            val socket = ServerSocket(preferredPort).apply { reuseAddress = true }
            serverSocket = socket
            val ip = resolveLocalIpv4(context)
            val url = "http://$ip:${socket.localPort}"
            _state.value = ShareServerState(
                isRunning = true,
                modeLabel = modeLabel,
                streamUrl = url,
                port = socket.localPort,
                clientCount = 0,
                fps = 0,
                framesSent = 0L
            )

            acceptJob = scope.launch {
                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch { handleClient(client) }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isRunning = false,
                errorMessage = "Failed to bind share port $preferredPort: ${e.message}"
            )
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        activeClients.set(0)
        _state.value = ShareServerState()
    }

    fun updateFrame(jpegBytes: ByteArray) {
        latestJpeg.set(jpegBytes)
        frameCounter++
        fpsWindowCount++
        val now = System.currentTimeMillis()
        val elapsed = now - fpsWindowStart
        val currentFps = if (elapsed >= 1000L) {
            val computed = ((fpsWindowCount * 1000L) / elapsed).toInt()
            fpsWindowCount = 0
            fpsWindowStart = now
            computed
        } else {
            _state.value.fps
        }

        if (_state.value.isRunning) {
            _state.value = _state.value.copy(
                fps = currentFps,
                framesSent = frameCounter,
                latestFrameBytes = jpegBytes
            )
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: ""
            val path = requestLine.split(" ").getOrNull(1) ?: "/"

            // Drain headers
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            val out = client.getOutputStream()
            when {
                path.startsWith("/stream.mjpg") -> streamMjpeg(out, client)
                path.startsWith("/snapshot.jpg") -> sendSnapshot(out)
                else -> sendViewerHtml(out)
            }
        } catch (_: Exception) {
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun sendViewerHtml(out: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
              <title>LiteDirt Realtime Stream</title>
              <style>
                body{margin:0;background:#090d16;color:#f1f5f9;font-family:system-ui,-apple-system,sans-serif;display:flex;flex-direction:column;align-items:center;min-height:100vh;padding:16px;box-sizing:border-box}
                .bar{display:flex;align-items:center;gap:10px;margin-bottom:14px;background:#111827;border:1px solid #2b384f;padding:10px 16px;border-radius:999px}
                .dot{width:10px;height:10px;border-radius:50%;background:#10b981;box-shadow:0 0 10px #10b981}
                img{max-width:100%;max-height:84vh;border-radius:18px;border:1px solid #2b384f;box-shadow:0 18px 50px rgba(0,0,0,0.6)}
              </style>
            </head>
            <body>
              <div class="bar"><span class="dot"></span><strong>LiteDirt Realtime Screen Share</strong></div>
              <img src="/stream.mjpg" alt="LiteDirt Live Feed"/>
            </body>
            </html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val headers = (
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${html.size}\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII)
        out.write(headers)
        out.write(html)
        out.flush()
    }

    private fun sendSnapshot(out: OutputStream) {
        val frame = latestJpeg.get()
        if (frame == null) {
            val body = "No frame yet".toByteArray()
            out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
            out.write(body)
            out.flush()
            return
        }
        val headers = (
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${frame.size}\r\n" +
                "Cache-Control: no-cache\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII)
        out.write(headers)
        out.write(frame)
        out.flush()
    }

    private fun streamMjpeg(out: OutputStream, client: Socket) {
        val count = activeClients.incrementAndGet()
        _state.value = _state.value.copy(clientCount = count)
        try {
            val header = (
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII)
            out.write(header)
            out.flush()

            var lastSentRef: ByteArray? = null
            while (!client.isClosed && _state.value.isRunning) {
                val frame = latestJpeg.get()
                if (frame != null && frame !== lastSentRef) {
                    val partHeader = (
                        "--frame\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII)
                    out.write(partHeader)
                    out.write(frame)
                    out.write("\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    lastSentRef = frame
                }
                Thread.sleep(66L) // ~15 fps cap for smooth Wi-Fi streaming
            }
        } catch (_: Exception) {
        } finally {
            val remaining = (activeClients.decrementAndGet()).coerceAtLeast(0)
            _state.value = _state.value.copy(clientCount = remaining)
        }
    }

    fun resolveLocalIpv4(context: Context): String {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val props: LinkProperties? = cm?.getLinkProperties(network)
            props?.linkAddresses?.forEach { linkAddr ->
                val addr = linkAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (_: Exception) {
        }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }
}
