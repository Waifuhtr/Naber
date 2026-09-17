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
12. [x] **Lazy/kademeli medya indirme** — tamamlandi: gorseller
    varsayilan olarak yalnizca wifi'de kendiliginden iniyor.
    Profilde "Her zaman / Yalnizca wifi / Elle indir" secenegi var;
    inmemis gorselin yerinde bulanik on izleme ve indirme dugmesi
    duruyor.
13. [x] **Delta/fark tabanli senkronizasyon** — tamamlandi: `/chats`
    artik "since" kabul ediyor, yalnizca degisen sohbetleri donuyor.
    Ekran ilk acilista tam liste alir, sonraki tazelemeler delta olur.
    Sinir: sabitleme/sessize alma sohbetin updated_at degerini
    degistirmedigi icin (yoksa sabitleyince sohbet listede zipliyordu)
    baska cihazdan yapilan bu degisiklikler ancak tam yenilemede
    gorunur.
14. [x] **Batarya optimizasyonu icin WorkManager** — tamamlandi:
    dosya temizligi gunde bir kez (pil azken calismaz) ve
    gonderilemeyen metin mesajlari ag geri gelince uygulama acik
    olmasa da gonderiliyor.
15. [ ] **Sikistirilmis JSON / gzip** — sunucu/hosting ayari,
    kullanicinin WordPress barindirmasinda kontrol etmesi gerekiyor
    (kod degisikligi degil).

## Genel ozellikler — KESIN EKLENECEK

Kullanici onayladi, hepsi eklenecek. Not: **belge/dosya gonderme
listeden cikarildi, istenmiyor.**

**Mesajlasma:**

- [x] **Sesli mesaj (voice note)** — tamamlandi (yazi alani bosken
  gonder dugmesi mikrofona doner; kayit AAC/m4a olarak yuklenir,
  balonda cal/durdur ve sure gosterilir). NOT: gercek cihazda
  denenmedi, kayit/calma donanim gerektiriyor.
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

## Tasarim degisiklikleri — KESIN YAPILACAK (henuz kodlanmadi)

Kullanici WhatsApp ekran goruntuleri paylasti (17 Eylul), su ikisi
onaylandi:

1. [x] **Mesaj eylem menusu — WhatsApp tarzi yeniden tasarim.** Mesaja
   uzun basinca cikan menu tek govde halinde olsun: ustte yatay emoji
   reaksiyon seridi, altinda yuvarlak koseli koyu kart icinde Yanitla /
   Duzenle / Ilet / Kopyala / Bilgi / Kendimden Sil / Herkesten Sil
   siralaniyor. Menu maddeleri zaten var (bkz. yukaridaki tamamlanan
   ozellikler), degisen yalnizca gorunum/duzen: reaksiyon seridiyle
   menu ayni kart icinde birlesiyor. WhatsApp'a ozgu "Guvenlik kodunu
   dogrula" ve "Cevir" secenekleri bizde anlamsiz (uctan uca sifreleme
   dogrulamasi ve ceviri ozelligi yok), eklenmeyecek.
2. [x] **Mesaji saga kaydirarak hizli yanitlama (swipe-to-reply).**
   Uzun basip menuden "Yanitla" secmek yerine, mesaj balonunu saga
   dogru kaydirinca otomatik yanit moduna gecilsin (WhatsApp'taki
   gibi) — uzun basip menuyu acmaktan daha pratik. Uzun basma
   menusundeki "Yanitla" secenegi de kalir, kaydirma ek bir kisayol
   olur.
3. [x] **Tam emoji secici (uzun basma menusundeki reaksiyon seridinin
   genisletilmis hali).** Su an sabit 6 emoji var (QUICK_REACTIONS).
   Bunun yaninda/ucunda bir "+" ile acilan, arama kutusu + kategoriler
   + "sik kullanilanlar" iceren tam emoji secici eklenecek — kullanici
   isterse 6 hazir emojiden birine hizlica basar, isterse "+" ile tum
   emoji setine erisir.
