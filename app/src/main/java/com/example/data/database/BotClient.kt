package com.example.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bot_clients")
data class BotClient(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val telegram: String,
    val balance: Double,
    val equity: Double,
    val drawdown: Double,
    val activeMode: String = "MODE_BOTH", // MODE_BOTH, MODE_BUY_ONLY, MODE_SELL_ONLY
    val expiryTimestamp: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000), // Default 30 Days license
    val isLicenseActive: Boolean = true,
    val lastActiveTime: Long = System.currentTimeMillis(),
    
    // Remote parameters of the GOGOR V12.24 Bot
    val initialLot: Double = 0.01,
    val lotStep: Double = 0.01,
    val stepPoints: Int = 1111,
    val maxSpread: Int = 500,
    val recoveryThreshold: Int = 5,
    val expMultiplier: Double = 2.0
)

@Dao
interface BotClientDao {
    @Query("SELECT * FROM bot_clients ORDER BY id DESC")
    fun getAllClients(): Flow<List<BotClient>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: BotClient)

    @Update
    suspend fun updateClient(client: BotClient)

    @Delete
    suspend fun deleteClient(client: BotClient)
    
    @Query("SELECT * FROM bot_clients WHERE id = :id")
    suspend fun getClientById(id: Int): BotClient?
}

@Database(entities = [BotClient::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun botClientDao(): BotClientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gogor_manager_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
