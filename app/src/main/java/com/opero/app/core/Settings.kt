package com.opero.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OperoSettings(
    val demoMode: Boolean = true,
    val companyVehicle: Boolean = true,
    val homeName: String = "집",
    val officeName: String = "회사",
    val askPurpose: Boolean = true,
    val askMemo: Boolean = true,
    val briefing: Boolean = true,
    /** 도착 후 몇 분이 지나야 메모를 물을지. 데모 모드에서는 0분이 편합니다. */
    val memoDelayMin: Int = 10,
    val briefingLeadSec: Int = 300,
    val userName: String = "",
    val department: String = "",
) {
    fun isCommutePlace(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name == homeName || name == officeName
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("opero", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<OperoSettings> = _state.asStateFlow()
    val value: OperoSettings get() = _state.value

    fun update(transform: (OperoSettings) -> OperoSettings) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putBoolean("demoMode", next.demoMode)
            .putBoolean("companyVehicle", next.companyVehicle)
            .putString("homeName", next.homeName)
            .putString("officeName", next.officeName)
            .putBoolean("askPurpose", next.askPurpose)
            .putBoolean("askMemo", next.askMemo)
            .putBoolean("briefing", next.briefing)
            .putInt("memoDelayMin", next.memoDelayMin)
            .putInt("briefingLeadSec", next.briefingLeadSec)
            .putString("userName", next.userName)
            .putString("department", next.department)
            .apply()
    }

    private fun load(): OperoSettings {
        val d = OperoSettings()
        return OperoSettings(
            demoMode = prefs.getBoolean("demoMode", d.demoMode),
            companyVehicle = prefs.getBoolean("companyVehicle", d.companyVehicle),
            homeName = prefs.getString("homeName", d.homeName) ?: d.homeName,
            officeName = prefs.getString("officeName", d.officeName) ?: d.officeName,
            askPurpose = prefs.getBoolean("askPurpose", d.askPurpose),
            askMemo = prefs.getBoolean("askMemo", d.askMemo),
            briefing = prefs.getBoolean("briefing", d.briefing),
            memoDelayMin = prefs.getInt("memoDelayMin", d.memoDelayMin),
            briefingLeadSec = prefs.getInt("briefingLeadSec", d.briefingLeadSec),
            userName = prefs.getString("userName", d.userName) ?: d.userName,
            department = prefs.getString("department", d.department) ?: d.department,
        )
    }
}
