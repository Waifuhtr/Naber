<?php
/**
 * Ayrintili grup yetkileri, sahiplik devri ve saka savunmasi testleri.
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-group-perms.php
 *
 * Not: veritabanina dokunan kisimlar burada test edilmez; yalnizca saf
 * mantik (yetki cozumleme, rastgele sahip secimi, sure dolma ayirimi).
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Ayrintili grup yetkileri' );

Naber_Tests::equals(
	array( 'remove_member', 'delete_message' ),
	Naber_Chat_Repo::parse_perms( 'remove_member,delete_message' ),
	'Virgullu metin listeye cozuluyor'
);
Naber_Tests::equals(
	array( 'remove_member' ),
	Naber_Chat_Repo::parse_perms( ' remove_member , uydurma_yetki ' ),
	'Tanimsiz yetkiler atiliyor, bosluklar kirpiliyor'
);
Naber_Tests::equals(
	array( 'delete_message' ),
	Naber_Chat_Repo::parse_perms( array( 'delete_message', 'delete_message' ) ),
	'Tekrarlar teke iniyor ve dizi girdi kabul ediliyor'
);
Naber_Tests::equals( array(), Naber_Chat_Repo::parse_perms( '' ), 'Bos metin bos liste veriyor' );

Naber_Tests::equals(
	Naber_Chat_Repo::PERMISSIONS,
	Naber_Chat_Repo::perms_for( 'owner', '' ),
	'Sahip butun yetkilere sahip'
);
Naber_Tests::equals(
	Naber_Chat_Repo::PERMISSIONS,
	Naber_Chat_Repo::perms_for( 'admin', '' ),
	'Yonetici butun yetkilere sahip'
);
Naber_Tests::equals(
	array( 'pin_message' ),
	Naber_Chat_Repo::perms_for( 'member', 'pin_message' ),
	'Duz uyede yalnizca verilen yetkiler var'
);
Naber_Tests::equals(
	array(),
	Naber_Chat_Repo::perms_for( 'member', '' ),
	'Yetkisiz uyenin listesi bos'
);

Naber_Tests::group( 'Sahiplik devri' );

$pool = array( 5, 7, 9 );
$picked = Naber_Groups::pick_random_owner( $pool, 7 );
Naber_Tests::ok( in_array( $picked, array( 5, 9 ), true ), 'Ayrilan uye disindan biri seciliyor' );
Naber_Tests::equals( 0, Naber_Groups::pick_random_owner( array( 4 ), 4 ), 'Baska uye yoksa 0 doner' );
Naber_Tests::equals( 0, Naber_Groups::pick_random_owner( array(), 0 ), 'Bos grupta 0 doner' );
Naber_Tests::equals( 3, Naber_Groups::pick_random_owner( array( 0, 3, 0 ), 0 ), 'Gecersiz kimlikler elenir' );

// Rastgelelik: yeterince denemede iki farkli sonuc cikmali.
$seen = array();
for ( $i = 0; $i < 40; $i++ ) {
	$seen[ Naber_Groups::pick_random_owner( array( 1, 2 ), 0 ) ] = true;
}
Naber_Tests::equals( 2, count( $seen ), 'Secim gercekten rastgele (iki aday da cikiyor)' );

Naber_Tests::group( 'Saka savunmasi' );

$message = Naber_Groups::random_prank_message();
Naber_Tests::ok( in_array( $message, Naber_Groups::PRANK_MESSAGES, true ), 'Saka mesaji listeden geliyor' );
Naber_Tests::ok( '' !== trim( $message ), 'Saka mesaji bos degil' );

$entries = array(
	array( 'conversation_id' => 1, 'user_id' => 10, 'role' => 'member', 'perms' => '', 'due' => 100 ),
	array( 'conversation_id' => 1, 'user_id' => 11, 'role' => 'admin', 'perms' => '', 'due' => 300 ),
	array( 'bozuk' => true ),
);
list( $due, $wait ) = Naber_Groups::split_due( $entries, 200 );
Naber_Tests::equals( 1, count( $due ), 'Suresi dolan tek kayit ayrildi' );
Naber_Tests::equals( 10, (int) $due[0]['user_id'], 'Dogru kullanici geri alinacaklar arasinda' );
Naber_Tests::equals( 1, count( $wait ), 'Suresi dolmayan bekliyor' );
Naber_Tests::equals( 11, (int) $wait[0]['user_id'], 'Bekleyen kayit dogru' );

list( $due2, $wait2 ) = Naber_Groups::split_due( array(), 200 );
Naber_Tests::equals( 0, count( $due2 ), 'Bos listede geri alinacak yok' );
Naber_Tests::equals( 0, count( $wait2 ), 'Bos listede bekleyen yok' );

Naber_Tests::equals(
	10,
	Naber_Groups::PRANK_RESTORE_SECONDS,
	'Geri alma suresi 10 saniye'
);

Naber_Tests::summary();
