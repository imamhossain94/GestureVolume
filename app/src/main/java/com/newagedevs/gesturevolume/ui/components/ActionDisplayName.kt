package com.newagedevs.gesturevolume.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions

/**
 * What an action is called on screen: the app's own name for one that opens an app — "WhatsApp"
 * says more than "Launch app" in a row about a double tap — and the catalog's label for the rest.
 *
 * An app since uninstalled reads as "Launch app": the action is still there, and still does
 * nothing, which the row should not pretend otherwise about by naming an app that is gone.
 */
@Composable
fun actionDisplayName(action: String): String {
    val pkg = HandlerActions.launchedPackage(action)
    if (pkg != null) {
        val context = LocalContext.current
        val name = remember(pkg) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
        }
        return name ?: stringResource(R.string.action_launch_app)
    }
    return HandlerActionCatalog.displayEntryFor(action)?.let { stringResource(it.labelRes) } ?: action
}
