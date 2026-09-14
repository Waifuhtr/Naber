<?php
/**
 * Sohbet ve mesaj veri katmani.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Chat_Repo {

	/** Iki kullanici icin sohbet bulur, yoksa olusturur. */
	public static function ensure_conversation( $user_a, $user_b ) {
		global $wpdb;
		$table = Naber_DB::table( 'conversations' );
		$one   = min( (int) $user_a, (int) $user_b );
		$two   = max( (int) $user_a, (int) $user_b );

		if ( $one === $two || $one <= 0 ) {
			return new WP_Error( 'naber_invalid_pair', 'Gecersiz sohbet katilimcilari.', array( 'status' => 400 ) );
		}

		$id = $wpdb->get_var( $wpdb->prepare( "SELECT id FROM {$table} WHERE user_one = %d AND user_two = %d", $one, $two ) );
		if ( $id ) {
			return (int) $id;
		}

		$now = Naber_DB::now();
		$wpdb->insert(
			$table,
			array(
				'user_one'   => $one,
				'user_two'   => $two,
				'created_at' => $now,
				'updated_at' => $now,
			),
			array( '%d', '%d', '%s', '%s' )
		);
		return (int) $wpdb->insert_id;
	}

	public static function get_conversation( $conversation_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'conversations' );
		return $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$table} WHERE id = %d", (int) $conversation_id ), ARRAY_A );
	}

	/** Kullanici bu sohbete ait mi? */
	public static function is_participant( $conversation, $user_id ) {
		if ( ! $conversation ) {
			return false;
		}
		$user_id = (int) $user_id;
		return (int) $conversation['user_one'] === $user_id || (int) $conversation['user_two'] === $user_id;
	}

	public static function other_user( $conversation, $user_id ) {
		return (int) $conversation['user_one'] === (int) $user_id ? (int) $conversation['user_two'] : (int) $conversation['user_one'];
	}

	/** Sohbet listesi: son mesaj + okunmamis sayisi. */
	public static function list_for_user( $user_id ) {
		global $wpdb;
		$conversations = Naber_DB::table( 'conversations' );
		$messages      = Naber_DB::table( 'messages' );
		$user_id       = (int) $user_id;

		$rows = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT c.*,
					(SELECT COUNT(*) FROM {$messages} m WHERE m.conversation_id = c.id AND m.receiver_id = %d AND m.is_read = 0) AS unread
				 FROM {$conversations} c
				 WHERE c.user_one = %d OR c.user_two = %d
				 ORDER BY c.updated_at DESC
				 LIMIT 200",
				$user_id,
				$user_id,
				$user_id
			),
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$peer_id = self::other_user( $row, $user_id );
			$peer    = Naber_Auth::user_payload( $peer_id );
			if ( ! $peer ) {
				continue;
			}
			$last = null;
			if ( (int) $row['last_message_id'] > 0 ) {
				$last_row = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$messages} WHERE id = %d", (int) $row['last_message_id'] ), ARRAY_A );
				if ( $last_row ) {
					$last = self::message_payload( $last_row );
				}
			}
			$out[] = array(
				'id'           => (int) $row['id'],
				'peer'         => $peer,
				'unread'       => (int) $row['unread'],
				'updated_at'   => self::ts( $row['updated_at'] ),
				'last_message' => $last,
			);
		}
		return $out;
	}

	public static function messages( $conversation_id, $args = array() ) {
		global $wpdb;
		$table = Naber_DB::table( 'messages' );
		$limit = isset( $args['limit'] ) ? max( 1, min( 100, (int) $args['limit'] ) ) : 50;
		$after = isset( $args['after'] ) ? (int) $args['after'] : 0;

		if ( $after > 0 ) {
			$rows = $wpdb->get_results(
				$wpdb->prepare( "SELECT * FROM {$table} WHERE conversation_id = %d AND id > %d ORDER BY id ASC LIMIT %d", $conversation_id, $after, $limit ),
				ARRAY_A
			);
		} else {
			$before = isset( $args['before'] ) ? (int) $args['before'] : 0;
			if ( $before > 0 ) {
				$rows = $wpdb->get_results(
					$wpdb->prepare( "SELECT * FROM {$table} WHERE conversation_id = %d AND id < %d ORDER BY id DESC LIMIT %d", $conversation_id, $before, $limit ),
					ARRAY_A
				);
			} else {
				$rows = $wpdb->get_results(
					$wpdb->prepare( "SELECT * FROM {$table} WHERE conversation_id = %d ORDER BY id DESC LIMIT %d", $conversation_id, $limit ),
					ARRAY_A
				);
			}
			$rows = array_reverse( (array) $rows );
		}

		return array_map( array( __CLASS__, 'message_payload' ), (array) $rows );
	}

	public static function insert_message( $conversation_id, $sender_id, $receiver_id, $type, $body, $media_id = 0, $client_id = '' ) {
		global $wpdb;
		$messages      = Naber_DB::table( 'messages' );
		$conversations = Naber_DB::table( 'conversations' );
		$now           = Naber_DB::now();

		// Ayni client_id tekrar gelirse mesaji cogaltma (ag tekrar denemeleri).
		if ( '' !== $client_id ) {
			$existing = $wpdb->get_row(
				$wpdb->prepare( "SELECT * FROM {$messages} WHERE client_id = %s AND sender_id = %d", $client_id, $sender_id ),
				ARRAY_A
			);
			if ( $existing ) {
				return self::message_payload( $existing );
			}
		}

		$wpdb->insert(
			$messages,
			array(
				'conversation_id' => (int) $conversation_id,
				'sender_id'       => (int) $sender_id,
				'receiver_id'     => (int) $receiver_id,
				'message_type'    => $type,
				'body'            => $body,
				'media_id'        => (int) $media_id,
				'client_id'       => (string) $client_id,
				'is_read'         => 0,
				'created_at'      => $now,
			),
			array( '%d', '%d', '%d', '%s', '%s', '%d', '%s', '%d', '%s' )
		);

		$message_id = (int) $wpdb->insert_id;
		$wpdb->update(
			$conversations,
			array( 'last_message_id' => $message_id, 'updated_at' => $now ),
			array( 'id' => (int) $conversation_id ),
			array( '%d', '%s' ),
			array( '%d' )
		);

		$row = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$messages} WHERE id = %d", $message_id ), ARRAY_A );
		return self::message_payload( $row );
	}

	public static function mark_read( $conversation_id, $reader_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'messages' );
		return (int) $wpdb->query(
			$wpdb->prepare(
				"UPDATE {$table} SET is_read = 1, read_at = %s WHERE conversation_id = %d AND receiver_id = %d AND is_read = 0",
				Naber_DB::now(),
				(int) $conversation_id,
				(int) $reader_id
			)
		);
	}

	public static function unread_total( $user_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'messages' );
		return (int) $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM {$table} WHERE receiver_id = %d AND is_read = 0", (int) $user_id ) );
	}

	/** Kullanicinin sohbetlerinde belirtilen id'den sonra gelen mesajlar (olay akisi). */
	public static function messages_since( $user_id, $since_id, $limit = 100 ) {
		global $wpdb;
		$messages = Naber_DB::table( 'messages' );
		$rows     = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT * FROM {$messages} WHERE id > %d AND (sender_id = %d OR receiver_id = %d) ORDER BY id ASC LIMIT %d",
				(int) $since_id,
				(int) $user_id,
				(int) $user_id,
				(int) $limit
			),
			ARRAY_A
		);
		return array_map( array( __CLASS__, 'message_payload' ), (array) $rows );
	}

	/** Karsi tarafin okudugu mesajlarin durumunu istemciye bildirmek icin. */
	public static function read_receipts( $user_id, $since_ts ) {
		global $wpdb;
		$messages = Naber_DB::table( 'messages' );
		$rows     = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT id, conversation_id, read_at FROM {$messages} WHERE sender_id = %d AND is_read = 1 AND read_at IS NOT NULL AND read_at > %s ORDER BY read_at ASC LIMIT 200",
				(int) $user_id,
				gmdate( 'Y-m-d H:i:s', (int) $since_ts )
			),
			ARRAY_A
		);
		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = array(
				'message_id'      => (int) $row['id'],
				'conversation_id' => (int) $row['conversation_id'],
				'read_at'         => self::ts( $row['read_at'] ),
			);
		}
		return $out;
	}

	public static function set_typing( $conversation_id, $user_id ) {
		set_transient( 'naber_typing_' . (int) $conversation_id . '_' . (int) $user_id, time(), 15 );
	}

	public static function typing_state( $conversation_id, $other_user_id ) {
		$value = get_transient( 'naber_typing_' . (int) $conversation_id . '_' . (int) $other_user_id );
		return $value && ( time() - (int) $value ) < 8;
	}

	public static function message_payload( $row ) {
		if ( ! $row ) {
			return null;
		}
		$payload = array(
			'id'              => (int) $row['id'],
			'conversation_id' => (int) $row['conversation_id'],
			'sender_id'       => (int) $row['sender_id'],
			'receiver_id'     => (int) $row['receiver_id'],
			'type'            => (string) $row['message_type'],
			'body'            => (string) $row['body'],
			'client_id'       => (string) $row['client_id'],
			'is_read'         => (bool) (int) $row['is_read'],
			'created_at'      => self::ts( $row['created_at'] ),
			'media'           => null,
		);
		if ( (int) $row['media_id'] > 0 ) {
			$payload['media'] = Naber_Media::payload( (int) $row['media_id'] );
		}
		return $payload;
	}

	public static function ts( $mysql_date ) {
		if ( ! $mysql_date || '0000-00-00 00:00:00' === $mysql_date ) {
			return 0;
		}
		return (int) strtotime( $mysql_date . ' UTC' );
	}
}
