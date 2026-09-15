# Yapilacaklar / bekleyen istekler

Bu dosya "kodlamaya gec" komutu gelene kadar biriken istekleri ve
optimizasyon fikirlerini tutar. Bir madde yapildiginda buradan silinir
veya "yapildi" olarak isaretlenip commit mesajina referans verilir.

## Hata duzeltmeleri

- **Tam ekran gorsel siyah gozukuyor.** Onizleme sohbette sorunsuz calisiyor
  ama gorsele tikleyip tam ekrana gecince siyah ekran cikiyor. Sebebi
  bulundu: `MessageImage` icindeki `Box` + `matchParentSize()` yapisi,
  tam ekran dialogunda sadece genislik verilip yukseklik verilmeyince
  kutunun yuksekligi sifira dusuyor. Cozum: tam ekran gorunumde
  `fillMaxSize()` + `ContentScale.Fit` kullanmak (veya medya en-boy
  oranini `aspectRatio` ile vermek).

## Yeni ozellikler

- **Durtme (poke) ozelligi.**
  - Kullanici bir profile girdiginde "Durt" butonu gorunur.
  - `POST /users/{id}/poke` gibi bir uc nokta; sunucu FCM ile "X seni
    durttu" bildirimi yollar, uygulama acikken olay akisindan da anlik
    haber gelir (toast/snackbar).
  - Spam onleme: ayni kisiye belirli bir sure (orn. 1 dakika) icinde
    tekrar durtulemez.
  - Veri: `naber_pokes` tablosu (id, from_id, to_id, created_at) — hem
    cooldown kontrolu hem ileride "kim seni durttu" gecmisi icin.
  - Bildirim turu ayri (`type: poke`), tiklaninca sohbete degil profile
    gitmeli.

## Optimizasyonlar — KESIN YAPILACAK

Kullanici onayladi, bunlar artik "fikir" degil onaylanmis is listesi.
Zaten yapilanlar: hash ile tekrar yukleme onleme, on izleme uretimi,
gorsel sikistirma, yerel onbellek (MediaStore + LocalStore).

Yapilacaklar:

1. **TURN kimlik bilgilerini onbellekleme** — Metered'dan her aramada
   yeni ICE sunucu bilgisi cekmek yerine, sure dolana kadar cihazda
   tutup tekrar kullanmak.
2. **Okundu bilgisini toplu gonderme** — her mesaj gorulunce ayri
   `mark_read` istegi yerine kisa bir gecikmeyle (debounce) tek istekte
   toplamak.
3. **Arka planda daha seyrek yoklama** — uygulama arka plandayken
   250ms'lik agresif yoklama yerine daha uzun araliklarla (5-10 sn)
   yoklamak; FCM zaten arka planda haber veriyor.
4. **Yerel arama/filtreleme** — sohbet listesi ve gecmiste arama
   yaparken sunucuya gitmeden `LocalStore` uzerinde filtrelemek.
5. **Rozet/ozet hesaplama cihazda** — okunmamis sayisi, son mesaj
   onizlemesi gibi hesaplamalar sunucudan gelen ham veriyle cihazda
   hesaplanir.
6. **Bildirim gruplama cihazda** — Android'in kendi bildirim gruplama
   API'si ile ayni sohbetten gelen bildirimler tek grupta toplanir.
7. **Statik icerikler APK icine gomulu** — ileride emoji/sticker paketi
   eklenirse sunucudan indirilmez, cihazda hazir bulunur.
8. **Baglanti turune gore polling hizi** — wifi'de daha sik, mobil
   veride daha seyrek yoklama (pil ve veri tasarrufu).
9. **Cihazda tam metin arama indeksi** — sohbet gecmisinde arama
   yaparken basit bir yerel indeks (ornegin mesaj govdesi uzerinde)
   kurup sunucuya arama istegi atmamak.
10. **Sinyal/ICE adaylarini toplu gonderme** — WebRTC sinyalleşmesinde
    ICE adaylari tek tek degil kisa bir pencerede toplanip tek istekte
    gonderilebilir (network round-trip azalir).
11. **Profil/kisi listesi onbellegi** — kisiler listesi de sohbetler
    gibi cihazda saklanip aninda gosterilebilir (LocalStore'a benzer
    bir `ContactsStore`).
12. **Lazy/kademeli medya indirme** — sohbet acildiginda ekranda
    gorunen gorseller once indirilir, gorunmeyenler kaydirildikca
    indirilir (LazyColumn zaten bunu kismen sagliyor, MediaStore.ensure
    sadece gorunur oldugunda tetiklenmeli).
13. **Delta/fark tabanli senkronizasyon** — sohbet listesi yenilenirken
    tum liste yerine yalnizca degisen sohbetler cekilebilir (sunucu
    `since` parametresiyle sadece guncellenenleri donsun).
14. **Batarya optimizasyonu icin WorkManager** — arka plan senkronizasyon
    ve temizlik islerini (LocalFiles.cleanup gibi) Android WorkManager
    ile sistem uygun gordugunde calistirmak, surekli calisan servisler
    yerine.
15. **Sikistirilmis JSON / gzip** — sunucu yanitlarinda gzip acik degilse
    acilmasi (WordPress/Apache seviyesinde) veri kullanimini dusurur.

## Genel ozellikler — KESIN EKLENECEK

