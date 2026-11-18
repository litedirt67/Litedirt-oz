package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wireless_profiles")
data class WirelessProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val hostIp: String,
    val port: Int = 5555,
    val pairingPort: Int? = null,
    val isRootBridge: Boolean = true,
    val lastStatus: String = "Standby",
    val lastCheckedAt: Long = System.currentTimeMillis()
)
