package com.newagedevs.gesturevolume.utils

import android.net.Uri
import com.newagedevs.gesturevolume.data.local.SearchStore

/**
 * Where a typed query goes.
 *
 * A pure function over strings, for the same reason [AudioStreamResolver] is one: deciding that
 * "555 1234" is a phone number and "2+2" is a sum is the part that can be wrong, and every wrong
 * answer still *does* something — it opens a dialer for a search, or searches for a sum. Keeping
 * the decision free of Android types means it is tested on the host JVM.
 *
 * The order is load-bearing. A number is checked before arithmetic, because "555-1234" parses as
 * subtraction; arithmetic is checked before the web, because a sum answered inline is the whole
 * point of having a calculator in the search bar.
 */
object SearchRouter {

    /** What a query turns into. */
    sealed interface Route {
        /** A sum, already worked out. */
        data class Calculation(val expression: String, val result: String) : Route

        /** A phone number, in whichever form the user asked for. */
        data class PhoneNumber(val digits: String, val action: String) : Route

        /** A web search on a provider. */
        data class Web(val provider: String, val query: String) : Route

        /** Nothing typed. */
        data object Empty : Route
    }

    /**
     * Whether [input] is a phone number: enough digits, and nothing but the punctuation numbers
     * are written with.
     *
     * Seven digits is the shortest number worth dialling anywhere; the ceiling of fifteen is the
     * E.164 maximum, above which it is an account number or an order reference, not a phone.
     */
    fun looksLikePhoneNumber(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.matches(Regex("""^\+?[0-9\s\-()./]+$"""))) return false
        val digits = trimmed.filter { it.isDigit() }
        return digits.length in 7..15
    }

    /** The digits of a phone number, keeping a leading `+`. */
    fun normalizeNumber(input: String): String {
        val trimmed = input.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /**
     * The number in the form a `wa.me` or `t.me` link needs: digits only, with the country code
     * in front. [dialPrefix] is used only when the number has no `+` of its own — a number the
     * user typed with one already knows its country.
     */
    fun toInternational(input: String, dialPrefix: String): String {
        val normalized = normalizeNumber(input)
        if (normalized.startsWith("+")) return normalized.drop(1)
        val digits = normalized.trimStart('0')
        val prefix = dialPrefix.filter { it.isDigit() }
        return if (prefix.isEmpty()) digits else prefix + digits
    }

    fun route(
        input: String,
        numberAction: String,
        defaultProvider: String,
        calculatorEnabled: Boolean
    ): Route {
        val query = input.trim()
        if (query.isEmpty()) return Route.Empty
        if (looksLikePhoneNumber(query)) {
            return Route.PhoneNumber(normalizeNumber(query), numberAction)
        }
        if (calculatorEnabled && ExpressionEvaluator.looksLikeExpression(query)) {
            ExpressionEvaluator.evaluateToText(query)?.let {
                return Route.Calculation(query, it)
            }
        }
        return Route.Web(defaultProvider, query)
    }

    /** The URL a provider searches [query] at. */
    fun webUrl(provider: String, query: String): String {
        val q = Uri.encode(query)
        return when (provider) {
            SearchStore.PROVIDER_YOUTUBE -> "https://www.youtube.com/results?search_query=$q"
            SearchStore.PROVIDER_MAPS -> "https://www.google.com/maps/search/?api=1&query=$q"
            SearchStore.PROVIDER_PLAY -> "https://play.google.com/store/search?q=$q"
            SearchStore.PROVIDER_WIKIPEDIA -> "https://www.wikipedia.org/search-redirect.php?search=$q"
            SearchStore.PROVIDER_DUCKDUCKGO -> "https://duckduckgo.com/?q=$q"
            SearchStore.PROVIDER_AMAZON -> "https://www.amazon.com/s?k=$q"
            else -> "https://www.google.com/search?q=$q"
        }
    }
}
