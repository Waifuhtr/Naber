<?php
/**
 * Anket testleri (saf fonksiyonlar).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-polls.php
 *
 * Not: oy verme ve sonuc sayimi veritabani gerektirdigi icin burada
 * test edilmez; secenek temizligi ve yuzde hesabi test edilir.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Secenek temizligi' );

Naber_Tests::equals(
	array( 'Pazartesi', 'Sali' ),
	Naber_Polls::sanitize_options( array( 'Pazartesi', 'Sali' ) ),
	'Gecerli secenekler oldugu gibi kaliyor'
);

Naber_Tests::equals(
	array( 'Pazartesi', 'Sali' ),
	Naber_Polls::sanitize_options( array( ' Pazartesi ', '', '   ', 'Sali' ) ),
	'Bos secenekler atiliyor, bosluklar kirpiliyor'
);

// Ayni secenek iki kez olursa oylar bolunur ve kullanici hangisine
// bastigini ayirt edemez.
Naber_Tests::equals(
	array( 'Evet', 'Hayir' ),
	Naber_Polls::sanitize_options( array( 'Evet', 'Hayir', 'Evet' ) ),
	'Tekrarlayan secenek tekilleniyor'
);

Naber_Tests::equals(
	array(),
	Naber_Polls::sanitize_options( array( 'Tek secenek' ) ),
	'Tek secenekli anket reddediliyor'
);

Naber_Tests::equals(
	array(),
	Naber_Polls::sanitize_options( array() ),
	'Bos liste reddediliyor'
);

Naber_Tests::equals(
	array(),
	Naber_Polls::sanitize_options( 'metin' ),
	'Dizi olmayan deger reddediliyor'
);

$many = Naber_Polls::sanitize_options( array( 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h' ) );
Naber_Tests::equals( Naber_Polls::MAX_OPTIONS, count( $many ), 'Secenek sayisi ust sinirla kirpiliyor' );

$long = Naber_Polls::sanitize_options( array( str_repeat( 'x', 200 ), 'Ikinci' ) );
Naber_Tests::equals(
	Naber_Polls::MAX_OPTION_LENGTH,
	mb_strlen( $long[0] ),
	'Uzun secenek metni kirpiliyor'
);

Naber_Tests::equals(
	array( 'Kalin yazi', 'Ikinci' ),
	Naber_Polls::sanitize_options( array( '<b>Kalin yazi</b>', 'Ikinci' ) ),
	'Etiketler temizleniyor'
);

Naber_Tests::group( 'Soru temizligi' );

Naber_Tests::equals( 'Nerede bulusalim?', Naber_Polls::sanitize_question( '  Nerede bulusalim?  ' ), 'Bosluklar kirpiliyor' );
Naber_Tests::equals( '', Naber_Polls::sanitize_question( '   ' ), 'Bos soru bos kaliyor' );
Naber_Tests::equals(
	Naber_Polls::MAX_QUESTION_LENGTH,
	mb_strlen( Naber_Polls::sanitize_question( str_repeat( 'a', 400 ) ) ),
	'Uzun soru kirpiliyor'
);

Naber_Tests::group( 'Yuzde hesabi' );

Naber_Tests::equals( 50, Naber_Polls::percent( 2, 4 ), 'Yarisi yuzde 50' );
Naber_Tests::equals( 100, Naber_Polls::percent( 3, 3 ), 'Tamami yuzde 100' );
Naber_Tests::equals( 0, Naber_Polls::percent( 0, 5 ), 'Oy yoksa yuzde 0' );
// Hic oy yokken bolme yapilmamali.
Naber_Tests::equals( 0, Naber_Polls::percent( 0, 0 ), 'Toplam sifirken sifira bolme olmuyor' );
Naber_Tests::equals( 33, Naber_Polls::percent( 1, 3 ), 'Ucte bir yuvarlanarak 33 oluyor' );

Naber_Tests::summary();
