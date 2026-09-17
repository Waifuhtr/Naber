<?php
/**
 * Kaybolan mesajlar: sure dogrulama testleri (saf fonksiyon).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-disappearing.php
 *
 * Not: mesajlari gercekten silen purge_disappeared() veritabani
 * gerektirdigi icin burada test edilmez.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Kaybolan mesaj suresi dogrulama' );

Naber_Tests::equals( 0, Naber_REST::sanitize_disappear_seconds( 0 ), 'Sifir (kapali) kabul ediliyor' );
Naber_Tests::equals( 3600, Naber_REST::sanitize_disappear_seconds( 3600 ), '1 saat kabul ediliyor' );
Naber_Tests::equals( 86400, Naber_REST::sanitize_disappear_seconds( 86400 ), '24 saat kabul ediliyor' );
Naber_Tests::equals( 604800, Naber_REST::sanitize_disappear_seconds( 604800 ), '7 gun kabul ediliyor' );
Naber_Tests::equals( 2592000, Naber_REST::sanitize_disappear_seconds( 2592000 ), '30 gun kabul ediliyor' );

Naber_Tests::group( 'Listede olmayan degerler kapali sayiliyor' );

Naber_Tests::equals( 0, Naber_REST::sanitize_disappear_seconds( 1 ), '1 saniye reddediliyor (veri aninda yok olmasin)' );
Naber_Tests::equals( 0, Naber_REST::sanitize_disappear_seconds( 7200 ), 'Listede olmayan sure reddediliyor' );
Naber_Tests::equals( 0, Naber_REST::sanitize_disappear_seconds( -604800 ), 'Negatif sure reddediliyor' );
Naber_Tests::equals( 0, Naber_REST::sanitize_disappear_seconds( 'abc' ), 'Metin reddediliyor' );
Naber_Tests::equals( 0, Naber_REST::sanitize_disappear_seconds( null ), 'Bos deger kapali sayiliyor' );

// Metin olarak gelen sayi kabul edilmeli: REST parametreleri metin gelir.
Naber_Tests::equals( 86400, Naber_REST::sanitize_disappear_seconds( '86400' ), 'Metin bicimindeki sayi kabul ediliyor' );

Naber_Tests::summary();
