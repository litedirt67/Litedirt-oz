package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class HostDeviceTelemetry(
    val androidVersion: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val localIp: String,
    val suAvailable: Boolean,
    val suPath: String?,
    val supportsAndroid10To15Plus: Boolean
)

data class ShellOutput(
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

object WirelessRootBridge {

    fun getHostTelemetry(context: Context): HostDeviceTelemetry {
        val suLocations = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )
        val foundSu = suLocations.firstOrNull { File(it).exists() }
        return HostDeviceTelemetry(
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            localIp = RealtimeShareServer.resolveLocalIpv4(context),
            suAvailable = foundSu != null,
            suPath = foundSu,
            supportsAndroid10To15Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        )
    }

    suspend fun testTcpEndpoint(host: String, port: Int): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim()
            if (cleanHost.isBlank() || port !in 1..65535) {
                return@withContext false to "Invalid host or port"
            }
            val start = System.currentTimeMillis()
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(cleanHost, port), 1500)
                }
                val rtt = System.currentTimeMillis() - start
                true to "TCP Reachable (${rtt}ms)"
            } catch (e: Exception) {
                false to (e.message?.take(48) ?: "Connection refused / unreachable")
            }
        }

    suspend fun runShellCommand(command: String, useRootIfAvailable: Boolean): ShellOutput =
        withContext(Dispatchers.IO) {
            val trimmed = command.trim()
            if (trimmed.isBlank()) {
                return@withContext ShellOutput(command, "", 0)
            }
            try {
                val shellBin = if (useRootIfAvailable && File("/system/bin/su").exists()) {
                    listOf("su", "-c", trimmed)
                } else {
                    listOf("/system/bin/sh", "-c", trimmed)
                }
                val proc = ProcessBuilder(shellBin)
                    .redirectErrorStream(true)
                    .start()
                val finished = proc.waitFor(4, TimeUnit.SECONDS)
                val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
                if (!finished) {
                    proc.destroyForcibly()
                    ShellOutput(trimmed, "$out\n[Timed out after 4s]".trim(), -1)
                } else {
                    ShellOutput(trimmed, out.ifBlank { "(no output)" }, proc.exitValue())
                }
            } catch (e: Exception) {
                ShellOutput(trimmed, "Error: ${e.message}", -1)
            }
        }

    fun openDeveloperSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
