package com.pace.legends.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/**
 * ✨ Shimmer (İskelet Yükleme) Efekti
 * 
 * Herhangi bir Box veya Text üzerine eklendiğinde üzerinden parlak bir ışık geçirir.
 * Arka plan rengini gri yapar ve animasyonu başlatır.
 */
fun Modifier.shimmerEffect(
    isLoading: Boolean = true,
    widthOfShadowBrush: Int = 500,
    angleOfAxisY: Float = 270f,
    durationMillis: Int = 1000,
): Modifier = composed {
    if (!isLoading) return@composed this

    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = (durationMillis + widthOfShadowBrush).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "Shimmer loading animation",
    )

    this
        .onGloballyPositioned {
            size = it.size
        }
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF2C2C2C), // Koyu Gri (Zemin)
                    Color(0xFF444444), // Açık Gri (Işık)
                    Color(0xFF2C2C2C), // Koyu Gri (Zemin)
                ),
                start = Offset(x = translateAnimation - widthOfShadowBrush, y = 0.0f),
                end = Offset(x = translateAnimation, y = angleOfAxisY),
            )
        )
}
