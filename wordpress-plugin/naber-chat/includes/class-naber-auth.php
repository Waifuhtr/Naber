<?php
/**
 * Token tabanli oturum, kullanici gosterimi, Naber adresi (yapay e-posta) ve kisi listesi.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Auth {

	const META_TOKENS   = 'naber_tokens';
	const META_LASTSEEN    = 'naber_last_seen';
	const META_ONLINE_UNTIL = 'naber_online_until';
	const ONLINE_WINDOW     = 45;
	const META_AVATAR   = 'naber_avatar_media_id';
	const META_CONTACTS = 'naber_contacts';
	const MAX_TOKENS    = 5;

	/** @var array Istek suresince kullanici gosterimlerini onbellekler. */
	private static $payload_cache = array();

	/** @var array */
	private static $presence_cache = array();
	const TOKEN_TTL     = 7776000; // 90 gun.

	public static function init() {
		add_filter( 'determine_current_user', array( __CLASS__, 'determine_current_user' ), 20 );
	}

	public static function bearer_token() {
		$header = '';
		if ( isset( $_SERVER['HTTP_AUTHORIZATION'] ) ) {
			$header = wp_unslash( $_SERVER['HTTP_AUTHORIZATION'] );
		} elseif ( isset( $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ) ) {
			$header = wp_unslash( $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] );
		} elseif ( function_exists( 'getallheaders' ) ) {
			foreach ( (array) getallheaders() as $key => $value ) {
				if ( 'authorization' === strtolower( $key ) ) {
					$header = $value;
					break;
				}
			}
		}
		if ( ! $header || stripos( $header, 'bearer ' ) !== 0 ) {
			return '';
		}
		return trim( substr( $header, 7 ) );
	}

	public static function determine_current_user( $user_id ) {
		if ( $user_id ) {
			return $user_id;
		}
		$token = self::bearer_token();
		if ( '' === $token ) {
			return $user_id;
		}
		$found = self::user_for_token( $token );
		return $found ? $found : $user_id;
	}

	public static function hash_token( $token ) {
		return hash( 'sha256', $token );
	}

	public static function user_for_token( $token ) {
		global $wpdb;
		$hash  = self::hash_token( $token );
		$users = $wpdb->get_col(
			$wpdb->prepare(
				"SELECT user_id FROM {$wpdb->usermeta} WHERE meta_key = %s AND meta_value LIKE %s LIMIT 5",
				self::META_TOKENS,
				'%' . $wpdb->esc_like( $hash ) . '%'
			)
		);
		foreach ( $users as $user_id ) {
			$tokens = get_user_meta( (int) $user_id, self::META_TOKENS, true );
			if ( ! is_array( $tokens ) || ! isset( $tokens[ $hash ] ) ) {
				continue;
			}
			if ( time() - (int) $tokens[ $hash ]['created'] > self::TOKEN_TTL ) {
				unset( $tokens[ $hash ] );
				update_user_meta( (int) $user_id, self::META_TOKENS, $tokens );
				continue;
			}
			return (int) $user_id;
		}
		return 0;
	}

	public static function issue_token( $user_id, $device = '' ) {
		$token  = wp_generate_password( 48, false, false );
		$hash   = self::hash_token( $token );
		$tokens = get_user_meta( $user_id, self::META_TOKENS, true );
		if ( ! is_array( $tokens ) ) {
			$tokens = array();
		}
		$tokens[ $hash ] = array(
			'created' => time(),
			'device'  => substr( (string) $device, 0, 120 ),
		);
		if ( count( $tokens ) > self::MAX_TOKENS ) {
			uasort( $tokens, function ( $a, $b ) {
				return $a['created'] <=> $b['created'];
			} );
			$tokens = array_slice( $tokens, count( $tokens ) - self::MAX_TOKENS, null, true );
		}
		update_user_meta( $user_id, self::META_TOKENS, $tokens );
		return $token;
	}

	public static function revoke_token( $user_id, $token ) {
		$tokens = get_user_meta( $user_id, self::META_TOKENS, true );
		if ( ! is_array( $tokens ) ) {
			return;
		}
		unset( $tokens[ self::hash_token( $token ) ] );
		update_user_meta( $user_id, self::META_TOKENS, $tokens );
	}

	public static function revoke_all_tokens( $user_id ) {
		delete_user_meta( $user_id, self::META_TOKENS );
	}

	// ------------------------------------------------------------------
	// Naber adresi (uygulamaya ozel yapay e-posta)
	// ------------------------------------------------------------------

	/**
	 * Kullanici adindan e-posta yerel kismini uretir.
	 * Turkce karakterler sadelestirilir, gecersiz karakterler atilir.
	 */
	public static function email_local_part( $username ) {
		$map = array(
			'ç' => 'c', 'ğ' => 'g', 'ı' => 'i', 'ö' => 'o', 'ş' => 's', 'ü' => 'u',
			'Ç' => 'c', 'Ğ' => 'g', 'İ' => 'i', 'Ö' => 'o', 'Ş' => 's', 'Ü' => 'u',
		);
		$local = strtr( (string) $username, $map );
		$local = strtolower( $local );
		$local = preg_replace( '/[^a-z0-9._-]/', '', $local );
		$local = trim( (string) $local, '._-' );
		if ( '' === $local ) {
			$local = 'naber';
		}
		return substr( $local, 0, 40 );
	}

	public static function email_domain() {
		$domain = trim( (string) Naber_Settings::get( 'email_domain', 'naber.com' ) );
		return '' === $domain ? 'naber.com' : strtolower( $domain );
	}

	/**
	 * Kullaniciya benzersiz bir Naber adresi uretir: kullaniciadi@naber.com
	 * Adres doluysa sonuna sayi eklenir.
	 *
	 * @param callable|null $exists Test edilebilirlik icin: adres kullaniliyorsa true donen fonksiyon.
	 */
	public static function build_naber_email( $username, $exists = null ) {
		$local  = self::email_local_part( $username );
		$domain = self::email_domain();
		if ( null === $exists ) {
			$exists = function ( $candidate ) {
				return (bool) email_exists( $candidate );
			};
		}

		$candidate = $local . '@' . $domain;
		$suffix    = 1;
		while ( call_user_func( $exists, $candidate ) && $suffix < 500 ) {
			$suffix++;
			$candidate = $local . $suffix . '@' . $domain;
		}
		return $candidate;
	}

	/** Bu adres bizim uygulamamizin adresi mi? */
	public static function is_naber_email( $email ) {
		$email = strtolower( trim( (string) $email ) );
		return '' !== $email && substr( $email, -strlen( '@' . self::email_domain() ) ) === '@' . self::email_domain();
	}

	// ------------------------------------------------------------------
	// Durum bilgileri
	// ------------------------------------------------------------------

	/**
	 * Cevrimici damgasi.
	 * "son gorulme" (META_LASTSEEN) gercek zamani tutar; "cevrimici" bilgisi ise
	 * META_ONLINE_UNTIL damgasindan okunur. Uygulama arka plana atilinca bu damga
	 * hemen gecmise cekilir, boylece karsi taraf aninda "son gorulme" gorur.
	 */
	public static function touch_presence( $user_id, $force = false ) {
		$user_id = (int) $user_id;
		$now     = time();

		if ( ! $force && isset( self::$presence_cache[ $user_id ] ) && ( $now - self::$presence_cache[ $user_id ] ) < 15 ) {
			return;
		}
		self::$presence_cache[ $user_id ] = $now;

		$until = (int) get_user_meta( $user_id, self::META_ONLINE_UNTIL, true );
		if ( ! $force && $until - $now > self::ONLINE_WINDOW - 15 ) {
			return;
		}

		update_user_meta( $user_id, self::META_LASTSEEN, $now );
		update_user_meta( $user_id, self::META_ONLINE_UNTIL, $now + self::ONLINE_WINDOW );
		self::flush_payload_cache( $user_id );
	}

	/** Uygulama arka plana alindiginda cagrilir: aninda cevrimdisi gorunur. */
	public static function set_offline( $user_id ) {
		$user_id = (int) $user_id;
		update_user_meta( $user_id, self::META_LASTSEEN, time() );
		update_user_meta( $user_id, self::META_ONLINE_UNTIL, time() - 1 );
		unset( self::$presence_cache[ $user_id ] );
		self::flush_payload_cache( $user_id );
	}

	/**
	 * Birden cok kullanicinin durumu tek sorguda.
	 *
	 * @return array user_id => array('online' => bool, 'last_seen' => int)
	 */
	public static function presence_of( array $ids ) {
		global $wpdb;

		$ids = array_values( array_unique( array_filter( array_map( 'intval', $ids ) ) ) );
		if ( ! $ids ) {
			return array();
		}

		$list = implode( ',', $ids );
		$rows = $wpdb->get_results(
			"SELECT user_id, meta_key, meta_value FROM {$wpdb->usermeta}
			 WHERE user_id IN ({$list}) AND meta_key IN ('" . self::META_LASTSEEN . "', '" . self::META_ONLINE_UNTIL . "')",
			ARRAY_A
		);

		$last  = array();
		$until = array();
		foreach ( (array) $rows as $row ) {
			if ( self::META_LASTSEEN === $row['meta_key'] ) {
				$last[ (int) $row['user_id'] ] = (int) $row['meta_value'];
			} else {
				$until[ (int) $row['user_id'] ] = (int) $row['meta_value'];
			}
		}

		$now = time();
		$out = array();
		foreach ( $ids as $id ) {
			$seen         = isset( $last[ $id ] ) ? $last[ $id ] : 0;
			$online_until = isset( $until[ $id ] ) ? $until[ $id ] : 0;
			$out[ $id ]   = array(
				// Eski kurulumlarda online_until yoksa son gorulme zamanina bakilir.
				'online'    => $online_until > 0 ? ( $online_until > $now ) : ( $seen > 0 && ( $now - $seen ) < self::ONLINE_WINDOW ),
				'last_seen' => $seen,
			);
		}
		return $out;
	}

	public static function is_online( $user_id ) {
		$until = (int) get_user_meta( $user_id, self::META_ONLINE_UNTIL, true );
		if ( $until > 0 ) {
			return $until > time();
		}
		$last = (int) get_user_meta( $user_id, self::META_LASTSEEN, true );
		return $last > 0 && ( time() - $last ) < self::ONLINE_WINDOW;
	}

	public static function is_admin_user( $user_id ) {
		$user = get_userdata( $user_id );
		return $user && ( user_can( $user, 'manage_options' ) || in_array( 'administrator', (array) $user->roles, true ) );
	}

	public static function is_disabled( $user_id ) {
		return '1' === (string) get_user_meta( $user_id, 'naber_disabled', true );
	}

	/** Yasakli kullanici giris yapamaz ve listelerde gorunmez. */
	public static function is_banned( $user_id ) {
		return '1' === (string) get_user_meta( $user_id, 'naber_banned', true );
	}

	public static function ban_reason( $user_id ) {
		return (string) get_user_meta( $user_id, 'naber_ban_reason', true );
	}

	public static function set_banned( $user_id, $banned, $reason = '' ) {
		if ( $banned ) {
			update_user_meta( $user_id, 'naber_banned', '1' );
			update_user_meta( $user_id, 'naber_ban_reason', sanitize_text_field( $reason ) );
			update_user_meta( $user_id, 'naber_banned_at', time() );
			self::revoke_all_tokens( $user_id );
		} else {
			delete_user_meta( $user_id, 'naber_banned' );
			delete_user_meta( $user_id, 'naber_ban_reason' );
			delete_user_meta( $user_id, 'naber_banned_at' );
		}
	}

	public static function avatar_url( $user_id ) {
		$media_id = (int) get_user_meta( $user_id, self::META_AVATAR, true );
		if ( $media_id > 0 ) {
			// Imzali adres her cagride tazelenir; eskiyen baglanti sorunu olmaz.
			return Naber_Media::url_for_id( $media_id );
		}
		return (string) get_user_meta( $user_id, 'naber_avatar_url', true );
	}

	// ------------------------------------------------------------------
	// Kisi listesi
	// ------------------------------------------------------------------

	public static function contacts( $user_id ) {
		$ids = get_user_meta( $user_id, self::META_CONTACTS, true );
		return is_array( $ids ) ? array_values( array_unique( array_map( 'intval', $ids ) ) ) : array();
	}

	public static function add_contact( $user_id, $contact_id ) {
		$ids = self::contacts( $user_id );
		if ( ! in_array( (int) $contact_id, $ids, true ) ) {
			$ids[] = (int) $contact_id;
			update_user_meta( $user_id, self::META_CONTACTS, $ids );
		}
		return true;
	}

	public static function remove_contact( $user_id, $contact_id ) {
		$ids = array_values( array_diff( self::contacts( $user_id ), array( (int) $contact_id ) ) );
		update_user_meta( $user_id, self::META_CONTACTS, $ids );
		return true;
	}

	// ------------------------------------------------------------------

	/** Profil degistiginde onbellegi temizler. */
	public static function flush_payload_cache( $user_id = 0 ) {
		if ( $user_id ) {
			unset( self::$payload_cache[ (int) $user_id ], self::$payload_cache[ (int) $user_id . ':p' ] );
			return;
		}
		self::$payload_cache = array();
	}

	public static function user_payload( $user, $include_private = false ) {
		$cache_key = null;
		if ( is_numeric( $user ) ) {
			$cache_key = (int) $user . ( $include_private ? ':p' : '' );
			if ( isset( self::$payload_cache[ $cache_key ] ) ) {
				return self::$payload_cache[ $cache_key ];
			}
			$user = get_userdata( (int) $user );
		}
		if ( ! $user ) {
			return null;
		}
		if ( null === $cache_key ) {
			$cache_key = (int) $user->ID . ( $include_private ? ':p' : '' );
			if ( isset( self::$payload_cache[ $cache_key ] ) ) {
				return self::$payload_cache[ $cache_key ];
			}
		}
		$last = (int) get_user_meta( $user->ID, self::META_LASTSEEN, true );
		$data = array(
			'id'           => (int) $user->ID,
			'username'     => $user->user_login,
			'display_name' => $user->display_name,
			'naber_email'  => $user->user_email,
			'avatar'       => self::avatar_url( $user->ID ),
			'avatar_id'    => (int) get_user_meta( $user->ID, self::META_AVATAR, true ),
			'about'        => (string) get_user_meta( $user->ID, 'naber_about', true ),
			'last_seen'    => $last,
			'online'       => self::is_online( $user->ID ),
			'is_admin'     => self::is_admin_user( $user->ID ),
		);
		if ( $include_private ) {
			$data['email']      = $user->user_email;
			$data['roles']      = array_values( (array) $user->roles );
			$data['disabled']   = self::is_disabled( $user->ID );
			$data['banned']     = self::is_banned( $user->ID );
			$data['ban_reason'] = self::ban_reason( $user->ID );
			$data['registered'] = strtotime( $user->user_registered . ' UTC' );
		}

		self::$payload_cache[ $cache_key ] = $data;
		return $data;
	}
}
