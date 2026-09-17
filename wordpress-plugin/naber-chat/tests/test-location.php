<?php
/**
 * Konum mesaji dogrulama testleri (saf fonksiyon).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-location.php
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Gecerli konumlar' );

Naber_Tests::equals(
	'41.015137,28.979530',
	Naber_REST::sanitize_location( '41.015137,28.97953' ),
	'Istanbul konumu 6 haneye tamamlanarak kabul ediliyor'
);

Naber_Tests::equals(
	'0.000000,0.000000',
	Naber_REST::sanitize_location( '0,0' ),
	'Sifir konumu gecerli'
);

Naber_Tests::equals(
	'-33.868800,151.209300',
	Naber_REST::sanitize_location( ' -33.8688 , 151.2093 ' ),
	'Bosluklar temizleniyor, negatif enlem kabul ediliyor'
);

Naber_Tests::equals(
	'-90.000000,-180.000000',
	Naber_REST::sanitize_location( '-90,-180' ),
	'Sinir degerler kabul ediliyor'
);

Naber_Tests::group( 'Gecersiz konumlar' );

Naber_Tests::equals( '', Naber_REST::sanitize_location( '' ), 'Bos deger reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_location( '41.0' ), 'Tek sayi reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_location( '41.0,28.9,5' ), 'Ucuncu deger reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_location( 'enlem,boylam' ), 'Metin reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_location( '91,28' ), 'Enlem 90 dereceyi asamaz' );
Naber_Tests::equals( '', Naber_REST::sanitize_location( '41,181' ), 'Boylam 180 dereceyi asamaz' );

// Konum govdesine kod kacirilmasin diye sayidan baska bir sey kabul
// edilmiyor; balonda adres olusturulurken bu deger dogrudan kullaniliyor.
Naber_Tests::equals(
	'',
	Naber_REST::sanitize_location( '41.0,28.9"><script>' ),
	'Sayi disi karakter iceren deger reddediliyor'
);

Naber_Tests::summary();
