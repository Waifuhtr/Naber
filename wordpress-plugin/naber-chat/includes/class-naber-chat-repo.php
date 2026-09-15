<?php
/**
 * Sohbet (birebir + grup), uyelik ve mesaj veri katmani.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Chat_Repo {

	// ------------------------------------------------------------------
	// Sohbet olusturma
	// ------------------------------------------------------------------

	/** Iki kullanici icin birebir sohbet bulur, yoksa olusturur. */
	public static function ensure_conversation( $user_a, $user_b ) {
		global $wpdb;
		$table = Naber_DB::table( 'conversations' );
		$one   = min( (int) $user_a, (int) $user_b );
		$two   = max( (int) $user_a, (int) $user_b );

		if ( $one === $two || $one <= 0 ) {
			return new WP_Error( 'naber_invalid_pair', 'Gecersiz sohbet katilimcilari.', array( 'status' => 400 ) );
		}

		$key = Naber_DB::direct_key( $one, $two );
		$id  = $wpdb->get_var( $wpdb->prepare( "SELECT id FROM {$table} WHERE pair_key = %s", $key ) );
		if ( $id ) {
			return (int) $id;
		}

		$now = Naber_DB::now();
		$wpdb->insert(
			$table,
			array(
				'type'       => 'direct',
				'pair_key'   => $key,
				'user_one'   => $one,
				'user_two'   => $two,
				'created_at' => $now,
				'updated_at' => $now,
			),
			array( '%s', '%s', '%d', '%d', '%s', '%s' )
		);

		$conversation_id = (int) $wpdb->insert_id;
		self::add_member( $conversation_id, $one, 'member' );
		self::add_member( $conversation_id, $two, 'member' );
		return $conversation_id;
	}

	/** Yeni grup olusturur; kurucusu sahip (owner) olur. */
	public static function create_group( $owner_id, $title, array $member_ids, $avatar_media_id = 0, $about = '' ) {
		global $wpdb;

		$title = trim( wp_strip_all_tags( (string) $title ) );
		if ( mb_strlen( $title ) < 2 ) {
			return new WP_Error( 'naber_group_title', 'Grup adi en az 2 karakter olmali.', array( 'status' => 400 ) );
		}
		if ( mb_strlen( $title ) > 60 ) {
			$title = mb_substr( $title, 0, 60 );
		}

		$now = Naber_DB::now();
		$wpdb->insert(
			Naber_DB::table( 'conversations' ),
			array(
				'type'            => 'group',
				'pair_key'        => 'g:' . wp_generate_password( 20, false, false ),
				'title'           => $title,
				'about'           => mb_substr( trim( wp_strip_all_tags( (string) $about ) ), 0, 200 ),
				'avatar_media_id' => (int) $avatar_media_id,
				'owner_id'        => (int) $owner_id,
				'created_at'      => $now,
				'updated_at'      => $now,
			),
			array( '%s', '%s', '%s', '%s', '%d', '%d', '%s', '%s' )
		);

		$conversation_id = (int) $wpdb->insert_id;
		self::add_member( $conversation_id, (int) $owner_id, 'owner' );

		foreach ( array_unique( array_map( 'intval', $member_ids ) ) as $member_id ) {
			if ( $member_id > 0 && $member_id !== (int) $owner_id && get_userdata( $member_id ) ) {
				self::add_member( $conversation_id, $member_id, 'member' );
			}
		}

		return $conversation_id;
	}

	public static function get_conversation( $conversation_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'conversations' );
		return $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$table} WHERE id = %d", (int) $conversation_id ), ARRAY_A );
	}

	// ------------------------------------------------------------------
	// Uyelik
	// ------------------------------------------------------------------

	public static function add_member( $conversation_id, $user_id, $role = 'member' ) {
		global $wpdb;
		$table = Naber_DB::table( 'members' );
		$wpdb->query(
			$wpdb->prepare(
				"INSERT INTO {$table} (conversation_id, user_id, role, joined_at) VALUES (%d, %d, %s, %s)
				 ON DUPLICATE KEY UPDATE role = VALUES(role)",
				(int) $conversation_id,
				(int) $user_id,
				$role,
				Naber_DB::now()
			)
		);
		return true;
	}

	public static function remove_member( $conversation_id, $user_id ) {
		global $wpdb;
		return (bool) $wpdb->delete(
			Naber_DB::table( 'members' ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%d', '%d' )
		);
	}

	public static function member( $conversation_id, $user_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'members' );
		return $wpdb->get_row(
			$wpdb->prepare( "SELECT * FROM {$table} WHERE conversation_id = %d AND user_id = %d", (int) $conversation_id, (int) $user_id ),
			ARRAY_A
		);
	}

	public static function member_ids( $conversation_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'members' );
		return array_map( 'intval', (array) $wpdb->get_col( $wpdb->prepare( "SELECT user_id FROM {$table} WHERE conversation_id = %d", (int) $conversation_id ) ) );
	}

	/** Uye listesi + kullanici bilgisi + rol. */
	public static function members( $conversation_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'members' );
		$rows  = $wpdb->get_results(
			$wpdb->prepare( "SELECT * FROM {$table} WHERE conversation_id = %d ORDER BY FIELD(role,'owner','admin','member'), id ASC", (int) $conversation_id ),
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$user = Naber_Auth::user_payload( (int) $row['user_id'] );
			if ( ! $user ) {
				continue;
			}
			$user['role']       = (string) $row['role'];
			$user['chat_muted'] = (bool) (int) $row['chat_muted'];
			$user['joined_at']  = self::ts( $row['joined_at'] );
			$out[]              = $user;
		}
		return $out;
	}

	public static function is_participant( $conversation, $user_id ) {
		if ( ! $conversation ) {
			return false;
		}
		return (bool) self::member( (int) $conversation['id'], (int) $user_id );
	}

	public static function role_of( $conversation_id, $user_id ) {
		$member = self::member( $conversation_id, $user_id );
		return $member ? (string) $member['role'] : '';
	}

	/** Yonetici mi? (grup sahibi veya yonetici) */
	public static function is_group_admin( $conversation_id, $user_id ) {
		return self::can_manage( self::role_of( $conversation_id, $user_id ) );
	}

	/** Rol bazli yetki kurali — testlerde de dogrudan kullanilir. */
	public static function can_manage( $role ) {
		return in_array( $role, array( 'owner', 'admin' ), true );
	}

	/**
	 * Bir uyenin baska bir uye uzerinde islem yapip yapamayacagini soyler.
	 * Sahip herkese, yonetici yalnizca duz uyelere mudahale edebilir.
	 */
	public static function can_act_on( $actor_role, $target_role ) {
		if ( 'owner' === $actor_role ) {
			return 'owner' !== $target_role;
		}
		if ( 'admin' === $actor_role ) {
			return 'member' === $target_role;
		}
		return false;
	}

	public static function set_chat_muted( $conversation_id, $user_id, $muted ) {
		global $wpdb;
		return (bool) $wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'chat_muted' => $muted ? 1 : 0 ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%d' ),
			array( '%d', '%d' )
		);
	}

	public static function set_notify_muted( $conversation_id, $user_id, $muted ) {
		global $wpdb;
		return (bool) $wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'notify_muted' => $muted ? 1 : 0 ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%d' ),
			array( '%d', '%d' )
		);
	}

	public static function set_role( $conversation_id, $user_id, $role ) {
		global $wpdb;
		return (bool) $wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'role' => $role ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%s' ),
			array( '%d', '%d' )
		);
	}

	public static function update_group( $conversation_id, $fields ) {
		global $wpdb;
		$data    = array();
		$formats = array();

		if ( isset( $fields['title'] ) ) {
			$title = trim( wp_strip_all_tags( (string) $fields['title'] ) );
			if ( mb_strlen( $title ) >= 2 ) {
				$data['title'] = mb_substr( $title, 0, 60 );
				$formats[]     = '%s';
			}
		}
		if ( isset( $fields['about'] ) ) {
			$data['about'] = mb_substr( trim( wp_strip_all_tags( (string) $fields['about'] ) ), 0, 200 );
			$formats[]     = '%s';
		}
		if ( isset( $fields['avatar_media_id'] ) ) {
			$data['avatar_media_id'] = (int) $fields['avatar_media_id'];
			$formats[]               = '%d';
		}
		if ( ! $data ) {
			return false;
		}

		$wpdb->update( Naber_DB::table( 'conversations' ), $data, array( 'id' => (int) $conversation_id ), $formats, array( '%d' ) );
		return true;
	}

	public static function other_user( $conversation, $user_id ) {
		if ( 'group' === $conversation['type'] ) {
			return 0;
		}
		return (int) $conversation['user_one'] === (int) $user_id ? (int) $conversation['user_two'] : (int) $conversation['user_one'];
	}

	// ------------------------------------------------------------------
	// Listeler
	// ------------------------------------------------------------------

	public static function list_for_user( $user_id ) {
		global $wpdb;
		$conversations = Naber_DB::table( 'conversations' );
		$members       = Naber_DB::table( 'members' );
		$messages      = Naber_DB::table( 'messages' );
		$user_id       = (int) $user_id;

		$rows = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT c.*, me.role, me.last_read_id, me.chat_muted, me.notify_muted,
					(SELECT COUNT(*) FROM {$messages} m WHERE m.conversation_id = c.id AND m.id > me.last_read_id AND m.sender_id <> %d AND m.deleted = 0) AS unread,
					(SELECT COUNT(*) FROM {$members} mm WHERE mm.conversation_id = c.id) AS member_count
				 FROM {$conversations} c
				 INNER JOIN {$members} me ON me.conversation_id = c.id AND me.user_id = %d
				 ORDER BY c.updated_at DESC
				 LIMIT 200",
				$user_id,
				$user_id
			),
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$payload = self::conversation_payload( $row, $user_id );
			if ( $payload ) {
				$out[] = $payload;
			}
		}
		return $out;
	}

	public static function conversation_payload( $row, $user_id, $with_members = false ) {
		global $wpdb;

		if ( is_numeric( $row ) ) {
			$row = self::get_conversation( $row );
		}
		if ( ! $row ) {
			return null;
		}

		$member = self::member( (int) $row['id'], $user_id );
		$peer   = null;

		if ( 'group' !== $row['type'] ) {
			$peer = Naber_Auth::user_payload( self::other_user( $row, $user_id ) );
			if ( ! $peer ) {
				return null;
			}
		}

		$last = null;
		if ( (int) $row['last_message_id'] > 0 ) {
			$last_row = $wpdb->get_row(
				$wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'messages' ) . ' WHERE id = %d', (int) $row['last_message_id'] ),
				ARRAY_A
			);
			if ( $last_row ) {
				$last = self::message_payload( $last_row, 'group' === $row['type'] );
			}
		}

		$unread = isset( $row['unread'] ) ? (int) $row['unread'] : self::unread_in( (int) $row['id'], $user_id );

		$payload = array(
			'id'           => (int) $row['id'],
			'type'         => (string) $row['type'],
			'title'        => 'group' === $row['type'] ? (string) $row['title'] : ( $peer ? $peer['display_name'] : '' ),
			'about'        => (string) $row['about'],
			'avatar'       => 'group' === $row['type'] ? Naber_Media::url_for_id( (int) $row['avatar_media_id'] ) : ( $peer ? $peer['avatar'] : '' ),
			'peer'         => $peer,
			'owner_id'     => (int) $row['owner_id'],
			'member_count' => isset( $row['member_count'] ) ? (int) $row['member_count'] : count( self::member_ids( (int) $row['id'] ) ),
			'role'         => $member ? (string) $member['role'] : '',
			'chat_muted'   => $member ? (bool) (int) $member['chat_muted'] : false,
			'notify_muted' => $member ? (bool) (int) $member['notify_muted'] : false,
			'unread'       => $unread,
			'updated_at'   => self::ts( $row['updated_at'] ),
			'last_message' => $last,
			'read_watermark' => self::read_watermark( (int) $row['id'], $user_id ),
		);

		if ( $with_members ) {
			$payload['members'] = self::members( (int) $row['id'] );
		}

		return $payload;
	}

	public static function unread_in( $conversation_id, $user_id ) {
		global $wpdb;
		$member = self::member( $conversation_id, $user_id );
		if ( ! $member ) {
			return 0;
		}
		return (int) $wpdb->get_var(
			$wpdb->prepare(
				'SELECT COUNT(*) FROM ' . Naber_DB::table( 'messages' ) . ' WHERE conversation_id = %d AND id > %d AND sender_id <> %d AND deleted = 0',
				(int) $conversation_id,
				(int) $member['last_read_id'],
				(int) $user_id
			)
		);
	}

	public static function unread_total( $user_id ) {
		global $wpdb;
		$messages = Naber_DB::table( 'messages' );
		$members  = Naber_DB::table( 'members' );
		return (int) $wpdb->get_var(
			$wpdb->prepare(
				"SELECT COUNT(*) FROM {$messages} m
				 INNER JOIN {$members} me ON me.conversation_id = m.conversation_id AND me.user_id = %d
				 WHERE m.id > me.last_read_id AND m.sender_id <> %d AND m.deleted = 0",
				(int) $user_id,
				(int) $user_id
			)
		);
	}

	// ------------------------------------------------------------------
	// Mesajlar
	// ------------------------------------------------------------------

	public static function messages( $conversation_id, $args = array() ) {
		global $wpdb;
		$table   = Naber_DB::table( 'messages' );
		$limit   = isset( $args['limit'] ) ? max( 1, min( 100, (int) $args['limit'] ) ) : 50;
		$after   = isset( $args['after'] ) ? (int) $args['after'] : 0;
		$is_group = ! empty( $args['is_group'] );

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

		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = self::message_payload( $row, $is_group );
		}
		return $out;
	}

	public static function insert_message( $conversation_id, $sender_id, $receiver_id, $type, $body, $media_id = 0, $client_id = '' ) {
		global $wpdb;
		$messages      = Naber_DB::table( 'messages' );
		$conversations = Naber_DB::table( 'conversations' );
		$now           = Naber_DB::now();

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

		// Gonderen kendi mesajini okumus sayilir.
		$wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'last_read_id' => $message_id ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $sender_id ),
			array( '%d' ),
			array( '%d', '%d' )
		);

		$row = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$messages} WHERE id = %d", $message_id ), ARRAY_A );
		return self::message_payload( $row );
	}

	public static function delete_message( $message_id ) {
		global $wpdb;
		return (bool) $wpdb->update(
			Naber_DB::table( 'messages' ),
			array( 'deleted' => 1, 'body' => '', 'media_id' => 0 ),
			array( 'id' => (int) $message_id ),
			array( '%d', '%s', '%d' ),
			array( '%d' )
		);
	}

	public static function get_message( $message_id ) {
		global $wpdb;
		return $wpdb->get_row( $wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'messages' ) . ' WHERE id = %d', (int) $message_id ), ARRAY_A );
	}

	public static function mark_read( $conversation_id, $reader_id ) {
		global $wpdb;
		$messages = Naber_DB::table( 'messages' );

		$last_id = (int) $wpdb->get_var( $wpdb->prepare( "SELECT MAX(id) FROM {$messages} WHERE conversation_id = %d", (int) $conversation_id ) );
		$wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'last_read_id' => $last_id ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $reader_id ),
			array( '%d' ),
			array( '%d', '%d' )
		);

		// Birebir sohbette klasik "okundu" tiki icin.
		return (int) $wpdb->query(
			$wpdb->prepare(
				"UPDATE {$messages} SET is_read = 1, read_at = %s WHERE conversation_id = %d AND sender_id <> %d AND is_read = 0",
				Naber_DB::now(),
				(int) $conversation_id,
				(int) $reader_id
			)
		);
	}

	/** Diger uyelerin hepsinin okudugu en yuksek mesaj kimligi. */
	public static function read_watermark( $conversation_id, $user_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'members' );
		$value = $wpdb->get_var(
			$wpdb->prepare( "SELECT MIN(last_read_id) FROM {$table} WHERE conversation_id = %d AND user_id <> %d", (int) $conversation_id, (int) $user_id )
		);
		return null === $value ? 0 : (int) $value;
	}

	/** Olay akisi: kullanicinin tum sohbetlerindeki yeni mesajlar. */
	public static function messages_since( $user_id, $since_id, $limit = 100 ) {
		global $wpdb;
		$messages = Naber_DB::table( 'messages' );
		$members  = Naber_DB::table( 'members' );

		$rows = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT m.*, c.type AS conversation_type FROM {$messages} m
				 INNER JOIN {$members} me ON me.conversation_id = m.conversation_id AND me.user_id = %d
				 INNER JOIN " . Naber_DB::table( 'conversations' ) . " c ON c.id = m.conversation_id
				 WHERE m.id > %d
				 ORDER BY m.id ASC
				 LIMIT %d",
				(int) $user_id,
				(int) $since_id,
				(int) $limit
			),
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = self::message_payload( $row, 'group' === $row['conversation_type'] );
		}
		return $out;
	}

	/** Her sohbet icin karsi tarafin okuma seviyesi (tik guncellemesi). */
	public static function read_states( $user_id ) {
		global $wpdb;
		$members = Naber_DB::table( 'members' );
		$rows    = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT mine.conversation_id, COALESCE(MIN(others.last_read_id), 0) AS watermark
				 FROM {$members} mine
				 LEFT JOIN {$members} others ON others.conversation_id = mine.conversation_id AND others.user_id <> mine.user_id
				 WHERE mine.user_id = %d
				 GROUP BY mine.conversation_id
				 LIMIT 200",
				(int) $user_id
			),
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = array(
				'conversation_id' => (int) $row['conversation_id'],
				'watermark'       => (int) $row['watermark'],
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

	/** Belirli bir sohbette yazan kisiler (kendisi haric). */
	public static function typing_users( $conversation_id, $user_id ) {
		$out = array();
		foreach ( self::member_ids( $conversation_id ) as $member_id ) {
			if ( $member_id === (int) $user_id ) {
				continue;
			}
			if ( self::typing_state( $conversation_id, $member_id ) ) {
				$user = Naber_Auth::user_payload( $member_id );
				if ( $user ) {
					$out[] = array( 'id' => $member_id, 'name' => $user['display_name'] );
				}
			}
		}
		return $out;
	}

	public static function message_payload( $row, $is_group = false ) {
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
			'deleted'         => (bool) (int) $row['deleted'],
			'created_at'      => self::ts( $row['created_at'] ),
			'media'           => null,
			'sender_name'     => '',
			'sender_avatar'   => '',
		);

		if ( $is_group ) {
			$sender = Naber_Auth::user_payload( (int) $row['sender_id'] );
			if ( $sender ) {
				$payload['sender_name']   = $sender['display_name'];
				$payload['sender_avatar'] = $sender['avatar'];
			}
		}

		if ( (int) $row['media_id'] > 0 && ! $payload['deleted'] ) {
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
