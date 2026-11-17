package com.cognify.data.repository

import com.cognify.data.model.Resource

data class BackupMetadata(
    val lastBackupTime: Long,
    val itemsSynced: Int,
    val backupSizeBytes: Long,
    val cloudSyncStatus: String
)

interface BackupRepository {
    suspend fun performManualBackup(): Resource<BackupMetadata>
    suspend fun performManualRestore(): Resource<BackupMetadata>
    suspend fun getLastBackupMetadata(): Resource<BackupMetadata>
    
    suspend fun createLocalBackup(appVersion: String): Resource<com.cognify.data.model.CognifyBackup>
    suspend fun restoreLocalBackup(backup: com.cognify.data.model.CognifyBackup): Resource<Unit>
}
