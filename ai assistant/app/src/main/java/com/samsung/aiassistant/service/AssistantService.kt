package com.samsung.aiassistant.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.samsung.aiassistant.AIAssistantApplication
import com.samsung.aiassistant.R
import com.samsung.aiassistant.core.AIEngine
import com.samsung.aiassistant.ui.MainActivity
import kotlinx.coroutines.*

class AssistantService : Service() {
    
    private lateinit var aiEngine: AIEngine
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val NOTIFICATION_ID = 1001
    
    override fun onCreate() {
        super.onCreate()
        aiEngine = AIEngine(this)
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service is running in foreground
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        return NotificationCompat.Builder(this, AIAssistantApplication.CHANNEL_ID)
            .setContentTitle("AI Assistant")
            .setContentText("AI Assistant is running")
            .setSmallIcon(R.drawable.ic_assistant)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        aiEngine.cleanup()
    }
}
