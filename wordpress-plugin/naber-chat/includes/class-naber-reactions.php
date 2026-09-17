<?php
/**
 * Emoji reaksiyonu: bir mesaja kullanici basina en fazla bir emoji.
 * Ayni emojiye tekrar basmak kaldirir (WhatsApp'taki gibi).
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Reactions {

	/**
	 * Reaksiyonu ekler/degistirir/kaldirir.
	 *
	 * @return array{action: string, emoji: string} Ne oldugunu bildirir:
	 *         'set' (yeni eklendi veya degistirildi) veya 'removed'.
	 */
	public static function toggle( $message_id, $user_id, $emoji ) {
		global $wpdb;
		$table   = Naber_DB::table( 'reactions' );
		$message_id = (int) $message_id;
		$user_id    = (int) $user_id;

		$existing = $wpdb->get_var(
			$wpdb->prepare( "SELECT emoji FROM {$table} WHERE message_id = %d AND user_id = %d", $message_id, $user_id )
		);

		if ( $existing === $emoji ) {
			$wpdb->delete( $table, array( 'message_id' => $message_id, 'user_id' => $user_id ), array( '%d', '%d' ) );
			return array( 'action' => 'removed', 'emoji' => $emoji );
		}

		$wpdb->query(
			$wpdb->prepare(
				"INSERT INTO {$table} (message_id, user_id, emoji, created_at) VALUES (%d, %d, %s, %s)
				 ON DUPLICATE KEY UPDATE emoji = VALUES(emoji), created_at = VALUES(created_at)",
				$message_id,
				$user_id,
				$emoji,
				Naber_DB::now()
			)
		);
		return array( 'action' => 'set', 'emoji' => $emoji );
	}

	/**
	 * Bir mesajin reaksiyon ozeti: emoji basina sayi ve bu kullanicinin
	 * kendi reaksiyonu (varsa).
	 */
	public static function summary( $message_id, $viewer_id = 0 ) {
		global $wpdb;
		$table = Naber_DB::table( 'reactions' );
		$rows  = $wpdb->get_results(
			$wpdb->prepare( "SELECT emoji, user_id FROM {$table} WHERE message_id = %d", (int) $message_id ),
			ARRAY_A
		);
		if ( ! $rows ) {
			return array();
		}

		$counts = array();
		$mine   = '';
		foreach ( $rows as $row ) {
			$emoji = (string) $row['emoji'];
			if ( ! isset( $counts[ $emoji ] ) ) {
				$counts[ $emoji ] = 0;
			}
			++$counts[ $emoji ];
			if ( $viewer_id && (int) $row['user_id'] === (int) $viewer_id ) {
				$mine = $emoji;
			}
		}

		$out = array();
		foreach ( $counts as $emoji => $count ) {
			$out[] = array(
				'emoji'    => $emoji,
				'count'    => $count,
				'reacted'  => $emoji === $mine,
			);
		}
		return $out;
	}

	/** Birden fazla mesaj icin toplu ozet (liste ekraninda N ayri sorgu yerine tek sorgu). */
	public static function summary_for_many( array $message_ids, $viewer_id = 0 ) {
		global $wpdb;
		$message_ids = array_values( array_unique( array_map( 'intval', $message_ids ) ) );
		if ( empty( $message_ids ) ) {
			return array();
		}

		$table        = Naber_DB::table( 'reactions' );
		$placeholders = implode( ',', array_fill( 0, count( $message_ids ), '%d' ) );
		$rows         = $wpdb->get_results(
			$wpdb->prepare( "SELECT message_id, emoji, user_id FROM {$table} WHERE message_id IN ({$placeholders})", $message_ids ),
			ARRAY_A
		);
		if ( ! $rows ) {
			return array();
		}

		$grouped = array();
		foreach ( $rows as $row ) {
			$grouped[ (int) $row['message_id'] ][] = $row;
		}

		$out = array();
		foreach ( $grouped as $message_id => $message_rows ) {
			$counts = array();
			$mine   = '';
			foreach ( $message_rows as $row ) {
				$emoji = (string) $row['emoji'];
				if ( ! isset( $counts[ $emoji ] ) ) {
					$counts[ $emoji ] = 0;
				}
				++$counts[ $emoji ];
				if ( $viewer_id && (int) $row['user_id'] === (int) $viewer_id ) {
					$mine = $emoji;
				}
			}
			$summary = array();
			foreach ( $counts as $emoji => $count ) {
				$summary[] = array( 'emoji' => $emoji, 'count' => $count, 'reacted' => $emoji === $mine );
			}
			$out[ $message_id ] = $summary;
		}
		return $out;
	}
}
