<?php
/**
 * Medya kayitlari ve Backblaze B2 yukleme akisi.
 *
 * Akis (Android):
 *   1) POST media/upload-url  -> tek kullanimlik B2 upload adresi + dosya adi
 *   2) Dogrudan B2'ye POST    -> uygulama dosyayi B2'ye yukler (ilerleme cubugu burada)
 *   3) POST media/complete    -> kayit dogrulanir, media_id doner
 *   4) POST messages          -> media_id ile mesaj gonderilir
 * Yedek yol: POST media/upload (dosya WordPress uzerinden gecer).
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Media {

	const ALLOWED_MIME = array(
		'image/jpeg' => 'jpg',
		'image/png'  => 'png',
		'image/webp' => 'webp',
		'image/gif'  => 'gif',
	);

	public static function max_bytes() {
		return max( 1, (int) Naber_Settings::get( 'b2_max_upload_mb', 25 ) ) * 1024 * 1024;
	}

	public static function is_allowed_mime( $mime ) {
		return isset( self::ALLOWED_MIME[ strtolower( (string) $mime ) ] );
	}

	/** Kullanici bazinda tahmin edilemez dosya adi uretir. */
	public static function build_file_name( $user_id, $mime ) {
		$prefix = (string) Naber_Settings::get( 'b2_path_prefix', 'naber/' );
		$ext    = isset( self::ALLOWED_MIME[ strtolower( $mime ) ] ) ? self::ALLOWED_MIME[ strtolower( $mime ) ] : 'bin';
		return sprintf(
			'%su%d/%s/%s.%s',
			$prefix,
			(int) $user_id,
			gmdate( 'Y/m' ),
			gmdate( 'Ymd-His' ) . '-' . wp_generate_password( 16, false, false ),
			$ext
		);
	}

	public static function create_pending( $user_id, $file_name, $mime, $size, $width = 0, $height = 0 ) {
		global $wpdb;
		$wpdb->insert(
			Naber_DB::table( 'media' ),
			array(
				'owner_id'   => (int) $user_id,
				'bucket'     => (string) Naber_Settings::get( 'b2_bucket_name' ),
				'file_name'  => (string) $file_name,
				'file_id'    => '',
				'mime'       => (string) $mime,
				'size'       => (int) $size,
				'width'      => (int) $width,
				'height'     => (int) $height,
				'status'     => 'pending',
				'created_at' => Naber_DB::now(),
			),
			array( '%d', '%s', '%s', '%s', '%s', '%d', '%d', '%d', '%s', '%s' )
		);
		return (int) $wpdb->insert_id;
	}

	public static function complete( $media_id, $user_id, $file_id, $size = 0 ) {
		global $wpdb;
		$row = self::get( $media_id );
		if ( ! $row || (int) $row['owner_id'] !== (int) $user_id ) {
			return new WP_Error( 'naber_media_not_found', 'Medya kaydi bulunamadi.', array( 'status' => 404 ) );
		}
		$wpdb->update(
			Naber_DB::table( 'media' ),
			array(
				'file_id' => (string) $file_id,
				'status'  => 'ready',
				'size'    => $size > 0 ? (int) $size : (int) $row['size'],
			),
			array( 'id' => (int) $media_id ),
			array( '%s', '%s', '%d' ),
			array( '%d' )
		);
		return self::get( $media_id );
	}

	public static function get( $media_id ) {
		global $wpdb;
		return $wpdb->get_row( $wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'media' ) . ' WHERE id = %d', (int) $media_id ), ARRAY_A );
	}

	/**
	 * Indirilebilir URL uretir.
	 * Genel (public) bucket veya CDN adresi tanimliysa dogrudan adres,
	 * aksi halde kisa omurlu imzali adres doner.
	 */
	public static function url_for( $row ) {
		if ( ! $row || 'ready' !== $row['status'] ) {
			return '';
		}

		$base = (string) Naber_Settings::get( 'b2_public_base_url' );
		if ( '' !== $base ) {
			return $base . '/' . ltrim( self::encode_path( $row['file_name'] ), '/' );
		}

		$ttl    = (int) Naber_Settings::get( 'b2_link_ttl', 3600 );
		$cache  = 'naber_dl_' . md5( dirname( $row['file_name'] ) );
		$cached = get_transient( $cache );
		if ( ! is_array( $cached ) ) {
			$b2   = new Naber_B2();
			$auth = $b2->download_authorization( trailingslashit( dirname( $row['file_name'] ) ), $ttl );
			if ( is_wp_error( $auth ) ) {
				return '';
			}
			$cached = $auth;
			set_transient( $cache, $cached, max( 60, $ttl - 120 ) );
		}

		return sprintf(
			'%s/file/%s/%s?Authorization=%s',
			rtrim( $cached['download_url'], '/' ),
			rawurlencode( (string) Naber_Settings::get( 'b2_bucket_name' ) ),
			self::encode_path( $row['file_name'] ),
			rawurlencode( $cached['token'] )
		);
	}

	private static function encode_path( $path ) {
		return implode( '/', array_map( 'rawurlencode', explode( '/', (string) $path ) ) );
	}

	public static function payload( $media_id ) {
		$row = is_array( $media_id ) ? $media_id : self::get( $media_id );
		if ( ! $row ) {
			return null;
		}
		return array(
			'id'     => (int) $row['id'],
			'url'    => self::url_for( $row ),
			'mime'   => (string) $row['mime'],
			'size'   => (int) $row['size'],
			'width'  => (int) $row['width'],
			'height' => (int) $row['height'],
			'status' => (string) $row['status'],
		);
	}

	public static function storage_stats() {
		global $wpdb;
		$table = Naber_DB::table( 'media' );
		$row   = $wpdb->get_row( "SELECT COUNT(*) AS files, COALESCE(SUM(size),0) AS bytes FROM {$table} WHERE status = 'ready'", ARRAY_A );
		return array(
			'files' => isset( $row['files'] ) ? (int) $row['files'] : 0,
			'bytes' => isset( $row['bytes'] ) ? (int) $row['bytes'] : 0,
		);
	}
}
