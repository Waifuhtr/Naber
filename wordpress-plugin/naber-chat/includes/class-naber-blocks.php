<?php
/**
 * Kullanici engelleme.
 *
 * Engelleme tek yonlu bir karardir ama etkisi karsiliklidir: A, B'yi
 * engellerse ikisi de birbirine mesaj gonderemez, arayamaz ve durtemez.
 * Bu, engelleyenin "neden yazamiyorum?" diye sorulmasini engeller ve
 * engelleyene surekli mesaj yagmasinin onune gecer.
 *
 * Gruplar engellemeden etkilenmez: ayni gruptaki iki kisi birbirini
 * engellemis olsa da grup mesajlarini gorur. Kucuk ve tanidik bir
 * toplulukta grubu bolmek dogru olmaz; birebir sohbet kapanir, o kadar.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Blocks {

	/** A, B'yi engelledi mi? */
	public static function has_blocked( $blocker_id, $blocked_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'blocks' );
		return (bool) $wpdb->get_var(
			$wpdb->prepare(
				"SELECT id FROM {$table} WHERE blocker_id = %d AND blocked_id = %d LIMIT 1",
				(int) $blocker_id,
				(int) $blocked_id
			)
		);
	}

	/** Iki kullanicidan herhangi biri digerini engellemis mi? */
	public static function between( $user_a, $user_b ) {
		global $wpdb;
		$table = Naber_DB::table( 'blocks' );
		return (bool) $wpdb->get_var(
			$wpdb->prepare(
				"SELECT id FROM {$table}
				 WHERE (blocker_id = %d AND blocked_id = %d)
				    OR (blocker_id = %d AND blocked_id = %d)
				 LIMIT 1",
				(int) $user_a,
				(int) $user_b,
				(int) $user_b,
				(int) $user_a
			)
		);
	}

	/**
	 * Engeller/engeli kaldirir.
	 *
	 * @return true|WP_Error
	 */
	public static function set( $blocker_id, $blocked_id, $blocked ) {
		$blocker_id = (int) $blocker_id;
		$blocked_id = (int) $blocked_id;

		if ( $blocker_id === $blocked_id ) {
			return new WP_Error( 'naber_self_block', 'Kendinizi engelleyemezsiniz.', array( 'status' => 400 ) );
		}
		if ( $blocked_id <= 0 ) {
			return new WP_Error( 'naber_user_missing', 'Kullanici bulunamadi.', array( 'status' => 404 ) );
		}

		global $wpdb;
		$table = Naber_DB::table( 'blocks' );

		if ( $blocked ) {
			$wpdb->query(
				$wpdb->prepare(
					"INSERT IGNORE INTO {$table} (blocker_id, blocked_id, created_at) VALUES (%d, %d, %s)",
					$blocker_id,
					$blocked_id,
					Naber_DB::now()
				)
			);
		} else {
			$wpdb->delete(
				$table,
				array( 'blocker_id' => $blocker_id, 'blocked_id' => $blocked_id ),
				array( '%d', '%d' )
			);
		}

		return true;
	}

	/** Kullanicinin engelledigi kisilerin kimlikleri. */
	public static function blocked_ids( $user_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'blocks' );
		$ids   = $wpdb->get_col(
			$wpdb->prepare( "SELECT blocked_id FROM {$table} WHERE blocker_id = %d", (int) $user_id )
		);
		return array_map( 'intval', (array) $ids );
	}

	/**
	 * Engel varken yapilacak islem icin hata uretir (saf fonksiyon).
	 *
	 * Mesaj, arama ve durtme icin ayni metin kullanilmasin diye islemin
	 * adi disaridan verilir.
	 */
	public static function blocked_error( $action = 'message' ) {
		$messages = array(
			'message' => 'Bu kisiyle mesajlasamazsiniz.',
			'call'    => 'Bu kisiyi arayamazsiniz.',
			'poke'    => 'Bu kisiyi durtemezsiniz.',
		);
		$text = isset( $messages[ $action ] ) ? $messages[ $action ] : $messages['message'];
		return new WP_Error( 'naber_blocked', $text, array( 'status' => 403 ) );
	}
}
