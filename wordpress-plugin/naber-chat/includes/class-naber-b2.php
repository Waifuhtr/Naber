<?php
/**
 * Backblaze B2 istemcisi.
 * Tum kimlik bilgileri yalnizca sunucuda tutulur; Android uygulamasi
 * sadece kisa omurlu upload/download yetkilerini gorur.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_B2 {

	const AUTH_URL       = 'https://api.backblazeb2.com/b2api/v3/b2_authorize_account';
	const AUTH_TRANSIENT = 'naber_b2_auth';

	/** @var callable|null Testlerde HTTP katmanini degistirmek icin. */
	private $http;

	/** @var array */
	private $settings;

	public function __construct( $settings = null, $http = null ) {
		$this->settings = null === $settings ? Naber_Settings::all() : $settings;
		$this->http     = $http;
	}

	/**
	 * @return array{ok:bool,status:int,body:array,error:string}
	 */
	private function request( $method, $url, $headers = array(), $body = null ) {
		if ( is_callable( $this->http ) ) {
			return call_user_func( $this->http, $method, $url, $headers, $body );
		}

		$args = array(
			'method'  => $method,
			'timeout' => 30,
			'headers' => $headers,
		);
		if ( null !== $body ) {
			$args['body'] = $body;
		}

		$response = wp_remote_request( $url, $args );
		if ( is_wp_error( $response ) ) {
			return array(
				'ok'     => false,
				'status' => 0,
				'body'   => array(),
				'error'  => $response->get_error_message(),
			);
		}

		$status = (int) wp_remote_retrieve_response_code( $response );
		$raw    = wp_remote_retrieve_body( $response );
		$json   = json_decode( $raw, true );
		if ( ! is_array( $json ) ) {
			$json = array();
		}

		$error = '';
		if ( $status < 200 || $status >= 300 ) {
			$error = isset( $json['message'] ) ? (string) $json['message'] : ( '' !== $raw ? substr( $raw, 0, 200 ) : 'HTTP ' . $status );
		}

		return array(
			'ok'     => ( $status >= 200 && $status < 300 ),
			'status' => $status,
			'body'   => $json,
			'error'  => $error,
		);
	}

	/**
	 * Hesap yetkilendirmesi. Basarili sonuc 23 saat onbelleklenir.
	 *
	 * @param bool $fresh Onbellegi atla.
	 * @return array|WP_Error
	 */
	public function authorize( $fresh = false ) {
		$key_id  = (string) $this->settings['b2_key_id'];
		$app_key = (string) $this->settings['b2_app_key'];

		if ( '' === $key_id || '' === $app_key ) {
			return new WP_Error( 'naber_b2_missing', 'Backblaze anahtarlari girilmemis. WordPress yoneticisi > Naber Chat > Depolama ekranindan girin.' );
		}

		if ( ! $fresh && function_exists( 'get_transient' ) ) {
			$cached = get_transient( self::AUTH_TRANSIENT );
			if ( is_array( $cached ) && isset( $cached['key_id'] ) && $cached['key_id'] === $key_id ) {
				return $cached;
			}
		}

		$res = $this->request(
			'GET',
			self::AUTH_URL,
			array( 'Authorization' => 'Basic ' . base64_encode( $key_id . ':' . $app_key ) )
		);

		if ( ! $res['ok'] ) {
			$hint = '';
			if ( 401 === $res['status'] ) {
				$hint = ' Key ID veya Application Key hatali gorunuyor.';
			}
			return new WP_Error( 'naber_b2_auth_failed', 'Backblaze yetkilendirmesi basarisiz: ' . $res['error'] . $hint, array( 'status' => $res['status'] ) );
		}

		$body    = $res['body'];
		$storage = isset( $body['apiInfo']['storageApi'] ) ? $body['apiInfo']['storageApi'] : array();

		$auth = array(
			'key_id'         => $key_id,
			'account_id'     => isset( $body['accountId'] ) ? (string) $body['accountId'] : '',
			'token'          => isset( $body['authorizationToken'] ) ? (string) $body['authorizationToken'] : '',
			'api_url'        => isset( $storage['apiUrl'] ) ? (string) $storage['apiUrl'] : '',
			'download_url'   => isset( $storage['downloadUrl'] ) ? (string) $storage['downloadUrl'] : '',
			'bucket_id'      => isset( $storage['bucketId'] ) ? (string) $storage['bucketId'] : '',
			'bucket_name'    => isset( $storage['bucketName'] ) ? (string) $storage['bucketName'] : '',
			'capabilities'   => isset( $storage['capabilities'] ) ? (array) $storage['capabilities'] : array(),
			'name_prefix'    => isset( $storage['namePrefix'] ) ? (string) $storage['namePrefix'] : '',
			'recommended_part_size' => isset( $storage['recommendedPartSize'] ) ? (int) $storage['recommendedPartSize'] : 0,
		);

		if ( '' === $auth['token'] || '' === $auth['api_url'] ) {
			return new WP_Error( 'naber_b2_auth_incomplete', 'Backblaze beklenen yanitı dondurmedi (apiUrl/authorizationToken eksik).' );
		}

		if ( function_exists( 'set_transient' ) ) {
			set_transient( self::AUTH_TRANSIENT, $auth, 23 * HOUR_IN_SECONDS );
		}

		return $auth;
	}

	public static function forget_auth() {
		if ( function_exists( 'delete_transient' ) ) {
			delete_transient( self::AUTH_TRANSIENT );
		}
	}

	private function api( $auth, $endpoint, array $payload ) {
		return $this->request(
			'POST',
			rtrim( $auth['api_url'], '/' ) . '/b2api/v3/' . $endpoint,
			array(
				'Authorization' => $auth['token'],
				'Content-Type'  => 'application/json',
			),
			wp_json_encode( $payload )
		);
	}

	/**
	 * Ayarlardaki bucket adina karsilik gelen bucket ID'sini bulur.
	 *
	 * @return array|WP_Error bucket bilgisi
	 */
	public function resolve_bucket( $auth = null ) {
		if ( null === $auth ) {
			$auth = $this->authorize();
		}
		if ( is_wp_error( $auth ) ) {
			return $auth;
		}

		$wanted = (string) $this->settings['b2_bucket_name'];
		if ( '' === $wanted ) {
			return new WP_Error( 'naber_b2_no_bucket', 'Bucket adi girilmemis.' );
		}

		// Anahtar tek bir bucket ile sinirlandirilmissa liste cagrisina gerek yok.
		if ( '' !== $auth['bucket_name'] ) {
			if ( strtolower( $auth['bucket_name'] ) !== strtolower( $wanted ) ) {
				return new WP_Error(
					'naber_b2_bucket_mismatch',
					sprintf( 'Bu anahtar yalnizca "%s" bucket\'ina erisebiliyor, ayarlarda ise "%s" yaziyor.', $auth['bucket_name'], $wanted )
				);
			}
			return array(
				'bucket_id'   => $auth['bucket_id'],
				'bucket_name' => $auth['bucket_name'],
				'bucket_type' => '',
				'restricted'  => true,
			);
		}

		$res = $this->api( $auth, 'b2_list_buckets', array(
			'accountId'  => $auth['account_id'],
			'bucketName' => $wanted,
		) );

		if ( ! $res['ok'] ) {
			return new WP_Error( 'naber_b2_list_failed', 'Bucket listesi alinamadi: ' . $res['error'] );
		}

		$buckets = isset( $res['body']['buckets'] ) ? (array) $res['body']['buckets'] : array();
		foreach ( $buckets as $bucket ) {
			if ( isset( $bucket['bucketName'] ) && strtolower( $bucket['bucketName'] ) === strtolower( $wanted ) ) {
				return array(
					'bucket_id'   => (string) $bucket['bucketId'],
					'bucket_name' => (string) $bucket['bucketName'],
					'bucket_type' => isset( $bucket['bucketType'] ) ? (string) $bucket['bucketType'] : '',
					'restricted'  => false,
				);
			}
		}

		return new WP_Error( 'naber_b2_bucket_not_found', sprintf( '"%s" adinda bir bucket bulunamadi. Bucket adini Backblaze panelindeki yazimla birebir girin.', $wanted ) );
	}

	/** Dogrudan yukleme icin tek kullanimlik upload URL'i. */
	public function get_upload_url( $bucket_id = '' ) {
		$auth = $this->authorize();
		if ( is_wp_error( $auth ) ) {
			return $auth;
		}
		if ( '' === $bucket_id ) {
			$bucket_id = (string) $this->settings['b2_bucket_id'];
		}
		if ( '' === $bucket_id ) {
			$bucket = $this->resolve_bucket( $auth );
			if ( is_wp_error( $bucket ) ) {
				return $bucket;
			}
			$bucket_id = $bucket['bucket_id'];
		}

		$res = $this->api( $auth, 'b2_get_upload_url', array( 'bucketId' => $bucket_id ) );
		if ( ! $res['ok'] ) {
			if ( 401 === $res['status'] ) {
				self::forget_auth();
			}
			return new WP_Error( 'naber_b2_upload_url_failed', 'Upload adresi alinamadi: ' . $res['error'] );
		}

		return array(
			'upload_url' => (string) $res['body']['uploadUrl'],
			'token'      => (string) $res['body']['authorizationToken'],
			'bucket_id'  => $bucket_id,
		);
	}

	/** Sunucu uzerinden dosya yukler (kucuk dosyalar / yedek yol). */
	public function upload( $file_name, $content, $mime ) {
		$target = $this->get_upload_url();
		if ( is_wp_error( $target ) ) {
			return $target;
		}

		$res = $this->request(
			'POST',
			$target['upload_url'],
			array(
				'Authorization'     => $target['token'],
				'X-Bz-File-Name'    => rawurlencode( $file_name ),
				'Content-Type'      => $mime ? $mime : 'b2/x-auto',
				'Content-Length'    => (string) strlen( $content ),
				'X-Bz-Content-Sha1' => sha1( $content ),
			),
			$content
		);

		if ( ! $res['ok'] ) {
			return new WP_Error( 'naber_b2_upload_failed', 'Dosya yuklenemedi: ' . $res['error'] );
		}

		return array(
			'file_id'   => isset( $res['body']['fileId'] ) ? (string) $res['body']['fileId'] : '',
			'file_name' => isset( $res['body']['fileName'] ) ? (string) $res['body']['fileName'] : $file_name,
			'size'      => isset( $res['body']['contentLength'] ) ? (int) $res['body']['contentLength'] : strlen( $content ),
			'mime'      => isset( $res['body']['contentType'] ) ? (string) $res['body']['contentType'] : $mime,
		);
	}

	/** Ozel bucket icin kisa omurlu indirme yetkisi uretir. */
	public function download_authorization( $prefix, $seconds = 3600 ) {
		$auth = $this->authorize();
		if ( is_wp_error( $auth ) ) {
			return $auth;
		}
		$bucket_id = (string) $this->settings['b2_bucket_id'];
		if ( '' === $bucket_id ) {
			$bucket = $this->resolve_bucket( $auth );
			if ( is_wp_error( $bucket ) ) {
				return $bucket;
			}
			$bucket_id = $bucket['bucket_id'];
		}

		$res = $this->api( $auth, 'b2_get_download_authorization', array(
			'bucketId'               => $bucket_id,
			'fileNamePrefix'         => $prefix,
			'validDurationInSeconds' => max( 60, min( 604800, (int) $seconds ) ),
		) );

		if ( ! $res['ok'] ) {
			return new WP_Error( 'naber_b2_download_auth_failed', 'Indirme yetkisi alinamadi: ' . $res['error'] );
		}

		return array(
			'token'        => (string) $res['body']['authorizationToken'],
			'download_url' => $auth['download_url'],
		);
	}

	public function delete_file( $file_name, $file_id ) {
		$auth = $this->authorize();
		if ( is_wp_error( $auth ) ) {
			return $auth;
		}
		$res = $this->api( $auth, 'b2_delete_file_version', array(
			'fileName' => $file_name,
			'fileId'   => $file_id,
		) );
		if ( ! $res['ok'] ) {
			return new WP_Error( 'naber_b2_delete_failed', 'Dosya silinemedi: ' . $res['error'] );
		}
		return true;
	}

	/**
	 * Girilen bucket bilgilerinin gercekten calisip calismadigini adim adim sinar.
	 * Yonetim ekranindaki "Baglantiyi test et" dugmesi ve
	 * GET /naber/v1/admin/storage/test bunu kullanir.
	 *
	 * @param bool $write_test Gercek bir dosya yazip silerek tam dogrulama yapar.
	 * @return array{ok:bool,steps:array,bucket_id:string,message:string}
	 */
	public function test_connection( $write_test = true ) {
		$steps  = array();
		$result = array( 'ok' => false, 'steps' => &$steps, 'bucket_id' => '', 'message' => '' );

		$format = Naber_Settings::validate_b2( $this->settings );
		if ( ! $format['valid'] ) {
			$steps[] = array(
				'key'     => 'format',
				'ok'      => false,
				'label'   => 'Alan bicimi',
				'message' => implode( ' ', $format['errors'] ),
			);
			$result['message'] = 'Girilen bilgiler bicim kontrolunden gecemedi.';
			return $result;
		}
		$steps[] = array( 'key' => 'format', 'ok' => true, 'label' => 'Alan bicimi', 'message' => 'Key ID, Application Key ve bucket adi bicimsel olarak gecerli.' );

		$auth = $this->authorize( true );
		if ( is_wp_error( $auth ) ) {
			$steps[] = array( 'key' => 'auth', 'ok' => false, 'label' => 'Kimlik dogrulama', 'message' => $auth->get_error_message() );
			$result['message'] = 'Backblaze hesabina baglanilamadi.';
			return $result;
		}
		$steps[] = array(
			'key'     => 'auth',
			'ok'      => true,
			'label'   => 'Kimlik dogrulama',
			'message' => 'Hesap dogrulandi. Yetkiler: ' . ( $auth['capabilities'] ? implode( ', ', $auth['capabilities'] ) : 'bilinmiyor' ),
		);

		$bucket = $this->resolve_bucket( $auth );
		if ( is_wp_error( $bucket ) ) {
			$steps[] = array( 'key' => 'bucket', 'ok' => false, 'label' => 'Bucket', 'message' => $bucket->get_error_message() );
			$result['message'] = 'Bucket dogrulanamadi.';
			return $result;
		}
		$result['bucket_id'] = $bucket['bucket_id'];
		$steps[]             = array(
			'key'     => 'bucket',
			'ok'      => true,
			'label'   => 'Bucket',
			'message' => sprintf( '"%s" bulundu (ID: %s%s).', $bucket['bucket_name'], $bucket['bucket_id'], $bucket['bucket_type'] ? ', tip: ' . $bucket['bucket_type'] : '' ),
		);

		$needed  = array( 'writeFiles', 'readFiles', 'deleteFiles' );
		$missing = array_values( array_diff( $needed, $auth['capabilities'] ) );
		if ( $auth['capabilities'] && $missing ) {
			$steps[] = array(
				'key'     => 'capabilities',
				'ok'      => false,
				'label'   => 'Yetkiler',
				'message' => 'Anahtarda su yetkiler eksik: ' . implode( ', ', $missing ) . '. Backblaze panelinde yeni bir anahtar olustururken bunlari isaretleyin.',
			);
			$result['message'] = 'Anahtar yetkileri yetersiz.';
			return $result;
		}
		$steps[] = array( 'key' => 'capabilities', 'ok' => true, 'label' => 'Yetkiler', 'message' => 'Okuma, yazma ve silme yetkileri mevcut.' );

		if ( ! $write_test ) {
			$result['ok']      = true;
			$result['message'] = 'Bilgiler gecerli (yazma testi atlandi).';
			return $result;
		}

		$prefix   = (string) $this->settings['b2_path_prefix'];
		$name     = $prefix . '_naber-test/' . gmdate( 'Ymd-His' ) . '-' . wp_generate_password( 8, false, false ) . '.txt';
		$content  = 'naber-chat baglanti testi ' . gmdate( 'c' );
		$uploaded = $this->upload( $name, $content, 'text/plain' );
		if ( is_wp_error( $uploaded ) ) {
			$steps[] = array( 'key' => 'write', 'ok' => false, 'label' => 'Yazma testi', 'message' => $uploaded->get_error_message() );
			$result['message'] = 'Bucket\'a yazilamadi.';
			return $result;
		}
		$steps[] = array( 'key' => 'write', 'ok' => true, 'label' => 'Yazma testi', 'message' => 'Deneme dosyasi yuklendi: ' . $uploaded['file_name'] );

		$deleted = $this->delete_file( $uploaded['file_name'], $uploaded['file_id'] );
		if ( is_wp_error( $deleted ) ) {
			$steps[] = array( 'key' => 'cleanup', 'ok' => false, 'label' => 'Temizlik', 'message' => $deleted->get_error_message() );
			$result['message'] = 'Deneme dosyasi silinemedi; yazma calisiyor ama silme yetkisini kontrol edin.';
			return $result;
		}
		$steps[] = array( 'key' => 'cleanup', 'ok' => true, 'label' => 'Temizlik', 'message' => 'Deneme dosyasi silindi.' );

		$result['ok']      = true;
		$result['message'] = 'Backblaze B2 ayarlari calisiyor.';
		return $result;
	}
}
