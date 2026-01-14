package com.pace.legends.ui.track

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.pace.legends.utils.LanguageHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
// 🆕 GPS Anti-Cheat Imports
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pace.legends.ui.components.LocationPermissionWrapper
import com.pace.legends.ui.components.DebugSpeedOverlay
import com.pace.legends.utils.shimmerEffect
import androidx.compose.ui.draw.clip
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
    trackId: String,
    onNavigateToLeaderboard: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateBack: () -> Unit = {}, // Added callback
    showWarningBanner: Boolean = false, // 🆕 Banner Control
    onBannerClick: () -> Unit = {}, // 🆕 Banner Action
    viewModel: TrackDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Map UI State to local variables for compatibility
    val track = uiState.currentTrack
    val path = uiState.trackPath
    val userLocation = uiState.currentPosition
    val progress = uiState.userProgress
    val distance = uiState.distanceCovered
    val walkedPath = uiState.walkedPath
    
    // 🆕 Rakipler (Ghost Racers)
    val opponents = uiState.opponents
    
    // F1 Sektörleri
    val sectors = uiState.sectors
    val currentSectorIndex = uiState.currentSectorIndex
    
    // Best lap time (seconds)
    val bestLapTime = uiState.bestLapTime
    val downloadState = uiState.downloadState
    
    // Projected finish time based on current pace
    val projectedFinishTime = uiState.projectedFinishTime

    // Smooth Animations
    val animatedUserLocation by animateLatLngAsState(userLocation)
    
    // 🆕 HAPTIC FEEDBACK (Taptic Engine)
    val context = LocalContext.current
    val vibrator = remember { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Sektör Geçişi (Hafif Titreşim)
    LaunchedEffect(currentSectorIndex) {
        if (currentSectorIndex > 0) { // İlk yüklemede titremesin
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                 @Suppress("DEPRECATION")
                 vibrator.vibrate(50) // Fallback
            }
        }
    }
    
    // Tur Tamamlama (Güçlü Çift Titreşim)
    LaunchedEffect(progress.completedLoops) {
        if (progress.completedLoops > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                kotlinx.coroutines.delay(150)
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500) // Fallback
            }
        }
    }
    
    // 🆕 GPS Anti-Cheat: Lifecycle Owner
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationManager = viewModel.raceLocationManager
    
    // 🆕 GPS Lifecycle Management: Resume'da başlat, Pause'da durdur
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (locationManager.hasLocationPermission()) {
                        locationManager.startLocationUpdates()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> locationManager.stopLocationUpdates()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationManager.stopLocationUpdates()
        }
    }
    
    // 🆕 Permission Wrapper ile tüm içeriği sarmala
    LocationPermissionWrapper(
        onPermissionGranted = {
            locationManager.startLocationUpdates()
        }
    ) {
    // P0 FIX: Add Navigation Bar
    Scaffold(
        topBar = {
             CenterAlignedTopAppBar(
                title = { Text(text = "", color = Color.White) }, 
                navigationIcon = {
                     IconButton(onClick = onNavigateBack) {
                         Icon(
                             imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                             contentDescription = "Geri",
                             tint = Color.White
                         )
                     }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent 
                )
             )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFF121212))) {
            // 🆕 Warning Banner
            if (showWarningBanner) {
                com.pace.legends.ui.components.HealthConnectWarningBanner(
                    onClick = onBannerClick,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }

            // MAP SECTION
            Box(modifier = Modifier.weight(1f)) {
                if (path.isNotEmpty()) {
                    // ... existing Map code ...
                    // P1 FIX: Tek LaunchedEffect - timing sorunu çözüldü
                    // Key eklendi: Path veya TrackId değiştiğinde kamera resetlensin
                    val cameraPositionState = rememberCameraPositionState(key = trackId) {
                        position = CameraPosition.fromLatLngZoom(path.first(), 15f)
                    }
                
                // Kamera Kullanıcıyı Takip Et
                // P2 FIX: 'animate' yerine 'move' kullanıldı.
                // Çünkü 'animatedUserLocation' ZATEN animasyonlu geliyor (animateLatLngAsState).
                // Çift animasyon (animate + animate) titremeye ve gecikmeye sebep oluyordu.
                LaunchedEffect(animatedUserLocation) {
                   if (animatedUserLocation.latitude != 0.0 && animatedUserLocation.longitude != 0.0) {
                       cameraPositionState.move(
                           CameraUpdateFactory.newLatLng(animatedUserLocation)
                       )
                   }
                }
                
                // ===== FIT BOUNDS: Pist yüklendiğinde tüm pisti göster =====
                // FIX: Her ekran girişinde yeniden fit yapılsın
                var hasFittedBounds by remember { mutableStateOf(false) }
                
                // Ekrandan çıkıldığında flag'i resetle
                androidx.compose.runtime.DisposableEffect(trackId) {
                    hasFittedBounds = false
                    onDispose { 
                        hasFittedBounds = false 
                    }
                }
                
                // Dinamik padding hesapla (ekran boyutuna göre)
                val density = androidx.compose.ui.platform.LocalDensity.current
                // P1 FIX: Padding azaltıldı (100dp -> 16dp) - Pist daha yakından görünsün
                val dynamicPadding = with(density) { 16.dp.toPx() }.toInt()
                
                LaunchedEffect(path, trackId) {
                    if (path.isEmpty()) return@LaunchedEffect
                    
                    // Her zaman fit yap (flag kontrolü olmadan)
                    try {
                            val boundsBuilder = LatLngBounds.Builder()
                            path.forEach { boundsBuilder.include(it) }
                            val bounds = boundsBuilder.build()
                            
                            // Kısa gecikme - haritanın yüklenmesini bekle
                            kotlinx.coroutines.delay(300)
                            
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngBounds(bounds, dynamicPadding),
                                1000
                            )
                            hasFittedBounds = true
                        } catch (e: Exception) {
                            android.util.Log.w("TrackMap", "FitBounds failed: ${e.message}")
                        }
                }

                // OPTIMIZATION: Map UI Settings & Properties Memorization
                // Her recompose'da yeni obje oluşturulmasını engelleyerek performansı artırır.
                val context = LocalContext.current
                val mapUiSettings = remember {
                    MapUiSettings(
                        zoomControlsEnabled = false,
                        scrollGesturesEnabled = true,
                        zoomGesturesEnabled = true,
                        rotationGesturesEnabled = true,
                        tiltGesturesEnabled = true
                    )
                }
                
                val mapProperties = remember {
                    MapProperties(
                        mapStyleOptions = com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                            context,
                            com.pace.legends.R.raw.map_style_dark
                        )
                    )
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = mapUiSettings,
                    properties = mapProperties
                ) {
                    // 🆕 RAKİPLER İÇİN İKONLAR (PERFORMANS: Cache)
                    val goldCarIcon = remember { createEmojiMarker("🏎️", 70) }
                    val diamondCarIcon = remember { createEmojiMarker("🚀", 70) }
                    val bronzeCarIcon = remember { createEmojiMarker("🚗", 60) }

                    // F1 Sektör Renk Paleti
                    val sectorColors = listOf(
                        Color(0xFF1E88E5),  // Mavi - Sector 1
                        Color(0xFFE53935),  // Kırmızı - Sector 2
                        Color(0xFFFFB300)   // Sarı - Sector 3
                    )
                    
                    // 1. BASE TRACK LAYER (Her zaman çizilir - Pistin Şekli)
                    if (path.isNotEmpty()) {
                        Polyline(
                            points = path,
                            color = Color(0xFF555555), // Orta Gri (Koyu modda görünür, göz yormaz)
                            width = 24f,
                            zIndex = 0.5f
                        )
                    }

                    // 2. SECTOR LAYER (Varsa zeminin üzerine renkli biner)
                    // Not: Sektörler biraz daha ince olabilir veya aynı kalınlıkta olup zIndex ile üste çıkabilir
                    if (sectors.isNotEmpty() && path.isNotEmpty()) {
                        sectors.forEachIndexed { index, sector ->
                            // Sektör koordinatlarını al (index sınırları kontrol et)
                            val startIdx = sector.startIndex.coerceIn(0, path.size - 1)
                            
                            // GAP FIX
                            val endIdx = (sector.endIndex + 2).coerceIn(startIdx + 1, path.size)
                            
                            if (endIdx > startIdx) {
                                val sectorPath = path.subList(startIdx, endIdx)
                                val baseColor = sectorColors.getOrElse(index % sectorColors.size) { Color.Gray }
                                
                                // Aktif sektörü vurgula
                                val isActive = index == currentSectorIndex
                                val displayColor = if (isActive) baseColor else baseColor.copy(alpha = 0.0f) // PASİF SEKTÖRLERİ GİZLE (Sadece Base görünsün) veya Alpha ver
                                // Tasarım Tercihi: Sadece AKTİF sektör renkli yansın, diğerleri gri kalsın (Base Layer sayesinde)
                                
                                if (isActive) {
                                    Polyline(
                                        points = sectorPath,
                                        color = baseColor,
                                        width = 24f, // Base ile aynı
                                        zIndex = 0.6f
                                    )
                                }
                            }
                        }
                    }

                    // 🆕 RAKİPLER (GHOST RACERS) - OPTIMIZED
                    opponents.forEach { opponent ->
                        // 🔑 CRITICAL PERFORMANS FIX: key() kullanımı
                        key(opponent.id) {
                            val animatedGhostPos by animateLatLngAsState(
                                targetValue = opponent.currentPosition,
                                animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
                            )

                            val icon = when (opponent.leagueTier) {
                                "GOLD" -> goldCarIcon
                                "DIAMOND" -> diamondCarIcon
                                else -> bronzeCarIcon
                            }

                            Marker(
                                state = MarkerState(position = animatedGhostPos),
                                title = "${opponent.displayName} (${opponent.leagueTier})",
                                icon = icon,
                                zIndex = 0.8f, // Kullanıcının (1.0) ve WalkedPath'in (1.0) altında
                                alpha = 0.9f
                            )
                        }
                    }

                    // Walked Path (Green) - En üstte
                    if (walkedPath.isNotEmpty()) {
                        Polyline(
                            points = walkedPath,
                            color = Color(0xFF69F0AE), // Parlak yeşil
                            width = 24f,
                            zIndex = 1.0f // Diğer çizgilerin üstünde çizilsin
                        )
                    }
                    // User Marker (F1 Car Icon - Animated)
                    if (animatedUserLocation.latitude != 0.0) {
                        val f1CarIcon = remember {
                            createEmojiMarker("🏎️", 80)
                        }
                        Marker(
                            state = MarkerState(position = animatedUserLocation),
                            title = "Sen",
                            icon = f1CarIcon
                        )
                    } else if (path.isNotEmpty()) {
                        val startIcon = remember {
                            createEmojiMarker("🏁", 64)  // Damalı bayrak
                        }
                        Marker(
                            state = MarkerState(position = path.first()),
                            title = "Başlangıç",
                            icon = startIcon
                        )
                    }
                }
                
                // LIVE PACE CARD
                // LIVE PACE CARD
                // Veri henüz iniyorsa veya pist verisi tam değilse loading göster
                val isDataLoading = downloadState is com.pace.legends.domain.model.DownloadState.Downloading || path.isEmpty()

                LivePaceCard(
                    bestLapTimeSeconds = bestLapTime,
                    projectedFinishSeconds = projectedFinishTime,
                    isLoading = isDataLoading,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
                
                // 🆕 DEBUG OVERLAY: GPS Hız Göstergesi (Sadece Debug modda görünür)
                DebugSpeedOverlay(
                    locationManager = locationManager,
                    stepCadence = 0f, // TODO: StepRepository'den cadence akışı eklenecek
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 80.dp)
                )

            } else {
                // Loading / Download State
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (val state = downloadState) {
                        is com.pace.legends.domain.model.DownloadState.Downloading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFFFF5722))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Pist Verisi İndiriliyor... %${state.progress}",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    modifier = Modifier.width(200.dp),
                                    color = Color(0xFFFF5722),
                                    trackColor = Color(0xFF333333)
                                )
                            }
                        }
                        is com.pace.legends.domain.model.DownloadState.Error -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.message,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        else -> {
                            CircularProgressIndicator(color = Color(0xFFFF5722))
                        }
                    }
                }
            }
        }

        // DASHBOARD
        Dashboard(
            trackName = LanguageHelper.getLocalizedText(track.genericName),
            completedLoops = progress.completedLoops,
            distanceMeters = distance,
            totalTrackMeters = track.totalDistanceMeters,
            onLeaderboardClick = onNavigateToLeaderboard,
            onStatsClick = onNavigateToStats
        )
    }
  }
}
}

