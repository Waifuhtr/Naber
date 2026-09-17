<?php
/**
 * Durtme (poke): bir kullanici digerine hafif bir "hey, naber?" sinyali
 * gonderir. Mesaj degildir, sohbet acmaz; yalnizca bildirim tetikler.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Pokes {

	/** Ayni kisiye tekrar durtmeden once beklenmesi gereken sure. */
	const COOLDOWN_SECONDS = 60;

	/**
	 * Durtme kaydi olusturur.
	 *
	 * @return array|WP_Error Basarili olursa bir sonraki durtmenin ne zaman
	 *                         yapilabilecegini de iceren dizi.
	 */
	public static function poke( $from_id, $to_id ) {
		$from_id = (int) $from_id;
		$to_id   = (int) $to_id;

		if ( $from_id === $to_id ) {
			return new WP_Error( 'naber_self_poke', 'Kendinizi durtemezsiniz.', array( 'status' => 400 ) );
		}

		$remaining = self::cooldown_remaining( $from_id, $to_id );
		if ( $remaining > 0 ) {
			return new WP_Error(
				'naber_poke_cooldown',
				sprintf( 'Bu kisiyi tekrar durtmek icin %d saniye beklemelisiniz.', $remaining ),
				array( 'status' => 429, 'retry_after' => $remaining )
			);
		}

		global $wpdb;
		$wpdb->insert(
			Naber_DB::table( 'pokes' ),
			array(
				'from_id'    => $from_id,
				'to_id'      => $to_id,
				'created_at' => Naber_DB::now(),
			),
			array( '%d', '%d', '%s' )
		);

		return array(
			'ok'               => true,
			'next_allowed_in'  => self::COOLDOWN_SECONDS,
		);
	}

	/** Su an durtulursa kac saniye sonra tekrar durtulebilir; durtulebiliyorsa 0. */
	public static function cooldown_remaining( $from_id, $to_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'pokes' );
		$last  = $wpdb->get_var(
			$wpdb->prepare(
				"SELECT created_at FROM {$table} WHERE from_id = %d AND to_id = %d ORDER BY id DESC LIMIT 1",
				(int) $from_id,
				(int) $to_id
			)
		);
		if ( ! $last ) {
			return 0;
		}

		return self::remaining_from_elapsed( time() - Naber_Chat_Repo::ts( $last ) );
	}

	/**
	 * Gecen saniyeye gore kalan bekleme suresini hesaplar (veritabanina
	 * dokunmayan saf fonksiyon; testlerde dogrudan cagrilir).
	 */
	public static function remaining_from_elapsed( $elapsed_seconds ) {
		$left = self::COOLDOWN_SECONDS - (int) $elapsed_seconds;
		return $left > 0 ? $left : 0;
	}
}
