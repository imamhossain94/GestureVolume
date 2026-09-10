package com.newagedevs.gesturevolume.overlay.deck

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.Weather
import com.newagedevs.gesturevolume.utils.WeatherReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the card is doing right now. */
private sealed interface WeatherUi {
    data object Loading : WeatherUi
    data object NoPermission : WeatherUi
    data object NoLocation : WeatherUi
    data object Failed : WeatherUi
    data class Ready(val report: WeatherReport) : WeatherUi
}

/**
 * Local conditions, from the last location fix the phone already has.
 *
 * Three states are failures the user can do something about, and each says what: the permission
 * has not been granted, nothing has a location fix yet, or the request did not come back. None of
 * them spins forever, which is what a weather card that cannot reach the network usually does.
 *
 * The permission is requested from a settings screen rather than here — an overlay cannot show a
 * runtime prompt — so the button opens the app's permission page.
 */
@Composable
fun WeatherCardContent(actions: DeckActions, palette: DeckPalette) {
    val context = actions.env.context
    var ui by remember { mutableStateOf<WeatherUi>(WeatherUi.Loading) }

    LaunchedEffect(Unit) {
        ui = when {
            !Weather.hasLocationPermission(context) -> WeatherUi.NoPermission
            else -> {
                val fix = withContext(Dispatchers.IO) { Weather.lastKnownLocation(context) }
                if (fix == null) {
                    WeatherUi.NoLocation
                } else {
                    val report = withContext(Dispatchers.IO) {
                        Weather.fetch(fix.latitude, fix.longitude, null)
                    }
                    if (report == null) WeatherUi.Failed else WeatherUi.Ready(report)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        when (val state = ui) {
            is WeatherUi.Loading -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(28.dp))
            }

            is WeatherUi.NoPermission -> {
                DeckHint(stringResource(R.string.weather_needs_location), palette)
                Spacer(modifier = Modifier.height(10.dp))
                DeckButton(text = stringResource(R.string.open_settings), palette = palette) {
                    actions.close()
                    actions.launch(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }
            }

            is WeatherUi.NoLocation -> DeckHint(stringResource(R.string.weather_no_fix), palette)

            is WeatherUi.Failed -> {
                DeckHint(stringResource(R.string.weather_failed), palette)
                Spacer(modifier = Modifier.height(10.dp))
                DeckButton(text = stringResource(R.string.weather_retry), palette = palette, filled = false) {
                    ui = WeatherUi.Loading
                }
            }

            is WeatherUi.Ready -> {
                val report = state.report
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = weatherIcon(report.code, report.isDay),
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = Weather.format(context, report.temperatureC),
                            color = palette.onBackground,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            text = stringResource(Weather.descriptionRes(report.code)),
                            color = palette.subtle,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.weather_high_low,
                        Weather.format(context, report.highC),
                        Weather.format(context, report.lowC)
                    ),
                    color = palette.onBackground,
                    fontSize = 13.sp
                )
                Text(
                    text = stringResource(
                        R.string.weather_feels_like,
                        Weather.format(context, report.feelsLikeC)
                    ),
                    color = palette.subtle,
                    fontSize = 13.sp
                )
                DeckHint(stringResource(R.string.weather_source), palette)
            }
        }
    }
}

private fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    2, 3, 45, 48 -> Icons.Filled.Cloud
    in 51..57 -> Icons.Filled.Grain
    in 61..67, in 80..82 -> Icons.Filled.Umbrella
    in 71..77, in 85..86 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.Bolt
    else -> Icons.Filled.Cloud
}
