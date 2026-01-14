package com.pace.legends

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.ads.MobileAds
import com.pace.legends.data.monetization.AdManager
import com.pace.legends.data.monetization.SubscriptionManager
import com.pace.legends.domain.manager.HealthConnectManager
import com.pace.legends.domain.repository.AuthRepository
import com.pace.legends.domain.repository.StepRepository
import com.pace.legends.domain.repository.UserRepository
import com.pace.legends.ui.MainViewModel
import com.pace.legends.ui.auth.LoginScreen
import com.pace.legends.ui.components.dialogs.PitStopSuccessDialog
import com.pace.legends.ui.leaderboard.LeaderboardScreen
import com.pace.legends.ui.onboarding.OnboardingScreen
import com.pace.legends.ui.permission.PermissionSetupScreen
import com.pace.legends.ui.profile.ProfileScreen
import com.pace.legends.ui.league.LeagueHomeScreen
import com.pace.legends.ui.stats.GlobalStatsScreen
import com.pace.legends.ui.track.TrackDetailScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var adManager: AdManager
    @Inject lateinit var subscriptionManager: SubscriptionManager
    @Inject lateinit var badgeManager: com.pace.legends.domain.manager.BadgeManager
    @Inject lateinit var remoteConfigManager: com.pace.legends.domain.manager.RemoteConfigManager
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var healthConnectManager: HealthConnectManager
    @Inject lateinit var leagueManager: com.pace.legends.domain.manager.LeagueManager
    @Inject lateinit var stepSyncManager: com.pace.legends.domain.manager.StepSyncManager // 🆕 Lig değişim eventi için
    
    // 🆕 Safety Net: Arka plana geçişte sync için
    @Inject lateinit var stepRepository: StepRepository
    
    // ViewModel state management
    private val viewModel: MainViewModel by viewModels()

    // Modern izin isteği API (registerForActivityResult)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkHealthConnectStatusOnly()
        } else {
            android.util.Log.w("MainActivity", "Standart izinler reddedildi")
        }
    }

    // İzin isteği devam ediyor mu? (Thread Safe)
    private val isPermissionRequestInProgress = AtomicBoolean(false)
    

    // Health Connect Permission Launcher
    private val healthConnectLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        isPermissionRequestInProgress.set(false) // İşlem bitti
        
        if (granted.containsAll(healthConnectManager.permissions)) {
            android.util.Log.d("MainActivity", "✅ Health Connect izinleri verildi")
            viewModel.updatePermissionStatus(true)
        } else {
            android.util.Log.w("MainActivity", "❌ Health Connect izinleri eksik veya reddedildi")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // P0 FIX: Splash Screen (Android 12+)
        installSplashScreen()  
        super.onCreate(savedInstanceState)
        
        // Initialize monetization SDKs & Remote Config ASYNC (Startup Optimization)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            // Parallel execution if needed, but sequential on BG thread is fine too
            launch {
                try {
                    MobileAds.initialize(this@MainActivity)
                    android.util.Log.d("MainActivity", "✅ MobileAds Initialized")
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "❌ MobileAds Init Failed: ${e.message}")
                }
            }
            
            launch {
                try {
                    // Remote Config Fetch
                    // Note: remoteConfigManager.fetchAndActivate() might be suspend or blocking. 
                    // If blocking, fine on IO. If async/callback, fine too.
                    remoteConfigManager.fetchAndActivate()
                    android.util.Log.d("MainActivity", "✅ Remote Config Fetched")
                    
                    val config = remoteConfigManager.configState.value
                    if (config.showInterstitialAds) {
                         // Ad triggers needs UI context or Activity for showing, 
                         // but loading can happen here if thread-safe.
                         // AdManager load usually requires Main thread for some SDKs, check logic.
                         // Google Mobile Ads loadRequest *can* be called on main thread, listeners handle bg.
                         // But initializing SDK is heavy.
                         kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                             adManager.loadInterstitial()
                         }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "❌ Remote Config Init Failed: ${e.message}")
                }
            }
            
            android.util.Log.d("MainActivity", "🚀 Startup tasks launched. Main thread free in ${System.currentTimeMillis() - startTime}ms")
            
            // 🆕 Phase 10: Schedule Daily Pruning
            val pruningRequest = androidx.work.PeriodicWorkRequest.Builder(
                com.pace.legends.worker.PruningWorker::class.java,
                1, java.util.concurrent.TimeUnit.DAYS
            )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiresDeviceIdle(true) // Sadece gece şarjda iken
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
            
            androidx.work.WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                "daily_pruning",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                pruningRequest
            )
        }
        
        setContent {
            com.pace.legends.ui.theme.PaceLegendsTheme { // P0 FIX: Theme Wrap
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                
                // Collect States
                val showPitStopSuccessDialog by viewModel.showPitStopSuccessDialog.collectAsState()
                val permissionsGranted by viewModel.permissionsGranted.collectAsState()
                val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
                val isInDemoMode by viewModel.isInDemoMode.collectAsState() // 🆕 Demo Mode State
                
                // 🆕 Periodic Sync (Lifecycle Aware via LaunchedEffect)
                LaunchedEffect(isUserLoggedIn, permissionsGranted) {
                    if (isUserLoggedIn && permissionsGranted) {
                        android.util.Log.d("MainActivity", "❤️ Periodic Sync Started (Compose)")
                        
                        // 1. App Start / Permission Gained: Force Sync & Restore Data
                        try {
                            android.util.Log.d("MainActivity", "🔄 Restoring user data (Firestore -> Local)...")
                            stepRepository.refreshUserData() // P0 FIX: Restore Track ID if missing
                            stepRepository.syncHealthConnectSteps(force = true)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Initial Force Sync error: ${e.message}")
                        }
                        
                        // 🆕 Phase 5: Rapid Polling is now handled in onResume/onPause via StepRepository
                    }
                }

                // 🆕 Badge Notification Listener
                LaunchedEffect(Unit) {
                    badgeManager.badgeEarnedEvents.collect { badgeType ->
                        val message = when (badgeType) {
                            com.pace.legends.domain.model.BadgeType.PERIOD_CHAMPION -> "🏆 TEBRİKLER! Dönem Şampiyonu oldunuz!"
                            com.pace.legends.domain.model.BadgeType.PODIUM_FINISH -> "🥈🥉 Harika! Podyuma çıktınız!"
                            com.pace.legends.domain.model.BadgeType.FORMATION_LAP -> "🏁 Formasyon Turu - İlk adımlarınız!"
                            com.pace.legends.domain.model.BadgeType.CHECKERED_FLAG -> "🏁 Damalı Bayrak - İlk turunuzu tamamladınız!"
                            com.pace.legends.domain.model.BadgeType.NIGHT_RACE -> "🌙 Gece Yarışı - Gece kuşu rozeti!"
                            com.pace.legends.domain.model.BadgeType.ENDURANCE_PILOT -> "🏃 Dayanıklılık Pilotu - Maraton tamamlandı!"
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }
                
                // 🆕 League Change Celebration Listener
                LaunchedEffect(Unit) {
                    stepSyncManager.leagueChangeEvents.collect { event ->
                        val message = if (event.isPromotion) {
                            "🎉 TEBRİKLER! ${event.previousTier} → ${event.newTier} ligine yükseldiniz!"
                        } else {
                            "📉 ${event.previousTier} → ${event.newTier} ligine düştünüz."
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }

                // 🆕 Phase 5: Ad Trigger (Lap Complete)
                LaunchedEffect(Unit) {
                    stepRepository.lapCompletedEvent.collect { lapCount ->
                        // 1. Show Ad
                        adManager.showInterstitial(this@MainActivity)
                        
                        // 2. Show Snackbar
                        snackbarHostState.showSnackbar(
                            message = "🏁 TUR $lapCount TAMAMLANDI! Harika gidiyorsun!",
                            actionLabel = "Süper"
                        )
                    }
                }
                
                // 🆕 Pit Stop Warning Listener (SafetyCarManager)
                LaunchedEffect(Unit) {
                    stepRepository.pitStopWarning.collect { message ->
                        snackbarHostState.showSnackbar(
                            message = "${message.title}\n${message.body}",
                            actionLabel = "Tamam"
                        )
                    }
                }
                
                // 🆕 Pit Stop Success Dialog
                if (showPitStopSuccessDialog) {
                    PitStopSuccessDialog(
                        onDismiss = {
                            viewModel.setPitStopDialogVisible(false)
                        }
                    )
                }
                
                // 🆕 Permission Setup Screen (Overlay)
                val showSetup = isUserLoggedIn && !permissionsGranted
                
                // P0 FIX: Reactive Navigation Logic
                // Auth durumu değişirse otomatik yönlendir
                LaunchedEffect(isUserLoggedIn) {
                    if (!isUserLoggedIn) {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    } else if (isUserLoggedIn && navController.currentDestination?.route == "login") {
                        navController.navigate("track_selection") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                }
                
                if (showSetup) {
                   PermissionSetupScreen(
                       onConnectClick = {
                           // Butona basınca izin iste
                           try {
                               if (healthConnectManager.healthConnectClient == null) {
                                   android.util.Log.e("MainActivity", "❌ Client is null, cannot request permissions")
                                   Toast.makeText(this@MainActivity, "Health Connect bu cihazda kullanılamıyor.", Toast.LENGTH_LONG).show()
                                   return@PermissionSetupScreen
                               }
                               
                               android.util.Log.d("MainActivity", "🔘 Connect Button Clicked. Permissions: ${healthConnectManager.permissions}")
                               
                               isPermissionRequestInProgress.set(true)
                               healthConnectLauncher.launch(healthConnectManager.permissions)
                           } catch (e: Exception) {
                               android.util.Log.e("MainActivity", "❌ Launcher error: ${e.message}")
                               Toast.makeText(this@MainActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                               isPermissionRequestInProgress.set(false)
                           }
                       },
                       onSkipClick = {
                           // 🆕 Skip / Demo Mode - İzinsiz devam et
                           android.util.Log.i("MainActivity", "⏭️ User skipped Health Connect permissions (Demo Mode)")
                           viewModel.updatePermissionStatus(granted = true, demoMode = true) // Demo mode aktif
                           Toast.makeText(this@MainActivity, "Demo modu aktif. Adım verisi için Health Connect'i bağlayın.", Toast.LENGTH_LONG).show()
                       },
                       isHealthConnectAvailable = healthConnectManager.isHealthConnectAvailable()
                   )
                } else {
                    // Normal App UI
                    Scaffold(
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                    ) { innerPadding ->
                        // Start Destination: Already handled by reactive effect, default to placeholder and let effect decide
                        // But standard NavHost needs a start. We use logic for initial composition.
                        val startDest = if (authRepository.getCurrentUserId() != null) "track_selection" else "login"
                    
                        NavHost(
                            navController = navController,
                            startDestination = startDest,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("login") {
                                LoginScreen(
                                    onLoginSuccess = { isSetupCompleted ->
                                        val destination = if (isSetupCompleted) "track_selection" else "onboarding"
                                        navController.navigate(destination) {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            
                            composable("onboarding") {
                                OnboardingScreen {
                                    val uid = authRepository.getCurrentUserId()
                                    if (uid != null) {
                                        this@MainActivity.lifecycleScope.launch {
                                            userRepository.updateSetupCompleted(uid, true)
                                        }
                                    }
                                    // Onboarding bitince izinleri kontrol et
                                    checkAndRequestPermissions()

                                    this@MainActivity.lifecycleScope.launch {
                                        // 🆕 P0 FIX: Offline Onboarding via WorkManager
                                        // Kullanıcıyı bekletme, arka planda hallet.
                                        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.pace.legends.worker.LeagueRegistrationWorker>()
                                            .setBackoffCriteria(
                                                androidx.work.BackoffPolicy.EXPONENTIAL,
                                                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                                                java.util.concurrent.TimeUnit.MILLISECONDS
                                            )
                                            .setConstraints(
                                                androidx.work.Constraints.Builder()
                                                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                                    .build()
                                            )
                                            .build()

                                        androidx.work.WorkManager.getInstance(this@MainActivity).enqueue(workRequest)
                                        android.util.Log.d("MainActivity", "🚀 League registration enqueued via WorkManager")
                                        
                                        navController.navigate("track_selection") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                }
                            }
                            
                            composable("track_selection") {
                                com.pace.legends.ui.league.LeagueHomeScreen(
                                    onNavigateToTrack = { trackId -> navController.navigate("track_detail/$trackId") },
                                    onNavigateToProfile = { navController.navigate("profile") },
                                    showWarningBanner = isInDemoMode,
                                    onBannerClick = { 
                                        // Reset permission state to force Setup Screen
                                        viewModel.updatePermissionStatus(granted = false, demoMode = false) 
                                    }
                                )
                            }
                            composable("track_detail/{trackId}") { backStackEntry ->
                                val trackId = backStackEntry.arguments?.getString("trackId") ?: "istanbul_park"
                                TrackDetailScreen(
                                    trackId = trackId,
                                    onNavigateToLeaderboard = { navController.navigate("leaderboard/$trackId") },
                                    onNavigateToStats = { navController.navigate("global_stats") },
                                    onNavigateBack = { navController.popBackStack() },
                                    showWarningBanner = isInDemoMode,
                                    onBannerClick = { 
                                        viewModel.updatePermissionStatus(granted = false, demoMode = false) 
                                    }
                                )
                            }
                            composable("leaderboard/{trackId}") { backStackEntry ->
                                val trackId = backStackEntry.arguments?.getString("trackId") ?: ""
                                LeaderboardScreen(
                                    trackId = trackId,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("global_stats") {
                                GlobalStatsScreen()
                            }
                            composable("profile") {
                                ProfileScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToLogin = { viewModel.signOut() },
                                    onNavigateToStore = { navController.navigate("store") }
                                )
                            }
                            
                            composable("store") {
                                com.pace.legends.ui.store.StoreScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * İzinleri kontrol et ve gerekiyorsa iste
     */
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Android 10+ için Activity Recognition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        
        // Android 13+ için Bildirim
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Standart izinler tam, Health Connect durumunu kontrol et
            checkHealthConnectStatusOnly()
        }
    }

    /**
     * Sadece durumu kontrol eder, izin istemez.
     */
    private fun checkHealthConnectStatusOnly() {
        if (!healthConnectManager.isHealthConnectAvailable()) return
        
        // ViewModel handles state update
        viewModel.checkHealthConnectPermissions()
        
        // Start sync if permitted (VM state might lag slightly so we double check here or observe)
        // Since VM check is async, we can rely on observation or just start if redundant
        // Better: Wait for VM state or just rely on onResume
        lifecycleScope.launch {
            if (healthConnectManager.hasAllPermissions()) {
                // Ensure VM state is updated
                viewModel.updatePermissionStatus(true)
            } else {
                viewModel.updatePermissionStatus(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sadece durum güncellemesi yapıyoruz ki kullanıcı ayarlardan dönmüşse UI güncellensin
        isPermissionRequestInProgress.set(false) // Safety reset
        checkHealthConnectStatusOnly()
        
        // 🆕 Phase 5: Live Map (Rapid Polling)
        // Uygulama ön plandayken sık güncelle
        stepRepository.startRapidPolling()
    }
    
    override fun onPause() {
        super.onPause()
        // 🆕 Phase 5: Battery Saving
        // Arka plana geçince durdur (WorkManager devralır)
        stepRepository.stopRapidPolling()
    }

    /**
     * 🆕 Periyodik Senkronizasyon (The Heartbeat)
     * Uygulama açıkken her 5 saniyede bir Health Connect'ten veri çeker.
     * Cost-Saving: Job ile yönetilir, arka planda durur.
     */
    // startPeriodicSync removed in favor of LaunchedEffect in Compose
    
    // P0 FIX: Data Loss Prevention using Application Scope
    override fun onStop() {
        super.onStop()
        
        android.util.Log.d("MainActivity", "🛑 App backgrounded")
        
        // Force sync - birikmiş adımları gönder
        // Use Application Scope to ensure sync completes even if Activity is destroyed
        (application as PaceLegendsApp).applicationScope.launch {
            try {
                stepRepository.forceSync()
                android.util.Log.d("MainActivity", "🛡️ App backgrounded - force sync completed safely")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "🛡️ Force sync failed: ${e.message}")
            }
        }
    }
}
