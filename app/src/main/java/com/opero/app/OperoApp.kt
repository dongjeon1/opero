package com.opero.app

import android.app.Application
import android.util.Log
import com.opero.app.car.DemoCarSource
import com.opero.app.car.PleosCarSource
import com.opero.app.core.SessionManager
import com.opero.app.core.SettingsStore
import com.opero.app.data.OperoDb
import com.opero.app.data.Repository
import com.opero.app.voice.DemoVoice
import com.opero.app.voice.PleosVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OperoApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repo: Repository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var session: SessionManager
        private set

    private val _demo = MutableStateFlow<DemoCarSource?>(null)
    /** 데모 모드일 때만 값이 있습니다. 데모 패널이 이벤트를 만들 때 씁니다. */
    val demo: StateFlow<DemoCarSource?> = _demo.asStateFlow()
    var demoVoice: DemoVoice? = null
        private set
    private val _carStatus = MutableStateFlow("")
    /** 차량 연결 상태 문구(데모 모드 / 차량 연결됨 / 일부 연결 안 됨) */
    val carStatus: StateFlow<String> = _carStatus.asStateFlow()
    private var statusJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repo = Repository(OperoDb.create(this))
        settings = SettingsStore(this)
        session = SessionManager(scope, repo, settings)
        connect()
    }

    /** 설정의 데모 모드에 따라 차량 소스와 음성을 연결합니다. 실제 SDK 연결에 실패하면 데모로 돌아갑니다. */
    fun connect() {
        if (settings.value.demoMode) {
            connectDemo()
            return
        }
        try {
            val car = PleosCarSource(this, scope)
            val voice = PleosVoice(this)
            _demo.value = null
            demoVoice = null
            followStatus(car.status)
            session.attach(car, voice)
        } catch (t: Throwable) {
            Log.w("OperoApp", "Pleos SDK unavailable, falling back to demo: $t")
            connectDemo()
        }
    }

    private fun connectDemo() {
        val car = DemoCarSource()
        val voice = DemoVoice(this)
        _demo.value = car
        demoVoice = voice
        followStatus(car.status)
        session.attach(car, voice)
    }

    private fun followStatus(source: StateFlow<String>) {
        statusJob?.cancel()
        statusJob = scope.launch { source.collect { _carStatus.value = it } }
    }
}
