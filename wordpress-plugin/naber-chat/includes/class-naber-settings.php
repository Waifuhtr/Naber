<?php
/**
 * Eklenti ayarlari: Backblaze B2 bucket bilgileri, TURN sunucusu ve FCM.
 * Hassas anahtarlar sadece burada (backend) tutulur, Android uygulamasina gonderilmez.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Settings {

	const OPTION = 'naber_chat_settings';

	private static $instance = null;
	/** @var array|null Test edilebilirlik icin bellek ici override. */
	private static $override = null;

	public static function instance() {
		if ( null === self::$instance ) {
			self::$instance = new self();
		}
		return self::$instance;
	}

	public static function defaults() {
		return array(
			'b2_key_id'          => '',
			'b2_app_key'         => '',
			'b2_bucket_name'     => '',
			'b2_bucket_id'       => '',
			'b2_public_base_url' => '',
			'b2_link_ttl'        => 3600,
			'b2_max_upload_mb'   => 25,
			'b2_path_prefix'     => 'naber/',
			'turn_urls'          => '',
			'metered_api_key'    => '',
			'metered_subdomain'  => '',
			'metered_ttl'        => 1800,
			'turn_username'      => '',
			'turn_credential'    => '',
			'stun_urls'          => 'stun:stun.l.google.com:19302',
			'fcm_project_id'     => '',
			'fcm_service_account' => '',
			'allow_registration' => 1,
			'poll_wait'          => 25,
			'poll_interval_ms'   => 250,
			'email_domain'       => 'naber.com',
		);
	}

	public static function all() {
		if ( null !== self::$override ) {
			return array_merge( self::defaults(), self::$override );
		}
		$saved = get_option( self::OPTION, array() );
		if ( ! is_array( $saved ) ) {
			$saved = array();
		}
		return array_merge( self::defaults(), $saved );
	}

	public static function get( $key, $fallback = null ) {
		$all = self::all();
		return array_key_exists( $key, $all ) ? $all[ $key ] : $fallback;
	}

	public static function update( array $values ) {
		$current = self::all();
		update_option( self::OPTION, array_merge( $current, $values ) );
	}

	/** Testlerde kullanilir; null verilirse gercek option'a geri doner. */
	public static function set_override( $values ) {
		self::$override = $values;
	}

	/**
	 * B2 bucket bilgilerinin bicimsel dogrulugunu kontrol eder.
	 * Ag baglantisi gerektirmez; "kaydetmeden once yanlis yazilmis mi" sorusuna cevap verir.
	 *
	 * @return array{valid:bool,errors:array<string,string>,values:array}
	 */
	public static function validate_b2( array $input ) {
		$errors = array();
		$values = array();

		$key_id = isset( $input['b2_key_id'] ) ? trim( (string) $input['b2_key_id'] ) : '';
		if ( '' === $key_id ) {
			$errors['b2_key_id'] = 'Application Key ID bos birakilamaz.';
		} elseif ( ! preg_match( '/^[A-Za-z0-9]{8,64}$/', $key_id ) ) {
			$errors['b2_key_id'] = 'Application Key ID yalnizca harf ve rakamlardan olusmali (8-64 karakter). Backblaze panelindeki keyID degerini yapistirin.';
		}
		$values['b2_key_id'] = $key_id;

		$app_key = isset( $input['b2_app_key'] ) ? trim( (string) $input['b2_app_key'] ) : '';
		if ( '' === $app_key ) {
			$errors['b2_app_key'] = 'Application Key bos birakilamaz.';
		} elseif ( strlen( $app_key ) < 20 || strlen( $app_key ) > 128 ) {
			$errors['b2_app_key'] = 'Application Key uzunlugu beklenenden farkli (20-128 karakter olmali).';
		} elseif ( preg_match( '/\s/', $app_key ) ) {
			$errors['b2_app_key'] = 'Application Key bosluk icermemeli; kopyalarken fazladan karakter alinmis olabilir.';
		}
		$values['b2_app_key'] = $app_key;

		$bucket = isset( $input['b2_bucket_name'] ) ? trim( (string) $input['b2_bucket_name'] ) : '';
		if ( '' === $bucket ) {
			$errors['b2_bucket_name'] = 'Bucket adi bos birakilamaz.';
		} else {
			$bucket_lc = strtolower( $bucket );
			if ( ! preg_match( '/^[a-z0-9][a-z0-9-]{4,48}[a-z0-9]$/', $bucket_lc ) ) {
				$errors['b2_bucket_name'] = 'Bucket adi 6-50 karakter olmali; sadece kucuk harf, rakam ve tire icerebilir.';
			} elseif ( 0 === strpos( $bucket_lc, 'b2-' ) ) {
				$errors['b2_bucket_name'] = 'Bucket adi "b2-" ile baslayamaz (Backblaze kurali).';
			}
			$bucket = $bucket_lc;
		}
		$values['b2_bucket_name'] = $bucket;

		$bucket_id = isset( $input['b2_bucket_id'] ) ? trim( (string) $input['b2_bucket_id'] ) : '';
		if ( '' !== $bucket_id && ! preg_match( '/^[a-f0-9]{16,40}$/i', $bucket_id ) ) {
			$errors['b2_bucket_id'] = 'Bucket ID gecersiz gorunuyor (16-40 karakterlik onaltilik deger bekleniyor). Bos birakirsaniz baglanti testi otomatik doldurur.';
		}
		$values['b2_bucket_id'] = $bucket_id;

		$base_url = isset( $input['b2_public_base_url'] ) ? trim( (string) $input['b2_public_base_url'] ) : '';
		if ( '' !== $base_url ) {
			if ( ! preg_match( '#^https://[^\s]+$#i', $base_url ) ) {
				$errors['b2_public_base_url'] = 'Genel erisim adresi https:// ile baslamali.';
			} else {
				$base_url = rtrim( $base_url, '/' );
			}
		}
		$values['b2_public_base_url'] = $base_url;

		$ttl = isset( $input['b2_link_ttl'] ) ? (int) $input['b2_link_ttl'] : 3600;
		if ( $ttl < 60 || $ttl > 604800 ) {
			$errors['b2_link_ttl'] = 'Baglanti gecerlilik suresi 60 ile 604800 saniye arasinda olmali.';
			$ttl = min( 604800, max( 60, $ttl ) );
		}
		$values['b2_link_ttl'] = $ttl;

		$max_mb = isset( $input['b2_max_upload_mb'] ) ? (int) $input['b2_max_upload_mb'] : 25;
		if ( $max_mb < 1 || $max_mb > 200 ) {
			$errors['b2_max_upload_mb'] = 'Azami dosya boyutu 1 ile 200 MB arasinda olmali.';
			$max_mb = min( 200, max( 1, $max_mb ) );
		}
		$values['b2_max_upload_mb'] = $max_mb;

		$prefix = isset( $input['b2_path_prefix'] ) ? trim( (string) $input['b2_path_prefix'] ) : 'naber/';
		$prefix = ltrim( $prefix, '/' );
		if ( '' !== $prefix ) {
			if ( ! preg_match( '#^[A-Za-z0-9._/-]+$#', $prefix ) ) {
				$errors['b2_path_prefix'] = 'Klasor onekinde yalnizca harf, rakam, nokta, tire, alt tire ve / kullanilabilir.';
			} else {
				$prefix = rtrim( $prefix, '/' ) . '/';
			}
		}
		$values['b2_path_prefix'] = $prefix;

		return array(
			'valid'  => empty( $errors ),
			'errors' => $errors,
			'values' => $values,
		);
	}

	/** B2 ayarlari eksiksiz mi? (medya yuklemesi icin gerekli minimum) */
	public static function b2_ready() {
		$all = self::all();
		return '' !== $all['b2_key_id'] && '' !== $all['b2_app_key'] && '' !== $all['b2_bucket_name'];
	}

	/** Elle girilen STUN/TURN adreslerinden iceServers listesi uretir. */
	public static function manual_ice_servers( $settings = null ) {
		$all    = null === $settings ? self::all() : array_merge( self::defaults(), $settings );
		$result = array();

		foreach ( preg_split( '/[\s,]+/', (string) $all['stun_urls'] ) as $stun ) {
			if ( '' !== trim( $stun ) ) {
				$result[] = array( 'urls' => trim( $stun ) );
			}
		}

		$turn_urls = array();
		foreach ( preg_split( '/[\s,]+/', (string) $all['turn_urls'] ) as $turn ) {
			if ( '' !== trim( $turn ) ) {
				$turn_urls[] = trim( $turn );
			}
		}
		if ( $turn_urls ) {
			$result[] = array(
				'urls'       => $turn_urls,
				'username'   => (string) $all['turn_username'],
				'credential' => (string) $all['turn_credential'],
			);
		}

		return $result;
	}

	/** Uygulamaya verilecek nihai liste (elle girilenler + Metered). */
	public static function ice_servers() {
		return ( new Naber_Turn() )->ice_servers();
	}

	/** Yonetim ekraninda gosterilecek maskeli deger. */
	public static function mask( $secret ) {
		$secret = (string) $secret;
		$len    = strlen( $secret );
		if ( 0 === $len ) {
			return '';
		}
		if ( $len <= 6 ) {
			return str_repeat( '*', $len );
		}
		return substr( $secret, 0, 3 ) . str_repeat( '*', max( 4, $len - 6 ) ) . substr( $secret, -3 );
	}
}
