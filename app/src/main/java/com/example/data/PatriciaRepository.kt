package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow

class PatriciaRepository(private val db: AppDatabase) {
    val settingsFlow: Flow<ConnectionSettings?> = db.connectionSettingsDao().getSettingsFlow()
    val allLogsFlow: Flow<List<VoiceLog>> = db.voiceLogDao().getAllLogsFlow()

    suspend fun getSettings(): ConnectionSettings {
        return db.connectionSettingsDao().getSettings()
            ?: ConnectionSettings(password = BuildConfig.SSH_LIGHTHOUSEBEACON_PWD)
    }

    suspend fun saveSettings(settings: ConnectionSettings) {
        db.connectionSettingsDao().saveSettings(settings)
    }

    suspend fun insertLog(log: VoiceLog): Long {
        return db.voiceLogDao().insertLog(log)
    }

    suspend fun clearLogs() {
        db.voiceLogDao().clearLogs()
    }
}