4. [ ] **Grup ayarlari ekrani (GroupInfoScreen) WhatsApp tarzi
   genisletme.** Kullanicinin paylastigi 4 grup ayarlari gorseline
   gore eklenecekler:
   - Grup fotografini degistirme (su an yalnizca goruntuleniyor,
     admin icin tiklanip degistirilebilir hale gelecek).
   - Grup izinleri (yalnizca admin degistirebilir): "Mesaj
     gonderebilir" (Herkes / Sadece yoneticiler), "Grup bilgisini
     duzenleyebilir" (Herkes / Sadece yoneticiler), "Uye ekleyebilir"
     (Herkes / Sadece yoneticiler).
   - Davet kodunun yaninda QR kod gosterimi (mevcut kod + kopyalama
     duruyor, QR gorsel bir ek secenek olarak eklenir).
   - **Grubu sil** (yalnizca kurucu/owner): "Gruptan ayril"dan farkli,
     tum grubu ve mesajlarini herkes icin kalici siler. Su an yalnizca
     kendi cikisimiz var, grubu tamamen kapatma yok.

   Bize gerekmeyenler, eklenmeyecek: "Guvenlik kodu / uctan uca
   sifreleme bilgisi" (uygulamada E2E sifreleme yok, gostermek yanlis
   guven verir), "Grubu sikayet et" (~10 kisilik guvenilir kapali
   grup, WhatsApp'in genel kullanici kitlesi icin anlamli bir ozellik),
   "Topluluklar" / "Yayin listesi" gibi buyuk olcekli WhatsApp
   ozellikleri (proje kapsami disi).

## Arama deneyimi — KESIN YAPILACAK (henuz kodlanmadi)

Kullanicinin 17 Eylul istegi (Discord benzeri):

1. [x] **Arka plan baloncugu (overlay).** Aramadayken uygulama arka
   plana alininca ekranda kucuk bir baloncuk kalsin; oradan ses paneli
   yonetilebilsin.
   - Kendini sessize alma (mikrofonu kapat) — zaten var, baloncuga
     tasinacak.
   - **Sagirlastirma (deafen)**: acik oldugu surece karsi taraflarin
     sesi hic duyulmaz, kapatinca yeniden duyulur. Yeni ozellik.
   - Baloncukta konusanlarin profil fotograflari gorunsun; kim
     konusuyorsa fotografi hafifce yanip sonsun.
   - Baloncuk bir sure dokunulmazsa kendiliginden saydamlasip gizlensin;
     tekrar dokunulana kadar one cikmasin.
   - Not: Android'de bu SYSTEM_ALERT_WINDOW (diger uygulamalarin
     uzerinde gosterme) izni gerektirir; izin istenecek ve verilmezse
     ozellik kapali kalacak.
2. [x] **Grup aramasina katilma dugmesi.** Grup aramasi surerken grup
   sohbetinde baslik yaninda "Katil" dugmesi gorunsun; arama bitene
   kadar dursun.
3. [x] **Aramadaki kisilerin seridi.** Basligin altinda kucuk bir
   seritte aramada olanlarin minik profil fotograflari gorunsun (isim
   yok, yalnizca fotograflar).

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
- **WhatsApp tarzi cikartma (sticker) paketi** (GIF/sticker destegi
  fikri somutlastirildi)
- **Discord tarzi ozel emoji** (statik + hareketli/animasyonlu; mesaj
  ve reaksiyonlarda kullanilabilir)
- **Medya galerisi gorunumu**
- **Canli konum paylasimi**
- **Sesli mesaj hiz kontrolu**
- **"Duyuru modu" grup ayari**
- **Favori/sabit kisiler**
- **Dogum gunu alani + hatirlatma**
- **Ana ekran widget'i**

## Grupla ilgili ek oneriler (henuz onaylanmadi, degerlendirilecek)

Kullanici grup gelistirmesi sirasinda baska onerilerim olup olmadigini
sordu, asagidakiler eklendi (Ek ozellik onerileri listesindeki
"Grupta sabitlenmis mesaj/duyuru" ve "Duyuru modu" ile ortusmeyenler):

- **Uyelik onayi**: davet koduyla katilmak isteyen kisi once bekleme
  listesine dusup yoneticinin onayindan sonra uye olsun (su an kod
  bilen herkes dogrudan katiliyor).
- **Katilma tarihi gosterimi**: uye satirinda "X tarihinde katildi"
  bilgisi (joined_at zaten veritabaninda var, sadece goruntuleme
  eklenir).
- **Toplu susturma suresi**: "Sureli sessize alma" zaten sohbet
  ekraninda var; grup ayarlarindan da kisayol olarak erisilebilir.
- **Grup istatistigi**: en aktif uye, toplam mesaj sayisi gibi kucuk
  bir ozet (dusuk oncelik, eglence amacli).

**Ikinci tur oneriler:**

- [ ] **ONEMLI — Sahiplik devri + otomatik yedek atama.** Su an
  owner rolu hic el degistirmiyor: kurucu gruptan ayrilirsa grup
  sahipsiz kalir. Kurucu "Sahipligi devret" diyip owner rolunu elle
  baska bir uyeye birakabilmeli; bunu yapmadan ayrilirsa sahiplik
  kalan uyelerden rastgele birine otomatik atansin (grup asla sahipsiz
  kalmasin). Gercek bir eksiklik, oncelik verilmeli.
