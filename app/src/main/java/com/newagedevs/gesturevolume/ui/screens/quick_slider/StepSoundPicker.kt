package com.newagedevs.gesturevolume.ui.screens.quick_slider

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.ui.components.ChoiceChip
import com.newagedevs.gesturevolume.utils.StepSound
import com.newagedevs.gesturevolume.utils.StepSoundLibrary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Which sound the steps make: the app's own, each played as it is tapped, or one from the phone —
 * its notification sounds, or any audio file on it.
 *
 * A sound is heard the way the panel will make it: as a few steps in a row, climbing in pitch when
 * the step sound is Adaptive, and through the same kind of volume the panel uses — the media volume
 * for Adaptive, the system's sounds for Click — so silence here means silence there too.
 */
@Composable
fun StepSoundPicker(store: QuickSliderStore, mode: String) {
    val context = LocalContext.current
    var source by remember { mutableStateOf(store.getStepSoundSource()) }
    var deviceName by remember { mutableStateOf(store.getStepSoundDeviceName()) }
    val player = remember { StepSound(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val scope = rememberCoroutineScope()
    val audition = remember { mutableStateOf<Job?>(null) }

    fun play(src: String) {
        val adaptive = mode == QuickSliderStore.SOUND_ADAPTIVE
        player.prepare(
            if (adaptive) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            src,
        )
        audition.value?.cancel()
        audition.value = scope.launch {
            val levels = if (adaptive) AUDITION_ADAPTIVE else AUDITION_CLICK
            levels.forEachIndexed { i, level ->
                if (i > 0) delay(AUDITION_GAP_MS)
                player.play(level, adaptive)
            }
        }
    }

    val unreadable = stringResource(R.string.step_sound_unreadable)
    fun chooseFromPhone(uri: Uri, name: String?) {
        // Checked before it is chosen: a sound that cannot be read now would only be the tick later.
        if (StepSound.canRead(context, uri) { _, _ -> true } != true) {
            Toast.makeText(context, unreadable, Toast.LENGTH_LONG).show()
            return
        }
        store.setStepSoundDevice(uri.toString(), name)
        source = store.getStepSoundSource()
        deviceName = name
        play(source)
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Kept for good: without it, access to the file ends when this screen does, and the panel
        // that plays it is not this screen.
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        chooseFromPhone(uri, displayName(context, uri))
    }
    val pickSystem = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } ?: return@rememberLauncherForActivityResult
        val name = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
        chooseFromPhone(uri, name)
    }
    val systemTitle = stringResource(R.string.step_sound_phone_sounds)

    Column {
        Text(
            text = stringResource(R.string.step_sound_choose),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.step_sound_choose_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        // One row that scrolls, rather than a wall of three dozen chips: the phone's own sounds and a
        // file at its end, and the one chosen brought into view when the screen opens.
        val fromPhone = StepSoundLibrary.isDevice(source)
        val rowState = rememberLazyListState()
        LaunchedEffect(Unit) {
            val at = if (fromPhone) 0 else StepSoundLibrary.IDS.indexOf(source).coerceAtLeast(0)
            if (at > 0) rowState.scrollToItem((at - 1).coerceAtLeast(0))
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (fromPhone) {
                item(key = "device") {
                    ChoiceChip(
                        label = deviceName ?: stringResource(R.string.step_sound_from_phone),
                        selected = true,
                        onClick = { play(source) },
                    )
                }
            }
            items(StepSoundLibrary.IDS, key = { it }) { id ->
                ChoiceChip(
                    label = stringResource(stepSoundLabel(id)),
                    selected = source == id,
                    onClick = {
                        store.setStepSoundSource(id)
                        source = id
                        play(id)
                    },
                )
            }
            item(key = "phoneSounds") {
                SourceChip(label = systemTitle, icon = Icons.Filled.MusicNote) {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, systemTitle)
                    StepSoundLibrary.deviceUri(source)?.let {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                    }
                    try {
                        pickSystem.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        // A phone without the system's sound picker: any audio file instead.
                        pickFile.launch(arrayOf("audio/*"))
                    }
                }
            }
            item(key = "audioFile") {
                SourceChip(label = stringResource(R.string.step_sound_audio_file), icon = Icons.Filled.Folder) {
                    try {
                        pickFile.launch(arrayOf("audio/*"))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, unreadable, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

/** A chip that goes somewhere to choose a sound — the phone's own, or a file — rather than being one. */
@Composable
private fun SourceChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        shape = RoundedCornerShape(12.dp),
    )
}

/** The name of one of the app's own sounds. */
fun stepSoundLabel(id: String): Int = when (id) {
    "tap" -> R.string.step_sound_tap
    "dial" -> R.string.step_sound_dial
    "keys" -> R.string.step_sound_keys
    "wood" -> R.string.step_sound_wood
    "pop" -> R.string.step_sound_pop
    "drop" -> R.string.step_sound_drop
    "bubble" -> R.string.step_sound_bubble
    "snap" -> R.string.step_sound_snap
    "thud" -> R.string.step_sound_thud
    "blip" -> R.string.step_sound_blip
    "marimba" -> R.string.step_sound_marimba
    "glass" -> R.string.step_sound_glass
    "chime" -> R.string.step_sound_chime
    "twinkle" -> R.string.step_sound_twinkle
    "firework" -> R.string.step_sound_firework
    "rumble" -> R.string.step_sound_rumble
    "launch" -> R.string.step_sound_launch
    "swish" -> R.string.step_sound_swish
    "crack" -> R.string.step_sound_crack
    "whoosh" -> R.string.step_sound_whoosh
    "spray" -> R.string.step_sound_spray
    "bubbles" -> R.string.step_sound_bubbles
    "meow" -> R.string.step_sound_meow
    "monkey" -> R.string.step_sound_monkey
    "ufo" -> R.string.step_sound_ufo
    "buzz" -> R.string.step_sound_buzz
    "splash" -> R.string.step_sound_splash
    "ribbit" -> R.string.step_sound_ribbit
    "zip" -> R.string.step_sound_zip
    "balloon" -> R.string.step_sound_balloon
    "burst" -> R.string.step_sound_burst
    "cosmic" -> R.string.step_sound_cosmic
    "boing" -> R.string.step_sound_boing
    else -> R.string.step_sound_tick
}

/** A file's own name, without its extension, as the user knows it. */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }

/** The levels the audition steps through: climbing, for Adaptive, to be heard rising. */
private val AUDITION_ADAPTIVE = listOf(0.3f, 0.5f, 0.7f)
private val AUDITION_CLICK = listOf(0.5f, 0.5f)
private const val AUDITION_GAP_MS = 110L
