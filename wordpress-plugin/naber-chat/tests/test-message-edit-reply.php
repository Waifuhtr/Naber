<?php
/**
 * Mesaj duzenleme ve yanitlama ile ilgili saf mantik testleri.
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-message-edit-reply.php
 *
 * Not: gercek veritabani gerektiren kisimlar (Naber_Chat_Repo::insert_message,
 * edit_message, reply_summary) burada test edilmez.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Duzenleme suresi penceresi (saf fonksiyon)' );

Naber_Tests::ok( Naber_REST::within_edit_window( 0 ), 'Az once gonderilen mesaj duzenlenebilir' );
Naber_Tests::ok( Naber_REST::within_edit_window( 899 ), 'Sure dolmadan hemen once duzenlenebilir' );
Naber_Tests::ok( Naber_REST::within_edit_window( 900 ), 'Tam sinirda duzenlenebilir' );
Naber_Tests::ok( ! Naber_REST::within_edit_window( 901 ), 'Sinirin bir saniye ustunde duzenlenemez' );
Naber_Tests::ok( ! Naber_REST::within_edit_window( 3600 ), 'Bir saat sonra duzenlenemez' );
Naber_Tests::equals( 900, Naber_REST::EDIT_WINDOW_SECONDS, 'Duzenleme penceresi 15 dakika (900 sn)' );

Naber_Tests::summary();
