package com.opero.app.car

import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 실제 차량(Pleos Connect) 이벤트 소스.
 * - 정차 판단: AAOS 표준 차량 속성 PARKING_BRAKE_ON, GEAR_SELECTION
 * - 주행거리·문: Pleos Vehicle SDK (Odometer, Door)
 * - 목적지·도착·남은 시간: Pleos NaviHelper SDK
 */
class PleosCarSource(
    private val context: Context,
    private val scope: CoroutineScope,
) : CarSource {

    private val _events = MutableSharedFlow<CarEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<CarEvent> = _events.asSharedFlow()
    private val _status = MutableStateFlow("차량 연결 중")
    override val status: StateFlow<String> = _status.asStateFlow()

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var propertyCallback: CarPropertyManager.CarPropertyEventCallback? = null
    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var naviListener: NaviHelperEventListener? = null
    private var pollJob: Job? = null

    private var parkingBrakeOn: Boolean? = null
    private var gearPark: Boolean? = null
    private var lastParked: Boolean? = null
    private var lastDoorPosition: Int? = null
    private var destination: Place? = null
    private val problems = mutableListOf<String>()

    override fun start() {
        startPowertrain()
        startVehicleSdk()
        startNavi()
    }

    override fun stop() {
        pollJob?.cancel()
        runCatching { propertyCallback?.let { propertyManager?.unregisterCallback(it) } }
        runCatching { car?.disconnect() }
        runCatching { naviListener?.let { navi?.removeListener(it) } }
        runCatching { navi?.release() }
        runCatching { vehicle?.release() }
    }

    // ---- 정차 판단 (AAOS 표준 속성) ----
    private fun startPowertrain() {
        try {
            val c = Car.createCar(context) ?: error("Car service unavailable")
            car = c
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            propertyManager = pm
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    when (value.propertyId) {
                        VehiclePropertyIds.PARKING_BRAKE_ON -> parkingBrakeOn = value.value as? Boolean
                        VehiclePropertyIds.GEAR_SELECTION -> gearPark = (value.value as? Int) == GEAR_PARK
                    }
                    emitParkedIfChanged()
                }

                override fun onErrorEvent(propertyId: Int, zone: Int) {
                    Log.w(TAG, "property error $propertyId")
                }
            }
            propertyCallback = cb
            settleUntil = System.currentTimeMillis() + 2_000
            pm.registerCallback(cb, VehiclePropertyIds.PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            pm.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (t: Throwable) {
            report("주차 상태", t)
        }
    }

    /** 시작 직후 두 값 중 하나만 먼저 들어와 '출발→정차'로 잘못 판단하지 않도록, 둘 다 오거나 2초가 지날 때까지 기다립니다. */
    private var settleUntil = 0L

    private fun emitParkedIfChanged() {
        val brake = parkingBrakeOn
        val gear = gearPark
        if (brake == null && gear == null) return
        if ((brake == null || gear == null) && System.currentTimeMillis() < settleUntil) {
            scope.launch {
                delay(settleUntil - System.currentTimeMillis() + 50)
                emitParkedIfChanged()
            }
            return
        }
        val parked = brake == true || gear == true
        if (parked != lastParked) {
            lastParked = parked
            requestLocation()
            _events.tryEmit(CarEvent.Parked(parked))
        }
    }

    // ---- 주행거리·문 (Pleos Vehicle SDK) ----
    private fun startVehicleSdk() {
        try {
            val v = Vehicle(context)
            v.initialize()
            vehicle = v
        } catch (t: Throwable) {
            report("Vehicle SDK", t)
            return
        }
        // Vehicle Service 연결은 비동기라, 도메인 등록을 몇 번 재시도합니다.
        // 미팅 후 '다시 탔다'는 신호는 운전석 문 닫힘을 우선 쓰고, 안 되면 안전벨트 체결로 대신합니다.
        scope.launch {
            var odometerOk = false
            var doorOk = false
            var beltOk = false
            repeat(5) {
                val v = vehicle ?: return@launch
                if (!odometerOk) odometerOk = runCatching {
                    val odometer = Reflect.call(v, "getOdometer")!!
                    val onDistance: (String, Array<out Any?>) -> Unit = { name, args ->
                        Diag.set("주행거리", "$name(${Reflect.show(args)})")
                        if (!name.contains("Fail")) distanceFrom(args)?.let { _events.tryEmit(CarEvent.Odometer(it)) }
                    }
                    val ok = Reflect.register(odometer, "registerDistanceDriven", onDistance) != null
                    // 값이 바뀔 때만 콜백이 오므로, 현재 값은 한 번 따로 요청합니다.
                    if (ok) Reflect.register(odometer, "getCurrentDistanceDriven", onDistance)
                    ok
                }.getOrElse { Diag.add("✗ 주행거리: $it"); false }
                if (!doorOk) doorOk = runCatching {
                    val door = Reflect.call(v, "getDoor")!!
                    Reflect.register(door, "registerDoorPosition") { name, args ->
                        Diag.set("문", "$name(${Reflect.show(args)})")
                        if (!name.contains("Fail")) onDoorPosition(doorValue(args))
                    } != null
                }.getOrElse { Diag.add("✗ 문: $it"); false }
                if (!doorOk && !beltOk) beltOk = runCatching {
                    val seat = Reflect.call(v, "getSeat")!!
                    Reflect.register(seat, "registerSeatBeltBuckledState") { name, args ->
                        Diag.set("안전벨트", "$name(${Reflect.show(args)})")
                        if (!name.contains("Fail")) onSeatBelt(beltValue(args))
                    } != null
                }.getOrElse { Diag.add("✗ 안전벨트: $it"); false }
                if (odometerOk && (doorOk || beltOk)) {
                    updateStatus()
                    return@launch
                }
                delay(1_000)
            }
            Diag.add("Vehicle 메서드: " + (vehicle?.javaClass?.methods?.map { it.name }?.filter { it.startsWith("get") && it != "getClass" }?.distinct()?.sorted()?.joinToString() ?: "-"))
            if (!odometerOk) problems += "주행거리"
            if (!doorOk && !beltOk) problems += "탑승 감지(문·안전벨트)"
            updateStatus()
        }
    }

    /** 주행거리 콜백 값: 숫자 인자 중 가장 큰 값(단위 코드 0·1 같은 값은 제외), 없으면 데이터 객체 안의 거리 값. */
    private fun distanceFrom(args: Array<out Any?>): Double? {
        args.filterIsInstance<Number>().map { it.toDouble() }.maxOrNull()?.let { return it }
        val obj = args.firstOrNull { it != null && it !is Number && it !is Boolean } ?: return null
        return Reflect.double(obj, "distanceDriven", "distance", "odometer", "value", "km")
    }

    /** 문 콜백 값: 숫자면 그대로, Boolean(열림)이면 1/0, 객체면 안의 position/value 값. */
    private fun doorValue(args: Array<out Any?>): Int? {
        args.lastOrNull { it is Number }?.let { return (it as Number).toInt() }
        args.lastOrNull { it is Boolean }?.let { return if (it as Boolean) 1 else 0 }
        val obj = args.firstOrNull { it != null } ?: return null
        (Reflect.prop(obj, "position", "value", "percent") as? Number)?.let { return it.toInt() }
        (Reflect.prop(obj, "open", "isOpen", "opened") as? Boolean)?.let { return if (it) 1 else 0 }
        return null
    }

    private fun beltValue(args: Array<out Any?>): Boolean? {
        args.lastOrNull { it is Boolean }?.let { return it as Boolean }
        args.lastOrNull { it is Number }?.let { return (it as Number).toInt() != 0 }
        val obj = args.firstOrNull { it != null } ?: return null
        (Reflect.prop(obj, "buckled", "isBuckled", "state", "value") as? Boolean)?.let { return it }
        val text = obj.toString().uppercase()
        return when {
            "UNBUCKLED" in text || "UNLATCH" in text -> false
            "BUCKLED" in text || "LATCH" in text -> true
            else -> null
        }
    }

    private fun onDoorPosition(position: Int?) {
        val previous = lastDoorPosition
        lastDoorPosition = position
        if (previous != null && previous > 0 && (position ?: 0) == 0) {
            _events.tryEmit(CarEvent.DriverDoorClosed)
        }
    }

    private var lastBelt: Boolean? = null

    /** 안전벨트가 풀려 있다가 채워지면 '다시 탑승'으로 봅니다. */
    private fun onSeatBelt(buckled: Boolean?) {
        val previous = lastBelt
        lastBelt = buckled
        if (previous == false && buckled == true) {
            _events.tryEmit(CarEvent.DriverDoorClosed)
        }
    }

    // ---- 목적지·도착 (Pleos NaviHelper SDK) ----
    private fun startNavi() {
        try {
            val helper = NaviHelper(context.applicationContext)
            navi = helper
            helper.initialize(
                onConnected = {
                    val listener = Reflect.proxy(NaviHelperEventListener::class.java) { name, args ->
                        onNaviEvent(name, args)
                    } as NaviHelperEventListener
                    naviListener = listener
                    helper.addListener(listener)
                    Reflect.callQuiet(helper, "getDestinationInfo")
                    startPolling()
                    updateStatus()
                },
                onFailed = { t -> report("NaviHelper", t) },
            )
        } catch (t: Throwable) {
            report("NaviHelper", t)
        }
    }

    private fun onNaviEvent(name: String, args: Array<out Any?>) {
        val data = args.firstOrNull()
        when (name) {
            "onDestinationInfo" -> {
                destination = placeFrom(data)
                _events.tryEmit(CarEvent.Destination(destination))
            }
            "onRouteStarted", "onDestinationChanged", "onRoutingCompleted", "onReRouteEnded" ->
                Reflect.callQuiet(navi, "getDestinationInfo")
            "onRouteCancelled" -> {
                destination = null
                _events.tryEmit(CarEvent.Destination(null))
            }
            "onDestinationArrived", "onWayPointArrived" ->
                _events.tryEmit(CarEvent.Arrived(placeFrom(data) ?: destination))
            "onRouteStateInfo", "onTripInfo", "onDrivingInfo" -> remainingSeconds(data)?.let {
                _events.tryEmit(CarEvent.Remaining(it))
            }
            "onCurrentLocationInfo" -> {
                Diag.set("현재 위치", Reflect.show(args))
                locationFrom(data)?.let { _events.tryEmit(CarEvent.Location(it)) }
            }
        }
    }

    /** 경로 정보 객체에서 '남은 시간'으로 보이는 값을 찾아 초 단위로 바꿉니다. */
    private fun remainingSeconds(info: Any?): Int? {
        val (key, value) = Reflect.findNumber(info, "remain", "time")
            ?: Reflect.findNumber(info, "time", "dest")
            ?: return null
        val seconds = when {
            key.contains("min") -> value * 60
            key.contains("milli") || key.endsWith("ms") -> value / 1000
            else -> value
        }
        return seconds.toInt().takeIf { it >= 0 }
    }

    private fun placeFrom(info: Any?): Place? {
        if (info == null) return null
        val name = Reflect.string(info, "poiName", "name", "destinationName", "title") ?: return null
        return Place(
            name = name,
            address = Reflect.string(info, "address", "addr"),
            lat = Reflect.double(info, "latitude", "lat"),
            lng = Reflect.double(info, "longitude", "lon", "lng"),
        )
    }

    /**
     * 현재 위치 정보에서 짧은 장소 이름을 만듭니다.
     * POI 이름이 있으면 그대로, 없으면 주소 뒤쪽(예: "강남구 테헤란로 123")을 씁니다.
     */
    private fun locationFrom(info: Any?): Place? {
        if (info == null) return null
        val poi = Reflect.string(info, "poiName", "placeName", "name", "title")
        val address = Reflect.string(info, "address", "roadAddress", "addr", "roadName")
        val name = poi ?: address?.let { shortAddress(it) } ?: return null
        return Place(
            name = name,
            address = address,
            lat = Reflect.double(info, "latitude", "lat"),
            lng = Reflect.double(info, "longitude", "lon", "lng"),
        )
    }

    private fun shortAddress(address: String): String {
        val parts = address.trim().split(Regex("\\s+"))
        return if (parts.size > 3) parts.takeLast(3).joinToString(" ") else address.trim()
    }

    /** 현재 위치를 한 번 요청합니다. 결과는 onCurrentLocationInfo 콜백으로 들어옵니다. */
    private fun requestLocation() {
        Reflect.callQuiet(navi, "getCurrentLocationInfo")
    }

    /** 현재 위치와(목적지가 있으면) 남은 시간 정보를 주기적으로 요청합니다. 결과는 콜백으로 들어옵니다. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                requestLocation()
                if (destination != null) {
                    Reflect.callQuiet(navi, "getRouteStateInfo")
                    Reflect.callQuiet(navi, "getTripInfo")
                }
                delay(15_000)
            }
        }
    }

    override fun navigateTo(place: Place): Boolean {
        val helper = navi ?: return false
        val lat = place.lat ?: return false
        val lng = place.lng ?: return false
        return try {
            val routeInfoClass = Class.forName("ai.pleos.playground.navi.data.RouteInfo")
            val ctor = routeInfoClass.constructors.maxByOrNull { it.parameterTypes.size } ?: return false
            // 문서의 Java 예제 순서: longitude, latitude, displayLongitude, displayLatitude,
            // poiName, poiId, address, poiSubId, RouteOption
            val values = mutableListOf<Any?>(lng, lat, null, null, place.name, "", place.address ?: "", "0")
            if (ctor.parameterTypes.size >= 9) {
                val optionType = ctor.parameterTypes[8]
                values += optionType.enumConstants?.firstOrNull { (it as Enum<*>).name == "RECOMMENDED" }
            }
            val routeInfo = ctor.newInstance(*values.take(ctor.parameterTypes.size).toTypedArray())
            Reflect.call(helper, "requestRoute", routeInfo)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "requestRoute failed: $t")
            false
        }
    }

    private fun report(what: String, t: Throwable) {
        Log.w(TAG, "$what unavailable: $t")
        problems += what
        updateStatus()
    }

    private fun updateStatus() {
        _status.value = if (problems.isEmpty()) "차량 연결됨" else "일부 연결 안 됨: ${problems.distinct().joinToString()}"
    }

    companion object {
        private const val TAG = "OperoCar"
        private const val GEAR_PARK = 0x0004
    }
}
