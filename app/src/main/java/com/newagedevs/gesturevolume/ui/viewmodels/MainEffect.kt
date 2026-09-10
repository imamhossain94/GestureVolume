package com.newagedevs.gesturevolume.ui.viewmodels

import com.newagedevs.gesturevolume.manager.BillingManager

sealed class MainEffect {
    data class ShowToast(val message: String) : MainEffect()
    data class ProductDetailsLoaded(val details: BillingManager.ProductPrice) : MainEffect()
    object RequestOverlayPermission : MainEffect()

    /**
     * Ask for POST_NOTIFICATIONS, once, when the service is first switched on.
     *
     * Never blocking: the overlay works perfectly without it, and the previous version of this
     * gate refused to start the service at all when the user declined — a silent failure over a
     * notification they were never going to see. All that is lost by declining is the shade's
     * Show/Settings/Stop row.
     */
    object RequestNotificationPermission : MainEffect()
    object ShowProDialog : MainEffect()
    object NavigateToAbout : MainEffect()
    object NavigateToFeedback : MainEffect()
    /** The drawer's Reset row was tapped. The confirmation lives in the UI, not the ViewModel. */
    object ConfirmResetApp : MainEffect()
    object ShowThemeDialog : MainEffect()
    object ShowLanguageDialog : MainEffect()
    object NavigateToTroubleshoot : MainEffect()

    /** Open the system accessibility settings, after the disclosure has been accepted. */
    object OpenAccessibilitySettings : MainEffect()
}