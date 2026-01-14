package com.pace.legends.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 🛡️ Konum İzni Yöneticisi
 * 
 * İçeriği sarmalar. İzin varsa içeriği gösterir.
 * İzin yoksa "Neden Gerekli?" ekranını veya sistem izin diyaloğunu gösterir.
 * 
 * Kullanım:
 * ```
 * LocationPermissionWrapper(
 *     onPermissionGranted = { locationManager.startLocationUpdates() }
 * ) {
 *     // İzin gerektiren içerik
 *     TrackMap(...)
 * }
 * ```
 */
@Composable
fun LocationPermissionWrapper(
    onPermissionGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Permission denied kalıcı olarak mı?
    var permanentlyDenied by remember { mutableStateOf(false) }

    // İzin sonucu dinleyicisi
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            hasPermission = true
            onPermissionGranted()
        } else {
            // Kullanıcı reddetti
            permanentlyDenied = true
        }
    }

    if (hasPermission) {
        // ✅ İzin var, içeriği göster
        content()
    } else {
        // 🛑 İzin yok, Rationale (Gerekçe) ekranı göster
        PermissionRationaleScreen(
            permanentlyDenied = permanentlyDenied,
            onRequestPermission = {
                launcher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        )
    }
}

@Composable
private fun PermissionRationaleScreen(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Emoji Icon
        Text(
            text = "📍",
            style = MaterialTheme.typography.displayLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Konum İzni Gerekli",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Adil bir yarış için GPS verisi gereklidir.\n\n" +
                   "🏎️ Hızınızı ve mesafenizi doğrulamak\n" +
                   "🛡️ Hilecileri engellemek\n" +
                   "🗺️ Canlı haritada yerinizi göstermek\n\n" +
                   "için konum iznine ihtiyacımız var.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (permanentlyDenied) {
            // Kullanıcı kalıcı olarak reddettiyse ayarlara yönlendir
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Ayarlardan İzin Ver")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "İzni reddettiğiniz için uygulamanın bu özelliği çalışamaz.\n" +
                       "Lütfen ayarlardan konum iznini etkinleştirin.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚀 İzin Ver ve Yarışa Başla")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = {
                // Kullanıcı ısrarla reddederse ayarlara yönlendir
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        ) {
            Text("⚙️ Ayarları Aç")
        }
    }
}
