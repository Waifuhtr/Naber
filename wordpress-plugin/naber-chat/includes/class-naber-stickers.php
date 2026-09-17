<?php
/**
 * Cikartma paketleri ve ozel emojiler.
 *
 * Ikisi de ayni tabloda tutulur, "kind" ile ayrilir:
 * - sticker: mesaj olarak gonderilen buyuk gorsel (paket adiyla gruplanir).
 * - emoji:   metin icinde ":ad:" seklinde yazilan kucuk gorsel; reaksiyon
 *            olarak da kullanilabilir.
 *
 * Hareketli (GIF/WebP) dosyalar da kabul edilir; medya katmani bu
 * turleri zaten destekliyor.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Stickers {

	const KINDS = array( 'sticker', 'emoji' );

	/** Bir kullanicinin ekleyebilecegi en fazla kayit (kotayi korumak icin). */
	const MAX_PER_USER = 200;

	/**
	 * Ozel emoji adini guvenli hale getirir: kucuk harf, yalnizca
	 * ingiliz harfleri, rakam ve alt cizgi. Saf fonksiyon.
	 */
	public static function normalize_name( $raw ) {
		$name = strtolower( trim( (string) $raw ) );
		$name = str_replace( ':', '', $name );
		$map  = array(
			'ı' => 'i', 'İ' => 'i', 'ş' => 's', 'Ş' => 's',
			'ğ' => 'g', 'Ğ' => 'g', 'ü' => 'u', 'Ü' => 'u',
			'ö' => 'o', 'Ö' => 'o', 'ç' => 'c', 'Ç' => 'c',
		);
		$name = strtr( $name, $map );
		$name = preg_replace( '/[^a-z0-9_]+/', '_', $name );
		$name = trim( (string) $name, '_' );
		return substr( (string) $name, 0, 30 );
	}

	/** Paket adi: gorunur metin, yalnizca kirpilir. */
	public static function normalize_pack( $raw ) {
		$pack = trim( wp_strip_all_tags( (string) $raw ) );
		return mb_substr( $pack, 0, 40 );
	}

	/**
	 * Metindeki ":ad:" bicimindeki ozel emoji adlarini bulur.
	 * Saf fonksiyon: veritabanina dokunmaz.
	 */
	public static function extract_names( $text ) {
		$text = (string) $text;
		if ( '' === $text || false === strpos( $text, ':' ) ) {
			return array();
		}
		if ( ! preg_match_all( '/:([a-z0-9_]{1,30}):/i', $text, $matches ) ) {
			return array();
		}
		$out = array();
		foreach ( $matches[1] as $name ) {
			$name = strtolower( $name );
			if ( ! in_array( $name, $out, true ) ) {
				$out[] = $name;
			}
		}
		return $out;
	}

	public static function table() {
		return Naber_DB::table( 'stickers' );
	}

	public static function get( $id ) {
		global $wpdb;
		return $wpdb->get_row(
			$wpdb->prepare( 'SELECT * FROM ' . self::table() . ' WHERE id = %d', (int) $id ),
			ARRAY_A
		);
	}

	public static function find_by_name( $kind, $name ) {
		global $wpdb;
		return $wpdb->get_row(
			$wpdb->prepare(
				'SELECT * FROM ' . self::table() . ' WHERE kind = %s AND name = %s',
				(string) $kind,
				self::normalize_name( $name )
			),
			ARRAY_A
		);
	}

	public static function count_for_user( $user_id ) {
		global $wpdb;
		return (int) $wpdb->get_var(
			$wpdb->prepare( 'SELECT COUNT(*) FROM ' . self::table() . ' WHERE uploader_id = %d', (int) $user_id )
		);
	}

	/**
	 * Yeni cikartma ya da ozel emoji ekler.
	 *
	 * @return array|WP_Error Eklenen kaydin yuku.
	 */
	public static function add( $kind, $pack, $name, $media_id, $animated, $uploader_id ) {
		global $wpdb;

		$kind = in_array( $kind, self::KINDS, true ) ? $kind : 'sticker';
		$name = self::normalize_name( $name );
		if ( '' === $name ) {
			return new WP_Error( 'naber_sticker_name', 'Gecerli bir ad girin (harf, rakam, alt cizgi).', array( 'status' => 400 ) );
		}

		$media = Naber_Media::get( (int) $media_id );
		if ( ! $media ) {
			return new WP_Error( 'naber_media_not_found', 'Gorsel bulunamadi.', array( 'status' => 404 ) );
		}
		if ( 0 !== strpos( (string) $media['mime'], 'image/' ) ) {
			return new WP_Error( 'naber_sticker_mime', 'Yalnizca gorsel dosyalari kullanilabilir.', array( 'status' => 400 ) );
		}
		if ( self::find_by_name( $kind, $name ) ) {
			return new WP_Error( 'naber_sticker_exists', 'Bu ad zaten kullaniliyor.', array( 'status' => 409 ) );
		}
		if ( self::count_for_user( $uploader_id ) >= self::MAX_PER_USER ) {
			return new WP_Error( 'naber_sticker_limit', 'Ekleyebilecegin en fazla kayit sayisina ulastin.', array( 'status' => 400 ) );
		}

		$wpdb->insert(
			self::table(),
			array(
				'kind'        => $kind,
				'pack'        => self::normalize_pack( $pack ),
				'name'        => $name,
				'media_id'    => (int) $media_id,
				'animated'    => $animated ? 1 : 0,
				'uploader_id' => (int) $uploader_id,
				'created_at'  => Naber_DB::now(),
			),
			array( '%s', '%s', '%s', '%d', '%d', '%d', '%s' )
		);

		return self::payload( self::get( (int) $wpdb->insert_id ) );
	}

	/** Kaydi siler. Yalnizca ekleyen kisi ya da uygulama yoneticisi. */
	public static function remove( $id, $user_id ) {
		global $wpdb;
		$row = self::get( $id );
		if ( ! $row ) {
			return new WP_Error( 'naber_sticker_not_found', 'Kayit bulunamadi.', array( 'status' => 404 ) );
		}
		if ( (int) $row['uploader_id'] !== (int) $user_id && ! Naber_Auth::is_admin_user( $user_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Bunu yalnizca ekleyen kisi silebilir.', array( 'status' => 403 ) );
		}
		$wpdb->delete( self::table(), array( 'id' => (int) $id ), array( '%d' ) );
		return true;
	}

	/** Butun cikartma ve ozel emojiler; istemci bunu onbellekler. */
	public static function all() {
		global $wpdb;
		$rows = $wpdb->get_results(
			'SELECT * FROM ' . self::table() . " ORDER BY kind ASC, pack ASC, id ASC",
			ARRAY_A
		);
		$out = array();
		foreach ( (array) $rows as $row ) {
			$payload = self::payload( $row );
			if ( $payload ) {
				$out[] = $payload;
			}
		}
		return $out;
	}

	public static function payload( $row ) {
		if ( ! $row ) {
			return null;
		}
		return array(
			'id'          => (int) $row['id'],
			'kind'        => (string) $row['kind'],
			'pack'        => (string) $row['pack'],
			'name'        => (string) $row['name'],
			'media_id'    => (int) $row['media_id'],
			'url'         => Naber_Media::url_for_id( (int) $row['media_id'] ),
			'animated'    => (bool) (int) $row['animated'],
			'uploader_id' => (int) $row['uploader_id'],
			'created_at'  => Naber_Chat_Repo::ts( $row['created_at'] ),
		);
	}
}
