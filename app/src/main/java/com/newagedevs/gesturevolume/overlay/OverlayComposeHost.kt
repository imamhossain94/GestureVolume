package com.newagedevs.gesturevolume.overlay

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A Compose root that can live in a window a Service owns.
 *
 * Compose expects to find a lifecycle, a saved-state registry and a ViewModel store above it in
 * the view tree, and an Activity provides all three for free. A window added straight through
 * `WindowManager.addView` from a Service has none, so this supplies them: one host per window,
 * created when the window goes up and destroyed when it comes down.
 *
 * The lifecycle is driven by the view's own attachment, which is the only honest signal a
 * Service-owned window has: RESUMED once the window manager has attached the view, DESTROYED
 * when [destroy] is called. Nothing is saved across the two — an overlay that has been dismissed
 * has nothing to restore.
 */
class OverlayComposeHost(context: Context) :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    /** The view to hand to the window manager. */
    val view: ComposeView = ComposeView(context)

    private var destroyed = false

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        attachTo(view)
        // Disposed explicitly in [destroy]; the window is removed by hand, so the composition's
        // lifetime is this host's, not the view pool's.
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!destroyed) lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }

            override fun onViewDetachedFromWindow(v: View) {
                if (!destroyed) lifecycleRegistry.currentState = Lifecycle.State.CREATED
            }
        })
    }

    /**
     * Puts this host's three owners on [target].
     *
     * Called for the ComposeView itself, and again for any view wrapped around it before the
     * window is added. Compose resolves the recomposer from the *root* of the window rather than
     * from the ComposeView, so a ComposeView placed inside a wrapper leaves the root with no
     * owner on it and the composition throws `ViewTreeLifecycleOwner not found` the instant the
     * window is attached.
     */
    fun attachTo(target: View) {
        target.setViewTreeLifecycleOwner(this)
        target.setViewTreeSavedStateRegistryOwner(this)
        target.setViewTreeViewModelStoreOwner(this)
    }

    fun setContent(content: @Composable () -> Unit) {
        view.setContent(content)
    }

    /**
     * Tears the composition down. Call after the view has been removed from the window manager,
     * or before — either order is safe, and calling twice is a no-op.
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
