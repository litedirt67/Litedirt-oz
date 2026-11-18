package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.DroidVmDatabase
import com.example.data.db.VaultItemEntity
import com.example.data.db.VmImageEntity
import com.example.data.db.WirelessProfileEntity
import com.example.data.repository.ApkInspector
import com.example.data.repository.ApkMetadata
import com.example.data.repository.CustomImageSpec
import com.example.data.repository.VmVaultRepository
import com.example.service.HostDeviceTelemetry
import com.example.service.RealtimeShareServer
import com.example.service.ScreenShareService
import com.example.service.ShareServerState
import com.example.service.ShellOutput
import com.example.service.WirelessRootBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VmAppView {
    HOME,
    APP_WINDOW,
    VM_BROWSER,
    VM_FILES,
    VM_TERMINAL,
    VM_SETTINGS
}

data class RunningVmSession(
    val sessionId: String,
    val image: VmImageEntity,
    val currentView: VmAppView = VmAppView.HOME,
    val activeApk: VaultItemEntity? = null,
    val cpuLoadPercent: Int = 18,
    val ramUsedMb: Int = 1120,
    val fps: Int = 60,
    val isBooting: Boolean = true,
    val bootStage: Int = 0, // 0 = bootloader/logo splash, 1 = brand boot animation, 2 = booted
    val startedAt: Long = System.currentTimeMillis()
)

data class FolderBreadcrumb(
    val id: Long?,
    val name: String
)

enum class ShareStreamMode {
    VM_VIEWPORT,
    DEVICE_MEDIA_PROJECTION
}

