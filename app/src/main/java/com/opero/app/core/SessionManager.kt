package com.opero.app.core

import android.util.Log
import com.opero.app.car.CarEvent
import com.opero.app.car.CarSource
import com.opero.app.car.Place
import com.opero.app.data.Client
import com.opero.app.data.Repository
import com.opero.app.data.Trip
import com.opero.app.data.TripType
import com.opero.app.voice.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면에 띄울 질문·안내 카드 */
data class Prompt(
    val kind: Kind,
    val badge: String,
    val question: String,
    val primary: String? = null,
    val secondary: String? = null,
    val listening: Boolean = false,
    val result: String? = null,
    val detail: List<Pair<String, String>> = emptyList(),
) {
    enum class Kind { PURPOSE, MEMO, BRIEFING, INFO }
}

/** 현재 운전 상태 요약(화면 상단 표시용) */
data class DriveState(
    val parked: Boolean = true,
    val odometerKm: Double? = null,
    val destination: Place? = null,
    val openTripStart: Long? = null,
    val personalMode: Boolean = false,
)

/**
 * 음성 대화 흐름(캔버스 8번 흐름도)을 구현하는 상태 머신.
 *
 * 주행 시작(정차 해제) → 구간 기록 시작
 * 도착 5분 전 → 지난 메모 브리핑
 * 도착 + 정차 → "업무 방문으로 기록할까요?" → 업무 / 개인 / 미확인 저장
 * 정차 중 운전석 문 닫힘 + 일정 시간 경과 → "미팅 메모 남길까요?" → 거래처별 메모 저장
 *
 * 질문은 정차 상태에서만 합니다. 주행 중에는 브리핑만 읽습니다.
 */
