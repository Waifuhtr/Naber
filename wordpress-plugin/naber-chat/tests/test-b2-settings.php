<?php
/**
 * Backblaze bucket ayarlari testleri.
 *
 * Calistirma:  php wordpress-plugin/naber-chat/tests/test-b2-settings.php
 *
 * 1) Bicim dogrulamasi: yanlis yazilmis Key ID / bucket adi vb. yakalanir mi?
 * 2) Baglanti testi: Backblaze taklit edilerek "bilgiler dogru mu" akisi sinanir.
 */

require_once __DIR__ . '/bootstrap.php';

$valid_input = array(
	'b2_key_id'          => '0035a1b2c3d4e5f0000000001',
	'b2_app_key'         => 'K003abcdefghijklmnopqrstuvwxyz1',
	'b2_bucket_name'     => 'naber-medya',
	'b2_bucket_id'       => 'a1b2c3d4e5f60718293a4b5c',
	'b2_path_prefix'     => 'naber',
	'b2_public_base_url' => 'https://f003.backblazeb2.com/file/naber-medya/',
	'b2_link_ttl'        => 3600,
	'b2_max_upload_mb'   => 25,
);

// ---------------------------------------------------------------- bicim testi
Naber_Tests::group( 'Bucket bilgileri bicim dogrulamasi' );

$result = Naber_Settings::validate_b2( $valid_input );
Naber_Tests::ok( $result['valid'], 'Dogru bilgiler kabul ediliyor' );
Naber_Tests::equals( 'naber/', $result['values']['b2_path_prefix'], 'Klasor onekinin sonuna / ekleniyor' );
Naber_Tests::equals( 'https://f003.backblazeb2.com/file/naber-medya', $result['values']['b2_public_base_url'], 'Genel adresin sonundaki / temizleniyor' );

$result = Naber_Settings::validate_b2( array_merge( $valid_input, array( 'b2_bucket_name' => 'Naber-Medya' ) ) );
Naber_Tests::ok( $result['valid'], 'Buyuk harfli bucket adi kabul ediliyor' );
Naber_Tests::equals( 'naber-medya', $result['values']['b2_bucket_name'], 'Bucket adi kucuk harfe cevriliyor' );

$cases = array(
	array( 'b2_key_id' => '' , 'field' => 'b2_key_id', 'label' => 'Bos Key ID reddediliyor' ),
	array( 'b2_key_id' => 'kisa', 'field' => 'b2_key_id', 'label' => 'Cok kisa Key ID reddediliyor' ),
	array( 'b2_key_id' => '0035a1b2c3d4-e5f0000000001', 'field' => 'b2_key_id', 'label' => 'Icinde tire olan Key ID reddediliyor' ),
	array( 'b2_app_key' => 'kisaanahtar', 'field' => 'b2_app_key', 'label' => 'Cok kisa Application Key reddediliyor' ),
	array( 'b2_app_key' => 'K003abcdefghij klmnopqrstuvwxyz', 'field' => 'b2_app_key', 'label' => 'Bosluk iceren Application Key reddediliyor' ),
	array( 'b2_bucket_name' => 'ab', 'field' => 'b2_bucket_name', 'label' => 'Cok kisa bucket adi reddediliyor' ),
	array( 'b2_bucket_name' => 'naber_medya', 'field' => 'b2_bucket_name', 'label' => 'Alt cizgili bucket adi reddediliyor' ),
	array( 'b2_bucket_name' => 'b2-naber-medya', 'field' => 'b2_bucket_name', 'label' => '"b2-" ile baslayan bucket adi reddediliyor' ),
	array( 'b2_bucket_id' => 'gecersiz-id', 'field' => 'b2_bucket_id', 'label' => 'Gecersiz bucket ID reddediliyor' ),
	array( 'b2_public_base_url' => 'http://ornek.com/file', 'field' => 'b2_public_base_url', 'label' => 'http:// adres reddediliyor (https gerekli)' ),
	array( 'b2_link_ttl' => 10, 'field' => 'b2_link_ttl', 'label' => 'Cok kisa imza suresi reddediliyor' ),
	array( 'b2_link_ttl' => 999999, 'field' => 'b2_link_ttl', 'label' => 'Cok uzun imza suresi reddediliyor' ),
	array( 'b2_max_upload_mb' => 0, 'field' => 'b2_max_upload_mb', 'label' => 'Sifir dosya boyutu reddediliyor' ),
	array( 'b2_max_upload_mb' => 500, 'field' => 'b2_max_upload_mb', 'label' => '500 MB sinir reddediliyor' ),
	array( 'b2_path_prefix' => 'naber klasor', 'field' => 'b2_path_prefix', 'label' => 'Bosluklu klasor oneki reddediliyor' ),
);

