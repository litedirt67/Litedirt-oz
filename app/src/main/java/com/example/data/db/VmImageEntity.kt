package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vm_images")
data class VmImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val androidVersionLabel: String, // e.g. "Android 10 (Q)"
    val apiLevel: Int,               // 29..36
    val abi: String,                 // "arm64-v8a" | "x86_64"
    val ramMb: Int,
    val storageGb: Int,
    val dpi: Int,
    val resolutionLabel: String,     // e.g. "1080x2400"
    val wirelessRootEnabled: Boolean,
    val wallpaperPreset: String,     // "cyber_wave" | "grid_core" | "custom"
    val bootBrand: String = "LiteDirt",
    val customWallpaperUri: String? = null,
    val imageArchivePath: String? = null,
    val bundledApkCount: Int = 0,
    val isCustomBuilt: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
