package com.opero.app.car

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 방문지(거래처) 정보. 내비게이션 목적지 정보에서 채워집니다. */
data class Place(
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** 앱이 사용하는 차량·내비게이션 이벤트. 실제 차량과 데모 모드가 같은 이벤트를 냅니다. */
sealed interface CarEvent {
    /** 정차(주차 브레이크 체결 또는 P단) 여부 */
    data class Parked(val parked: Boolean) : CarEvent
    /** 차량 주행거리계 누적 거리(km) */
    data class Odometer(val km: Double) : CarEvent
    /** 운전석 문이 열렸다가 닫힘 */
    data object DriverDoorClosed : CarEvent
    /** 내비게이션 목적지 설정(null이면 취소) */
    data class Destination(val place: Place?) : CarEvent
    /** 목적지 도착 */
    data class Arrived(val place: Place?) : CarEvent
    /** 목적지까지 남은 시간(초) */
    data class Remaining(val seconds: Int) : CarEvent
    /** 현재 위치(내비게이션 현재 위치 정보). 출발지·도착지 이름에 씁니다. */
    data class Location(val place: Place) : CarEvent
}

interface CarSource {
    val events: SharedFlow<CarEvent>
    val status: StateFlow<String>
    fun start()
    fun stop()
    /** 해당 장소로 경로 안내를 요청합니다. 요청하지 못하면 false. */
    fun navigateTo(place: Place): Boolean
}
