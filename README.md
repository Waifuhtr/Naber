# Naber

WhatsApp benzeri, kucuk olcekli (yaklasik 10 kullanici) bir mesajlasma uygulamasi.

| Katman | Teknoloji |
| --- | --- |
| Mobil | Android / Kotlin / Jetpack Compose (Material 3) |
| Backend & veritabani | WordPress eklentisi (`naber-chat`), REST API |
| Medya | Backblaze B2 (bucket bilgileri eklenti ekranindan girilir) |
| Sesli arama | WebRTC (P2P), signaling WordPress uzerinden, TURN harici |
| Bildirim | Firebase Cloud Messaging (istege bagli) |
| Yonetim | Uygulama ici panel — yalnizca WordPress'te admin olan kullanicida gorunur |

Uctan uca sifreleme yoktur (istenmedi). Redis/Kafka/mikroservis gibi yapilar kullanilmaz.

---

## 1. Mimari ozet

```
Android (Kotlin, Compose)
   |  HTTPS + Bearer token
   v
WordPress + naber-chat eklentisi  ──►  MySQL (wp_naber_* tablolari)
   |                 |
   |                 └── WebRTC signaling (offer / answer / ICE)
   |
   ├── Backblaze B2   (gorseller; anahtarlar yalnizca sunucuda)
   └── Firebase FCM   (uygulama kapaliyken bildirim)

Ses trafigi:  Android  <────  WebRTC P2P  ────>  Android
              (kurulamazsa harici TURN sunucusu uzerinden)
```

**Gercek zamanlilik:** Paylasimli WordPress hostinglerinde WebSocket calismadigi icin
uzun yoklama (long-polling) kullanilir: `GET /events` istegi sunucuda en fazla 25 saniye
bekler, yeni mesaj/sinyal olunca hemen doner. Bekleme sirasinda yalnizca iki kucuk
`MAX(id)` sorgusu 250 ms araliklarla calisir; tam yanit ancak gercekten yeni bir sey
oldugunda hazirlanir. Teslim suresi tipik olarak yarim saniyenin altindadir.
Hosting es zamanli istek sinirindan sikayet ederse **Naber Chat > Canli baglanti suresi**
ve **Kontrol araligi** degerleri yonetim ekranindan ayarlanabilir.

---

## 2. Dosya yapisi

```
wordpress-plugin/naber-chat/
├── naber-chat.php                 Eklenti girisi, kanca kayitlari
├── includes/
│   ├── class-naber-db.php         Tablolar (conversations, messages, media, calls, signals, devices)
│   ├── class-naber-settings.php   Ayarlar + bucket bilgisi dogrulama kurallari
│   ├── class-naber-b2.php         Backblaze B2 istemcisi + baglanti testi
│   ├── class-naber-auth.php       Token oturumu, presence, is_admin bilgisi
│   ├── class-naber-chat-repo.php  Sohbet/mesaj sorgulari, okundu & yaziyor durumu
│   ├── class-naber-media.php      Medya kayitlari, imzali indirme adresleri
│   ├── class-naber-calls.php      Arama kayitlari ve signaling kuyrugu
│   ├── class-naber-push.php       FCM HTTP v1 bildirimleri
│   ├── class-naber-rest.php       Tum REST uclari + yetki kontrolleri
│   └── class-naber-admin-page.php WordPress yonetim ekrani (bucket formu + test dugmesi)
└── tests/
    ├── bootstrap.php              Kucuk WordPress taklidi + sahte Backblaze API
    └── test-b2-settings.php       Bucket bilgisi testleri (54 kontrol)

android/
├── app/src/main/java/com/naber/app/
│   ├── NaberApp.kt                Uygulama girisi, servis kaydi, bildirim kanallari
│   ├── MainActivity.kt            Navigasyon, oturum durumu, arama ekrani katmani
│   ├── data/
│   │   ├── Models.kt              Veri siniflari + JSON cozumleme
│   │   ├── Session.kt             Token/sunucu adresi saklama (SharedPreferences)
│   │   ├── ApiClient.kt           Tum REST cagrilari + B2'ye dogrudan yukleme
│   │   └── EventHub.kt            Uzun yoklama akisi (mesaj, sinyal, okundu, yaziyor)
│   ├── call/CallManager.kt        WebRTC baglantisi, mikrofon, TURN fallback
│   ├── push/                      FCM servisi ve bildirimler
│   └── ui/screens/                Giris, sohbetler, sohbet, yeni sohbet, profil, yonetim, arama
└── app/build.gradle.kts           google-services.json varsa Firebase otomatik devreye girer
```

