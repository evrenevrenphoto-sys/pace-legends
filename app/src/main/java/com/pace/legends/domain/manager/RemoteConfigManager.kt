package com.pace.legends.domain.manager

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.pace.legends.BuildConfig
import com.pace.legends.domain.model.AppConfig
import com.pace.legends.domain.model.PitStopMessagesConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigManager @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
    private val gson = Gson()
    
    private val _configState = MutableStateFlow(AppConfig())
    val configState: StateFlow<AppConfig> = _configState.asStateFlow()
    
    // 🆕 Pit Stop Mesajları
    private val _pitStopMessages = MutableStateFlow(PitStopMessagesConfig.getDefaults())
    val pitStopMessages: StateFlow<PitStopMessagesConfig> = _pitStopMessages.asStateFlow()
    
    // 🆕 Dinamik Arka Plan Adım Limiti
    private val _backgroundStepLimit = MutableStateFlow(DEFAULT_BACKGROUND_STEP_LIMIT)
    val backgroundStepLimit: StateFlow<Long> = _backgroundStepLimit.asStateFlow()
    
    // 🆕 Dinamik Yarışma Süresi (gün)
    private val _raceDurationDays = MutableStateFlow(DEFAULT_RACE_DURATION_DAYS)
    val raceDurationDays: StateFlow<Int> = _raceDurationDays.asStateFlow()
    
    // 🆕 Yarışma Başlangıç Tarihi (yyyy-MM-dd formatında)
    private val _raceStartDate = MutableStateFlow(DEFAULT_RACE_START_DATE)
    val raceStartDate: StateFlow<String> = _raceStartDate.asStateFlow()

    // 🆕 Yeni Remote Config Parametreleri
    private val _raceDisplayName = MutableStateFlow("Aylık Maraton")
    val raceDisplayName: StateFlow<String> = _raceDisplayName.asStateFlow()

    private val _raceDescription = MutableStateFlow("")
    val raceDescription: StateFlow<String> = _raceDescription.asStateFlow()
    
    // Aslında endDate otomatik hesaplanıyor (start + duration) ama override etmek istenirse:
    private val _raceEndDate = MutableStateFlow("") 
    val raceEndDate: StateFlow<String> = _raceEndDate.asStateFlow()
    
    private val _raceEmoji = MutableStateFlow("🏆")
    val raceEmoji: StateFlow<String> = _raceEmoji.asStateFlow()
    
    // 🆕 Lig Sistemi Parametreleri
    private val _promotionThreshold = MutableStateFlow(DEFAULT_PROMOTION_THRESHOLD)
    val promotionThreshold: StateFlow<Int> = _promotionThreshold.asStateFlow()
    
    private val _demotionThreshold = MutableStateFlow(DEFAULT_DEMOTION_THRESHOLD)
    val demotionThreshold: StateFlow<Int> = _demotionThreshold.asStateFlow()
    
    private val _leagueSize = MutableStateFlow(DEFAULT_LEAGUE_SIZE)
    val leagueSize: StateFlow<Int> = _leagueSize.asStateFlow()
    
    // 🆕 Adaptive Throttling: Sync için minimum adım eşiği (Remote Config'den güncellenebilir)
    private val _stepSyncMilestone = MutableStateFlow(DEFAULT_STEP_SYNC_MILESTONE)
    val stepSyncMilestone: StateFlow<Long> = _stepSyncMilestone.asStateFlow()

    init {
        val configSettings = remoteConfigSettings {
            // Debug: 0s, Release: 12 saat (43200s)
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 43200
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Default değerler (XML'den yükle)
        remoteConfig.setDefaultsAsync(com.pace.legends.R.xml.remote_config_defaults)
        
        // İlk açılışta hemen fetch dene
        fetchAndActivate()
    }

    fun fetchAndActivate() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateConfigState()
                    updatePitStopMessages()
                    updateBackgroundStepLimit()
                    updateRaceDuration()
                    updateLeagueSettings()
                    updateStepSyncMilestone() // 🆕 Adaptive Throttling
                } else {
                    // Fetch failed, use defaults or cache
                    updateConfigState()
                    updatePitStopMessages()
                    updateBackgroundStepLimit()
                    updateRaceDuration()
                    updateLeagueSettings()
                    updateStepSyncMilestone() // 🆕 Adaptive Throttling
                }
            }
    }

    private fun updateConfigState() {
        val newConfig = AppConfig(
            showInterstitialAds = remoteConfig.getBoolean(KEY_SHOW_ADS),
            adFrequencySteps = remoteConfig.getLong(KEY_AD_FREQ).toInt(),
            minVersionCode = remoteConfig.getLong(KEY_MIN_VERSION).toInt()
        )
        _configState.value = newConfig
    }
    
    /**
     * 🆕 Pit Stop mesajlarını Remote Config'den parse et
     */
    private fun updatePitStopMessages() {
        try {
            val jsonString = remoteConfig.getString(KEY_PIT_STOP_MESSAGES)
            if (jsonString.isNotEmpty()) {
                val parsed = gson.fromJson(jsonString, PitStopMessagesConfig::class.java)
                if (parsed != null && 
                    (parsed.warningLevel.isNotEmpty() || parsed.criticalLevel.isNotEmpty())) {
                    _pitStopMessages.value = parsed
                    android.util.Log.d("RemoteConfig", "✅ Pit stop messages loaded: ${parsed.warningLevel.size} warning, ${parsed.criticalLevel.size} critical")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RemoteConfig", "❌ Failed to parse pit_stop_messages: ${e.message}")
            // Keep defaults
        }
    }
    
    /**
     * 🆕 Arka plan adım limitini Remote Config'den al
     */
    private fun updateBackgroundStepLimit() {
        val limit = remoteConfig.getLong(KEY_BACKGROUND_STEP_LIMIT)
        if (limit > 0) {
            _backgroundStepLimit.value = limit
            android.util.Log.d("RemoteConfig", "✅ Background step limit loaded: $limit")
        }
    }
    
    /**
     * 🆕 Yarışma süresi parametrelerini Remote Config'den al
     * race_duration_days: Yarışma süresi (gün cinsinden, varsayılan 30)
     * race_start_date: Yarışma başlangıç tarihi (yyyy-MM-dd, varsayılan 2026-01-01)
     */
    private fun updateRaceDuration() {
        val durationDays = remoteConfig.getLong(KEY_RACE_DURATION_DAYS).toInt()
        if (durationDays > 0) {
            _raceDurationDays.value = durationDays
        }
        
        val startDate = remoteConfig.getString(KEY_RACE_START_DATE)
        if (startDate.isNotEmpty()) {
            _raceStartDate.value = startDate
        }
        
        val displayName = remoteConfig.getString(KEY_RACE_DISPLAY_NAME)
        if (displayName.isNotEmpty()) _raceDisplayName.value = displayName
        
        val description = remoteConfig.getString(KEY_RACE_DESCRIPTION)
        if (description.isNotEmpty()) _raceDescription.value = description

        val endDate = remoteConfig.getString(KEY_RACE_END_DATE)
        if (endDate.isNotEmpty()) _raceEndDate.value = endDate

        val emoji = remoteConfig.getString(KEY_RACE_EMOJI)
        if (emoji.isNotEmpty()) _raceEmoji.value = emoji
        
        android.util.Log.d("RemoteConfig", "✅ Race config loaded: $displayName ($startDate, $durationDays days)")
    }

    // Helpers for AdManager compatibility
    fun shouldShowAds(): Boolean = _configState.value.showInterstitialAds
    
    fun getInterstitialAdFrequency(): Int = _configState.value.adFrequencySteps
    
    /**
     * 🆕 Adaptive Throttling: Sync için minimum adım eşiğini güncelle
     * Firebase Console'dan "step_sync_milestone" key'i ile değiştirilebilir.
     * Fatura kabarırsa eşiği 2000'e çekip anında müdahale edebilirsiniz.
     */
    private fun updateStepSyncMilestone() {
        val milestone = remoteConfig.getLong(KEY_STEP_SYNC_MILESTONE)
        if (milestone > 0) {
            _stepSyncMilestone.value = milestone
            android.util.Log.d("RemoteConfig", "✅ Step sync milestone loaded: $milestone adım")
        }
    }
    
    /**
     * 🆕 Helper: Maliyet optimizasyonu için sync eşiği
     * StepSyncManager bu değeri kullanarak throttling yapar.
     */
    fun getStepSyncMilestone(): Long = _stepSyncMilestone.value

    companion object {
        private const val KEY_SHOW_ADS = "show_interstitial_ads"
        private const val KEY_AD_FREQ = "ad_frequency"
        private const val KEY_MIN_VERSION = "min_version_code"
        private const val KEY_PIT_STOP_MESSAGES = "pit_stop_messages"
        private const val KEY_BACKGROUND_STEP_LIMIT = "background_step_limit"
        private const val KEY_RACE_DURATION_DAYS = "race_duration_days"
        private const val KEY_RACE_START_DATE = "race_start_date"
        private const val KEY_RACE_DISPLAY_NAME = "race_display_name"
        private const val KEY_RACE_DESCRIPTION = "race_description"
        private const val KEY_RACE_END_DATE = "race_end_date"
        private const val KEY_RACE_EMOJI = "race_emoji"
        private const val KEY_TIER_TRACKS = "tier_tracks"
        private const val KEY_PROMOTION_THRESHOLD = "promotion_threshold"
        private const val KEY_DEMOTION_THRESHOLD = "demotion_threshold"
        private const val KEY_LEAGUE_SIZE = "league_size"
        private const val KEY_STEP_SYNC_MILESTONE = "step_sync_milestone" // 🆕 Adaptive Throttling
        
        const val DEFAULT_BACKGROUND_STEP_LIMIT = 10_000L
        const val DEFAULT_RACE_DURATION_DAYS = 30  // Varsayılan: Aylık
        const val DEFAULT_RACE_START_DATE = "2026-01-01"  // Varsayılan başlangıç
        const val DEFAULT_PROMOTION_THRESHOLD = 5  // İlk 5 yükselir
        const val DEFAULT_DEMOTION_THRESHOLD = 5   // Son 5 düşer
        const val DEFAULT_LEAGUE_SIZE = 50        // Lig başına kullanıcı
        const val DEFAULT_STEP_SYNC_MILESTONE = 500L // 🆕 Adaptive Throttling (Default: 500 adım)
        
        // 🆕 Default tier -> track mapping (Eldeki 2 pist ile döngüsel)
        val DEFAULT_TIER_TRACKS = mapOf(
            "QUALIFYING" to "istanbul_park", // Havuz Pisti
            "BRONZE" to "spa",
            "SILVER" to "istanbul_park",
            "GOLD" to "spa",
            "PLATINUM" to "istanbul_park",
            "DIAMOND" to "spa",
            "LEGEND" to "spa"
        )
        
        // Pit stop messages: No hardcoded JSON, falls back to PitStopMessagesConfig.getDefaults()
        // See: assets/default_pit_stop_messages.json for reference configuration
        private const val DEFAULT_PIT_STOP_JSON = ""
    }
    
    // 🆕 Tier -> Track Mapping
    private val _tierTracks = MutableStateFlow(DEFAULT_TIER_TRACKS)
    val tierTracks: StateFlow<Map<String, String>> = _tierTracks.asStateFlow()
    
    /**
     * 🆕 Lig kademesine göre pist ID'sini döndür
     */
    fun getTrackForTier(tierName: String): String {
        return _tierTracks.value[tierName] ?: DEFAULT_TIER_TRACKS[tierName] ?: "baku"
    }
    
    /**
     * 🆕 Lig ayarlarını Remote Config'den güncelle
     */
    private fun updateLeagueSettings() {
        _promotionThreshold.value = remoteConfig.getLong(KEY_PROMOTION_THRESHOLD).toInt()
        _demotionThreshold.value = remoteConfig.getLong(KEY_DEMOTION_THRESHOLD).toInt()
        _leagueSize.value = remoteConfig.getLong(KEY_LEAGUE_SIZE).toInt()
        
        // 🆕 Tier→Track mapping'i de güncelle
        updateTierTracks()
        
        android.util.Log.d("RemoteConfig", "⚙️ League settings: Promote=${_promotionThreshold.value}, Demote=${_demotionThreshold.value}, Size=${_leagueSize.value}")
    }
    
    /**
     * 🆕 Tier→Track Mapping'i Remote Config'den parse et
     * 
     * Firebase Console'da şu formatta JSON ayarlayın:
     * {
     *   "QUALIFYING": "istanbul_park",
     *   "BRONZE": "spa",
     *   "SILVER": "monaco",
     *   "GOLD": "silverstone",
     *   "PLATINUM": "istanbul_park",
     *   "DIAMOND": "spa",
     *   "LEGEND": "monaco"
     * }
     */
    private fun updateTierTracks() {
        try {
            val jsonString = remoteConfig.getString(KEY_TIER_TRACKS)
            
            if (jsonString.isEmpty()) {
                android.util.Log.d("RemoteConfig", "ℹ️ tier_tracks not set, using defaults")
                return
            }
            
            // Parse JSON to Map<String, String>
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            val parsed: Map<String, String> = gson.fromJson(jsonString, type)
            
            if (parsed.isNotEmpty()) {
                // Merge with defaults (parsed overrides defaults)
                val merged = DEFAULT_TIER_TRACKS.toMutableMap()
                merged.putAll(parsed)
                
                _tierTracks.value = merged
                
                android.util.Log.d("RemoteConfig", "✅ Tier tracks loaded from Remote Config:")
                merged.forEach { (tier, track) ->
                    android.util.Log.d("RemoteConfig", "   $tier → $track")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RemoteConfig", "❌ Failed to parse tier_tracks: ${e.message}")
            // Keep defaults
        }
    }
}
