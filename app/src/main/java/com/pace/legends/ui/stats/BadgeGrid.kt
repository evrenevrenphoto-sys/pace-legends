package com.pace.legends.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pace.legends.R
import com.pace.legends.domain.model.BadgeType
import com.pace.legends.domain.model.UserBadge

@Composable
fun BadgeGrid(earnedBadges: List<UserBadge>) {
    val allBadges = BadgeType.values()
    val columns = 2
    val chunkedBadges = allBadges.toList().chunked(columns)
    
    // Grid items (Non-scrollable, handled by parent LazyColumn)
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        chunkedBadges.forEach { rowBadges ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowBadges.forEach { badgeType ->
                    val isEarned = earnedBadges.any { it.badgeId == badgeType.id }
                    Box(modifier = Modifier.weight(1f)) {
                        BadgeItem(badgeType = badgeType, isEarned = isEarned)
                    }
                }
                
                // Eğer satır eksikse (tek eleman varsa) boşluk doldur
                if (rowBadges.size < columns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun BadgeItem(badgeType: BadgeType, isEarned: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF1E1E2E), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        val containerColor = if (isEarned) Color(0x33FFD700) else Color(0x1FFFFFFF)
        
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            // İkonlar henüz yoksa varsayılan bir ikon kullanabiliriz veya eklemeliyiz
            // Şimdilik Text emoji kullanalım ikon kaynağı sorun olmasın diye
             Text(
                 text = when(badgeType) {
                     BadgeType.FORMATION_LAP -> "🏎️"
                     BadgeType.CHECKERED_FLAG -> "🏁"
                     BadgeType.NIGHT_RACE -> "🌃"
                     BadgeType.ENDURANCE_PILOT -> "🔋"
                     BadgeType.PERIOD_CHAMPION -> "🏆"
                     BadgeType.PODIUM_FINISH -> "🏅"
                 },
                 fontSize = 32.sp,
                 color = if(isEarned) Color.Unspecified else Color.Gray
             )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = badgeType.title,
            color = if (isEarned) Color.White else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = badgeType.description,
            color = Color.Gray,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}
