package com.pace.legends.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pace.legends.domain.model.AvatarFrame
import com.pace.legends.domain.model.CoinRewardType

@Composable
fun RewardDialog(
    rewardType: CoinRewardType,
    amount: Int,
    newFrame: AvatarFrame? = null,
    message: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // Full screen for confetti
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 1. Confetti Layer (Arka plan)
            SimpleConfetti(modifier = Modifier.fillMaxSize())
            
            // 2. Dialog Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E) // Dark gray
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Emoji (Bouncing?)
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = newFrame?.emoji ?: rewardType.emoji,
                                fontSize = 48.sp
                            )
                        }
                    }
                    
                    // Title
                    Text(
                        text = if (newFrame != null) "YENİ TEMA!" else "ÖDÜL KAZANDIN!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700), // Gold
                        letterSpacing = 1.sp
                    )
                    
                    // Main Content
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (newFrame != null) {
                            // Frame Reward Reference
                            Text(
                                text = newFrame.name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Avatar çerçevesi kilidi açıldı",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        } else {
                            // Coin Reward
                            Text(
                                text = "+$amount",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "COINS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                    
                    // Divider
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    
                    // Description
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // CTA Button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700), // Gold
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "HARİKA!",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
