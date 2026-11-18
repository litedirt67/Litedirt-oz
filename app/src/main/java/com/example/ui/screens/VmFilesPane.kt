package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.VaultItemEntity
import com.example.data.repository.ApkMetadata
import java.util.Locale

private enum class ContainerFileFilter(val label: String) {
    ALL("All"),
    APKS("APKs"),
    FOLDERS("Folders"),
    FILES("Files")
}

private data class ContainerCrumb(
    val id: Long?,
    val label: String
)

@Composable
fun VmFilesPane(
    containerLabel: String,
    allVaultItems: List<VaultItemEntity>,
    apkMetaCache: Map<Long, ApkMetadata>,
    currentFolderId: Long?,
    onChangeFolder: (Long?) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onCloseFiles: (() -> Unit)? = null,
    onImportDocuments: (uris: List<Uri>, parentFolderId: Long?) -> Unit,
    onCreateFolder: (name: String, parentFolderId: Long?) -> Unit,
    onLaunchApk: (VaultItemEntity) -> Unit,
    onRenameItem: (VaultItemEntity, String) -> Unit,
    onMoveItem: (VaultItemEntity, Long?) -> Unit,
    onDeleteItem: (VaultItemEntity) -> Unit,
    onBuildShareIntent: (VaultItemEntity) -> Intent?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ContainerFileFilter.ALL) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var inspectedItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var renameTarget by remember { mutableStateOf<VaultItemEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<VaultItemEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<VaultItemEntity?>(null) }

    val itemById = remember(allVaultItems) { allVaultItems.associateBy { it.id } }

    // Ensure currentFolderId remains valid if a folder gets deleted
    val effectiveFolderId = remember(currentFolderId, itemById) {
        if (currentFolderId != null && itemById[currentFolderId]?.isFolder == true) {
            currentFolderId
        } else {
            null
        }
    }

    val breadcrumbs = remember(effectiveFolderId, itemById) {
        val trail = mutableListOf<ContainerCrumb>()
        var cursorId = effectiveFolderId
        val visited = mutableSetOf<Long>()
        while (cursorId != null && visited.add(cursorId)) {
            val folder = itemById[cursorId] ?: break
            trail.add(ContainerCrumb(folder.id, folder.name))
            cursorId = folder.parentFolderId
        }
        trail.reverse()
        listOf(ContainerCrumb(null, "LiteDirt")) + trail
    }

    val currentParentId = remember(effectiveFolderId, itemById) {
        effectiveFolderId?.let { itemById[it]?.parentFolderId }
    }

    BackHandler(enabled = effectiveFolderId != null) {
        onChangeFolder(currentParentId)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportDocuments(uris, effectiveFolderId)
        }
    }

    val folderChildren = remember(allVaultItems, effectiveFolderId) {
        allVaultItems
            .filter { it.parentFolderId == effectiveFolderId }
            .sortedWith(
                compareByDescending<VaultItemEntity> { it.isFolder }
                    .thenByDescending { it.isApk }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
    }

    val filteredChildren = remember(folderChildren, searchQuery, activeFilter) {
        folderChildren.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                (item.apkPackageName?.contains(searchQuery, ignoreCase = true) == true)
            val matchesFilter = when (activeFilter) {
                ContainerFileFilter.ALL -> true
                ContainerFileFilter.APKS -> item.isApk
                ContainerFileFilter.FOLDERS -> item.isFolder
                ContainerFileFilter.FILES -> !item.isFolder && !item.isApk
            }
            matchesQuery && matchesFilter
        }
    }

    val totalFolderBytes = remember(folderChildren) {
        folderChildren.filterNot { it.isFolder }.sumOf { it.sizeBytes }
    }

    val childCountByFolder = remember(allVaultItems) {
        allVaultItems.mapNotNull { it.parentFolderId }.groupingBy { it }.eachCount()
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(if (isFullscreen) 0.dp else 16.dp),
        border = if (isFullscreen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxSize()
            .testTag("vm_files_pane")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Container File Explorer Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onChangeFolder(currentParentId) },
                    enabled = effectiveFolderId != null,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("vm_files_up_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Up to parent folder",
                        tint = if (effectiveFolderId != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Container Explorer • $containerLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "/sdcard/${breadcrumbs.joinToString("/") { it.label }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        newFolderName = ""
                        showNewFolderDialog = true
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("vm_files_new_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New folder in container",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("vm_files_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = "Import APK or file into folder",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("vm_files_fullscreen_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.Close else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen explorer" else "Fullscreen explorer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onCloseFiles != null && !isFullscreen) {
                    IconButton(
                        onClick = onCloseFiles,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("vm_files_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close explorer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Interactive Breadcrumbs & Search/Filter Strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(breadcrumbs) { idx, crumb ->
                            val isCurrent = idx == breadcrumbs.lastIndex
                            Surface(
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onChangeFolder(crumb.id) }
                            ) {
                                Text(
                                    text = if (idx == 0) "/${crumb.label}" else "› ${crumb.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${folderChildren.size} • ${formatContainerSize(totalFolderBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Compact Search Input + Filter Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search container APKs or files...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vm_files_search_input")
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ContainerFileFilter.entries) { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // Directory Listing
            if (filteredChildren.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank() || activeFilter != ContainerFileFilter.ALL) {
                                "No matching items here"
                            } else {
                                "Container directory is empty"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Import .apk packages or create subfolders directly inside this VM path.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import APK/File")
                            }
                            OutlinedButton(
                                onClick = {
                                    newFolderName = ""
                                    showNewFolderDialog = true
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Folder")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredChildren, key = { it.id }) { item ->
                        val meta = if (item.isApk) apkMetaCache[item.id] else null
                        val folderItemCount = if (item.isFolder) childCountByFolder[item.id] ?: 0 else 0
                        ContainerFileRow(
                            item = item,
                            meta = meta,
                            folderItemCount = folderItemCount,
                            onClick = {
                                if (item.isFolder) {
                                    onChangeFolder(item.id)
                                } else {
                                    inspectedItem = item
                                }
                            },
                            onRunApk = { onLaunchApk(item) },
                            onManage = { inspectedItem = item }
                        )
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Container Folder") },
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
                        onCreateFolder(newFolderName, effectiveFolderId)
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

    inspectedItem?.let { target ->
        val meta = if (target.isApk) apkMetaCache[target.id] else null
        AlertDialog(
            onDismissRequest = { inspectedItem = null },
            title = {
                Text(
                    text = meta?.appLabel ?: target.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "/sdcard/LiteDirt/${target.relativePath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (target.isFolder) {
                        Text(
                            text = "Directory • ${childCountByFolder[target.id] ?: 0} item(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (target.isApk) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Package: ${meta?.packageName ?: target.apkPackageName ?: "Unknown"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Version: ${meta?.versionName ?: target.apkVersionName ?: "1.0"} • ${formatContainerSize(target.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = meta?.androidSupportSummary ?: "Android 10 (API 29) Compatible",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "${target.mimeType} • ${formatContainerSize(target.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Quick Management Action Grid inside Dialog
                    if (target.isApk) {
                        Button(
                            onClick = {
                                onLaunchApk(target)
                                inspectedItem = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run APK in VM")
                        }
                    } else if (target.isFolder) {
                        Button(
                            onClick = {
                                onChangeFolder(target.id)
                                inspectedItem = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Folder")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                renameInput = target.name
                                renameTarget = target
                                inspectedItem = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DriveFileRenameOutline, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Rename")
                        }
                        OutlinedButton(
                            onClick = {
                                moveTarget = target
                                inspectedItem = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Move")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!target.isFolder) {
                            OutlinedButton(
                                onClick = {
                                    onBuildShareIntent(target)?.let { intent ->
                                        context.startActivity(Intent.createChooser(intent, "Share ${target.name}"))
                                    }
                                    inspectedItem = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                deleteTarget = target
                                inspectedItem = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedItem = null }) {
                    Text("Close")
                }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename ${if (target.isFolder) "Folder" else "File"}") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameItem(target, renameInput)
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    moveTarget?.let { target ->
        val invalidFolderIds = remember(target, allVaultItems) {
            if (!target.isFolder) {
                emptySet()
            } else {
                collectFolderAndDescendants(target.id, allVaultItems)
            }
        }
        val destinationFolders = remember(allVaultItems, invalidFolderIds, target) {
            allVaultItems.filter {
                it.isFolder && it.id !in invalidFolderIds && it.id != target.parentFolderId
            }.sortedBy { it.relativePath.lowercase(Locale.US) }
        }

        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move '${target.name}'") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(220.dp)
                ) {
                    if (target.parentFolderId != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onMoveItem(target, null)
                                        moveTarget = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("/sdcard/LiteDirt (Root)", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    items(destinationFolders, key = { it.id }) { folder ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveItem(target, folder.id)
                                    moveTarget = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(folder.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "/sdcard/LiteDirt/${folder.relativePath}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (destinationFolders.isEmpty() && target.parentFolderId == null) {
                        item {
                            Text(
                                text = "Create another folder first to move items into it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete '${target.name}'?") },
            text = {
                Text(
                    text = if (target.isFolder) {
                        "This removes the folder and all APKs/files inside it from the container."
                    } else {
                        "This permanently removes the file from your LiteDirt container vault."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItem(target)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ContainerFileRow(
    item: VaultItemEntity,
    meta: ApkMetadata?,
    folderItemCount: Int,
    onClick: () -> Unit,
    onRunApk: () -> Unit,
    onManage: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (item.isApk) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("vm_file_row_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                item.isFolder -> {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Folder",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                item.isApk && meta?.icon != null -> {
                    Image(
                        bitmap = meta.icon,
                        contentDescription = meta.appLabel,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                item.isApk -> {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = "APK",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                else -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = "File",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meta?.appLabel ?: item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    item.isFolder -> "$folderItemCount item${if (folderItemCount == 1) "" else "s"} • Tap to open"
                    item.isApk -> "${item.apkPackageName ?: item.name} • v${item.apkVersionName ?: "1.0"} • ${formatContainerSize(item.sizeBytes)}"
                    else -> "${item.mimeType} • ${formatContainerSize(item.sizeBytes)}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.isApk) {
                IconButton(
                    onClick = onRunApk,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vm_file_run_apk_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run APK in VM",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(
                onClick = onManage,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("vm_file_manage_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Manage ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun collectFolderAndDescendants(
    rootFolderId: Long,
    allItems: List<VaultItemEntity>
): Set<Long> {
    val result = mutableSetOf(rootFolderId)
    var added = true
    while (added) {
        added = false
        for (item in allItems) {
            if (item.isFolder && item.parentFolderId in result && result.add(item.id)) {
                added = true
            }
        }
    }
    return result
}

private fun formatContainerSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
