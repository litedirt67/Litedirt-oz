package com.example.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

data class ApkMetadata(
    val appLabel: String,
    val packageName: String,
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val icon: ImageBitmap?
) {
    val androidSupportSummary: String
        get() {
            val minLabel = sdkToAndroidName(minSdk)
            return if (minSdk <= 29) {
                "Android 10 VM Compatible (min $minLabel)"
            } else {
                "Needs $minLabel+ (above Android 10 VM)"
            }
        }

    companion object {
        fun sdkToAndroidName(sdk: Int): String = when (sdk) {
            in 1..23 -> "API $sdk"
            24, 25 -> "Android 7"
            26, 27 -> "Android 8"
            28 -> "Android 9"
            29 -> "Android 10"
            30 -> "Android 11"
            31, 32 -> "Android 12"
            33 -> "Android 13"
            34 -> "Android 14"
            35 -> "Android 15"
            36 -> "Android 16"
            else -> "API $sdk"
        }
    }
}

object ApkInspector {
    @Suppress("DEPRECATION")
    fun inspectApkFile(context: Context, apkFile: File): ApkMetadata? {
        if (!apkFile.exists() || !apkFile.isFile) return null
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
        } catch (_: Exception) {
            null
        } ?: return null

        val appInfo = pkgInfo.applicationInfo
        if (appInfo != null) {
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath
        }

        val label = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
            ?: apkFile.nameWithoutExtension
        val iconDrawable = try {
            appInfo?.loadIcon(pm)
        } catch (_: Exception) {
            null
        }
        val iconBitmap = iconDrawable?.let { drawableToBitmap(it)?.asImageBitmap() }
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo?.minSdkVersion ?: 24
        } else {
            24
        }
        val targetSdk = appInfo?.targetSdkVersion ?: 34
        val perms = pkgInfo.requestedPermissions?.toList() ?: emptyList()

        return ApkMetadata(
            appLabel = label,
            packageName = pkgInfo.packageName ?: "unknown.package",
            versionName = pkgInfo.versionName ?: "1.0",
            minSdk = minSdk,
            targetSdk = targetSdk,
            permissions = perms,
            icon = iconBitmap
        )
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(96)
        val height = drawable.intrinsicHeight.coerceAtLeast(96)
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
