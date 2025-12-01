package com.newagedevs.gesturevolume.ui.viewmodels

import com.limurse.iap.DataWrappers

sealed class MainEffect {
    data class ShowToast(val message: String) : MainEffect()
    data class ProductDetailsLoaded(val details: DataWrappers.PricingPhase) : MainEffect()
    object RequestOverlayPermission : MainEffect()
    object RequestNotificationPermission : MainEffect()
    object ShowProDialog : MainEffect()
    object NavigateToAbout : MainEffect()
    object NavigateToFeedback : MainEffect()
}