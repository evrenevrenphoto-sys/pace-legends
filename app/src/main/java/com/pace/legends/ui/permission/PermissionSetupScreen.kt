package com.pace.legends.ui.permission

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient

/**
 * 🆕 Permission Setup Screen
 * Kullanıcıyı karşılayan ve izin vermeye yönlendiren tam ekran UI
 */
@Composable
fun PermissionSetupScreen(
    onConnectClick: () -> Unit,
    onSkipClick: (() -> Unit)? = null, // 🆕 Skip butonu (HC yoksa veya emülatörde)
    isHealthConnectAvailable: Boolean = true // 🆕 HC durumu
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212) // Dark background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon (Material Icon)
            Icon(
                imageVector = Icons.Default.Favorite, // Heart icon
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFFFF5722) // Orange
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Pace Legends'a Hoş Geldin!",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isHealthConnectAvailable) {
                    "Gerçek hayat adımlarını yarışa dönüştürmek için adım verilerine erişmemiz gerekiyor."
                } else {
                    "Bu cihazda Health Connect desteklenmiyor. Demo modu ile devam edebilirsiniz."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            if (isHealthConnectAvailable) {
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5722)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "Başla ve Bağla",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 🆕 Skip / Demo Mode Button
            if (onSkipClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onSkipClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHealthConnectAvailable) Color.DarkGray else Color(0xFFFF5722)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (isHealthConnectAvailable) "Şimdilik Atla" else "Demo Modu ile Devam Et",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Fallback Button (only if HC available)
            if (isHealthConnectAvailable) {
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("PermissionSetup", "Settings launch failed: ${e.message}")
                            android.widget.Toast.makeText(context, "Ayarlar açılamadı", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                     Text(
                        text = "Health Connect Ayarlarını Aç",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
