<?php
/**
 * Engelleme: hata uretimi testleri (saf fonksiyon).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-blocks.php
 *
 * Not: has_blocked/between/set veritabani gerektirdigi icin burada test
 * edilmez; yalnizca islem adina gore dogru mesajin uretildigi dogrulanir.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Engel hatasi' );

$message = Naber_Blocks::blocked_error( 'message' );
Naber_Tests::ok( is_wp_error( $message ), 'Mesaj icin WP_Error donuyor' );
Naber_Tests::equals( 'naber_blocked', $message->get_error_code(), 'Hata kodu naber_blocked' );
Naber_Tests::equals( 403, $message->get_error_data()['status'], 'Durum kodu 403' );

$call = Naber_Blocks::blocked_error( 'call' );
Naber_Tests::ok(
	false !== strpos( $call->get_error_message(), 'arayamazsiniz' ),
	'Arama icin aramaya ozel mesaj donuyor'
);

$poke = Naber_Blocks::blocked_error( 'poke' );
Naber_Tests::ok(
	false !== strpos( $poke->get_error_message(), 'durtemezsiniz' ),
	'Durtme icin durtmeye ozel mesaj donuyor'
);

// Bilinmeyen islem adi sessizce mesaja duser; kullanici bos hata gormez.
$unknown = Naber_Blocks::blocked_error( 'bilinmeyen' );
Naber_Tests::equals(
	$message->get_error_message(),
	$unknown->get_error_message(),
	'Bilinmeyen islem adi varsayilan mesaja dusuyor'
);

Naber_Tests::group( 'Kendini engelleme' );

$self = Naber_Blocks::set( 7, 7, true );
Naber_Tests::ok( is_wp_error( $self ), 'Kendini engellemek reddediliyor' );
Naber_Tests::equals( 'naber_self_block', $self->get_error_code(), 'Kendini engelleme hata kodu dogru' );

$missing = Naber_Blocks::set( 7, 0, true );
Naber_Tests::ok( is_wp_error( $missing ), 'Gecersiz kullanici kimligi reddediliyor' );
Naber_Tests::equals( 'naber_user_missing', $missing->get_error_code(), 'Eksik kullanici hata kodu dogru' );

Naber_Tests::summary();
