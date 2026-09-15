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

## Genel ozellik onerileri (durtme disinda)

Kucuk bir arkadas/aile grubu (~10 kisi) icin, karmasiklastirmadan asil
deger katacak ozellikler. Kullanici sececek, hepsi zorunlu degil.

**Mesajlasmada eksik olan cekirdek ozellikler:**

- **Sesli mesaj (voice note)** — WhatsApp'in en cok kullanilan
  ozelliklerinden biri; basili tutup kaydet, birak gonder.
- **Mesaja yanit verme (reply/quote)** — hangi mesaja cevap
  yazildigini gostermek, kalabalik sohbetlerde onemli.
- **Mesaj iletme (forward)** — bir mesaji baska bir sohbete gondermek.
- **Emoji reaksiyon** — mesaja basili tutup 👍❤️😂 gibi hizli tepki
  birakmak; her seye yazili cevap yazmaktan kurtarir.
- **Mesaj duzenleme** — gonderilen metni kisa bir sure icinde
  duzeltebilme ("duzenlendi" etiketiyle).
- **Belge/dosya gonderme** — su an sadece gorsel var; PDF, zip gibi
  dosyalar da gonderilebilsin.
- **Uygulama ici kamera** — galeriye gitmeden dogrudan fotograf
  cekip gondermek (WhatsApp'taki gibi).

**Sohbet yonetimi:**

- **Sohbeti sabitleme (pin)** — onemli sohbetler listenin basinda
  kalsin.
- **Sureli sessize alma** — "8 saat / 1 hafta / surekli" gibi secenekler
  (su an sadece acik/kapali gibi gorunuyor).
- **Sohbet ici global arama** — tum sohbetlerde tek seferde arama
  (belirli bir kisiden veya kelimeden gecen mesajlari bulmak).
- **Kaybolan mesajlar** — secilirse mesajlar belirli sure sonra
  otomatik silinir (gizlilik odakli sohbetler icin).

**Grup ozellikleri:**

- **@bahsetme (mention)** — grupta birinin adini yazinca ona ozel
  bildirim gitmesi.
- **Grup davet linki** — yeni birini eklemek icin manuel uye eklemek
  yerine paylasilabilir bir link.
- **Anket (poll)** — grup icinde hizli oylama.

**Gizlilik / guvenlik:**

- **Uygulama kilidi** — PIN veya parmak izi ile uygulamayi acma
  (telefonu baskasi alirsa sohbetler gorunmesin).
- **Kullanici engelleme** — istenmeyen kisiyi engelleme (10 kisilik
  guvenilir grup icin dusuk oncelikli ama yine de faydali).
- **Son gorulme / okundu bilgisini gizleme secenegi** — kullanici
  isterse bu bilgileri paylasmasin.

**Kisisellestirme:**

- **Acik tema secenegi** — su an sadece koyu tema var; kullanici
  secebilse iyi olur.
- **Sohbet arka plani / renk teması** — her sohbete farkli bir gorunum.
- **Konum paylasma** — anlik veya tek seferlik konum gonderme.

**Onerilen oncelik (dustuğu yerden baslanabilir):**

1. Mesaja yanit verme + emoji reaksiyon (en cok fark yaratacaklar, gorece kolay)
2. Sesli mesaj
3. Sohbeti sabitleme + sureli sessize alma
4. Uygulama kilidi
5. Digerleri kullaniciya gore

## Kodlamaya gecince siralama

1. Tam ekran gorsel siyah ekran duzeltmesi (kucuk, hizli).
2. Durtme ozelligi (sunucu + istemci).
3. Onaylanan optimizasyonlar (yukaridaki KESIN YAPILACAK listesi).
4. Kullanicinin sectigi genel ozellikler.
