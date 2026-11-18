package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.example.data.db.DroidVmDao
import com.example.data.db.VaultItemEntity
import com.example.data.db.VmImageEntity
import com.example.data.db.WirelessProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CustomImageSpec(
    val name: String,
    val androidVersionLabel: String,
    val apiLevel: Int,
    val abi: String,
    val ramMb: Int,
    val storageGb: Int,
    val dpi: Int,
    val resolutionLabel: String,
    val wirelessRootEnabled: Boolean,
    val wallpaperPreset: String,
    val bootBrand: String,
    val customWallpaperUri: String?,
    val bundledVaultItemIds: List<Long>
)

class VmVaultRepository(
    private val context: Context,
    private val dao: DroidVmDao
) {
    private val vaultRootDir: File by lazy {
        File(context.filesDir, "vm_vault").apply { mkdirs() }
    }
    private val imagesRootDir: File by lazy {
        File(context.filesDir, "vm_images").apply { mkdirs() }
    }

    val vmImages: Flow<List<VmImageEntity>> = dao.observeVmImages()
    val allApks: Flow<List<VaultItemEntity>> = dao.observeAllApks()
    val allVaultItems: Flow<List<VaultItemEntity>> = dao.observeAllVaultItems()
    val wirelessProfiles: Flow<List<WirelessProfileEntity>> = dao.observeWirelessProfiles()

    fun observeVaultItems(parentId: Long?): Flow<List<VaultItemEntity>> =
        dao.observeVaultItemsInFolder(parentId)

    suspend fun seedDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        dao.deleteNonAndroid10BuiltInImages()
        if (dao.getVmImageCount() == 0) {
            dao.insertVmImage(
                VmImageEntity(
                    name = "Android 10 Q Lite",
                    androidVersionLabel = "Android 10 (Q)",
                    apiLevel = 29,
                    abi = "arm64-v8a",
                    ramMb = 2048,
                    storageGb = 16,
                    dpi = 320,
                    resolutionLabel = "720x1280",
                    wirelessRootEnabled = true,
                    wallpaperPreset = "cyber_wave",
                    bootBrand = "LiteDirt",
                    isCustomBuilt = false
                )
            )
            dao.insertVmImage(
                VmImageEntity(
                    name = "Android 10 Q Pro",
                    androidVersionLabel = "Android 10 (Q)",
                    apiLevel = 29,
                    abi = "arm64-v8a",
                    ramMb = 4096,
                    storageGb = 32,
                    dpi = 420,
                    resolutionLabel = "1080x2400",
                    wirelessRootEnabled = true,
                    wallpaperPreset = "grid_core",
                    bootBrand = "LiteDirt",
                    isCustomBuilt = false
                )
            )
        }

        if (dao.getVaultItemCount() == 0) {
            val defaultFolders = listOf("APKs", "Shared", "SystemImages")
            defaultFolders.forEach { folderName ->
                File(vaultRootDir, folderName).mkdirs()
                dao.insertVaultItem(
                    VaultItemEntity(
                        name = folderName,
                        parentFolderId = null,
                        isFolder = true,
                        relativePath = folderName,
                        mimeType = "inode/directory"
                    )
                )
            }
        }

        if (dao.getWirelessProfileCount() == 0) {
            dao.insertWirelessProfile(
                WirelessProfileEntity(
                    label = "Local Wireless ADB",
                    hostIp = "127.0.0.1",
                    port = 5555,
                    pairingPort = 37000,
                    isRootBridge = true,
                    lastStatus = "Ready to test"
                )
            )
        }
    }

    suspend fun createFolder(name: String, parentFolderId: Long?): VaultItemEntity? =
        withContext(Dispatchers.IO) {
            val cleanName = name.trim().replace("/", "_")
            if (cleanName.isBlank()) return@withContext null
            val parentRel = parentFolderId?.let { dao.getVaultItemById(it)?.relativePath }
            val relPath = if (parentRel.isNullOrBlank()) cleanName else "$parentRel/$cleanName"
            val dir = File(vaultRootDir, relPath)
            dir.mkdirs()
            val entity = VaultItemEntity(
                name = cleanName,
                parentFolderId = parentFolderId,
                isFolder = true,
                relativePath = relPath,
                mimeType = "inode/directory"
            )
            val id = dao.insertVaultItem(entity)
            entity.copy(id = id)
        }

    suspend fun importDocuments(uris: List<Uri>, parentFolderId: Long?): List<VaultItemEntity> =
        withContext(Dispatchers.IO) {
            val imported = mutableListOf<VaultItemEntity>()
            val parentRel = parentFolderId?.let { dao.getVaultItemById(it)?.relativePath }
            val targetDir = if (parentRel.isNullOrBlank()) vaultRootDir else File(vaultRootDir, parentRel)
            targetDir.mkdirs()

            for (uri in uris) {
                val rawName = resolveDisplayName(uri) ?: "imported_${System.currentTimeMillis()}"
                val safeName = uniqueFileName(targetDir, rawName)
                val destFile = File(targetDir, safeName)

                val copied = try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    } != null
                } catch (_: Exception) {
                    false
                }

                if (!copied || !destFile.exists()) continue

                val mime = context.contentResolver.getType(uri)
                    ?: if (safeName.endsWith(".apk", ignoreCase = true)) {
                        "application/vnd.android.package-archive"
                    } else {
                        "application/octet-stream"
                    }
                val isApk = safeName.endsWith(".apk", ignoreCase = true) ||
                    mime == "application/vnd.android.package-archive"
                val apkMeta = if (isApk) ApkInspector.inspectApkFile(context, destFile) else null
                val relPath = if (parentRel.isNullOrBlank()) safeName else "$parentRel/$safeName"

                val entity = VaultItemEntity(
                    name = safeName,
                    parentFolderId = parentFolderId,
                    isFolder = false,
                    relativePath = relPath,
                    mimeType = mime,
                    sizeBytes = destFile.length(),
                    isApk = isApk,
                    apkPackageName = apkMeta?.packageName,
                    apkVersionName = apkMeta?.versionName,
                    apkMinSdk = apkMeta?.minSdk,
                    apkTargetSdk = apkMeta?.targetSdk,
                    apkPermissionsSummary = apkMeta?.permissions?.take(6)?.joinToString(", ")
                )
                val id = dao.insertVaultItem(entity)
                imported.add(entity.copy(id = id))
            }
            imported
        }

    suspend fun downloadFromUrlToVault(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        parentFolderId: Long? = null
    ): VaultItemEntity? = withContext(Dispatchers.IO) {
        val targetDir = if (parentFolderId == null) {
            File(vaultRootDir, "APKs").apply { mkdirs() }
        } else {
            val parentRel = dao.getVaultItemById(parentFolderId)?.relativePath
            if (parentRel.isNullOrBlank()) vaultRootDir else File(vaultRootDir, parentRel).apply { mkdirs() }
        }
        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .ifBlank { "download_${System.currentTimeMillis()}" }
        val safeName = uniqueFileName(targetDir, guessed)
        val destFile = File(targetDir, safeName)

        try {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "LiteDirt-Android10-VM/1.0")
            }
            conn.connect()
            if (conn.responseCode !in 200..399) {
                conn.disconnect()
                return@withContext null
            }
            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
        } catch (_: Exception) {
            if (destFile.exists()) destFile.delete()
            return@withContext null
        }

        if (!destFile.exists() || destFile.length() == 0L) return@withContext null

        val resolvedMime = mimeType?.takeIf { it.isNotBlank() }
            ?: if (safeName.endsWith(".apk", ignoreCase = true)) {
                "application/vnd.android.package-archive"
            } else {
                "application/octet-stream"
            }
        val isApk = safeName.endsWith(".apk", ignoreCase = true) ||
            resolvedMime == "application/vnd.android.package-archive"
        val apkMeta = if (isApk) ApkInspector.inspectApkFile(context, destFile) else null
        val relPath = if (parentFolderId == null) "APKs/$safeName" else {
            val parentRel = dao.getVaultItemById(parentFolderId)?.relativePath
            if (parentRel.isNullOrBlank()) safeName else "$parentRel/$safeName"
        }

        val entity = VaultItemEntity(
            name = safeName,
            parentFolderId = parentFolderId,
            isFolder = false,
            relativePath = relPath,
            mimeType = resolvedMime,
            sizeBytes = destFile.length(),
            isApk = isApk,
            apkPackageName = apkMeta?.packageName,
            apkVersionName = apkMeta?.versionName,
            apkMinSdk = apkMeta?.minSdk,
            apkTargetSdk = apkMeta?.targetSdk,
            apkPermissionsSummary = apkMeta?.permissions?.take(6)?.joinToString(", ")
        )
        val id = dao.insertVaultItem(entity)
        entity.copy(id = id)
    }

    suspend fun renameVaultItem(item: VaultItemEntity, newName: String) = withContext(Dispatchers.IO) {
        val clean = newName.trim().replace("/", "_")
        if (clean.isBlank() || clean == item.name) return@withContext
        val oldRel = item.relativePath
        val oldFile = File(vaultRootDir, oldRel)
        val parentDir = oldFile.parentFile ?: vaultRootDir
        val newFile = File(parentDir, clean)
        if (oldFile.exists()) {
            oldFile.renameTo(newFile)
        }
        val parentRel = oldRel.substringBeforeLast('/', "")
        val newRel = if (parentRel.isBlank()) clean else "$parentRel/$clean"
        dao.updateVaultItem(item.copy(name = clean, relativePath = newRel))
        if (item.isFolder) {
            updateDescendantsRelativePath(item.id, newRel)
        }
    }

    suspend fun moveVaultItem(item: VaultItemEntity, targetFolderId: Long?): Boolean =
        withContext(Dispatchers.IO) {
            if (item.parentFolderId == targetFolderId) return@withContext false
            if (item.isFolder && targetFolderId != null) {
                if (targetFolderId == item.id || isDescendantFolder(item.id, targetFolderId)) {
                    return@withContext false
                }
            }

            val targetParentRel = targetFolderId?.let { dao.getVaultItemById(it)?.relativePath }
            val targetDir = if (targetParentRel.isNullOrBlank()) {
                vaultRootDir
            } else {
                File(vaultRootDir, targetParentRel)
            }
            targetDir.mkdirs()

            val oldFile = File(vaultRootDir, item.relativePath)
            val safeName = uniqueFileName(targetDir, item.name)
            val destFile = File(targetDir, safeName)

            val movedOnDisk = if (oldFile.exists()) {
                if (oldFile.renameTo(destFile)) {
                    true
                } else {
                    try {
                        oldFile.copyRecursively(destFile, overwrite = true)
                        oldFile.deleteRecursively()
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            } else {
                true
            }

            if (!movedOnDisk) return@withContext false

            val newRel = if (targetParentRel.isNullOrBlank()) safeName else "$targetParentRel/$safeName"
            dao.updateVaultItem(
                item.copy(
                    name = safeName,
                    parentFolderId = targetFolderId,
                    relativePath = newRel
                )
            )
            if (item.isFolder) {
                updateDescendantsRelativePath(item.id, newRel)
            }
            true
        }

    private suspend fun isDescendantFolder(ancestorId: Long, candidateId: Long): Boolean {
        var current = dao.getVaultItemById(candidateId)
        val visited = mutableSetOf<Long>()
        while (current != null && visited.add(current.id)) {
            val parentId = current.parentFolderId ?: return false
            if (parentId == ancestorId) return true
            current = dao.getVaultItemById(parentId)
        }
        return false
    }

    private suspend fun updateDescendantsRelativePath(folderId: Long, folderNewRel: String) {
        val children = dao.getChildrenDirect(folderId)
        for (child in children) {
            val childNewRel = "$folderNewRel/${child.name}"
            dao.updateVaultItem(child.copy(relativePath = childNewRel))
            if (child.isFolder) {
                updateDescendantsRelativePath(child.id, childNewRel)
            }
        }
    }

    suspend fun deleteVaultItem(item: VaultItemEntity) = withContext(Dispatchers.IO) {
        deleteChildrenRecursive(item.id)
        val target = File(vaultRootDir, item.relativePath)
        if (target.exists()) {
            target.deleteRecursively()
        }
        dao.deleteVaultItemById(item.id)
    }

    private suspend fun deleteChildrenRecursive(folderId: Long) {
        val children = dao.getChildrenDirect(folderId)
        for (child in children) {
            if (child.isFolder) {
                deleteChildrenRecursive(child.id)
            }
            dao.deleteVaultItemById(child.id)
        }
    }

    fun getVaultFile(item: VaultItemEntity): File = File(vaultRootDir, item.relativePath)

    suspend fun buildCustomImage(spec: CustomImageSpec): VmImageEntity = withContext(Dispatchers.IO) {
        val slug = spec.name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "custom_vm" }
        val archiveFile = File(imagesRootDir, "${slug}_${System.currentTimeMillis()}.zip")

        val manifestJson = JSONObject().apply {
            put("format", "droidvm-image-v1")
            put("name", spec.name.trim())
            put("androidVersion", spec.androidVersionLabel)
            put("apiLevel", spec.apiLevel)
            put("abi", spec.abi)
            put("ramMb", spec.ramMb)
            put("storageGb", spec.storageGb)
            put("dpi", spec.dpi)
            put("resolution", spec.resolutionLabel)
            put("wirelessRoot", spec.wirelessRootEnabled)
            put("wallpaperPreset", spec.wallpaperPreset)
            put("bootBrand", spec.bootBrand)
            put("createdAt", System.currentTimeMillis())
        }

        val bundledItems = spec.bundledVaultItemIds.mapNotNull { dao.getVaultItemById(it) }
        val bundledArray = JSONArray()
        bundledItems.forEach { bundledArray.put(it.relativePath) }
        manifestJson.put("bundledPackages", bundledArray)

        var savedWallpaperFile: String? = null
        if (!spec.customWallpaperUri.isNullOrBlank()) {
            try {
                val wpUri = Uri.parse(spec.customWallpaperUri)
                val wpFile = File(imagesRootDir, "wp_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(wpUri)?.use { input ->
                    FileOutputStream(wpFile).use { output -> input.copyTo(output) }
                }
                if (wpFile.exists() && wpFile.length() > 0) {
                    savedWallpaperFile = wpFile.absolutePath
                }
            } catch (_: Exception) {
            }
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.dvmimg"))
            zip.write(manifestJson.toString(2).toByteArray())
            zip.closeEntry()

            for (item in bundledItems) {
                val f = getVaultFile(item)
                if (f.exists() && f.isFile) {
                    zip.putNextEntry(ZipEntry("system/app/${item.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            if (savedWallpaperFile != null) {
                val wp = File(savedWallpaperFile)
                if (wp.exists()) {
                    zip.putNextEntry(ZipEntry("system/media/wallpaper.jpg"))
                    wp.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

        val entity = VmImageEntity(
            name = spec.name.trim(),
            androidVersionLabel = spec.androidVersionLabel,
            apiLevel = spec.apiLevel,
            abi = spec.abi,
            ramMb = spec.ramMb,
            storageGb = spec.storageGb,
            dpi = spec.dpi,
            resolutionLabel = spec.resolutionLabel,
            wirelessRootEnabled = spec.wirelessRootEnabled,
            wallpaperPreset = if (savedWallpaperFile != null) "custom" else spec.wallpaperPreset,
            bootBrand = spec.bootBrand.trim().ifBlank { "LiteDirt" },
            customWallpaperUri = savedWallpaperFile ?: spec.customWallpaperUri,
            imageArchivePath = archiveFile.absolutePath,
            bundledApkCount = bundledItems.count { it.isApk },
            isCustomBuilt = true
        )
        val id = dao.insertVmImage(entity)
        entity.copy(id = id)
    }

    suspend fun deleteVmImage(image: VmImageEntity) = withContext(Dispatchers.IO) {
        image.imageArchivePath?.let { File(it).delete() }
        dao.deleteVmImageById(image.id)
    }

    suspend fun exportArchiveToUri(image: VmImageEntity, destUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val srcPath = image.imageArchivePath ?: return@withContext false
            val srcFile = File(srcPath)
            if (!srcFile.exists()) return@withContext false
            try {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    srcFile.inputStream().use { inp -> inp.copyTo(out) }
                } != null
            } catch (_: Exception) {
                false
            }
        }

    suspend fun addWirelessProfile(label: String, hostIp: String, port: Int, pairingPort: Int?, isRootBridge: Boolean) {
        dao.insertWirelessProfile(
            WirelessProfileEntity(
                label = label.ifBlank { "Wireless Node" },
                hostIp = hostIp.trim(),
                port = port,
                pairingPort = pairingPort,
                isRootBridge = isRootBridge,
                lastStatus = "Configured"
            )
        )
    }

    suspend fun updateWirelessProfile(profile: WirelessProfileEntity) {
        dao.updateWirelessProfile(profile)
    }

    suspend fun deleteWirelessProfile(id: Long) {
        dao.deleteWirelessProfileById(id)
    }

    fun buildShareVaultFileIntent(item: VaultItemEntity): Intent? {
        val file = getVaultFile(item)
        if (!file.exists() || !file.isFile) return null
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun uniqueFileName(dir: File, original: String): String {
        val candidate = File(dir, original)
        if (!candidate.exists()) return original
        val base = original.substringBeforeLast('.', original)
        val ext = original.substringAfterLast('.', "")
        var idx = 1
        while (true) {
            val nextName = if (ext.isNotEmpty() && original.contains('.')) {
                "${base}_$idx.$ext"
            } else {
                "${base}_$idx"
            }
            if (!File(dir, nextName).exists()) return nextName
            idx++
        }
    }
}
