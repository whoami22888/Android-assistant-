package com.samsung.aiassistant.core

import android.content.Context
import com.samsung.aiassistant.data.database.AppDatabase
import com.samsung.aiassistant.data.model.*
import com.samsung.aiassistant.utils.NLPProcessor
import kotlinx.coroutines.*
import java.util.*

class AIEngine(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val nlpProcessor = NLPProcessor()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val conversationContext = mutableListOf<ConversationEntry>()
    private val maxContextSize = 10
    
    suspend fun processInput(input: String, inputType: InputType = InputType.TEXT): AIResponse {
        return withContext(Dispatchers.Default) {
            try {
                // Save conversation
                val conversationEntry = ConversationEntry(
                    userInput = input,
                    assistantResponse = "",
                    inputType = inputType,
                    timestamp = System.currentTimeMillis()
                )
                
                // Analyze intent
                val intent = nlpProcessor.analyzeIntent(input)
                
                // Check for learned patterns
                val learnedResponse = checkLearnedPatterns(input)
                if (learnedResponse != null) {
                    return@withContext learnedResponse
                }
                
                // Process based on intent
                val response = when (intent.type) {
                    IntentType.SEARCH -> handleSearch(input, intent)
                    IntentType.FILE_OPERATION -> handleFileOperation(input, intent)
                    IntentType.MEMORY_QUERY -> handleMemoryQuery(input, intent)
                    IntentType.LEARNING -> handleLearning(input, intent)
                    IntentType.SYSTEM_CONTROL -> handleSystemControl(input, intent)
                    IntentType.CONVERSATION -> handleConversation(input, intent)
                    IntentType.CALCULATION -> handleCalculation(input, intent)
                    else -> handleGeneral(input, intent)
                }
                
                // Save conversation with response
                database.conversationDao().insert(
                    conversationEntry.copy(assistantResponse = response.text)
                )
                
                // Update context
                updateContext(conversationEntry.copy(assistantResponse = response.text))
                
                // Learn from interaction
                learnFromInteraction(input, response.text, intent)
                
                response
            } catch (e: Exception) {
                AIResponse(
                    text = "I encountered an error: ${e.message}. Let me try to help you differently.",
                    success = false,
                    data = null
                )
            }
        }
    }
    
    private suspend fun checkLearnedPatterns(input: String): AIResponse? {
        val patterns = database.learnedPatternDao().findMatchingPatterns(input)
        return patterns.firstOrNull()?.let { pattern ->
            database.learnedPatternDao().incrementFrequency(pattern.id)
            AIResponse(
                text = pattern.response,
                success = true,
                data = mapOf("source" to "learned_pattern")
            )
        }
    }
    
    private suspend fun handleSearch(input: String, intent: Intent): AIResponse {
        val query = intent.entities["query"] ?: input
        return AIResponse(
            text = "Searching for: $query",
            success = true,
            action = AIAction.SEARCH,
            data = mapOf("query" to query)
        )
    }
    
    private suspend fun handleFileOperation(input: String, intent: Intent): AIResponse {
        val operation = intent.entities["operation"] ?: "unknown"
        val path = intent.entities["path"] ?: ""
        
        return AIResponse(
            text = "I'll help you $operation the file at $path",
            success = true,
            action = AIAction.FILE_OPERATION,
            data = mapOf("operation" to operation, "path" to path)
        )
    }
    
    private suspend fun handleMemoryQuery(input: String, intent: Intent): AIResponse {
        val key = intent.entities["key"] ?: ""
        val memory = database.memoryDao().getMemoryByKey(key)
        
        return if (memory != null) {
            database.memoryDao().updateAccess(memory.id)
            AIResponse(
                text = "I remember: ${memory.value}",
                success = true,
                data = mapOf("memory" to memory.value)
            )
        } else {
            AIResponse(
                text = "I don't have any memory about that. Would you like me to remember something?",
                success = false
            )
        }
    }
    
    private suspend fun handleLearning(input: String, intent: Intent): AIResponse {
        val key = intent.entities["key"] ?: UUID.randomUUID().toString()
        val value = intent.entities["value"] ?: input
        val category = intent.entities["category"] ?: "general"
        
        val memory = MemoryEntry(
            key = key,
            value = value,
            category = category,
            importance = 5
        )
        
        database.memoryDao().insert(memory)
        
        return AIResponse(
            text = "I've learned and stored this information in my memory.",
            success = true,
            data = mapOf("stored" to true)
        )
    }
    
    private suspend fun handleSystemControl(input: String, intent: Intent): AIResponse {
        val command = intent.entities["command"] ?: ""
        
        return AIResponse(
            text = "Executing system command: $command",
            success = true,
            action = AIAction.SYSTEM_CONTROL,
            data = mapOf("command" to command)
        )
    }
    
    private suspend fun handleConversation(input: String, intent: Intent): AIResponse {
        val responses = listOf(
            "I'm here to help! What would you like to know?",
            "That's interesting! Tell me more.",
            "I understand. How can I assist you with that?",
            "I'm listening. What else can I do for you?",
            "Got it! What would you like me to do next?"
        )
        
        val contextualResponse = generateContextualResponse(input)
        
        return AIResponse(
            text = contextualResponse ?: responses.random(),
            success = true
        )
    }
    
    private suspend fun handleCalculation(input: String, intent: Intent): AIResponse {
        val expression = intent.entities["expression"] ?: input
        val result = nlpProcessor.evaluateExpression(expression)
        
        return AIResponse(
            text = "The result is: $result",
            success = true,
            data = mapOf("result" to result)
        )
    }
    
    private suspend fun handleGeneral(input: String, intent: Intent): AIResponse {
        val keywords = nlpProcessor.extractKeywords(input)
        val relevantMemories = keywords.mapNotNull { keyword ->
            database.memoryDao().getMemoryByKey(keyword)
        }
        
        val response = if (relevantMemories.isNotEmpty()) {
            "Based on what I know: ${relevantMemories.first().value}"
        } else {
            "I can help you with searches, file operations, remembering information, and much more. What would you like to do?"
        }
        
        return AIResponse(
            text = response,
            success = true
        )
    }
    
    private fun generateContextualResponse(input: String): String? {
        if (conversationContext.isEmpty()) return null
        
        val recentContext = conversationContext.takeLast(3)
        val contextKeywords = recentContext.flatMap { 
            nlpProcessor.extractKeywords(it.userInput) 
        }
        
        val inputKeywords = nlpProcessor.extractKeywords(input)
        val overlap = contextKeywords.intersect(inputKeywords.toSet())
        
        return if (overlap.isNotEmpty()) {
            "Continuing from our previous discussion about ${overlap.first()}, I can help you with that."
        } else null
    }
    
    private fun updateContext(entry: ConversationEntry) {
        conversationContext.add(entry)
        if (conversationContext.size > maxContextSize) {
            conversationContext.removeAt(0)
        }
    }
    
    private suspend fun learnFromInteraction(input: String, response: String, intent: Intent) {
        scope.launch {
            val pattern = LearnedPattern(
                pattern = input.lowercase(),
                response = response,
                frequency = 1,
                tags = intent.entities.keys.toList()
            )
            
            val existing = database.learnedPatternDao().findMatchingPatterns(input)
            if (existing.isEmpty()) {
                database.learnedPatternDao().insert(pattern)
            }
        }
    }
    
    suspend fun getMemoryLog(): List<MemoryEntry> {
        return withContext(Dispatchers.IO) {
            kotlinx.coroutines.flow.first(database.memoryDao().getAllMemories())
        }
    }
    
    suspend fun getConversationHistory(limit: Int = 50): List<ConversationEntry> {
        return withContext(Dispatchers.IO) {
            database.conversationDao().getRecentConversations(limit)
        }
    }
    
    fun cleanup() {
        scope.cancel()
    }
}

data class AIResponse(
    val text: String,
    val success: Boolean,
    val action: AIAction? = null,
    val data: Map<String, Any>? = null
)

enum class AIAction {
    SEARCH, FILE_OPERATION, SYSTEM_CONTROL, MEMORY_STORE, NONE
}

data class Intent(
    val type: IntentType,
    val confidence: Float,
    val entities: Map<String, String>
)

enum class IntentType {
    SEARCH, FILE_OPERATION, MEMORY_QUERY, LEARNING, 
    SYSTEM_CONTROL, CONVERSATION, CALCULATION, UNKNOWN
}
