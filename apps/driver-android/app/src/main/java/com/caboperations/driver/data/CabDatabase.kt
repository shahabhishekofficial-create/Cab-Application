package com.caboperations.driver.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface PendingTransactionDao {
    @Insert suspend fun insert(transaction: PendingTransaction)
    @Query("SELECT * FROM pending_transactions WHERE synced = 0 ORDER BY createdAt")
    suspend fun pending(): List<PendingTransaction>
    @Query("UPDATE pending_transactions SET synced = 1, lastError = NULL WHERE clientTransactionId = :id")
    suspend fun markSynced(id: String)
    @Query("UPDATE pending_transactions SET attempts = attempts + 1, lastError = :error WHERE clientTransactionId = :id")
    suspend fun markFailed(id: String, error: String)
    @Query("SELECT COUNT(*) FROM pending_transactions WHERE synced = 0")
    suspend fun pendingCount(): Int
}

@Dao
interface LocalSessionDao {
    @Insert
    fun insert(session: LocalSession)

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun find(sessionId: String): LocalSession?

    @Query("SELECT * FROM sessions WHERE driverId = :driverId AND vehicleId = :vehicleId AND status = 'OPEN' LIMIT 1")
    suspend fun currentOpen(driverId: String, vehicleId: String): LocalSession?
}

@Database(
    entities = [PendingTransaction::class, LocalSession::class, LocalTrip::class, LocalFuel::class, LocalExpense::class],
    version = 1,
    exportSchema = true
)
abstract class CabDatabase : RoomDatabase() {
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun localSessionDao(): LocalSessionDao

    companion object {
        @Volatile private var INSTANCE: CabDatabase? = null

        fun get(context: Context): CabDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                CabDatabase::class.java,
                "cab_operations.db"
            ).build().also { INSTANCE = it }
        }
    }
}
