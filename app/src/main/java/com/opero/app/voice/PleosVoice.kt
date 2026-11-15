package com.opero.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/**
 * Pleos SpeechToText / TextToSpeech SDK를 쓰는 음성 입출력.
 * ON_DEVICE 모드는 네트워크와 앱 등록 없이 동작합니다.
 */
class PleosVoice(context: Context) : Voice {

    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    private val tts = PleosTts(context.applicationContext, TtsMode.ON_DEVICE)

    private val sttReady = CompletableDeferred<Boolean>()
    private val ttsReady = CompletableDeferred<Boolean>()

    private val _partial = MutableStateFlow("")
    override val partial: StateFlow<String> = _partial.asStateFlow()

    private var pendingResult: CompletableDeferred<String?>? = null
    private var pendingSpeech: CompletableDeferred<Unit>? = null

    private val resultListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            _partial.value = stt
            if (completed) pendingResult?.complete(stt)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            pendingResult?.complete(_partial.value.ifBlank { null })
        }
        override fun onError(errorType: SttError) {
            Log.w(TAG, "STT error $errorType")
            pendingResult?.complete(null)
        }
    }

    private val ttsListener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            pendingSpeech?.complete(Unit)
        }
        override fun onStop() {
            pendingSpeech?.complete(Unit)
        }
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "TTS error $errorType")
            pendingSpeech?.complete(Unit)
        }
    }

    init {
        stt.initialize(object : SttInit {
            override fun onSuccess() {
                stt.addListener(resultListener)
                runCatching { stt.setLocale(Locale.KOREA) }
                sttReady.complete(true)
            }
            override fun onFailure(errorType: SttError) {
                Log.w(TAG, "STT init failed $errorType")
                sttReady.complete(false)
            }
        })
        tts.initialize(object : TtsInit {
            override fun onSuccess() {
                tts.addEventListener(ttsListener)
                tts.setLocale(Locale.KOREA)
                ttsReady.complete(true)
            }
            override fun onFailure(errorType: TtsError) {
                Log.w(TAG, "TTS init failed $errorType")
                ttsReady.complete(false)
            }
        })
    }

    override suspend fun speak(text: String) {
        if (withTimeoutOrNull(5_000) { ttsReady.await() } != true) return
        val done = CompletableDeferred<Unit>()
        pendingSpeech = done
        tts.speak(text, true)
        withTimeoutOrNull(20_000) { done.await() }
        pendingSpeech = null
    }

    override suspend fun listen(timeoutMs: Long): String? {
        if (withTimeoutOrNull(5_000) { sttReady.await() } != true) return null
        val result = CompletableDeferred<String?>()
        pendingResult = result
        _partial.value = ""
        stt.request()
        val text = withTimeoutOrNull(timeoutMs) { result.await() }
        if (text == null) stt.stop()
        pendingResult = null
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }

    override fun stopListening() {
        runCatching { stt.stop() }
        pendingResult?.complete(null)
    }

    override fun release() {
        runCatching { stt.removeListener(resultListener); stt.release() }
        runCatching { tts.removeEventListener(ttsListener); tts.release() }
    }

    companion object {
        private const val TAG = "OperoVoice"
    }
}
