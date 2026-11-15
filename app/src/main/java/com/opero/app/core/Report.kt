package com.opero.app.core

import android.content.Context
import com.opero.app.data.Expense
import com.opero.app.data.Trip
import com.opero.app.data.TripType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MonthSummary(
    val businessKm: Double,
    val commuteKm: Double,
    val personalKm: Double,
    val unconfirmedKm: Double,
    val totalKm: Double,
    val businessRatio: Int,
    val businessTrips: Int,
    val purposeRecorded: Int,
    val expenseTotal: Int,
)

object Report {

    fun monthRange(year: Int, month0: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month0, 1, 0, 0, 0)
        val from = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return from to cal.timeInMillis - 1
    }

    fun dayRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        return from to from + 24 * 3600_000L - 1
    }

    fun summarize(trips: List<Trip>, expenses: List<Expense>): MonthSummary {
        val closed = trips.filter { it.type != TripType.OPEN }
        fun km(type: String) = closed.filter { it.type == type }.sumOf { it.distanceKm }
        val business = km(TripType.BUSINESS)
        val commute = km(TripType.COMMUTE)
        val personal = km(TripType.PERSONAL)
        val unconfirmed = km(TripType.UNCONFIRMED)
        val total = closed.sumOf { it.distanceKm }
        val businessTrips = closed.filter { it.type == TripType.BUSINESS }
        return MonthSummary(
            businessKm = business,
            commuteKm = commute,
            personalKm = personal,
            unconfirmedKm = unconfirmed,
            totalKm = total,
            // 국세청 기준 업무사용비율 = 업무용 사용거리(출퇴근 + 일반업무) / 총 주행거리
            businessRatio = if (total > 0) (((business + commute) / total) * 100).toInt() else 0,
            businessTrips = businessTrips.size,
            purposeRecorded = businessTrips.count { !it.purpose.isNullOrBlank() },
            expenseTotal = expenses.sumOf { it.amount },
        )
    }

    /** 업무용승용차 운행기록부 항목 순서의 CSV(엑셀에서 바로 열리도록 UTF-8 BOM)를 만듭니다. */
    fun exportCsv(context: Context, monthLabel: String, trips: List<Trip>, settings: OperoSettings): File {
        val day = SimpleDateFormat("yyyy-MM-dd (E)", Locale.KOREA)
        val sb = StringBuilder("﻿")
        sb.append("사용일자,부서,성명,주행 전 계기판의 거리(km),주행 후 계기판의 거리(km),주행거리(km),출퇴근용(km),일반 업무용(km),비고\n")
        trips.filter { it.type != TripType.OPEN }.forEach { t ->
            val d = String.format(Locale.US, "%.1f", t.distanceKm)
            val commute = if (t.type == TripType.COMMUTE) d else ""
            val business = if (t.type == TripType.BUSINESS) d else ""
            val note = when (t.type) {
                TripType.BUSINESS -> "${t.endPlace ?: ""} ${t.purpose ?: ""}".trim()
                TripType.PERSONAL -> "개인 사용"
                TripType.UNCONFIRMED -> "확인 필요"
                else -> t.purpose ?: ""
            }
            sb.append(listOf(
                day.format(Date(t.startTime)), settings.department, settings.userName,
                String.format(Locale.US, "%.1f", t.startOdoKm),
                String.format(Locale.US, "%.1f", t.endOdoKm ?: t.startOdoKm),
                d, commute, business, note,
            ).joinToString(",") { csv(it) }).append('\n')
        }
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "opero_운행기록부_$monthLabel.csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun csv(value: String): String =
        if (value.contains(',') || value.contains('"')) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
