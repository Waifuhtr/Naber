<?php
/**
 * TURN sunucu yapilandirmasi.
 *
 * Iki kaynak desteklenir:
 *  - Elle girilen STUN/TURN adresleri
 *  - Metered (metered.ca) TURN servisi: API anahtari ile kisa omurlu
 *    kimlik bilgileri sunucu tarafinda cekilir, uygulamaya gomulmez.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Turn {

	const CACHE_KEY = 'naber_turn_servers';

	/** @var callable|null Testlerde HTTP katmanini degistirmek icin. */
	private $http;

	/** @var array */
	private $settings;

	public function __construct( $settings = null, $http = null ) {
		$this->settings = null === $settings ? Naber_Settings::all() : $settings;
		$this->http     = $http;
	}

	/**
	 * "naber", "naber.metered.live", "https://naber.metered.live/" -> "naber"
	 */
	public static function normalize_subdomain( $value ) {
		$value = strtolower( trim( (string) $value ) );
		$value = preg_replace( '#^https?://#', '', $value );
		$value = rtrim( (string) $value, '/' );
		$value = preg_replace( '/\.metered\.live$/', '', (string) $value );
		$value = preg_replace( '/[^a-z0-9-]/', '', (string) $value );
		return (string) $value;
	}

	public static function credentials_url( $subdomain, $api_key ) {
		return sprintf(
			'https://%s.metered.live/api/v1/turn/credentials?apiKey=%s',
			self::normalize_subdomain( $subdomain ),
			rawurlencode( (string) $api_key )
		);
	}

	/**
	 * Metered yanitini iceServers bicimine cevirir.
	 * Hem duz dizi hem de { "iceServers": [...] } bicimini kabul eder.
	 *
	 * @return array
	 */
	public static function parse_credentials( $data ) {
		if ( is_string( $data ) ) {
			$data = json_decode( $data, true );
		}
		if ( isset( $data['iceServers'] ) && is_array( $data['iceServers'] ) ) {
			$data = $data['iceServers'];
		}
		if ( ! is_array( $data ) ) {
			return array();
		}

		$out = array();
		foreach ( $data as $item ) {
			if ( ! is_array( $item ) || empty( $item['urls'] ) ) {
				continue;
			}

			$urls = $item['urls'];
			if ( is_array( $urls ) ) {
				$urls = array_values( array_filter( array_map( 'strval', $urls ) ) );
				if ( ! $urls ) {
					continue;
				}
			} else {
				$urls = (string) $urls;
				if ( '' === $urls ) {
					continue;
				}
			}

			$entry = array( 'urls' => $urls );
			if ( ! empty( $item['username'] ) ) {
				$entry['username'] = (string) $item['username'];
			}
			if ( ! empty( $item['credential'] ) ) {
				$entry['credential'] = (string) $item['credential'];
			}
			$out[] = $entry;
		}

		return $out;
	}

	private function request( $url ) {
		if ( is_callable( $this->http ) ) {
			return call_user_func( $this->http, 'GET', $url );
		}

		$response = wp_remote_get( $url, array( 'timeout' => 15 ) );
		if ( is_wp_error( $response ) ) {
			return array( 'ok' => false, 'status' => 0, 'body' => null, 'error' => $response->get_error_message() );
		}

		$status = (int) wp_remote_retrieve_response_code( $response );
		$raw    = wp_remote_retrieve_body( $response );

		return array(
			'ok'     => ( $status >= 200 && $status < 300 ),
			'status' => $status,
			'body'   => json_decode( $raw, true ),
			'error'  => ( $status >= 200 && $status < 300 ) ? '' : ( '' !== $raw ? substr( $raw, 0, 200 ) : 'HTTP ' . $status ),
		);
	}

	public function metered_configured() {
		return '' !== trim( (string) $this->settings['metered_api_key'] )
			&& '' !== self::normalize_subdomain( $this->settings['metered_subdomain'] );
	}

	/**
	 * Metered'dan kimlik bilgilerini ceker.
	 *
	 * @return array|WP_Error
	 */
	public function fetch_metered() {
		if ( ! $this->metered_configured() ) {
			return new WP_Error( 'naber_turn_missing', 'Metered API anahtari veya uygulama adi girilmemis.' );
		}

		$url = self::credentials_url( $this->settings['metered_subdomain'], $this->settings['metered_api_key'] );
		$res = $this->request( $url );

		if ( ! $res['ok'] ) {
			$hint = '';
			if ( 401 === $res['status'] || 403 === $res['status'] ) {
				$hint = ' API anahtari hatali gorunuyor.';
			} elseif ( 404 === $res['status'] ) {
				$hint = ' Uygulama adi (alt alan adi) hatali gorunuyor.';
			}
			return new WP_Error( 'naber_turn_failed', 'Metered baglantisi basarisiz: ' . $res['error'] . $hint, array( 'status' => $res['status'] ) );
		}

		$servers = self::parse_credentials( $res['body'] );
		if ( ! $servers ) {
			return new WP_Error( 'naber_turn_empty', 'Metered gecerli bir sunucu listesi dondurmedi.' );
		}

		return $servers;
	}

	/**
	 * Uygulamaya verilecek nihai iceServers listesi.
	 * Metered sonuclari onbelleklenir (varsayilan 30 dakika).
	 */
	public function ice_servers( $fresh = false ) {
		$manual = Naber_Settings::manual_ice_servers( $this->settings );

		if ( ! $this->metered_configured() ) {
			return $manual;
		}

		$cached = $fresh ? false : get_transient( self::CACHE_KEY );
		if ( is_array( $cached ) && ! empty( $cached['servers'] ) ) {
			return array_merge( $manual, $cached['servers'] );
		}

		$servers = $this->fetch_metered();
		if ( is_wp_error( $servers ) ) {
			// Metered'a ulasilamazsa elle girilen sunucularla devam edilir.
			return $manual;
		}

		$ttl = (int) $this->settings['metered_ttl'];
		$ttl = max( 300, min( 43200, $ttl ? $ttl : 1800 ) );
		set_transient( self::CACHE_KEY, array( 'servers' => $servers ), $ttl );

		return array_merge( $manual, $servers );
	}

	public static function forget_cache() {
		delete_transient( self::CACHE_KEY );
	}

	/**
	 * Yonetim ekranindaki "TURN testi" dugmesi icin adim adim sinama.
	 *
	 * @return array{ok:bool,steps:array,message:string,servers:array}
	 */
	public function test() {
		$steps  = array();
		$manual = Naber_Settings::manual_ice_servers( $this->settings );

		$steps[] = array(
			'key'     => 'manual',
			'ok'      => ! empty( $manual ),
			'label'   => 'Elle girilen sunucular',
			'message' => $manual
				? count( $manual ) . ' adet STUN/TURN girdisi tanimli.'
				: 'Elle girilmis STUN/TURN yok (Metered kullanilacaksa sorun degil).',
		);

		if ( ! $this->metered_configured() ) {
			$steps[] = array(
				'key'     => 'metered',
				'ok'      => false,
				'label'   => 'Metered',
				'message' => 'API anahtari veya uygulama adi girilmemis.',
			);
			$ok = ! empty( $manual );
			return array(
				'ok'      => $ok,
				'steps'   => $steps,
				'servers' => $manual,
				'message' => $ok
					? 'Metered kapali; elle girilen sunucular kullanilacak.'
					: 'Hicbir TURN/STUN kaynagi tanimli degil. P2P kurulamazsa aramalar basarisiz olur.',
			);
		}

		$servers = $this->fetch_metered();
		if ( is_wp_error( $servers ) ) {
			$steps[] = array( 'key' => 'metered', 'ok' => false, 'label' => 'Metered', 'message' => $servers->get_error_message() );
			return array(
				'ok'      => false,
				'steps'   => $steps,
				'servers' => $manual,
				'message' => 'Metered kimlik bilgileri alinamadi.',
			);
		}

		$turn_count = 0;
		$stun_count = 0;
		foreach ( $servers as $server ) {
			$urls = is_array( $server['urls'] ) ? $server['urls'] : array( $server['urls'] );
			foreach ( $urls as $url ) {
				if ( 0 === strpos( $url, 'turn' ) ) {
					$turn_count++;
				} elseif ( 0 === strpos( $url, 'stun' ) ) {
					$stun_count++;
				}
			}
		}

		$steps[] = array(
			'key'     => 'metered',
			'ok'      => true,
			'label'   => 'Metered',
			'message' => sprintf( 'Kimlik bilgileri alindi: %d TURN, %d STUN adresi.', $turn_count, $stun_count ),
		);

		$has_credentials = false;
		foreach ( $servers as $server ) {
			if ( ! empty( $server['username'] ) && ! empty( $server['credential'] ) ) {
				$has_credentials = true;
				break;
			}
		}

		$steps[] = array(
			'key'     => 'credentials',
			'ok'      => $has_credentials,
			'label'   => 'Kimlik bilgisi',
			'message' => $has_credentials
				? 'TURN kullanici adi ve sifresi geldi.'
				: 'Yanitta kullanici adi/sifre yok; yalnizca STUN calisir, rolelenmis arama yapilamaz.',
		);

		self::forget_cache();

		return array(
			'ok'      => $turn_count > 0 && $has_credentials,
			'steps'   => $steps,
			'servers' => array_merge( $manual, $servers ),
			'message' => $turn_count > 0 && $has_credentials
				? 'TURN yapilandirmasi calisiyor.'
				: 'TURN adresi bulunamadi; aramalar yalnizca dogrudan baglanti kurulabilen aglarda calisir.',
		);
	}
}
