package com.newagedevs.gesturevolume.ui.screens.deck

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import com.newagedevs.gesturevolume.ui.motion.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.newagedevs.gesturevolume.ui.motion.IconButton
import androidx.compose.material3.MaterialTheme
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickDialEntry
import com.newagedevs.gesturevolume.data.local.SearchStore
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.screens.handler_action.SectionTitle
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.PermissionNeeds

/** The short name of where a quick-dial button goes: the phone, SMS, WhatsApp or Telegram. */
fun quickDialViaLabel(via: String): Int = when (via) {
    SearchStore.NUMBER_SMS -> R.string.quick_dial_via_sms
    SearchStore.NUMBER_WHATSAPP -> R.string.quick_dial_via_whatsapp
    SearchStore.NUMBER_TELEGRAM -> R.string.quick_dial_via_telegram
    else -> R.string.quick_dial_via_call
}

/**
 * The one-tap buttons the Deck shows: each one calls a person, or opens an SMS, WhatsApp or
 * Telegram chat with them.
 *
 * A contact can be picked with the system picker, which hands back one phone row with a
 * temporary read grant — so no Contacts permission is needed for this screen at all — or typed
 * in by hand. Either way what is stored is a name, a number and where it opens, nothing that
 * refers back to the address book.
 *
 * The two settings at the foot — Call directly and the country code — are the search bar's, shown
 * here as well because they decide what these buttons do. Call directly used to live only in the
 * search settings, so a user who wanted quick dial to call at once had no way to know where to
 * look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDialScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit = {},
) {
    val context = LocalContext.current
    val store = viewModel.preference.deck
    val search = viewModel.preference.search
    var entries by remember { mutableStateOf(store.getQuickDial()) }
    var showDialog by remember { mutableStateOf(false) }
    /** The entry being edited, or null while a new one is being added. */
    var editing by remember { mutableStateOf<QuickDialEntry?>(null) }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var via by remember { mutableStateOf(SearchStore.NUMBER_DIAL) }

    var directCall by remember { mutableStateOf(search.getDirectCall()) }
    var prefix by remember { mutableStateOf(search.getDialPrefix()) }

    // Re-read on return: the permission can be revoked in system settings while the switch that
    // needs it stays on, and then the note under that switch is the only sign of it.
    var phoneGranted by remember {
        mutableStateOf(PermissionNeeds.hasPermission(context, Manifest.permission.CALL_PHONE))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phoneGranted = PermissionNeeds.hasPermission(context, Manifest.permission.CALL_PHONE)
                // Either can have been changed on the search settings screen since this one opened.
                directCall = search.getDirectCall()
                prefix = search.getDialPrefix()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        directCall = granted
        phoneGranted = granted
        search.setDirectCall(granted)
    }

    fun openDialog(entry: QuickDialEntry?) {
        editing = entry
        name = entry?.name.orEmpty()
        number = entry?.number.orEmpty()
        via = entry?.via ?: SearchStore.NUMBER_DIAL
        showDialog = true
    }

    val pickContact = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.preference.setAppOpenAdPaused(false)
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    openDialog(null)
                    number = cursor.getString(0).orEmpty()
                    name = cursor.getString(1).orEmpty()
                }
            }
        }
    }

    if (showDialog) {
        val current = editing
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = stringResource(if (current == null) R.string.quick_dial_new else R.string.quick_dial_edit),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.quick_dial_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text(stringResource(R.string.quick_dial_number)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.quick_dial_opens_in),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ViaChips(selected = via, onSelect = { via = it })
                    // A chat link is built from the full international number. Said here, where the
                    // choice is made, rather than left to a chat that opens on the wrong person.
                    val needsCountryCode = (via == SearchStore.NUMBER_WHATSAPP || via == SearchStore.NUMBER_TELEGRAM) &&
                        !number.trim().startsWith("+") && prefix.isBlank()
                    if (needsCountryCode) {
                        Text(
                            text = stringResource(R.string.quick_dial_country_code_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val valid = name.isNotBlank() && number.isNotBlank()
                        val added = valid && current == null
                        if (valid) {
                            if (current == null) {
                                store.addQuickDial(name, number, via)
                            } else {
                                store.updateQuickDial(current.copy(name = name, number = number, via = via))
                            }
                            entries = store.getQuickDial()
                        }
                        showDialog = false
                        // A happy moment: a contact was added and the dialog is closed.
                        if (added) {
                            viewModel.onHappyMoment(
                                com.newagedevs.gesturevolume.utils.AdPacing.Trigger.QUICK_DIAL_ADDED
                            )
                        }
                    },
                    enabled = name.isNotBlank() && number.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(if (current == null) R.string.quick_dial_add else R.string.save)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false }, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deck_quick_dial_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.deck_quick_dial_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.preference.setAppOpenAdPaused(true)
                        try {
                            pickContact.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            )
                        } catch (_: Exception) {
                            viewModel.preference.setAppOpenAdPaused(false)
                            openDialog(null)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.quick_dial_pick_contact)) }
                OutlinedButton(
                    onClick = { openDialog(null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.quick_dial_add)) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    if (entries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.quick_dial_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    entries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openDialog(entry) }
                                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                // A call shows only the number, as it always has; anything else
                                // names the app it opens in, since that is the surprising part.
                                Text(
                                    text = if (entry.via == SearchStore.NUMBER_DIAL) {
                                        entry.number
                                    } else {
                                        stringResource(R.string.quick_dial_via_line, stringResource(quickDialViaLabel(entry.via)), entry.number)
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { store.removeQuickDial(entry.id); entries = store.getQuickDial() }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.quick_dial_settings_title), MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.quick_dial_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingSwitchItem(
                        title = stringResource(R.string.search_direct_call),
                        description = stringResource(R.string.search_direct_call_desc),
                        checked = directCall,
                        onCheckedChange = { on ->
                            if (on && !PermissionNeeds.hasPermission(context, Manifest.permission.CALL_PHONE)) {
                                phoneLauncher.launch(Manifest.permission.CALL_PHONE)
                            } else {
                                directCall = on
                                search.setDirectCall(on)
                            }
                        }
                    )
                    if (directCall && !phoneGranted) {
                        PermissionNote(
                            missing = PermissionNeeds.Permission.PHONE,
                            onOpenPermissions = onOpenPermissions,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { v -> prefix = v.filter { it.isDigit() }.take(4); search.setDialPrefix(prefix) },
                        label = { Text(stringResource(R.string.search_dial_prefix)) },
                        prefix = { Text("+") },
                        supportingText = { Text(stringResource(R.string.search_dial_prefix_desc)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Phone, SMS, WhatsApp, Telegram: where one quick-dial button goes. */
@Composable
private fun ViaChips(selected: String, onSelect: (String) -> Unit) {
    // Two rows of two: four chips on one row do not fit a dialog on a narrow phone.
    SearchStore.ALL_NUMBER_ACTIONS.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            row.forEach { id ->
                val on = id == selected
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { onSelect(id) }
                ) {
                    Text(
                        text = stringResource(quickDialViaLabel(id)),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
