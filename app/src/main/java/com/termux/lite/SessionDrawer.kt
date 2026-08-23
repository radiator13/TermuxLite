package com.termux.lite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val MAX_SESSIONS = 8

/** Slimmer than Material 3's 240–360dp ModalDrawerSheet clamp. */
private val DrawerWidth = 208.dp
private val DrawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
private val ItemShape = RoundedCornerShape(10.dp)

@OptIn(ExperimentalLayoutApi::class)
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
    Surface(
        modifier = Modifier
            .width(DrawerWidth)
            .fillMaxHeight(),
        shape = DrawerShape,
        color = theme.chromeColor,
        contentColor = theme.fgColor,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, theme.fgColor.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Start + WindowInsetsSides.Vertical
                    )
                )
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)
        ) {
            DrawerHeader(theme = theme, sessionCount = sessions.size)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (sessions.isEmpty()) {
                    Text(
                        text = if (bootstrapReady) "No sessions" else "Waiting…",
                        color = theme.fgColor.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                } else {
                    sessions.forEachIndexed { index, session ->
                        SessionRow(
                            theme = theme,
                            index = index + 1,
                            session = session,
                            onSelect = { onSelect(session.id) },
                            onClose = { onClose(session.id) },
                            onRename = {
                                renameTarget = session
                                renameText = session.label.removeSuffix(" (exit)")
                            }
                        )
                    }
                }
            }

            val atCap = sessions.size >= MAX_SESSIONS
            val newEnabled = bootstrapReady && !atCap
            DrawerAction(
                theme = theme,
                icon = Icons.Filled.Add,
                label = if (atCap) "Full ($MAX_SESSIONS)" else "New",
                enabled = newEnabled,
                accent = true,
                onClick = onNew
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = theme.fgColor.copy(alpha = 0.12f)
            )

            DrawerAction(
                theme = theme,
                icon = Icons.Filled.Settings,
                label = "Settings",
                enabled = true,
                accent = false,
                onClick = onSettings
            )
        }
    }

    val target = renameTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = theme.chromeColor,
            titleContentColor = theme.fgColor,
            textContentColor = theme.fgColor,
            title = {
                Text("Rename", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.fgColor,
                        unfocusedTextColor = theme.fgColor,
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.fgColor.copy(alpha = 0.35f),
                        cursorColor = theme.accentColor,
                        focusedLabelColor = theme.accentColor
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(target.id, renameText)
                    renameTarget = null
                }) {
                    Text("Save", color = theme.accentColor, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = theme.fgColor.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

@Composable
private fun DrawerHeader(theme: TerminalTheme, sessionCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(theme.accentColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "TermuxLite",
            color = theme.fgColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$sessionCount/$MAX_SESSIONS",
            color = theme.fgColor.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DrawerAction(
    theme: TerminalTheme,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    accent: Boolean,
    onClick: () -> Unit
) {
    val fg = when {
        !enabled -> theme.fgColor.copy(alpha = 0.38f)
        accent -> theme.accentColor
        else -> theme.fgColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    theme: TerminalTheme,
    index: Int,
    session: SessionInfo,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onRename: () -> Unit
) {
    val selected = session.selected
    val bg = if (selected) theme.accentColor.copy(alpha = 0.16f) else Color.Transparent
    val badgeBg = if (selected) theme.accentColor else theme.keyColor
    val badgeFg = if (selected) {
        if (theme.isLight) Color.White else theme.chromeColor
    } else {
        theme.fgColor.copy(alpha = if (session.running) 0.9f else 0.5f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(bg)
            .combinedClickable(onClick = onSelect, onLongClick = onRename)
            .semantics { contentDescription = "Session $index, long-press to rename" }
            .padding(start = 4.dp, end = 2.dp)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) theme.accentColor else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                color = badgeFg,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = session.label,
            color = if (session.running) theme.fgColor else theme.fgColor.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (session.canClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close session $index",
                tint = theme.fgColor.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(6.dp)
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
        TopBarIconButton(
            theme = theme,
            icon = Icons.Filled.Menu,
            contentDescription = "Sessions",
            onClick = onMenu
        )
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
                text = title,
                color = theme.fgColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 4.dp)
            )
        }
        TopBarIconButton(
            theme = theme,
            icon = Icons.Filled.Add,
            contentDescription = "New session",
            onClick = onNew,
            enabled = canCreate,
            isAccent = true
        )
        TopBarIconButton(
            theme = theme,
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = onSettings
        )
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
private fun TopBarIconButton(
    theme: TerminalTheme,
    icon: ImageVector,
    contentDescription: String,
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
    }
}
