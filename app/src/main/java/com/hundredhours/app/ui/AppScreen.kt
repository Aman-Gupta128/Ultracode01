package com.hundredhours.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hundredhours.app.DAY_MS
import com.hundredhours.app.DEFAULT_GOAL_HOURS
import com.hundredhours.app.HOUR_MS
import com.hundredhours.app.MINUTE_MS
import com.hundredhours.app.R
import com.hundredhours.app.StudyState
import com.hundredhours.app.StudyStore
import com.hundredhours.app.ThemeMode
import com.hundredhours.app.formatHms
import com.hundredhours.app.formatHoursDecimal
import com.hundredhours.app.formatShort
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun AppScreen(onTimerStarted: () -> Unit) {
    val state by StudyStore.state.collectAsStateWithLifecycle()
    val theme by StudyStore.theme.collectAsStateWithLifecycle()

    // Re-render every second, aligned to the clock.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            if (StudyStore.state.value.let { it.isRunning && it.isWeekOver(now) }) {
                StudyStore.update { it.settle(now) }
            }
            delay(1000 - now % 1000)
        }
    }

    // Surface sets the default text/icon colour for the theme (otherwise it is black, invisible in dark mode).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            when {
                !state.hasWeek -> SetupScreen(state, theme, onStart = { goal ->
                    StudyStore.update { it.startWeek(System.currentTimeMillis(), goal) }
                    onTimerStarted()
                })
                state.isWeekOver(now) -> ResultScreen(state, now, onNewWeek = { StudyStore.update { it.reset() } })
                else -> TimerScreen(state, now, theme, onTimerStarted)
            }
        }
    }
}

// ---------------------------------------------------------------- Setup

@Composable
private fun SetupScreen(state: StudyState, theme: ThemeMode, onStart: (Int) -> Unit) {
    var goalText by rememberSaveable { mutableStateOf((state.goalMs / HOUR_MS).toString()) }
    val goal = goalText.toIntOrNull()
    val valid = goal != null && goal in 1..168

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ThemeMenu(theme) }
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = goalText,
            onValueChange = { v -> goalText = v.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.goal_hours)) },
            supportingText = { if (!valid) Text(stringResource(R.string.goal_invalid)) },
            isError = !valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.setup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        BigButton(stringResource(R.string.start_week), enabled = valid) { onStart(goal ?: DEFAULT_GOAL_HOURS) }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- Timer

@Composable
private fun TimerScreen(state: StudyState, now: Long, theme: ThemeMode, onTimerStarted: () -> Unit) {
    val remaining = state.remainingMs(now)
    val goalH = state.goalMs / HOUR_MS
    val progress = (state.studiedMs(now).toFloat() / state.goalMs).coerceIn(0f, 1f)

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.day_of_week, state.dayIndex(now) + 1),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TimerMenu(state, now, theme)
        }

        Spacer(Modifier.weight(1f))

        Text(
            formatHms(remaining),
            style = Tabular.copy(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
            color = if (state.isRunning) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            if (remaining > 0) stringResource(R.string.left_of_goal, goalH)
            else stringResource(R.string.goal_reached_extra, formatHms(state.extraMs(now))),
            style = MaterialTheme.typography.bodyLarge,
            color = if (remaining > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(16.dp))
        val pace = state.paceMsPerDay(now)
        Text(
            buildString {
                append(stringResource(R.string.week_left, formatShort(state.weekLeftMs(now))))
                if (pace != null) append("  ·  ").append(stringResource(R.string.pace, formatHoursDecimal(pace)))
            },
            style = MaterialTheme.typography.bodyMedium.merge(Tabular),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        DayBars(state, now)

        Spacer(Modifier.height(28.dp))
        if (state.isRunning) {
            BigButton(stringResource(R.string.pause), filled = false) {
                StudyStore.update { it.pause(System.currentTimeMillis()) }
            }
        } else {
            BigButton(stringResource(R.string.start)) {
                StudyStore.update { it.start(System.currentTimeMillis()) }
                onTimerStarted()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimerMenu(state: StudyState, now: Long, theme: ThemeMode) {
    var open by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.isRunning) {
                DropdownMenuItem(text = { Text(stringResource(R.string.discard_current)) }, onClick = { open = false; dialog = "discard" })
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.undo_last)) },
                    enabled = state.sessions.isNotEmpty(),
                    onClick = { open = false; dialog = "undo" },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.adjust_time)) }, onClick = { open = false; dialog = "adjust" })
            HorizontalDivider()
            ThemeItems(theme) { open = false }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.start_over)) }, onClick = { open = false; dialog = "reset" })
        }
    }

    when (dialog) {
        "discard" -> ConfirmDialog(
            title = stringResource(R.string.discard_current),
            text = stringResource(R.string.discard_text, formatShort(now - (state.runningSince ?: now))),
            confirm = stringResource(R.string.discard),
            onConfirm = { StudyStore.update { it.discardRunning() } },
            onDismiss = { dialog = null },
        )
        "undo" -> state.sessions.lastOrNull()?.let { last ->
            val fmt = remember { SimpleDateFormat("EEE HH:mm", Locale.getDefault()) }
            ConfirmDialog(
                title = stringResource(R.string.undo_last),
                text = stringResource(R.string.undo_text, formatShort(last.durationMs), fmt.format(Date(last.start))),
                confirm = stringResource(R.string.remove),
                onConfirm = { StudyStore.update { it.undoLastSession() } },
                onDismiss = { dialog = null },
            )
        }
        "adjust" -> AdjustDialog(onDismiss = { dialog = null })
        "reset" -> ConfirmDialog(
            title = stringResource(R.string.start_over),
            text = stringResource(R.string.start_over_text),
            confirm = stringResource(R.string.start_over),
            onConfirm = { StudyStore.update { it.reset() } },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ThemeMenu(theme: ThemeMode) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThemeItems(theme) { open = false }
        }
    }
}

