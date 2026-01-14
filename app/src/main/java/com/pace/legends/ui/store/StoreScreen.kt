package com.pace.legends.ui.store

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pace.legends.domain.model.AvatarFrame
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    onNavigateBack: () -> Unit,
    viewModel: StoreViewModel = hiltViewModel()
) {
    val coinBalance by viewModel.coinBalance.collectAsState()
    val storeFrames by viewModel.storeFrames.collectAsState()
    val ownedFrameIds by viewModel.ownedFrameIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0F0F1A), // PaceDarkBackground
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "MAĞAZA", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 💰 Bakiye Kartı
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2A2A40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💰", fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "MEVCUT BAKİYE",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "%,d".format(coinBalance),
                            color = Color(0xFFFFD700),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "ÖZEL ÇERÇEVELER",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 🛒 Ürün Listesi
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(storeFrames) { frame ->
                    StoreItemCard(
                        frame = frame,
                        isOwned = ownedFrameIds.contains(frame.id),
                        userBalance = coinBalance,
                        onBuy = { viewModel.buyFrame(frame) },
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

@Composable
fun StoreItemCard(
    frame: AvatarFrame,
    isOwned: Boolean,
    userBalance: Long,
    onBuy: () -> Unit,
    isLoading: Boolean
) {
    val canAfford = userBalance >= (frame.price ?: Int.MAX_VALUE)
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isOwned) Color.Gray.copy(alpha=0.3f) else Color(0xFF2A2A40)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji Display
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF0F0F1A), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(frame.emoji, fontSize = 48.sp)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                frame.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action Button
            if (isOwned) {
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = Color.Gray.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ALINDI", color = Color.Gray)
                }
            } else {
                Button(
                    onClick = onBuy,
                    enabled = canAfford && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canAfford) Color(0xFFFFD700) else Color.Red.copy(alpha = 0.5f),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (canAfford) {
                        Text("💰 ${frame.price}", fontWeight = FontWeight.Bold)
                    } else {
                        Text("💰 ${frame.price}", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha=0.5f))
                    }
                }
            }
        }
    }
}
