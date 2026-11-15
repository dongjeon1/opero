package com.opero.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opero.app.OperoApp
import kotlinx.coroutines.launch

/** 캔버스 7번 '첫 실행 · 기본 설정' */
@Composable
fun SettingsScreen(app: OperoApp) {
    val s by app.settings.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("설정", color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text("타고 내리기만 하면 운행기록이 남아요. 기록은 이 차 안에만 저장돼요.", color = Palette.Body, fontSize = 16.sp)

        Card(Modifier.fillMaxWidth()) {
            Toggle("데모 모드", "차량 없이 화면의 데모 패널로 시연해요. 끄면 Pleos 차량 SDK에 연결해요.", s.demoMode) { on ->
                app.settings.update { it.copy(demoMode = on) }
                app.connect()
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text("차량 용도", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Choice("회사 업무용 차량", s.companyVehicle, Modifier.weight(1f)) { app.settings.update { it.copy(companyVehicle = true) } }
                Choice("개인 차량을 업무에도 사용", !s.companyVehicle, Modifier.weight(1f)) { app.settings.update { it.copy(companyVehicle = false) } }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text("출퇴근 자동 구분", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("내비게이션 목적지 이름이 아래와 같으면 출퇴근으로 기록해요.", color = Palette.Sub, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(s.homeName, { v -> app.settings.update { it.copy(homeName = v) } }, label = { Text("집") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(s.officeName, { v -> app.settings.update { it.copy(officeName = v) } }, label = { Text("회사") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text("운행기록부 사용자", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(s.department, { v -> app.settings.update { it.copy(department = v) } }, label = { Text("부서") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(s.userName, { v -> app.settings.update { it.copy(userName = v) } }, label = { Text("성명") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text("음성 질문", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Toggle("도착하면 방문 목적 묻기", null, s.askPurpose) { on -> app.settings.update { it.copy(askPurpose = on) } }
            Toggle("미팅 후 탑승하면 메모 묻기", "도착 후 ${s.memoDelayMin}분이 지나고 운전석 문이 닫히면 물어봐요. (데모 모드에서는 바로)", s.askMemo) { on -> app.settings.update { it.copy(askMemo = on) } }
            Toggle("도착 5분 전 지난 메모 브리핑", null, s.briefing) { on -> app.settings.update { it.copy(briefing = on) } }
        }
        // 시연 준비: 샘플 기록 채우기 / 모든 기록 지우기 (확인과 결과는 알림 창으로)
        val scope = rememberCoroutineScope()
        var ask by remember { mutableStateOf<String?>(null) }      // "fill" | "clear"
        var result by remember { mutableStateOf<Pair<String, String>?>(null) } // 제목, 내용
        Card(Modifier.fillMaxWidth()) {
            Text("기록 관리", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("시연 전에 기록을 비우거나, 지난 4주 영업 동선 샘플(거래처 4곳·메모·경비)을 채울 수 있어요.", color = Palette.Sub, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("샘플 기록 채우기", Modifier.weight(1f), height = 56) { ask = "fill" }
                SecondaryButton("모든 기록 지우기", Modifier.weight(1f), height = 56) { ask = "clear" }
            }
        }
        when (ask) {
            "fill" -> OperoAlert(
                title = "샘플 기록을 채울까요?",
                message = "지난 4주 평일의 출퇴근·거래처 방문·메모·경비를 채워요. 이미 있는 기록은 그대로 두고 함께 쌓여요.",
                confirmText = "채우기", dismissText = "취소",
                onConfirm = {
                    ask = null
                    scope.launch {
                        result = runCatching { app.repo.fillSample() }.fold(
                            { "샘플 기록을 채웠어요" to "리포트와 거래처 탭에서 지난 4주 기록을 확인해 보세요." },
                            { e -> "샘플 기록을 채우지 못했어요" to "${e.javaClass.simpleName} ${e.message ?: ""}".take(200) },
                        )
                    }
                },
                onDismiss = { ask = null },
            )
            "clear" -> OperoAlert(
                title = "모든 기록을 지울까요?",
                message = "운행 구간, 거래처, 메모, 경비가 모두 지워지고 되돌릴 수 없어요.",
                confirmText = "모두 지우기", dismissText = "취소", destructive = true,
                onConfirm = {
                    ask = null
                    scope.launch {
                        result = runCatching { app.repo.clearAll(); app.session.forgetPlaces() }.fold(
                            { "모든 기록을 지웠어요" to "새로 운행하면 다시 기록이 쌓여요." },
                            { e -> "기록을 지우지 못했어요" to "${e.javaClass.simpleName} ${e.message ?: ""}".take(200) },
                        )
                    }
                },
                onDismiss = { ask = null },
            )
        }
        result?.let { (title, message) -> OperoAlert(title = title, message = message, onConfirm = { result = null }) }

        val status by app.carStatus.collectAsStateWithLifecycle()
        val diag by com.opero.app.car.Diag.lines.collectAsStateWithLifecycle()
        Card(Modifier.fillMaxWidth()) {
            Text("차량 연결 진단", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(status.ifBlank { "연결 정보 없음" }, color = Palette.Sub, fontSize = 14.sp)
            if (diag.isNotEmpty()) {
                Text(diag.joinToString("\n"), color = Palette.Body, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontSize = 16.sp)
            desc?.let { Text(it, color = Palette.Sub, fontSize = 13.sp) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Blue),
        )
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) PrimaryButton(text, modifier, height = 56, onClick = onClick)
    else SecondaryButton(text, modifier, height = 56, onClick = onClick)
}
