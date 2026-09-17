<?php
/**
 * Sureli sessize alma mantigi testleri (saf fonksiyon).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-mute-pin.php
 *
 * Not: veritabani gerektiren kisimlar (set_notify_muted, set_pinned)
 * burada test edilmez; yalnizca is_notify_muted() saf mantigi test edilir.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Sessize alma durumu (suresi gecmis mi?)' );

Naber_Tests::ok(
	! Naber_Chat_Repo::is_notify_muted( array( 'notify_muted' => 0, 'notify_muted_until' => null ) ),
	'notify_muted kapaliysa sessiz sayilmiyor'
);

Naber_Tests::ok(
	Naber_Chat_Repo::is_notify_muted( array( 'notify_muted' => 1, 'notify_muted_until' => null ) ),
	'notify_muted acik ve suresiz ise sessiz sayiliyor'
);

Naber_Tests::ok(
	Naber_Chat_Repo::is_notify_muted( array( 'notify_muted' => 1, 'notify_muted_until' => '0000-00-00 00:00:00' ) ),
	'Gecersiz (sifir) tarih suresiz gibi davraniyor'
);

$future = gmdate( 'Y-m-d H:i:s', time() + 3600 );
Naber_Tests::ok(
	Naber_Chat_Repo::is_notify_muted( array( 'notify_muted' => 1, 'notify_muted_until' => $future ) ),
	'Bitis suresi gelecekte ise hala sessiz'
);

$past = gmdate( 'Y-m-d H:i:s', time() - 3600 );
Naber_Tests::ok(
	! Naber_Chat_Repo::is_notify_muted( array( 'notify_muted' => 1, 'notify_muted_until' => $past ) ),
	'Bitis suresi gectiyse artik sessiz degil (otomatik acilir)'
);

Naber_Tests::summary();
