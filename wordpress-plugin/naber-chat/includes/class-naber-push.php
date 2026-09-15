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

	public static function device_count( $user_id = 0 ) {
		global $wpdb;
		$table = Naber_DB::table( 'devices' );
		if ( $user_id ) {
			return (int) $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM {$table} WHERE user_id = %d", (int) $user_id ) );
		}
		return (int) $wpdb->get_var( "SELECT COUNT(*) FROM {$table}" );
	}

	public static function last_error() {
		$error = get_option( 'naber_push_last_error' );
		return is_array( $error ) ? $error : array();
	}

	private static function remember_error( $message ) {
		update_option( 'naber_push_last_error', array( 'message' => (string) $message, 'at' => time() ), false );
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
			self::remember_error( $access->get_error_message() );
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

			if ( is_wp_error( $res ) ) {
				self::remember_error( $res->get_error_message() );
				continue;
			}

			$code = (int) wp_remote_retrieve_response_code( $res );
			if ( $code >= 200 && $code < 300 ) {
				$sent++;
				continue;
			}

			$body = wp_remote_retrieve_body( $res );
			self::remember_error( 'FCM ' . $code . ': ' . substr( (string) $body, 0, 300 ) );

			// Gecersiz veya silinmis cihaz jetonlari temizlenir.
			if ( 404 === $code || ( 400 === $code && false !== stripos( (string) $body, 'not a valid FCM registration token' ) ) ) {
				self::unregister_device( $token );
			}
		}

		return $sent > 0;
	}

	/**
	 * Yonetim panelinden calistirilan deneme bildirimi.
	 * FCM'in dondurdugu hata aynen gosterilir; sessiz basarisizlik olmaz.
	 *
	 * @return array{ok:bool,steps:array,message:string}
	 */
	public static function send_test( $user_id ) {
		$steps = array();

		$project = (string) Naber_Settings::get( 'fcm_project_id' );
		$json    = (string) Naber_Settings::get( 'fcm_service_account' );
		$creds   = json_decode( $json, true );

		$steps[] = array(
			'key'     => 'config',
			'ok'      => '' !== $project && is_array( $creds ) && ! empty( $creds['client_email'] ),
			'label'   => 'Yapilandirma',
			'message' => '' === $project
				? 'Firebase proje kimligi girilmemis.'
				: ( is_array( $creds ) && ! empty( $creds['client_email'] )
					? 'Proje: ' . $project . ' / servis hesabi: ' . $creds['client_email']
					: 'Servis hesabi JSON gecersiz veya girilmemis.' ),
		);

		if ( ! $steps[0]['ok'] ) {
			return array( 'ok' => false, 'steps' => $steps, 'message' => 'Firebase ayarlari eksik.' );
		}

		if ( is_array( $creds ) && ! empty( $creds['project_id'] ) && $creds['project_id'] !== $project ) {
			$steps[] = array(
				'key'     => 'project_match',
				'ok'      => false,
				'label'   => 'Proje eslesmesi',
				'message' => 'Servis hesabi "' . $creds['project_id'] . '" projesine ait, ayarlarda "' . $project . '" yaziyor.',
			);
			return array( 'ok' => false, 'steps' => $steps, 'message' => 'Proje kimligi servis hesabiyla uyusmuyor.' );
		}

		$tokens  = self::tokens_for_user( $user_id );
		$steps[] = array(
			'key'     => 'devices',
			'ok'      => ! empty( $tokens ),
			'label'   => 'Kayitli cihaz',
			'message' => $tokens
				? count( $tokens ) . ' cihaz kayitli.'
				: 'Bu hesap icin kayitli cihaz yok. Uygulamayi acip giris yapin (bildirim izni verilmeli).',
		);

		if ( ! $tokens ) {
			return array( 'ok' => false, 'steps' => $steps, 'message' => 'Gonderilecek cihaz bulunamadi.' );
		}

		$access = self::access_token();
		if ( is_wp_error( $access ) ) {
			$steps[] = array( 'key' => 'token', 'ok' => false, 'label' => 'Google yetkilendirmesi', 'message' => $access->get_error_message() );
			return array( 'ok' => false, 'steps' => $steps, 'message' => 'Google erisim jetonu alinamadi.' );
		}
		$steps[] = array( 'key' => 'token', 'ok' => true, 'label' => 'Google yetkilendirmesi', 'message' => 'Erisim jetonu alindi.' );

		$url     = 'https://fcm.googleapis.com/v1/projects/' . rawurlencode( $project ) . '/messages:send';
		$ok      = 0;
		$details = array();

		foreach ( $tokens as $token ) {
			$res = wp_remote_post( $url, array(
				'timeout' => 15,
				'headers' => array(
					'Authorization' => 'Bearer ' . $access,
					'Content-Type'  => 'application/json',
				),
				'body'    => wp_json_encode( array(
					'message' => array(
						'token'        => $token,
						'notification' => array( 'title' => 'Naber', 'body' => 'Bildirim testi basarili.' ),
						'data'         => array( 'type' => 'test' ),
						'android'      => array( 'priority' => 'HIGH' ),
					),
				) ),
			) );

			if ( is_wp_error( $res ) ) {
				$details[] = 'Baglanti hatasi: ' . $res->get_error_message();
				continue;
			}

			$code = (int) wp_remote_retrieve_response_code( $res );
			if ( $code >= 200 && $code < 300 ) {
				$ok++;
				continue;
			}

			$body      = json_decode( wp_remote_retrieve_body( $res ), true );
			$message   = isset( $body['error']['message'] ) ? $body['error']['message'] : wp_remote_retrieve_body( $res );
			$details[] = 'HTTP ' . $code . ': ' . substr( (string) $message, 0, 200 );

			if ( 404 === $code ) {
				self::unregister_device( $token );
			}
		}

		$steps[] = array(
			'key'     => 'send',
			'ok'      => $ok > 0,
			'label'   => 'Gonderim',
			'message' => $ok > 0
				? $ok . ' cihaza gonderildi. Telefonunuzda bildirimi gormelisiniz.'
				: implode( ' | ', $details ),
		);

		if ( $ok < 1 && $details ) {
			self::remember_error( implode( ' | ', $details ) );
		}

		return array(
			'ok'      => $ok > 0,
			'steps'   => $steps,
			'message' => $ok > 0 ? 'Deneme bildirimi gonderildi.' : 'Bildirim gonderilemedi.',
		);
	}

	private static function b64( $value ) {
		return rtrim( strtr( base64_encode( $value ), '+/', '-_' ), '=' );
	}
}
