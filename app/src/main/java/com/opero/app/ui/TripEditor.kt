package com.opero.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opero.app.OperoApp
import com.opero.app.data.Trip
import com.opero.app.data.TripType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 구간 확인·수정 (정차 중에만 열림).
 * 대답을 놓쳐 '미확인'으로 남은 구간을 업무·출퇴근·개인으로 정리하고, 잘못 기록된 구간은 지웁니다.
 */
@Composable
fun TripEditor(app: OperoApp, trip: Trip, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(if (trip.type == TripType.UNCONFIRMED) TripType.BUSINESS else trip.type) }
    var purpose by remember { mutableStateOf(trip.purpose ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var lastPurpose by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(trip.id) {
        lastPurpose = trip.endPlace?.let { app.repo.clientByName(it) }?.lastPurpose
    }
    val time = SimpleDateFormat("HH:mm", Locale.KOREA)

    // 화면 전체를 덮는 반투명 배경. 바깥을 누르면 닫힙니다.
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Surface)
                // 카드 안을 눌러도 닫히지 않게 클릭을 여기서 받습니다.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("구간 확인", color = Palette.Sub, fontSize = 15.sp)
            Text("${trip.startPlace} → ${trip.endPlace ?: ""}", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "${time.format(Date(trip.startTime))}–${trip.endTime?.let { time.format(Date(it)) } ?: ""} · ${km(trip.distanceKm)}",
                color = Palette.Body, fontSize = 16.sp,
            )

            Label("구분")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(TripType.BUSINESS, TripType.COMMUTE, TripType.PERSONAL).forEach { t ->
                    Segment(TripType.label(t), selected = type == t, modifier = Modifier.weight(1f)) { type = t }
                }
            }

            if (type == TripType.BUSINESS) {
                Label("방문 목적")
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    singleLine = true,
                    placeholder = { Text("예: 정기 방문, 납품, 견적 상담", color = Palette.Sub) },
                    textStyle = TextStyle(color = Palette.Text, fontSize = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Blue,
                        unfocusedBorderColor = Palette.Line,
                        cursorColor = Palette.Blue,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = (listOfNotNull(lastPurpose) + listOf("정기 방문", "납품", "미팅", "견적 상담")).distinct().take(5)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s ->
                        Chip(s, bg = if (purpose == s) Palette.BlueSoft else Palette.Surface2, fg = if (purpose == s) Palette.BlueText else Palette.Body,
                            modifier = Modifier.clickable { purpose = s })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("저장", Modifier.weight(1f)) {
                    scope.launch {
                        val p = if (type == TripType.BUSINESS) purpose.trim().ifBlank { "업무 방문" } else null
                        app.repo.confirmTrip(trip, type, p)
                        onClose()
                    }
                }
                SecondaryButton("구간 삭제", Modifier.weight(1f)) { confirmDelete = true }
            }
        }
    }
    if (confirmDelete) {
        OperoAlert(
            title = "이 구간을 삭제할까요?",
            message = "${trip.startPlace} → ${trip.endPlace ?: ""} (${km(trip.distanceKm)}) 구간이 운행기록부에서 빠지고 되돌릴 수 없어요.",
            confirmText = "삭제", dismissText = "취소", destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    app.repo.deleteTrip(trip)
                    onClose()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.Blue else Palette.Surface2)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Palette.OnBlue else Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
