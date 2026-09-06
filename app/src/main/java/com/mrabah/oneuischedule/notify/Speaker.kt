package com.mrabah.oneuischedule.notify

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks the alert out loud. A teacher mid-lesson has their hands and eyes on
 * the class, not on a screen, so "باقي خمس دقائق" is more use spoken than shown.
 *
 * The engine is created per announcement and released when it finishes: a
 * long-lived TextToSpeech would hold audio focus all day for two sentences.
 */
object Speaker {

    private const val UTTERANCE = "period-alert"

    fun say(context: Context, text: String) {
        if (text.isBlank()) return
        val app = context.applicationContext
        var engine: TextToSpeech? = null

        engine = TextToSpeech(app) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                tts.shutdown()
                return@TextToSpeech
            }

            val arabic = Locale("ar")
            val result = tts.setLanguage(arabic)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // No Arabic voice installed; the bell still rings, so stay quiet.
                tts.shutdown()
                return@TextToSpeech
            }

            tts.setSpeechRate(0.95f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { tts.shutdown() }
                @Deprecated("required by the base class")
                override fun onError(utteranceId: String?) { tts.shutdown() }
            })

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
        }
    }

    /** True when the device has an Arabic voice at all. */
    fun probe(context: Context, onResult: (Boolean) -> Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine
            if (status != TextToSpeech.SUCCESS || tts == null) {
                onResult(false)
                return@TextToSpeech
            }
            val result = tts.isLanguageAvailable(Locale("ar"))
            tts.shutdown()
            onResult(
                result == TextToSpeech.LANG_AVAILABLE ||
                    result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            )
        }
    }
}
