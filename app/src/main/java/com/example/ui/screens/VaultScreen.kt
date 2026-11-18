package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.VaultItemEntity
import com.example.data.repository.ApkMetadata
import com.example.ui.FolderBreadcrumb

private enum class VaultFilter(val label: String) {
    ALL("All"),
    APKS("APKs"),
    FOLDERS("Folders"),
    FILES("Files")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    breadcrumbs: List<FolderBreadcrumb>,
    items: List<VaultItemEntity>,
    apkMetaCache: Map<Long, ApkMetadata>,
    onOpenFolder: (VaultItemEntity) -> Unit,
    onNavigateBreadcrumb: (Int) -> Unit,
    onNavigateUp: () -> Boolean,
    onCreateFolder: (String) -> Unit,
    onImportDocuments: (List<android.net.Uri>) -> Unit,
    onRenameItem: (VaultItemEntity, String) -> Unit,
    onDeleteItem: (VaultItemEntity) -> Unit,
    onLaunchApkInVm: (VaultItemEntity) -> Unit,
    onBuildShareIntent: (VaultItemEntity) -> Intent?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VaultFilter.ALL) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedItemForSheet by remember { mutableStateOf<VaultItemEntity?>(null) }
    var itemToRename by remember { mutableStateOf<VaultItemEntity?>(null) }
    var renameValue by remember { mutableStateOf("") }

    BackHandler(enabled = breadcrumbs.size > 1) {
        onNavigateUp()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportDocuments(uris)
        }
    }

    val filteredItems = remember(items, searchQuery, filter) {
        items.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                (item.apkPackageName?.contains(searchQuery, ignoreCase = true) == true)
            val matchesFilter = when (filter) {
                VaultFilter.ALL -> true
                VaultFilter.APKS -> item.isApk
                VaultFilter.FOLDERS -> item.isFolder
                VaultFilter.FILES -> !item.isFolder && !item.isApk
            }
            matchesQuery && matchesFilter
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header + Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "APK & File Vault",
                style = MaterialTheme.typography.headlineSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("import_apk_file_button")
                ) {
                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import APK / File")
                }
                FilledTonalButton(
                    onClick = {
                        newFolderName = ""
                        showNewFolderDialog = true
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("create_folder_button")
                ) {
                    Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+ Folder")
                }
            }

            // Breadcrumbs
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(breadcrumbs) { idx, crumb ->
                    Surface(
                        color = if (idx == breadcrumbs.lastIndex) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable { onNavigateBreadcrumb(idx) }
                    ) {
                        Text(
                            text = if (idx == 0) "/${crumb.name}" else "› ${crumb.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (idx == breadcrumbs.lastIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Search & Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search APKs, folders, or package IDs...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VaultFilter.entries) { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = { Text(entry.label) }
                    )
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "This folder is empty",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap Import APK / File to load .apk packages or create a subfolder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val meta = if (item.isApk) apkMetaCache[item.id] else null
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.isFolder) onOpenFolder(item) else selectedItemForSheet = item
                            }
                            .testTag("vault_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                item.isFolder -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "Folder",
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                                item.isApk && meta?.icon != null -> {
                                    Image(
                                        bitmap = meta.icon,
                                        contentDescription = meta.appLabel,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                item.isApk -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Android,
                                                contentDescription = "APK",
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                                contentDescription = "File",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta?.appLabel ?: item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = when {
                                    item.isFolder -> "Folder • Tap to open"
                                    item.isApk -> "${item.apkPackageName ?: item.name} • v${item.apkVersionName ?: "1.0"} • ${formatSize(item.sizeBytes)}"
                                    else -> "${item.mimeType} • ${formatSize(item.sizeBytes)}"
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.isApk) {
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = meta?.androidSupportSummary ?: "Android 10 Compatible",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (item.isApk) {
                                IconButton(
                                    onClick = { onLaunchApkInVm(item) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Launch in VM",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    renameValue = item.name
                                    itemToRename = item
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileRenameOutline,
                                    contentDescription = "Rename",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateFolder(newFolderName)
                        showNewFolderDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    itemToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename Item") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameItem(target, renameValue)
                        itemToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedItemForSheet?.let { item ->
        val meta = if (item.isApk) apkMetaCache[item.id] else null
        ModalBottomSheet(onDismissRequest = { selectedItemForSheet = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = meta?.appLabel ?: item.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Path: /Vault/${item.relativePath} • ${formatSize(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.isApk) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Package: ${meta?.packageName ?: item.apkPackageName ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                            Text("Version: ${meta?.versionName ?: item.apkVersionName ?: "1.0"}", style = MaterialTheme.typography.bodySmall)
                            Text("Support: ${meta?.androidSupportSummary ?: "Android 10 (API 29)"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            val permList = meta?.permissions?.take(6)?.joinToString(", ") ?: item.apkPermissionsSummary
                            if (!permList.isNullOrBlank()) {
                                Text("Permissions: $permList", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            onLaunchApkInVm(item)
                            selectedItemForSheet = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Run in Active VM Sandbox")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onBuildShareIntent(item)?.let { shareIntent ->
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${item.name}"))
                            }
                            selectedItemForSheet = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    OutlinedButton(
                        onClick = {
                            onDeleteItem(item)
                            selectedItemForSheet = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.2f MB", mb)
}
