package com.samsung.aiassistant.data.database

import androidx.room.*
import com.samsung.aiassistant.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntry>>
    
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversations(limit: Int = 50): List<ConversationEntry>
    
    @Insert
    suspend fun insert(conversation: ConversationEntry): Long
    
    @Query("DELETE FROM conversations WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("SELECT * FROM conversations WHERE userInput LIKE '%' || :query || '%' OR assistantResponse LIKE '%' || :query || '%'")
    suspend fun searchConversations(query: String): List<ConversationEntry>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY importance DESC, lastAccessed DESC")
    fun getAllMemories(): Flow<List<MemoryEntry>>
    
    @Query("SELECT * FROM memory_entries WHERE key = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): MemoryEntry?
    
    @Query("SELECT * FROM memory_entries WHERE category = :category ORDER BY importance DESC")
    suspend fun getMemoriesByCategory(category: String): List<MemoryEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntry): Long
    
    @Update
    suspend fun update(memory: MemoryEntry)
    
    @Delete
    suspend fun delete(memory: MemoryEntry)
    
    @Query("UPDATE memory_entries SET lastAccessed = :timestamp, accessCount = accessCount + 1 WHERE id = :id")
    suspend fun updateAccess(id: Long, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface LearnedPatternDao {
    @Query("SELECT * FROM learned_patterns ORDER BY frequency DESC, successRate DESC")
    fun getAllPatterns(): Flow<List<LearnedPattern>>
    
    @Query("SELECT * FROM learned_patterns WHERE pattern LIKE '%' || :query || '%' ORDER BY frequency DESC LIMIT 10")
    suspend fun findMatchingPatterns(query: String): List<LearnedPattern>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: LearnedPattern): Long
    
    @Update
    suspend fun update(pattern: LearnedPattern)
    
    @Query("UPDATE learned_patterns SET frequency = frequency + 1, lastUsed = :timestamp WHERE id = :id")
    suspend fun incrementFrequency(id: Long, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface UserPreferenceDao {
    @Query("SELECT * FROM user_preferences")
    fun getAllPreferences(): Flow<List<UserPreference>>
    
    @Query("SELECT * FROM user_preferences WHERE key = :key LIMIT 1")
    suspend fun getPreference(key: String): UserPreference?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: UserPreference)
    
    @Delete
    suspend fun delete(preference: UserPreference)
}

@Dao
interface FileOperationDao {
    @Query("SELECT * FROM file_operations ORDER BY timestamp DESC LIMIT 100")
    fun getRecentOperations(): Flow<List<FileOperation>>
    
    @Insert
    suspend fun insert(operation: FileOperation): Long
    
    @Query("DELETE FROM file_operations WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
