# Firebase Kurulum Rehberi

Pace Legends uygulamasının tam fonksiyonel çalışabilmesi için Firebase projesi oluşturulmalı ve yapılandırılmalıdır.

## 1. Firebase Projesi Oluşturma
1. [Firebase Console](https://console.firebase.google.com/) adresine gidin.
2. "Proje Ekle" (Add Project) butonuna tıklayın.
3. Proje adını `Pace Legends` (veya istediğiniz bir isim) yapın.
4. Google Analytics'i etkinleştirin veya devre dışı bırakın (tercih sizin, test için gerekli değil).
5. "Proje Oluştur" (Create Project) diyerek tamamlayın.

## 2. Android Uygulaması Ekleme
1. Proje ana sayfasındaki **Android** ikonuna tıklayın.
2. **Android paket adı** (Package Name) kısmına tam olarak şunu yazın:
   `com.pace.legends`
3. (İsteğe bağlı) Uygulama takma adı: Pace Legends
4. "Uygulamayı Kaydet" (Register app) butonuna tıklayın.

## 3. Yapılandırma Dosyasını İndirme
1. **google-services.json** dosyasını indirin.
2. Bu dosyayı projenizin `app/` klasörünün içine kopyalayın.
   *   Tam yol: `D:\Pace Legends\app\google-services.json`
   *   Mevcut `mock` dosyanın üzerine yazın.

## 4. Servisleri Etkinleştirme
Sol menüden aşağıdaki servisleri sırasıyla açıp "Başla" (Get Started) diyerek etkinleştirin:

### A. Cloud Firestore (Database)
1. **Build > Firestore Database** menüsüne gidin.
2. "Veritabanı Oluştur" (Create Database) deyin.
3. **Test Modunda Başla** (Start in Test Mode) seçeneğini seçin. (Geliştirme aşamasında izin sorunları yaşamamak için).
4. Konum olarak size en yakın olanı (örn. `eur3` - Europe West) seçebilirsiniz.

### B. Remote Config (Reklam Ayarları)
1. **Release & Monitor > Remote Config** menüsüne gidin.
2. "Yapılandırma oluştur" (Create configuration) deyin.
3. Aşağıdaki parametreleri ekleyin:
    *   **Parametre Adı:** `show_ads`
        *   **Veri Tipi:** Boolean
        *   **Varsayılan Değer:** `true`
    *   **Parametre Adı:** `interstitial_ad_frequency`
        *   **Veri Tipi:** Number
        *   **Varsayılan Değer:** `3`

Bu adımları tamamladığınızda uygulama gerçek verilerle konuşmaya hazır olacaktır!