foreach ( $cases as $case ) {
	$field = $case['field'];
	$label = $case['label'];
	unset( $case['field'], $case['label'] );
	$check = Naber_Settings::validate_b2( array_merge( $valid_input, $case ) );
	Naber_Tests::ok( ! $check['valid'] && isset( $check['errors'][ $field ] ), $label );
}

$check = Naber_Settings::validate_b2( array_merge( $valid_input, array( 'b2_bucket_id' => '' ) ) );
Naber_Tests::ok( $check['valid'], 'Bucket ID bos birakilabiliyor (test otomatik dolduruyor)' );

// ------------------------------------------------------------ baglanti testi
Naber_Tests::group( 'Backblaze baglanti testi (taklit API)' );

$settings = array_merge( Naber_Settings::defaults(), $valid_input, array( 'b2_bucket_id' => '', 'b2_public_base_url' => '' ) );

$http   = new Naber_Fake_B2_Http();
$b2     = new Naber_B2( $settings, $http );
$test   = $b2->test_connection( true );
$by_key = array();
foreach ( $test['steps'] as $step ) {
	$by_key[ $step['key'] ] = $step;
}
Naber_Tests::ok( $test['ok'], 'Dogru bilgilerle test basarili' );
Naber_Tests::equals( 'a1b2c3d4e5f60718293a4b5c', $test['bucket_id'], 'Bucket ID otomatik bulunuyor' );
Naber_Tests::ok( isset( $by_key['write'] ) && $by_key['write']['ok'], 'Deneme dosyasi yaziliyor' );
Naber_Tests::ok( isset( $by_key['cleanup'] ) && $by_key['cleanup']['ok'], 'Deneme dosyasi siliniyor' );

// Yanlis anahtar -> 401
delete_transient( Naber_B2::AUTH_TRANSIENT );
$http              = new Naber_Fake_B2_Http();
$http->auth_status = 401;
$test              = ( new Naber_B2( $settings, $http ) )->test_connection( true );
$auth_step         = null;
foreach ( $test['steps'] as $step ) {
	if ( 'auth' === $step['key'] ) {
		$auth_step = $step;
	}
}
Naber_Tests::ok( ! $test['ok'], 'Yanlis anahtar testi basarisiz donduruyor' );
Naber_Tests::ok( $auth_step && ! $auth_step['ok'], 'Hata kimlik dogrulama adiminda yakalaniyor' );
Naber_Tests::ok( $auth_step && false !== strpos( $auth_step['message'], 'Application Key' ), 'Kullaniciya anlasilir aciklama veriliyor' );

// Bucket bulunamiyor
delete_transient( Naber_B2::AUTH_TRANSIENT );
$http = new Naber_Fake_B2_Http( array( array( 'bucketId' => 'ffffffffffffffffffffffff', 'bucketName' => 'baska-bucket', 'bucketType' => 'allPrivate' ) ) );
$test = ( new Naber_B2( $settings, $http ) )->test_connection( true );
Naber_Tests::ok( ! $test['ok'], 'Var olmayan bucket adi hata veriyor' );
Naber_Tests::ok( false !== strpos( $test['message'], 'Bucket' ), 'Hata mesaji bucket adimini isaret ediyor' );

// Anahtar baska bir bucket ile sinirli
delete_transient( Naber_B2::AUTH_TRANSIENT );
$http                    = new Naber_Fake_B2_Http();
$http->restricted_bucket = 'baska-bucket';
$test                    = ( new Naber_B2( $settings, $http ) )->test_connection( true );
$bucket_step             = null;
foreach ( $test['steps'] as $step ) {
	if ( 'bucket' === $step['key'] ) {
		$bucket_step = $step;
	}
}
Naber_Tests::ok( ! $test['ok'], 'Baska bucket ile sinirli anahtar yakalaniyor' );
Naber_Tests::ok( $bucket_step && false !== strpos( $bucket_step['message'], 'baska-bucket' ), 'Hata mesaji anahtarin erisebildigi bucket adini soyluyor' );

// Eksik yetki
delete_transient( Naber_B2::AUTH_TRANSIENT );
$http               = new Naber_Fake_B2_Http();
$http->capabilities = array( 'listBuckets', 'readFiles' );
$test               = ( new Naber_B2( $settings, $http ) )->test_connection( true );
$cap_step           = null;
foreach ( $test['steps'] as $step ) {
	if ( 'capabilities' === $step['key'] ) {
		$cap_step = $step;
	}
}
Naber_Tests::ok( ! $test['ok'], 'Yazma yetkisi olmayan anahtar reddediliyor' );
Naber_Tests::ok( $cap_step && false !== strpos( $cap_step['message'], 'writeFiles' ), 'Eksik yetkiler tek tek listeleniyor' );

