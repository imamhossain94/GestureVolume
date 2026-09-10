package com.newagedevs.gesturevolume.overlay.deck

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.AudioStreamResolver
import com.newagedevs.gesturevolume.utils.ClipboardEntry
import com.newagedevs.gesturevolume.utils.ExpressionEvaluator
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Which card [tile] opens. Every PANEL tile has one; anything else is a defect worth seeing. */
@Composable
fun DeckCardContent(tile: DeckTile, actions: DeckActions, palette: DeckPalette) {
    when (tile.id) {
        DeckTiles.SEARCH -> SearchCard(actions, palette)
        DeckTiles.VOLUME -> VolumeCard(actions, palette)
        DeckTiles.BRIGHTNESS -> BrightnessCard(actions, palette)
        DeckTiles.MEDIA -> MediaCard(actions, palette)
        DeckTiles.TIMER -> TimerCard(actions, palette)
        DeckTiles.CALCULATOR -> CalculatorCard(actions, palette)
        DeckTiles.NOTES -> NotesCard(actions, palette)
        DeckTiles.CLIPBOARD -> ClipboardCard(actions, palette)
        DeckTiles.CHECKLIST -> ChecklistCard(actions, palette)
        DeckTiles.COIN -> CoinCard(actions, palette)
        DeckTiles.DICE -> DiceCard(actions, palette)
        DeckTiles.WEATHER -> WeatherCard(actions, palette)
        else -> Text(stringResource(tile.labelRes), color = palette.onBackground)
    }
}

// =================================================================================================
// Shared pieces
// =================================================================================================

@Composable
fun DeckButton(
    text: String,
    palette: DeckPalette,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) palette.accent.copy(alpha = if (enabled) 1f else 0.4f) else palette.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) palette.onAccent else palette.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun DeckChip(text: String, selected: Boolean, palette: DeckPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.accent else palette.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) palette.onAccent else palette.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DeckHint(text: String, palette: DeckPalette) {
    Text(
        text = text,
        color = palette.subtle,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
fun DeckTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    palette: DeckPalette,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder, color = palette.subtle, fontSize = 14.sp) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        textStyle = androidx.compose.ui.text.TextStyle(color = palette.onBackground, fontSize = 14.sp),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onSearch = { onImeAction() }, onSend = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.onBackground.copy(alpha = 0.25f),
            cursorColor = palette.accent,
            focusedTextColor = palette.onBackground,
            unfocusedTextColor = palette.onBackground
        )
    )
}

@Composable
private fun DeckSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    palette: DeckPalette,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = palette.accent,
            activeTrackColor = palette.accent,
            inactiveTrackColor = palette.accent.copy(alpha = 0.25f),
            disabledThumbColor = palette.subtle,
            disabledActiveTrackColor = palette.subtle
        )
    )
}

// =================================================================================================
// Volume
// =================================================================================================

@Composable
private fun VolumeCard(actions: DeckActions, palette: DeckPalette) {
    val volume = actions.env.volume
    val streams = remember {
        listOf(
            AudioStreamResolver.STREAM_MUSIC to R.string.volume_stream_media,
            AudioStreamResolver.STREAM_RING to R.string.volume_stream_ring,
            AudioStreamResolver.STREAM_ALARM to R.string.volume_stream_alarm
        )
    }
    Column {
        streams.forEach { (stream, labelRes) ->
            val res = remember(stream) { volume.forStream(stream) }
            var level by remember(stream) { mutableFloatStateOf((volume.percent(res) ?: 0) / 100f) }
            val writable = stream != AudioStreamResolver.STREAM_RING || volume.canWriteRing()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(labelRes),
                    color = palette.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.width(56.dp)
                )
                DeckSlider(
                    value = level,
                    enabled = writable,
                    onValueChange = { v ->
                        level = v
                        volume.setPercent(res, (v * 100).toInt(), showUi = false)
                        actions.onInteraction()
                    },
                    palette = palette
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val media = remember { volume.media() }
            var muted by remember { mutableStateOf(volume.isMuted(media)) }
            DeckButton(
                text = stringResource(if (muted) R.string.deck_unmute else R.string.deck_mute),
                palette = palette,
                filled = false
            ) {
                if (muted) {
                    volume.unmute(media, actions.env.preference.getPreMuteLevel(media.stream))
                } else {
                    volume.mute(media)?.let { actions.env.preference.setPreMuteLevel(media.stream, it) }
                }
                muted = volume.isMuted(media)
            }
            DeckButton(text = stringResource(R.string.action_open_volume_ui), palette = palette, filled = false) {
                actions.close()
                volume.panel(media)
            }
        }
    }
}

