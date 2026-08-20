package com.termux.lite

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val MAX_SESSIONS = 8

@Composable
fun SessionDrawer(
    theme: TerminalTheme,
    sessions: List<SessionInfo>,
    bootstrapReady: Boolean,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
    onClose: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onSettings: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    ModalDrawerSheet(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(),
        drawerContainerColor = theme.chromeColor,
        drawerContentColor = theme.fgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "TermuxLite",
                color = theme.fgColor,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sessions · long-press to rename",
                color = theme.fgColor.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (sessions.isEmpty()) {
                Text(
                    text = if (bootstrapReady) "No sessions" else "Waiting for bootstrap…",
                    color = theme.fgColor.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                sessions.forEach { session ->
                    SessionRow(
                        theme = theme,
                        session = session,
                        onSelect = { onSelect(session.id) },
                        onClose = { onClose(session.id) },
                        onRename = {
                            renameTarget = session
                            renameText = session.title.substringAfter(". ").ifBlank { session.title }
                        }
                    )
                }
            }

            val atCap = sessions.size >= MAX_SESSIONS
            val newEnabled = bootstrapReady && !atCap
            Text(
                text = if (atCap) "New session (max $MAX_SESSIONS)" else "+  New session",
                color = if (newEnabled) theme.accentColor else theme.fgColor.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = newEnabled, onClick = onNew)
                    .padding(vertical = 12.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = theme.fgColor.copy(alpha = 0.2f)
            )

            Text(
                text = "Settings",
                color = theme.fgColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSettings)
                    .padding(vertical = 12.dp)
            )
        }
    }

    val target = renameTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = {
                Text("Session name", fontFamily = FontFamily.Monospace)
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(target.id, renameText)
                    renameTarget = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    theme: TerminalTheme,
    session: SessionInfo,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onRename: () -> Unit
) {
    val bg = if (session.selected) theme.keyActiveColor else theme.chromeColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(bg)
            .combinedClickable(onClick = onSelect, onLongClick = onRename)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = session.title,
            color = if (session.running) theme.fgColor else theme.fgColor.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (session.canClose) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "×",
                color = theme.fgColor,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
fun TopBar(
    theme: TerminalTheme,
    title: String,
    sessions: List<SessionInfo>,
    canCreate: Boolean,
    onMenu: () -> Unit,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(theme.chromeColor)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopBarButton(theme = theme, label = "☰", onClick = onMenu)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sessions.forEachIndexed { index, session ->
                SessionNumberChip(
                    theme = theme,
                    number = index + 1,
                    selected = session.selected,
                    running = session.running,
                    onClick = { onSelect(session.id) }
                )
            }
            Text(
                text = title.substringAfter(". ").ifBlank { title },
                color = theme.fgColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 4.dp)
            )
        }
        TopBarButton(theme = theme, label = "+", onClick = onNew, enabled = canCreate, isAccent = true)
        TopBarButton(theme = theme, label = "⚙", onClick = onSettings)
    }
}

@Composable
private fun SessionNumberChip(
    theme: TerminalTheme,
    number: Int,
    selected: Boolean,
    running: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) theme.keyActiveColor else theme.keyColor
    val fg = if (selected) theme.fgColor else if (running) theme.fgColor.copy(alpha = 0.9f) else theme.fgColor.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = fg,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TopBarButton(
    theme: TerminalTheme,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isAccent: Boolean = false
) {
    val bg = when {
        !enabled -> Color.Transparent
        isAccent -> theme.accentColor.copy(alpha = 0.22f)
        else -> theme.keyColor
    }
    val fg = when {
        !enabled -> theme.fgColor.copy(alpha = 0.35f)
        isAccent -> theme.accentColor
        else -> theme.fgColor
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
