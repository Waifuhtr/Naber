<?php
/**
 * Sesli arama: birebir ve grup aramalari, katilimci yonetimi, WebRTC signaling.
 * Ses trafigi dogrudan WebRTC ile gider; WordPress yalnizca offer/answer/ICE tasir.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Calls {

	const RING_TIMEOUT = 60;

	// ------------------------------------------------------------------
	// Baslatma
	// ------------------------------------------------------------------

	public static function start_direct( $caller_id, $callee_id ) {
		$conversation_id = Naber_Chat_Repo::ensure_conversation( $caller_id, $callee_id );
		if ( is_wp_error( $conversation_id ) ) {
			return $conversation_id;
		}

		self::expire_stale( $caller_id );

		$call_id = self::insert_call( $conversation_id, 'direct', $caller_id, $callee_id );
		self::add_participant( $call_id, $caller_id, 'joined' );
		self::add_participant( $call_id, $callee_id, 'ringing' );

		return self::get( $call_id );
	}

	public static function start_group( $caller_id, $conversation_id ) {
		$conversation = Naber_Chat_Repo::get_conversation( $conversation_id );
		if ( ! $conversation || 'group' !== $conversation['type'] ) {
			return new WP_Error( 'naber_group_not_found', 'Grup bulunamadi.', array( 'status' => 404 ) );
		}
		if ( ! Naber_Chat_Repo::member( $conversation_id, $caller_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu grubun uyesi degilsiniz.', array( 'status' => 403 ) );
		}

		// Ayni grupta zaten suren bir arama varsa ona katil.
		$existing = self::active_group_call( $conversation_id );
		if ( $existing ) {
			self::add_participant( (int) $existing['id'], $caller_id, 'joined' );
			return $existing;
		}

		self::expire_stale( $caller_id );

		$call_id = self::insert_call( $conversation_id, 'group', $caller_id, 0 );
		self::add_participant( $call_id, $caller_id, 'joined' );
		foreach ( Naber_Chat_Repo::member_ids( $conversation_id ) as $member_id ) {
			if ( $member_id !== (int) $caller_id ) {
				self::add_participant( $call_id, $member_id, 'ringing' );
			}
		}

		return self::get( $call_id );
	}

	private static function insert_call( $conversation_id, $type, $caller_id, $callee_id ) {
		global $wpdb;
		$wpdb->insert(
			Naber_DB::table( 'calls' ),
			array(
				'conversation_id' => (int) $conversation_id,
				'type'            => $type,
				'caller_id'       => (int) $caller_id,
				'callee_id'       => (int) $callee_id,
				'status'          => 'ringing',
				'created_at'      => Naber_DB::now(),
			),
			array( '%d', '%s', '%d', '%d', '%s', '%s' )
		);
		return (int) $wpdb->insert_id;
	}

	public static function active_group_call( $conversation_id ) {
		global $wpdb;
		$row = $wpdb->get_row(
			$wpdb->prepare(
				'SELECT * FROM ' . Naber_DB::table( 'calls' ) . " WHERE conversation_id = %d AND type = 'group' AND status IN ('ringing','active') AND created_at > %s ORDER BY id DESC LIMIT 1",
				(int) $conversation_id,
				gmdate( 'Y-m-d H:i:s', time() - 4 * HOUR_IN_SECONDS )
			),
			ARRAY_A
		);
		return $row ? $row : null;
	}

	/**
	 * Zil suresi gecmis ama kapanmamis aramalari kapatir.
	 *
	 * Uygulama arama sirasinda oldurulurse kayit 'ringing' olarak asili kalir;
	 * temizlenmezse kullanici uygulamayi actiginda olmayan bir arama ekrani
	 * ("hayalet arama") gorur. Hem birebir hem grup aramalari taranir.
	 */
	private static function expire_stale( $user_id, $throttle = 0 ) {
		// Olay akisi bu temizligi saniyede bir tetikleyebilir; gereksiz yazma
		// yukunu onlemek icin kullanici basina araliga bakilir.
		if ( $throttle > 0 ) {
			$key = 'naber_call_sweep_' . (int) $user_id;
			if ( get_transient( $key ) ) {
				return;
			}
			set_transient( $key, 1, $throttle );
		}

		global $wpdb;
		$calls        = Naber_DB::table( 'calls' );
		$participants = Naber_DB::table( 'call_participants' );
		$deadline     = gmdate( 'Y-m-d H:i:s', time() - self::RING_TIMEOUT );

		$wpdb->query(
			$wpdb->prepare(
				"UPDATE {$calls} SET status = 'missed', ended_at = %s, end_reason = 'timeout'
				 WHERE status = 'ringing' AND type = 'direct' AND (caller_id = %d OR callee_id = %d) AND created_at < %s",
				Naber_DB::now(),
				(int) $user_id,
				(int) $user_id,
				$deadline
			)
		);

		// Grup aramasi: kimse katilmadan zil suresi gectiyse dusur.
		$wpdb->query(
			$wpdb->prepare(
				"UPDATE {$calls} c SET c.status = 'missed', c.ended_at = %s, c.end_reason = 'timeout'
				 WHERE c.status = 'ringing' AND c.type = 'group' AND c.ended_at IS NULL AND c.created_at < %s
				   AND EXISTS (SELECT 1 FROM {$participants} p WHERE p.call_id = c.id AND p.user_id = %d AND p.status = 'ringing')
				   AND NOT EXISTS (SELECT 1 FROM {$participants} j WHERE j.call_id = c.id AND j.status = 'joined' AND j.left_at IS NULL)",
				Naber_DB::now(),
				$deadline,
				(int) $user_id
			)
		);

		// Kapanmis aramalarda asili kalan 'ringing' katilimci satirlari.
		$wpdb->query(
			"UPDATE {$participants} p
			 INNER JOIN {$calls} c ON c.id = p.call_id
			 SET p.status = 'missed'
			 WHERE p.status = 'ringing' AND (c.ended_at IS NOT NULL OR c.status IN ('ended','missed','rejected'))"
		);
	}

	// ------------------------------------------------------------------
	// Katilimcilar
	// ------------------------------------------------------------------

	public static function add_participant( $call_id, $user_id, $status = 'ringing' ) {
		global $wpdb;
		$table = Naber_DB::table( 'call_participants' );
		$joined = 'joined' === $status ? Naber_DB::now() : null;

		$wpdb->query(
			$wpdb->prepare(
				"INSERT INTO {$table} (call_id, user_id, status, joined_at) VALUES (%d, %d, %s, %s)
				 ON DUPLICATE KEY UPDATE status = VALUES(status), joined_at = COALESCE(joined_at, VALUES(joined_at)), left_at = NULL",
				(int) $call_id,
				(int) $user_id,
				$status,
				$joined
			)
		);
		return true;
	}

	public static function participant( $call_id, $user_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'call_participants' );
		return $wpdb->get_row(
			$wpdb->prepare( "SELECT * FROM {$table} WHERE call_id = %d AND user_id = %d", (int) $call_id, (int) $user_id ),
			ARRAY_A
		);
	}

	public static function participants( $call_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'call_participants' );
		$rows  = $wpdb->get_results( $wpdb->prepare( "SELECT * FROM {$table} WHERE call_id = %d ORDER BY id ASC", (int) $call_id ), ARRAY_A );

		$out = array();
		foreach ( (array) $rows as $row ) {
			$user = Naber_Auth::user_payload( (int) $row['user_id'] );
			if ( ! $user ) {
				continue;
			}
			$user['call_status'] = (string) $row['status'];
			$user['muted']       = (bool) (int) $row['muted'];
			$out[]               = $user;
		}
		return $out;
	}

	/** Halihazirda aramada olan kullanicilar (mesh baglanti kurmak icin). */
	public static function joined_user_ids( $call_id, $except = 0 ) {
		global $wpdb;
		$table = Naber_DB::table( 'call_participants' );
		$ids   = $wpdb->get_col(
			$wpdb->prepare( "SELECT user_id FROM {$table} WHERE call_id = %d AND status = 'joined' AND user_id <> %d", (int) $call_id, (int) $except )
		);
		return array_map( 'intval', (array) $ids );
	}

	public static function set_participant_status( $call_id, $user_id, $status ) {
		global $wpdb;
		$data    = array( 'status' => $status );
		$formats = array( '%s' );

		if ( 'joined' === $status ) {
			$data['joined_at'] = Naber_DB::now();
			$formats[]         = '%s';
		}
		if ( in_array( $status, array( 'left', 'kicked', 'rejected' ), true ) ) {
			$data['left_at'] = Naber_DB::now();
			$formats[]       = '%s';
		}

		$wpdb->update(
			Naber_DB::table( 'call_participants' ),
			$data,
			array( 'call_id' => (int) $call_id, 'user_id' => (int) $user_id ),
			$formats,
			array( '%d', '%d' )
		);
		return true;
	}

	public static function set_participant_muted( $call_id, $user_id, $muted ) {
		global $wpdb;
		$wpdb->update(
			Naber_DB::table( 'call_participants' ),
			array( 'muted' => $muted ? 1 : 0 ),
			array( 'call_id' => (int) $call_id, 'user_id' => (int) $user_id ),
			array( '%d' ),
			array( '%d', '%d' )
		);
		return true;
	}

	// ------------------------------------------------------------------
	// Durum
	// ------------------------------------------------------------------

	public static function get( $call_id ) {
		global $wpdb;
		return $wpdb->get_row( $wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'calls' ) . ' WHERE id = %d', (int) $call_id ), ARRAY_A );
	}

	public static function is_participant( $call, $user_id ) {
		if ( ! $call ) {
			return false;
		}
		if ( (int) $call['caller_id'] === (int) $user_id || (int) $call['callee_id'] === (int) $user_id ) {
			return true;
		}
		return (bool) self::participant( (int) $call['id'], (int) $user_id );
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

		// Arama bittiyse hala "caliyor" gorunen katilimcilar temizlenir; aksi
		// halde uygulama bir daha acildiginda hayalet gelen arama ekrani cikar.
		if ( in_array( $status, array( 'ended', 'rejected', 'missed', 'failed' ), true ) ) {
			$wpdb->query(
				$wpdb->prepare(
					'UPDATE ' . Naber_DB::table( 'call_participants' ) . " SET status = 'missed', left_at = %s
					 WHERE call_id = %d AND status = 'ringing'",
					$now,
					(int) $call_id
				)
			);
		}

		return self::get( $call_id );
	}

	/** Kullaniciya gelen, henuz cevaplanmamis arama. */
	public static function active_incoming( $user_id ) {
		global $wpdb;
		$calls        = Naber_DB::table( 'calls' );
		$participants = Naber_DB::table( 'call_participants' );

		// Once bayat kayitlari kapat; yoksa olmayan bir arama "geliyor" gorunur.
		self::expire_stale( $user_id, 30 );

		$row = $wpdb->get_row(
			$wpdb->prepare(
				"SELECT c.* FROM {$calls} c
				 INNER JOIN {$participants} p ON p.call_id = c.id AND p.user_id = %d
				 WHERE p.status = 'ringing' AND c.status IN ('ringing','active')
				   AND c.ended_at IS NULL AND c.created_at > %s
				 ORDER BY c.id DESC LIMIT 1",
				(int) $user_id,
				gmdate( 'Y-m-d H:i:s', time() - self::RING_TIMEOUT )
			),
			ARRAY_A
		);

		return $row ? self::payload( $row ) : null;
	}

	// ------------------------------------------------------------------
	// Signaling
	// ------------------------------------------------------------------

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

	/** Bir olayi aramadaki herkese duyurur (durum degisiklikleri icin). */
	public static function broadcast( $call_id, $sender_id, $type, $payload ) {
		foreach ( self::participants( $call_id ) as $participant ) {
			if ( (int) $participant['id'] !== (int) $sender_id ) {
				self::add_signal( $call_id, $sender_id, (int) $participant['id'], $type, $payload );
			}
		}
	}

	/**
	 * Durum degisikligini katilimci listesiyle birlikte duyurur.
	 * Uygulamalar boylece susturma/atma/katilma bilgisini ek istek yapmadan,
	 * aninda ekrana yansitir.
	 */
	public static function broadcast_state( $call_id, $sender_id, array $extra, $include_sender = false ) {
		$participants = self::participants( $call_id );
		$payload      = wp_json_encode( array_merge( $extra, array( 'participants' => $participants ) ) );

		foreach ( $participants as $participant ) {
			if ( ! $include_sender && (int) $participant['id'] === (int) $sender_id ) {
				continue;
			}
			self::add_signal( $call_id, $sender_id, (int) $participant['id'], 'state', $payload );
		}
	}

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

	public static function cleanup_signals() {
		global $wpdb;
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . Naber_DB::table( 'signals' ) . ' WHERE created_at < %s', gmdate( 'Y-m-d H:i:s', time() - DAY_IN_SECONDS ) ) );
	}

	// ------------------------------------------------------------------

	public static function history( $user_id, $limit = 50 ) {
		global $wpdb;
		$calls        = Naber_DB::table( 'calls' );
		$participants = Naber_DB::table( 'call_participants' );

		$rows = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT DISTINCT c.* FROM {$calls} c
				 INNER JOIN {$participants} p ON p.call_id = c.id AND p.user_id = %d
				 ORDER BY c.id DESC LIMIT %d",
				(int) $user_id,
				(int) $limit
			),
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = self::payload( $row );
		}
		return $out;
	}

	public static function payload( $row, $with_participants = false ) {
		if ( ! $row ) {
			return null;
		}
		if ( is_numeric( $row ) ) {
			$row = self::get( $row );
			if ( ! $row ) {
				return null;
			}
		}

		$conversation = (int) $row['conversation_id'] ? Naber_Chat_Repo::get_conversation( (int) $row['conversation_id'] ) : null;

		$payload = array(
			'id'              => (int) $row['id'],
			'type'            => (string) $row['type'],
			'conversation_id' => (int) $row['conversation_id'],
			'group_title'     => $conversation && 'group' === $conversation['type'] ? (string) $conversation['title'] : '',
			'caller'          => Naber_Auth::user_payload( (int) $row['caller_id'] ),
			'callee'          => (int) $row['callee_id'] ? Naber_Auth::user_payload( (int) $row['callee_id'] ) : null,
			'caller_id'       => (int) $row['caller_id'],
			'callee_id'       => (int) $row['callee_id'],
			'status'          => (string) $row['status'],
			'created_at'      => Naber_Chat_Repo::ts( $row['created_at'] ),
			'answered_at'     => Naber_Chat_Repo::ts( $row['answered_at'] ),
			'ended_at'        => Naber_Chat_Repo::ts( $row['ended_at'] ),
			'duration'        => (int) $row['duration'],
			'end_reason'      => (string) $row['end_reason'],
		);

		if ( $with_participants || 'group' === $row['type'] ) {
			$payload['participants'] = self::participants( (int) $row['id'] );
		}

		return $payload;
	}

	public static function stats() {
		global $wpdb;
		$table = Naber_DB::table( 'calls' );
		$row   = $wpdb->get_row( "SELECT COUNT(*) AS total, COALESCE(SUM(duration),0) AS seconds, SUM(status='missed') AS missed, SUM(type='group') AS groups_total FROM {$table}", ARRAY_A );
		return array(
			'total'   => isset( $row['total'] ) ? (int) $row['total'] : 0,
			'seconds' => isset( $row['seconds'] ) ? (int) $row['seconds'] : 0,
			'missed'  => isset( $row['missed'] ) ? (int) $row['missed'] : 0,
			'group'   => isset( $row['groups_total'] ) ? (int) $row['groups_total'] : 0,
		);
	}
}