// =================================================================================================
// Brightness
// =================================================================================================

@Composable
private fun BrightnessCard(actions: DeckActions, palette: DeckPalette) {
    val brightness = actions.env.brightness
    val canWrite = brightness.canWrite()
    var level by remember { mutableFloatStateOf(brightness.fraction() ?: 0.5f) }
    var auto by remember { mutableStateOf(brightness.isAutoBrightnessOn()) }
    Column {
        if (!canWrite) {
            DeckHint(stringResource(R.string.brightness_needs_permission_short), palette)
            Spacer(modifier = Modifier.height(8.dp))
            DeckButton(text = stringResource(R.string.open_settings), palette = palette) {
                actions.close()
                actions.launch(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${actions.env.context.packageName}".toUri())
                )
            }
            return
        }
        DeckSlider(
            value = level,
            onValueChange = { v ->
                level = v
                brightness.setFraction(v)
                if (brightness.autoDisabledByFraction) {
                    actions.env.preference.setBrightnessAutoWasOn(true)
                    auto = false
                }
                actions.onInteraction()
            },
            palette = palette
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.deck_brightness_auto),
                color = palette.onBackground,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = auto,
                onCheckedChange = { on ->
                    if (brightness.setAutoBrightness(on)) {
                        auto = on
                        if (on) actions.env.preference.setBrightnessAutoWasOn(false)
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent)
            )
        }
    }
}

// =================================================================================================
// Media
// =================================================================================================

@Composable
private fun MediaCard(actions: DeckActions, palette: DeckPalette) {
    val toggles = actions.env.toggles
    var playing by remember { mutableStateOf(toggles.isMusicActive()) }
    LaunchedEffect(Unit) {
        while (true) {
            playing = toggles.isMusicActive()
            delay(1000)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(if (playing) R.string.deck_media_playing else R.string.deck_media_nothing),
            color = palette.subtle,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Filled.SkipPrevious, stringResource(R.string.action_media_previous), palette, 44.dp) {
                toggles.mediaPrevious()
            }
            RoundIconButton(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                stringResource(R.string.action_media_play_pause), palette, 60.dp, filled = true
            ) {
                toggles.mediaPlayPause()
                playing = !playing
            }
            RoundIconButton(Icons.Filled.SkipNext, stringResource(R.string.action_media_next), palette, 44.dp) {
                toggles.mediaNext()
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        val volume = actions.env.volume
        val media = remember { volume.media() }
        var level by remember { mutableFloatStateOf((volume.percent(media) ?: 0) / 100f) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = palette.subtle, modifier = Modifier.size(18.dp))
            DeckSlider(
                value = level,
                onValueChange = { v ->
                    level = v
                    volume.setPercent(media, (v * 100).toInt(), showUi = false)
                    actions.onInteraction()
                },
                palette = palette
            )
        }
        DeckHint(stringResource(R.string.deck_media_hint), palette)
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: DeckPalette,
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) palette.accent else palette.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (filled) palette.onAccent else palette.accent,
            modifier = Modifier.size(size / 2)
        )
    }
}

// =================================================================================================
// Timer
// =================================================================================================

