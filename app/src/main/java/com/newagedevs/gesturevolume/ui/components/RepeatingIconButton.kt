package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A small button that fires once on press and then keeps firing while it is held.
 *
 * For nudging a value one step at a time. Tapping is exact and holding is fast, which is the pair
 * of behaviours a keyboard's arrow key has and a Compose `IconButton` does not: `onClick` fires on
 * *release*, once, so a user who wants twenty steps taps twenty times.
 *
 * The acceleration is deliberately coarse — one slow phase, then one fast one. A smooth ramp
 * sounds better and is worse to aim with: the rate under the finger keeps changing, so the user
 * cannot learn how long a press is worth and ends up overshooting and correcting. Two rates are
 * two things to learn, and the fast one is slow enough (25 per second) to stop on a value.
 */
@Composable
fun RepeatingIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    content: @Composable () -> Unit,
) {
    // Read at fire time rather than captured when the gesture started, so a repeat that runs past
    // the end of the range stops driving a callback whose owner has already disabled it.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    val haptics = LocalHapticFeedback.current
    // The repeat outlives the pointer callback that starts it, so it needs a scope tied to the
    // composition rather than to the gesture: the gesture scope ends the moment the handler
    // returns, which is before the first delay has elapsed.
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (enabled) tint.copy(alpha = 0.10f) else Color.Transparent
            )
            .semantics {
                this.role = Role.Button
                this.contentDescription = contentDescription
                onClick { currentOnClick(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    // Deliberately not consumed. These buttons sit in a scrolling settings list,
                    // and consuming the press would stop the list scrolling whenever a finger
                    // happened to land on one — a 28dp dead zone every second row. Leaving it
                    // unconsumed costs nothing, because a scroll does not begin until the finger
                    // has passed the touch slop, and when it does the cancellation below ends the
                    // repeat. A press that stays put is still a press.
                    awaitFirstDown(requireUnconsumed = false)

                    val job = scope.launch {
                        if (currentEnabled) {
                            currentOnClick()
                            haptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                            )
                        }
                        // The pause before the repeat starts, so a tap is only ever one step.
                        delay(400)
                        var interval = 90L
                        var fired = 0
                        while (isActive && currentEnabled) {
                            currentOnClick()
                            fired++
                            // Six slow steps is about half a second — long enough to place a
                            // value by ear, short enough that a long haul does not feel stuck.
                            if (fired == 6) interval = 40L
                            delay(interval)
                        }
                    }

                    // Cancelled as well as completed: a press that slides off the button, or that
                    // the list steals to scroll, must stop repeating rather than run on.
                    waitForUpOrCancellation()
                    job.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) tint else tint.copy(alpha = 0.3f)
        ) {
            content()
        }
    }
}