Kullanici onayladi, hepsi eklenecek. Not: **belge/dosya gonderme
listeden cikarildi, istenmiyor.**

**Mesajlasma:**

- **Sesli mesaj (voice note)** — basili tutup kaydet, birak gonder.
- **Mesaja yanit verme (reply/quote)** — hangi mesaja cevap
  yazildigini gostermek.
- **Mesaj iletme (forward)** — bir mesaji baska bir sohbete gondermek.
- **Emoji reaksiyon** — mesaja basili tutup 👍❤️😂 gibi hizli tepki.
- **Mesaj duzenleme** — gonderilen metni kisa sure icinde duzeltebilme
  ("duzenlendi" etiketiyle).
- **Uygulama ici kamera** — galeriye gitmeden dogrudan fotograf
  cekip gondermek.

**Sohbet yonetimi:**

- **Sohbeti sabitleme (pin)** — onemli sohbetler listenin basinda.
- **Sureli sessize alma** — "8 saat / 1 hafta / surekli" secenekleri.
- **Global arama** — tum sohbetlerde tek seferde arama.
- **Kaybolan mesajlar — ISTEGE BAGLI (opt-in).** Varsayilan kapali;
  kullanici bir sohbette ozellikle acarsa o sohbette mesajlar belirli
  sure sonra otomatik silinir. Sohbet bazinda ayri ayri acilip
  kapatilabilmeli, global bir ayar olmamali.

**Grup ozellikleri:**

- **@bahsetme (mention)** — grupta birinin adini yazinca ona ozel
  bildirim gitmesi.
- **Grup davet KODU (link degil).** Grup bilgisinde kisa bir kod
  uretilir (orn. 6 haneli). Ana ekranda "Kod ile grup bul" secenegi
  ile kullanici bu kodu girip gruba katilir. Link paylasimindan
  farkli olarak kod disaridan tiklanabilir bir URL olmadigindan
  daha kontrollu: sadece kod bilen, uygulamaya zaten giris yapmis
  kisi katilabilir. Kodun suresiz mi yoksa yenilenebilir mi olacagi
  (guvenlik icin admin "kodu yenile" diyebilsin) sonra netlesir.
- **Anket (poll)** — grup icinde hizli oylama.

**Gizlilik / guvenlik:**

- **Uygulama kilidi** — PIN veya parmak izi ile uygulamayi acma.
- **Kullanici engelleme.**
- **Son gorulme / okundu bilgisini gizleme secenegi.**

**Kisisellestirme:**

- **Acik tema secenegi** — su an sadece koyu tema var.
- **Sohbet arka plani / renk teması.**
- **Konum paylasma** — anlik veya tek seferlik konum gonderme.

## Ek ozellik onerileri (henuz onaylanmadi, degerlendirilecek)

Daha once onerilenlere ek, yine kucuk guvenilir grup (~10 kisi)
mantigina uygun, karmasiklastirmayan fikirler:

- **Mesaj yildizlama (starred messages)** — onemli mesajlari
  isaretleyip sonradan tek bir ekrandan hepsini gorme.
- **Sohbeti arsivleme** — silmeden listeden gizleme, istenince geri
  cikarma.
- **Grupta sabitlenmis mesaj/duyuru** — grup bilgisinin ustunde
  sabit duran bir duyuru satiri (yalnizca admin degistirebilir).
- **Kisiye ozel takma ad (nickname)** — karsi tarafin profil adini
  degistirmeden, yalnizca sizin ekraninizda farkli bir isimle gorme.
- **Gorunmez mod** — yalnizca son gorulmeyi degil, anlik cevrimici
  noktasini da gizleme (simetrik: gizleyen karsisininkini de goremez).
- **"Yaziyor" gostergesini kapatma secenegi** — gizlilik simetrisiyle
  ayni mantik.
- **GIF/sticker destegi** — yerel bir sticker paketi veya basit bir
  GIF arama entegrasyonu.
- **Medya galerisi gorunumu** — bir sohbetin butun gonderilen
  gorsellerini izgara halinde tek ekranda gorme (galeriye girmeden).
- **Canli konum paylasimi** — sureli (orn. 30 dk) hareket eden konum,
  tek seferlik konumdan farkli olarak.
- **Sesli mesaj hiz kontrolu** — 1x / 1.5x / 2x dinleme (sesli mesaj
  eklenince dogal bir tamamlayici).
- **"Duyuru modu" grup ayari** — admin acarsa yalnizca adminler mesaj
  atabilir, digerleri sadece okur (kurallar/duyurular grubu icin).
- **Favori/sabit kisiler** — sohbet listesinde en ust kisimda ayri bir
  "favoriler" bolumu.
- **Dogum gunu alani + hatirlatma** — profile dogum tarihi eklenir,
  o gun diger kullanicilara hafif bir bildirim/rozet gosterilir.
- **Ana ekran widget'i** — son mesajlari veya okunmamis sayisini
  telefonun ana ekraninda gosteren kucuk bir widget.

## Kodlamaya gecince siralama

1. Tam ekran gorsel siyah ekran duzeltmesi (kucuk, hizli).
2. Durtme ozelligi (sunucu + istemci).
3. Onaylanan optimizasyonlar (KESIN YAPILACAK listesi).
4. Onaylanan genel ozellikler (KESIN EKLENECEK listesi) — kullanici
   sirayi belirleyecek.
5. Ek oneriler arasindan kullanicinin sectikleri.
