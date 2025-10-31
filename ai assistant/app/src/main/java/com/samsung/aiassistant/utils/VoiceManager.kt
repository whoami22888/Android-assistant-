package com.samsung.aiassistant.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class VoiceManager(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isSpeaking = false
    
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    init {
        initializeTTS()
        initializeSpeechRecognizer()
    }
    
    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }
                    
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })
            }
        }
    }
    
    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }
                
                override fun onBeginningOfSpeech() {}
                
                override fun onRmsChanged(rmsdB: Float) {}
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    isListening = false
                }
                
                override fun onError(error: Int) {
                    isListening = false
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error"
                    }
                    onErrorCallback?.invoke(errorMessage)
                }
                
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResultCallback?.invoke(matches[0])
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {}
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    
    fun startListening(
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        if (isListening) {
            stopListening()
        }
        
        onResultCallback = onResult
        onErrorCallback = onError
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }
        
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onErrorCallback?.invoke("Failed to start listening: ${e.message}")
        }
    }
    
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }
    
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (textToSpeech == null) {
            initializeTTS()
        }
        
        val utteranceId = UUID.randomUUID().toString()
        
        if (onComplete != null) {
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    onComplete()
                }
                
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    onComplete()
                }
            })
        }
        
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    
    fun stopSpeaking() {
        if (isSpeaking) {
            textToSpeech?.stop()
            isSpeaking = false
        }
    }
    
    fun isCurrentlyListening(): Boolean = isListening
    
    fun isCurrentlySpeaking(): Boolean = isSpeaking
    
    fun setLanguage(locale: Locale) {
        textToSpeech?.language = locale
    }
    
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }
    
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }
    
    fun cleanup() {
        stopListening()
        stopSpeaking()
        
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
