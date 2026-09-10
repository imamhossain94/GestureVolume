package com.newagedevs.gesturevolume.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** A place's current weather and the day's range, as the Deck's card shows it. */
data class WeatherReport(
    val temperatureC: Int,
    val feelsLikeC: Int,
    val code: Int,
    val highC: Int,
    val lowC: Int,
    val isDay: Boolean,
    val place: String?
)

/**
 * Current conditions from Open-Meteo.
 *
 * Open-Meteo rather than one of the keyed services: it needs no API key, so nothing about this
 * feature depends on a secret that a fork or a clean checkout would not have, and its free tier
 * asks for no attribution beyond the licence. Only a coarse latitude and longitude ever leave the
 * device, rounded to two decimals — roughly a kilometre, which is the resolution a temperature
 * reading deserves anyway.
 *
 * Plain `HttpURLConnection` rather than a client library: one request, two JSON objects, and the
 * app already carries enough dependencies.
 */
object Weather {

    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    private const val TIMEOUT_MS = 8_000

    /** Whether a location fix can be had at all. */
    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The most recent fix any provider has, or null.
     *
     * Deliberately the *last known* location rather than a fresh request: a weather card does not
     * justify waking the GPS, and a fix from a few minutes ago is the same weather. Returns null
     * when nothing has a fix, which the card reports rather than spinning forever.
     */
    fun lastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        return providers.mapNotNull { provider ->
            runCatching {
                @Suppress("MissingPermission")
                manager.getLastKnownLocation(provider)
            }.getOrNull()
        }.maxByOrNull { it.time }
    }

    /** Blocking. Call from a background dispatcher. */
    fun fetch(latitude: Double, longitude: Double, placeName: String?): WeatherReport? {
        val lat = String.format(Locale.US, "%.2f", latitude)
        val lon = String.format(Locale.US, "%.2f", longitude)
        val url = "$ENDPOINT?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val body = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                try {
                    if (responseCode != HttpURLConnection.HTTP_OK) return@run null
                    inputStream.bufferedReader().use { it.readText() }
                } finally {
                    disconnect()
                }
            }
        }.getOrNull() ?: return null

        return runCatching {
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            WeatherReport(
                temperatureC = current.getDouble("temperature_2m").roundToInt(),
                feelsLikeC = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")).roundToInt(),
                code = current.optInt("weather_code", 0),
                highC = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                lowC = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                isDay = current.optInt("is_day", 1) == 1,
                place = placeName
            )
        }.getOrNull()
    }

    /**
     * The WMO weather code, as a description.
     *
     * The codes are grouped rather than listed one by one: "light drizzle" and "dense drizzle"
     * are the same coat.
     */
    fun descriptionRes(code: Int): Int = when (code) {
        0 -> com.newagedevs.gesturevolume.R.string.weather_clear
        1, 2 -> com.newagedevs.gesturevolume.R.string.weather_partly_cloudy
        3 -> com.newagedevs.gesturevolume.R.string.weather_overcast
        45, 48 -> com.newagedevs.gesturevolume.R.string.weather_fog
        in 51..57 -> com.newagedevs.gesturevolume.R.string.weather_drizzle
        in 61..67, in 80..82 -> com.newagedevs.gesturevolume.R.string.weather_rain
        in 71..77, in 85..86 -> com.newagedevs.gesturevolume.R.string.weather_snow
        in 95..99 -> com.newagedevs.gesturevolume.R.string.weather_thunderstorm
        else -> com.newagedevs.gesturevolume.R.string.weather_unknown
    }

    /** Degrees in the unit the user's locale uses, since Open-Meteo is asked for Celsius. */
    fun format(context: Context, celsius: Int): String {
        val fahrenheit = usesFahrenheit(context)
        val value = if (fahrenheit) (celsius * 9 / 5.0 + 32).roundToInt() else celsius
        return context.getString(
            if (fahrenheit) com.newagedevs.gesturevolume.R.string.weather_degrees_f
            else com.newagedevs.gesturevolume.R.string.weather_degrees_c,
            value
        )
    }

    /**
     * Whether this locale is one of the handful that still measures temperature in Fahrenheit.
     *
     * `LocaleData` is not public API and `Locale` carries no unit system below Android 14, so the
     * country list is the honest way to answer it.
     */
    private fun usesFahrenheit(context: Context): Boolean {
        val country = context.resources.configuration.locales[0].country.uppercase(Locale.US)
        return country in setOf("US", "BS", "BZ", "KY", "LR", "PW", "FM", "MH")
    }
}
