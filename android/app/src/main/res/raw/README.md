# Ses dosyalari

Bu klasore asagidaki dosyalari koyun. Dosya adlari **birebir** boyle olmali
(kucuk harf, rakam ve alt cizgi; tire veya bosluk olmaz):

| Dosya | Ne zaman calar | Onerilen |
| --- | --- | --- |
| `message_sent.mp3` | Mesaj gonderildiginde | 0.2 - 0.6 sn, kisa "tik" |
| `message_received.mp3` | Yeni mesaj geldiginde | 0.3 - 0.8 sn |
| `call_ringtone.mp3` | Gelen arama calarken | 5 - 30 sn, basa donunce dogal donen bir kayit |

Bicim: `.mp3`, `.ogg` veya `.wav` kullanilabilir; uzantisi degisirse
kod degisikligi gerekmez, dosya adi ayni kalsin (`message_sent.ogg` gibi).

Dosyalar yoksa uygulama yine derlenir ve calisir; yalnizca o ses calmaz.
