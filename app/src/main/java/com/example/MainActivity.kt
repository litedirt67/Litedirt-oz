package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.DroidVmViewModel
import com.example.ui.screens.ImageBuilderScreen
import com.example.ui.screens.ShareAndRootScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.VmWorkspaceScreen
import com.example.ui.theme.DroidVmTheme

enum class DroidVmDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    VMS("vms", R.string.nav_vms, Icons.Filled.Layers, Icons.Outlined.Layers, "nav_vms"),
    VAULT("vault", R.string.nav_vault, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_vault"),
    BUILDER("builder", R.string.nav_builder, Icons.Filled.Build, Icons.Outlined.Build, "nav_builder"),
    SHARE("share", R.string.nav_share, Icons.Filled.Cast, Icons.Outlined.Cast, "nav_share")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidVmTheme {
                DroidVmApp()
            }
        }
    }
}

@Composable
fun DroidVmApp(viewModel: DroidVmViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: DroidVmDestination.VMS.route

    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    val vmImages by viewModel.vmImages.collectAsStateWithLifecycle()
    val runningSessions by viewModel.runningSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val splitViewEnabled by viewModel.splitViewEnabled.collectAsStateWithLifecycle()
    val allApks by viewModel.allApks.collectAsStateWithLifecycle()
    val allVaultItems by viewModel.allVaultItems.collectAsStateWithLifecycle()
    val folderStack by viewModel.folderStack.collectAsStateWithLifecycle()
    val currentFolderItems by viewModel.currentFolderItems.collectAsStateWithLifecycle()
    val apkMetaCache by viewModel.apkMetaCache.collectAsStateWithLifecycle()
    val builderDraft by viewModel.builderDraft.collectAsStateWithLifecycle()
    val isBuildingImage by viewModel.isBuildingImage.collectAsStateWithLifecycle()
    val shareState by viewModel.shareServerState.collectAsStateWithLifecycle()
    val shareMode by viewModel.shareMode.collectAsStateWithLifecycle()
    val jpegQuality by viewModel.jpegQuality.collectAsStateWithLifecycle()
    val hostTelemetry by viewModel.hostTelemetry.collectAsStateWithLifecycle()
    val wirelessProfiles by viewModel.wirelessProfiles.collectAsStateWithLifecycle()
    val shellHistory by viewModel.shellHistory.collectAsStateWithLifecycle()
    val useRootShell by viewModel.useRootShell.collectAsStateWithLifecycle()

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(DroidVmDestination.VMS.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DroidVmDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DroidVmDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = DroidVmDestination.VMS.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(DroidVmDestination.VMS.route) {
                        VmWorkspaceScreen(
                            vmImages = vmImages,
                            runningSessions = runningSessions,
                            activeSessionId = activeSessionId,
                            splitViewEnabled = splitViewEnabled,
                            isSharingLive = shareState.isRunning,
                            apks = allApks,
                            allVaultItems = allVaultItems,
                            apkMetaCache = apkMetaCache,
                            onBootVm = { viewModel.bootVm(it) },
                            onSelectSession = { viewModel.selectSession(it) },
                            onCloseSession = { viewModel.closeSession(it) },
                            onToggleSplitView = { viewModel.toggleSplitView() },
                            onSetSessionView = { sid, v, apk -> viewModel.setSessionView(sid, v, apk) },
                            onRebootSession = { viewModel.rebootSession(it) },
                            onQuickImportDocuments = { viewModel.importDocuments(it) },
                            onQuickToggleShare = { viewModel.toggleQuickVmShare() },
                            onOpenBrowser = { viewModel.openBrowserInActiveVm() },
                            onOpenVmFiles = { viewModel.openFilesInActiveVm() },
                            onDownloadFromBrowser = { u, cd, m -> viewModel.downloadBrowserFile(u, cd, m) },
                            onCreateFolderInParent = { name, parentId -> viewModel.createFolderInParent(name, parentId) },
                            onImportDocumentsToFolder = { uris, parentId -> viewModel.importDocumentsToFolder(uris, parentId) },
                            onRenameVaultItem = { item, newName -> viewModel.renameVaultItem(item, newName) },
                            onMoveVaultItem = { item, targetFolderId -> viewModel.moveVaultItem(item, targetFolderId) },
                            onDeleteVaultItem = { viewModel.deleteVaultItem(it) },
                            onBuildShareIntent = { viewModel.buildShareVaultIntent(it) },
                            onDeleteVmImage = { viewModel.deleteVmImage(it) },
                            onOpenVaultTab = { navigateTo(DroidVmDestination.VAULT.route) },
                            onOpenBuilderTab = { navigateTo(DroidVmDestination.BUILDER.route) },
                            onOpenShareTab = { navigateTo(DroidVmDestination.SHARE.route) }
                        )
                    }

                    composable(DroidVmDestination.VAULT.route) {
                        VaultScreen(
                            breadcrumbs = folderStack,
                            items = currentFolderItems,
                            apkMetaCache = apkMetaCache,
                            onOpenFolder = { viewModel.openFolder(it) },
                            onNavigateBreadcrumb = { viewModel.navigateToBreadcrumb(it) },
                            onNavigateUp = { viewModel.navigateFolderUp() },
                            onCreateFolder = { viewModel.createFolder(it) },
                            onImportDocuments = { viewModel.importDocuments(it) },
                            onRenameItem = { item, name -> viewModel.renameVaultItem(item, name) },
                            onDeleteItem = { viewModel.deleteVaultItem(it) },
                            onLaunchApkInVm = { apk ->
                                viewModel.launchApkInActiveVm(apk)
                                navigateTo(DroidVmDestination.VMS.route)
                            },
                            onBuildShareIntent = { viewModel.buildShareVaultIntent(it) }
                        )
                    }

                    composable(DroidVmDestination.BUILDER.route) {
                        ImageBuilderScreen(
                            draft = builderDraft,
                            isBuilding = isBuildingImage,
                            vaultItems = allVaultItems,
                            builtImages = vmImages,
                            onUpdateDraft = { viewModel.updateBuilderDraft(it) },
                            onBuildCustomImage = { onDone ->
                                viewModel.buildCustomImage { created ->
                                    onDone(created)
                                    navigateTo(DroidVmDestination.VMS.route)
                                }
                            },
                            onBootVm = { img ->
                                viewModel.bootVm(img)
                                navigateTo(DroidVmDestination.VMS.route)
                            },
                            onExportImage = { img, uri -> viewModel.exportImageToUri(img, uri) }
                        )
                    }

                    composable(DroidVmDestination.SHARE.route) {
                        ShareAndRootScreen(
                            shareState = shareState,
                            shareMode = shareMode,
                            jpegQuality = jpegQuality,
                            hostTelemetry = hostTelemetry,
                            wirelessProfiles = wirelessProfiles,
                            shellHistory = shellHistory,
                            useRootShell = useRootShell,
                            onSetShareMode = { viewModel.setShareMode(it) },
                            onUpdateJpegQuality = { viewModel.updateJpegQuality(it) },
                            onStartVmViewportStream = { viewModel.startVmViewportStream() },
                            onStartMediaProjection = { code, data -> viewModel.startMediaProjectionShare(code, data) },
                            onStopSharing = { viewModel.stopAllScreenSharing() },
                            onRefreshTelemetry = { viewModel.refreshHostTelemetry() },
                            onAddWirelessProfile = { l, ip, p, pp, r ->
                                viewModel.addWirelessProfile(l, ip, p, pp, r)
                            },
                            onTestWirelessProfile = { viewModel.testWirelessProfile(it) },
                            onDeleteWirelessProfile = { viewModel.deleteWirelessProfile(it) },
                            onToggleUseRootShell = { viewModel.toggleUseRootShell(it) },
                            onRunShellCommand = { viewModel.executeShellCommand(it) },
                            onClearShell = { viewModel.clearShellHistory() },
                            onOpenDeveloperSettings = { viewModel.openDeveloperSettings() }
                        )
                    }
                }
            }
        }
    }
}
