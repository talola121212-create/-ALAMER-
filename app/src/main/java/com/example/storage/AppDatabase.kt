package com.example.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerCacheDao {
    @Query("SELECT * FROM customer_cache WHERE normalizedPhone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerCacheEntity?

    @Query("SELECT * FROM customer_cache ORDER BY cachedAtEpochMs DESC")
    fun getAllCachedCustomers(): Flow<List<CustomerCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(customer: CustomerCacheEntity)

    @Query("DELETE FROM customer_cache")
    suspend fun clearCache()
}

@Dao
interface CallEventDao {
    @Query("SELECT * FROM call_events ORDER BY createdAtUtc DESC")
    fun getAllCalls(): Flow<List<CallEventEntity>>

    @Query("SELECT * FROM call_events WHERE isSynced = 0 ORDER BY createdAtUtc ASC")
    suspend fun getPendingUnsyncedCalls(): List<CallEventEntity>

    @Query("SELECT COUNT(*) FROM call_events WHERE isSynced = 0")
    fun getPendingCallsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEventEntity)

    @Query("UPDATE call_events SET isSynced = 1 WHERE callId = :callId")
    suspend fun markAsSynced(callId: String)

    @Query("SELECT * FROM call_events WHERE callId = :callId LIMIT 1")
    suspend fun getCallById(callId: String): CallEventEntity?
}

@Database(
    entities = [CustomerCacheEntity::class, CallEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerCacheDao(): CustomerCacheDao
    abstract fun callEventDao(): CallEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alamer_call_assistant.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
