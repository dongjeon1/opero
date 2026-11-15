package com.opero.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opero.app.ui.ClientsScreen
import com.opero.app.ui.DemoPanel
import com.opero.app.ui.OperoTheme
import com.opero.app.ui.Palette
import com.opero.app.ui.PromptOverlay
import com.opero.app.ui.ReportScreen
import com.opero.app.ui.SettingsScreen
import com.opero.app.ui.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        OperoService.start(this)
        val app = application as OperoApp
        setContent { OperoTheme { OperoRoot(app) } }
    }

    private fun requestRuntimePermissions() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) wanted += "android.permission.POST_NOTIFICATIONS"
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }
}

private enum class Tab(val label: String) { TODAY("오늘"), CLIENTS("거래처"), REPORT("리포트"), SETTINGS("설정") }

@Composable
private fun OperoRoot(app: OperoApp) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var selectedClient by remember { mutableStateOf<Long?>(null) }
    val prompt by app.session.prompt.collectAsStateWithLifecycle()
    val drive by app.session.drive.collectAsStateWithLifecycle()
    val demo by app.demo.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val status by app.carStatus.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(
            Modifier.width(120.dp).fillMaxHeight().background(Palette.Rail).padding(vertical = 24.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Opero", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
            Tab.entries.forEach { t ->
                val on = t == tab
                Text(
                    t.label,
                    color = if (on) Palette.Text else Palette.Sub,
                    fontSize = 17.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Palette.BlueSoft else Color.Transparent)
                        .clickable { tab = t; if (t != Tab.CLIENTS) selectedClient = null }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            // 언제든 한 마디로 기록: "지금부터 개인 용무", "주차비 3천 원"
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.Blue)
                    .clickable { app.session.voiceCommand() }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("말하기", color = Palette.OnBlue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text("한 마디 기록", color = Palette.OnBlue, fontSize = 11.sp, maxLines = 1)
            }
            if (drive.personalMode) {
                Text("개인 용무 중", color = Palette.Sub, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            // 주행 중에는 기록 수정·리포트 화면을 열지 않습니다(데모 모드 제외).
            val locked = !drive.parked && !settings.demoMode && tab != Tab.TODAY
            if (locked) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("주행 중에는 '오늘' 화면만 볼 수 있어요", color = Palette.Sub, fontSize = 20.sp)
                }
            } else {
                when (tab) {
                    Tab.TODAY -> TodayScreen(app, drive, status)
                    Tab.CLIENTS -> ClientsScreen(app, selectedClient) { selectedClient = it }
                    Tab.REPORT -> ReportScreen(app)
                    Tab.SETTINGS -> SettingsScreen(app)
                }
            }
            prompt?.let { PromptOverlay(it, app.session) }
        }

        demo?.let { DemoPanel(it, app.demoVoice) }
    }
}
