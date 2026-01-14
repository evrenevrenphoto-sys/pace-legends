package com.pace.legends.ui.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 🏎️ Pit Stop Başarı Dialog'u
 *
 * Kullanıcı pit stop bildirimini tıklayarak uygulamaya döndüğünde gösterilir
 */
@Composable
fun PitStopSuccessDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = "✅ Pit Stop Başarılı",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Lastikler değişti, artık hızlanabiliriz! 🏎️💨",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Veriler başarıyla güncellendi ve senkronizasyon tekrar aktif.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5722)
                )
            ) {
                Text(
                    text = "Yarışa Dön",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
