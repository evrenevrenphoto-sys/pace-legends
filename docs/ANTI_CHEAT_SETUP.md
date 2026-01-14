# 🛡️ Pace Legends Anti-Cheat Sistemi

## Genel Bakış

Bu sistem, Google Fit veya diğer uygulamalara manuel olarak eklenen sahte adım verilerini tespit eder.

## Tespit Kriterleri

| Kriter | Eşik | Puan | Açıklama |
|--------|------|------|----------|
| İmkansız Hız | >200 adım/dk | 50 | Koşarak bile sürdürülemez |
| 5dk Spike | >2000 adım | 40 | 5 dakikada 2000+ adım |
| 15dk Spike | >5000 adım | 30 | 15 dakikada 5000+ adım |
| Gece Aktivitesi | >3000 adım (00:00-05:00) | 20 | Gece saatlerinde şüpheli artış |
| Günlük Limit | >60000 adım | 25 | Günde 60.000+ adım imkansız |

**Flag Eşiği:** Toplam puan ≥50 ise kullanıcı flag'lenir.

## Kurulum

### 1. Firebase CLI Kurulumu (Eğer yoksa)

```bash
npm install -g firebase-tools
firebase login
```

### 2. Functions Bağımlılıklarını Yükle

```bash
cd functions
npm install
```

### 3. Firebase Projesini Bağla

```bash
cd ..
firebase use --add
# Proje ID'nizi seçin
```

### 4. Deploy

```bash
firebase deploy --only functions
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

## Kullanım

### Şüpheli Kullanıcıları Görüntüleme

Firestore Console'da:
- `users/{userId}/antiCheat/flagged = true` olan kullanıcıları filtreleyin

### Log İnceleme

```bash
firebase functions:log --only onLeaderboardUpdate
```

### Kullanıcı Flag'ini Kaldırma (Admin)

Firebase Console'dan `users/{userId}/antiCheat/flagged` alanını `false` yapın.

## Mimari

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Android App    │────▶│    Firestore     │────▶│ Cloud Functions │
│  (Adım Sync)    │     │  (Leaderboard)   │     │  (Anti-Cheat)   │
└─────────────────┘     └──────────────────┘     └────────┬────────┘
                                                          │
                                                          ▼
                                               ┌─────────────────┐
                                               │ antiCheatLogs   │
                                               │ users.antiCheat │
                                               └─────────────────┘
```

## Leaderboard'da Flag'li Kullanıcıları Gizleme

Android uygulamasında `LeaderboardRepository`'de şu filtreyi ekleyin:

```kotlin
.whereEqualTo("antiCheat.flagged", false)
// veya
.whereNotEqualTo("antiCheat.flagged", true)
```

## Test

### Manuel Test
1. Google Fit'e manuel 10.000 adım ekleyin
2. Pace Legends'ı açın ve sync yapın
3. Firebase Console'da `antiCheatLogs` koleksiyonunu kontrol edin
4. `users/{userId}/antiCheat` alanını kontrol edin

### Emulator Test
```bash
cd functions
npm run serve
```

## Güvenlik Notları

- AntiCheat alanı Firestore Rules ile korunuyor
- Sadece Cloud Functions bu alana yazabilir
- Kullanıcılar kendi flag durumlarını değiştiremez
