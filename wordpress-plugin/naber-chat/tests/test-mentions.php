<?php
/**
 * Bahsetme (@isim) eslestirme testleri (saf fonksiyon).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-mentions.php
 */

require_once __DIR__ . '/bootstrap.php';

$members = array(
	array( 'id' => 1, 'display_name' => 'Ali' ),
	array( 'id' => 2, 'display_name' => 'Ali Veli' ),
	array( 'id' => 3, 'display_name' => 'Sukru' ),
	array( 'id' => 4, 'display_name' => 'Ayse' ),
);

Naber_Tests::group( 'Bahsetme eslestirme' );

Naber_Tests::equals(
	array(),
	Naber_Chat_Repo::mentioned_ids( 'Merhaba nasilsiniz', $members ),
	'@ yoksa kimse bahsedilmis sayilmiyor'
);

Naber_Tests::equals(
	array( 4 ),
	Naber_Chat_Repo::mentioned_ids( 'Selam @Ayse bugun gelecek misin', $members ),
	'Tek kisi bulunuyor'
);

// En uzun isim once denenir; yoksa "Ali Veli" yazilinca "Ali" de
// bahsedilmis sayilir ve yanlis kisiye bildirim gider.
Naber_Tests::equals(
	array( 2 ),
	Naber_Chat_Repo::mentioned_ids( '@Ali Veli bunu sen yapar misin', $members ),
	'Uzun isim kisa ismi golgede birakmiyor'
);

Naber_Tests::equals(
	array( 1 ),
	Naber_Chat_Repo::mentioned_ids( '@Ali gelir misin', $members ),
	'Kisa isim tek basina bulunuyor'
);

Naber_Tests::group( 'Buyuk/kucuk ve Turkce harfler' );

Naber_Tests::equals(
	array( 4 ),
	Naber_Chat_Repo::mentioned_ids( '@AYSE neredesin', $members ),
	'Buyuk harfle yazilan isim bulunuyor'
);

Naber_Tests::equals(
	array( 3 ),
	Naber_Chat_Repo::mentioned_ids( '@Sükrü bak burada', $members ),
	'Turkce harfle yazilsa da ASCII isim bulunuyor'
);

Naber_Tests::group( 'Herkes' );

Naber_Tests::equals(
	array( 1, 2, 3, 4 ),
	Naber_Chat_Repo::mentioned_ids( '@herkes toplanti var', $members ),
	'@herkes tum uyeleri kapsiyor'
);

Naber_Tests::equals(
	array( 1, 2, 3, 4 ),
	Naber_Chat_Repo::mentioned_ids( '@everyone meeting', $members ),
	'@everyone de tum uyeleri kapsiyor'
);

Naber_Tests::group( 'Coklu ve tekrarli bahsetme' );

$two = Naber_Chat_Repo::mentioned_ids( '@Ayse ve @Sukru bakar misiniz', $members );
sort( $two );
Naber_Tests::equals( array( 3, 4 ), $two, 'Iki kisi ayni anda bulunuyor' );

Naber_Tests::equals(
	array( 4 ),
	Naber_Chat_Repo::mentioned_ids( '@Ayse @Ayse @Ayse', $members ),
	'Ayni kisi tekrar edilse de bir kez listeleniyor'
);

Naber_Tests::equals(
	array(),
	Naber_Chat_Repo::mentioned_ids( 'e-posta: ali@naber.com', $members ),
	'Grupta olmayan bir ad (e-posta icindeki) bahsetme sayilmiyor'
);

Naber_Tests::summary();
