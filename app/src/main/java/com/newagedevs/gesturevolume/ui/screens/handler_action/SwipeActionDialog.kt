package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions

/**
 * Picks what swiping the bar up, or down, does.
 *
 * It offered five things — nothing, the Quick panel, and the volume and brightness bindings for its
 * direction — while the controller had long since been able to run *any* action on a vertical swipe:
 * a binding that is not steered fires once, at the top of the stroke, exactly as a tap does. So this
 * is now the full catalog, with the bindings that are steered by the length of the stroke kept
 * together at the head, where the Quick panel, which both directions default to, leads.
 */
@Composable
fun SwipeActionDialog(
    title: String,
    currentAction: String,
    isSwipeUp: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    /** See [TapActionDialog]'s parameter of the same name. */
    onUseEverywhere: (() -> Unit)? = null,
) {
    val groups = remember(isSwipeUp) {
        val steered = listOfNotNull(HandlerActionCatalog.entryFor(HandlerActions.OPEN_QUICK_SLIDER)) +
            HandlerActionCatalog.swipeAdjust(isSwipeUp)
        // Everything else, minus the Quick panel, which is already at the head.
        val rest = HandlerActionCatalog.grouped(allowReposition = false).mapNotNull { (group, entries) ->
            val kept = entries.filterNot { it.action == HandlerActions.OPEN_QUICK_SLIDER }
            if (kept.isEmpty()) null else group to kept
        }
        listOf(HandlerActionCatalog.Group.SWIPE to steered) + rest
    }
    ActionPickerDialog(title, currentAction, groups, onDismiss, onSelect, onUseEverywhere)
}
