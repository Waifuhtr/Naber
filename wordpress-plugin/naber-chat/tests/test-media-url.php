<?php
/**
 * S3 uyumlu imzali adres (AWS Signature V4) testleri.
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-media-url.php
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'AWS Signature V4 imzasi' );

// AWS belgelerindeki resmi ornek: GET examplebucket/test.txt, 24 saat gecerli.
$url = Naber_B2::presign_url(
	'examplebucket.s3.amazonaws.com',
	'/test.txt',
	'AKIAIOSFODNN7EXAMPLE',
	'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
	'us-east-1',
	86400,
	strtotime( '2013-05-24 00:00:00 UTC' )
);

preg_match( '/X-Amz-Signature=([a-f0-9]+)/', $url, $matches );
Naber_Tests::equals(
	'aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404',
	isset( $matches[1] ) ? $matches[1] : '',
	'AWS belgelerindeki referans imza ile birebir ayni'
);

Naber_Tests::ok( false !== strpos( $url, 'X-Amz-Algorithm=AWS4-HMAC-SHA256' ), 'Algoritma parametresi var' );
Naber_Tests::ok( false !== strpos( $url, 'X-Amz-Expires=86400' ), 'Gecerlilik suresi adrese yaziliyor' );
Naber_Tests::ok( false !== strpos( $url, 'X-Amz-SignedHeaders=host' ), 'Imzalanan baslik host' );
Naber_Tests::ok( 0 === strpos( $url, 'https://examplebucket.s3.amazonaws.com/test.txt?' ), 'Adres dogru kuruluyor' );

// Ayni girdi ayni imzayi vermeli (belirlenimci).
$again = Naber_B2::presign_url(
	'examplebucket.s3.amazonaws.com',
	'/test.txt',
	'AKIAIOSFODNN7EXAMPLE',
	'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
	'us-east-1',
	86400,
	strtotime( '2013-05-24 00:00:00 UTC' )
);
Naber_Tests::equals( $url, $again, 'Ayni girdi ayni adresi uretiyor' );

// Farkli dosya farkli imza vermeli.
$other = Naber_B2::presign_url(
	'examplebucket.s3.amazonaws.com',
	'/baska.txt',
	'AKIAIOSFODNN7EXAMPLE',
	'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
	'us-east-1',
	86400,
	strtotime( '2013-05-24 00:00:00 UTC' )
);
Naber_Tests::ok( $other !== $url, 'Farkli dosya farkli imza uretiyor' );

Naber_Tests::group( 'Yol kodlamasi ve bolge' );

$path_url = Naber_B2::presign_url(
	's3.us-west-004.backblazeb2.com',
	'/naber-medya/naber/u7/2026/09/gorsel adi.jpg',
	'0035a1b2c3d4e5f0000000001',
	'K003abcdefghijklmnopqrstuvwxyz1',
	'us-west-004',
	3600,
	strtotime( '2026-09-15 12:00:00 UTC' )
);
Naber_Tests::ok( false !== strpos( $path_url, '/naber-medya/naber/u7/2026/09/gorsel%20adi.jpg' ), 'Bosluk %20 olarak kodlaniyor, egik cizgiler korunuyor' );
Naber_Tests::ok( false === strpos( $path_url, '?Authorization=' ), 'Friendly adres bicimi kullanilmiyor' );

Naber_Tests::equals( 'us-west-004', Naber_B2::region_from_host( 's3.us-west-004.backblazeb2.com' ), 'Bolge S3 adresinden cikariliyor' );
Naber_Tests::equals( 'eu-central-003', Naber_B2::region_from_host( 'https://s3.eu-central-003.backblazeb2.com' ), 'https onekli adresten de cikariliyor' );

// Sure sinirlari
$clamped = Naber_B2::presign_url( 'h.example.com', '/a', 'k', 's', 'r', 99999999, 1700000000 );
Naber_Tests::ok( false !== strpos( $clamped, 'X-Amz-Expires=604800' ), 'Azami sure 7 gune sinirlaniyor' );
$short = Naber_B2::presign_url( 'h.example.com', '/a', 'k', 's', 'r', 5, 1700000000 );
Naber_Tests::ok( false !== strpos( $short, 'X-Amz-Expires=60' ), 'Asgari sure 60 saniyeye yukseltiliyor' );

exit( Naber_Tests::summary() );
