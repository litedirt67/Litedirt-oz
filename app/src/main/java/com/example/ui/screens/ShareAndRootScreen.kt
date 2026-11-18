package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.db.WirelessProfileEntity
import com.example.service.HostDeviceTelemetry
import com.example.service.ShareServerState
import com.example.service.ShellOutput
import com.example.ui.ShareStreamMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareAndRootScreen(
    shareState: ShareServerState,
    shareMode: ShareStreamMode,
    jpegQuality: Int,
    hostTelemetry: HostDeviceTelemetry,
    wirelessProfiles: List<WirelessProfileEntity>,
    shellHistory: List<ShellOutput>,
    useRootShell: Boolean,
    onSetShareMode: (ShareStreamMode) -> Unit,
    onUpdateJpegQuality: (Int) -> Unit,
    onStartVmViewportStream: () -> Unit,
    onStartMediaProjection: (Int, android.content.Intent) -> Unit,
    onStopSharing: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    onAddWirelessProfile: (String, String, Int, Int?, Boolean) -> Unit,
    onTestWirelessProfile: (WirelessProfileEntity) -> Unit,
    onDeleteWirelessProfile: (Long) -> Unit,
    onToggleUseRootShell: (Boolean) -> Unit,
    onRunShellCommand: (String) -> Unit,
    onClearShell: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddNodeDialog by remember { mutableStateOf(false) }
    var nodeLabel by remember { mutableStateOf("Wireless Root Node") }
    var nodeIp by remember { mutableStateOf(hostTelemetry.localIp) }
    var nodePort by remember { mutableStateOf("5555") }
    var nodePairingPort by remember { mutableStateOf("") }
    var shellInput by remember { mutableStateOf("") }
    var copiedNotice by remember { mutableStateOf(false) }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            onStartMediaProjection(result.resultCode, data)
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    val previewBitmap = remember(shareState.latestFrameBytes) {
        shareState.latestFrameBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Realtime Screen Share Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(
                    1.dp,
                    if (shareState.isRunning) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Realtime Screen Share", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Live LAN MJPEG + Web viewer over Wi-Fi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (shareState.isRunning) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "LIVE ${shareState.fps} FPS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = shareMode == ShareStreamMode.VM_VIEWPORT,
                            onClick = { onSetShareMode(ShareStreamMode.VM_VIEWPORT) },
                            label = { Text("VM Display Stream") }
                        )
                        FilterChip(
                            selected = shareMode == ShareStreamMode.DEVICE_MEDIA_PROJECTION,
                            onClick = { onSetShareMode(ShareStreamMode.DEVICE_MEDIA_PROJECTION) },
                            label = { Text("Full Device Screen") }
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Stream Quality", style = MaterialTheme.typography.labelLarge)
                            Text("$jpegQuality%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = jpegQuality.toFloat(),
                            onValueChange = { onUpdateJpegQuality(it.roundToInt()) },
                            valueRange = 35f..90f
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!shareState.isRunning) {
                            Button(
                                onClick = {
                                    if (shareMode == ShareStreamMode.VM_VIEWPORT) {
                                        onStartVmViewportStream()
                                    } else {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                            mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_realtime_share_button")
                            ) {
                                Icon(Icons.Default.Cast, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Start Realtime Share")
                            }
                        } else {
                            Button(
                                onClick = onStopSharing,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("stop_realtime_share_button")
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop Realtime Share")
                            }
                        }
                    }

                    if (shareState.isRunning) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Open on any browser (same Wi-Fi):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(shareState.streamUrl, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text("${shareState.clientCount} connected viewer(s) • ${shareState.framesSent} frames", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("DroidVM Stream URL", shareState.streamUrl))
                                        copiedNotice = true
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy Stream URL")
                                }
                            }
                        }
                        if (copiedNotice) {
                            Text("URL copied to clipboard!", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }

                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Live stream frame preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black)
                            )
                        }
                    }

                    shareState.errorMessage?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Wireless Root & Host Diagnostics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Wireless Root & ADB Bridge", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Android ${hostTelemetry.androidVersion} (API ${hostTelemetry.sdkInt}) • ${hostTelemetry.localIp}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onRefreshTelemetry, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Default.Refresh, "Refresh telemetry")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (hostTelemetry.suAvailable) "su Binary: ${hostTelemetry.suPath}"
                                    else "su: Not Detected (Wireless ADB Mode)"
                                )
                            }
                        )
                        AssistChip(
                            onClick = {},
                            label = {
                                Text("ABI: ${hostTelemetry.supportedAbis.firstOrNull() ?: "arm64-v8a"}")
                            }
                        )
                        if (hostTelemetry.supportsAndroid10To15Plus) {
                            AssistChip(
                                onClick = {},
                                label = { Text("VM Target: Android 10 (Q)") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                nodeIp = hostTelemetry.localIp
                                showAddNodeDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("add_wireless_node_button")
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Wireless Node")
                        }
                        OutlinedButton(
                            onClick = onOpenDeveloperSettings,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.DeveloperMode, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Wireless Debug")
                        }
                    }
                }
            }
        }

        // Saved Wireless Endpoints
        items(wirelessProfiles, key = { it.id }) { profile ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${profile.hostIp}:${profile.port}" + (profile.pairingPort?.let { " (pair:$it)" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Status: ${profile.lastStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { onTestWirelessProfile(profile) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.NetworkPing, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ping")
                    }
                    IconButton(
                        onClick = { onDeleteWirelessProfile(profile.id) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.DeleteOutline, "Delete node", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Interactive Wireless Root / Local Shell Console
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF060911)),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Interactive Shell Console", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("su", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = useRootShell,
                                onCheckedChange = onToggleUseRootShell
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "uname -a",
                            "id",
                            "getprop ro.build.version.release",
                            "getprop ro.product.cpu.abi",
                            "ls -la"
                        ).forEach { quickCmd ->
                            AssistChip(
                                onClick = { onRunShellCommand(quickCmd) },
                                label = { Text(quickCmd, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF0B111E),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (shellHistory.isEmpty()) {
                                Text(
                                    text = "Ready. Tap a command chip or type a shell command below.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                            } else {
                                shellHistory.takeLast(8).forEach { entry ->
                                    Column {
                                        Text(
                                            text = "$ ${entry.command}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = entry.output,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE2E8F0)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = shellInput,
                            onValueChange = { shellInput = it },
                            placeholder = { Text("Enter shell command...") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val cmd = shellInput.trim()
                                if (cmd.isNotEmpty()) {
                                    onRunShellCommand(cmd)
                                    shellInput = ""
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Run command")
                        }
                    }

                    TextButton(onClick = onClearShell) {
                        Text("Clear Console")
                    }
                }
            }
        }
    }

    if (showAddNodeDialog) {
        AlertDialog(
            onDismissRequest = { showAddNodeDialog = false },
            title = { Text("Add Wireless Root / ADB Node") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nodeLabel,
                        onValueChange = { nodeLabel = it },
                        label = { Text("Label") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = nodeIp,
                        onValueChange = { nodeIp = it },
                        label = { Text("Host IP") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = nodePort,
                        onValueChange = { nodePort = it.filter { ch -> ch.isDigit() } },
                        label = { Text("ADB / Root TCP Port") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = nodePairingPort,
                        onValueChange = { nodePairingPort = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Pairing Port (Android 11+, optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = nodePort.toIntOrNull() ?: 5555
                        val pp = nodePairingPort.toIntOrNull()
                        onAddWirelessProfile(nodeLabel, nodeIp, p, pp, true)
                        showAddNodeDialog = false
                    }
                ) {
                    Text("Save Node")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
