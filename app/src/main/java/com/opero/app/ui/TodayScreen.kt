package com.opero.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opero.app.OperoApp
import com.opero.app.core.DriveState
import com.opero.app.core.Report
import com.opero.app.data.Memo
import com.opero.app.data.Trip
import com.opero.app.data.TripType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 캔버스 3번 '오늘의 운행' */
@Composable
fun TodayScreen(app: OperoApp, drive: DriveState, status: String) {
    val range = remember { Report.dayRange() }
    val trips by remember { app.repo.tripsBetween(range.first, range.second) }.collectAsStateWithLifecycle(emptyList())
    val expenses by remember { app.repo.expensesBetween(range.first, range.second) }.collectAsStateWithLifecycle(emptyList())
    val summary = Report.summarize(trips, expenses)
    val time = SimpleDateFormat("HH:mm", Locale.KOREA)

    var nextMemo by remember { mutableStateOf<Memo?>(null) }
    LaunchedEffect(drive.destination) {
        nextMemo = drive.destination?.let { app.repo.clientByName(it.name) }?.let { app.repo.latestMemo(it.id) }
    }

    var editing by remember { mutableStateOf<Trip?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Opero · " + SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(Date()), color = Palette.Sub, fontSize = 15.sp)
                Text("오늘의 운행", color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("업무 거리", km(summary.businessKm + summary.commuteKm))
                Stat("개인 거리", km(summary.personalKm))
                Stat("경비", String.format(Locale.KOREA, "%,d원", summary.expenseTotal))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(if (drive.parked) "P · 정차 중" else "주행 중 · 구간 기록 중", bg = if (drive.parked) Palette.AmberSoft else Palette.BlueSoft, fg = if (drive.parked) Palette.AmberText else Palette.BlueText)
            drive.odometerKm?.let { Chip(String.format(Locale.KOREA, "계기판 %,.1f km", it), bg = Palette.Surface2, fg = Palette.Body) }
            if (drive.personalMode) Chip("개인 용무 기록 중", bg = Palette.Surface2, fg = Palette.Body)
            Text(status, color = Palette.Sub, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val closed = trips.filter { it.type != TripType.OPEN }.reversed()
                val unconfirmed = closed.count { it.type == TripType.UNCONFIRMED }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("기록된 구간")
                    if (unconfirmed > 0) {
                        Chip(
                            if (drive.parked) "미확인 ${unconfirmed}건 · 눌러서 정리" else "미확인 ${unconfirmed}건 · 정차 후 정리",
                            bg = Palette.AmberSoft, fg = Palette.AmberText,
                        )
                    }
                }
                if (closed.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Text("아직 오늘 기록된 운행이 없어요. 출발하면 자동으로 기록이 시작돼요.", color = Palette.Body, fontSize = 16.sp)
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(closed, key = { it.id }) { t ->
                        // 정차 중에만 눌러서 구분·목적을 고치거나 삭제할 수 있어요.
                        Card(Modifier.fillMaxWidth().clickable(enabled = drive.parked) { editing = t }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("${time.format(Date(t.startTime))}–${t.endTime?.let { time.format(Date(it)) } ?: ""}", color = Palette.Sub, fontSize = 15.sp, modifier = Modifier.width(110.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${t.startPlace} → ${t.endPlace ?: ""}", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text(t.purpose ?: "방문 목적 확인 필요", color = Palette.Body, fontSize = 14.sp)
                                }
                                Text(km(t.distanceKm), color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                val business = t.type == TripType.BUSINESS || t.type == TripType.COMMUTE
                                Chip(TripType.label(t.type), bg = if (business) Palette.BlueSoft else Palette.Surface2, fg = if (business) Palette.BlueText else Palette.Body)
                            }
                        }
                    }
                }
            }
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("다음 방문")
                Card(Modifier.fillMaxWidth()) {
                    val dest = drive.destination
                    if (dest == null) {
                        Text("설정된 목적지가 없어요", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("내비게이션에서 목적지를 정하면 지난 메모를 도착 전에 들려드려요.", color = Palette.Body, fontSize = 14.sp)
                    } else {
                        Text(dest.name, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        dest.address?.let { Text(it, color = Palette.Sub, fontSize = 14.sp) }
                        val memo = nextMemo
                        if (memo != null) {
                            Card(color = Palette.Surface2) {
                                Text("지난 메모 · " + SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(memo.createdAt)), color = Palette.AmberText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(memo.text, color = Palette.Text, fontSize = 15.sp)
                            }
                        }
                    }
                }
                SecondaryButton(if (drive.personalMode) "개인 용무 끝" else "개인 용무 시작", Modifier.fillMaxWidth(), height = 56) {
                    app.session.togglePersonalMode()
                }
                Text("구간을 누르면 구분과 목적을 고칠 수 있어요. 기록 수정과 리포트는 정차 중에만 열려요.", color = Palette.Sub, fontSize = 13.sp)
            }
        }
    }
    // 주행을 시작하면 수정 창은 자동으로 닫혀요.
    LaunchedEffect(drive.parked) { if (!drive.parked) editing = null }
    editing?.let { t -> TripEditor(app, t) { editing = null } }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Card(Modifier.width(130.dp)) {
        Text(label, color = Palette.Sub, fontSize = 13.sp)
        Text(value, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

fun km(v: Double): String = String.format(Locale.KOREA, "%.1f km", v)
