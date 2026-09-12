package com.caboperations.driver.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.caboperations.driver.sync.SyncPolicy

@Dao interface PendingTransactionDao {
 @Insert suspend fun insert(transaction: PendingTransaction)
 @Query("SELECT * FROM pending_transactions WHERE synced = 0 ORDER BY CASE type WHEN 'SESSION_START' THEN 0 WHEN 'TRIP_START' THEN 10 WHEN 'TRIP_END' THEN 11 WHEN 'TRIP' THEN 12 WHEN 'FUEL' THEN 12 WHEN 'EXPENSE' THEN 12 WHEN 'SESSION_CLOSE' THEN 20 WHEN 'FILE_UPLOAD' THEN 30 ELSE 40 END, createdAt LIMIT ${SyncPolicy.MAX_BATCH_SIZE}") suspend fun pending(): List<PendingTransaction>
 @Query("UPDATE pending_transactions SET synced = 1, lastError = NULL WHERE clientTransactionId = :id") suspend fun markSynced(id:String)
 @Query("UPDATE pending_transactions SET attempts = attempts + 1, lastError = :error WHERE clientTransactionId = :id") suspend fun markFailed(id:String,error:String)
 @Query("SELECT COUNT(*) FROM pending_transactions WHERE synced = 0") suspend fun pendingCount():Int
 @Query("SELECT COUNT(*) FROM pending_transactions WHERE synced = 0 AND attempts >= ${SyncPolicy.MAX_RETRY_ATTEMPTS}") suspend fun exhaustedCount():Int
 @Query("SELECT * FROM pending_transactions WHERE clientTransactionId = :id LIMIT 1") suspend fun find(id:String):PendingTransaction?
}

@Dao interface LocalSessionDao {
 @Insert fun insert(session:LocalSession)
 @Query("SELECT * FROM sessions WHERE sessionId = :sessionId LIMIT 1") suspend fun find(sessionId:String):LocalSession?
 @Query("SELECT * FROM sessions WHERE driverId = :driverId AND vehicleId = :vehicleId AND status = 'OPEN' LIMIT 1") suspend fun currentOpen(driverId:String,vehicleId:String):LocalSession?
 @Query("SELECT MAX(closeOdometer) FROM sessions WHERE vehicleId = :vehicleId") suspend fun maxClosedOdometer(vehicleId:String):Double?
 @Query("UPDATE sessions SET status = 'CLOSED', closeOdometer = :closeOdometer WHERE sessionId = :sessionId") suspend fun markClosed(sessionId:String,closeOdometer:Double)
}

@Dao interface LocalTripDao {
 @Insert suspend fun insert(trip:LocalTrip)
 @Query("SELECT * FROM trips WHERE sessionId = :sessionId AND status = 'IN_PROGRESS' LIMIT 1") suspend fun active(sessionId:String):LocalTrip?
 @Query("SELECT MAX(startOdometer) FROM trips WHERE sessionId = :sessionId") suspend fun maxStartOdometer(sessionId:String):Double?
 @Query("SELECT MAX(endOdometer) FROM trips WHERE sessionId = :sessionId") suspend fun maxEndOdometer(sessionId:String):Double?
 @Query("UPDATE trips SET endOdometer = :endOdometer, grossFare = :grossFare, status = :status WHERE clientTransactionId = :clientTransactionId") suspend fun finish(clientTransactionId:String,endOdometer:Double,grossFare:Double,status:String)
}

@Dao interface LocalFuelDao { @Insert suspend fun insert(fuel:LocalFuel); @Query("SELECT MAX(odometer) FROM fuel_transactions WHERE sessionId = :sessionId") suspend fun maxOdometer(sessionId:String):Double? }
@Dao interface LocalExpenseDao { @Insert suspend fun insert(expense:LocalExpense) }

@Database(entities=[PendingTransaction::class,LocalSession::class,LocalTrip::class,LocalFuel::class,LocalExpense::class],version=1,exportSchema=true)
abstract class CabDatabase:RoomDatabase(){
 abstract fun pendingTransactionDao():PendingTransactionDao
 abstract fun localSessionDao():LocalSessionDao
 abstract fun localTripDao():LocalTripDao
 abstract fun localFuelDao():LocalFuelDao
 abstract fun localExpenseDao():LocalExpenseDao
 companion object { @Volatile private var INSTANCE:CabDatabase?=null; fun get(context:Context):CabDatabase=INSTANCE?: synchronized(this){ INSTANCE?:Room.databaseBuilder(context.applicationContext,CabDatabase::class.java,"cab_operations.db").build().also{INSTANCE=it} } }
}
