package com.newagedevs.gesturevolume.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.data.local.SharedPref
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PurchaseEvent {
    PURCHASE_SUCCESS,
    PURCHASE_RESTORED,
    ALREADY_OWNED,
    PURCHASE_FAILURE,
    NOTHING_TO_RESTORE
}

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preference: SharedPref
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var retryCount = 0
    private val maxRetries = 5

    init {
        Log.d("BillingManager", "BillingManager initialized")
    }

    private val _events = MutableSharedFlow<PurchaseEvent>()
    val events: SharedFlow<PurchaseEvent> = _events.asSharedFlow()

    private val _isPremium = MutableStateFlow(preference.isProFeatureActivated())
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    data class ProductPrice(
        val formattedPrice: String,
        val amount: Double,
        val currencyCode: String
    )

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _lifetimePrice = MutableStateFlow(ProductPrice("$7.99", 7.99, "USD"))
    val lifetimePrice: StateFlow<ProductPrice> = _lifetimePrice.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private var pendingPurchaseActivity: Activity? = null
    private var pendingPurchaseProductId: String? = null

    fun initialize() {
        Log.d("BillingManager", "initialize() called")
        scope.launch {
            startConnection()
        }
    }

    private suspend fun startConnection() {
        withContext(Dispatchers.Main) {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingResponseCode.OK) {
                        retryCount = 0
                        queryProductDetails()
                        queryPurchasesSilent()
                        
                        val act = pendingPurchaseActivity
                        val pid = pendingPurchaseProductId
                        if (act != null && pid != null) {
                            pendingPurchaseActivity = null
                            pendingPurchaseProductId = null
                            purchase(act, pid)
                        }
                    } else {
                        scheduleRetry()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    scheduleRetry()
                }
            })
        }
    }

    private fun scheduleRetry() {
        if (retryCount >= maxRetries) {
            return
        }
        val delayMs = (1L shl retryCount) * 2000L // 2s, 4s, 8s, 16s, 32s
        retryCount++
        scope.launch {
            delay(delayMs)
            if (!billingClient.isReady) {
                startConnection()
            }
        }
    }

    private fun queryProductDetails() {
        val inAppProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BuildConfig.PRODUCT_LIFETIME)
                .setProductType(ProductType.INAPP)
                .build()
        )
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProducts)
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, result ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                result.productDetailsList?.forEach { details ->
                    productDetailsMap[details.productId] = details
                    updatePriceFlow(details)
                }
            }
        }
    }

    private fun updatePriceFlow(productDetails: ProductDetails) {
        val productId = productDetails.productId
        if (productDetails.productType == ProductType.INAPP) {
            productDetails.oneTimePurchaseOfferDetails?.let { details ->
                val price = details.formattedPrice
                val amount = details.priceAmountMicros / 1000000.0
                val currency = details.priceCurrencyCode

                if (productId == BuildConfig.PRODUCT_LIFETIME) {
                    _lifetimePrice.value = ProductPrice(price, amount, currency)
                }
            }
        }
    }

    fun purchase(activity: Activity, productId: String) {
        if (!billingClient.isReady) {
            pendingPurchaseActivity = activity
            pendingPurchaseProductId = productId
            scope.launch { startConnection() }
            return
        }

        val productDetails = productDetailsMap[productId]
        if (productDetails == null) {
            queryProductDetails()
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
            // User canceled
        } else {
            handlePurchaseFailure(billingResult.responseCode)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingResponseCode.OK) {
                        val productId = purchase.products.firstOrNull() ?: ""
                        handlePurchaseSuccess(productId)
                    }
                }
            } else {
                val productId = purchase.products.firstOrNull() ?: ""
                handlePurchaseSuccess(productId)
            }
        }
    }

    private fun handlePurchaseSuccess(productId: String) {
        preference.setProFeatureActivated(true)
        _isPremium.value = true
        scope.launch {
            _events.emit(PurchaseEvent.PURCHASE_SUCCESS)
        }
    }

    private fun handlePurchaseFailure(code: Int?) {
        if (code == BillingResponseCode.ITEM_ALREADY_OWNED) {
            scope.launch { _events.emit(PurchaseEvent.ALREADY_OWNED) }
            queryPurchasesSilent()
            return
        }
        scope.launch { _events.emit(PurchaseEvent.PURCHASE_FAILURE) }
    }

    fun restorePurchases() {
        if (_isRestoring.value) return
        _isRestoring.value = true
        queryPurchases(isExplicitRestore = true)
    }

    fun queryPurchasesSilent() {
        queryPurchases(isExplicitRestore = false)
    }

    fun queryPurchases(isExplicitRestore: Boolean = false) {
        if (!billingClient.isReady) {
            scope.launch { startConnection() }
            return
        }

        var inappProcessed = false
        var isProFound = false
        var restoredProductId: String? = null

        fun checkFinished() {
            if (inappProcessed) {
                finalizePurchaseQuery(
                    isProFound = isProFound,
                    restoredProductId = restoredProductId,
                    isExplicitRestore = isExplicitRestore
                )
            }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val result = findActivePurchase(purchases)
                if (result != null) {
                    isProFound = true
                    if (restoredProductId == null) restoredProductId = result
                }
            }
            inappProcessed = true
            checkFinished()
        }
    }

    private fun findActivePurchase(purchases: List<Purchase>): String? {
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                continue
            }
            val productId = purchase.products.firstOrNull() ?: continue
            if (productId == BuildConfig.PRODUCT_LIFETIME) {
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams) { }
                }
                return productId
            }
        }
        return null
    }

    private fun finalizePurchaseQuery(
        isProFound: Boolean,
        restoredProductId: String?,
        isExplicitRestore: Boolean
    ) {
        if (isProFound && restoredProductId != null) {
            preference.setProFeatureActivated(true)
            _isPremium.value = true

            if (isExplicitRestore) {
                _isRestoring.value = false
                scope.launch { _events.emit(PurchaseEvent.PURCHASE_RESTORED) }
            }
        } else {
            preference.setProFeatureActivated(false)
            _isPremium.value = false

            if (isExplicitRestore) {
                _isRestoring.value = false
                scope.launch { _events.emit(PurchaseEvent.NOTHING_TO_RESTORE) }
            }
        }
    }
}
