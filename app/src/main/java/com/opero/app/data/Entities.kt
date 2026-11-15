package com.opero.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 운행 구간의 구분. 국세청 운행기록부의 '출퇴근용 / 일반 업무용' 구분과 맞춥니다. */
object TripType {
    const val OPEN = "OPEN"               // 주행 중(아직 끝나지 않음)
    const val BUSINESS = "BUSINESS"       // 일반 업무용
    const val COMMUTE = "COMMUTE"         // 출퇴근용
    const val PERSONAL = "PERSONAL"       // 개인 용무
    const val UNCONFIRMED = "UNCONFIRMED" // 대답이 없어 확인이 필요한 구간

    fun label(type: String): String = when (type) {
        BUSINESS -> "일반 업무"
        COMMUTE -> "출퇴근"
        PERSONAL -> "개인"
        UNCONFIRMED -> "미확인"
        else -> "주행 중"
    }
}

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startOdoKm: Double,
    val endOdoKm: Double? = null,
    val startPlace: String,
    val endPlace: String? = null,
    val clientId: Long? = null,
    val type: String = TripType.OPEN,
    val purpose: String? = null,
    val memoAsked: Boolean = false,
) {
    val distanceKm: Double
        get() = ((endOdoKm ?: startOdoKm) - startOdoKm).coerceAtLeast(0.0)
}

@Entity(tableName = "clients", indices = [Index(value = ["name"], unique = true)])
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val lastPurpose: String? = null,
    val lastVisit: Long? = null,
)

@Entity(tableName = "memos")
data class Memo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val tripId: Long? = null,
    val createdAt: Long,
    val text: String,
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val createdAt: Long,
    val category: String,
    val amount: Int,
)
