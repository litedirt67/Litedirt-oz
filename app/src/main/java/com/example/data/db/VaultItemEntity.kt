package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentFolderId: Long? = null,
    val isFolder: Boolean = false,
    val relativePath: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val isApk: Boolean = false,
    val apkPackageName: String? = null,
    val apkVersionName: String? = null,
    val apkMinSdk: Int? = null,
    val apkTargetSdk: Int? = null,
    val apkPermissionsSummary: String? = null,
    val importedAt: Long = System.currentTimeMillis()
)
