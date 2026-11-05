package com.cortex.assistant

import android.app.Application
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages ORDER BY createdAt ASC") fun observeAll(): Flow<List<ChatMessage>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(message: ChatMessage)
    @Query("DELETE FROM messages") suspend fun clear()
}

@Database(entities = [ChatMessage::class], version = 1, exportSchema = false)
abstract class CortexDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}

class CortexApplication : Application() {
    val database: CortexDatabase by lazy {
        Room.databaseBuilder(applicationContext, CortexDatabase::class.java, "cortex-local.db").build()
    }
}
