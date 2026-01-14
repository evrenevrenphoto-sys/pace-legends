package com.pace.legends.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pace.legends.domain.repository.LeaderboardEntry
import com.pace.legends.domain.model.PeriodInfo
import com.pace.legends.ui.theme.*

/**
 * Aylık Maraton Leaderboard Ekranı
 * 
 * F1 teması:
 * - Top 3 için podyum kartları
 * - Grid list 4-50
 * - Sticky user rank bar
 * - Pull-to-refresh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    trackId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val leaderboard by viewModel.leaderboard.collectAsState()
    val userRank by viewModel.userRank.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val periodInfo by viewModel.periodInfo.collectAsState()

    LaunchedEffect(trackId) {
        viewModel.loadLeaderboard(trackId)
    }
    
    val top3 = leaderboard.take(3)
    val theGrid = leaderboard.drop(3)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    /* Dinamik Header kullanıyoruz, title boş */
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = PaceTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = PaceDarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(PaceDarkBackground, PaceDarkBackgroundDeep)
                    )
                )
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(bottom = if (userRank != null) 72.dp else 0.dp)
                ) {
                    // 🆕 DİNAMİK HEADER
                    if (periodInfo.periodId.isNotEmpty()) {
                        RaceHeader(
                            periodInfo = periodInfo,
                            trackName = trackId // İdealde display name resolving yapılır
                        )
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PacePrimary)
                        }
                    } else if (leaderboard.isEmpty()) {
                        // Empty State
                        EmptyLeaderboardState()
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 🏆 PODYUM (Top 3)
                            if (top3.isNotEmpty()) {
                                item {
                                    PodiumSection(top3)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    if (theGrid.isNotEmpty()) {
                                        Text(
                                            "THE GRID",
                                            color = PaceTextMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }
                            }
                            
                            // 📊 THE GRID (4-50)
                            items(theGrid) { entry ->
                                GridItem(entry)
                            }
                        }
                    }
                }
            }
            
            // 📌 STICKY USER RANK BAR
            userRank?.let { user ->
                UserRankBar(
                    entry = user,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

/**
 * Boş durum - Motivasyon mesajı
 */
@Composable
fun EmptyLeaderboardState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏎️", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Motorları Isıt!",
                color = PaceTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "İlk turu sen at ve lider ol!",
                color = PaceTextMuted,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Podyum bölümü (Top 3)
 */
@Composable
fun PodiumSection(top3: List<LeaderboardEntry>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        // 2. Sıra (Sol)
        if (top3.size > 1) {
            PodiumCard(
                entry = top3[1],
                height = 140.dp,
                color = PaceSilver,
                emoji = "🥈"
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // 1. Sıra (Orta - En yüksek)
        if (top3.isNotEmpty()) {
            PodiumCard(
                entry = top3[0],
                height = 180.dp,
                color = PaceGold,
                emoji = "🥇"
            )
        }
        
        // 3. Sıra (Sağ)
        if (top3.size > 2) {
            PodiumCard(
                entry = top3[2],
                height = 120.dp,
                color = PaceBronze,
                emoji = "🥉"
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun PodiumCard(
    entry: LeaderboardEntry,
    height: androidx.compose.ui.unit.Dp,
    color: Color,
    emoji: String
) {
    val containerColor = if (entry.isCurrentUser) PaceUserHighlight else PacePodiumBackground
    val borderStroke = if (entry.isCurrentUser) androidx.compose.foundation.BorderStroke(2.dp, color) else null

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier
            .width(110.dp)
            .height(height)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(emoji, fontSize = 32.sp)
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    entry.displayName.take(12),
                    color = PaceTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,

                    maxLines = 1
                )
                // 🆕 Pro Badge (Podium)
                if (entry.isPro) {
                    Icon(
                        imageVector = Icons.Default.Star, // Verified yerine Star (garanti olması için)
                        contentDescription = "Pro Member",
                        tint = PaceGold,
                        modifier = Modifier
                            .size(12.dp)
                            .padding(top = 2.dp)
                    )
                }
                Text(
                    formatSteps(entry.steps),
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "adım",
                    color = PaceTextMuted,
                    fontSize = 10.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(color)
            )
        }
    }
}

/**
 * Grid item (4-50 sırası)
 */
@Composable
fun GridItem(entry: LeaderboardEntry) {
    val containerColor = if (entry.isCurrentUser) PaceUserHighlight else PaceCardBackgroundAlt
    val borderStroke = if (entry.isCurrentUser) androidx.compose.foundation.BorderStroke(1.dp, PacePrimary) else null

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sıra
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (entry.isCurrentUser) PacePrimary else PacePrimary.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#${entry.rank}",
                    color = if (entry.isCurrentUser) PaceTextPrimary else PacePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // İsim
            Text(
                if (entry.isCurrentUser) "${entry.displayName} (Sen)" else entry.displayName,
                color = if (entry.isCurrentUser) PacePrimary else PaceTextPrimary,
                modifier = Modifier.weight(1f),
                fontWeight = if (entry.isCurrentUser) FontWeight.Bold else FontWeight.Medium
            )
            
            // 🆕 Pro Badge (Grid)
            if (entry.isPro) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Pro Member",
                    tint = PaceGold,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Adım
            Text(
                formatSteps(entry.steps),
                color = PaceTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Alt çubuk - Kullanıcının kendi sırası
 */
@Composable
fun UserRankBar(
    entry: LeaderboardEntry,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PaceUserHighlight),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = PacePrimary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Senin Sıran",
                    color = PaceTextMuted,
                    fontSize = 12.sp
                )
                Text(
                    if (entry.rank > 0) "#${entry.rank}" else "50+",
                    color = PaceTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatSteps(entry.steps),
                    color = PacePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "adım",
                    color = PaceTextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun RaceHeader(
    periodInfo: PeriodInfo,
    trackName: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = PaceCardBackgroundAlt
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Yarış Adı + Emoji
            Text(
                text = "${periodInfo.emoji} ${periodInfo.displayName}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PaceTextPrimary,
                textAlign = TextAlign.Center
            )
            
            // Açıklama (varsa)
            if (periodInfo.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = periodInfo.description,
                    fontSize = 14.sp,
                    color = PaceTextMuted,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Kalan Süre Badge
            RemainingTimeBadge(periodInfo)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Track Adı
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📍", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = trackName,
                    fontSize = 14.sp,
                    color = PaceTextMuted
                )
            }
        }
    }
}

@Composable
fun RemainingTimeBadge(periodInfo: PeriodInfo) {
    val backgroundColor = when {
        periodInfo.isLastDay -> PaceDanger           // Kırmızı - Son gün!
        periodInfo.daysRemaining <= 3 -> PaceWarningOrange  // Turuncu - Az kaldı
        else -> PaceSuccess                           // Yeşil - Rahat
    }
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⏰", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatRemainingTime(periodInfo),
                color = PaceTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

private fun formatRemainingTime(info: PeriodInfo): String {
    return if (info.isExpired) "Süre doldu!" else info.remainingTimeDisplay
}

/**
 * Adım formatla (1000 = 1K, 1000000 = 1M)
 */
fun formatSteps(steps: Long): String {
    return when {
        steps >= 1_000_000 -> String.format("%.1fM", steps / 1_000_000.0)
        steps >= 1_000 -> String.format("%.1fK", steps / 1_000.0)
        else -> steps.toString()
    }
}

