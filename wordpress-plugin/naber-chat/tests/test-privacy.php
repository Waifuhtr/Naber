<?php
/**
 * Gizlilik kurallari testleri (saf fonksiyon).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-privacy.php
 *
 * Not: ayarin kaydedilmesi ve sorgulara yansimasi veritabani gerektirir,
 * burada yalnizca simetrik gorunurluk kurali test edilir.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Son gorulme gorunurlugu' );

Naber_Tests::ok(
	Naber_Auth::can_see_last_seen( false, false ),
	'Kimse gizlemiyorsa son gorulme gorunuyor'
);

Naber_Tests::ok(
	! Naber_Auth::can_see_last_seen( false, true ),
	'Karsi taraf gizliyorsa gorunmuyor'
);

// Simetri: gizleyen kisi baskalarininkini de goremez, yoksa herkes
// gizler ama herkesi gormeye devam ederdi.
Naber_Tests::ok(
	! Naber_Auth::can_see_last_seen( true, false ),
	'Kendi gizleyen baskasininkini goremiyor'
);

Naber_Tests::ok(
	! Naber_Auth::can_see_last_seen( true, true ),
	'Ikisi de gizliyorsa gorunmuyor'
);

Naber_Tests::group( 'Okundu bilgisi gorunurlugu' );

Naber_Tests::ok( Naber_Auth::can_see_read( false, false ), 'Kimse gizlemiyorsa okundu bilgisi gorunuyor' );
Naber_Tests::ok( ! Naber_Auth::can_see_read( false, true ), 'Karsi taraf gizliyorsa gorunmuyor' );
Naber_Tests::ok( ! Naber_Auth::can_see_read( true, false ), 'Kendi gizleyen baskasininkini goremiyor' );
Naber_Tests::ok( ! Naber_Auth::can_see_read( true, true ), 'Ikisi de gizliyorsa gorunmuyor' );

Naber_Tests::summary();
