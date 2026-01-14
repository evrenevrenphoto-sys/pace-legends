package com.pace.legends.domain.manager

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val prefs = context.getSharedPreferences("subscription_prefs", Context.MODE_PRIVATE)
    
    init {
        // Debug Override kontrolü
        if (com.pace.legends.BuildConfig.DEBUG && prefs.getBoolean("debug_pro_override", false)) {
            _isPro.value = true
        } else {
            // 🆕 P1 FIX: API Key from BuildConfig (set in local.properties)
            try {
                val apiKey = com.pace.legends.BuildConfig.REVENUECAT_API_KEY
                if (apiKey.isNotBlank()) {
                    Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
                    checkSubscriptionStatus()
                } else {
                    android.util.Log.w("SubscriptionManager", "⚠️ REVENUECAT_API_KEY not set in local.properties")
                }
            } catch (e: Exception) {
                android.util.Log.e("SubscriptionManager", "RevenueCat init failed: ${e.message}")
            }
        }
    }

    fun checkSubscriptionStatus() {
        if (com.pace.legends.BuildConfig.DEBUG && prefs.getBoolean("debug_pro_override", false)) {
            _isPro.value = true
            return
        }

        // Emniyet sübabı: Init başarısız ise crash olmasın
        if (!Purchases.isConfigured) return

        Purchases.sharedInstance.getCustomerInfoWith({ error -> 
            android.util.Log.e("SubscriptionManager", "RC Error: ${error.message}")
        }) { customerInfo ->
            // "pro_access" entitlement ID'si RevenueCat dashboard ile eşleşmeli
            _isPro.value = customerInfo.entitlements["pro_access"]?.isActive == true
        }
    }
    
    fun setDebugPro(enabled: Boolean) {
        prefs.edit().putBoolean("debug_pro_override", enabled).apply()
        _isPro.value = enabled
        if (!enabled) checkSubscriptionStatus() // Disable edilirse gerçeğini kontrol et
    }
    
    fun purchasePro(activity: Activity) {
        // TODO: Implement actual purchase flow
    }
}
