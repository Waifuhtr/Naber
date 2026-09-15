<?php
/**
 * TURN yapilandirmasi testleri (Metered + elle girilen sunucular).
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-turn.php
 */

require_once __DIR__ . '/bootstrap.php';

/** Metered API'sini taklit eden HTTP katmani. */
class Naber_Fake_Turn_Http {

	public $calls    = array();
	public $status   = 200;
	public $body;

	public function __construct( $body = null ) {
		$this->body = null === $body ? array(
			array( 'urls' => 'stun:stun.relay.metered.ca:80' ),
			array( 'urls' => 'turn:global.relay.metered.ca:80', 'username' => 'abc123', 'credential' => 'secret123' ),
			array( 'urls' => 'turn:global.relay.metered.ca:443?transport=tcp', 'username' => 'abc123', 'credential' => 'secret123' ),
			array( 'urls' => 'turns:global.relay.metered.ca:443?transport=tcp', 'username' => 'abc123', 'credential' => 'secret123' ),
		) : $body;
	}

	public function __invoke( $method, $url, $headers = array(), $body = null ) {
		$this->calls[] = $url;
		$ok = ( $this->status >= 200 && $this->status < 300 );
		return array(
			'ok'     => $ok,
			'status' => $this->status,
			'body'   => $ok ? $this->body : array( 'message' => 'error' ),
			'error'  => $ok ? '' : 'HTTP ' . $this->status,
		);
	}
}

$base = array_merge( Naber_Settings::defaults(), array(
	'stun_urls'         => 'stun:stun.l.google.com:19302',
	'turn_urls'         => '',
	'metered_api_key'   => 'key_1234567890',
	'metered_subdomain' => 'naber',
	'metered_ttl'       => 1800,
) );

// ------------------------------------------------------- adres normalizasyonu
Naber_Tests::group( 'Metered uygulama adi ve adres' );

Naber_Tests::equals( 'naber', Naber_Turn::normalize_subdomain( 'naber' ), 'Duz ad oldugu gibi kullaniliyor' );
Naber_Tests::equals( 'naber', Naber_Turn::normalize_subdomain( 'naber.metered.live' ), 'Tam alan adi kisaltiliyor' );
Naber_Tests::equals( 'naber', Naber_Turn::normalize_subdomain( 'https://naber.metered.live/' ), 'Tam adres kisaltiliyor' );
Naber_Tests::equals( 'naber-test', Naber_Turn::normalize_subdomain( 'Naber-Test' ), 'Buyuk harfler kucultuluyor' );
Naber_Tests::equals( 'naber', Naber_Turn::normalize_subdomain( ' naber/ ' ), 'Bosluk ve egik cizgi temizleniyor' );

Naber_Tests::equals(
	'https://naber.metered.live/api/v1/turn/credentials?apiKey=key_123',
	Naber_Turn::credentials_url( 'https://naber.metered.live', 'key_123' ),
	'Istek adresi dogru kuruluyor'
);

// ------------------------------------------------------- yanit cozumleme
Naber_Tests::group( 'Metered yaniti cozumleme' );

$parsed = Naber_Turn::parse_credentials( array(
	array( 'urls' => 'stun:stun.relay.metered.ca:80' ),
	array( 'urls' => 'turn:global.relay.metered.ca:80', 'username' => 'u', 'credential' => 'p' ),
) );
Naber_Tests::equals( 2, count( $parsed ), 'Duz dizi yaniti cozumleniyor' );
Naber_Tests::equals( 'u', $parsed[1]['username'], 'Kullanici adi aktariliyor' );
Naber_Tests::ok( ! isset( $parsed[0]['username'] ), 'STUN girdisine bos kimlik eklenmiyor' );

$wrapped = Naber_Turn::parse_credentials( array( 'iceServers' => array( array( 'urls' => array( 'turn:a:80', 'turn:a:443' ), 'username' => 'u', 'credential' => 'p' ) ) ) );
Naber_Tests::equals( 1, count( $wrapped ), 'iceServers ile sarilmis yanit da kabul ediliyor' );
Naber_Tests::equals( 2, count( $wrapped[0]['urls'] ), 'Coklu adres listesi korunuyor' );

$json = Naber_Turn::parse_credentials( '[{"urls":"turn:x:80","username":"u","credential":"p"}]' );
Naber_Tests::equals( 1, count( $json ), 'JSON metni cozumleniyor' );

