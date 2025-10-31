package com.samsung.aiassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.samsung.aiassistant.core.AIEngine
import kotlinx.coroutines.*

class AIAccessibilityService : AccessibilityService() {
    
    private lateinit var aiEngine: AIEngine
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        aiEngine = AIEngine(this)
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            notificationTimeout = 100
        }
        
        serviceInfo = info
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    handleViewClicked(it)
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(it)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleContentChanged(it)
                }
            }
        }
    }
    
    private fun handleViewClicked(event: AccessibilityEvent) {
        val source = event.source ?: return
        val text = source.text?.toString() ?: ""
        
        serviceScope.launch {
            // Log interaction for learning
            aiEngine.processInput("User clicked: $text", com.samsung.aiassistant.data.model.InputType.SYSTEM)
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        
        serviceScope.launch {
            // Learn app usage patterns
            aiEngine.processInput(
                "User opened: $packageName - $className",
                com.samsung.aiassistant.data.model.InputType.SYSTEM
            )
        }
    }
    
    private fun handleContentChanged(event: AccessibilityEvent) {
        // Monitor content changes for context awareness
        val source = event.source ?: return
        extractTextFromNode(source)
    }
    
    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        
        if (node.text != null) {
            builder.append(node.text).append(" ")
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                builder.append(extractTextFromNode(child))
                child.recycle()
            }
        }
        
        return builder.toString()
    }
    
    override fun onInterrupt() {
        // Handle interruption
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        aiEngine.cleanup()
    }
    
    // Helper functions for system control
    fun performGlobalAction(action: Int): Boolean {
        return performGlobalAction(action)
    }
    
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeByTextRecursive(rootNode, text)
    }
    
    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findNodeByTextRecursive(child, text)
                if (result != null) {
                    return result
                }
                child.recycle()
            }
        }
        
        return null
    }
}
