package com.opero.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opero.app.OperoApp
import com.opero.app.car.Place
import com.opero.app.data.Client
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 캔버스 6번 '거래처 상세 · 방문·메모 이력' */
@Composable
fun ClientsScreen(app: OperoApp, selectedId: Long?, onSelect: (Long) -> Unit) {
    val clients by remember { app.repo.clients() }.collectAsStateWithLifecycle(emptyList())
    val selected = clients.firstOrNull { it.id == selectedId } ?: clients.firstOrNull()

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(Palette.Rail).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label("거래처 · ${clients.size}곳")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(clients, key = { it.id }) { c ->
                    val on = c.id == selected?.id
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) Palette.BlueSoft else Color.Transparent)
                            .clickable { onSelect(c.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(c.name, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(c.lastPurpose ?: "방문 기록", color = Palette.Sub, fontSize = 13.sp)
                    }
                }
            }
        }
        if (selected == null) {
            Column(Modifier.fillMaxSize().padding(40.dp)) {
                Text("아직 거래처가 없어요", color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("내비게이션 목적지로 도착하면 자동으로 거래처가 만들어져요.", color = Palette.Body, fontSize = 16.sp)
            }
        } else {
            ClientDetail(app, selected)
        }
    }
}

@Composable
private fun ClientDetail(app: OperoApp, client: Client) {
    val context = LocalContext.current
    val trips by remember(client.id) { app.repo.tripsForClient(client.id) }.collectAsStateWithLifecycle(emptyList())
    val memos by remember(client.id) { app.repo.memos(client.id) }.collectAsStateWithLifecycle(emptyList())
    val day = SimpleDateFormat("M월 d일", Locale.KOREA)

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(client.name, color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "방문 ${trips.size}회 · 누적 거리 ${km(trips.sumOf { it.distanceKm })}" +
                        (client.lastVisit?.let { " · 마지막 방문 ${day.format(Date(it))}" } ?: ""),
                    color = Palette.Sub, fontSize = 15.sp,
                )
            }
            PrimaryButton("여기로 안내", height = 56) {
                val ok = app.session.navigateTo(Place(client.name, client.address, client.lat, client.lng))
                Toast.makeText(context, if (ok) "${client.name}로 안내를 요청했어요" else "위치 정보가 없어 안내할 수 없어요", Toast.LENGTH_SHORT).show()
            }
        }
        client.lastPurpose?.let { Chip("자주 쓰는 목적 · $it", bg = Palette.Surface2, fg = Palette.Body) }
        Label("메모 이력")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (memos.isEmpty()) item {
                Card(Modifier.fillMaxWidth()) { Text("아직 메모가 없어요. 미팅 후 차에 타면 메모를 물어볼게요.", color = Palette.Body, fontSize = 15.sp) }
            }
            items(memos, key = { it.id }) { m ->
                val trip = trips.firstOrNull { it.id == m.tripId }
                Card(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.width(110.dp)) {
                            Text(day.format(Date(m.createdAt)), color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            trip?.let { Text(km(it.distanceKm), color = Palette.Sub, fontSize = 13.sp) }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(trip?.purpose ?: "메모", color = Palette.AmberText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(m.text, color = Palette.Text, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
