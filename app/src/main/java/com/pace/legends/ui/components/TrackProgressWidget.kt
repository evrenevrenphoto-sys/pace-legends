package com.pace.legends.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pace.legends.domain.util.RaceProgressCalculator
import com.pace.legends.domain.util.RaceProgressCalculator.RaceProgress

/**
 * F1 temalı yarış ilerleme widget'ı
 * 
 * Dairesel progress bar ile tur ve sektör gösterimi
 */
@Composable
fun TrackProgressWidget(
    raceProgress: RaceProgress,
    trackName: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = raceProgress.progressPercentage,
        animationSpec = tween(durationMillis = 500),
        label = "progress"
    )
    
    val sectorColor = Color(RaceProgressCalculator.getSectorColor(raceProgress.currentSector))
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pist adı
            Text(
                text = trackName,
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Dairesel Progress + Tur Sayısı
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Arka plan çemberi
                CircularProgressBackground()
                
                // İlerleme çemberi (sektör renkli)
                CircularProgressBar(
                    progress = animatedProgress,
                    color = sectorColor,
                    modifier = Modifier.size(180.dp)
                )
                
                // Ortadaki tur bilgisi
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TUR",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${raceProgress.totalLaps}",
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Sektör göstergesi
                    SectorIndicator(
                        currentSector = raceProgress.currentSector,
                        sectorColor = sectorColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // İlerleme detayları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Mevcut tur mesafesi (metre cinsinden)
                ProgressStat(
                    label = "Bu Tur",
                    value = formatDistance(raceProgress.currentLapSteps),
                    unit = "/ ${formatDistance(raceProgress.trackLength)}"
                )
                
                // Yüzde
                ProgressStat(
                    label = "İlerleme",
                    value = "${(raceProgress.progressPercentage * 100).toInt()}%",
                    unit = ""
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Motivasyon mesajı
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = sectorColor.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = raceProgress.motivationMessage,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun CircularProgressBackground() {
    Canvas(modifier = Modifier.size(180.dp)) {
        drawArc(
            color = Color(0xFF2A2A40),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )
    }
}

@Composable
private fun CircularProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sweepAngle = progress * 360f
        
        // Glow efekti
        drawArc(
            color = color.copy(alpha = 0.3f),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )
        
        // Ana çizgi
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )
    }
}

@Composable
private fun SectorIndicator(
    currentSector: Int,
    sectorColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val sector = index + 1
            val isActive = sector == currentSector
            val isPassed = sector < currentSector
            
            val color = when {
                isActive -> sectorColor
                isPassed -> Color(RaceProgressCalculator.getSectorColor(sector))
                else -> Color(0xFF3A3A4A)
            }
            
            Box(
                modifier = Modifier
                    .width(if (isActive) 24.dp else 16.dp)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun ProgressStat(
    label: String,
    value: String,
    unit: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * Sayı formatlama (1000 -> 1K)
 */
private fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}

/**
 * Mesafe formatlama (metre -> km veya m)
 */
private fun formatDistance(meters: Long): String {
    return when {
        meters >= 1000 -> String.format("%.1f km", meters / 1000.0)
        else -> "$meters m"
    }
}
