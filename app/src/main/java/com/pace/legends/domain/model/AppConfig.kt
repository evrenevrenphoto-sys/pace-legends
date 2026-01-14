package com.pace.legends.domain.model

data class AppConfig(
    val showInterstitialAds: Boolean = true,
    val adFrequencySteps: Int = 3000,
    val minVersionCode: Int = 1
)
