package com.pace.legends.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.collectLatest

/**
 * Profil Ekranı
 * 
 * Kullanıcı bilgilerini gösterir ve Guest/User durumuna göre 
 * Giriş Yap veya Çıkış Yap seçenekleri sunar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToStore: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val userUiState by viewModel.userUiState.collectAsState()
    // Derived state for compatibility
    val (displayName, email, isAnonymousUser) = when(val state = userUiState) {
        is com.pace.legends.ui.profile.UserUiState.Success -> Triple(state.displayName, state.email, state.isAnonymous)
        else -> Triple("", null, true)
    }
    
    val isLoading by viewModel.isLoading.collectAsState()
    val championBadges by viewModel.championBadges.collectAsState()
    val allBadges by viewModel.allBadges.collectAsState()
    val antiCheatStats by viewModel.antiCheatStats.collectAsState()
    val coinBalance by viewModel.coinBalance.collectAsState() // 💰 Coin bakiyesi
    val currentReward by viewModel.currentReward.collectAsState() // 🎁 Reward Dialog State
    val unlockedFrames by viewModel.unlockedFrames.collectAsState() // 🖼️ Unlock Frames
    val activeFrame by viewModel.activeFrame.collectAsState() // 🖼️ Active Frame
    
    // UI State for Edit Name Dialog
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showFrameSelection by remember { mutableStateOf(false) } // 🖼️ Frame Selection Sheet
    
    var newName by remember { mutableStateOf("") }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 🆕 Effects: Load items on entry
    LaunchedEffect(Unit) {
        viewModel.refreshUser()
    }
    
    // 🆕 Edit Name Dialog
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("İsim Değiştir") },
            text = { 
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Yeni İsim") }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.updateDisplayName(newName)
                    showEditNameDialog = false
                }) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("İptal") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = com.pace.legends.ui.theme.PaceDarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 🔙 HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Coin Balance
                    Surface(
                        color = Color(0xFF1E1E2E),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 8.dp).clickable { onNavigateToStore() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("🪙 $coinBalance", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Logout / Login
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(
                            if(isAnonymousUser) Icons.Default.Person else Icons.Default.Refresh,
                            contentDescription = "Login/Logout",
                            tint = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 👤 PROFILE HEADER (Avatar + Info)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable { showFrameSelection = true } // 🆕 Çerçeve seçimi için tıklanabilir
                ) {
                    // Frame (Eğer varsa)
                    if (activeFrame.id != "default" && activeFrame.id != "none") {
                         // Frame Image loading logic would go here
                         // For now just allow clicking
                    }
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                // Details Section
                Column {
                    if (isAnonymousUser) {
                        Text(
                            "ROOKIE DRIVER",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Misafir Sürücü",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "DRIVER NAME",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                displayName.uppercase(),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { 
                                    newName = displayName
                                    showEditNameDialog = true 
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(start = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Düzenle",
                                    tint = com.pace.legends.ui.theme.PaceSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            "LICENSE NO",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            email ?: "N/A",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Footer Barcode style decoration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                com.pace.legends.ui.theme.PacePrimary,
                                com.pace.legends.ui.theme.PaceSecondary,
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            // 🏅 ALL BADGES SECTION
            if (allBadges.isNotEmpty()) {
                AllBadgesSection(allBadges)
            }
            
            // 🏆 CHAMPIONSHIPS SECTION
            if (championBadges.isNotEmpty()) {
                ChampionshipsSection(championBadges)
            }
            
            // 🆕 CLAIM PAST CHAMPIONSHIPS BUTTON
            if (!isAnonymousUser) {
                OutlinedButton(
                    onClick = { viewModel.claimPastChampionships() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFD700),
                        containerColor = Color.Transparent
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🏆 GEÇMİŞ ŞAMPİYONLUKLARI TALEP ET", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // ACTIONS
            if (isAnonymousUser) {
                // Login Teaser
                Text(
                    "Verilerinizi kaydetmek ve sıralamaya girmek için lisansınızı alın.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { onNavigateToLogin() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.pace.legends.ui.theme.PacePrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RESMİ SÜRÜCÜ OL (Giriş Yap)", fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                // 🛠️ DEBUG ARAÇLARI (Sadece Geliştirici Sürümünde Görünür)
                if (com.pace.legends.BuildConfig.DEBUG) {
                    // 1. Manuel Sync
                    OutlinedButton(
                        onClick = { viewModel.forceSyncNow() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = com.pace.legends.ui.theme.PaceSecondary,
                            containerColor = Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.pace.legends.ui.theme.PaceSecondary.copy(alpha = 0.5f)),
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = com.pace.legends.ui.theme.PaceSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DEBUG: MANUEL SYNC", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Test Period End
                    OutlinedButton(
                        onClick = { viewModel.testProcessEndOfPeriod() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = com.pace.legends.ui.theme.PaceSuccess,
                            containerColor = Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.pace.legends.ui.theme.PaceSuccess.copy(alpha = 0.5f)),
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = com.pace.legends.ui.theme.PaceSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DEBUG: LİG ATLAT (TEST)", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 3. Test Reward Dialog
                    OutlinedButton(
                        onClick = { viewModel.testRewardDialog() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFFD700),
                            containerColor = Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🎁 DEBUG: ÖDÜL TEST ET", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 🆕 DEBUG: TOGGLE PRO
                    OutlinedButton(
                        onClick = { viewModel.toggleProStatus() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFFD700),
                            containerColor = Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⭐️ DEBUG: PRO STATUS DEĞİŞTİR", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 🆕 DEBUG: COİN EKLE
                    OutlinedButton(
                        onClick = { viewModel.debugAddCoins() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF4CAF50),
                            containerColor = Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("💰 DEBUG: COİN EKLE (+100)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 3. Reset Data
                    OutlinedButton(
                        onClick = { viewModel.resetAppData() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFAAAAAA),
                            containerColor = Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFAAAAAA).copy(alpha = 0.3f)),
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFAAAAAA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DEBUG: SIFIRLA", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 🛡️ 4. ANTI-CHEAT STATS CARD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (antiCheatStats.manualSteps > 0) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🛡️ ANTI-CHEAT STATS",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Son: ${antiCheatStats.lastCheck}",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Stats Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // Verified Steps
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "✅ Doğrulanmış",
                                        color = Color(0xFF4ECDC4),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        "${antiCheatStats.verifiedSteps}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                
                                // Manual Steps
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "⚠️ Manuel",
                                        color = if (antiCheatStats.manualSteps > 0) Color(0xFFFF6B6B) else Color.Gray,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        "${antiCheatStats.manualSteps}",
                                        color = if (antiCheatStats.manualSteps > 0) Color(0xFFFF6B6B) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                
                                // Filtered %
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "🚫 Filtrelenen",
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        "%.1f%%".format(antiCheatStats.manualPercentage),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Check Button
                            Button(
                                onClick = { viewModel.loadAntiCheatStats() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2A2A4A)
                                ),
                                enabled = !antiCheatStats.isLoading && !isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (antiCheatStats.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("🔍 Health Connect Kontrol Et", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            
                // Logout
                OutlinedButton(
                    onClick = { viewModel.signOut() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = com.pace.legends.ui.theme.PaceError,
                        containerColor = Color.Transparent
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.pace.legends.ui.theme.PaceError.copy(alpha = 0.5f)),
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = com.pace.legends.ui.theme.PaceError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("SÖZLEŞMEYİ FESHET (Çıkış Yap)", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // STATUS INDICATOR (PIT BOARD STYLE)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E2E)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Light
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (isAnonymousUser) com.pace.legends.ui.theme.PaceWarning else com.pace.legends.ui.theme.PaceSuccess,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isAnonymousUser) "GUEST MODE (Veriler Yerel)" else "CONNECTED (Bulut Senkronizasyon)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    
    // 🖼️ AVATAR SELECTION SHEET
    if (showFrameSelection) {
        com.pace.legends.ui.components.AvatarSelectionSheet(
            frames = unlockedFrames,
            activeFrameId = activeFrame.id,
            onFrameSelected = { frameId ->
                viewModel.setActiveFrame(frameId)
                showFrameSelection = false
            },
            onDismiss = { showFrameSelection = false }
        )
    }
    
    // 🎁 REWARD DIALOG OVERLAY
    // 🎁 REWARD DIALOG OVERLAY
    currentReward?.let { reward ->
        com.pace.legends.ui.components.RewardDialog(
            rewardType = reward.type,
            amount = reward.amount,
            newFrame = reward.newFrame,
            message = reward.message,
            onDismiss = { viewModel.dismissReward() }
        )
    }
}

/**
 * 🏅 Tüm Rozetler Bölümü
 */