- [ ] **ONEMLI — Sistem mesajlari.** "X gruba katildi", "Y grubu
  birakti", "Z cikarildi", "Grup adi/fotografi degisti" gibi olaylar
  su an sohbet akisinda hic gorunmuyor (WhatsApp'in standart ozelligi).
  Gri bilgi balonu olarak akisa eklenmeli.
- [ ] **Sahibi cikarmaya calisana saka savunmasi.** Rol hiyerarsisi
  zaten var (`can_act_on()` bir yonetici/yetkilinin sahibi
  cikarmasina izin vermiyor). Biri bunu zorlarsa: once sahibin
  yonetici etiketi bir anlik kaybolur (saldirgan basardigini sansin —
  "zafer" hissi), hemen ardindan saldirganin kendisi gruptan atilir ve
  ekraninda komik bir mesaj cikar; 10 saniye sonra hem sahibin etiketi
  hem de saldirganin uyeligi otomatik geri gelir. Gercek bir guvenlik
  onlemi degil, kucuk arkadas grubu icin eglence amacli bir "tuzak".
- [ ] **Ayrintili yetki sistemi.** Su an yalnizca owner/admin/member
  uc sabit rol var. Sahip birine yetki verirken artik hangi yetkilerin
  verildigini tek tek secebilmeli: ornegin "uye cikarabilir" ve
  "baskasinin mesajini silebilir" ayri ayri acilip kapatilabilsin.
  Boylece tam admin yapmadan yalnizca belirli bir yetkiyi (ornegin
  sadece mesaj silme) veren "yetkili uye" tanimlanabilir. (Mesaj
  silme yetkisi su an zaten admin/owner icin var — ikisi de herkesin
  mesajini herkesten silebiliyor; bu genisletmeyle sade bir "yetkili"
  uyeye de ayrica verilebilecek.)
- **Davet kodunu harici paylasma**: kopyalamanin yanina Android
  paylasim sayfasini (Intent.ACTION_SEND) acan bir "Paylas" dugmesi.
- [x] **Sabitlenmis mesaj — ayri ekran degil, sohbetin en ustune
  sabitleme.** Onceki "ayri ekranda listeleme" fikrinden vazgecildi:
  sabitlenen mesaj normal akistan cikmaz, sohbetin en ustune yapisik
  durur ve yaninda kucuk bir pano igne (📌) isareti gorunur; dokununca
  asil mesaja atlanir.
- **"@herkes" kisitlamasi**: grup izinlerine ek olarak, "@herkes"
  bahsetmesini yalnizca yoneticilerin kullanabilmesi secenegi.

## Durum ozeti (en son guncelleme: Persembe 05:00 tetikleyicisi)

Tamamlanan: hata duzeltmesi (1/1), durtme ozelligi, optimizasyonlar
(12/15 yapildi, 2 kasitli atlandi/ertelendi, 1 kaldi), genel ozellikler
(19/19 yapildi: yanitla, duzenle, reaksiyon, iletme, kamera, sabitleme,
sureli sessize alma, davet kodu, uygulama kilidi, global arama,
kaybolan mesajlar, engelleme, bahsetme, gizlilik secenekleri,
acik tema, sohbet arka plani, konum paylasma, anket, sesli mesaj).

Eklenti surumu: 1.19.0. Toplam test: 253, hepsi geciyor.

Kalan is: onaylanan butun ozellikler ve optimizasyonlar bitti.
Geriye kod degisikligi gerektirmeyen/bilerek ertelenen maddeler
(gzip sunucu ayari, ICE toplu gonderme) ve yeni onaylanan bir
tasarim isi kaldi: mesaj eylem menusunun WhatsApp tarzi yeniden
tasarimi + saga kaydirarak yanitlama (yukaridaki "Tasarim
degisiklikleri" bolumu, henuz kodlanmadi).

## Kodlamaya gecince siralama

1. [x] Tam ekran gorsel siyah ekran duzeltmesi.
2. [x] Durtme ozelligi (sunucu + istemci).
3. [x] Onaylanan optimizasyonlar — 12/15 yapildi; kalan 3 madde bilerek
   birakildi (ayrintilar yukarida).
4. [x] Onaylanan genel ozellikler — 19/19 tamamlandi.
5. [x] Tasarim degisiklikleri — mesaj eylem menusu yeniden tasarimi,
   saga kaydirarak yanitlama ve tam emoji secici tamamlandi. Kalan:
   grup ayarlari ekraninin genisletilmesi.
6. Ek oneriler arasindan kullanicinin sectikleri (henuz baslanmadi).
