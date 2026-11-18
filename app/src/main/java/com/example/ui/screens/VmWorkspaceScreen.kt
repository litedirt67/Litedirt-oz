package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.db.VaultItemEntity
import com.example.data.db.VmImageEntity
import com.example.data.repository.ApkMetadata
import com.example.ui.RunningVmSession
import com.example.ui.VmAppView
import java.io.File

@Composable
fun VmWorkspaceScreen(
    vmImages: List<VmImageEntity>,
    runningSessions: List<RunningVmSession>,
    activeSessionId: String?,
    splitViewEnabled: Boolean,
    isSharingLive: Boolean,
    apks: List<VaultItemEntity>,
    allVaultItems: List<VaultItemEntity>,
    apkMetaCache: Map<Long, ApkMetadata>,
    onBootVm: (VmImageEntity) -> Unit,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onToggleSplitView: () -> Unit,
    onSetSessionView: (String, VmAppView, VaultItemEntity?) -> Unit,
    onRebootSession: (String) -> Unit,
    onQuickImportDocuments: (List<android.net.Uri>) -> Unit,
    onQuickToggleShare: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenVmFiles: () -> Unit,
    onDownloadFromBrowser: (String, String?, String?) -> Unit,
    onCreateFolderInParent: (String, Long?) -> Unit,
    onImportDocumentsToFolder: (List<android.net.Uri>, Long?) -> Unit,
    onRenameVaultItem: (VaultItemEntity, String) -> Unit,
    onMoveVaultItem: (VaultItemEntity, Long?) -> Unit,
    onDeleteVaultItem: (VaultItemEntity) -> Unit,
    onBuildShareIntent: (VaultItemEntity) -> android.content.Intent?,
    onDeleteVmImage: (VmImageEntity) -> Unit,
    onOpenVaultTab: () -> Unit,
    onOpenBuilderTab: () -> Unit,
    onOpenShareTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSession = runningSessions.firstOrNull { it.sessionId == activeSessionId }
        ?: runningSessions.firstOrNull()

    val quickImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onQuickImportDocuments(uris)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vm_banner),
                    contentDescription = "DroidVM engine banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xE6090D16),
                                    Color(0xAA090D16),
                                    Color(0x44090D16)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "ANDROID 10 (Q) LITE ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "${runningSessions.size} Active Task${if (runningSessions.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "LiteDirt Workspace",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                text = "Android 10 Lite VM • Multitask & APK Runner",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenBuilderTab,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("quick_new_image_button")
                        ) {
                            Text("+ Build Image")
                        }
                    }
                }
            }
        }

        // Quick Button Dock (1-Tap Actions)
        item {
            QuickButtonDock(
                hasActiveSession = activeSession != null,
                splitViewEnabled = splitViewEnabled,
                isSharingLive = isSharingLive,
                onOpenBrowser = onOpenBrowser,
                onOpenVmFiles = onOpenVmFiles,
                onImportApk = { quickImportLauncher.launch(arrayOf("*/*")) },
                onOpenFolders = onOpenVaultTab,
                onBuildImage = onOpenBuilderTab,
                onToggleShare = onQuickToggleShare,
                onToggleSplit = onToggleSplitView,
                onReboot = { activeSession?.let { onRebootSession(it.sessionId) } },
                onOpenRoot = onOpenShareTab
            )
        }

        // Multitask Taskbar & Split Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Multitask Switcher",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        FilledTonalButton(
                            onClick = onToggleSplitView,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("split_multitask_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (splitViewEnabled) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Splitscreen,
                                contentDescription = "Toggle Split Multitask",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (splitViewEnabled) "Split ON" else "Split View")
                        }
                    }

                    if (runningSessions.isEmpty()) {
                        Text(
                            text = "No VM running yet. Boot an Android 10 (Q) image below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(runningSessions, key = { it.sessionId }) { session ->
                                val selected = session.sessionId == activeSession?.sessionId
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .clickable { onSelectSession(session.sessionId) }
                                        .testTag("vm_task_pill_${session.image.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = session.image.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "API ${session.image.apiLevel} • ${session.cpuLoadPercent}% CPU",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onCloseSession(session.sessionId) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close VM task",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Virtual Display(s) — Single or Split Multitask
        if (activeSession != null) {
            item {
                AnimatedVisibility(visible = splitViewEnabled && runningSessions.size >= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val first = runningSessions[0]
                        val second = runningSessions[1]
                        VirtualDeviceFrame(
                            session = first,
                            apks = apks,
                            allVaultItems = allVaultItems,
                            apkMetaCache = apkMetaCache,
                            compact = true,
                            onSetView = { view, apk -> onSetSessionView(first.sessionId, view, apk) },
                            onReboot = { onRebootSession(first.sessionId) },
                            onDownloadFromBrowser = onDownloadFromBrowser,
                            onCreateFolderInParent = onCreateFolderInParent,
                            onImportDocumentsToFolder = onImportDocumentsToFolder,
                            onRenameVaultItem = onRenameVaultItem,
                            onMoveVaultItem = onMoveVaultItem,
                            onDeleteVaultItem = onDeleteVaultItem,
                            onBuildShareIntent = onBuildShareIntent,
                            onOpenVaultTab = onOpenVaultTab,
                            onOpenShareTab = onOpenShareTab,
                            modifier = Modifier.weight(1f)
                        )
                        VirtualDeviceFrame(
                            session = second,
                            apks = apks,
                            allVaultItems = allVaultItems,
                            apkMetaCache = apkMetaCache,
                            compact = true,
                            onSetView = { view, apk -> onSetSessionView(second.sessionId, view, apk) },
                            onReboot = { onRebootSession(second.sessionId) },
                            onDownloadFromBrowser = onDownloadFromBrowser,
                            onCreateFolderInParent = onCreateFolderInParent,
                            onImportDocumentsToFolder = onImportDocumentsToFolder,
                            onRenameVaultItem = onRenameVaultItem,
                            onMoveVaultItem = onMoveVaultItem,
                            onDeleteVaultItem = onDeleteVaultItem,
                            onBuildShareIntent = onBuildShareIntent,
                            onOpenVaultTab = onOpenVaultTab,
                            onOpenShareTab = onOpenShareTab,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!splitViewEnabled || runningSessions.size < 2) {
                    VirtualDeviceFrame(
                        session = activeSession,
                        apks = apks,
                        allVaultItems = allVaultItems,
                        apkMetaCache = apkMetaCache,
                        compact = false,
                        onSetView = { view, apk -> onSetSessionView(activeSession.sessionId, view, apk) },
                        onReboot = { onRebootSession(activeSession.sessionId) },
                        onDownloadFromBrowser = onDownloadFromBrowser,
                        onCreateFolderInParent = onCreateFolderInParent,
                        onImportDocumentsToFolder = onImportDocumentsToFolder,
                        onRenameVaultItem = onRenameVaultItem,
                        onMoveVaultItem = onMoveVaultItem,
                        onDeleteVaultItem = onDeleteVaultItem,
                        onBuildShareIntent = onBuildShareIntent,
                        onOpenVaultTab = onOpenVaultTab,
                        onOpenShareTab = onOpenShareTab,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Available VM Images Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VM System Images",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${vmImages.size} profiles",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(vmImages, key = { it.id }) { image ->
            val isRunning = runningSessions.any { it.image.id == image.id }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isRunning) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = image.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (image.isCustomBuilt) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "CUSTOM",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${image.androidVersionLabel} • API ${image.apiLevel} • ${image.abi}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (image.isCustomBuilt) {
                            IconButton(
                                onClick = { onDeleteVmImage(image) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete custom image",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${image.ramMb / 1024} GB RAM") },
                            leadingIcon = {
                                Icon(Icons.Default.Memory, null, modifier = Modifier.size(14.dp))
                            }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("${image.storageGb} GB Disk") }
                        )
                        if (image.wirelessRootEnabled) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Wireless Root") },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onBootVm(image) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("boot_vm_${image.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRunning) "Focus VM" else "Boot VM")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualDeviceFrame(
    session: RunningVmSession,
    apks: List<VaultItemEntity>,
    allVaultItems: List<VaultItemEntity>,
    apkMetaCache: Map<Long, ApkMetadata>,
    compact: Boolean,
    onSetView: (VmAppView, VaultItemEntity?) -> Unit,
    onReboot: () -> Unit,
    onDownloadFromBrowser: (String, String?, String?) -> Unit,
    onCreateFolderInParent: (String, Long?) -> Unit,
    onImportDocumentsToFolder: (List<android.net.Uri>, Long?) -> Unit,
    onRenameVaultItem: (VaultItemEntity, String) -> Unit,
    onMoveVaultItem: (VaultItemEntity, Long?) -> Unit,
    onDeleteVaultItem: (VaultItemEntity) -> Unit,
    onBuildShareIntent: (VaultItemEntity) -> android.content.Intent?,
    onOpenVaultTab: () -> Unit,
    onOpenShareTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frameHeight = if (compact) 340.dp else 440.dp
    var browserFullscreen by remember(session.sessionId) { mutableStateOf(false) }
    var filesFullscreen by remember(session.sessionId) { mutableStateOf(false) }
    var explorerFolderId by remember(session.sessionId) { mutableStateOf<Long?>(null) }
    Card(
        modifier = modifier.height(frameHeight),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF060911))
    ) {
        Crossfade(targetState = session.isBooting, label = "vm_boot_crossfade") { booting ->
            if (booting) {
                VirtualPhoneBootLogoScreen(
                    brandName = session.image.bootBrand,
                    bootStage = session.bootStage,
                    compact = compact
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Wallpaper background
                    val customWp = session.image.customWallpaperUri
                    if (!customWp.isNullOrBlank() && File(customWp).exists()) {
                        AsyncImage(
                            model = File(customWp),
                            contentDescription = "Custom VM wallpaper",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_vm_wallpaper),
                            contentDescription = "VM desktop wallpaper",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66050811))
                    )

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Virtual Android Status Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xAA090D16))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${session.image.androidVersionLabel} • ${session.image.resolutionLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (session.image.wirelessRootEnabled) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "ROOT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${session.fps}fps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = onReboot,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Reboot VM",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                // Virtual OS Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    when (session.currentView) {
                        VmAppView.HOME -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = session.image.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "RAM ${session.ramUsedMb}/${session.image.ramMb} MB • CPU ${session.cpuLoadPercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(if (compact) 2 else 4),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f).padding(top = 12.dp)
                                ) {
                                    item {
                                        VmDesktopIcon(
                                            label = "Browser",
                                            tint = MaterialTheme.colorScheme.primary,
                                            icon = Icons.Default.Public,
                                            onClick = { onSetView(VmAppView.VM_BROWSER, null) }
                                        )
                                    }
                                    item {
                                        VmDesktopIcon(
                                            label = "Files",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            icon = Icons.Default.Folder,
                                            onClick = { onSetView(VmAppView.VM_FILES, null) }
                                        )
                                    }
                                    item {
                                        VmDesktopIcon(
                                            label = "Shell",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            icon = Icons.Default.Terminal,
                                            onClick = { onSetView(VmAppView.VM_TERMINAL, null) }
                                        )
                                    }
                                    item {
                                        VmDesktopIcon(
                                            label = "VM Info",
                                            tint = MaterialTheme.colorScheme.primary,
                                            icon = Icons.Default.Settings,
                                            onClick = { onSetView(VmAppView.VM_SETTINGS, null) }
                                        )
                                    }
                                    items(apks.take(if (compact) 3 else 5), key = { it.id }) { apk ->
                                        val meta = apkMetaCache[apk.id]
                                        VmApkDesktopIcon(
                                            label = meta?.appLabel ?: apk.name.removeSuffix(".apk"),
                                            meta = meta,
                                            onClick = { onSetView(VmAppView.APP_WINDOW, apk) }
                                        )
                                    }
                                }

                                if (apks.isEmpty()) {
                                    OutlinedButton(
                                        onClick = onOpenVaultTab,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("+ Import APK to Run Here")
                                    }
                                }
                            }
                        }

                        VmAppView.APP_WINDOW -> {
                            val apk = session.activeApk
                            val meta = apk?.let { apkMetaCache[it.id] }
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (meta?.icon != null) {
                                            Image(
                                                bitmap = meta.icon,
                                                contentDescription = meta.appLabel,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Android,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = meta?.appLabel ?: apk?.name ?: "Sandbox App",
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = meta?.packageName ?: apk?.apkPackageName ?: "com.sandbox.app",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "Running in ${session.image.androidVersionLabel} (${session.image.abi})",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = meta?.androidSupportSummary ?: "Android 10 (API 29) lightweight container",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            val perms = meta?.permissions?.take(4)?.joinToString("\n• ")
                                            if (!perms.isNullOrBlank()) {
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = "Permissions:\n• $perms",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 5,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = onOpenShareTab,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Stream Screen")
                                        }
                                        OutlinedButton(
                                            onClick = { onSetView(VmAppView.HOME, null) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Minimize")
                                        }
                                    }
                                }
                            }
                        }

                        VmAppView.VM_BROWSER -> {
                            if (browserFullscreen) {
                                Surface(
                                    color = Color(0xFF090D16),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Browser active in fullscreen...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            } else {
                                VmBrowserPane(
                                    initialUrl = "litedirt://home",
                                    isFullscreen = false,
                                    onToggleFullscreen = { browserFullscreen = true },
                                    onDownloadFile = onDownloadFromBrowser,
                                    onCloseBrowser = { onSetView(VmAppView.HOME, null) }
                                )
                            }
                        }

                        VmAppView.VM_FILES -> {
                            if (filesFullscreen) {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "File Explorer active in fullscreen...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                VmFilesPane(
                                    containerLabel = session.image.name,
                                    allVaultItems = allVaultItems,
                                    apkMetaCache = apkMetaCache,
                                    currentFolderId = explorerFolderId,
                                    onChangeFolder = { explorerFolderId = it },
                                    isFullscreen = false,
                                    onToggleFullscreen = { filesFullscreen = true },
                                    onCloseFiles = { onSetView(VmAppView.HOME, null) },
                                    onImportDocuments = onImportDocumentsToFolder,
                                    onCreateFolder = onCreateFolderInParent,
                                    onLaunchApk = { apk -> onSetView(VmAppView.APP_WINDOW, apk) },
                                    onRenameItem = onRenameVaultItem,
                                    onMoveItem = onMoveVaultItem,
                                    onDeleteItem = onDeleteVaultItem,
                                    onBuildShareIntent = onBuildShareIntent
                                )
                            }
                        }

                        VmAppView.VM_TERMINAL -> {
                            Surface(
                                color = Color(0xEE04070D),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "droidvm@${session.image.abi}:/#",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "ro.build.version.release=${session.image.androidVersionLabel}\nro.build.version.sdk=${session.image.apiLevel}\ndroidvm.wireless_root=${session.image.wirelessRootEnabled}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFE2E8F0)
                                    )
                                    Spacer(Modifier.weight(1f))
                                    FilledTonalButton(onClick = onOpenShareTab) {
                                        Text("Open Wireless Root Console")
                                    }
                                }
                            }
                        }

                        VmAppView.VM_SETTINGS -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("VM Hardware Spec", style = MaterialTheme.typography.titleMedium)
                                    Text("OS: ${session.image.androidVersionLabel} (API ${session.image.apiLevel})", style = MaterialTheme.typography.bodySmall)
                                    Text("CPU ABI: ${session.image.abi}", style = MaterialTheme.typography.bodySmall)
                                    Text("RAM: ${session.image.ramMb} MB | Disk: ${session.image.storageGb} GB", style = MaterialTheme.typography.bodySmall)
                                    Text("Display: ${session.image.resolutionLabel} @ ${session.image.dpi}dpi", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Wireless Root: ${if (session.image.wirelessRootEnabled) "Enabled" else "Standard"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.weight(1f))
                                    OutlinedButton(onClick = onReboot) {
                                        Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Restart VM (Boot Logo)")
                                    }
                                }
                            }
                        }
                    }
                }

                // Virtual Android Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC090D16))
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSetView(VmAppView.HOME, null) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "VM Back", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onSetView(VmAppView.HOME, null) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Home, "VM Home", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onSetView(VmAppView.VM_SETTINGS, null) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ViewAgenda, "VM Overview", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
            }
        }
    }

    if (browserFullscreen) {
        Dialog(
            onDismissRequest = { browserFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            VmBrowserPane(
                initialUrl = "litedirt://home",
                isFullscreen = true,
                onToggleFullscreen = { browserFullscreen = false },
                onDownloadFile = onDownloadFromBrowser,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (filesFullscreen) {
        Dialog(
            onDismissRequest = { filesFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            VmFilesPane(
                containerLabel = session.image.name,
                allVaultItems = allVaultItems,
                apkMetaCache = apkMetaCache,
                currentFolderId = explorerFolderId,
                onChangeFolder = { explorerFolderId = it },
                isFullscreen = true,
                onToggleFullscreen = { filesFullscreen = false },
                onImportDocuments = onImportDocumentsToFolder,
                onCreateFolder = onCreateFolderInParent,
                onLaunchApk = { apk ->
                    filesFullscreen = false
                    onSetView(VmAppView.APP_WINDOW, apk)
                },
                onRenameItem = onRenameVaultItem,
                onMoveItem = onMoveVaultItem,
                onDeleteItem = onDeleteVaultItem,
                onBuildShareIntent = onBuildShareIntent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun VirtualPhoneBootLogoScreen(
    brandName: String,
    bootStage: Int,
    compact: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "boot_logo_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030508))
            .padding(20.dp)
    ) {
        // Bootloader top badge (stage 0)
        if (bootStage == 0) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
                Text(
                    text = "BOOTLOADER UNLOCKED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Centered Phone Brand Logo
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(pulseScale)
                    .size(if (compact) 68.dp else 88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f * glowAlpha),
                                Color(0xFF0B1521)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f * glowAlpha),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_art),
                    contentDescription = "$brandName boot logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (compact) 54.dp else 70.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }

            Text(
                text = brandName.ifBlank { "LITEDIRT" }.uppercase(),
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                color = Color.White,
                letterSpacing = 3.sp,
                modifier = Modifier.alpha(glowAlpha)
            )
        }

        // Bottom "Powered by Android 10" footer + animated dots
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { idx ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if ((bootStage + idx) % 2 == 0) MaterialTheme.colorScheme.primary
                                else Color(0xFF334155)
                            )
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "POWERED BY ANDROID 10",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
private fun VmDesktopIcon(
    label: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Surface(
            color = tint.copy(alpha = 0.2f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VmApkDesktopIcon(
    label: String,
    meta: ApkMetadata?,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        if (meta?.icon != null) {
            Image(
                bitmap = meta.icon,
                contentDescription = label,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickButtonDock(
    hasActiveSession: Boolean,
    splitViewEnabled: Boolean,
    isSharingLive: Boolean,
    onOpenBrowser: () -> Unit,
    onOpenVmFiles: () -> Unit,
    onImportApk: () -> Unit,
    onOpenFolders: () -> Unit,
    onBuildImage: () -> Unit,
    onToggleShare: () -> Unit,
    onToggleSplit: () -> Unit,
    onReboot: () -> Unit,
    onOpenRoot: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                QuickActionTile(
                    label = "Browser",
                    icon = Icons.Default.Public,
                    accent = MaterialTheme.colorScheme.primary,
                    active = false,
                    testTag = "quick_btn_browser",
                    onClick = onOpenBrowser
                )
            }
            item {
                QuickActionTile(
                    label = "Explorer",
                    icon = Icons.Default.Folder,
                    accent = MaterialTheme.colorScheme.tertiary,
                    active = false,
                    testTag = "quick_btn_vm_files",
                    onClick = onOpenVmFiles
                )
            }
            item {
                QuickActionTile(
                    label = "Import APK",
                    icon = Icons.Default.UploadFile,
                    accent = MaterialTheme.colorScheme.primary,
                    active = false,
                    testTag = "quick_btn_import",
                    onClick = onImportApk
                )
            }
            item {
                QuickActionTile(
                    label = "Folders",
                    icon = Icons.Default.Folder,
                    accent = MaterialTheme.colorScheme.tertiary,
                    active = false,
                    testTag = "quick_btn_folders",
                    onClick = onOpenFolders
                )
            }
            item {
                QuickActionTile(
                    label = if (isSharingLive) "Live ON" else "Share Live",
                    icon = Icons.Default.Cast,
                    accent = MaterialTheme.colorScheme.secondary,
                    active = isSharingLive,
                    testTag = "quick_btn_share",
                    onClick = onToggleShare
                )
            }
            item {
                QuickActionTile(
                    label = if (splitViewEnabled) "Split ON" else "Split Task",
                    icon = Icons.Default.Splitscreen,
                    accent = MaterialTheme.colorScheme.primary,
                    active = splitViewEnabled,
                    testTag = "quick_btn_split",
                    onClick = onToggleSplit
                )
            }
            if (hasActiveSession) {
                item {
                    QuickActionTile(
                        label = "Reboot",
                        icon = Icons.Default.RestartAlt,
                        accent = MaterialTheme.colorScheme.tertiary,
                        active = false,
                        testTag = "quick_btn_reboot",
                        onClick = onReboot
                    )
                }
            }
            item {
                QuickActionTile(
                    label = "Build Img",
                    icon = Icons.Default.Build,
                    accent = MaterialTheme.colorScheme.primary,
                    active = false,
                    testTag = "quick_btn_build",
                    onClick = onBuildImage
                )
            }
            item {
                QuickActionTile(
                    label = "Root Wi-Fi",
                    icon = Icons.Default.Terminal,
                    accent = MaterialTheme.colorScheme.secondary,
                    active = false,
                    testTag = "quick_btn_root",
                    onClick = onOpenRoot
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (active) accent else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

