package com.stackdrop.game.billing

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing for the one-time "premium" product (no banner or between-run ads, and the
 * gold app icon). Play is the source of truth: ownership is re-checked on every launch and every
 * return to the app, so reinstalls, new phones, refunds and pending purchases that complete in the
 * background are handled. All callbacks arrive on the UI thread.
 */
class BillingManager(private val activity: Activity, private val listener: Listener) : PurchasesUpdatedListener {

    interface Listener {
        /** Called on every launch once Play answers, and after a purchase. */
        fun onPremiumChanged(owned: Boolean)

        /** The localized price, e.g. "$2.99", or null when the product is not available. */
        fun onPrice(price: String?)

        fun onMessage(message: String)
    }

    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var product: ProductDetails? = null
    private val handler = Handler(Looper.getMainLooper())
    private var connected = false
    private var connecting = false
    private var ended = false
    private var retryMs = FIRST_RETRY_MS

    fun start() {
        if (ended || connected || connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    // Play may be updating or briefly unavailable; try again, waiting longer each time.
                    Log.i(TAG, "Billing unavailable: ${result.responseCode} ${result.debugMessage}")
                    if (!ended) handler.postDelayed(::start, retryMs)
                    retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
                    return
                }
                connected = true
                retryMs = FIRST_RETRY_MS
                loadProduct()
                restore()
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection reconnects on the next call.
                Log.i(TAG, "Billing service disconnected")
            }
        })
    }

    fun buy() {
        val details = product
        val offer = details?.oneTimePurchaseOfferDetails
        if (details == null || offer == null) {
            listener.onMessage("Premium isn't available right now")
            loadProduct()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .apply { offer.offerToken?.let(::setOfferToken) }
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "Purchase flow not started: ${result.responseCode} ${result.debugMessage}")
            listener.onMessage("Couldn't open the purchase. Try again")
        }
    }

    /** On returning to the app: picks up purchases that completed meanwhile, or retries setup. */
    fun refresh() {
        if (!connected) return start()
        if (product == null) loadProduct()
        restore()
    }

    fun end() {
        ended = true
        handler.removeCallbacksAndMessages(null)
        client.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), fromRestore = false)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                listener.onMessage("Purchase didn't go through")
            }
        }
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Product details unavailable: ${result.responseCode} ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            product = details.productDetailsList.firstOrNull()
            listener.onPrice(product?.oneTimePurchaseOfferDetails?.formattedPrice)
        }
    }

    private fun restore() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            // Only a definite answer from Play may take premium away; an error leaves it as it was.
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, fromRestore = true)
        }
    }

    private fun handle(purchases: List<Purchase>, fromRestore: Boolean) {
        val purchase = purchases.firstOrNull { PREMIUM_ID in it.products }
        if (purchase == null) {
            if (fromRestore) listener.onPremiumChanged(false)
            return
        }
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) acknowledge(purchase)
                listener.onPremiumChanged(true)
            }
            Purchase.PurchaseState.PENDING ->
                if (!fromRestore) listener.onMessage("Purchase pending. Premium unlocks once it completes")
            else -> Unit
        }
    }

    /**
     * Unacknowledged purchases are refunded by Play after three days, so a failure is retried a few
     * times now, and again on every return to the app (refresh) until it goes through.
     */
    private fun acknowledge(purchase: Purchase, attempt: Int = 1) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) return@acknowledgePurchase
            Log.w(TAG, "Acknowledge failed (attempt $attempt): ${result.responseCode} ${result.debugMessage}")
            if (attempt < ACK_ATTEMPTS && !ended) {
                handler.postDelayed({ acknowledge(purchase, attempt + 1) }, FIRST_RETRY_MS * attempt)
            }
        }
    }

    companion object {
        /** Create a one-time product with exactly this ID in Play Console → Monetize → In-app products. */
        const val PREMIUM_ID = "premium"
        private const val TAG = "Billing"
        private const val FIRST_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 5 * 60_000L
        private const val ACK_ATTEMPTS = 3
    }
}