/**
 * Live Pace Card - Modern performans göstergesi
 */
/**
 * Live Pace Card - Modern performans göstergesi
 */
@Composable
fun LivePaceCard(
    bestLapTimeSeconds: Long,
    projectedFinishSeconds: Long,
    isLoading: Boolean, // 🆕 Yükleme durumu eklendi
    modifier: Modifier = Modifier
) {
    val isOnRecordPace = projectedFinishSeconds > 0 && 
                         (bestLapTimeSeconds == 0L || projectedFinishSeconds < bestLapTimeSeconds) &&
                         !isLoading // Loading iken rekor ışığı yanmasın
    
    // Loading sırasında nötr renk
    val targetColor = if (isLoading) Color(0xFF1E1E2E) else if (isOnRecordPace) Color(0xFF1B5E20) else Color(0xFF1E1E2E)

    val cardColor by animateColorAsState(
        targetValue = targetColor,
        label = "cardColor"
    )
    
    val textColor = if (isOnRecordPace) Color(0xFF69F0AE) else Color.White

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SOL TARAF: Best Lap
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Başlık (Static)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = if (isLoading) Color.Gray else Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EN İYİ", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                
                // Değer (Dynamic or Skeleton)
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(width = 60.dp, height = 20.dp)
                            .shimmerEffect() // ✨ Shimmer Uygulandı
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Text(
                        if (bestLapTimeSeconds > 0) formatTime(bestLapTimeSeconds) else "--:--",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )
            
            // SAĞ TARAF: Projected Time
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Başlık (Static)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = if (isLoading) Color.Gray else textColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isOnRecordPace) "REKOR TEMPO! 🔥" else "TAHMİNİ",
                        color = if (isLoading) Color.Gray else if (isOnRecordPace) Color(0xFF69F0AE) else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Değer (Dynamic or Skeleton)
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(width = 60.dp, height = 20.dp)
                            .shimmerEffect() // ✨ Shimmer Uygulandı
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Text(
                        if (projectedFinishSeconds > 0) formatTime(projectedFinishSeconds) else "--:--",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun Dashboard(
    trackName: String,
    completedLoops: Int,
    distanceMeters: Double,
    totalTrackMeters: Int,
    onLeaderboardClick: () -> Unit = {},
    onStatsClick: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = trackName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar
            val loopProgress by remember(distanceMeters, totalTrackMeters) {
                derivedStateOf {
                    if (totalTrackMeters > 0) {
                        ((distanceMeters % totalTrackMeters) / totalTrackMeters).toFloat()
                    } else 0f
                }
            }
            
            Text(
                "Tur İlerlemesi: ${(loopProgress * 100).toInt()}%",
                color = Color.Gray,
                fontSize = 12.sp
            )
            LinearProgressIndicator(
                progress = { loopProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color(0xFFFF5722),
                trackColor = Color(0xFF333333)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    title = "TUR",
                    value = completedLoops.toString(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                
                val remainingMeters = if (totalTrackMeters > 0) {
                     totalTrackMeters - (distanceMeters % totalTrackMeters)
                } else 0.0
                
                StatCard(
                    title = "KALAN",
                    value = "${(remainingMeters / 1000).format(2)} km",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onLeaderboardClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🏆 Lider Tablosu", color = Color.White, fontSize = 13.sp)
                }
                
                OutlinedButton(
                    onClick = onStatsClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📊 İstatistikler", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

@Composable
fun animateLatLngAsState(
    targetValue: LatLng,
    animationSpec: androidx.compose.animation.core.AnimationSpec<LatLng> = tween(durationMillis = 1000, easing = LinearEasing)
): State<LatLng> {
    val animatable = remember { Animatable<LatLng, AnimationVector2D>(targetValue, LatLngVectorConverter) }
    
    LaunchedEffect(targetValue) {
        animatable.animateTo(targetValue, animationSpec)
    }
    
    return animatable.asState()
}

val LatLngVectorConverter: TwoWayConverter<LatLng, AnimationVector2D>
    get() = TwoWayConverter(
        convertToVector = { AnimationVector2D(it.latitude.toFloat(), it.longitude.toFloat()) },
        convertFromVector = { LatLng(it.v1.toDouble(), it.v2.toDouble()) }
    )

/**
 * Emoji karakterini Google Maps marker için Bitmap'e çevirir.
 * @param emoji Gösterilecek emoji (örn: "🏎️")
 * @param sizePx Marker boyutu pixel cinsinden
 * @return BitmapDescriptor (Marker icon'u olarak kullanılabilir)
 */
fun createEmojiMarker(emoji: String, sizePx: Int): BitmapDescriptor {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val paint = Paint().apply {
        textSize = sizePx * 0.75f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    
    // Emoji'yi ortala
    val xPos = sizePx / 2f
    val yPos = (sizePx / 2f) - ((paint.descent() + paint.ascent()) / 2f)
    
    canvas.drawText(emoji, xPos, yPos, paint)
    
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

