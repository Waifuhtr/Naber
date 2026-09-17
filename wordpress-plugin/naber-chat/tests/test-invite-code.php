<?php
/**
 * Grup davet kodu uretimi testleri (saf fonksiyon).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-invite-code.php
 *
 * Not: veritabani gerektiren kisimlar (assign_invite_code, join_by_code)
 * burada test edilmez; yalnizca random_invite_code() saf mantigi test edilir.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Davet kodu uretimi' );

$code = Naber_Chat_Repo::random_invite_code();
Naber_Tests::equals( 6, strlen( $code ), 'Varsayilan uzunluk 6 karakter' );

$allowed = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
$only_allowed = true;
for ( $i = 0; $i < strlen( $code ); $i++ ) {
	if ( false === strpos( $allowed, $code[ $i ] ) ) {
		$only_allowed = false;
		break;
	}
}
Naber_Tests::ok( $only_allowed, 'Yalnizca izin verilen karakterler kullaniliyor' );

Naber_Tests::ok( false === strpos( $code, '0' ), 'Kod 0 rakami icermiyor (O ile karismasin)' );
Naber_Tests::ok( false === strpos( $code, '1' ), 'Kod 1 rakami icermiyor (I/l ile karismasin)' );
Naber_Tests::ok( false === strpos( $code, 'O' ), 'Kod O harfi icermiyor' );
Naber_Tests::ok( false === strpos( $code, 'I' ), 'Kod I harfi icermiyor' );

Naber_Tests::equals( 8, strlen( Naber_Chat_Repo::random_invite_code( 8 ) ), 'Ozel uzunluk parametresi calisiyor' );

// Ayni girdiyle farkli cikti (rastgelelik) - cok dusuk ihtimalle ayni gelebilir,
// bu yuzden birden fazla deneme yapip en az bir farkli sonuc arariz.
$codes = array();
for ( $i = 0; $i < 10; $i++ ) {
	$codes[] = Naber_Chat_Repo::random_invite_code();
}
Naber_Tests::ok( count( array_unique( $codes ) ) > 1, '10 uretimde en az iki farkli kod cikiyor (rastgele)' );

Naber_Tests::summary();