Naber_Tests::equals( 0, count( Naber_Turn::parse_credentials( 'bozuk-json' ) ), 'Bozuk yanit bos liste veriyor' );
Naber_Tests::equals( 0, count( Naber_Turn::parse_credentials( array( array( 'username' => 'u' ) ) ) ), 'Adres icermeyen girdi atiliyor' );

// ------------------------------------------------------- baglanti testi
Naber_Tests::group( 'TURN baglanti testi' );

delete_transient( Naber_Turn::CACHE_KEY );
$http = new Naber_Fake_Turn_Http();
$turn = new Naber_Turn( $base, $http );
$test = $turn->test();
Naber_Tests::ok( $test['ok'], 'Dogru bilgilerle TURN testi basarili' );
Naber_Tests::ok( false !== strpos( $test['steps'][1]['message'], '3 TURN' ), 'TURN adres sayisi raporlaniyor' );
Naber_Tests::equals( 1, count( $http->calls ), 'Tek istek gonderiliyor' );

delete_transient( Naber_Turn::CACHE_KEY );
$http         = new Naber_Fake_Turn_Http();
$http->status = 401;
$test         = ( new Naber_Turn( $base, $http ) )->test();
Naber_Tests::ok( ! $test['ok'], 'Yanlis API anahtari basarisiz donuyor' );
Naber_Tests::ok( false !== strpos( $test['steps'][1]['message'], 'API anahtari' ), 'Anlasilir hata mesaji veriliyor' );

delete_transient( Naber_Turn::CACHE_KEY );
$http         = new Naber_Fake_Turn_Http();
$http->status = 404;
$test         = ( new Naber_Turn( $base, $http ) )->test();
Naber_Tests::ok( false !== strpos( $test['steps'][1]['message'], 'Uygulama adi' ), 'Yanlis uygulama adi ayirt ediliyor' );

// Yalnizca STUN donerse uyari verilmeli
delete_transient( Naber_Turn::CACHE_KEY );
$http = new Naber_Fake_Turn_Http( array( array( 'urls' => 'stun:stun.relay.metered.ca:80' ) ) );
$test = ( new Naber_Turn( $base, $http ) )->test();
Naber_Tests::ok( ! $test['ok'], 'Sadece STUN donen yapilandirma yetersiz sayiliyor' );

// Metered kapaliyken elle girilenler kullanilir
delete_transient( Naber_Turn::CACHE_KEY );
$manual_only = array_merge( $base, array(
	'metered_api_key'   => '',
	'metered_subdomain' => '',
	'turn_urls'         => 'turn:turn.ornek.com:3478',
	'turn_username'     => 'naber',
	'turn_credential'   => 'gizli',
) );
$http = new Naber_Fake_Turn_Http();
$turn = new Naber_Turn( $manual_only, $http );
Naber_Tests::ok( ! $turn->metered_configured(), 'Eksik bilgiyle Metered devre disi' );
Naber_Tests::equals( 0, count( $http->calls ), 'Metered kapaliyken istek gonderilmiyor' );
Naber_Tests::equals( 2, count( $turn->ice_servers() ), 'Elle girilen STUN ve TURN listeleniyor' );

// ------------------------------------------------------- birlesik liste
Naber_Tests::group( 'iceServers birlestirme ve onbellek' );

delete_transient( Naber_Turn::CACHE_KEY );
$http    = new Naber_Fake_Turn_Http();
$turn    = new Naber_Turn( $base, $http );
$servers = $turn->ice_servers();
Naber_Tests::equals( 5, count( $servers ), 'Elle girilen STUN + Metered 4 girdi birlestiriliyor' );
Naber_Tests::equals( 'stun:stun.l.google.com:19302', $servers[0]['urls'], 'Elle girilen sunucu ilk sirada' );

$turn->ice_servers();
Naber_Tests::equals( 1, count( $http->calls ), 'Ikinci cagride onbellek kullaniliyor (yeni istek yok)' );

$fresh_http = new Naber_Fake_Turn_Http();
$fresh_http->status = 500;
$fallback = ( new Naber_Turn( array_merge( $base, array( 'stun_urls' => 'stun:a:1' ) ), $fresh_http ) )->ice_servers( true );
Naber_Tests::equals( 1, count( $fallback ), 'Metered erisilemezse elle girilenlerle devam ediliyor' );

exit( Naber_Tests::summary() );
