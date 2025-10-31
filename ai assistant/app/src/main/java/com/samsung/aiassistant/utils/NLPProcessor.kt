package com.samsung.aiassistant.utils

import com.samsung.aiassistant.core.Intent
import com.samsung.aiassistant.core.IntentType
import java.util.regex.Pattern

class NLPProcessor {
    
    private val searchKeywords = listOf("search", "find", "look for", "google", "browse", "what is", "who is", "where is")
    private val fileKeywords = listOf("file", "create", "delete", "save", "open", "read", "write", "folder", "directory")
    private val memoryKeywords = listOf("remember", "recall", "what did", "do you know", "tell me about")
    private val learningKeywords = listOf("learn", "remember this", "store", "save this", "note that")
    private val systemKeywords = listOf("open app", "launch", "close", "settings", "volume", "brightness")
    private val calculationKeywords = listOf("calculate", "compute", "what is", "plus", "minus", "times", "divided")
    
    fun analyzeIntent(input: String): Intent {
        val lowerInput = input.lowercase()
        
        return when {
            containsAny(lowerInput, searchKeywords) -> {
                Intent(
                    type = IntentType.SEARCH,
                    confidence = 0.9f,
                    entities = mapOf("query" to extractSearchQuery(input))
                )
            }
            containsAny(lowerInput, fileKeywords) -> {
                Intent(
                    type = IntentType.FILE_OPERATION,
                    confidence = 0.85f,
                    entities = extractFileEntities(input)
                )
            }
            containsAny(lowerInput, memoryKeywords) -> {
                Intent(
                    type = IntentType.MEMORY_QUERY,
                    confidence = 0.8f,
                    entities = mapOf("key" to extractMemoryKey(input))
                )
            }
            containsAny(lowerInput, learningKeywords) -> {
                Intent(
                    type = IntentType.LEARNING,
                    confidence = 0.85f,
                    entities = extractLearningEntities(input)
                )
            }
            containsAny(lowerInput, systemKeywords) -> {
                Intent(
                    type = IntentType.SYSTEM_CONTROL,
                    confidence = 0.8f,
                    entities = mapOf("command" to extractSystemCommand(input))
                )
            }
            isCalculation(lowerInput) -> {
                Intent(
                    type = IntentType.CALCULATION,
                    confidence = 0.9f,
                    entities = mapOf("expression" to input)
                )
            }
            else -> {
                Intent(
                    type = IntentType.CONVERSATION,
                    confidence = 0.5f,
                    entities = emptyMap()
                )
            }
        }
    }
    
    fun extractKeywords(input: String): List<String> {
        val stopWords = setOf("the", "is", "at", "which", "on", "a", "an", "and", "or", "but", "in", "with", "to", "for")
        return input.lowercase()
            .split(Pattern.compile("[\\s,.:;!?]+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
    }
    
    fun evaluateExpression(expression: String): String {
        return try {
            val cleaned = expression.replace(Regex("[^0-9+\\-*/().\\s]"), "")
            val result = evaluateSimpleExpression(cleaned)
            result.toString()
        } catch (e: Exception) {
            "Unable to calculate"
        }
    }
    
    private fun evaluateSimpleExpression(expr: String): Double {
        val tokens = expr.replace(" ", "").toCharArray()
        val values = mutableListOf<Double>()
        val ops = mutableListOf<Char>()
        var i = 0
        
        while (i < tokens.size) {
            when {
                tokens[i].isWhitespace() -> i++
                tokens[i].isDigit() || tokens[i] == '.' -> {
                    val sb = StringBuilder()
                    while (i < tokens.size && (tokens[i].isDigit() || tokens[i] == '.')) {
                        sb.append(tokens[i++])
                    }
                    values.add(sb.toString().toDouble())
                    continue
                }
                tokens[i] == '(' -> ops.add(tokens[i])
                tokens[i] == ')' -> {
                    while (ops.isNotEmpty() && ops.last() != '(') {
                        values.add(applyOp(ops.removeAt(ops.size - 1), values.removeAt(values.size - 1), values.removeAt(values.size - 1)))
                    }
                    if (ops.isNotEmpty()) ops.removeAt(ops.size - 1)
                }
                tokens[i] in listOf('+', '-', '*', '/') -> {
                    while (ops.isNotEmpty() && hasPrecedence(tokens[i], ops.last())) {
                        values.add(applyOp(ops.removeAt(ops.size - 1), values.removeAt(values.size - 1), values.removeAt(values.size - 1)))
                    }
                    ops.add(tokens[i])
                }
            }
            i++
        }
        
        while (ops.isNotEmpty()) {
            values.add(applyOp(ops.removeAt(ops.size - 1), values.removeAt(values.size - 1), values.removeAt(values.size - 1)))
        }
        
        return values.lastOrNull() ?: 0.0
    }
    
    private fun applyOp(op: Char, b: Double, a: Double): Double {
        return when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> if (b != 0.0) a / b else 0.0
            else -> 0.0
        }
    }
    
    private fun hasPrecedence(op1: Char, op2: Char): Boolean {
        if (op2 == '(' || op2 == ')') return false
        if ((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-')) return false
        return true
    }
    
    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }
    
    private fun extractSearchQuery(input: String): String {
        val patterns = listOf(
            "search for (.+)",
            "find (.+)",
            "look for (.+)",
            "google (.+)",
            "what is (.+)",
            "who is (.+)",
            "where is (.+)"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(input)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return input
    }
    
    private fun extractFileEntities(input: String): Map<String, String> {
        val entities = mutableMapOf<String, String>()
        
        val operations = listOf("create", "delete", "save", "open", "read", "write", "move", "copy")
        val operation = operations.find { input.lowercase().contains(it) } ?: "unknown"
        entities["operation"] = operation
        
        val pathPattern = Regex("""[/\\]?[\w/\\.-]+\.\w+""")
        val pathMatch = pathPattern.find(input)
        if (pathMatch != null) {
            entities["path"] = pathMatch.value
        }
        
        return entities
    }
    
    private fun extractMemoryKey(input: String): String {
        val patterns = listOf(
            "remember (.+)",
            "recall (.+)",
            "what did (.+)",
            "tell me about (.+)"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(input)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return input
    }
    
    private fun extractLearningEntities(input: String): Map<String, String> {
        val entities = mutableMapOf<String, String>()
        
        val patterns = listOf(
            "remember this[:\\s]+(.+)",
            "learn[:\\s]+(.+)",
            "store[:\\s]+(.+)",
            "save this[:\\s]+(.+)"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(input)
            if (match != null) {
                entities["value"] = match.groupValues[1].trim()
                return entities
            }
        }
        
        entities["value"] = input
        return entities
    }
    
    private fun extractSystemCommand(input: String): String {
        val commands = listOf("open", "launch", "close", "start", "stop", "settings", "volume", "brightness")
        val command = commands.find { input.lowercase().contains(it) } ?: "unknown"
        return command
    }
    
    private fun isCalculation(input: String): Boolean {
        return input.matches(Regex(".*\\d+.*[+\\-*/].*\\d+.*")) || 
               containsAny(input, calculationKeywords)
    }
}
