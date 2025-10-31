package com.samsung.aiassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.samsung.aiassistant.R
import com.samsung.aiassistant.core.AIEngine
import com.samsung.aiassistant.data.model.ConversationEntry
import com.samsung.aiassistant.data.model.InputType
import com.samsung.aiassistant.network.WebSearchEngine
import com.samsung.aiassistant.service.AssistantService
import com.samsung.aiassistant.utils.FileManager
import com.samsung.aiassistant.utils.VoiceManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var aiEngine: AIEngine
    private lateinit var voiceManager: VoiceManager
    private lateinit var fileManager: FileManager
    private lateinit var webSearchEngine: WebSearchEngine
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var voiceButton: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    
    private val chatAdapter = ChatAdapter()
    private val messages = mutableListOf<ChatMessage>()
    
    private val PERMISSION_REQUEST_CODE = 100
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeComponents()
        setupUI()
        checkPermissions()
        startAssistantService()
        
        addMessage("Hello! I'm your AI Assistant. How can I help you today?", false)
    }
    
    private fun initializeComponents() {
        aiEngine = AIEngine(this)
        voiceManager = VoiceManager(this)
        fileManager = FileManager(this)
        webSearchEngine = WebSearchEngine()
    }
    
    private fun setupUI() {
        recyclerView = findViewById(R.id.recyclerView)
        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        voiceButton = findViewById(R.id.voiceButton)
        progressBar = findViewById(R.id.progressBar)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = chatAdapter
        
        sendButton.setOnClickListener {
            val input = inputEditText.text.toString().trim()
            if (input.isNotEmpty()) {
                sendMessage(input, InputType.TEXT)
                inputEditText.text.clear()
            }
        }
        
        voiceButton.setOnClickListener {
            startVoiceInput()
        }
    }
    
    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Please grant storage permissions", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun startAssistantService() {
        val serviceIntent = Intent(this, AssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    private fun sendMessage(text: String, inputType: InputType) {
        addMessage(text, true)
        
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val response = aiEngine.processInput(text, inputType)
                
                addMessage(response.text, false)
                
                // Handle specific actions
                when (response.action) {
                    com.samsung.aiassistant.core.AIAction.SEARCH -> {
                        handleSearch(response.data?.get("query") as? String ?: text)
                    }
                    com.samsung.aiassistant.core.AIAction.FILE_OPERATION -> {
                        handleFileOperation(response.data)
                    }
                    else -> {
                        // Speak response
                        voiceManager.speak(response.text)
                    }
                }
                
            } catch (e: Exception) {
                addMessage("Error: ${e.message}", false)
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun handleSearch(query: String) {
        lifecycleScope.launch {
            try {
                val searchResult = webSearchEngine.search(query, 5)
                
                if (searchResult.success && searchResult.items.isNotEmpty()) {
                    val resultText = buildString {
                        appendLine("Search results for '$query':")
                        searchResult.items.forEachIndexed { index, item ->
                            appendLine("\n${index + 1}. ${item.title}")
                            appendLine("   ${item.snippet}")
                            appendLine("   ${item.url}")
                        }
                    }
                    addMessage(resultText, false)
                } else {
                    addMessage("No search results found for '$query'", false)
                }
            } catch (e: Exception) {
                addMessage("Search error: ${e.message}", false)
            }
        }
    }
    
    private fun handleFileOperation(data: Map<String, Any>?) {
        if (data == null) return
        
        val operation = data["operation"] as? String ?: return
        val path = data["path"] as? String ?: return
        
        lifecycleScope.launch {
            try {
                val result = when (operation.lowercase()) {
                    "create" -> fileManager.createFile(path)
                    "read" -> fileManager.readFile(path)
                    "delete" -> fileManager.deleteFile(path)
                    "list" -> fileManager.listFiles(path)
                    else -> null
                }
                
                result?.let {
                    if (it.success) {
                        addMessage(it.message, false)
                        if (it.content != null) {
                            addMessage("Content:\n${it.content}", false)
                        }
                        if (it.files != null && it.files.isNotEmpty()) {
                            val fileList = it.files.joinToString("\n") { file ->
                                "${if (file.isDirectory) "[DIR]" else "[FILE]"} ${file.name}"
                            }
                            addMessage("Files:\n$fileList", false)
                        }
                    } else {
                        addMessage("Operation failed: ${it.message}", false)
                    }
                }
            } catch (e: Exception) {
                addMessage("File operation error: ${e.message}", false)
            }
        }
    }
    
    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
            return
        }
        
        voiceManager.startListening { result ->
            if (result.isNotEmpty()) {
                inputEditText.setText(result)
                sendMessage(result, InputType.VOICE)
            }
        }
    }
    
    private fun addMessage(text: String, isUser: Boolean) {
        runOnUiThread {
            messages.add(ChatMessage(text, isUser))
            chatAdapter.notifyItemInserted(messages.size - 1)
            recyclerView.smoothScrollToPosition(messages.size - 1)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions denied. App may not work fully.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceManager.cleanup()
        aiEngine.cleanup()
    }
    
    // Chat Adapter
    inner class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return ChatViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            holder.bind(messages[position])
        }
        
        override fun getItemCount(): Int = messages.size
        
        inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val messageText: TextView = itemView.findViewById(R.id.messageText)
            private val messageContainer: LinearLayout = itemView.findViewById(R.id.messageContainer)
            
            fun bind(message: ChatMessage) {
                messageText.text = message.text
                
                val layoutParams = messageContainer.layoutParams as FrameLayout.LayoutParams
                if (message.isUser) {
                    layoutParams.gravity = android.view.Gravity.END
                    messageContainer.setBackgroundResource(R.drawable.bg_user_message)
                } else {
                    layoutParams.gravity = android.view.Gravity.START
                    messageContainer.setBackgroundResource(R.drawable.bg_assistant_message)
                }
                messageContainer.layoutParams = layoutParams
            }
        }
    }
    
    data class ChatMessage(val text: String, val isUser: Boolean)
}
