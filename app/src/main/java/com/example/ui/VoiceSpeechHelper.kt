package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class VoiceSpeechHelper(
    private val context: Context,
    private val onCommandRecognized: (String, Boolean) -> Unit
) {
    var isListening by mutableStateOf(false)
        private set

    var partialText by mutableStateOf("")
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    var rmsLevel by mutableStateOf(0f)
        private set

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        errorText = null
        partialText = ""
        rmsLevel = 0f

        // Make sure SpeechRecognizer is created on the Main UI thread
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        rmsLevel = rmsdB
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio do sistema"
                            SpeechRecognizer.ERROR_CLIENT -> "Erro do cliente de voz"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de gravação de áudio ausente"
                            SpeechRecognizer.ERROR_NETWORK -> "Falha na conexão de rede"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo limite de rede esgotado"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma palavra identificada"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O serviço de voz está ocupado"
                            SpeechRecognizer.ERROR_SERVER -> "Erro no servidor de voz"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silêncio detectado"
                            else -> "Erro desconhecido ($error)"
                        }
                        errorText = message
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            onCommandRecognized(recognizedText, true) // Final result
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            partialText = text
                            onCommandRecognized(text, false) // Partial/intermediate result
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            errorText = "Microfone inacessível ou ocupado por outro app"
            isListening = false
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
