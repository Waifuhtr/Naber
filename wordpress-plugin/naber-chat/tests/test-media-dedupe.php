<?php
/**
 * Ayni dosyayi iki kez yuklememe (ozet) ve gorsel on izlemesi testleri.
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-media-dedupe.php
 */

require_once __DIR__ . '/bootstrap.php';

// --------------------------------------------------------------------
Naber_Tests::group( 'Dosya ozeti (SHA-256) dogrulama' );

$gecerli = hash( 'sha256', 'naber' );
Naber_Tests::equals( $gecerli, Naber_REST::sanitize_hash( $gecerli ), 'Gecerli ozet aynen kabul ediliyor' );
Naber_Tests::equals( $gecerli, Naber_REST::sanitize_hash( strtoupper( $gecerli ) ), 'Buyuk harfli ozet kucultuluyor' );
Naber_Tests::equals( $gecerli, Naber_REST::sanitize_hash( '  ' . $gecerli . "\n" ), 'Bastaki sondaki bosluklar atiliyor' );

Naber_Tests::equals( '', Naber_REST::sanitize_hash( '' ), 'Bos deger reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_hash( null ), 'Null reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_hash( substr( $gecerli, 0, 63 ) ), 'Kisa ozet reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_hash( $gecerli . 'a' ), 'Uzun ozet reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_hash( str_repeat( 'z', 64 ) ), 'Onaltilik olmayan karakter reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_hash( "' OR 1=1 --" ), 'SQL denemesi reddediliyor' );

Naber_Tests::ok( null === Naber_Media::find_by_hash( '' ), 'Bos ozet veritabanina hic gitmiyor' );
Naber_Tests::ok( null === Naber_Media::find_by_hash( 'kisa' ), 'Gecersiz ozet veritabanina hic gitmiyor' );
Naber_Tests::ok( null === Naber_Media::find_by_hash( str_repeat( 'g', 64 ) ), 'Onaltilik olmayan 64 karakter reddediliyor' );

// Ayni icerik her zaman ayni ozeti vermeli; farkli icerik farkli ozet.
Naber_Tests::equals( hash( 'sha256', 'ayni' ), hash( 'sha256', 'ayni' ), 'Ayni icerik ayni ozeti veriyor' );
Naber_Tests::ok( hash( 'sha256', 'ayni' ) !== hash( 'sha256', 'baska' ), 'Farkli icerik farkli ozet veriyor' );

// --------------------------------------------------------------------
Naber_Tests::group( 'Gorsel on izlemesi (base64)' );

$kucuk = base64_encode( 'sahte-jpeg-verisi' );
Naber_Tests::equals( $kucuk, Naber_REST::sanitize_preview( $kucuk ), 'Gecerli base64 aynen kabul ediliyor' );
Naber_Tests::equals(
	$kucuk,
	Naber_REST::sanitize_preview( 'data:image/jpeg;base64,' . $kucuk ),
	'data: oneki ayiklaniyor'
);
Naber_Tests::equals(
	$kucuk,
	Naber_REST::sanitize_preview( "  " . chunk_split( $kucuk, 8, "\n" ) ),
	'Satir sonlari ve bosluklar temizleniyor'
);

Naber_Tests::equals( '', Naber_REST::sanitize_preview( '' ), 'Bos on izleme bos donuyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_preview( null ), 'Null on izleme bos donuyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_preview( '<script>alert(1)</script>' ), 'HTML icerik reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_preview( 'abc$%&*' ), 'Base64 disi karakterler reddediliyor' );

// Cok buyuk on izleme mesaj satirini sisirmemeli.
$buyuk = base64_encode( str_repeat( 'x', Naber_REST::MAX_PREVIEW_CHARS ) );
Naber_Tests::ok( strlen( $buyuk ) > Naber_REST::MAX_PREVIEW_CHARS, 'Test verisi gercekten sinirin ustunde' );
Naber_Tests::equals( '', Naber_REST::sanitize_preview( $buyuk ), 'Sinirin ustundeki on izleme reddediliyor' );

// Sinirin tam altindaki deger gecmeli.
$sinirda = str_repeat( 'A', Naber_REST::MAX_PREVIEW_CHARS );
Naber_Tests::equals( $sinirda, Naber_REST::sanitize_preview( $sinirda ), 'Sinira esit uzunluk kabul ediliyor' );

// Base64 dolgu karakteri (=) kabul edilmeli.
Naber_Tests::equals( 'QQ==', Naber_REST::sanitize_preview( 'QQ==' ), 'Dolgu karakterli base64 kabul ediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_preview( 'QQ==AA' ), 'Dolgudan sonra veri gelirse reddediliyor' );

Naber_Tests::summary();
