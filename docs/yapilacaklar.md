# Yapilacaklar / bekleyen istekler

Bu dosya "kodlamaya gec" komutu gelene kadar biriken istekleri ve
optimizasyon fikirlerini tutar. Bir madde yapildiginda buradan silinir
veya "yapildi" olarak isaretlenip commit mesajina referans verilir.

## Hata duzeltmeleri

- [x] **Tam ekran gorsel siyah gozukuyor.** Duzeltildi: `MessageImage`
  cagrisina tam ekranda `fillMaxSize()` verildi
  (commit: "Tam ekran gorsel siyah ekran hatasini duzelt").

## Yeni ozellikler

- [x] **Durtme (poke) ozelligi.** Sunucu (naber_pokes tablosu, 60 sn
  bekleme suresi, FCM bildirimi) + istemci (UserProfileScreen, "Durt"
  butonu, geri sayim) tamamlandi
  (commit: "Durtme (poke) ozelligi ve kullanici profili ekrani").

## Optimizasyonlar — KESIN YAPILACAK

Kullanici onayladi, bunlar artik "fikir" degil onaylanmis is listesi.
Zaten yapilanlar: hash ile tekrar yukleme onleme, on izleme uretimi,
gorsel sikistirma, yerel onbellek (MediaStore + LocalStore).

1. [x] **TURN kimlik bilgilerini onbellekleme** — istemci tarafinda
   5 dakika onbelleklendi, arama kabulunde tekrar sorulmuyor.
2. [x] **Okundu bilgisini toplu gonderme** — 300ms debounce ile tek
   istekte toplaniyor.
3. [x] **Arka planda daha seyrek yoklama** — zaten yapilmisti (bu
   turda dogrulandi): `onPause()` yoklamayi tamamen durduruyor, FCM
   devraliyor. Ek islem gerekmedi.
4. [x] **Yerel arama/filtreleme** — zaten yapilmisti (dogrulandi):
   ChatsTab ve ContactsTab arama kutulari sunucuya gitmeden yerel
   listede filtreliyor.
5. [x] **Rozet/ozet hesaplama cihazda** — okunmamis toplami artik
   sohbet listesinden aninda hesaplaniyor (`EventHub.setUnreadTotal`).
6. [x] **Bildirim gruplama cihazda** — Android grup API'si ile iki+
   sohbetten bildirim varken tek ozet gosteriliyor.
7. [ ] **Statik icerikler APK icine gomulu** — ileride emoji/sticker
   paketi eklenirse gecerli olacak; su an bekletiliyor (henuz boyle
   bir ozellik yok).
8. [x] **Baglanti turune gore polling hizi** — olculu baglantida
   30 sn, wifi'de 20 sn bekleme (sunucu zaten 30 sn'ye sabitliyor).
9. [x] **Cihazda tam metin arama indeksi** — "Global arama" ile birlikte
   yapildi: `LocalStore.searchMessages()` saklanan JSON dosyalarini
   tarar, sunucuya istek gitmez.
10. [ ] **BILEREK ATLANDI: Sinyal/ICE adaylarini toplu gonderme.**
    Arama baglantisini kuran kritik ve kirilgan bir yol (bu projede
    daha once echo/mute/hangup hatalarina sebep olmustu); gercek
    cihazda canli test gerektirir, kullanici yokken riske atilmadi.
    Kullanici musait olunca elle test ederek yapilmali.
11. [x] **Profil/kisi listesi onbellegi** — ContactsTab artik
    LocalStore'da saklaniyor, acilista aninda dolu geliyor.
12. [ ] **Lazy/kademeli medya indirme** — henuz yapilmadi.
13. [ ] **Delta/fark tabanli senkronizasyon** — henuz yapilmadi.
14. [ ] **Batarya optimizasyonu icin WorkManager** — henuz yapilmadi.
15. [ ] **Sikistirilmis JSON / gzip** — sunucu/hosting ayari,
    kullanicinin WordPress barindirmasinda kontrol etmesi gerekiyor
    (kod degisikligi degil).

## Genel ozellikler — KESIN EKLENECEK

Kullanici onayladi, hepsi eklenecek. Not: **belge/dosya gonderme
listeden cikarildi, istenmiyor.**

**Mesajlasma:**

- [ ] **Sesli mesaj (voice note)** — henuz yapilmadi.
- [x] **Mesaja yanit verme (reply/quote)** — tamamlandi (sunucu:
  reply_to_id + reply ozeti; istemci: on izleme cubugu + balon ustunde
  ozet). commit: "Mesaja yanit verme ve mesaj duzenleme".
- [x] **Mesaj iletme (forward)** — tamamlandi (mesaj menusunde "Ilet",
  sohbet secme penceresi). commit: "Mesaj iletme (forward)".
- [x] **Emoji reaksiyon** — tamamlandi (naber_reactions tablosu,
  toggle/degistir, balon altinda rozetler). commit: "Emoji reaksiyonu".
- [x] **Mesaj duzenleme** — tamamlandi (15 dakika pencere, "duzenlendi"
  etiketi). commit: "Mesaja yanit verme ve mesaj duzenleme".
- [x] **Uygulama ici kamera** — tamamlandi (FileProvider + TakePicture,
  ek menude "Kamera"). commit: "Uygulama ici kamera".

**Sohbet yonetimi:**

- [x] **Sohbeti sabitleme (pin)** — tamamlandi (members.pinned_at,
  sabitlenenler listede ustte). commit: "Sohbeti sabitleme ve sureli
  sessize alma".
