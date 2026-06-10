package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "voice_logs")
data class VoiceLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val textSent: String,
    val responseReceived: String?,
    val status: String, // "SUCCESS", "ERROR", "CONNECTING"
    val errorDetails: String? = null
)

@Dao
interface VoiceLogDao {
    @Query("SELECT * FROM voice_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<VoiceLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VoiceLog): Long

    @Query("DELETE FROM voice_logs")
    suspend fun clearLogs()
}
