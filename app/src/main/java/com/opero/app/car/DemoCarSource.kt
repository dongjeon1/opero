package com.opero.app.car

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 차량 없이 시연하기 위한 데모 이벤트 소스. 화면의 데모 패널 버튼으로 이벤트를 만듭니다. */
class DemoCarSource : CarSource {
    private val _events = MutableSharedFlow<CarEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<CarEvent> = _events.asSharedFlow()
    private val _status = MutableStateFlow("데모 모드")
    override val status: StateFlow<String> = _status.asStateFlow()

    private val _parked = MutableStateFlow(true)
    val parked: StateFlow<Boolean> = _parked.asStateFlow()
    private val _odometer = MutableStateFlow(24512.0)
    val odometer: StateFlow<Double> = _odometer.asStateFlow()
    private val _destination = MutableStateFlow<Place?>(null)
    val destination: StateFlow<Place?> = _destination.asStateFlow()

    val samplePlaces = listOf(
        Place("○○상사", "경기 성남시 분당구", 37.3947, 127.1112),
        Place("□□전자", "경기 수원시 영통구", 37.2565, 127.0518),
        Place("△△산업", "서울 금천구 가산동", 37.4784, 126.8826),
        Place("회사", "서울 강남구", 37.4979, 127.0276),
        Place("집", "서울 송파구", 37.5145, 127.1059),
    )

    /** 데모 차량의 현재 위치. 처음은 회사, 이후에는 마지막으로 세운 목적지. */
    private var here: Place = samplePlaces.first { it.name == "회사" }

    override fun start() {
        _events.tryEmit(CarEvent.Odometer(_odometer.value))
        _events.tryEmit(CarEvent.Location(here))
        _events.tryEmit(CarEvent.Parked(_parked.value))
    }

    override fun stop() = Unit

    override fun navigateTo(place: Place): Boolean {
        setDestination(place)
        return true
    }

    fun setDestination(place: Place?) {
        _destination.value = place
        _events.tryEmit(CarEvent.Destination(place))
    }

    /** 주차 브레이크 해제 → 출발 */
    fun depart() {
        if (_parked.value) {
            _parked.value = false
            // 실제 차량처럼 출발 순간의 현재 위치를 먼저 알려, 출발지 이름이 채워지게 합니다.
            _events.tryEmit(CarEvent.Location(here))
            _events.tryEmit(CarEvent.Parked(false))
        }
    }

    fun drive(km: Double) {
        depart()
        _odometer.value = ((_odometer.value + km) * 10).toLong() / 10.0
        _events.tryEmit(CarEvent.Odometer(_odometer.value))
    }

    fun approach() {
        _events.tryEmit(CarEvent.Remaining(290))
    }

    fun arrive() {
        _events.tryEmit(CarEvent.Arrived(_destination.value))
    }

    /** 주차 브레이크 체결 */
    fun park() {
        if (!_parked.value) {
            _parked.value = true
            // 목적지에 세웠으면 그곳이 새 현재 위치예요. (목적지 없이 세우면 위치를 모르는 것으로 둡니다.)
            _destination.value?.let {
                here = it
                _events.tryEmit(CarEvent.Location(it))
            }
            _events.tryEmit(CarEvent.Parked(true))
        }
    }

    fun driverDoorClosed() {
        _events.tryEmit(CarEvent.DriverDoorClosed)
    }
}
