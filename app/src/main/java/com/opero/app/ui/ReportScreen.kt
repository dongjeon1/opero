package com.opero.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opero.app.OperoApp
import com.opero.app.core.Report
import com.opero.app.data.TripType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 캔버스 4번 '월간 리포트 · 운행기록부' */
@Composable
fun ReportScreen(app: OperoApp) {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var offset by remember { mutableIntStateOf(0) }
    val cal = (now.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
    val year = cal.get(Calendar.YEAR)
    val month0 = cal.get(Calendar.MONTH)
    val range = Report.monthRange(year, month0)
    val trips by remember(range) { app.repo.tripsBetween(range.first, range.second) }.collectAsStateWithLifecycle(emptyList())
    val expenses by remember(range) { app.repo.expensesBetween(range.first, range.second) }.collectAsStateWithLifecycle(emptyList())
    val s = Report.summarize(trips, expenses)
    val day = SimpleDateFormat("M.d (E)", Locale.KOREA)
    val closed = trips.filter { it.type != TripType.OPEN }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("월간 리포트", color = Palette.Sub, fontSize = 15.sp)
                Text("${year}년 ${month0 + 1}월 운행기록부", color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("◀", height = 56) { offset -= 1 }
                SecondaryButton("▶", height = 56) { if (offset < 0) offset += 1 }
                PrimaryButton("CSV로 내보내기", height = 56) {
                    val label = String.format(Locale.US, "%d-%02d", year, month0 + 1)
                    val file = Report.exportCsv(context, label, closed, app.settings.value)
                    Toast.makeText(context, "저장했어요: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1.6f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label("업무 사용 비율")
                    Text("${s.businessRatio}%", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Palette.Line)) {
                    Box(Modifier.fillMaxWidth(s.businessRatio / 100f).fillMaxHeight().background(Palette.Blue))
                }
                Text("업무 ${km(s.businessKm)} · 출퇴근 ${km(s.commuteKm)} · 개인 ${km(s.personalKm)} · 미확인 ${km(s.unconfirmedKm)}", color = Palette.Body, fontSize = 14.sp)
            }
            Card(Modifier.weight(1f)) {
                Label("목적 기록률")
                val rate = if (s.businessTrips == 0) 0 else s.purposeRecorded * 100 / s.businessTrips
                Text("$rate%", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("업무 운행 ${s.businessTrips}건", color = Palette.Body, fontSize = 14.sp)
            }
            Card(Modifier.weight(1f)) {
                Label("경비")
                Text(String.format(Locale.KOREA, "%,d원", s.expenseTotal), color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("주차 · 통행료 · 충전 등", color = Palette.Body, fontSize = 14.sp)
            }
        }
        Card(Modifier.fillMaxWidth().weight(1f)) {
            ReportRow("사용일자", "주행 전 계기판", "주행 후 계기판", "주행거리", "구분", "방문 목적", header = true)
            LazyColumn {
                items(closed, key = { it.id }) { t ->
                    ReportRow(
                        day.format(Date(t.startTime)),
                        String.format(Locale.KOREA, "%,.1f", t.startOdoKm),
                        String.format(Locale.KOREA, "%,.1f", t.endOdoKm ?: t.startOdoKm),
                        km(t.distanceKm),
                        TripType.label(t.type),
                        listOfNotNull(t.endPlace, t.purpose).joinToString(" · "),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportRow(a: String, b: String, c: String, d: String, e: String, f: String, header: Boolean = false) {
    val color = if (header) Palette.Sub else Palette.Text
    val size = if (header) 13.sp else 15.sp
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(a, color = color, fontSize = size, fontWeight = weight, modifier = Modifier.width(110.dp))
        Text(b, color = color, fontSize = size, fontWeight = weight, modifier = Modifier.weight(1f))
        Text(c, color = color, fontSize = size, fontWeight = weight, modifier = Modifier.weight(1f))
        Text(d, color = color, fontSize = size, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
        Text(e, color = if (header) Palette.Sub else Palette.BlueText, fontSize = size, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
        Text(f, color = color, fontSize = size, fontWeight = weight, modifier = Modifier.weight(1.4f))
    }
}
