package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.SearchStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The router decides what a typed line *is*, and every wrong answer is visible: a search that
 * opens the dialer, a phone number that goes to Google. These pin the order of the tiers.
 */
class SearchRouterTest {

    private fun route(input: String, calculator: Boolean = true) = SearchRouter.route(
        input = input,
        numberAction = SearchStore.NUMBER_DIAL,
        defaultProvider = SearchStore.PROVIDER_GOOGLE,
        calculatorEnabled = calculator
    )

    @Test
    fun `a phone number beats arithmetic`() {
        // 555-1234 parses as subtraction; a number has to win, or dialling is impossible.
        val r = route("555-1234")
        assertTrue(r is SearchRouter.Route.PhoneNumber)
        assertEquals("5551234", (r as SearchRouter.Route.PhoneNumber).digits)
    }

    @Test
    fun `international numbers keep their plus`() {
        val r = route("+880 1711 111111") as SearchRouter.Route.PhoneNumber
        assertEquals("+8801711111111", r.digits)
    }

    @Test
    fun `arithmetic beats the web`() {
        val r = route("12*3")
        assertTrue(r is SearchRouter.Route.Calculation)
        assertEquals("36", (r as SearchRouter.Route.Calculation).result)
    }

    @Test
    fun `arithmetic goes to the web when the calculator is off`() {
        assertTrue(route("12*3", calculator = false) is SearchRouter.Route.Web)
    }

    @Test
    fun `plain words go to the web`() {
        val r = route("weather in dhaka")
        assertTrue(r is SearchRouter.Route.Web)
        assertEquals("weather in dhaka", (r as SearchRouter.Route.Web).query)
    }

    @Test
    fun `empty is empty`() {
        assertTrue(route("   ") is SearchRouter.Route.Empty)
    }

    @Test
    fun `short and long digit runs are not phone numbers`() {
        assertFalse(SearchRouter.looksLikePhoneNumber("12345"))
        assertFalse(SearchRouter.looksLikePhoneNumber("1234567890123456789"))
        assertTrue(SearchRouter.looksLikePhoneNumber("(02) 9955-1234"))
    }

    @Test
    fun `international form uses the prefix only when the number lacks one`() {
        assertEquals("8801711111111", SearchRouter.toInternational("01711111111", "880"))
        assertEquals("8801711111111", SearchRouter.toInternational("+8801711111111", "880"))
        assertEquals("1711111111", SearchRouter.toInternational("01711111111", ""))
    }
}
