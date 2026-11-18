package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.VaultItemEntity
import com.example.data.db.VmImageEntity
import com.example.ui.ImageBuilderDraft
import kotlin.math.roundToInt

private data class Android10ProfilePreset(
    val label: String,
    val ramMb: Int,
    val storageGb: Int,
    val resolution: String,
    val dpi: Int
)

private val ANDROID_10_PRESETS = listOf(
    Android10ProfilePreset("Lite (2 GB · 720p)", 2048, 16, "720x1280", 320),
    Android10ProfilePreset("Balanced (3 GB · 1080p)", 3072, 24, "1080x2400", 420),
    Android10ProfilePreset("Performance (4 GB · 1080p)", 4096, 32, "1080x2400", 420)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageBuilderScreen(
    draft: ImageBuilderDraft,
    isBuilding: Boolean,
    vaultItems: List<VaultItemEntity>,
    builtImages: List<VmImageEntity>,
    onUpdateDraft: ((ImageBuilderDraft) -> ImageBuilderDraft) -> Unit,
    onBuildCustomImage: ((VmImageEntity) -> Unit) -> Unit,
    onBootVm: (VmImageEntity) -> Unit,
    onExportImage: (VmImageEntity, android.net.Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingExportImage by remember { mutableStateOf<VmImageEntity?>(null) }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUpdateDraft {
                it.copy(
                    wallpaperPreset = "custom",
                    customWallpaperUri = uri.toString()
                )
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val img = pendingExportImage
        if (uri != null && img != null) {
            onExportImage(img, uri)
        }
        pendingExportImage = null
    }

    val packableFiles = remember(vaultItems) {
        vaultItems.filter { !it.isFolder }
    }
    val customBuiltList = remember(builtImages) {
        builtImages.filter { it.isCustomBuilt }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Custom Android 10 Image Builder",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Build a lightweight Android 10 (Q · API 29) custom system image (.dvmimg / .zip) with bundled APKs & wallpaper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Core Config Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { v -> onUpdateDraft { it.copy(name = v) } },
                        label = { Text("Image Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_image_name_input")
                    )

                    Text("Startup Brand Logo (Saat Hidup)", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("LiteDirt", "Pixel", "HyperOS", "ROG", "OneUI").forEach { brand ->
                            FilterChip(
                                selected = draft.bootBrand.equals(brand, ignoreCase = true),
                                onClick = { onUpdateDraft { it.copy(bootBrand = brand) } },
                                label = { Text(brand) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft.bootBrand,
                        onValueChange = { v -> onUpdateDraft { it.copy(bootBrand = v) } },
                        label = { Text("Custom Boot Brand Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Fixed Lightweight Android 10 Target Badge + Quick Resource Presets
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Base OS: Android 10 (Q · API 29)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Lightweight Core",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Text("Resource Profile", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ANDROID_10_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = draft.ramMb == preset.ramMb && draft.resolutionLabel == preset.resolution,
                                onClick = {
                                    onUpdateDraft {
                                        it.copy(
                                            androidVersionLabel = "Android 10 (Q)",
                                            apiLevel = 29,
                                            ramMb = preset.ramMb,
                                            storageGb = preset.storageGb,
                                            resolutionLabel = preset.resolution,
                                            dpi = preset.dpi
                                        )
                                    }
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Text("CPU Architecture & Display", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("arm64-v8a", "x86_64").forEach { abi ->
                            FilterChip(
                                selected = draft.abi == abi,
                                onClick = { onUpdateDraft { it.copy(abi = abi) } },
                                label = { Text(abi) }
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("720x1280" to 320, "1080x2400" to 420).forEach { (res, dpi) ->
                            FilterChip(
                                selected = draft.resolutionLabel == res,
                                onClick = {
                                    onUpdateDraft { it.copy(resolutionLabel = res, dpi = dpi) }
                                },
                                label = { Text("$res (${dpi}dpi)") }
                            )
                        }
                    }

                    // RAM & Storage Sliders (Lightweight 1-4 GB RAM, 8-32 GB Disk)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("RAM Allocation", style = MaterialTheme.typography.labelLarge)
                            Text("${draft.ramMb / 1024} GB (${draft.ramMb} MB)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = (draft.ramMb / 1024).toFloat().coerceIn(1f, 4f),
                            onValueChange = { gb ->
                                val rounded = gb.roundToInt().coerceIn(1, 4)
                                onUpdateDraft { it.copy(ramMb = rounded * 1024) }
                            },
                            valueRange = 1f..4f,
                            steps = 2
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Virtual Disk Partition", style = MaterialTheme.typography.labelLarge)
                            Text("${draft.storageGb} GB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = draft.storageGb.toFloat().coerceIn(8f, 32f),
                            onValueChange = { gb ->
                                val stepped = (gb / 8f).roundToInt().coerceIn(1, 4) * 8
                                onUpdateDraft { it.copy(storageGb = stepped) }
                            },
                            valueRange = 8f..32f,
                            steps = 2
                        )
                    }

                    // Wireless Root Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Enable Wireless Root Bridge", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Pre-configure root wireless shell & ADB TCP port 5555",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = draft.wirelessRootEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateDraft { it.copy(wirelessRootEnabled = enabled) }
                            }
                        )
                    }

                    // Custom Wallpaper Picker (Photo Picker)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom Boot / Desktop Image", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (draft.customWallpaperUri != null) "Custom gallery image selected" else "Default holographic theme",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                wallpaperPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick Image")
                        }
                    }
                }
            }
        }

        // Bundle APKs & Files into Custom Image
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Bundle APKs & Files into Image", style = MaterialTheme.typography.titleMedium)
                    if (packableFiles.isEmpty()) {
                        Text(
                            text = "No imported APKs or files in Vault yet. Import files in the Files tab to pre-bundle them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        packableFiles.forEach { item ->
                            val checked = draft.selectedVaultIds.contains(item.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onUpdateDraft {
                                            val next = if (checked) it.selectedVaultIds - item.id
                                            else it.selectedVaultIds + item.id
                                            it.copy(selectedVaultIds = next)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        onUpdateDraft {
                                            val next = if (isChecked) it.selectedVaultIds + item.id
                                            else it.selectedVaultIds - item.id
                                            it.copy(selectedVaultIds = next)
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (item.isApk) "APK • ${item.apkPackageName ?: "package"}" else item.relativePath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Button(
                        onClick = { onBuildCustomImage { onBootVm(it) } },
                        enabled = !isBuilding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("build_custom_image_button")
                    ) {
                        if (isBuilding) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Building .dvmimg Archive...")
                        } else {
                            Icon(Icons.Default.Build, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Build Android 10 Image & Boot VM")
                        }
                    }
                }
            }
        }

        // Built Custom Images List
        if (customBuiltList.isNotEmpty()) {
            item {
                Text("Your Custom Built Images", style = MaterialTheme.typography.titleLarge)
            }
            items(customBuiltList, key = { it.id }) { image ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(image.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${image.androidVersionLabel} • ${image.abi} • ${image.bundledApkCount} APK(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                pendingExportImage = image
                                exportLauncher.launch("${image.name.replace(" ", "_")}.zip")
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(".zip")
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clickable { onBootVm(image) }
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Boot custom image",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
