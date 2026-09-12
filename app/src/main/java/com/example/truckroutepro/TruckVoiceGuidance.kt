package com.example.truckroutepro

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TruckVoiceGuidance(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false
    var isMuted = false

    private var lastSpokenInstruction = ""

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
            }
        }
    }

    fun speakInstruction(text: String, force: Boolean = false) {
        if (isMuted || !isInitialized) return
        if (force || text != lastSpokenInstruction) {
            lastSpokenInstruction = text
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TRUCK_NAV_TTS")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
