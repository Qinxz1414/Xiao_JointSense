package cloud.univ.jointsense.domain.repository

interface DataManagementRepository {
    suspend fun clearAllData()
    suspend fun restoreBuiltInSamples()
}
