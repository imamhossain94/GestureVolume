package com.newagedevs.gesturevolume.ui.screens.deck

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SearchStore
import com.newagedevs.gesturevolume.ui.screens.handler_action.SectionTitle
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel

/** The translated name of a search provider identifier. */
fun providerLabel(id: String): Int = when (id) {
    SearchStore.PROVIDER_GOOGLE -> R.string.provider_google
    SearchStore.PROVIDER_YOUTUBE -> R.string.provider_youtube
    SearchStore.PROVIDER_MAPS -> R.string.provider_maps
    SearchStore.PROVIDER_PLAY -> R.string.provider_play
    SearchStore.PROVIDER_WIKIPEDIA -> R.string.provider_wikipedia
    SearchStore.PROVIDER_DUCKDUCKGO -> R.string.provider_duckduckgo
    else -> R.string.provider_amazon
}

fun numberActionLabel(id: String): Int = when (id) {
    SearchStore.NUMBER_SMS -> R.string.search_number_sms
    SearchStore.NUMBER_WHATSAPP -> R.string.search_number_whatsapp
    SearchStore.NUMBER_TELEGRAM -> R.string.search_number_telegram
    else -> R.string.search_number_dial
}

/**
 * What the Deck's search looks through, and where the rest goes.
 *
 * The two switches that need a runtime permission — contacts and direct calls — ask for it the
 * moment they are switched on and switch themselves back off if it is refused, so the setting
 * never claims something the permission does not allow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSettingsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val store = viewModel.preference.search
    var indexApps by remember { mutableStateOf(store.getIndexApps()) }
    var indexContacts by remember { mutableStateOf(store.getIndexContacts()) }
    var calculator by remember { mutableStateOf(store.getInlineCalculator()) }
    var providers by remember { mutableStateOf(store.getProviders()) }
    var defaultProvider by remember { mutableStateOf(store.getDefaultProvider()) }
    var numberAction by remember { mutableStateOf(store.getNumberAction()) }
    var directCall by remember { mutableStateOf(store.getDirectCall()) }
    var prefix by remember { mutableStateOf(store.getDialPrefix()) }

    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        indexContacts = granted
        store.setIndexContacts(granted)
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        directCall = granted
        store.setDirectCall(granted)
    }

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_settings_title)) },
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
            SectionTitle(stringResource(R.string.search_sources_title), MaterialTheme.colorScheme.primary)
            Card {
                SettingSwitchItem(
                    title = stringResource(R.string.search_index_apps),
                    description = stringResource(R.string.search_index_apps_desc),
                    checked = indexApps,
                    onCheckedChange = { indexApps = it; store.setIndexApps(it) }
                )
                Sep()
                SettingSwitchItem(
                    title = stringResource(R.string.search_index_contacts),
                    description = stringResource(R.string.search_index_contacts_desc),
                    checked = indexContacts,
                    onCheckedChange = { on ->
                        if (on && !granted(Manifest.permission.READ_CONTACTS)) {
                            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            indexContacts = on
                            store.setIndexContacts(on)
                        }
                    }
                )
                Sep()
                SettingSwitchItem(
                    title = stringResource(R.string.search_calculator),
                    description = stringResource(R.string.search_calculator_desc),
                    checked = calculator,
                    onCheckedChange = { calculator = it; store.setInlineCalculator(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.search_providers_title), MaterialTheme.colorScheme.primary)
            Card {
                Text(
                    text = stringResource(R.string.search_providers_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                ChipRows(
                    ids = SearchStore.ALL_PROVIDERS,
                    label = { stringResource(providerLabel(it)) },
                    selected = { it in providers },
                    onClick = { id ->
                        providers = if (id in providers) providers - id else providers + id
                        store.setProviders(providers)
                        if (defaultProvider !in providers) {
                            defaultProvider = providers.firstOrNull() ?: SearchStore.PROVIDER_GOOGLE
                            store.setDefaultProvider(defaultProvider)
                        }
                    }
                )
                Sep()
                Text(
                    text = stringResource(R.string.search_default_provider),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChipRows(
                    ids = SearchStore.ALL_PROVIDERS.filter { it in providers },
                    label = { stringResource(providerLabel(it)) },
                    selected = { it == defaultProvider },
                    onClick = { defaultProvider = it; store.setDefaultProvider(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.search_numbers_title), MaterialTheme.colorScheme.primary)
            Card {
                Text(
                    text = stringResource(R.string.search_number_action),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChipRows(
                    ids = SearchStore.ALL_NUMBER_ACTIONS,
                    label = { stringResource(numberActionLabel(it)) },
                    selected = { it == numberAction },
                    onClick = { numberAction = it; store.setNumberAction(it) }
                )
                Sep()
                SettingSwitchItem(
                    title = stringResource(R.string.search_direct_call),
                    description = stringResource(R.string.search_direct_call_desc),
                    checked = directCall,
                    onCheckedChange = { on ->
                        if (on && !granted(Manifest.permission.CALL_PHONE)) {
                            phoneLauncher.launch(Manifest.permission.CALL_PHONE)
                        } else {
                            directCall = on
                            store.setDirectCall(on)
                        }
                    }
                )
                Sep()
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { v -> prefix = v.filter { it.isDigit() }.take(4); store.setDialPrefix(prefix) },
                    label = { Text(stringResource(R.string.search_dial_prefix)) },
                    prefix = { Text("+") },
                    supportingText = { Text(stringResource(R.string.search_dial_prefix_desc)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun Sep() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    )
}

/** Selectable chips, wrapped onto as many rows as they need. */
@Composable
private fun ChipRows(
    ids: List<String>,
    label: @Composable (String) -> String,
    selected: (String) -> Boolean,
    onClick: (String) -> Unit
) {
    ids.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            row.forEach { id ->
                val on = selected(id)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { onClick(id) }
                ) {
                    Text(
                        text = label(id),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
