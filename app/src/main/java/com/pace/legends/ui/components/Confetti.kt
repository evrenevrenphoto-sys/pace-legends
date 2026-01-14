package com.pace.legends.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.isActive
import java.lang.Math.random
import kotlin.math.cos
import kotlin.math.sin

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var angle: Float,
    var spin: Float,
    var size: Float,
    var alpha: Float = 1f
)

@Composable
fun ConfettiEffect(
    modifier: Modifier = Modifier.fillMaxSize(),
    durationMillis: Long = 3000
) {
    val particles = remember {
        val colors = listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFF4081), // Pink
            Color(0xFF00B0FF), // Blue
            Color(0xFF00E676), // Green
            Color(0xFFAA00FF)  // Purple
        )
        List(100) {
            Particle(
                x = 0.5f, // Start from center (normalized 0..1, will scale in draw)
                y = -0.1f, // Above screen
                vx = (random() * 10 - 5).toFloat(), // Random X spread
                vy = (random() * 10 + 5).toFloat(), // Downward velocity
                color = colors.random(),
                angle = (random() * 360).toFloat(),
                spin = (random() * 10 - 5).toFloat(),
                size = (random() * 20 + 10).toFloat()
            )
        }
    }

    // Animation state
    var time by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        val startTime = System.nanoTime()
        while (isActive && (System.nanoTime() - startTime) / 1_000_000 < durationMillis) {
            withFrameNanos { now ->
                time = (now - startTime) / 1_000_000_000f // seconds
                
                // Update physics
                particles.forEach { p ->
                    p.x += p.vx * 0.001f // Adjust scale for screen width later
                    p.y += p.vy * 0.01f  // Fall down
                    p.vy += 0.5f         // Gravity
                    p.angle += p.spin
                    
                    // Simple drag
                    p.vx *= 0.99f
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        particles.forEach { p ->
            // Map normalized coordinates to screen
            // Initial burst from center top
            if (p.y < 0) {
                 p.x = width / 2 + p.vx * 10 // Start centerish
            }
            
            val drawX = if(p.y < 0) width / 2 else (width / 2) + (p.x * width / 10) // Very rough physics mapping
            // Let's use simpler physics: x and y are absolute pixels, init them in layout
        }
    }
}

// Better Implementation:
@Composable
fun SimpleConfetti(
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val particles = remember { List(70) { ConfettiParticle() } }
    
    // Animation driver
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    // We use a simpler approach: Draw based on 'time'
    // But for a realistic burst, we need state persistence across frames.
    // 'Canvas' draw block runs every frame if we read a State.
    
    // Let's use a State list to hold current positions
    val particleStates = remember { mutableStateListOf<ConfettiParticle>() }
    
    LaunchedEffect(Unit) {
        // Init
        val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta)
        repeat(100) {
            particleStates.add(
                ConfettiParticle(
                    x = 500f, // Will be set to center in draw
                    y = -50f,
                    vx = (Math.random() * 20 - 10).toFloat(),
                    vy = (Math.random() * 10 + 10).toFloat(),
                    color = colors.random()
                )
            )
        }
        
        var lastTime = System.nanoTime()
        while(isActive) {
            withFrameNanos { now ->
                val dt = (now - lastTime) / 1_000_000_000f // seconds
                lastTime = now
                
                particleStates.forEach { p ->
                    p.x += p.vx * 60 * dt * 5 
                    p.y += p.vy * 60 * dt * 5
                    p.vy += 9.8f * dt * 20 // Gravity
                    p.rotation += p.rotSpeed
                }
                
                // Remove off-screen? No, simpler to just let them fall
            }
        }
    }
    
    Canvas(modifier = modifier) {
        // Reset X to center if it's the very first frame/init? 
        // No, we handle logic in LaunchedEffect. But we need screen width provided.
        // For MvP, we'll assume a standard burst from center.
        
        particleStates.forEach { p ->
             // Fix X start position relative to screen width on first draw if needed
             if (p.isFirstFrame) {
                 p.x = size.width / 2
                 p.isFirstFrame = false
             }
             
             withTransform({
                 rotate(p.rotation, pivot = Offset(p.x, p.y))
             }) {
                 drawRect(
                     color = p.color,
                     topLeft = Offset(p.x, p.y),
                     size = androidx.compose.ui.geometry.Size(20f, 20f)
                 )
             }
        }
    }
}

class ConfettiParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var color: Color = Color.Red,
    var rotation: Float = 0f,
    var rotSpeed: Float = (Math.random() * 10 - 5).toFloat(),
    var isFirstFrame: Boolean = true
)