@Composable
private fun TimerCard(actions: DeckActions, palette: DeckPalette) {
    val state = actions.env.state
    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.timerEndAt) {
        while (state.timerRunning) {
            remainingMs = (state.timerEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(250)
        }
        remainingMs = 0L
    }
    val justFinished = state.timerFinishedAt > 0L &&
        SystemClock.elapsedRealtime() - state.timerFinishedAt < 60_000L

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val shownSeconds = if (state.timerRunning) ((remainingMs + 999) / 1000).toInt() else state.timerSeconds
        Text(
            text = formatClock(shownSeconds),
            color = palette.onBackground,
            fontSize = 44.sp,
            fontWeight = FontWeight.Light
        )
        if (justFinished && !state.timerRunning) {
            Text(
                text = stringResource(R.string.deck_timer_finished),
                color = palette.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (!state.timerRunning) {
            // Two rows of three rather than one of six: the card is a fixed width, and a single
            // row of six chips is wider than it, which pushed the last two off the edge.
            listOf(listOf(1, 5, 10), listOf(15, 30, 60)).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    row.forEach { minutes ->
                        DeckChip(
                            text = stringResource(R.string.deck_minutes_short, minutes),
                            selected = state.timerSeconds == minutes * 60,
                            palette = palette
                        ) { state.timerSeconds = minutes * 60 }
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DeckButton(text = "−1", palette = palette, filled = false) {
                    state.timerSeconds = (state.timerSeconds - 60).coerceAtLeast(30)
                }
                DeckButton(text = stringResource(R.string.deck_timer_start), palette = palette, modifier = Modifier.weight(1f)) {
                    actions.startTimer(state.timerSeconds)
                }
                DeckButton(text = "+1", palette = palette, filled = false) {
                    state.timerSeconds = (state.timerSeconds + 60).coerceAtMost(24 * 3600)
                }
            }
        } else {
            DeckButton(text = stringResource(R.string.deck_timer_stop), palette = palette, modifier = Modifier.fillMaxWidth()) {
                actions.stopTimer()
            }
        }
        DeckHint(stringResource(R.string.tile_timer_desc), palette)
    }
}

private fun formatClock(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

// =================================================================================================
// Calculator
// =================================================================================================

@Composable
private fun CalculatorCard(actions: DeckActions, palette: DeckPalette) {
    val state = actions.env.state
    val input = state.calculatorInput
    val preview = remember(input) { ExpressionEvaluator.evaluateToText(input) }
    val errorText = stringResource(R.string.deck_calc_error)

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.chip)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = input.ifEmpty { "0" },
                color = palette.onBackground,
                fontSize = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            if (preview != null && input.isNotEmpty()) {
                Text(text = "= $preview", color = palette.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val keys = listOf(
            listOf("C", "(", ")", "⌫"),
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "−"),
            listOf("0", ".", "=", "+")
        )
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                row.forEach { key ->
                    val isOp = key in setOf("÷", "×", "−", "+", "=")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (key == "=") palette.accent else palette.chip)
                            .clickable {
                                when (key) {
                                    "C" -> state.calculatorInput = ""
                                    "⌫" -> state.calculatorInput = input.dropLast(1)
                                    "=" -> {
                                        val result = ExpressionEvaluator.evaluateToText(input)
                                        if (result != null) state.calculatorInput = result
                                        else if (input.isNotEmpty()) actions.message(errorText)
                                    }
                                    "÷" -> state.calculatorInput = input + "/"
                                    "×" -> state.calculatorInput = input + "*"
                                    "−" -> state.calculatorInput = input + "-"
                                    else -> state.calculatorInput = input + key
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            color = when {
                                key == "=" -> palette.onAccent
                                isOp -> palette.accent
                                else -> palette.onBackground
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckButton(
                text = stringResource(R.string.deck_calc_copy),
                palette = palette,
                filled = false,
                enabled = preview != null
            ) { preview?.let { actions.copy(it, paste = false) } }
            actions.env.toggles.systemCalculatorIntent()?.let { intent ->
                DeckButton(text = stringResource(R.string.deck_calc_open_system), palette = palette, filled = false) {
                    actions.close()
                    actions.launch(intent)
                }
            }
        }
    }
}

// =================================================================================================
// Notes
// =================================================================================================

@Composable
private fun NotesCard(actions: DeckActions, palette: DeckPalette) {
    val store = actions.env.preference.deck
    var draft by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    val notes = remember(version) { store.getNotes() }

    Column(modifier = Modifier.fillMaxWidth()) {
        DeckTextField(
            value = draft,
            onValueChange = { draft = it; actions.onInteraction() },
            placeholder = stringResource(R.string.deck_notes_hint),
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            imeAction = ImeAction.Default
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckButton(text = stringResource(R.string.deck_save), palette = palette, enabled = draft.isNotBlank()) {
                store.addNote(draft)
                draft = ""
                version++
            }
            DeckButton(text = stringResource(R.string.deck_open_all_notes), palette = palette, filled = false) {
                actions.openAppScreen("notes")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (notes.isEmpty()) {
            DeckHint(stringResource(R.string.deck_notes_empty), palette)
        }
        notes.take(6).forEach { note ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { actions.copy(note.text, paste = false) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.text,
                    color = palette.onBackground,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { store.removeNote(note.id); version++ }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.deck_delete), tint = palette.subtle, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// =================================================================================================
// Clipboard
// =================================================================================================

@Composable
private fun ClipboardCard(actions: DeckActions, palette: DeckPalette) {
    val preference = actions.env.preference
    var version by remember { mutableIntStateOf(0) }
    // Read once, as the card opens: this is the moment the user asked for the clipboard, and the
    // only moment it is worth spending the system's "pasted from your clipboard" notice on.
    LaunchedEffect(Unit) {
        actions.captureClipboard()
        version++
    }
    val entries: List<ClipboardEntry> = remember(version) { preference.getClipboardEntries() }
    val autoPaste = preference.getClipboardAutoPaste()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (entries.isEmpty()) {
            DeckHint(stringResource(R.string.deck_clipboard_empty), palette)
        } else {
            DeckHint(
                stringResource(if (autoPaste) R.string.deck_clipboard_tap_paste else R.string.deck_clipboard_tap_copy),
                palette
            )
        }
        entries.take(8).forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { actions.copy(entry.text, paste = autoPaste) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = palette.subtle, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = entry.text,
                    color = palette.onBackground,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { preference.removeClipboardEntry(entry.id); version++ }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.deck_delete), tint = palette.subtle, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckButton(text = stringResource(R.string.deck_open_clipboard_screen), palette = palette, filled = false) {
                actions.openAppScreen("clipboard")
            }
            if (entries.isNotEmpty()) {
                DeckButton(text = stringResource(R.string.deck_clipboard_clear), palette = palette, filled = false) {
                    preference.clearClipboardEntries()
                    version++
                }
            }
        }
    }
}

// =================================================================================================
// Checklist
// =================================================================================================

@Composable
private fun ChecklistCard(actions: DeckActions, palette: DeckPalette) {
    val store = actions.env.preference.deck
    var draft by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { store.getChecklist() }

    fun add() {
        if (draft.isBlank()) return
        store.addChecklistItem(draft)
        draft = ""
        version++
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckTextField(
                value = draft,
                onValueChange = { draft = it; actions.onInteraction() },
                placeholder = stringResource(R.string.deck_checklist_hint),
                palette = palette,
                modifier = Modifier.weight(1f),
                onImeAction = { add() }
            )
            DeckButton(text = stringResource(R.string.deck_add), palette = palette, enabled = draft.isNotBlank()) { add() }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (items.isEmpty()) DeckHint(stringResource(R.string.deck_checklist_empty), palette)
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { store.toggleChecklistItem(item.id); version++ },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { store.toggleChecklistItem(item.id); version++ },
                    colors = CheckboxDefaults.colors(checkedColor = palette.accent, checkmarkColor = palette.onAccent, uncheckedColor = palette.subtle)
                )
                Text(
                    text = item.text,
                    color = if (item.done) palette.subtle else palette.onBackground,
                    fontSize = 14.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { store.removeChecklistItem(item.id); version++ }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.deck_delete), tint = palette.subtle, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (items.any { it.done }) {
            DeckButton(text = stringResource(R.string.deck_checklist_clear_done), palette = palette, filled = false) {
                store.clearDoneChecklistItems()
                version++
            }
        }
    }
}

// =================================================================================================
// Coin and dice
// =================================================================================================

@Composable
private fun CoinCard(actions: DeckActions, palette: DeckPalette) {
    val state = actions.env.state
    val rotation by animateFloatAsState(
        targetValue = state.coinTosses * 720f + (if (state.coinHeads == false) 180f else 0f),
        animationSpec = tween(durationMillis = 700),
        label = "coin"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
                .clip(CircleShape)
                .background(palette.accent),
            contentAlignment = Alignment.Center
        ) {
            val showingBack = ((rotation / 180f).toInt() % 2) != 0
            Text(
                text = if (showingBack) "T" else "H",
                color = palette.onAccent,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { if (showingBack) rotationY = 180f }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = when (state.coinHeads) {
                true -> stringResource(R.string.deck_coin_heads)
                false -> stringResource(R.string.deck_coin_tails)
                null -> ""
            },
            color = palette.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        DeckButton(text = stringResource(R.string.deck_coin_toss), palette = palette, modifier = Modifier.fillMaxWidth()) {
            state.coinHeads = Random.nextBoolean()
            state.coinTosses++
        }
    }
}

@Composable
private fun DiceCard(actions: DeckActions, palette: DeckPalette) {
    val state = actions.env.state
    val spin by animateFloatAsState(
        targetValue = state.diceRolls * 360f,
        animationSpec = tween(durationMillis = 600),
        label = "dice"
    )
    val dice = state.dice
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            DieFace(dice?.first ?: 1, palette, Modifier.graphicsLayer { rotationZ = spin })
            DieFace(dice?.second ?: 1, palette, Modifier.graphicsLayer { rotationZ = -spin })
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (dice != null) stringResource(R.string.deck_dice_total, dice.first + dice.second) else "",
            color = palette.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        DeckButton(text = stringResource(R.string.deck_dice_roll), palette = palette, modifier = Modifier.fillMaxWidth()) {
            state.dice = Random.nextInt(1, 7) to Random.nextInt(1, 7)
            state.diceRolls++
        }
    }
}

/** A die face drawn with pips, so it needs no image asset. */
@Composable
private fun DieFace(value: Int, palette: DeckPalette, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accent)
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val r = size.minDimension / 12f
            val c = size.minDimension / 2f
            val o = size.minDimension / 4f
            val pips: List<Pair<Float, Float>> = when (value.coerceIn(1, 6)) {
                1 -> listOf(c to c)
                2 -> listOf(c - o to c - o, c + o to c + o)
                3 -> listOf(c - o to c - o, c to c, c + o to c + o)
                4 -> listOf(c - o to c - o, c + o to c - o, c - o to c + o, c + o to c + o)
                5 -> listOf(c - o to c - o, c + o to c - o, c to c, c - o to c + o, c + o to c + o)
                else -> listOf(c - o to c - o, c + o to c - o, c - o to c, c + o to c, c - o to c + o, c + o to c + o)
            }
            pips.forEach { (x, y) ->
                drawCircle(color = palette.onAccent, radius = r, center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
    }
}

// =================================================================================================
// Weather — filled in by the weather step; until then the card says what it will be.
// =================================================================================================

@Composable
private fun WeatherCard(actions: DeckActions, palette: DeckPalette) {
    WeatherCardContent(actions, palette)
}

/** Shared row used by the clipboard and notes screens too. */
@Composable
fun CopyRow(text: String, palette: DeckPalette, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = palette.onBackground, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Check, contentDescription = null, tint = palette.subtle, modifier = Modifier.size(16.dp))
    }
}
