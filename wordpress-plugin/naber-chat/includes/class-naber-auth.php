<?php
/**
 * Token tabanli oturum. WordPress kullanici sistemi aynen kullanilir;
 * ikinci bir kullanici tablosu olusturulmaz.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Auth {

	const META_TOKENS   = 'naber_tokens';
	const META_LASTSEEN = 'naber_last_seen';
	const MAX_TOKENS    = 5;
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

	/** Token -> user_id (gecersizse 0). */
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

	public static function touch_presence( $user_id ) {
		update_user_meta( $user_id, self::META_LASTSEEN, time() );
	}

	public static function is_online( $user_id ) {
		$last = (int) get_user_meta( $user_id, self::META_LASTSEEN, true );
		return $last > 0 && ( time() - $last ) < 70;
	}

	public static function is_admin_user( $user_id ) {
		$user = get_userdata( $user_id );
		return $user && ( user_can( $user, 'manage_options' ) || in_array( 'administrator', (array) $user->roles, true ) );
	}

	public static function is_disabled( $user_id ) {
		return '1' === (string) get_user_meta( $user_id, 'naber_disabled', true );
	}

	/** Uygulamaya donen kullanici gosterimi. */
	public static function user_payload( $user, $include_private = false ) {
		if ( is_numeric( $user ) ) {
			$user = get_userdata( (int) $user );
		}
		if ( ! $user ) {
			return null;
		}
		$last = (int) get_user_meta( $user->ID, self::META_LASTSEEN, true );
		$data = array(
			'id'           => (int) $user->ID,
			'username'     => $user->user_login,
			'display_name' => $user->display_name,
			'avatar'       => (string) get_user_meta( $user->ID, 'naber_avatar_url', true ),
			'about'        => (string) get_user_meta( $user->ID, 'naber_about', true ),
			'last_seen'    => $last,
			'online'       => self::is_online( $user->ID ),
			'is_admin'     => self::is_admin_user( $user->ID ),
		);
		if ( $include_private ) {
			$data['email']    = $user->user_email;
			$data['roles']    = array_values( (array) $user->roles );
			$data['disabled'] = self::is_disabled( $user->ID );
		}
		return $data;
	}
}
