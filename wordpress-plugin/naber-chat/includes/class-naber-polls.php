<?php
/**
 * Anket: soru + secenekler, oy verme ve sonuc ozeti.
 *
 * Anket bir mesajdir (message_type = 'poll'); sohbet akisinda yerini
 * korur, yanitlanabilir ve silinebilir. Secenekler anket satirinda JSON
 * olarak durur, oylar ayri tabloda secenek sirasina gore tutulur.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Polls {

	const MIN_OPTIONS  = 2;
	const MAX_OPTIONS  = 6;
	const MAX_OPTION_LENGTH   = 80;
	const MAX_QUESTION_LENGTH = 200;

	/**
	 * Secenek listesini temizler (saf fonksiyon).
	 *
	 * Bos secenekler atilir, ayni secenek iki kez yazilmissa tekillenir,
	 * uzunluk sinirlanir. Geriye [self::MIN_OPTIONS, self::MAX_OPTIONS]
	 * araliginda bir liste kalmazsa bos dizi doner.
	 *
	 * @return string[]
	 */
	public static function sanitize_options( $options ) {
		if ( ! is_array( $options ) ) {
			return array();
		}

		$clean = array();
		foreach ( $options as $option ) {
			$text = trim( wp_strip_all_tags( (string) $option ) );
			if ( '' === $text ) {
				continue;
			}
			if ( mb_strlen( $text ) > self::MAX_OPTION_LENGTH ) {
				$text = mb_substr( $text, 0, self::MAX_OPTION_LENGTH );
			}
			// Ayni secenek iki kez yazilmissa oylar bolunur, kullanici da
			// hangisine bastigini ayirt edemez.
			if ( in_array( $text, $clean, true ) ) {
				continue;
			}
			$clean[] = $text;
			if ( count( $clean ) >= self::MAX_OPTIONS ) {
				break;
			}
		}

		return count( $clean ) >= self::MIN_OPTIONS ? $clean : array();
	}

	/** Soru metnini temizler (saf fonksiyon). */
	public static function sanitize_question( $question ) {
		$text = trim( wp_strip_all_tags( (string) $question ) );
		if ( mb_strlen( $text ) > self::MAX_QUESTION_LENGTH ) {
			$text = mb_substr( $text, 0, self::MAX_QUESTION_LENGTH );
		}
		return $text;
	}

	/**
	 * Oy yuzdesi (saf fonksiyon).
	 *
	 * Hic oy yoksa 0 doner; boylece sifira bolme olmaz ve arayuzde bos
	 * cubuklar cizilir.
	 */
	public static function percent( $votes, $total ) {
		$total = (int) $total;
		if ( $total <= 0 ) {
			return 0;
		}
		return (int) round( ( (int) $votes * 100 ) / $total );
	}

	/** Anket olusturur. Secenekler gecersizse WP_Error doner. */
	public static function create( $message_id, $conversation_id, $user_id, $question, $options, $multiple ) {
		global $wpdb;

		$question = self::sanitize_question( $question );
		$options  = self::sanitize_options( $options );

		if ( '' === $question ) {
			return new WP_Error( 'naber_poll_question', 'Anket sorusu bos olamaz.', array( 'status' => 400 ) );
		}
		if ( ! $options ) {
			return new WP_Error(
				'naber_poll_options',
				sprintf( 'Anket icin en az %d farkli secenek gerekiyor.', self::MIN_OPTIONS ),
				array( 'status' => 400 )
			);
		}

		$wpdb->insert(
			Naber_DB::table( 'polls' ),
			array(
				'message_id'      => (int) $message_id,
				'conversation_id' => (int) $conversation_id,
				'question'        => $question,
				'options'         => wp_json_encode( $options ),
				'multiple'        => $multiple ? 1 : 0,
				'created_by'      => (int) $user_id,
				'created_at'      => Naber_DB::now(),
			),
			array( '%d', '%d', '%s', '%s', '%d', '%d', '%s' )
		);

		return (int) $wpdb->insert_id;
	}

	public static function get( $poll_id ) {
		global $wpdb;
		return $wpdb->get_row(
			$wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'polls' ) . ' WHERE id = %d', (int) $poll_id ),
			ARRAY_A
		);
	}

	public static function for_message( $message_id ) {
		global $wpdb;
		return $wpdb->get_row(
			$wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'polls' ) . ' WHERE message_id = %d', (int) $message_id ),
			ARRAY_A
		);
	}

	/**
	 * Oy verir ya da oyu geri ceker.
	 *
	 * Tek secimli ankette yeni oy oncekinin yerini alir; boylece
	 * kullanici fikrini degistirdiginde eski oyu elle kaldirmasi
	 * gerekmez.
	 */
	public static function vote( $poll_id, $user_id, $option_index ) {
		global $wpdb;

		$poll = self::get( $poll_id );
		if ( ! $poll ) {
			return new WP_Error( 'naber_poll_not_found', 'Anket bulunamadi.', array( 'status' => 404 ) );
		}

		$options      = self::options_of( $poll );
		$option_index = (int) $option_index;
		if ( $option_index < 0 || $option_index >= count( $options ) ) {
			return new WP_Error( 'naber_poll_option', 'Boyle bir secenek yok.', array( 'status' => 400 ) );
		}

		$table   = Naber_DB::table( 'poll_votes' );
		$user_id = (int) $user_id;

		$existing = $wpdb->get_var(
			$wpdb->prepare(
				"SELECT id FROM {$table} WHERE poll_id = %d AND user_id = %d AND option_index = %d",
				(int) $poll_id,
				$user_id,
				$option_index
			)
		);

		if ( $existing ) {
			// Ayni secenege tekrar basmak oyu geri ceker.
			$wpdb->delete( $table, array( 'id' => (int) $existing ), array( '%d' ) );
			return true;
		}

		if ( empty( $poll['multiple'] ) ) {
			$wpdb->delete( $table, array( 'poll_id' => (int) $poll_id, 'user_id' => $user_id ), array( '%d', '%d' ) );
		}

		$wpdb->insert(
			$table,
			array(
				'poll_id'      => (int) $poll_id,
				'user_id'      => $user_id,
				'option_index' => $option_index,
				'created_at'   => Naber_DB::now(),
			),
			array( '%d', '%d', '%d', '%s' )
		);

		return true;
	}

	/** Anket satirindaki JSON secenekleri dizi olarak verir. */
	public static function options_of( $poll ) {
		$decoded = json_decode( (string) $poll['options'], true );
		return is_array( $decoded ) ? array_values( $decoded ) : array();
	}

	/**
	 * Istemciye gonderilen anket gosterimi: secenekler, oy sayilari,
	 * yuzdeler ve bu kullanicinin verdigi oylar.
	 */
	public static function payload( $poll, $viewer_id = 0 ) {
		global $wpdb;

		if ( ! $poll ) {
			return null;
		}

		$options = self::options_of( $poll );
		$table   = Naber_DB::table( 'poll_votes' );

		$rows = $wpdb->get_results(
			$wpdb->prepare( "SELECT option_index, COUNT(*) AS votes FROM {$table} WHERE poll_id = %d GROUP BY option_index", (int) $poll['id'] ),
			ARRAY_A
		);

		$counts = array();
		$total  = 0;
		foreach ( (array) $rows as $row ) {
			$counts[ (int) $row['option_index'] ] = (int) $row['votes'];
			$total                               += (int) $row['votes'];
		}

		$mine = array();
		if ( $viewer_id > 0 ) {
			$mine = array_map(
				'intval',
				(array) $wpdb->get_col(
					$wpdb->prepare( "SELECT option_index FROM {$table} WHERE poll_id = %d AND user_id = %d", (int) $poll['id'], (int) $viewer_id )
				)
			);
		}

		$out = array();
		foreach ( $options as $index => $text ) {
			$votes = isset( $counts[ $index ] ) ? $counts[ $index ] : 0;
			$out[] = array(
				'index'   => (int) $index,
				'text'    => (string) $text,
				'votes'   => $votes,
				'percent' => self::percent( $votes, $total ),
				'mine'    => in_array( (int) $index, $mine, true ),
			);
		}

		return array(
			'id'       => (int) $poll['id'],
			'question' => (string) $poll['question'],
			'multiple' => (bool) (int) $poll['multiple'],
			'total'    => $total,
			'options'  => $out,
		);
	}
}
