package com.opero.app.data

import com.opero.app.car.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** 운행 기록·거래처·메모·경비 저장소. 모든 데이터는 차량(기기) 안에만 저장합니다. */
class Repository(private val db: OperoDb) {

    suspend fun openTrip(): Trip? = db.trips().openTrip()

    suspend fun startTrip(now: Long, odoKm: Double, startPlace: String): Trip {
        val trip = Trip(startTime = now, startOdoKm = odoKm, startPlace = startPlace)
        val id = db.trips().insert(trip)
        return trip.copy(id = id)
    }

    suspend fun updateTrip(trip: Trip) = db.trips().update(trip)

    suspend fun trip(id: Long): Trip? = db.trips().byId(id)

    suspend fun deleteTrip(trip: Trip) = db.trips().delete(trip)

    /** 시연용 샘플 기록(지난 4주)을 채웁니다. */
    suspend fun fillSample() = withContext(Dispatchers.IO) { SampleData.fill(db) }

    /** 모든 운행·거래처·메모·경비 기록을 지웁니다. */
    suspend fun clearAll() = withContext(Dispatchers.IO) { db.clearAllTables() }

    /**
     * 기록 수정 화면에서 구간의 구분과 목적을 바꿉니다.
     * 도착지가 등록된 거래처면 그 거래처의 '지난 목적'도 함께 갱신해, 다음 방문 때 제안에 쓰입니다.
     */
    suspend fun confirmTrip(trip: Trip, type: String, purpose: String?) {
        val client = trip.endPlace?.let { clientByName(it) }
        db.trips().update(trip.copy(type = type, purpose = purpose, clientId = trip.clientId ?: client?.id))
        if (type == TripType.BUSINESS && client != null && !purpose.isNullOrBlank()) {
            updateClient(client.copy(lastPurpose = purpose))
        }
    }

    fun tripsBetween(from: Long, to: Long): Flow<List<Trip>> = db.trips().between(from, to)

    fun tripsForClient(clientId: Long): Flow<List<Trip>> = db.trips().forClient(clientId)

    /** 도착지를 거래처로 등록(이미 있으면 위치 정보만 보완)하고 반환합니다. */
    suspend fun upsertClient(place: Place): Client {
        val existing = db.clients().byName(place.name)
        if (existing != null) {
            val merged = existing.copy(
                address = existing.address ?: place.address,
                lat = existing.lat ?: place.lat,
                lng = existing.lng ?: place.lng,
            )
            if (merged != existing) db.clients().update(merged)
            return merged
        }
        val client = Client(name = place.name, address = place.address, lat = place.lat, lng = place.lng)
        val id = db.clients().insert(client)
        return if (id > 0) client.copy(id = id) else db.clients().byName(place.name)!!
    }

    suspend fun clientByName(name: String): Client? = db.clients().byName(name)

    suspend fun client(id: Long): Client? = db.clients().byId(id)

    suspend fun updateClient(client: Client) = db.clients().update(client)

    fun clients(): Flow<List<Client>> = db.clients().all()

    suspend fun addMemo(clientId: Long, tripId: Long?, now: Long, text: String) {
        db.memos().insert(Memo(clientId = clientId, tripId = tripId, createdAt = now, text = text))
    }

    suspend fun latestMemo(clientId: Long): Memo? = db.memos().latest(clientId)

    fun memos(clientId: Long): Flow<List<Memo>> = db.memos().forClient(clientId)

    fun memoCount(clientId: Long): Flow<Int> = db.memos().countFor(clientId)

    suspend fun addExpense(tripId: Long?, now: Long, category: String, amount: Int) {
        db.expenses().insert(Expense(tripId = tripId, createdAt = now, category = category, amount = amount))
    }

    fun expensesBetween(from: Long, to: Long): Flow<List<Expense>> = db.expenses().between(from, to)
}
