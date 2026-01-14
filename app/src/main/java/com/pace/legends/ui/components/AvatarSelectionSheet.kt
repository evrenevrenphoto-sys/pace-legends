package com.pace.legends.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pace.legends.domain.model.AvatarFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSelectionSheet(
    frames: List<AvatarFrame>,
    activeFrameId: String,
    onFrameSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp) // Bottom padding for safety
        ) {
            Text(
                text = "Profil Teması Seç",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(frames) { frame ->
                    FrameItem(
                        frame = frame,
                        isActive = frame.id == activeFrameId,
                        onClick = {
                            if (frame.isUnlocked) {
                                onFrameSelected(frame.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FrameItem(
    frame: AvatarFrame,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) Color(0xFFFFD700).copy(alpha = 0.2f) else Color(0xFF2A2A3A)
    val borderColor = if (isActive) Color(0xFFFFD700) else Color.Transparent
    val alpha = if (frame.isUnlocked) 1f else 0.5f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(enabled = frame.isUnlocked, onClick = onClick)
            .padding(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Emoji Display
            Text(
                text = frame.emoji,
                fontSize = 32.sp,
                modifier = Modifier.padding(8.dp)
            )
            
            // Lock Overlay
            if (!frame.isUnlocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White
                    )
                }
            }
            
            // Active Check
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xFFFFD700), CircleShape)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = frame.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) Color(0xFFFFD700) else Color.White.copy(alpha = alpha),
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
