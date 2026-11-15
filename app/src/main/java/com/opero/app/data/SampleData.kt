package com.opero.app.data

import java.util.Calendar
import kotlin.random.Random

/**
 * 시연용 샘플 기록: 지난 4주 평일의 영업 동선(출퇴근·거래처 방문·점심 개인 용무)과 메모·경비.
 * 데모 차량의 계기판(24,512 km)보다 앞선 주행거리로 채워, 이어서 데모를 돌려도 숫자가 자연스럽게 이어집니다.
 * 거래처 이름은 데모 패널의 목적지와 같아서, 데모에서 목적지를 고르면 지난 메모 브리핑이 바로 나옵니다.
 */
object SampleData {
    private data class Site(val name: String, val address: String, val lat: Double, val lng: Double, val purpose: String)

    private val sites = listOf(
        Site("○○상사", "경기 성남시 분당구", 37.3947, 127.1112, "정기 방문"),
        Site("□□전자", "경기 수원시 영통구", 37.2565, 127.0518, "견적 상담"),
        Site("△△산업", "서울 금천구 가산동", 37.4784, 126.8826, "납품"),
        Site("◇◇물산", "서울 성동구 성수동", 37.5446, 127.0557, "신규 미팅"),
    )

    /** 회사에서 각 거래처까지, 거래처 사이 대략 거리(km) */
    private val fromOffice = mapOf("○○상사" to 18.4, "□□전자" to 31.2, "△△산업" to 16.8, "◇◇물산" to 9.6)

    private val memos = mapOf(
        "○○상사" to listOf(
            "김 과장님이 다음 분기 단가 재협상 원하심. 견적서 수정본 준비할 것.",
            "신제품 샘플 반응 좋음. 다음 주에 구매팀 미팅 잡아 주신다고 함.",
            "지난번 요청한 수정 견적 전달 완료. 결재는 월말 예정.",
        ),
        "□□전자" to listOf(
            "이 부장님 연말 예산 확정 전. 12월 첫 주에 다시 연락 달라고 하심.",
            "경쟁사 대비 납기 문의 많음. 납기 일정표 챙겨 갈 것.",
        ),
        "△△산업" to listOf(
            "납품 수량 20% 늘리고 싶다고 함. 재고 확인 후 회신.",
            "검수 담당자 바뀜. 새 담당 박 대리님 연락처 받음.",
        ),
        "◇◇물산" to listOf(
            "첫 미팅. 소개 자료 반응 괜찮음. 시범 도입 조건 정리해서 메일 보내기.",
        ),
    )

    suspend fun fill(db: OperoDb, now: Long = System.currentTimeMillis()) {
        val rnd = Random(7)
        val clientIds = HashMap<String, Long>()
        for (s in sites) {
            val existing = db.clients().byName(s.name)
            clientIds[s.name] = existing?.id
                ?: db.clients().insert(Client(name = s.name, address = s.address, lat = s.lat, lng = s.lng))
        }
        val memoIndex = HashMap<String, Int>()

        // 4주 전 월요일부터 어제까지의 평일
        val days = ArrayList<Calendar>()
        val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -28) }
        while (true) {
            c.add(Calendar.DAY_OF_YEAR, 1)
            if (sameDay(c.timeInMillis, now) || c.timeInMillis > now) break
            val dow = c.get(Calendar.DAY_OF_WEEK)
            if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) days += c.clone() as Calendar
        }

        // 마지막 날 퇴근 후 계기판이 24,500 km 근처가 되도록 거꾸로 시작값을 잡습니다.
        var odo = 24_500.0 - days.size * 95.0
        for ((i, day) in days.withIndex()) {
            fun at(h: Int, m: Int): Long = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            suspend fun trip(start: Long, mins: Int, from: String, to: String, km: Double, type: String, purpose: String? = null): Long {
                val t = Trip(
                    startTime = start, endTime = start + mins * 60_000L,
                    startOdoKm = odo, endOdoKm = odo + km,
                    startPlace = from, endPlace = to,
                    clientId = clientIds[to], type = type, purpose = purpose, memoAsked = true,
                )
                odo += km
                return db.trips().insert(t)
            }

            // 출근
            trip(at(8, 10), 35, "집", "회사", 14.2, TripType.COMMUTE)
            // 오전·오후 거래처 두 곳
            val first = sites[i % sites.size]
            val second = sites[(i + 1 + i / sites.size) % sites.size].let { if (it == first) sites[(i + 2) % sites.size] else it }
            val k1 = fromOffice.getValue(first.name) + rnd.nextDouble(-1.0, 1.0)
            val t1 = trip(at(9, 40), 40, "회사", first.name, k1, TripType.BUSINESS, first.purpose)
            addMemo(db, clientIds.getValue(first.name), t1, at(11, 5), first.name, memoIndex)
            if (rnd.nextInt(3) == 0) db.expenses().insert(Expense(tripId = t1, createdAt = at(11, 10), category = "주차비", amount = listOf(2_000, 3_000, 4_000).random(rnd)))

            // 점심 개인 용무(가끔)
            if (i % 3 == 1) trip(at(12, 10), 12, first.name, "식당", 3.1, TripType.PERSONAL)

            val k2 = (fromOffice.getValue(first.name) + fromOffice.getValue(second.name)) * 0.6 + rnd.nextDouble(-1.5, 1.5)
            val t2 = trip(at(13, 30), 45, first.name, second.name, k2, TripType.BUSINESS, second.purpose)
            addMemo(db, clientIds.getValue(second.name), t2, at(15, 0), second.name, memoIndex)
            if (k2 > 20 && rnd.nextBoolean()) db.expenses().insert(Expense(tripId = t2, createdAt = at(13, 30), category = "통행료", amount = listOf(1_900, 2_800, 4_800).random(rnd)))

            trip(at(16, 20), 40, second.name, "회사", fromOffice.getValue(second.name) + rnd.nextDouble(-1.0, 1.0), TripType.BUSINESS, "복귀")
            // 퇴근
            trip(at(18, 30), 38, "회사", "집", 14.4, TripType.COMMUTE)

            if (i % 6 == 4) db.expenses().insert(Expense(createdAt = at(17, 0), category = "주유·충전", amount = 52_000))
        }

        // 거래처의 지난 목적·방문 시각 갱신 (다음 방문 때 "정기 방문으로 기록할까요?" 제안에 쓰임)
        for (s in sites) {
            val id = clientIds.getValue(s.name)
            db.clients().byId(id)?.let { db.clients().update(it.copy(lastPurpose = s.purpose, lastVisit = now - 86_400_000L)) }
        }
    }

    private suspend fun addMemo(db: OperoDb, clientId: Long, tripId: Long, at: Long, name: String, index: HashMap<String, Int>) {
        val list = memos[name] ?: return
        val i = index[name] ?: 0
        if (i >= list.size) return // 같은 메모를 반복하지 않도록, 준비한 메모를 다 쓰면 그 거래처는 메모 없이 기록
        index[name] = i + 1
        db.memos().insert(Memo(clientId = clientId, tripId = tripId, createdAt = at, text = list[i]))
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val x = Calendar.getInstance().apply { timeInMillis = a }
        val y = Calendar.getInstance().apply { timeInMillis = b }
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    }
}
