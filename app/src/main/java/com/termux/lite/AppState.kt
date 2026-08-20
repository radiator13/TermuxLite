package com.termux.lite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class SessionInfo(
    val id: Int,
    val title: String,
    val running: Boolean,
    val selected: Boolean,
    val canClose: Boolean
)

object AppState {
    var bootstrap: BootstrapState by mutableStateOf(BootstrapState.Starting)
    var ctrl: ModState by mutableStateOf(ModState.Off)
    var alt: ModState by mutableStateOf(ModState.Off)
    @Volatile
    var volumeCtrl: Boolean = false

    var theme: TerminalTheme by mutableStateOf(TermThemes.Paper)
    var extraKeys: Boolean by mutableStateOf(true)
    var keepScreenOn: Boolean by mutableStateOf(false)
    var fontSize: Int by mutableIntStateOf(Prefs.DEFAULT_TEXT_SIZE)
    var settingsOpen: Boolean by mutableStateOf(false)
    var drawerOpen: Boolean by mutableStateOf(false)
    var pendingDrawerClose: Boolean by mutableStateOf(false)
    var sessions: List<SessionInfo> by mutableStateOf(emptyList())

    fun controlDown(): Boolean = ctrl.isActive() || volumeCtrl

    fun altDown(): Boolean = alt.isActive()

    fun consumeOneShotModifiers() {
        ctrl = ctrl.consume()
        alt = alt.consume()
    }

    fun loadFromPrefs() {
        theme = TermThemes.byId(Prefs.themeId)
        extraKeys = Prefs.extraKeys
        keepScreenOn = Prefs.keepScreenOn
        fontSize = Prefs.textSize
    }
}
