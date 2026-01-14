# Google Maps API Key Kurulum Rehberi

Haritanın uygulamada (gri ekran yerine) düzgün görünebilmesi için geçerli bir API Key gereklidir.

## 1. Google Cloud Console'u Açın
1. [Google Cloud Console](https://console.cloud.google.com/) adresine gidin.
2. Üst menüden **Pace Legends** (veya Firebase için oluşturduğunuz) projenizi seçin. (Firebase projeniz otomatik olarak burada da görünür).

## 2. Maps SDK'yı Etkinleştirin
1. Sol menüden **APIs & Services > Library** yolunu izleyin.
2. Arama çubuğuna **"Maps SDK for Android"** yazın.
3. Çıkan sonuçta **Maps SDK for Android**'e tıklayın.
4. **ENABLE** (Etkinleştir) butonuna tıklayın.

## 3. API Key Oluşturun
1. Sol menüden **APIs & Services > Credentials** yolunu izleyin.
2. Üstteki **+ CREATE CREDENTIALS** butonuna tıklayın ve **API Key**'i seçin.
3. Ekranda oluşturulan anahtarı kopyalayın.

## 4. Anahtarı Projeye Ekleyin
1. Android Studio'da `AndroidManifest.xml` dosyasını açın.
2. Aşağıdaki satırı bulun:
   ```xml
   <meta-data
       android:name="com.google.android.gms.geo.API_KEY"
       android:value="AIzaSyDummyKeyForBuildSuccess00000000"/>
   ```
3. `android:value` kısmındaki dummy değeri silip, kopyaladığınız yeni anahtarı yapıştırın.

## (Önemli) Anahtarı Kısıtlama (Önerilen)
Güvenlik için anahtarınızı sadece bu uygulamanın kullanabileceği şekilde kısıtlamalısınız.

1. Cloud Console'da oluşturduğunuz anahtarın ismine tıklayın (veya kalem ikonuna).
2. **API restrictions** bölümünde:
   *   "Restrict key" seçeneğini seçin.
   *   Listeden **Maps SDK for Android**'i seçip kaydedin.
3. **Application restrictions** bölümünde:
   *   "Android apps" seçeneğini seçin.
   *   **Package name:** `com.pace.legends`
   *   **SHA-1 certificate fingerprint:** Logcat'teki hata mesajında veya Gradle `signinReport` çıktısında gördüğünüz SHA-1 değerini girin.
     *   *Logcat'teki hata mesajında genellikle şöyle yazar:* `Android Application (<cert_fingerprint>;...`

Bu adımları tamamladıktan sonra uygulamayı yeniden çalıştırın. Harita artık yüklenecektir! 🗺️