class SessionManager(
    private val scope: CoroutineScope,
    private val repo: Repository,
    private val settings: SettingsStore,
) {
    private val _prompt = MutableStateFlow<Prompt?>(null)
    val prompt: StateFlow<Prompt?> = _prompt.asStateFlow()

    private val _drive = MutableStateFlow(DriveState())
    val drive: StateFlow<DriveState> = _drive.asStateFlow()

    private var car: CarSource? = null
    private var voice: Voice? = null
    val currentVoice: Voice? get() = voice
    private var eventJob: Job? = null
    private val mutex = Mutex()

    private var lastOdometer: Double? = null
    private var lastPlaceName: String? = null
    private var lastLocation: Place? = null
    private var lastLocationAt: Long = 0
    // 위치가 정차·출발 직후에 들어올 수 있어, 이름이 비어 저장된 구간을 잠시 기억했다가 채웁니다.
    private var fillStartTripId: Long? = null
    private var fillEndTripId: Long? = null
    private var fillUntil: Long = 0
    private var arrivedPlace: Place? = null
    private var arrivedAt: Long = 0
    private var briefingPending: Client? = null
    private var memoCandidate: Long? = null      // 메모를 물어볼 업무 운행 id
    private var memoCandidateSince: Long = 0
    private var pendingButton: CompletableDeferred<Boolean>? = null
    private var dialogJob: Job? = null

    fun attach(car: CarSource, voice: Voice) {
        detach()
        this.car = car
        this.voice = voice
        eventJob = scope.launch {
            car.events.collect { event -> mutex.withLock { handle(event) } }
        }
        car.start()
        scope.launch {
            repo.openTrip()?.let { trip -> _drive.value = _drive.value.copy(openTripStart = trip.startTime) }
        }
    }

    fun detach() {
        eventJob?.cancel()
        dialogJob?.cancel()
        car?.stop()
        voice?.release()
        car = null
        voice = null
        _prompt.value = null
    }

    fun navigateTo(place: Place): Boolean = car?.navigateTo(place) ?: false

    /** 화면 버튼으로 질문에 답할 때 */
    fun onPromptButton(primary: Boolean) {
        pendingButton?.complete(primary)
        voice?.stopListening()
        if (pendingButton == null) _prompt.value = null
    }

    fun dismissPrompt() {
        dialogJob?.cancel()
        voice?.stopListening()
        _prompt.value = null
    }

    /** 기록을 모두 지운 뒤 호출: 기억해 둔 장소 이름을 비웁니다. */
    fun forgetPlaces() {
        lastPlaceName = null
        fillStartTripId = null
        fillEndTripId = null
    }

    /**
     * '말하기' 버튼: 언제든 한 마디로 기록합니다.
     * "지금부터 개인 용무" / "개인 용무 끝" / "주차비 3천 원"
     */
    fun voiceCommand() {
        if (_prompt.value != null) return
        startDialog { command() }
    }

    fun togglePersonalMode() {
        val on = !_drive.value.personalMode
        _drive.value = _drive.value.copy(personalMode = on)
        info(if (on) "지금부터 개인 용무로 기록해요" else "개인 용무를 끝냈어요")
    }

    private suspend fun handle(event: CarEvent) {
        when (event) {
            is CarEvent.Odometer -> {
                lastOdometer = event.km
                _drive.value = _drive.value.copy(odometerKm = event.km)
            }
            is CarEvent.Destination -> {
                _drive.value = _drive.value.copy(destination = event.place)
                briefingPending = event.place?.let { place ->
                    repo.clientByName(place.name)?.takeIf { repo.latestMemo(it.id) != null }
                }
            }
            is CarEvent.Remaining -> {
                val client = briefingPending
                if (client != null && settings.value.briefing && event.seconds <= settings.value.briefingLeadSec) {
                    briefingPending = null
                    startDialog { brief(client) }
                }
            }
            is CarEvent.Arrived -> {
                arrivedPlace = event.place ?: _drive.value.destination
                arrivedAt = System.currentTimeMillis()
            }
            is CarEvent.Parked -> {
                _drive.value = _drive.value.copy(parked = event.parked)
                if (event.parked) onParked() else onDeparted()
            }
            CarEvent.DriverDoorClosed -> onDriverDoorClosed()
            is CarEvent.Location -> onLocation(event.place)
        }
    }

    /** 최근 1분 안의 현재 위치 이름 */
    private fun recentLocationName(): String? =
        lastLocation?.takeIf { System.currentTimeMillis() - lastLocationAt < 60_000 }?.name

    private suspend fun onLocation(place: Place) {
        lastLocation = place
        lastLocationAt = System.currentTimeMillis()
        if (System.currentTimeMillis() > fillUntil) {
            fillStartTripId = null
            fillEndTripId = null
            return
        }
        fillStartTripId?.let { id ->
            fillStartTripId = null
            repo.trip(id)?.let { repo.updateTrip(it.copy(startPlace = place.name)) }
        }
        fillEndTripId?.let { id ->
            fillEndTripId = null
            lastPlaceName = place.name
            repo.trip(id)?.let { repo.updateTrip(it.copy(endPlace = place.name)) }
        }
    }

    private suspend fun onDeparted() {
        if (repo.openTrip() != null) return
        val now = System.currentTimeMillis()
        val known = lastPlaceName ?: recentLocationName()
        val trip = repo.startTrip(now, lastOdometer ?: 0.0, known ?: "출발지")
        if (known == null) {
            fillStartTripId = trip.id
            fillUntil = now + 30_000
        }
        memoCandidate = null
        _drive.value = _drive.value.copy(openTripStart = trip.startTime)
        Log.i(TAG, "trip started ${trip.id}")
    }

    private suspend fun onParked() {
        val trip = repo.openTrip() ?: return
        val now = System.currentTimeMillis()
        val recentArrival = arrivedPlace?.takeIf { now - arrivedAt < 10 * 60_000 }
        val place = recentArrival ?: _drive.value.destination
        val locationName = recentLocationName()
        val placeName = place?.name ?: locationName ?: "도착지"
        val ended = trip.copy(endTime = now, endOdoKm = lastOdometer ?: trip.startOdoKm, endPlace = placeName)
        lastPlaceName = placeName.takeIf { it != "도착지" }
        if (place == null) {
            // 목적지 없이 세운 경우: 정차 직후 들어오는 현재 위치로 다시 채웁니다.
            fillEndTripId = trip.id
            fillUntil = now + 30_000
        }
        arrivedPlace = null
        val personal = _drive.value.personalMode
        _drive.value = _drive.value.copy(openTripStart = null, personalMode = false, destination = null)

        when {
            personal -> {
                repo.updateTrip(ended.copy(type = TripType.PERSONAL, purpose = "개인 용무"))
                startDialog { say(Prompt.Kind.INFO, "개인 용무로 기록했어요", ended) }
            }
            settings.value.isCommutePlace(placeName) -> {
                repo.updateTrip(ended.copy(type = TripType.COMMUTE, purpose = "출퇴근"))
                startDialog { say(Prompt.Kind.INFO, "출퇴근으로 기록했어요", ended) }
            }
            place == null || !settings.value.askPurpose -> {
                repo.updateTrip(ended.copy(type = TripType.UNCONFIRMED))
            }
            else -> {
                val client = repo.upsertClient(place)
                val saved = ended.copy(clientId = client.id, type = TripType.UNCONFIRMED)
                repo.updateTrip(saved)
                startDialog { askPurpose(saved, client) }
            }
        }
    }

    private suspend fun onDriverDoorClosed() {
        val tripId = memoCandidate ?: return
        if (!_drive.value.parked || !settings.value.askMemo) return
        val elapsedMin = (System.currentTimeMillis() - memoCandidateSince) / 60_000
        val delayMin = if (settings.value.demoMode) 0 else settings.value.memoDelayMin
        if (elapsedMin < delayMin) return
        val trip = repo.trip(tripId) ?: return
        if (trip.memoAsked) return
        val client = trip.clientId?.let { repo.client(it) } ?: return
        memoCandidate = null
        repo.updateTrip(trip.copy(memoAsked = true))
        startDialog { askMemo(trip, client) }
    }

    // ---------------- 대화 ----------------

    private fun startDialog(block: suspend () -> Unit) {
        dialogJob?.cancel()
        dialogJob = scope.launch {
            try {
                block()
            } finally {
                pendingButton = null
            }
        }
    }

    private suspend fun askPurpose(trip: Trip, client: Client) {
        val suggestion = client.lastPurpose
        val question = if (suggestion != null) {
            "${client.name} ${suggestion}${josaRo(suggestion)} 기록할까요?"
        } else {
            "${client.name} 업무 방문으로 기록할까요? 어떤 업무였나요?"
        }
        val detail = tripDetail(trip, client.name)
        val answer = ask(
            Prompt(Prompt.Kind.PURPOSE, "P · 주차 · 도착 기록", question, "업무로 기록", "개인 용무", detail = detail),
            listenMs = 10_000,
        )
        val now = System.currentTimeMillis()
        val result = when (answer) {
            is Answer.Button -> if (answer.primary) IntentParser.Purpose.Business(suggestion ?: "업무 방문") else IntentParser.Purpose.Personal
            is Answer.Spoken -> IntentParser.purpose(answer.text, suggestion)
        }
        val message = when (result) {
            is IntentParser.Purpose.Business -> {
                repo.updateTrip(trip.copy(type = TripType.BUSINESS, purpose = result.purpose))
                repo.updateClient(client.copy(lastPurpose = result.purpose, lastVisit = now))
                memoCandidate = trip.id
                memoCandidateSince = now
                "${result.purpose}${josaRo(result.purpose)} 기록했어요"
            }
            IntentParser.Purpose.Personal -> {
                repo.updateTrip(trip.copy(type = TripType.PERSONAL, purpose = "개인 용무"))
                "개인 용무로 기록했어요"
            }
            IntentParser.Purpose.Unconfirmed -> "나중에 확인할 수 있게 '미확인'으로 저장했어요"
        }
        (answer as? Answer.Spoken)?.text?.let { saveExpenseIfAny(it, trip.id) }
        finish(message)
    }

    private suspend fun askMemo(trip: Trip, client: Client) {
        val first = ask(
            Prompt(Prompt.Kind.MEMO, "P · 주차 중 · 운전석 탑승", "${client.name} 미팅 메모 남길까요?", "저장", "건너뛰기"),
            listenMs = 20_000,
        )
        var text: String? = when (first) {
            is Answer.Button -> if (first.primary) voice?.partial?.value?.takeIf { it.isNotBlank() } else null
            is Answer.Spoken -> first.text
        }
        if (text != null && IntentParser.isCancel(text)) text = null
        if (text != null && IntentParser.isYesOnly(text)) {
            // "응"이라고만 하면 이어서 내용을 듣습니다.
            val second = ask(
                Prompt(Prompt.Kind.MEMO, "P · 메모 듣는 중", "말씀하세요. 끝나면 잠시 멈추면 돼요.", "저장", "취소"),
                listenMs = 25_000,
            )
            text = when (second) {
                is Answer.Button -> if (second.primary) voice?.partial?.value?.takeIf { it.isNotBlank() } else null
                is Answer.Spoken -> second.text
            }
        }
        if (text.isNullOrBlank()) {
            finish("메모 없이 넘어갈게요")
            return
        }
        repo.addMemo(client.id, trip.id, System.currentTimeMillis(), text)
        saveExpenseIfAny(text, trip.id)
        finish("${client.name} 메모를 저장했어요")
    }

    private suspend fun command() {
        val badge = if (_drive.value.parked) "P · 정차 중 · 말하기" else "주행 중 · 말하기"
        val answer = ask(
            Prompt(Prompt.Kind.INFO, badge, "무엇을 기록할까요?", secondary = "취소",
                detail = listOf("개인 용무" to "\"지금부터 개인 용무\" · \"개인 용무 끝\"", "경비" to "\"주차비 3천 원\" · \"통행료 4,800원\"")),
            listenMs = 8_000,
        )
        val text = (answer as? Answer.Spoken)?.text?.trim()
        val understood = text != null && (IntentParser.isPersonalEnd(text) || IntentParser.isPersonalStart(text) || IntentParser.expense(text) != null)
        if (answer is Answer.Button || text.isNullOrBlank() || (!understood && IntentParser.isCancel(text))) {
            _prompt.value = null
            return
        }
        val personal = _drive.value.personalMode
        when {
            IntentParser.isPersonalEnd(text) -> {
                if (personal) _drive.value = _drive.value.copy(personalMode = false)
                finish(if (personal) "개인 용무를 끝냈어요. 다시 업무로 기록해요" else "지금은 업무로 기록 중이에요")
            }
            IntentParser.isPersonalStart(text) -> {
                _drive.value = _drive.value.copy(personalMode = true)
                finish("지금부터 개인 용무로 기록해요")
            }
            IntentParser.expense(text) != null -> {
                val item = IntentParser.expense(text)!!
                repo.addExpense(repo.openTrip()?.id, System.currentTimeMillis(), item.category, item.amount)
                finish(String.format(Locale.KOREA, "%s %,d원 기록했어요", item.category, item.amount))
            }
            else -> finish("\"개인 용무\"나 \"주차비 3천 원\"처럼 말해 주세요")
        }
    }

    private suspend fun brief(client: Client) {
        val memo = repo.latestMemo(client.id) ?: return
        val date = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(memo.createdAt))
        val text = "${client.name}입니다. 지난 $date 메모예요. ${memo.text}"
        _prompt.value = Prompt(Prompt.Kind.BRIEFING, "도착 전 브리핑 · 지난 방문 $date", "\"$text\"")
        voice?.speak(text)
        val again = voice?.listen(4_000)
        if (IntentParser.isRepeat(again)) voice?.speak(text)
        delay(1_500)
        _prompt.value = null
    }

    private suspend fun say(kind: Prompt.Kind, message: String, trip: Trip) {
        _prompt.value = Prompt(kind, "P · 주차", message, detail = tripDetail(trip, trip.endPlace ?: "도착지"))
        voice?.speak(message)
        delay(2_500)
        _prompt.value = null
    }

    private fun info(message: String) {
        startDialog {
            _prompt.value = Prompt(Prompt.Kind.INFO, "안내", message)
            voice?.speak(message)
            delay(1_500)
            _prompt.value = null
        }
    }

    private suspend fun finish(message: String) {
        _prompt.value = _prompt.value?.copy(listening = false, result = message, primary = null, secondary = null)
        voice?.speak(message)
        delay(2_000)
        _prompt.value = null
    }

    private suspend fun saveExpenseIfAny(text: String, tripId: Long) {
        IntentParser.expense(text)?.let { repo.addExpense(tripId, System.currentTimeMillis(), it.category, it.amount) }
    }

    private sealed interface Answer {
        data class Button(val primary: Boolean) : Answer
        data class Spoken(val text: String?) : Answer
    }

    /** 질문을 띄우고 읽은 뒤, 음성 대답과 화면 버튼 중 먼저 오는 것을 돌려줍니다. */
    private suspend fun ask(prompt: Prompt, listenMs: Long): Answer {
        val button = CompletableDeferred<Boolean>()
        pendingButton = button
        _prompt.value = prompt
        voice?.speak(prompt.question)
        if (button.isCompleted) return Answer.Button(button.await())
        _prompt.value = prompt.copy(listening = true)
        val spoken = scope.async { voice?.listen(listenMs) }
        val answer = select<Answer> {
            button.onAwait { Answer.Button(it) }
            spoken.onAwait { Answer.Spoken(it) }
        }
        if (answer is Answer.Button) spoken.cancel()
        pendingButton = null
        return answer
    }

    private fun tripDetail(trip: Trip, endName: String): List<Pair<String, String>> {
        val fmt = SimpleDateFormat("HH:mm", Locale.KOREA)
        return listOf(
            "출발" to "${trip.startPlace} · ${fmt.format(Date(trip.startTime))}",
            "도착" to "$endName · ${fmt.format(Date(trip.endTime ?: System.currentTimeMillis()))}",
            "주행거리" to String.format(Locale.KOREA, "%.1f km", trip.distanceKm),
            "계기판 누적" to String.format(Locale.KOREA, "%,.1f → %,.1f km", trip.startOdoKm, trip.endOdoKm ?: trip.startOdoKm),
        )
    }

    /** 받침 유무에 따라 '으로/로' */
    private fun josaRo(word: String): String {
        val last = word.trim().lastOrNull() ?: return "로"
        if (last !in '가'..'힣') return "로"
        val jong = (last.code - 0xAC00) % 28
        return if (jong == 0 || jong == 8) "로" else "으로"
    }

    companion object {
        private const val TAG = "OperoSession"
    }
}