// Yazma yetkisi var ama silme yasak
delete_transient( Naber_B2::AUTH_TRANSIENT );
$http                = new Naber_Fake_B2_Http();
$http->delete_status = 401;
$test                = ( new Naber_B2( $settings, $http ) )->test_connection( true );
Naber_Tests::ok( ! $test['ok'], 'Deneme dosyasi silinemezse uyari veriliyor' );

// Yazma testi atlanabiliyor
delete_transient( Naber_B2::AUTH_TRANSIENT );
$http = new Naber_Fake_B2_Http();
$test = ( new Naber_B2( $settings, $http ) )->test_connection( false );
Naber_Tests::ok( $test['ok'], 'Yazma testi atlandiginda da dogrulama yapilabiliyor' );
$wrote = false;
foreach ( $http->calls as $call ) {
	if ( false !== strpos( $call['url'], 'b2_upload_file' ) ) {
		$wrote = true;
	}
}
Naber_Tests::ok( ! $wrote, 'Yazma testi kapaliyken bucket\'a dosya yazilmiyor' );

// Bicimsel olarak hatali bilgilerle ag istegine hic cikilmiyor
delete_transient( Naber_B2::AUTH_TRANSIENT );
$http = new Naber_Fake_B2_Http();
$test = ( new Naber_B2( array_merge( $settings, array( 'b2_bucket_name' => 'b2-yasak' ) ), $http ) )->test_connection( true );
Naber_Tests::ok( ! $test['ok'], 'Bicim hatasi testi hemen durduruyor' );
Naber_Tests::equals( 0, count( $http->calls ), 'Bicim hatali bilgiyle Backblaze\'e istek gonderilmiyor' );

// -------------------------------------------------------------- medya kurali
Naber_Tests::group( 'Medya kurallari' );

Naber_Settings::set_override( array_merge( $valid_input, array( 'b2_path_prefix' => 'naber/', 'b2_max_upload_mb' => 25 ) ) );

Naber_Tests::ok( Naber_Media::is_allowed_mime( 'image/jpeg' ), 'JPEG kabul ediliyor' );
Naber_Tests::ok( Naber_Media::is_allowed_mime( 'IMAGE/PNG' ), 'Buyuk harfli MIME kabul ediliyor' );
Naber_Tests::ok( ! Naber_Media::is_allowed_mime( 'application/x-php' ), 'PHP dosyasi reddediliyor' );
Naber_Tests::ok( ! Naber_Media::is_allowed_mime( 'video/mp4' ), 'Video (ilk surumde) reddediliyor' );
Naber_Tests::equals( 25 * 1024 * 1024, Naber_Media::max_bytes(), 'Azami boyut ayardan okunuyor' );

$name = Naber_Media::build_file_name( 7, 'image/jpeg' );
Naber_Tests::ok( 0 === strpos( $name, 'naber/u7/' ), 'Dosya adi klasor oneki ve kullanici ile basliyor' );
Naber_Tests::ok( '.jpg' === substr( $name, -4 ), 'Uzanti MIME turune gore veriliyor' );
Naber_Tests::ok( $name !== Naber_Media::build_file_name( 7, 'image/jpeg' ), 'Dosya adlari tahmin edilemez (her cagride farkli)' );

Naber_Tests::group( 'iceServers ve maskeleme' );
Naber_Settings::set_override( array(
	'stun_urls'       => 'stun:stun.l.google.com:19302, stun:stun1.l.google.com:19302',
	'turn_urls'       => 'turn:turn.ornek.com:3478?transport=udp turn:turn.ornek.com:3478?transport=tcp',
	'turn_username'   => 'naber',
	'turn_credential' => 'gizli',
) );
$ice = Naber_Settings::ice_servers();
Naber_Tests::equals( 3, count( $ice ), 'Iki STUN ve bir TURN girdisi uretiliyor' );
Naber_Tests::equals( 2, count( $ice[2]['urls'] ), 'TURN adresleri tek girdide toplaniyor' );
Naber_Tests::equals( 'naber', $ice[2]['username'], 'TURN kullanici adi aktariliyor' );
$masked = Naber_Settings::mask( 'K003abcdefghijklmnop' );
Naber_Tests::equals( 'K00**************nop', $masked, 'Gizli anahtar maskeleniyor (sadece bas ve son 3 karakter goruluyor)' );
Naber_Tests::ok( false === strpos( $masked, 'abcdefghij' ), 'Maskede anahtarin govdesi gorunmuyor' );
Naber_Tests::equals( '****', Naber_Settings::mask( 'kisa' ), 'Kisa degerler tamamen maskeleniyor' );
Naber_Tests::equals( '', Naber_Settings::mask( '' ), 'Bos deger bos donuyor' );

Naber_Settings::set_override( null );

exit( Naber_Tests::summary() );
