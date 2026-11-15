package com.opero.app.voice

import kotlinx.coroutines.flow.StateFlow

/** 음성 입출력. 실제 차량은 Pleos STT/TTS, 데모 모드는 안드로이드 TTS + 화면 입력을 씁니다. */
interface Voice {
    /** 인식 중인 문장(중간 결과 포함) */
    val partial: StateFlow<String>

    /** 말하고, 끝날 때까지 기다립니다. */
    suspend fun speak(text: String)

    /** 한 번 듣고 최종 문장을 돌려줍니다. 시간 안에 말이 없으면 null. */
    suspend fun listen(timeoutMs: Long): String?

    fun stopListening()

    fun release()
}