@Composable
fun AllBadgesSection(badges: List<ProfileViewModel.BadgeUI>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "🏅 ROZETLERİM",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Show badges in a grid-like layout (2 columns)
        badges.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { badge ->
                    BadgeCard(
                        badge = badge,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty space if odd number
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 🏅 Tek Rozet Kartı
 */
@Composable
fun BadgeCard(
    badge: ProfileViewModel.BadgeUI,
    modifier: Modifier = Modifier
) {
    val emoji = when (badge.type) {
        com.pace.legends.domain.model.BadgeType.FORMATION_LAP -> "🏁"
        com.pace.legends.domain.model.BadgeType.CHECKERED_FLAG -> "🏁"
        com.pace.legends.domain.model.BadgeType.NIGHT_RACE -> "🌙"
        com.pace.legends.domain.model.BadgeType.ENDURANCE_PILOT -> "🏃"
        com.pace.legends.domain.model.BadgeType.PERIOD_CHAMPION -> "🥇"
        com.pace.legends.domain.model.BadgeType.PODIUM_FINISH -> "🏆"
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (badge.isEarned) Color(0xFF1E1E2E) else Color(0xFF151520)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (badge.isEarned) emoji else "🔒",
                fontSize = 28.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = badge.type.title,
                color = if (badge.isEarned) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = badge.type.description,
                color = Color.Gray.copy(alpha = if (badge.isEarned) 0.8f else 0.5f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 11.sp
            )
        }
    }
}

/**
 * 🏆 Şampiyonluklar Bölümü
 */
@Composable
fun ChampionshipsSection(badges: List<ProfileViewModel.ChampionBadgeUI>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "🏆 ŞAMPİYONLUKLAR",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        badges.forEach { badge ->
            ChampionBadgeCard(badge)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 🏅 Şampiyonluk Rozet Kartı
 */
@Composable
fun ChampionBadgeCard(badge: ProfileViewModel.ChampionBadgeUI) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Medal Emoji
            Text(
                text = when (badge.rank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    else -> "🥉"
                },
                fontSize = 32.sp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Track & Period Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = badge.trackDisplayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = badge.periodDisplayName,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            // Rank Label
            Text(
                text = if (badge.rank == 1) "ŞAMPİYON" else "${badge.rank}. SIRA",
                color = if (badge.rank == 1) Color(0xFFFFD700) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
