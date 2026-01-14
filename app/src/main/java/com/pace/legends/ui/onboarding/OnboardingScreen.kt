package com.pace.legends.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onContinueClick: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E))
                )
            )
    ) {
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingPage(page = page)
        }
        
        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                repeat(3) { iteration ->
                    val color = if (pagerState.currentPage == iteration) 
                        Color(0xFFFF5722) else Color.Gray.copy(alpha = 0.5f)
                    val width = if (pagerState.currentPage == iteration) 32.dp else 12.dp
                    
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip Button (Hide on last page)
                if (pagerState.currentPage < 2) {
                    TextButton(onClick = onContinueClick) {
                        Text("Atla", color = Color.Gray)
                    }
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }

                // Next / Start Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onContinueClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    if (pagerState.currentPage < 2) {
                        Text("İlerle")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    } else {
                        Text("Yarışa Başla! 🏁")
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPage(page: Int) {
    val content = when (page) {
        0 -> Triple(
            "🏎️", 
            "Adımlarını Yarışa Dönüştür", 
            "Günlük yürüyüşlerinle sanal F1 pistlerinde tur at. Gerçek pist mesafelerini tamamla!"
        )
        1 -> Triple(
            "🌍", 
            "Efsanevi Pistleri Keşfet", 
            "İstanbul Park, Monaco, Monza... Dünyanın en ünlü pistlerinde yarış deneyimini yaşa."
        )
        else -> Triple(
            "🏆", 
            "Liderlik Mücadelesi", 
            "Arkadaşlarınla yarış, en iyi tur sürelerini yap ve liderlik tablosunda zirveye oyna!"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = content.first,
            fontSize = 100.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Text(
            text = content.second,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = content.third,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        
        Spacer(modifier = Modifier.height(100.dp)) // Bottom padding compensation
    }
}
