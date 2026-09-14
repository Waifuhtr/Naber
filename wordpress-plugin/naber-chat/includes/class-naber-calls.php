<?php
/**
 * Sesli arama kayitlari ve WebRTC signaling.
 * Ses trafigi dogrudan WebRTC uzerinden gider; WordPress yalnizca
 * offer/answer/ICE mesajlarini tasir.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Calls {

	public static function start( $caller_id, $callee_id ) {
		global $wpdb;

		$conversation_id = Naber_Chat_Repo::ensure_conversation( $caller_id, $callee_id );
		if ( is_wp_error( $conversation_id ) ) {
			return $conversation_id;
		}

		// Ayni kullanici icin cevapsiz kalan eski cagrilari kapat.
		$wpdb->query(
			$wpdb->prepare(
				'UPDATE ' . Naber_DB::table( 'calls' ) . " SET status = 'missed', ended_at = %s, end_reason = 'timeout'
				 WHERE status = 'ringing' AND (caller_id = %d OR callee_id = %d) AND created_at < %s",
				Naber_DB::now(),
				(int) $caller_id,
				(int) $caller_id,
				gmdate( 'Y-m-d H:i:s', time() - 60 )
			)
		);

		$wpdb->insert(
			Naber_DB::table( 'calls' ),
			array(
				'conversation_id' => (int) $conversation_id,
				'caller_id'       => (int) $caller_id,
				'callee_id'       => (int) $callee_id,
				'status'          => 'ringing',
				'created_at'      => Naber_DB::now(),
			),
			array( '%d', '%d', '%d', '%s', '%s' )
		);

		return self::get( (int) $wpdb->insert_id );
	}

	public static function get( $call_id ) {
		global $wpdb;
		return $wpdb->get_row( $wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'calls' ) . ' WHERE id = %d', (int) $call_id ), ARRAY_A );
	}

	public static function is_participant( $call, $user_id ) {
		return $call && ( (int) $call['caller_id'] === (int) $user_id || (int) $call['callee_id'] === (int) $user_id );
	}

	public static function set_status( $call_id, $status, $reason = '' ) {
		global $wpdb;
		$call = self::get( $call_id );
		if ( ! $call ) {
			return new WP_Error( 'naber_call_not_found', 'Arama bulunamadi.', array( 'status' => 404 ) );
		}

		$data    = array( 'status' => $status );
		$formats = array( '%s' );
		$now     = Naber_DB::now();

		if ( 'active' === $status && empty( $call['answered_at'] ) ) {
			$data['answered_at'] = $now;
			$formats[]           = '%s';
		}

		if ( in_array( $status, array( 'ended', 'rejected', 'missed', 'failed' ), true ) ) {
			$data['ended_at']   = $now;
			$formats[]          = '%s';
			$data['end_reason'] = (string) $reason;
			$formats[]          = '%s';
			if ( ! empty( $call['answered_at'] ) ) {
				$data['duration'] = max( 0, Naber_Chat_Repo::ts( $now ) - Naber_Chat_Repo::ts( $call['answered_at'] ) );
				$formats[]        = '%d';
			}
		}

		$wpdb->update( Naber_DB::table( 'calls' ), $data, array( 'id' => (int) $call_id ), $formats, array( '%d' ) );
		return self::get( $call_id );
	}

	public static function active_incoming( $user_id ) {
		global $wpdb;
		$row = $wpdb->get_row(
			$wpdb->prepare(
				'SELECT * FROM ' . Naber_DB::table( 'calls' ) . " WHERE callee_id = %d AND status = 'ringing' AND created_at > %s ORDER BY id DESC LIMIT 1",
				(int) $user_id,
				gmdate( 'Y-m-d H:i:s', time() - 60 )
			),
			ARRAY_A
		);
		return $row ? self::payload( $row ) : null;
	}

	public static function add_signal( $call_id, $sender_id, $receiver_id, $type, $payload ) {
		global $wpdb;
		$wpdb->insert(
			Naber_DB::table( 'signals' ),
			array(
				'call_id'     => (int) $call_id,
				'sender_id'   => (int) $sender_id,
				'receiver_id' => (int) $receiver_id,
				'type'        => (string) $type,
				'payload'     => is_string( $payload ) ? $payload : wp_json_encode( $payload ),
				'created_at'  => Naber_DB::now(),
			),
			array( '%d', '%d', '%d', '%s', '%s', '%s' )
		);
		return (int) $wpdb->insert_id;
	}

	/** Kullaniciya gelen signaling mesajlari (since_id sonrasi). */
	public static function signals_for( $user_id, $since_id, $call_id = 0 ) {
		global $wpdb;
		$table = Naber_DB::table( 'signals' );

		if ( $call_id > 0 ) {
			$rows = $wpdb->get_results(
				$wpdb->prepare( "SELECT * FROM {$table} WHERE receiver_id = %d AND call_id = %d AND id > %d ORDER BY id ASC LIMIT 100", (int) $user_id, (int) $call_id, (int) $since_id ),
				ARRAY_A
			);
		} else {
			$rows = $wpdb->get_results(
				$wpdb->prepare( "SELECT * FROM {$table} WHERE receiver_id = %d AND id > %d ORDER BY id ASC LIMIT 100", (int) $user_id, (int) $since_id ),
				ARRAY_A
			);
		}

		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = array(
				'id'        => (int) $row['id'],
				'call_id'   => (int) $row['call_id'],
				'sender_id' => (int) $row['sender_id'],
				'type'      => (string) $row['type'],
				'payload'   => (string) $row['payload'],
			);
		}
		return $out;
	}

	/** 24 saatten eski signaling satirlarini temizler. */
	public static function cleanup_signals() {
		global $wpdb;
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . Naber_DB::table( 'signals' ) . ' WHERE created_at < %s', gmdate( 'Y-m-d H:i:s', time() - DAY_IN_SECONDS ) ) );
	}

	public static function history( $user_id, $limit = 50 ) {
		global $wpdb;
		$rows = $wpdb->get_results(
			$wpdb->prepare(
				'SELECT * FROM ' . Naber_DB::table( 'calls' ) . ' WHERE caller_id = %d OR callee_id = %d ORDER BY id DESC LIMIT %d',
				(int) $user_id,
				(int) $user_id,
				(int) $limit
			),
			ARRAY_A
		);
		return array_map( array( __CLASS__, 'payload' ), (array) $rows );
	}

	public static function payload( $row ) {
		if ( ! $row ) {
			return null;
		}
		return array(
			'id'          => (int) $row['id'],
			'caller'      => Naber_Auth::user_payload( (int) $row['caller_id'] ),
			'callee'      => Naber_Auth::user_payload( (int) $row['callee_id'] ),
			'caller_id'   => (int) $row['caller_id'],
			'callee_id'   => (int) $row['callee_id'],
			'status'      => (string) $row['status'],
			'created_at'  => Naber_Chat_Repo::ts( $row['created_at'] ),
			'answered_at' => Naber_Chat_Repo::ts( $row['answered_at'] ),
			'ended_at'    => Naber_Chat_Repo::ts( $row['ended_at'] ),
			'duration'    => (int) $row['duration'],
			'end_reason'  => (string) $row['end_reason'],
		);
	}

	public static function stats() {
		global $wpdb;
		$table = Naber_DB::table( 'calls' );
		$row   = $wpdb->get_row( "SELECT COUNT(*) AS total, COALESCE(SUM(duration),0) AS seconds, SUM(status='missed') AS missed FROM {$table}", ARRAY_A );
		return array(
			'total'   => isset( $row['total'] ) ? (int) $row['total'] : 0,
			'seconds' => isset( $row['seconds'] ) ? (int) $row['seconds'] : 0,
			'missed'  => isset( $row['missed'] ) ? (int) $row['missed'] : 0,
		);
	}
}
