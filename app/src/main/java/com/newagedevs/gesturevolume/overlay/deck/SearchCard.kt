package com.newagedevs.gesturevolume.overlay.deck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SearchStore
import com.newagedevs.gesturevolume.utils.SearchRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One thing the search found on the phone. */
private sealed interface SearchHit {
    data class App(val packageName: String, val label: String, val icon: ImageBitmap?) : SearchHit
    data class Contact(val name: String, val number: String) : SearchHit
}

/**
 * The Deck's search: apps, contacts, sums and the web, in one field.
 *
 * Everything on the phone is matched as the user types, off the main thread and debounced, so a
 * long contact list does not stutter the field. What is not on the phone is routed by
 * [SearchRouter] — a sum is answered inline, a phone number offers to dial, and anything else
 * goes to whichever providers are switched on.
 */
@Composable
fun SearchCard(actions: DeckActions, palette: DeckPalette) {
    val env = actions.env
    val context = env.context
    val store = env.preference.search
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }

    val providers = remember { SearchStore.ALL_PROVIDERS.filter { it in store.getProviders() } }
    val calculatorOn = remember { store.getInlineCalculator() }
    val numberAction = remember { store.getNumberAction() }
    val defaultProvider = remember { store.getDefaultProvider() }

    // Loaded once per opening, then filtered in memory: the package manager is far too slow to
    // call on every keystroke, and a phone has a few hundred launchable apps at most.
    var apps by remember { mutableStateOf<List<SearchHit.App>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (store.getIndexApps()) {
            apps = withContext(Dispatchers.IO) { loadApps(context) }
        }
        focusRequester.requestFocus()
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        // A beat before searching, so a fast typist queries once rather than once per letter.
        delay(140)
        val appHits = apps.filter { it.label.contains(trimmed, ignoreCase = true) }.take(6)
        val contactHits = if (store.getIndexContacts() && hasContactsPermission(context)) {
            withContext(Dispatchers.IO) { queryContacts(context, trimmed) }
        } else {
            emptyList()
        }
        hits = appHits + contactHits
    }

    val route = remember(query, calculatorOn) {
        SearchRouter.route(query, numberAction, defaultProvider, calculatorOn)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeckTextField(
                value = query,
                onValueChange = { query = it; actions.onInteraction() },
                placeholder = stringResource(R.string.search_placeholder),
                palette = palette,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                imeAction = ImeAction.Search,
                onImeAction = { submit(actions, route, providers, defaultProvider) }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.chip)
                    .clickable { startVoiceSearch(actions) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.search_voice),
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // The answer to a sum, above the results: it is what the user is looking at.
        (route as? SearchRouter.Route.Calculation)?.let { calc ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.chip)
                    .clickable { actions.copy(calc.result, paste = false) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "= ${calc.result}",
                    color = palette.accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = stringResource(R.string.deck_calc_copy), color = palette.subtle, fontSize = 12.sp)
            }
        }

        (route as? SearchRouter.Route.PhoneNumber)?.let { number ->
            Spacer(modifier = Modifier.height(8.dp))
            ResultRow(
                title = number.digits,
                subtitle = stringResource(numberActionLabelRes(number.action)),
                palette = palette,
                leading = {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                }
            ) { openNumber(actions, number.digits, number.action) }
        }

        hits.forEach { hit ->
            Spacer(modifier = Modifier.height(4.dp))
            when (hit) {
                is SearchHit.App -> ResultRow(
                    title = hit.label,
                    subtitle = null,
                    palette = palette,
                    leading = {
                        if (hit.icon != null) {
                            Image(
                                bitmap = hit.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = palette.subtle, modifier = Modifier.size(22.dp))
                        }
                    }
                ) { actions.launchApp(hit.packageName) }

                is SearchHit.Contact -> ResultRow(
                    title = hit.name,
                    subtitle = hit.number,
                    palette = palette,
                    leading = {
                        Icon(Icons.Filled.Call, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                    }
                ) { openNumber(actions, hit.number, numberAction) }
            }
        }

        if (query.isNotBlank() && providers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = stringResource(R.string.search_on_the_web), color = palette.subtle, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            providers.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    row.forEach { provider ->
                        DeckChip(
                            text = stringResource(providerLabelRes(provider)),
                            selected = provider == defaultProvider,
                            palette = palette
                        ) {
                            actions.close()
                            actions.launch(
                                Intent(Intent.ACTION_VIEW, SearchRouter.webUrl(provider, query.trim()).toUri())
                            )
                        }
                    }
                }
            }
        }

        if (query.isBlank()) {
            DeckHint(stringResource(R.string.search_hint), palette)
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String?,
    palette: DeckPalette,
    leading: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = palette.onBackground,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(text = subtitle, color = palette.subtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = palette.subtle, modifier = Modifier.size(16.dp))
    }
}

