package com.newagedevs.gesturevolume.data.local

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * What the Deck's search bar looks through and where it sends what it cannot answer itself.
 *
 * Providers and the number action are stored as plain identifiers — a persistence format like
 * the action strings, never to be renamed. Unknown values read back as the default.
 */
class SearchStore(private val prefs: SharedPreferences) {

    companion object {
        const val PROVIDER_GOOGLE = "google"
        const val PROVIDER_YOUTUBE = "youtube"
        const val PROVIDER_MAPS = "maps"
        const val PROVIDER_PLAY = "play"
        const val PROVIDER_WIKIPEDIA = "wikipedia"
        const val PROVIDER_DUCKDUCKGO = "duckduckgo"
        const val PROVIDER_AMAZON = "amazon"

        val ALL_PROVIDERS: List<String> = listOf(
            PROVIDER_GOOGLE, PROVIDER_YOUTUBE, PROVIDER_MAPS, PROVIDER_PLAY,
            PROVIDER_WIKIPEDIA, PROVIDER_DUCKDUCKGO, PROVIDER_AMAZON
        )
        val DEFAULT_PROVIDERS: Set<String> = setOf(PROVIDER_GOOGLE, PROVIDER_YOUTUBE, PROVIDER_MAPS)

        const val NUMBER_DIAL = "dial"
        const val NUMBER_SMS = "sms"
        const val NUMBER_WHATSAPP = "whatsapp"
        const val NUMBER_TELEGRAM = "telegram"
        val ALL_NUMBER_ACTIONS: List<String> = listOf(NUMBER_DIAL, NUMBER_SMS, NUMBER_WHATSAPP, NUMBER_TELEGRAM)

        private const val INDEX_APPS = "searchIndexApps"
        private const val INDEX_CONTACTS = "searchIndexContacts"
        private const val CALCULATOR = "searchInlineCalculator"
        private const val PROVIDERS = "searchProviders"
        private const val DEFAULT_PROVIDER = "searchDefaultProvider"
        private const val NUMBER_ACTION = "searchNumberAction"
        private const val DIRECT_CALL = "searchDirectCall"
        private const val DIAL_PREFIX = "searchDialPrefix"
    }

    fun getIndexApps(): Boolean = prefs.getBoolean(INDEX_APPS, true)
    fun setIndexApps(value: Boolean) = prefs.edit { putBoolean(INDEX_APPS, value) }

    /** Off by default: it is the one switch that needs the Contacts permission. */
    fun getIndexContacts(): Boolean = prefs.getBoolean(INDEX_CONTACTS, false)
    fun setIndexContacts(value: Boolean) = prefs.edit { putBoolean(INDEX_CONTACTS, value) }

    fun getInlineCalculator(): Boolean = prefs.getBoolean(CALCULATOR, true)
    fun setInlineCalculator(value: Boolean) = prefs.edit { putBoolean(CALCULATOR, value) }

    fun getProviders(): Set<String> =
        prefs.getStringSet(PROVIDERS, null)?.filter { it in ALL_PROVIDERS }?.toSet()
            ?: DEFAULT_PROVIDERS

    fun setProviders(value: Set<String>) =
        prefs.edit { putStringSet(PROVIDERS, value.filter { it in ALL_PROVIDERS }.toSet()) }

    /** Where a plain query goes when the user presses Search: one of the enabled providers. */
    fun getDefaultProvider(): String {
        val stored = prefs.getString(DEFAULT_PROVIDER, PROVIDER_GOOGLE) ?: PROVIDER_GOOGLE
        return if (stored in ALL_PROVIDERS) stored else PROVIDER_GOOGLE
    }

    fun setDefaultProvider(value: String) = prefs.edit { putString(DEFAULT_PROVIDER, value) }

    fun getNumberAction(): String {
        val stored = prefs.getString(NUMBER_ACTION, NUMBER_DIAL) ?: NUMBER_DIAL
        return if (stored in ALL_NUMBER_ACTIONS) stored else NUMBER_DIAL
    }

    fun setNumberAction(value: String) = prefs.edit { putString(NUMBER_ACTION, value) }

    /** Place the call outright rather than opening the dialer. Needs the Phone permission. */
    fun getDirectCall(): Boolean = prefs.getBoolean(DIRECT_CALL, false)
    fun setDirectCall(value: Boolean) = prefs.edit { putBoolean(DIRECT_CALL, value) }

    /** Country code prepended to a number that has none, digits only, or empty for none. */
    fun getDialPrefix(): String = prefs.getString(DIAL_PREFIX, "") ?: ""
    fun setDialPrefix(value: String) = prefs.edit { putString(DIAL_PREFIX, value.filter { it.isDigit() }.take(4)) }
}
