package com.opero.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opero.app.car.DemoCarSource
import com.opero.app.voice.DemoVoice
import java.util.Locale

/** 차량 없이 시연하기 위한 이벤트 패널. 데모 모드에서만 오른쪽에 나타납니다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(car: DemoCarSource, voice: DemoVoice?) {
    val parked by car.parked.collectAsStateWithLifecycle()
    val odometer by car.odometer.collectAsStateWithLifecycle()
    val destination by car.destination.collectAsStateWithLifecycle()
    var answer by remember { mutableStateOf("") }
    val quick = listOf(
        "응, 견적 미팅",
        "응",
        "개인 용무야",
        "지금부터 개인 용무",
        "개인 용무 끝",
        "주차비 3천 원",
        "수정 견적 승인. 12월 납품 300개. 다음 주 화요일까지 계약서 보내기. 주차비 3천 원",
        "다시",
    )

    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Palette.Rail)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("데모 패널", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            (if (parked) "P · 정차" else "주행 중") + String.format(Locale.KOREA, " · 계기판 %,.1f km", odometer),
            color = Palette.Sub, fontSize = 13.sp,
        )

        Label("1. 목적지")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            car.samplePlaces.forEach { p ->
                val on = destination?.name == p.name
                TextButton(onClick = { car.setDestination(p) }) {
                    Text(p.name, color = if (on) Palette.Blue else Palette.Body, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Label("2. 주행")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("출발", Modifier.weight(1f), height = 48) { car.depart() }
            SecondaryButton("+6.1km", Modifier.weight(1f), height = 48) { car.drive(6.1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("도착 5분 전", Modifier.weight(1f), height = 48) { car.approach() }
            SecondaryButton("도착", Modifier.weight(1f), height = 48) { car.arrive() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("주차", Modifier.weight(1f), height = 48) { car.park() }
            SecondaryButton("문 닫힘", Modifier.weight(1f), height = 48) { car.driverDoorClosed() }
        }

        Label("3. 음성 대답 (말한 것으로 입력)")
        OutlinedTextField(answer, { answer = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("예: 응, 견적 미팅") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("말하기", Modifier.weight(1f), height = 48) {
                voice?.answer(answer.ifBlank { null })
                answer = ""
            }
            SecondaryButton("무응답", Modifier.weight(1f), height = 48) { voice?.answer(null) }
        }
        quick.forEach { q ->
            TextButton(onClick = { voice?.answer(q) }) {
                Text("\"$q\"", color = Palette.Body, fontSize = 13.sp)
            }
        }
    }
}