/** Enter on the keyboard: the route decides, the default provider catches the rest. */
private fun submit(
    actions: DeckActions,
    route: SearchRouter.Route,
    providers: List<String>,
    defaultProvider: String
) {
    when (route) {
        is SearchRouter.Route.Empty -> Unit
        is SearchRouter.Route.Calculation -> actions.copy(route.result, paste = false)
        is SearchRouter.Route.PhoneNumber -> openNumber(actions, route.digits, route.action)
        is SearchRouter.Route.Web -> {
            val provider = if (defaultProvider in providers) defaultProvider else providers.firstOrNull()
            if (provider == null) {
                actions.message(actions.env.context.getString(R.string.search_no_providers))
                return
            }
            actions.close()
            actions.launch(Intent(Intent.ACTION_VIEW, SearchRouter.webUrl(provider, route.query).toUri()))
        }
    }
}

/**
 * Opens a number in whichever app the setting names, falling back to the dialer.
 *
 * WhatsApp and Telegram take a `wa.me` / `t.me` link rather than a package-specific intent, so a
 * user without the app gets a web page rather than a crash — and the number needs its country
 * code, which is why the prefix setting exists.
 */
private fun openNumber(actions: DeckActions, number: String, action: String) {
    val env = actions.env
    val context = env.context
    val prefix = env.preference.search.getDialPrefix()
    actions.close()
    val intent = when (action) {
        SearchStore.NUMBER_SMS ->
            Intent(Intent.ACTION_SENDTO, "smsto:${SearchRouter.normalizeNumber(number)}".toUri())
        SearchStore.NUMBER_WHATSAPP ->
            Intent(Intent.ACTION_VIEW, "https://wa.me/${SearchRouter.toInternational(number, prefix)}".toUri())
        SearchStore.NUMBER_TELEGRAM ->
            Intent(Intent.ACTION_VIEW, "https://t.me/+${SearchRouter.toInternational(number, prefix)}".toUri())
        else -> {
            val direct = env.preference.search.getDirectCall() &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
            Intent(
                if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                "tel:${SearchRouter.normalizeNumber(number)}".toUri()
            )
        }
    }
    if (!actions.launch(intent)) {
        actions.message(context.getString(R.string.action_unavailable_on_device))
    }
}

/**
 * The system's speech recogniser, whose result comes back to a small transparent Activity — an
 * overlay window cannot receive an Activity result of its own.
 */
private fun startVoiceSearch(actions: DeckActions) {
    val context = actions.env.context
    actions.close()
    val intent = Intent(context, com.newagedevs.gesturevolume.ui.activities.VoiceSearchActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!actions.launch(intent)) {
        actions.message(context.getString(R.string.search_voice_unavailable))
    }
}

private fun hasContactsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

/** Up to four contacts matching [query] by name or number. */
private fun queryContacts(context: android.content.Context, query: String): List<SearchHit.Contact> =
    runCatching {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection =
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val args = arrayOf("%$query%", "%$query%")
        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            val seen = mutableSetOf<String>()
            val out = mutableListOf<SearchHit.Contact>()
            while (cursor.moveToNext() && out.size < 4) {
                val name = cursor.getString(0) ?: continue
                val number = cursor.getString(1) ?: continue
                if (seen.add(name)) out += SearchHit.Contact(name, number)
            }
            out
        } ?: emptyList()
    }.getOrDefault(emptyList())

private fun loadApps(context: android.content.Context): List<SearchHit.App> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val sizePx = (32 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    return runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        .distinctBy { it.activityInfo.packageName }
        .mapNotNull { info ->
            runCatching {
                SearchHit.App(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

private fun providerLabelRes(id: String): Int = when (id) {
    SearchStore.PROVIDER_YOUTUBE -> R.string.provider_youtube
    SearchStore.PROVIDER_MAPS -> R.string.provider_maps
    SearchStore.PROVIDER_PLAY -> R.string.provider_play
    SearchStore.PROVIDER_WIKIPEDIA -> R.string.provider_wikipedia
    SearchStore.PROVIDER_DUCKDUCKGO -> R.string.provider_duckduckgo
    SearchStore.PROVIDER_AMAZON -> R.string.provider_amazon
    else -> R.string.provider_google
}

private fun numberActionLabelRes(id: String): Int = when (id) {
    SearchStore.NUMBER_SMS -> R.string.search_number_sms
    SearchStore.NUMBER_WHATSAPP -> R.string.search_number_whatsapp
    SearchStore.NUMBER_TELEGRAM -> R.string.search_number_telegram
    else -> R.string.search_number_dial
}
