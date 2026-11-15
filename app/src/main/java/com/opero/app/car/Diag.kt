package com.opero.app.car

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 차량 SDK 연결 진단 기록. 오늘 화면 아래에 그대로 보여 줘서, Logcat 없이도 원인을 볼 수 있게 합니다. */
object Diag {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()
    private val seen = HashSet<String>()

    fun add(line: String) {
        Log.i("OperoDiag", line)
        synchronized(seen) {
            if (!seen.add(line)) return
            _lines.value = (_lines.value + line).takeLast(40)
        }
    }

    /** 같은 키의 줄은 최신 값으로 바꿉니다(콜백 값처럼 계속 바뀌는 것). */
    fun set(key: String, line: String) {
        synchronized(seen) {
            _lines.value = (_lines.value.filterNot { it.startsWith("$key ") } + "$key $line").takeLast(40)
        }
    }
}