- [x] **Sureli sessize alma** — tamamlandi (notify_muted_until: 8 saat,
  1 hafta, suresiz). commit: "Sohbeti sabitleme ve sureli sessize alma".
- [x] **Global arama** — tamamlandi (sohbet listesindeki arama kutusu
  artik mesaj metinlerinde de ariyor; arama cihazda yapildigi icin
  cevrimdisi da calisir). Not: yalnizca cihazda saklanan gecmis
  (sohbet basina son 300 mesaj) taranir.
- [x] **Kaybolan mesajlar — ISTEGE BAGLI (opt-in)** — tamamlandi
  (conversations.disappear_seconds; kapali/1 saat/24 saat/7 gun/30 gun.
  Varsayilan kapali. Suresi dolan mesajlar sohbet her acildiginda
  sunucudan silinir, cihazda da gizlenir).

**Grup ozellikleri:**

- [x] **@bahsetme (mention)** — tamamlandi (grupta "@" yazinca uye
  onerisi cikar, mesajda vurgulanir; bahsedilen kisi sohbeti sessize
  almis olsa bile bildirim alir. "@herkes" grubun tamamini kapsar).
- [x] **Grup davet KODU + "Kod ile grup bul"** — tamamlandi (8 haneli
  kod, yoneticinin yenileyebilmesi). commit: "Grup davet kodu".
- [x] **Anket (poll)** — tamamlandi (ek menuden olusturulur; 2-6
  secenek, tek ya da coklu secim. Oylar mesaj balonunda cubuk ve
  yuzde olarak gorunur; ayni secenege tekrar basmak oyu geri ceker).

**Gizlilik / guvenlik:**

- [x] **Uygulama kilidi** — tamamlandi (PIN: tuza dayali SHA-256 ozet,
  arka plana alininca ve uygulama sifirdan acilinca kilitlenir).
  Parmak izi sonraya birakildi.
- [x] **Kullanici engelleme** — tamamlandi (naber_blocks tablosu;
  engel karsilikli etki eder: iki taraf da mesaj gonderemez, arayamaz,
  durtemez. Ortak gruplar etkilenmez. Profil ekranindan acilir).
- [x] **Son gorulme / okundu bilgisini gizleme secenegi** — tamamlandi
  (profildeki iki anahtar). Ikisi de simetrik: gizleyen kisi
  baskalarininkini de goremez.

**Kisisellestirme:**

- [x] **Acik tema secenegi** — tamamlandi (profilde "Koyu tema"
  anahtari; tercih cihazda saklanir). NOT: gercek cihazda gorsel
  olarak denenmedi, renkler gozden gecirilmeli.
- [x] **Sohbet arka plani / renk temasi** — tamamlandi (sohbet
  menusunden 6 hazir arka plandan biri secilir; secim sohbete ozel ve
  yalnizca o cihazda saklanir. Koyu/acik tema icin ayri renkler).
- [x] **Konum paylasma** — tamamlandi (ek menude "Konum gonder";
  mesaj turu "location", govdesi "enlem,boylam". Balona dokununca
  telefonun harita uygulamasinda aciliyor).

## Ek ozellik onerileri (henuz onaylanmadi, degerlendirilecek)

Daha once onerilenlere ek, yine kucuk guvenilir grup (~10 kisi)
mantigina uygun, karmasiklastirmayan fikirler. Degisiklik yok, aynen
duruyor:

- **Mesaj yildizlama (starred messages)**
- **Sohbeti arsivleme**
- **Grupta sabitlenmis mesaj/duyuru**
- **Kisiye ozel takma ad (nickname)**
- **Gorunmez mod** (simetrik gizlilik)
- **"Yaziyor" gostergesini kapatma secenegi**
- **GIF/sticker destegi**
- **Medya galerisi gorunumu**
- **Canli konum paylasimi**
- **Sesli mesaj hiz kontrolu**
- **"Duyuru modu" grup ayari**
- **Favori/sabit kisiler**
- **Dogum gunu alani + hatirlatma**
- **Ana ekran widget'i**

## Durum ozeti (en son guncelleme: Persembe 05:00 tetikleyicisi)

Tamamlanan: hata duzeltmesi (1/1), durtme ozelligi, optimizasyonlar
(9/15 yapildi, 2 kasitli atlandi/ertelendi, 4 kaldi), genel ozellikler
(18/19 yapildi: yanitla, duzenle, reaksiyon, iletme, kamera, sabitleme,
sureli sessize alma, davet kodu, uygulama kilidi, global arama,
kaybolan mesajlar, engelleme, bahsetme, gizlilik secenekleri,
acik tema, sohbet arka plani, konum paylasma, anket).

Eklenti surumu: 1.17.0. Toplam test: 253, hepsi geciyor.

Kalan buyuk is: sesli mesaj, lazy medya indirme, delta
senkronizasyon, WorkManager.

## Kodlamaya gecince siralama

1. [x] Tam ekran gorsel siyah ekran duzeltmesi.
2. [x] Durtme ozelligi (sunucu + istemci).
3. Onaylanan optimizasyonlar — 9/15 yapildi, kalanlar surdurulecek.
4. Onaylanan genel ozellikler — 18/19 yapildi, kalanlar surdurulecek.
5. Ek oneriler arasindan kullanicinin sectikleri (henuz baslanmadi).