data class ImageBuilderDraft(
    val name: String = "My Android 10 Custom",
    val androidVersionLabel: String = "Android 10 (Q)",
    val apiLevel: Int = 29,
    val abi: String = "arm64-v8a",
    val ramMb: Int = 4096,
    val storageGb: Int = 32,
    val dpi: Int = 420,
    val resolutionLabel: String = "1080x2400",
    val wirelessRootEnabled: Boolean = true,
    val wallpaperPreset: String = "cyber_wave",
    val bootBrand: String = "LiteDirt",
    val customWallpaperUri: String? = null,
    val selectedVaultIds: Set<Long> = emptySet()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DroidVmViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DroidVmDatabase.getInstance(application).droidVmDao()
    private val repository = VmVaultRepository(application, dao)

    val vmImages: StateFlow<List<VmImageEntity>> = repository.vmImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allApks: StateFlow<List<VaultItemEntity>> = repository.allApks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVaultItems: StateFlow<List<VaultItemEntity>> = repository.allVaultItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wirelessProfiles: StateFlow<List<WirelessProfileEntity>> = repository.wirelessProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _folderStack = MutableStateFlow(listOf(FolderBreadcrumb(null, "Vault")))
    val folderStack: StateFlow<List<FolderBreadcrumb>> = _folderStack.asStateFlow()

    val currentFolderItems: StateFlow<List<VaultItemEntity>> = _folderStack
        .flatMapLatest { stack ->
            repository.observeVaultItems(stack.lastOrNull()?.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _apkMetaCache = MutableStateFlow<Map<Long, ApkMetadata>>(emptyMap())
    val apkMetaCache: StateFlow<Map<Long, ApkMetadata>> = _apkMetaCache.asStateFlow()

    private val _runningSessions = MutableStateFlow<List<RunningVmSession>>(emptyList())
    val runningSessions: StateFlow<List<RunningVmSession>> = _runningSessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _splitViewEnabled = MutableStateFlow(false)
    val splitViewEnabled: StateFlow<Boolean> = _splitViewEnabled.asStateFlow()

    private val _builderDraft = MutableStateFlow(
        ImageBuilderDraft(
            name = "My Android 10 Lite",
            androidVersionLabel = "Android 10 (Q)",
            apiLevel = 29,
            ramMb = 2048,
            storageGb = 16,
            dpi = 320,
            resolutionLabel = "720x1280"
        )
    )
    val builderDraft: StateFlow<ImageBuilderDraft> = _builderDraft.asStateFlow()

    private val _isBuildingImage = MutableStateFlow(false)
    val isBuildingImage: StateFlow<Boolean> = _isBuildingImage.asStateFlow()

    val shareServerState: StateFlow<ShareServerState> = RealtimeShareServer.state

    private val _shareMode = MutableStateFlow(ShareStreamMode.VM_VIEWPORT)
    val shareMode: StateFlow<ShareStreamMode> = _shareMode.asStateFlow()

    private val _jpegQuality = MutableStateFlow(72)
    val jpegQuality: StateFlow<Int> = _jpegQuality.asStateFlow()

    private val _hostTelemetry = MutableStateFlow(WirelessRootBridge.getHostTelemetry(application))
    val hostTelemetry: StateFlow<HostDeviceTelemetry> = _hostTelemetry.asStateFlow()

    private val _shellHistory = MutableStateFlow<List<ShellOutput>>(
        listOf(
            ShellOutput(
                command = "litedirt --status",
                output = "LiteDirt Bridge Ready | Android 10 (Q · API 29) Lightweight Engine | Wireless Shell Ready",
                exitCode = 0
            )
        )
    )
    val shellHistory: StateFlow<List<ShellOutput>> = _shellHistory.asStateFlow()

    private val _useRootShell = MutableStateFlow(false)
    val useRootShell: StateFlow<Boolean> = _useRootShell.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var vmViewportStreamJob: Job? = null

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
        }
        // Automatically boot first VM when available so workspace is immediately interactive
        viewModelScope.launch {
            vmImages.collect { images ->
                if (images.isNotEmpty() && _runningSessions.value.isEmpty()) {
                    bootVm(images.first())
                }
            }
        }
        // Inspect APKs lazily as vault changes
        viewModelScope.launch {
            allApks.collect { apks ->
                apks.forEach { item ->
                    if (!_apkMetaCache.value.containsKey(item.id)) {
                        val meta = withContext(Dispatchers.IO) {
                            ApkInspector.inspectApkFile(getApplication(), repository.getVaultFile(item))
                        }
                        if (meta != null) {
                            _apkMetaCache.update { it + (item.id to meta) }
                        }
                    }
                }
            }
        }
        // Live VM telemetry ticker
        viewModelScope.launch {
            while (isActive) {
                delay(1800L)
                _runningSessions.update { list ->
                    list.map { session ->
                        val baseCpu = if (session.currentView == VmAppView.APP_WINDOW) 26 else 12
                        val jitter = ((System.currentTimeMillis() / 1000) % 9).toInt()
                        val ramRatio = if (session.currentView == VmAppView.APP_WINDOW) 0.42 else 0.28
                        session.copy(
                            cpuLoadPercent = (baseCpu + jitter).coerceIn(5, 92),
                            ramUsedMb = (session.image.ramMb * ramRatio).toInt() + jitter * 14,
                            fps = if (session.image.apiLevel >= 33) 60 else 59
                        )
                    }
                }
            }
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    // --- VM Multitask Workspace ---
    fun bootVm(image: VmImageEntity) {
        val existing = _runningSessions.value.firstOrNull { it.image.id == image.id }
        if (existing != null) {
            _activeSessionId.value = existing.sessionId
            return
        }
        val sessionId = "vm_${image.id}_${System.currentTimeMillis()}"
        val session = RunningVmSession(
            sessionId = sessionId,
            image = image,
            ramUsedMb = (image.ramMb * 0.28).toInt(),
            isBooting = true,
            bootStage = 0
        )
        _runningSessions.update { it + session }
        _activeSessionId.value = sessionId
        runBootAnimationSequence(sessionId)
        _statusMessage.value = "Powering on ${image.name} (${image.bootBrand})..."
    }

    fun cloneOrBootNewInstance(image: VmImageEntity) {
        val sessionId = "vm_${image.id}_${System.currentTimeMillis()}"
        val session = RunningVmSession(
            sessionId = sessionId,
            image = image,
            ramUsedMb = (image.ramMb * 0.28).toInt(),
            isBooting = true,
            bootStage = 0
        )
        _runningSessions.update { it + session }
        _activeSessionId.value = sessionId
        runBootAnimationSequence(sessionId)
        _statusMessage.value = "Powering on parallel instance: ${image.name}"
    }

    fun rebootSession(sessionId: String) {
        _runningSessions.update { list ->
            list.map {
                if (it.sessionId == sessionId) {
                    it.copy(isBooting = true, bootStage = 0, currentView = VmAppView.HOME)
                } else it
            }
        }
        runBootAnimationSequence(sessionId)
        _statusMessage.value = "Restarting VM..."
    }

    private fun runBootAnimationSequence(sessionId: String) {
        viewModelScope.launch {
            delay(750L)
            _runningSessions.update { list ->
                list.map { if (it.sessionId == sessionId) it.copy(bootStage = 1) else it }
            }
            delay(1750L)
            _runningSessions.update { list ->
                list.map {
                    if (it.sessionId == sessionId) it.copy(isBooting = false, bootStage = 2) else it
                }
            }
        }
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun closeSession(sessionId: String) {
        _runningSessions.update { list -> list.filterNot { it.sessionId == sessionId } }
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = _runningSessions.value.firstOrNull()?.sessionId
        }
        if (_runningSessions.value.size < 2) {
            _splitViewEnabled.value = false
        }
    }

    fun toggleSplitView() {
        if (_runningSessions.value.size < 2) {
            // Boot a second VM automatically if available for effortless multitask demo
            val activeImgId = _runningSessions.value.firstOrNull()?.image?.id
            val second = vmImages.value.firstOrNull { it.id != activeImgId } ?: vmImages.value.firstOrNull()
            if (second != null) {
                cloneOrBootNewInstance(second)
                _splitViewEnabled.value = true
            } else {
                _statusMessage.value = "Create or keep 2 VMs to enable split multitask"
            }
        } else {
            _splitViewEnabled.update { !it }
        }
    }

    fun setSessionView(sessionId: String, view: VmAppView, apk: VaultItemEntity? = null) {
        _runningSessions.update { list ->
            list.map { s ->
                if (s.sessionId == sessionId) {
                    s.copy(currentView = view, activeApk = apk ?: s.activeApk)
                } else s
            }
        }
    }

    fun launchApkInActiveVm(apk: VaultItemEntity) {
        val activeId = _activeSessionId.value ?: _runningSessions.value.firstOrNull()?.sessionId
        if (activeId == null) {
            val firstImg = vmImages.value.firstOrNull()
            if (firstImg != null) {
                bootVm(firstImg)
                val newId = _activeSessionId.value
                if (newId != null) {
                    setSessionView(newId, VmAppView.APP_WINDOW, apk)
                }
            }
            return
        }
        setSessionView(activeId, VmAppView.APP_WINDOW, apk)
        _statusMessage.value = "Loaded ${apk.name} inside active VM"
    }

    fun openBrowserInActiveVm() {
        val activeId = _activeSessionId.value ?: _runningSessions.value.firstOrNull()?.sessionId
        if (activeId == null) {
            val firstImg = vmImages.value.firstOrNull() ?: return
            bootVm(firstImg)
            _activeSessionId.value?.let { setSessionView(it, VmAppView.VM_BROWSER) }
        } else {
            setSessionView(activeId, VmAppView.VM_BROWSER)
        }
    }

    fun openFilesInActiveVm() {
        val activeId = _activeSessionId.value ?: _runningSessions.value.firstOrNull()?.sessionId
        if (activeId == null) {
            val firstImg = vmImages.value.firstOrNull() ?: return
            bootVm(firstImg)
            _activeSessionId.value?.let { setSessionView(it, VmAppView.VM_FILES) }
        } else {
            setSessionView(activeId, VmAppView.VM_FILES)
        }
    }

    fun downloadBrowserFile(url: String, contentDisposition: String?, mimeType: String?) {
        viewModelScope.launch {
            _statusMessage.value = "Downloading into LiteDirt Vault..."
            val downloaded = repository.downloadFromUrlToVault(url, contentDisposition, mimeType)
            _statusMessage.value = if (downloaded != null) {
                if (downloaded.isApk) {
                    "Downloaded APK: ${downloaded.name} (ready in Vault)"
                } else {
                    "Downloaded file: ${downloaded.name}"
                }
            } else {
                "Download failed or blocked by host"
            }
        }
    }

    // --- Vault & Folder Operations ---
    fun openFolder(folder: VaultItemEntity) {
        if (!folder.isFolder) return
        _folderStack.update { it + FolderBreadcrumb(folder.id, folder.name) }
    }

    fun navigateToBreadcrumb(index: Int) {
        _folderStack.update { stack ->
            if (index in stack.indices) stack.subList(0, index + 1) else stack
        }
    }

    fun navigateFolderUp(): Boolean {
        val current = _folderStack.value
        return if (current.size > 1) {
            _folderStack.value = current.dropLast(1)
            true
        } else {
            false
        }
    }

    fun createFolder(name: String) {
        createFolderInParent(name, _folderStack.value.lastOrNull()?.id)
    }

    fun createFolderInParent(name: String, parentId: Long?) {
        viewModelScope.launch {
            val created = repository.createFolder(name, parentId)
            _statusMessage.value = if (created != null) {
                "Created folder '${created.name}'"
            } else {
                "Enter a valid folder name"
            }
        }
    }

    fun importDocuments(uris: List<Uri>) {
        importDocumentsToFolder(uris, _folderStack.value.lastOrNull()?.id)
    }

    fun importDocumentsToFolder(uris: List<Uri>, parentId: Long?) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importDocuments(uris, parentId)
            _statusMessage.value = if (imported.isNotEmpty()) {
                val apkCount = imported.count { it.isApk }
                if (apkCount > 0) {
                    "Imported ${imported.size} file(s) ($apkCount APK inspected)"
                } else {
                    "Imported ${imported.size} file(s) into container"
                }
            } else {
                "No files could be imported"
            }
        }
    }

    fun renameVaultItem(item: VaultItemEntity, newName: String) {
        viewModelScope.launch {
            repository.renameVaultItem(item, newName)
            _statusMessage.value = "Renamed to $newName"
        }
    }

    fun moveVaultItem(item: VaultItemEntity, targetFolderId: Long?) {
        viewModelScope.launch {
            val moved = repository.moveVaultItem(item, targetFolderId)
            _statusMessage.value = if (moved) {
                "Moved '${item.name}'"
            } else {
                "Unable to move '${item.name}' to selected folder"
            }
        }
    }

    fun deleteVaultItem(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.deleteVaultItem(item)
            _statusMessage.value = "Deleted ${item.name}"
        }
    }

    fun buildShareVaultIntent(item: VaultItemEntity): Intent? =
        repository.buildShareVaultFileIntent(item)

    // --- Custom Image Builder ("Image bikin sendiri") ---
    fun updateBuilderDraft(transform: (ImageBuilderDraft) -> ImageBuilderDraft) {
        _builderDraft.update(transform)
    }

    fun buildCustomImage(onBuilt: (VmImageEntity) -> Unit = {}) {
        val draft = _builderDraft.value
        if (draft.name.isBlank()) {
            _statusMessage.value = "Please enter a name for your custom image"
            return
        }
        viewModelScope.launch {
            _isBuildingImage.value = true
            val created = repository.buildCustomImage(
                CustomImageSpec(
                    name = draft.name,
                    androidVersionLabel = draft.androidVersionLabel,
                    apiLevel = draft.apiLevel,
                    abi = draft.abi,
                    ramMb = draft.ramMb,
                    storageGb = draft.storageGb,
                    dpi = draft.dpi,
                    resolutionLabel = draft.resolutionLabel,
                    wirelessRootEnabled = draft.wirelessRootEnabled,
                    wallpaperPreset = draft.wallpaperPreset,
                    bootBrand = draft.bootBrand,
                    customWallpaperUri = draft.customWallpaperUri,
                    bundledVaultItemIds = draft.selectedVaultIds.toList()
                )
            )
            _isBuildingImage.value = false
            _statusMessage.value = "Built custom image '${created.name}' (.dvmimg/.zip)"
            onBuilt(created)
        }
    }

    fun deleteVmImage(image: VmImageEntity) {
        viewModelScope.launch {
            _runningSessions.value.filter { it.image.id == image.id }.forEach {
                closeSession(it.sessionId)
            }
            repository.deleteVmImage(image)
            _statusMessage.value = "Removed image ${image.name}"
        }
    }

    fun exportImageToUri(image: VmImageEntity, destUri: Uri) {
        viewModelScope.launch {
            val ok = repository.exportArchiveToUri(image, destUri)
            _statusMessage.value = if (ok) {
                "Exported ${image.name} image archive"
            } else {
                "No exportable archive for starter preset (build a custom image to export)"
            }
        }
    }

    // --- Realtime Screen Share ---
    fun setShareMode(mode: ShareStreamMode) {
        _shareMode.value = mode
    }

    fun updateJpegQuality(quality: Int) {
        val clamped = quality.coerceIn(30, 95)
        _jpegQuality.value = clamped
        if (shareServerState.value.isRunning && _shareMode.value == ShareStreamMode.DEVICE_MEDIA_PROJECTION) {
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, ScreenShareService::class.java).apply {
                action = ScreenShareService.ACTION_UPDATE_QUALITY
                putExtra(ScreenShareService.EXTRA_QUALITY, clamped)
            }
            ctx.startService(intent)
        }
    }

    fun startVmViewportStream() {
        stopAllScreenSharing()
        val ctx = getApplication<Application>()
        RealtimeShareServer.start(ctx, modeLabel = "VM Display Stream")
        vmViewportStreamJob = viewModelScope.launch(Dispatchers.Default) {
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            while (isActive && RealtimeShareServer.state.value.isRunning) {
                val active = _runningSessions.value.firstOrNull { it.sessionId == _activeSessionId.value }
                    ?: _runningSessions.value.firstOrNull()
                val jpeg = renderVmViewportFrame(active, timeFmt.format(Date()), _jpegQuality.value)
                RealtimeShareServer.updateFrame(jpeg)
                delay(120L) // ~8-10 FPS crisp VM stream
            }
        }
        _statusMessage.value = "Realtime VM stream started"
    }

    fun startMediaProjectionShare(resultCode: Int, data: Intent) {
        vmViewportStreamJob?.cancel()
        vmViewportStreamJob = null
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_START
            putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenShareService.EXTRA_DATA_INTENT, data)
            putExtra(ScreenShareService.EXTRA_QUALITY, _jpegQuality.value)
        }
        ContextCompat.startForegroundService(ctx, intent)
        _statusMessage.value = "Realtime full screen sharing started"
    }

    fun stopAllScreenSharing() {
        vmViewportStreamJob?.cancel()
        vmViewportStreamJob = null
        val ctx = getApplication<Application>()
        val stopIntent = Intent(ctx, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_STOP
        }
        ctx.startService(stopIntent)
        RealtimeShareServer.stop()
    }

    fun toggleQuickVmShare() {
        if (shareServerState.value.isRunning) {
            stopAllScreenSharing()
            _statusMessage.value = "Realtime share stopped"
        } else {
            _shareMode.value = ShareStreamMode.VM_VIEWPORT
            startVmViewportStream()
        }
    }

    private fun renderVmViewportFrame(
        session: RunningVmSession?,
        clockText: String,
        quality: Int
    ): ByteArray {
        val w = 540
        val h = 960
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (session?.isBooting == true) {
            canvas.drawColor(Color.rgb(3, 5, 10))
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val subBootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 229, 255)
                textSize = 20f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(session.image.bootBrand.uppercase(), w / 2f, h / 2f - 10f, brandPaint)
            canvas.drawText(
                if (session.bootStage == 0) "BOOTLOADER STARTING..." else "POWERED BY ANDROID 10",
                w / 2f,
                h - 110f,
                subBootPaint
            )
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            bmp.recycle()
            return baos.toByteArray()
        }

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(
                    Color.rgb(9, 13, 22),
                    Color.rgb(12, 34, 54),
                    Color.rgb(11, 20, 38)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val gridPaint = Paint().apply {
            color = Color.argb(30, 0, 229, 255)
            strokeWidth = 1.5f
        }
        for (x in 0..w step 45) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), gridPaint)
        for (y in 0..h step 45) canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), gridPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(241, 245, 249)
            textSize = 26f
            isFakeBoldText = true
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            textSize = 20f
        }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 17, 24, 39)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.rgb(0, 229, 255)
        }

        // Top bar
        canvas.drawText(clockText, 28f, 44f, textPaint)
        canvas.drawText(
            session?.image?.androidVersionLabel ?: "DroidVM Standby",
            175f,
            44f,
            subPaint
        )

        // Main VM status card
        val rect = RectF(28f, 90f, w - 28f, 340f)
        canvas.drawRoundRect(rect, 24f, 24f, cardPaint)
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)

        canvas.drawText(session?.image?.name ?: "No Active VM", 52f, 145f, textPaint)
        canvas.drawText(
            "ABI: ${session?.image?.abi ?: "arm64-v8a"}  |  API ${session?.image?.apiLevel ?: 29}",
            52f,
            190f,
            subPaint
        )
        val rootText = if (session?.image?.wirelessRootEnabled == true) "WIRELESS ROOT: ON" else "STANDARD SANDBOX"
        subPaint.color = if (session?.image?.wirelessRootEnabled == true) Color.rgb(16, 185, 129) else Color.rgb(148, 163, 184)
        canvas.drawText(rootText, 52f, 232f, subPaint)
        subPaint.color = Color.rgb(241, 245, 249)
        canvas.drawText(
            "CPU ${session?.cpuLoadPercent ?: 0}%  •  RAM ${session?.ramUsedMb ?: 0} MB  •  ${session?.fps ?: 60} FPS",
            52f,
            280f,
            subPaint
        )

        // Active app or desktop panel
        val appRect = RectF(28f, 375f, w - 28f, 840f)
        canvas.drawRoundRect(appRect, 24f, 24f, cardPaint)
        subPaint.color = Color.rgb(0, 229, 255)
        val viewTitle = when (session?.currentView) {
            VmAppView.APP_WINDOW -> "Running App: ${session.activeApk?.name ?: "Sandbox App"}"
            VmAppView.VM_BROWSER -> "LiteDirt Built-in Browser"
            VmAppView.VM_FILES -> "VM Explorer (/sdcard/LiteDirt)"
            VmAppView.VM_TERMINAL -> "VM Root Wireless Terminal"
            VmAppView.VM_SETTINGS -> "VM System Properties"
            else -> "Virtual Desktop (${allApks.value.size} Imported APKs)"
        }
        canvas.drawText(viewTitle, 52f, 430f, textPaint)
        if (session?.activeApk != null && session.currentView == VmAppView.APP_WINDOW) {
            canvas.drawText("Pkg: ${session.activeApk.apkPackageName ?: "unknown"}", 52f, 480f, subPaint)
            canvas.drawText("Ver: ${session.activeApk.apkVersionName ?: "1.0"}", 52f, 520f, subPaint)
        } else {
            allApks.value.take(5).forEachIndexed { idx, apk ->
                canvas.drawText("• ${apk.name}", 52f, 485f + idx * 44f, subPaint)
            }
        }

        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        bmp.recycle()
        return baos.toByteArray()
    }

    // --- Wireless Root & Terminal ---
    fun refreshHostTelemetry() {
        _hostTelemetry.value = WirelessRootBridge.getHostTelemetry(getApplication())
    }

    fun addWirelessProfile(label: String, hostIp: String, port: Int, pairingPort: Int?, isRoot: Boolean) {
        viewModelScope.launch {
            repository.addWirelessProfile(label, hostIp, port, pairingPort, isRoot)
            _statusMessage.value = "Added wireless endpoint $hostIp:$port"
        }
    }

    fun testWirelessProfile(profile: WirelessProfileEntity) {
        viewModelScope.launch {
            val (ok, msg) = WirelessRootBridge.testTcpEndpoint(profile.hostIp, profile.port)
            repository.updateWirelessProfile(
                profile.copy(
                    lastStatus = msg,
                    lastCheckedAt = System.currentTimeMillis()
                )
            )
            _statusMessage.value = "${profile.hostIp}:${profile.port} → $msg"
        }
    }

    fun deleteWirelessProfile(id: Long) {
        viewModelScope.launch {
            repository.deleteWirelessProfile(id)
        }
    }

    fun toggleUseRootShell(enabled: Boolean) {
        _useRootShell.value = enabled
    }

    fun executeShellCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            val result = WirelessRootBridge.runShellCommand(command, _useRootShell.value)
            _shellHistory.update { (it + result).takeLast(30) }
        }
    }

    fun clearShellHistory() {
        _shellHistory.value = emptyList()
    }

    fun openDeveloperSettings() {
        val ok = WirelessRootBridge.openDeveloperSettings(getApplication())
        if (!ok) {
            _statusMessage.value = "Unable to open Developer Options on this device"
        }
    }

    override fun onCleared() {
        vmViewportStreamJob?.cancel()
        super.onCleared()
    }
}
