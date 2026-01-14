package com.pace.legends.domain.model



data class UserProgress(
    val trackId: String = "",
    val userId: String = "", // Firestore ID
    val displayName: String = "Anonymous",
    val totalSteps: Long = 0,
    val completedLoops: Int = 0,
    val totalActiveTimeMillis: Long = 0,
    val lastUpdateTimestamp: Long = 0,
    val bestLapTimeSeconds: Long = 0,  // Kullanıcının en iyi tur süresi (hayalet için kişisel rekor)
    val isSynced: Boolean = false,
    val lastSyncedTimestamp: Long = 0,
    val totalDistanceWalked: Double = 0.0,      // Pist üzerinde toplam mesafe
    val currentLapStartTime: Long = 0,          // Mevcut turun başlangıcı
    val periodStartTime: Long = 0,              // Dönem başlangıcı (ayın 1'i veya seçim tarihi)
    val currentLapNumber: Int = 1,               // Şu an kaçıncı turda
    // 🆕 Tüm zamanların toplamı (period bağımsız)
    val allTimeSteps: Long = 0,
    val allTimeLaps: Int = 0,
    val allTimeDistanceMeters: Double = 0.0,
    // 🆕 Pro Member Status
    val isPro: Boolean = false
)
