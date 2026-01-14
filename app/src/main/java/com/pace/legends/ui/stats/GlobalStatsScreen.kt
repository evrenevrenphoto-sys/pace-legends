package com.pace.legends.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pace.legends.domain.repository.AllTimeStats
import androidx.hilt.navigation.compose.hiltViewModel
import com.pace.legends.domain.manager.StepSyncManager
import com.pace.legends.domain.model.PeriodHistory
import com.pace.legends.ui.leaderboard.RemainingTimeBadge

@Composable
fun GlobalStatsScreen(
    viewModel: AppGlobalStatsViewModel = hiltViewModel()
) {
    // ✅ Single UiState collection
    val uiState by viewModel.uiState.collectAsState()
    
    // Local aliases for backward compatibility
    val periodInfo = uiState.periodInfo
    val currentRaceStats = uiState.currentRace
    val todaySteps = uiState.todaySteps
    val weekSteps = uiState.weekSteps
    val monthSteps = uiState.monthSteps
    val racePeriodSteps = uiState.racePeriodSteps
    val allTimeStats = uiState.allTimeStats
    val pastPeriods = uiState.pastPeriods
    
    // 🆕 Snackbar for error handling
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error if present
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.errorShown()
        }
    }

    Scaffold(
        containerColor = Color(0xFF1A1A2E),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A))
                    )
                )
        ) {
            // Loading indicator
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFFF5722)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header
                    item {
                        Text(
                            "📊 İSTATİSTİKLER",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 1. Aktif Yarış
                    item {
                        Text(
                            "🏎️ AKTİF YARIŞ",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ActiveRaceCard(periodInfo, currentRaceStats)
                    }
                    
                    // 1.5 Yarış Dönemi
                    item {
                        Text(
                            "🏁 YARIŞ DÖNEMİ",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        RacePeriodCard(periodInfo, racePeriodSteps)
                    }

                    // 2. Dönem Bazlı
                    item {
                        Text(
                            "📅 DÖNEM BAZLI",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        PeriodBreakdownRow(todaySteps, weekSteps, monthSteps)
                    }
                    
                    // 3. Tüm Zamanlar
                    item {
                        Text(
                            "📈 TÜM ZAMANLAR",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        AllTimeStatsCard(allTimeStats)
                    }
                    
                    // 4. Geçmiş Yarışlar
                    if (pastPeriods.isNotEmpty()) {
                        item {
                            Text(
                                "🏅 GEÇMİŞ YARIŞLAR",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(pastPeriods) { period ->
                            PastPeriodCard(period)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==================== AKTİF YARIŞ KARTI ====================
@Composable
fun ActiveRaceCard(
    periodInfo: StepSyncManager.PeriodInfo,
    stats: GlobalStatsUiState.CurrentRaceStats
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header - kept minimal
            
            // Yarış Adı
            Text(
                "${periodInfo.emoji} ${periodInfo.displayName}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            
            // Kalan süre
            Spacer(modifier = Modifier.height(8.dp))
            RemainingTimeBadge(periodInfo)
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF333333))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("📊", formatNumber(stats.steps), "adım")
                StatItem("🔄", stats.laps.toString(), "tur")
                if (stats.rank != null) {
                    StatItem("🏅", "${stats.rank}.", "sıra")
                }
            }
        }
    }
}

@Composable
fun StatItem(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}



@Composable
fun RacePeriodCard(
    periodInfo: StepSyncManager.PeriodInfo,
    steps: Long
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Başlık ve Tarih
            Text(
                text = "${periodInfo.displayName} (${formatDateRange(periodInfo.startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000, periodInfo.endDate.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000)})",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF333333))
            Spacer(modifier = Modifier.height(12.dp))
            
            // Adım ve Süre
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Column {
                    Text("📊", fontSize = 20.sp)
                    Text(
                        formatNumber(steps),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("adım", color = Color.Gray, fontSize = 12.sp)
                 }
                 
                 Column(horizontalAlignment = Alignment.End) {
                    RemainingTimeBadge(periodInfo)
                 }
            }
        }
    }
}

// ==================== DÖNEM BAZLI ADIMLAR ====================
@Composable
fun PeriodBreakdownRow(
    todaySteps: Long,
    weekSteps: Long,
    monthSteps: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PeriodBox(
            modifier = Modifier.weight(1f),
            emoji = "📅",
            label = "BUGÜN",
            value = formatNumber(todaySteps),
            color = Color(0xFF4CAF50)
        )
        PeriodBox(
            modifier = Modifier.weight(1f),
            emoji = "📆",
            label = "HAFTA",
            value = formatNumber(weekSteps),
            color = Color(0xFF2196F3)
        )
        PeriodBox(
            modifier = Modifier.weight(1f),
            emoji = "🗓️",
            label = "AY",
            value = formatNumber(monthSteps),
            color = Color(0xFF9C27B0)
        )
    }
}

@Composable
fun PeriodBox(
    modifier: Modifier = Modifier,
    emoji: String,
    label: String,
    value: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 20.sp)
            Text(
                label,
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                "adım",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

// ==================== TÜM ZAMANLAR ====================
@Composable
fun AllTimeStatsCard(stats: AllTimeStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header moved outside
            
            AllTimeRow("🚶", formatNumber(stats.totalSteps), "toplam adım")
            AllTimeRow("🔄", stats.totalLaps.toString(), "toplam tur")
            AllTimeRow("🗺️", String.format("%.1f km", stats.totalDistance / 1000.0), "yürüdün")
        }
    }
}

@Composable
fun AllTimeRow(emoji: String, value: String, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}

// ==================== GEÇMİŞ YARIŞLAR ====================
@Composable
fun PastPeriodCard(period: PeriodHistory) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252535)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sol: Yarış bilgisi
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    period.displayName ?: "Dönem ${period.periodId}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    formatDateRange(period.startTimestamp, period.endTimestamp),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${formatNumber(period.totalSteps)} adım • ${period.completedLaps} tur",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            
            // Sağ: Sıralama rozeti
            if (period.finalRank != null) {
                val (medal, color) = when (period.finalRank) {
                    1 -> "🥇" to Color(0xFFFFD700)
                    2 -> "🥈" to Color(0xFFC0C0C0)
                    3 -> "🥉" to Color(0xFFCD7F32)
                    else -> "🏅" to Color.Gray
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(medal, fontSize = 28.sp)
                    Text(
                        "${period.finalRank}. sıra",
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

fun formatDateRange(start: Long, end: Long): String {
    val formatter = java.text.SimpleDateFormat("d MMM", java.util.Locale("tr"))
    return "${formatter.format(start)} - ${formatter.format(end)}"
}

fun formatNumber(num: Long): String {
    return java.text.NumberFormat.getIntegerInstance(java.util.Locale("tr")).format(num)
}
