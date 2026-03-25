package com.newagedevs.gesturevolume.ui.viewmodels

import com.newagedevs.gesturevolume.manager.BillingManager

sealed class MainEffect {
    data class ShowToast(val message: String) : MainEffect()
    data class ProductDetailsLoaded(val details: BillingManager.ProductPrice) : MainEffect()
    object RequestOverlayPermission : MainEffect()
    object RequestNotificationPermission : MainEffect()
    object ShowProDialog : MainEffect()
    object NavigateToAbout : MainEffect()
    object NavigateToFeedback : MainEffect()
    object ShowThemeDialog : MainEffect()
    object ShowLanguageDialog : MainEffect()
}