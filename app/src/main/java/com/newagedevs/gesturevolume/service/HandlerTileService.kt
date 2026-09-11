package com.newagedevs.gesturevolume.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A Quick Settings tile that puts the bar away and brings it back: one tap from the shade, beside
 * the system's own Screen record tile.
 *
 * It exists because nothing else can do this job. Android tells an app nothing before the phone's
 * buttons take a screenshot, or while another app records the screen, so the bar cannot step aside
 * for either on its own. This makes stepping it aside a tap. It sends the same deliberate commands
 * as the notification's Show and Hide button, so the bar stays put away until shown again.
 */
@AndroidEntryPoint
class HandlerTileService : TileService() {

    @Inject
    lateinit var preference: SharedPref

    override fun onStartListening() {
        super.onStartListening()
        render(preference.isHandlerHidden())
    }

    override fun onClick() {
        super.onClick()
        if (!OverlayRuntime.isOverlayActive(this)) {
            render(preference.isHandlerHidden())
            return
        }
        val hide = !preference.isHandlerHidden()
        OverlayRuntime.sendCommand(this, if (hide) "user_hide" else "user_show")
        // Drawn from what was asked for rather than read back: on the notification route the
        // command lands a moment later, and the tile would show the old state until it did.
        render(hide)
    }

    private fun render(hidden: Boolean) {
        val tile = qsTile ?: return
        val running = OverlayRuntime.isOverlayActive(this)
        tile.state = when {
            !running -> Tile.STATE_UNAVAILABLE
            hidden -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        tile.label = getString(R.string.tile_handler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when {
                    !running -> R.string.tile_handler_off
                    hidden -> R.string.tile_handler_hidden
                    else -> R.string.tile_handler_shown
                }
            )
        }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to redraw the tile, after the bar was shown or put away some other way. */
        fun refresh(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, HandlerTileService::class.java))
            }
        }
    }
}