---

## 3. Kurulum

### 3.1 WordPress eklentisi

1. `wordpress-plugin/naber-chat` klasorunu sitenizde `wp-content/plugins/naber-chat` altina kopyalayin.
2. Eklentiler ekranindan **Naber Chat**'i etkinlestirin (tablolar otomatik olusur).
3. Sol menude **Naber Chat** ekranini acin.

### 3.2 Backblaze B2 bucket bilgileri (eklenti uzerinden)

Backblaze panelinde:

1. Bir bucket olusturun (ornek: `naber-medya`). Ozel (private) birakabilirsiniz.
2. **App Keys > Add a New Application Key**: bu bucket'a `readFiles`, `writeFiles`,
   `deleteFiles` ve `listBuckets` yetkisi verin. `keyID` ve `applicationKey` degerlerini kopyalayin.

WordPress > Naber Chat ekraninda:

| Alan | Aciklama |
| --- | --- |
| Application Key ID | Backblaze `keyID` |
| Application Key | Yalnizca olusturuldugu anda gorunur; kaybederseniz yeni anahtar uretin |
| Bucket adi | Ornek `naber-medya` (6-50 karakter, kucuk harf/rakam/tire, `b2-` ile baslayamaz) |
| Bucket ID | Bos birakin — baglanti testi otomatik doldurur |
| Klasor oneki | Bucket icinde kullanilacak klasor, ornek `naber/` |
| Genel erisim adresi | Bucket public ise veya CDN varsa; bos ise imzali adres uretilir |
| Imzali adres suresi | 60-604800 saniye |
| Azami dosya boyutu | 1-200 MB |

**Baglantiyi test et** dugmesi formdaki degerlerle (kaydetmeden once) su adimlari sirayla dener:

1. Alan bicimi — yanlis yazilmis anahtar/bucket adi
2. Kimlik dogrulama — `b2_authorize_account`
3. Bucket — bucket gercekten var mi, anahtar bu bucket'a erisebiliyor mu
4. Yetkiler — `writeFiles`, `readFiles`, `deleteFiles` var mi
5. Yazma testi — kucuk bir deneme dosyasi yuklenir
6. Temizlik — deneme dosyasi silinir

Her adim tek tek "gecti / kaldi" olarak gosterilir; hata varsa nedenini yazar
(ornegin "Bu anahtar yalnizca X bucket'ina erisebiliyor").

Ayni test uygulama icindeki **Yonetim** ekranindan da calistirilabilir
(`POST /wp-json/naber/v1/admin/storage/test`, sadece admin).

Anahtarlar Android uygulamasina hic gonderilmez; uygulama yalnizca tek kullanimlik
upload adresi ve kisa omurlu indirme jetonu alir.

### 3.3 Sesli arama (TURN - Metered)

