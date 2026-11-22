# Smart Kitchen AI 🥬✨

Yapay zeka destekli akıllı mutfak yönetimi, son tüketim tarihi (SKT) takibi, fiş/barkod tarama ve sıfır atık yemek planlama Android uygulaması.

---

## Öne Çıkan Özellikler

- **Akıllı Dolap & Kiler Envanteri (`Dolabım`)**:
  - Buzdolabı, Kiler ve Dondurucu bazlı canlı ürün sayımı, arama, durum filtresi ve sıralama (`SKT`, `A-Z`, `Kalan %`).
  - Tüketilen veya israf edilen ürünlerde **Geri Al (Undo)** desteği.
- **Acil Tüketilecekler & Sistem Bildirimleri (`Acil`)**:
  - Süresi geçmiş, 0-3 gün kalan veya miktarı %25'in altına düşen ürünleri önceliklendirir; 7 gün, 3 gün, 1 gün ve son gün sistem bildirimi gönderir.
- **Akıllı Alışveriş Listesi (`Alışveriş`)**:
  - Room veritabanı destekli onay kutuları, tahmini toplam bütçe, mükerrer kontrolü ve işaretlenenleri tek dokunuşla dolaba aktarma.
- **Multimodal Fiş, Dolap & Barkod Tarama (`Tara` & Barkod Okuyucu)**:
  - Google ML Kit OCR + Barkod tarama + Open Food Facts entegrasyonu + Gemini AI görsel analizi ve çevrimdışı ayrıştırıcı.
  - Kaydetmeden önce ürün adı, miktar, fiyat, saklama alanı ve raf ömrünü düzenleyebilme.
- **Kişisel Tarif Motoru, Sayaçlı Pişirme Modu & Haftalık Plan (`Tarifler`)**:
  - Diyet (`Vegan`, `Vejetaryen`, `Glütensiz`), alerji ve sevilmeyen malzeme filtrelerine tam uyumlu Gemini AI ve çevrimdışı tarifler.
  - **Adım Adım Pişirme Modu**: Ekranı açık tutan, adım bazlı sayaç (`+1/+3/+5/+10 dk` ekleme) ve süre dolduğunda bildirim.
  - **7 Günlük Haftalık Menü Planlayıcı**: Dolaptaki malzemelere göre haftalık sıfır atık planı.
- **Sürdürülebilirlik & Aylık Mutfak Karnesi**:
  - Kurtarılan bütçe (`₺`), önlenen `CO₂` (`kg`), sanal su tasarrufu (`L`), rozetler ve paylaşılabilir aylık mutfak karnesi.

---

## Gemini API Anahtarı Kurulumu

Uygulama doğrudan Google Gemini REST API (`gemini-2.5-flash` / fallback modelleri) üzerinden çalışır ve iki farklı API anahtarı yöntemi destekler:

### Yöntem 1: Uygulama İçinden Ekleme (Önerilen — Kod Değiştirmeden)
1. [Google AI Studio](https://aistudio.google.com/apikey) üzerinden `AIza...` ile başlayan yeni bir API anahtarı oluşturun.
2. Uygulamada sağ üstteki **Yeşil Mutfak Puanı** rozetine dokunarak **Sürdürülebilirlik & Ayarlar** sekmesini açın.
3. **Gemini API Anahtarı** kartına anahtarınızı yapıştırıp **Kaydet**'e basın ve **Bağlantıyı Test Et** ile doğrulayın.

### Yöntem 2: Derleme Zamanında `.env` Dosyası ile
1. Proje kök dizinindeki `.env.example` dosyasını `.env` adıyla kopyalayın:
   ```bash
   cp .env.example .env
   ```
2. `.env` dosyasındaki satırı kendi anahtarınızla güncelleyin (`.env` dosyası `.gitignore` içindedir, GitHub'a gönderilmez):
   ```properties
   GEMINI_API_KEY=AIzaSy...SIZIN_ANAHTARINIZ
   ```

> **Not:** Geçerli bir API anahtarı eklenmediğinde veya internet olmadığında uygulama otomatik olarak gelişmiş **Çevrimdışı Akıllı Ayrıştırıcı** ve **Yerel Tarif Motoru** ile çalışmaya devam eder.

---

## Derleme ve Test

Projede Gradle Wrapper dahildir. Android SDK kurulu bir ortamda:

```bash
# Birim testlerini çalıştır (JVM + Robolectric)
./gradlew testDebugUnitTest

# Debug APK oluştur
./gradlew assembleDebug
```

Windows PowerShell üzerinde:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

---

## Teknoloji Yığını

- **Dil & UI**: Kotlin, Jetpack Compose, Material 3
- **Mimari**: MVVM (`InventoryViewModel`), `domain` katmanı (`ReceiptParser`, `LocalRecipeEngine`, `UsernameValidator`, `EcoMetricsCalculator`)
- **Veritabanı**: Room (SQLite)
- **Yapay Zeka & Görsel İşleme**: Google Gemini REST API (`OkHttp` + `Moshi`), Google ML Kit (Text Recognition & Barcode Scanning), Open Food Facts API
- **Test**: JUnit4, Robolectric
