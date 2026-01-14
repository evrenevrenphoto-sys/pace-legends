package com.pace.legends.ui.league

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert // 🆕 Added import
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pace.legends.domain.model.LeagueTier
import com.pace.legends.domain.repository.LeaderboardEntry
import com.pace.legends.utils.LanguageHelper

/**
 * Lig = Pist Ana Ekranı
 * 
 * Kullanıcı pist seçmez, liği onun pistini belirler.
 */
@Composable
fun LeagueHomeScreen(
    onNavigateToTrack: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    showWarningBanner: Boolean = false, // 🆕 Banner Control
    onBannerClick: () -> Unit = {}, // 🆕 Banner Action
    viewModel: LeagueHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Remote config flow'ları (ViewModel'de ayrı tutulmuştu)
    val promotionThreshold by viewModel.promotionThreshold.collectAsState()
    val demotionThreshold by viewModel.demotionThreshold.collectAsState()
    val leagueSizeConfig by viewModel.leagueSize.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Hata mesajı gösterme
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.userMessageShown()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A))
                    )
                )
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 🆕 Warning Banner
            if (showWarningBanner) {
                com.pace.legends.ui.components.HealthConnectWarningBanner(
                    onClick = onBannerClick,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${uiState.leagueInfo.tier.emoji} ${uiState.leagueInfo.tier.displayName}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.userRank > 0) {
                        Text(
                            text = "Sıralama: #${uiState.userRank}",
                            color = Color(0xFFFF5722),
                            fontSize = 14.sp
                        )
                    }
                }
                
                IconButton(
                    onClick = onNavigateToProfile,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(0xFF2A2A40),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profil",
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF5722))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Atanmış Pist Kartı
                    item {
                        AssignedTrackCard(
                            trackName = uiState.assignedTrack?.let { 
                                LanguageHelper.getLocalizedText(it.genericName)  
                            } ?: "Yükleniyor...",
                            trackDistance = uiState.assignedTrack?.totalDistanceMeters?.let { 
                                "${it / 1000.0} km / tur" 
                            } ?: "",
                            tier = uiState.leagueInfo.tier,
                            onClick = {
                                uiState.assignedTrack?.id?.let { onNavigateToTrack(it) }
                            }
                        )
                    }
                    
                        // Yükselme/Düşme Durumu
                    item {
                        // Config values already collected at top
                        
                        PromotionStatusCard(
                            rank = uiState.userRank,
                            tier = uiState.leagueInfo.tier,
                            promotionThreshold = promotionThreshold,
                            demotionThreshold = demotionThreshold,
                            totalInLeague = uiState.leaderboard.size.coerceAtLeast(leagueSizeConfig)
                        )
                    }
                    
                    // Top 10 Sıralaması
                    item {
                        Text(
                            "🏆 Lig Sıralaması",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(uiState.leaderboard) { entry ->
                        LeaderboardRow(entry = entry, userRank = uiState.userRank)
                    }
                    
                    // 🆕 Pagination Loading / Trigger
                    if (!uiState.endReached && uiState.leaderboard.isNotEmpty()) {
                        item {
                            LaunchedEffect(Unit) {
                                viewModel.loadMoreLeaderboard()
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFFF5722))
                            }
                        }
                    }
                    
                    // Lig Haritası
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        LeagueMapCard(
                            currentTier = uiState.leagueInfo.tier,
                            tierTracks = uiState.tierTracks
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssignedTrackCard(
    trackName: String,
    trackDistance: String,
    tier: LeagueTier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A40)),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Senin Pistin",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "🏎️ $trackName",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                text = trackDistance,
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Piste Git 📍")
            }
        }
    }
}

@Composable
fun PromotionStatusCard(
    rank: Int,
    tier: LeagueTier,
    promotionThreshold: Int,
    demotionThreshold: Int,
    totalInLeague: Int
) {
    val safePromotionThreshold = if (promotionThreshold > 0) promotionThreshold else 10
    val isInPromotionZone = rank in 1..safePromotionThreshold
    val isInDemotionZone = rank > (totalInLeague - demotionThreshold)
    
    val (backgroundColor, icon, statusText, statusColor) = when {
        // 1. Sıralama henüz hesaplanmadıysa (veya veri gecikmesi)
        rank <= 0 -> {
            Quadruple(
                Color(0xFF2A2A40),
                Icons.Default.MoreVert,
                "⏳ Sıralama Hesaplanıyor...",
                Color.Gray
            )
        }
        // 2. Yükselme Bölgesi
        isInPromotionZone && tier.canPromote() -> {
            val nextTier = tier.nextTier()
            Quadruple(
                Color(0xFF1B5E20).copy(alpha = 0.3f),
                Icons.Default.KeyboardArrowUp,
                "⬆️ Yükselme Bölgesinde! (${nextTier?.displayName ?: "Üst Lig"})",
                Color(0xFF4CAF50)
            )
        }
        // 3. Düşme Bölgesi
        isInDemotionZone && tier.canDemote() -> {
            val prevTier = tier.previousTier()
            Quadruple(
                Color(0xFFB71C1C).copy(alpha = 0.3f),
                Icons.Default.KeyboardArrowDown,
                "⬇️ Düşme Tehlikesi! (${prevTier?.displayName ?: "Alt Lig"})",
                Color(0xFFE53935)
            )
        }
        // 4. Diğer Durumlar (Güvende)
        else -> {
            // Eleme Havuzu için özel mesaj
            if (tier == LeagueTier.QUALIFYING) {
                Quadruple(
                    Color(0xFF2A2A40),
                    Icons.Default.Star,
                    "🏁 Yükselmek için İlk ${safePromotionThreshold}'a gir!",
                    Color(0xFFFF9800)
                )
            } else {
                Quadruple(
                    Color(0xFF2A2A40),
                    Icons.Default.Star,
                    "📍 Güvende - Liginde Kalıyorsun",
                    Color.Gray
                )
            }
        }
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun LeaderboardRow(entry: LeaderboardEntry, userRank: Int) {
    val isCurrentUser = entry.rank == userRank
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) Color(0xFF2E1E2E) else Color(0xFF1E1E2E)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${entry.rank}",
                    color = when (entry.rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> Color.Gray
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.width(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.displayName,
                    color = if (isCurrentUser) Color(0xFFFF5722) else Color.White,
                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = "${entry.steps} adım",
                color = Color(0xFFFF5722),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LeagueMapCard(
    currentTier: LeagueTier,
    tierTracks: Map<LeagueTier, com.pace.legends.domain.model.Track?>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "🗺️ Lig Haritası",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            LeagueTier.entries.reversed().forEach { tier ->
                val track = tierTracks[tier]
                val isCurrentTier = tier == currentTier
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tier.emoji,
                        fontSize = if (isCurrentTier) 20.sp else 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tier.displayName,
                            color = if (isCurrentTier) Color(0xFFFF5722) else Color.White,
                            fontWeight = if (isCurrentTier) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isCurrentTier) 14.sp else 12.sp
                        )
                        track?.let {
                            Text(
                                text = LanguageHelper.getLocalizedText(it.genericName),
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                    if (isCurrentTier) {
                        Text(
                            "📍 SEN",
                            color = Color(0xFFFF5722),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Helper data class
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
