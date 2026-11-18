package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DroidVmDao {
    // VM Images
    @Query("SELECT * FROM vm_images ORDER BY isCustomBuilt DESC, createdAt DESC")
    fun observeVmImages(): Flow<List<VmImageEntity>>

    @Query("SELECT COUNT(*) FROM vm_images")
    suspend fun getVmImageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVmImage(image: VmImageEntity): Long

    @Update
    suspend fun updateVmImage(image: VmImageEntity)

    @Query("DELETE FROM vm_images WHERE id = :id")
    suspend fun deleteVmImageById(id: Long)

    @Query("DELETE FROM vm_images WHERE isCustomBuilt = 0 AND apiLevel != 29")
    suspend fun deleteNonAndroid10BuiltInImages()

    // Vault Items (APKs, Files, Folders)
    @Query("SELECT * FROM vault_items WHERE (:parentId IS NULL AND parentFolderId IS NULL) OR parentFolderId = :parentId ORDER BY isFolder DESC, importedAt DESC")
    fun observeVaultItemsInFolder(parentId: Long?): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isApk = 1 ORDER BY importedAt DESC")
    fun observeAllApks(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items ORDER BY isFolder DESC, name ASC")
    fun observeAllVaultItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE parentFolderId = :folderId")
    suspend fun getChildrenDirect(folderId: Long): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getVaultItemById(id: Long): VaultItemEntity?

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun getVaultItemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItemEntity): Long

    @Update
    suspend fun updateVaultItem(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItemById(id: Long)

    // Wireless Root Profiles
    @Query("SELECT * FROM wireless_profiles ORDER BY lastCheckedAt DESC")
    fun observeWirelessProfiles(): Flow<List<WirelessProfileEntity>>

    @Query("SELECT COUNT(*) FROM wireless_profiles")
    suspend fun getWirelessProfileCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWirelessProfile(profile: WirelessProfileEntity): Long

    @Update
    suspend fun updateWirelessProfile(profile: WirelessProfileEntity)

    @Query("DELETE FROM wireless_profiles WHERE id = :id")
    suspend fun deleteWirelessProfileById(id: Long)
}
