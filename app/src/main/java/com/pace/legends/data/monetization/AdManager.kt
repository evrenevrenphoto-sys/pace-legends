package com.pace.legends.data.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.pace.legends.domain.manager.RemoteConfigManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigManager: RemoteConfigManager,
    private val subscriptionManager: SubscriptionManager
) {

    private var interstitialAd: InterstitialAd? = null
    // Real ID or Test ID (ca-app-pub-3940256099942544/1033173712 for test)
    // P1 FIX: Hardcoded key moved to BuildConfig
    private val adUnitId = com.pace.legends.BuildConfig.ADMOB_AD_UNIT_ID 

    fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(context, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                interstitialAd = null
            }

            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
            }
        })
    }

    fun showInterstitial(activity: Activity) {
        // 1. Check if user is PRO
        if (subscriptionManager.isProUser.value) return

        // 2. Check Remote Config
        CoroutineScope(Dispatchers.Main).launch {
            // Assume we can access flow value or property from RemoteConfigManager
            if (remoteConfigManager.shouldShowAds()) {
                if (interstitialAd != null) {
                    interstitialAd?.show(activity)
                    interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial() // Preload next
                        }
                        
                        override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                            interstitialAd = null
                        }
                    }
                } else {
                    loadInterstitial()
                }
            }
        }
    }
}
