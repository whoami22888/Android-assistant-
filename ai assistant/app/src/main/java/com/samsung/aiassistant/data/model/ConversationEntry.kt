package com.samsung.aiassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "conversations")
data class ConversationEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userInput: String,
    val assistantResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val inputType: InputType = InputType.TEXT,
    val context: String? = null,
    val sentiment: String? = null
)

enum class InputType {
    TEXT, VOICE, SYSTEM
}

@Entity(tableName = "memory_entries")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val value: String,
    val category: String,
    val importance: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val accessCount: Int = 0
)

@Entity(tableName = "learned_patterns")
data class LearnedPattern(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pattern: String,
    val response: String,
    val frequency: Int = 1,
    val successRate: Float = 1.0f,
    val lastUsed: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList()
)

@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey
    val key: String,
    val value: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_operations")
data class FileOperation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operation: String,
    val filePath: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null
)

class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
}
