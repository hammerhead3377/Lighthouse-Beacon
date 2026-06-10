package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "connection_settings")
data class ConnectionSettings(
    @PrimaryKey val id: Int = 1,
    val host: String = "100.124.172.9",
    val username: String = "thomas",
    val port: Int = 22,
    val authType: String = "PASSWORD", // "PASSWORD" or "PRIVATE_KEY"
    val password: String = "",
    val privateKey: String = "",
    val commandTemplate: String = "/home/thomas/.local/bin/claw-agent '%s'",
    // Options
    val autoReadTts: Boolean = true,
    val continuousLoop: Boolean = false,
    val ttsLanguage: String = "en",
    val responseJsonPath: String = "result.payloads[0].text" // OR simple 'text'
)

@Dao
interface ConnectionSettingsDao {
    @Query("SELECT * FROM connection_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<ConnectionSettings?>

    @Query("SELECT * FROM connection_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): ConnectionSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ConnectionSettings)
}
