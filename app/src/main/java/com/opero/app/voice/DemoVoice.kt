package com.opero.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * 데모 모드 음성: 말하기는 안드로이드 기본 TTS, 듣기는 데모 패널에서 입력한 문장으로 대신합니다.
 * 시연 영상을 차량 없이 찍을 수 있게 하려는 용도입니다.
 */
class DemoVoice(context: Context) : Voice {

    private val _partial = MutableStateFlow("")
    override val partial: StateFlow<String> = _partial.asStateFlow()

    private val ready = CompletableDeferred<Boolean>()
    private var pendingSpeech: CompletableDeferred<Unit>? = null
    private var pendingAnswer: CompletableDeferred<String?>? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                pendingSpeech?.complete(Unit)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                pendingSpeech?.complete(Unit)
            }
        })
    }

    /** 듣는 중이면 true. 데모 패널이 입력창을 강조하는 데 씁니다. */
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    override suspend fun speak(text: String) {
        val ok = withTimeoutOrNull(3_000) { ready.await() } == true
        if (!ok) {
            delay((text.length * 70L).coerceIn(800L, 3_000L))
            return
        }
        tts.language = Locale.KOREA
        val done = CompletableDeferred<Unit>()
        pendingSpeech = done
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
        withTimeoutOrNull(20_000) { done.await() }
        pendingSpeech = null
    }

    override suspend fun listen(timeoutMs: Long): String? {
        val answer = CompletableDeferred<String?>()
        pendingAnswer = answer
        _partial.value = ""
        _listening.value = true
        val text = withTimeoutOrNull(timeoutMs) { answer.await() }
        _listening.value = false
        pendingAnswer = null
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 데모 패널에서 '말한 것으로' 입력한 문장을 전달합니다. null이면 무응답. */
    fun answer(text: String?) {
        if (text != null) _partial.value = text
        pendingAnswer?.complete(text)
    }

    override fun stopListening() {
        pendingAnswer?.complete(null)
    }

    override fun release() {
        tts.shutdown()
    }
}
