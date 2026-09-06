package com.mrabah.oneuischedule.notify

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Speaks alerts aloud. A teacher mid-lesson has their hands and eyes on the
 * class, so "باقي خمس دقائق" is more use spoken than shown.
 *
 * Android does not ship one Arabic voice, it ships several — male and female,
 * local and network — and their quality differs enormously. The network ones
 * are neural and sound close to human; the local ones are older and flatter.
 * So the voice is a user choice, not a default.
 */
object Speaker {

    private const val UTTERANCE = "period-alert"

    data class VoiceOption(
        val id: String,
        val label: String,
        val network: Boolean,
        val quality: Int,
    )

    /** Runs the engine once, hands over the Arabic voices, then releases it. */
    fun voices(context: Context, onResult: (List<VoiceOption>) -> Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine
            if (status != TextToSpeech.SUCCESS || tts == null) {
                onResult(emptyList())
                return@TextToSpeech
            }
            val list = try {
                tts.voices.orEmpty()
                    .filter { it.locale.language == "ar" && !it.isNetworkConnectionRequired || it.locale.language == "ar" }
                    .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                    .sortedByDescending { it.quality }
                    .map { it.toOption() }
            } catch (t: Throwable) {
                emptyList()
            }
            tts.shutdown()
            onResult(list)
        }
    }

    private fun Voice.toOption(): VoiceOption {
        val region = locale.country.ifBlank { locale.language }
        val kind = if (isNetworkConnectionRequired) "شبكة" else "محلي"
        val grade = when {
            quality >= Voice.QUALITY_VERY_HIGH -> "عالية جدًا"
            quality >= Voice.QUALITY_HIGH -> "عالية"
            quality >= Voice.QUALITY_NORMAL -> "متوسطة"
            else -> "منخفضة"
        }
        return VoiceOption(
            id = name,
            label = "$region · $kind · جودة $grade",
            network = isNetworkConnectionRequired,
            quality = quality,
        )
    }

    /** @param voiceId a name from [voices]; blank falls back to the engine default. */
    fun say(context: Context, text: String, voiceId: String = "") {
        if (text.isBlank()) return
        val app = context.applicationContext
        var engine: TextToSpeech? = null

        engine = TextToSpeech(app) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                tts.shutdown()
                return@TextToSpeech
            }

            val result = tts.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // No Arabic voice installed; the bell still rings, so stay quiet.
                tts.shutdown()
                return@TextToSpeech
            }

            if (voiceId.isNotBlank()) {
                tts.voices.orEmpty().firstOrNull { it.name == voiceId }?.let { tts.voice = it }
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
}