WordPress TURN sunucusu saglamaz; harici bir servis kullanilir. Eklenti
[Metered](https://www.metered.ca/) ile dogrudan calisir:

1. Metered panelinde bir uygulama olusturun (ornek: `naber` -> `naber.metered.live`).
2. **Developers > API Keys** bolumunden API anahtarini alin.
3. WordPress > Naber Chat > **Sesli arama** bolumune uygulama adini ve API anahtarini girin.
4. **TURN yapilandirmasini test et** dugmesine basin: kimlik bilgileri gercekten cekiliyor mu,
   kac TURN/STUN adresi donuyor, kullanici adi/sifre geliyor mu adim adim gosterilir.

Sunucu kimlik bilgilerini `https://<uygulama>.metered.live/api/v1/turn/credentials` adresinden
ceker ve varsayilan olarak 30 dakika onbellekler. API anahtari APK'ya gomulmez; uygulama
yalnizca `GET /ice-servers` ile kisa omurlu listeyi alir. Kendi TURN sunucunuz varsa ayni
ekrandaki "Ek TURN adresleri" alanina girip ikisini birlikte kullanabilirsiniz.

### 3.4 Bildirimler (istege bagli)

1. Firebase'de proje olusturun, Android uygulamasini `com.naber.app` (debug icin `com.naber.app.debug`) olarak ekleyin.
2. `google-services.json` dosyasini `android/app/` altina koyun (derleme onu otomatik algilar).
3. Firebase > Proje ayarlari > Servis hesaplari > yeni ozel anahtar (JSON) alin ve
   WordPress ekranindaki **Servis hesabi JSON** alanina yapistirin, proje kimligini de girin.

Firebase yapilandirilmazsa uygulama yine calisir: acikken mesajlar olay akisindan gelir,
yalnizca uygulama kapaliyken bildirim gelmez.

### 3.5 Android uygulamasi

```bash
cd android
./gradlew assembleDebug        # cikti: app/build/outputs/apk/debug/app-debug.apk
```

Uygulamayi ilk actiginizda WordPress adresinizi girip kayit olun veya giris yapin.

---

## 4. REST uclari (`/wp-json/naber/v1`)

| Uc | Aciklama |
| --- | --- |
| `POST /register`, `POST /login`, `POST /logout` | Hesap ve token |
| `GET /me`, `POST /me` | Profil bilgisi ve duzenleme (`is_admin` buradan gelir) |
| `GET /users` | Kisi listesi |
| `GET /chats`, `POST /chats` | Sohbet listesi / sohbet acma |
| `GET /chats/{id}/messages` | Mesaj gecmisi |
| `POST /chats/{id}/read`, `POST /chats/{id}/typing` | Okundu ve "yaziyor" |
| `POST /messages` | Metin veya gorsel mesaji |
| `POST /media/upload-url`, `POST /media/complete` | B2'ye dogrudan yukleme akisi |
| `POST /media/upload` | Yedek yol (dosya sunucu uzerinden gecer) |
| `GET /media/{id}/url` | Imzali indirme adresi |
| `GET /events` | Uzun yoklama: mesaj, sinyal, okundu, yaziyor, gelen arama |
| `GET /ice-servers` | STUN/TURN yapilandirmasi |
| `POST /calls/start`, `/calls/{id}/accept|reject|end` | Arama akisi |
| `POST /calls/{id}/signal`, `GET /calls/{id}/signals` | WebRTC signaling |
| `GET /calls` | Arama gecmisi |
| `POST /devices`, `DELETE /devices` | FCM cihaz jetonu |
| `GET /admin/stats`, `GET /admin/users`, `POST|DELETE /admin/users/{id}`, `POST /admin/storage/test` | Yalnizca admin (aksi halde 403) |

---

## 5. Guvenlik

- Token'lar kullanici metasinda **SHA-256 ozetiyle** saklanir, duz metin tutulmaz (90 gun gecerli, cihaz basina en fazla 5).
- Her sohbet/mesaj/arama ucunda katilimci kontrolu yapilir; baskasinin sohbeti 403 doner.
- Mesaj gonderirken gonderen daima oturum sahibidir (istemciden `sender_id` alinmaz).
- Admin uclarinda UI gizlemenin yani sira sunucu tarafinda rol kontrolu vardir.
- Yuklemelerde MIME ve boyut kontrolu (varsayilan: jpeg/png/webp/gif, 25 MB).
- Backblaze ve FCM sirlari yalnizca WordPress tarafinda; APK icinde gomulu sir yoktur.
- Devre disi birakilan kullanicinin tum token'lari silinir.

---

## 6. Testler

```bash
php wordpress-plugin/naber-chat/tests/test-b2-settings.php
```

54 kontrol: bucket bilgisi bicim dogrulamasi (yanlis Key ID, gecersiz bucket adi,
`b2-` oneki, http adres, sinir disi sureler...) ve taklit Backblaze API uzerinde
baglanti testi (401, bucket yok, yanlis bucket'a kisitli anahtar, eksik yetki,
silme izni yok, yazma testi kapali). Ag baglantisi gerektirmez.

GitHub Actions (`.github/workflows/android.yml`) her push'ta bu testleri calistirir
ve debug APK'yi derleyip artifact olarak yukler.
