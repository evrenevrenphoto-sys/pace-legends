@echo off
echo Pace Legends Temizlik Araci Baslatiliyor...
echo.

echo 1. Gereksiz hata dokumleri (.hprof) siliniyor (Yaklasik 1.5 GB)...
del /s /q *.hprof

echo.
echo 2. Gecici Build dosyalari temizleniyor...
rmdir /s /q .gradle
rmdir /s /q build
rmdir /s /q app\build

echo.
echo 3. Android Studio ayarlari (.idea) temizleniyor...
rmdir /s /q .idea

echo.
echo ==========================================
echo ISLEM TAMAMLANDI!
echo Projeniz artik tasinmaya hazir (yaklasik 20-50 MB).
echo.
echo NOT: Yeni bilgisayarda actiginizda ilk acilis biraz uzun surebilir,
echo cunku Gradle dosyalari yeniden indirilecek.
echo ==========================================
pause
