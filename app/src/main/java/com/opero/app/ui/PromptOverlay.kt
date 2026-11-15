package com.opero.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opero.app.core.Prompt
import com.opero.app.core.SessionManager

/** 도착·메모 질문과 브리핑 카드. 캔버스 1·2·5번 화면에 해당합니다. */
@Composable
fun PromptOverlay(prompt: Prompt, session: SessionManager) {
    if (prompt.kind == Prompt.Kind.BRIEFING) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Palette.Surface)
                    .padding(26.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prompt.badge, color = Palette.BlueText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(prompt.question, color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("\"다시\"라고 하면 한 번 더", color = Palette.Sub, fontSize = 14.sp)
                    SecondaryButton("닫기", height = 52) { session.dismissPrompt() }
                }
            }
        }
        return
    }

    val voice = session.currentVoice
    val heard by (voice?.partial ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()

    Box(Modifier.fillMaxSize().background(Color(0xF00D0F13))) {
        Row(Modifier.fillMaxSize().padding(36.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Chip(prompt.badge, bg = Palette.AmberSoft, fg = Palette.AmberText)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Opero", color = Palette.Sub, fontSize = 18.sp)
                    Text(prompt.question, color = Palette.Text, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 46.sp)
                }
                if (prompt.result != null) {
                    Card(color = Palette.BlueSoft) {
                        Text(prompt.result, color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (prompt.listening || heard.isNotBlank()) {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(if (prompt.listening) Palette.Rec else Palette.Sub))
                            Text(if (prompt.listening) "듣는 중" else "들은 대답", color = Palette.Sub, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(if (heard.isBlank()) "…" else "\"$heard\"", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (prompt.primary != null || prompt.secondary != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        prompt.primary?.let { PrimaryButton(it, Modifier.weight(1.4f), height = 64) { session.onPromptButton(true) } }
                        prompt.secondary?.let { SecondaryButton(it, Modifier.weight(1f), height = 64) { session.onPromptButton(false) } }
                    }
                }
            }
            if (prompt.detail.isNotEmpty()) {
                Column(
                    Modifier
                        .width(420.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Palette.Surface)
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("이번 운행 기록", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Chip("자동 기록됨")
                    }
                    prompt.detail.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, color = Palette.Sub, fontSize = 15.sp)
                            Text(v, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("주행거리는 차량 주행거리계 값, 도착지는 내비게이션 정보로 채웠어요.", color = Palette.Sub, fontSize = 13.sp)
                }
            }
        }
    }
}
