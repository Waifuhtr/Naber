<?php
/**
 * Cikartma ve ozel emoji adlandirma testleri (saf fonksiyonlar).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-stickers.php
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Ozel emoji adi' );

Naber_Tests::equals( 'kahkaha', Naber_Stickers::normalize_name( 'Kahkaha' ), 'Buyuk harf kuculuyor' );
Naber_Tests::equals( 'gulus_2', Naber_Stickers::normalize_name( 'gulus 2' ), 'Bosluk alt cizgi oluyor' );
Naber_Tests::equals( 'sukru', Naber_Stickers::normalize_name( 'Şükrü' ), 'Turkce harfler ascii karsiligina donuyor' );
Naber_Tests::equals( 'parti', Naber_Stickers::normalize_name( ':parti:' ), 'Iki nokta isaretleri temizleniyor' );
Naber_Tests::equals( '', Naber_Stickers::normalize_name( '!!!' ), 'Gecersiz ad bos donuyor' );
Naber_Tests::equals( 30, strlen( Naber_Stickers::normalize_name( str_repeat( 'a', 60 ) ) ), 'Ad 30 karaktere kirpiliyor' );

Naber_Tests::group( 'Paket adi' );

Naber_Tests::equals( 'Kanka Paketi', Naber_Stickers::normalize_pack( '  Kanka Paketi  ' ), 'Bosluklar kirpiliyor' );
Naber_Tests::equals( 'Merhaba', Naber_Stickers::normalize_pack( '<b>Merhaba</b>' ), 'Etiketler temizleniyor' );

Naber_Tests::group( 'Metinden ozel emoji cikarma' );

Naber_Tests::equals(
	array( 'kahkaha' ),
	Naber_Stickers::extract_names( 'bu cok komikti :kahkaha:' ),
	'Tek emoji bulunuyor'
);
Naber_Tests::equals(
	array( 'kahkaha', 'parti' ),
	Naber_Stickers::extract_names( ':kahkaha: ve :parti:' ),
	'Birden fazla emoji bulunuyor'
);
Naber_Tests::equals(
	array( 'kahkaha' ),
	Naber_Stickers::extract_names( ':kahkaha: :KAHKAHA:' ),
	'Ayni emoji tekrar edilse de bir kez listeleniyor'
);
Naber_Tests::equals( array(), Naber_Stickers::extract_names( 'iki nokta yok' ), 'Emoji yoksa bos liste' );
Naber_Tests::equals( array(), Naber_Stickers::extract_names( 'saat 12:30 gibi' ), 'Saat bicimi emoji sayilmiyor' );
Naber_Tests::equals( array(), Naber_Stickers::extract_names( '' ), 'Bos metin bos liste' );

Naber_Tests::group( 'Desteklenen turler' );

Naber_Tests::ok( in_array( 'sticker', Naber_Stickers::KINDS, true ), 'Cikartma turu tanimli' );
Naber_Tests::ok( in_array( 'emoji', Naber_Stickers::KINDS, true ), 'Ozel emoji turu tanimli' );

Naber_Tests::summary();
