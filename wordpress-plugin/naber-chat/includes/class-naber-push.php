<?php
/**
 * Firebase Cloud Messaging (HTTP v1) bildirimleri.
 * Servis hesabi JSON'u yalnizca WordPress ayarlarinda tutulur.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Push {

	const TOKEN_TRANSIENT = 'naber_fcm_access_token';

	public static function register_device( $user_id, $token, $platform = 'android' ) {
		global $wpdb;
		$table = Naber_DB::table( 'devices' );
		$token = trim( (string) $token );
		if ( '' === $token ) {
			return false;
		}
		$wpdb->query(
			$wpdb->prepare(
				"INSERT INTO {$table} (user_id, token, platform, updated_at) VALUES (%d, %s, %s, %s)
				 ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), platform = VALUES(platform), updated_at = VALUES(updated_at)",
				$user_id,
				$token,
				$platform,
				Naber_DB::now()
			)
		);
		return true;
	}

	public static function unregister_device( $token ) {
		global $wpdb;
		return (bool) $wpdb->delete( Naber_DB::table( 'devices' ), array( 'token' => (string) $token ), array( '%s' ) );
	}

	public static function tokens_for_user( $user_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'devices' );
		return (array) $wpdb->get_col( $wpdb->prepare( "SELECT token FROM {$table} WHERE user_id = %d", $user_id ) );
	}

	public static function is_configured() {
		$project = (string) Naber_Settings::get( 'fcm_project_id' );
		$json    = (string) Naber_Settings::get( 'fcm_service_account' );
		return '' !== $project && '' !== $json;
	}

	/** Servis hesabi JSON'undan OAuth2 erisim jetonu uretir. */
	public static function access_token() {
		$cached = get_transient( self::TOKEN_TRANSIENT );
		if ( $cached ) {
			return $cached;
		}

		$creds = json_decode( (string) Naber_Settings::get( 'fcm_service_account' ), true );
		if ( ! is_array( $creds ) || empty( $creds['client_email'] ) || empty( $creds['private_key'] ) ) {
			return new WP_Error( 'naber_fcm_config', 'FCM servis hesabi JSON gecersiz.' );
		}

		$now    = time();
		$header = self::b64( wp_json_encode( array( 'alg' => 'RS256', 'typ' => 'JWT' ) ) );
		$claim  = self::b64( wp_json_encode( array(
			'iss'   => $creds['client_email'],
			'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
			'aud'   => 'https://oauth2.googleapis.com/token',
			'iat'   => $now,
			'exp'   => $now + 3600,
		) ) );

		$signature = '';
		if ( ! function_exists( 'openssl_sign' ) || ! openssl_sign( $header . '.' . $claim, $signature, $creds['private_key'], 'sha256WithRSAEncryption' ) ) {
			return new WP_Error( 'naber_fcm_sign', 'JWT imzalanamadi (openssl eklentisi gerekli).' );
		}

		$assertion = $header . '.' . $claim . '.' . self::b64( $signature );
		$response  = wp_remote_post( 'https://oauth2.googleapis.com/token', array(
			'timeout' => 20,
			'body'    => array(
				'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
				'assertion'  => $assertion,
			),
		) );

		if ( is_wp_error( $response ) ) {
			return $response;
		}
		$body = json_decode( wp_remote_retrieve_body( $response ), true );
		if ( empty( $body['access_token'] ) ) {
			return new WP_Error( 'naber_fcm_token', 'FCM erisim jetonu alinamadi.' );
		}

		set_transient( self::TOKEN_TRANSIENT, $body['access_token'], 3000 );
		return $body['access_token'];
	}

	/**
	 * Kullaniciya bildirim gonderir. Yapilandirilmamissa sessizce gecer;
	 * uygulama zaten canli olay akisindan mesaji alir.
	 */
	public static function send_to_user( $user_id, array $notification, array $data = array(), $high_priority = false ) {
		if ( ! self::is_configured() ) {
			return false;
		}
		$tokens = self::tokens_for_user( $user_id );
		if ( ! $tokens ) {
			return false;
		}
		$access = self::access_token();
		if ( is_wp_error( $access ) ) {
			return false;
		}

		$project = (string) Naber_Settings::get( 'fcm_project_id' );
		$url     = 'https://fcm.googleapis.com/v1/projects/' . rawurlencode( $project ) . '/messages:send';
		$sent    = 0;

		foreach ( $tokens as $token ) {
			$message = array(
				'message' => array(
					'token'   => $token,
					'data'    => array_map( 'strval', $data ),
					'android' => array(
						'priority' => $high_priority ? 'HIGH' : 'NORMAL',
					),
				),
			);
			// Arama bildirimleri uygulama tarafinda tam ekran gosterildigi icin
			// yalnizca data mesaji olarak gonderilir.
			if ( ! empty( $notification ) && empty( $data['silent'] ) && 'call' !== ( isset( $data['type'] ) ? $data['type'] : '' ) ) {
				$message['message']['notification'] = $notification;
			}

			$res = wp_remote_post( $url, array(
				'timeout' => 15,
				'headers' => array(
					'Authorization' => 'Bearer ' . $access,
					'Content-Type'  => 'application/json',
				),
				'body'    => wp_json_encode( $message ),
			) );

			if ( ! is_wp_error( $res ) ) {
				$code = (int) wp_remote_retrieve_response_code( $res );
				if ( $code >= 200 && $code < 300 ) {
					$sent++;
				} elseif ( 404 === $code || 400 === $code ) {
					self::unregister_device( $token );
				}
			}
		}

		return $sent > 0;
	}

	private static function b64( $value ) {
		return rtrim( strtr( base64_encode( $value ), '+/', '-_' ), '=' );
	}
}