@Composable
private fun ThemeItems(theme: ThemeMode, onPicked: () -> Unit) {
    listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
    ).forEach { (mode, label) ->
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            leadingIcon = { RadioButton(selected = theme == mode, onClick = null) },
            onClick = { StudyStore.setTheme(mode); onPicked() },
        )
    }
}

@Composable
private fun AdjustDialog(onDismiss: () -> Unit) {
    var add by rememberSaveable { mutableStateOf(true) }
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    val totalMs = (hours.toLongOrNull() ?: 0) * HOUR_MS + (minutes.toLongOrNull() ?: 0) * MINUTE_MS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adjust_time)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.adjust_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                listOf(true to R.string.adjust_add, false to R.string.adjust_remove).forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = add == value, role = Role.RadioButton) { add = value }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = add == value, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(label))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.hours)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.minutes)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = totalMs > 0, onClick = {
                StudyStore.update { it.adjust(System.currentTimeMillis(), if (add) totalMs else -totalMs) }
                onDismiss()
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- Result

@Composable
private fun ResultScreen(state: StudyState, now: Long, onNewWeek: () -> Unit) {
    val studied = state.studiedMs(now)
    val reached = studied >= state.goalMs
    val goalH = state.goalMs / HOUR_MS

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(if (reached) R.string.result_reached else R.string.result_done),
            style = MaterialTheme.typography.headlineMedium,
            color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            formatHms(studied),
            style = Tabular.copy(fontSize = 56.sp, fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.result_studied_of, goalH),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        DayBars(state, now)
        Spacer(Modifier.height(28.dp))
        BigButton(stringResource(R.string.new_week), onClick = onNewWeek)
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- Shared pieces

@Composable
private fun DayBars(state: StudyState, now: Long) {
    val clipped = minOf(now, state.weekEnd ?: now)
    val daily = state.dailyMs(clipped)
    val today = if (state.isWeekOver(now)) -1 else state.dayIndex(now)
    val dailyTarget = state.goalMs / 7
    val maxMs = maxOf(daily.max(), dailyTarget, HOUR_MS)
    val dayFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val weekStart = state.weekStart ?: 0

    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0 until 7) {
            val ms = daily[i]
            val isToday = i == today
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    if (ms > 0) formatHoursDecimal(ms) else "",
                    style = MaterialTheme.typography.labelSmall.merge(Tabular),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.weight(1f).width(18.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    val fraction = (ms.toFloat() / maxMs).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    dayFmt.format(Date(weekStart + i * DAY_MS)).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean = true, filled: Boolean = true, onClick: () -> Unit) {
    val colors = if (filled) ButtonDefaults.buttonColors()
    else ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}
