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

		self::assign_invite_code( $conversation_id );

		// Sohbetin ilk satiri: grubun ne zaman ve kim tarafindan kuruldugu.
		Naber_Groups::system_message(
			$conversation_id,
			Naber_Groups::display_name( (int) $owner_id ) . ' "' . $title . '" grubunu olusturdu.'
		);

		return $conversation_id;
	}

	/**
	 * Karisik gelmeyen karakterlerden (0/O, 1/I/l yok) rastgele davet kodu.
	 * Saf fonksiyon: veritabanina dokunmaz, testlerde dogrudan cagrilir.
	 */
	public static function random_invite_code( $length = 6 ) {
		$alphabet = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
		$code     = '';
		for ( $i = 0; $i < $length; $i++ ) {
			$code .= $alphabet[ random_int( 0, strlen( $alphabet ) - 1 ) ];
		}
		return $code;
	}

	/** Gruba benzersiz bir davet kodu atar (cakisirsa tekrar dener). */
	public static function assign_invite_code( $conversation_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'conversations' );

		for ( $attempt = 0; $attempt < 10; $attempt++ ) {
			$code    = self::random_invite_code();
			$updated = $wpdb->query(
				$wpdb->prepare(
					"UPDATE {$table} SET invite_code = %s WHERE id = %d AND NOT EXISTS (SELECT 1 FROM {$table} t2 WHERE t2.invite_code = %s)",
					$code,
					(int) $conversation_id,
					$code
				)
			);
			if ( $updated ) {
				return $code;
			}
		}
		return '';
	}

	/** Davet kodundan gruba katilir. Zaten uyeyse tekrar eklemez. */
	public static function join_by_code( $code, $user_id ) {
		global $wpdb;
		$code = strtoupper( trim( (string) $code ) );
		if ( '' === $code ) {
			return new WP_Error( 'naber_invalid_code', 'Gecerli bir kod girin.', array( 'status' => 400 ) );
		}

		$conversation_id = (int) $wpdb->get_var(
			$wpdb->prepare(
				'SELECT id FROM ' . Naber_DB::table( 'conversations' ) . " WHERE invite_code = %s AND type = 'group'",
				$code
			)
		);
		if ( ! $conversation_id ) {
			return new WP_Error( 'naber_code_not_found', 'Bu koda sahip bir grup bulunamadi.', array( 'status' => 404 ) );
		}

		if ( ! self::member( $conversation_id, $user_id ) ) {
			self::add_member( $conversation_id, $user_id, 'member' );
			self::touch_conversation( $conversation_id );
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
		self::touch_conversation( $conversation_id );
		return true;
	}

	public static function remove_member( $conversation_id, $user_id ) {
		global $wpdb;
		$removed = (bool) $wpdb->delete(
			Naber_DB::table( 'members' ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%d', '%d' )
		);
		self::touch_conversation( $conversation_id );
		return $removed;
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
			$user['perms']      = self::perms_for( (string) $row['role'], isset( $row['perms'] ) ? $row['perms'] : '' );
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
	 * Ayrintili yetkiler.
	 *
	 * Yonetici yapmadan da tek tek verilebilir: "uyeleri cikarabilsin ama
	 * grup adini degistiremesin" gibi. Sahip ve yoneticiler zaten hepsine
	 * sahiptir, listeye bakilmaz.
	 */
	const PERMISSIONS = array( 'remove_member', 'delete_message', 'edit_group', 'add_member', 'pin_message' );

	/** Metinden gecerli yetki listesi cikarir. */
	public static function parse_perms( $raw ) {
		if ( is_array( $raw ) ) {
			$parts = $raw;
		} else {
			$parts = explode( ',', (string) $raw );
		}
		$out = array();
		foreach ( $parts as $part ) {
			$key = trim( (string) $part );
			if ( '' !== $key && in_array( $key, self::PERMISSIONS, true ) && ! in_array( $key, $out, true ) ) {
				$out[] = $key;
			}
		}
		return $out;
	}

	/** Bir uyenin sahip oldugu yetkiler; yonetici ve sahip icin hepsi. */
	public static function perms_for( $role, $raw ) {
		if ( self::can_manage( $role ) ) {
			return self::PERMISSIONS;
		}
		return self::parse_perms( $raw );
	}

	public static function perms_of( $conversation_id, $user_id ) {
		$member = self::member( $conversation_id, $user_id );
		if ( ! $member ) {
			return array();
		}
		return self::perms_for( (string) $member['role'], isset( $member['perms'] ) ? $member['perms'] : '' );
	}

	/** Kullanici bu grupta belirtilen yetkiye sahip mi? */
	public static function has_perm( $conversation_id, $user_id, $perm ) {
		return in_array( $perm, self::perms_of( $conversation_id, $user_id ), true );
	}

	public static function set_perms( $conversation_id, $user_id, $perms ) {
		global $wpdb;
		self::touch_conversation( $conversation_id );
		return (bool) $wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'perms' => implode( ',', self::parse_perms( $perms ) ) ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%s' ),
			array( '%d', '%d' )
		);
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
		self::touch_conversation( $conversation_id );
		return (bool) $wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'chat_muted' => $muted ? 1 : 0 ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%d' ),
			array( '%d', '%d' )
		);
	}

	/**
	 * @param int $duration_seconds 0 ise suresiz sessize alir. Sessize
	 *                              alma kaldiriliyorsa ($muted=false) yok
	 *                              sayilir.
	 */
	public static function set_notify_muted( $conversation_id, $user_id, $muted, $duration_seconds = 0 ) {
		global $wpdb;
		$until = null;
		if ( $muted && $duration_seconds > 0 ) {
			$until = gmdate( 'Y-m-d H:i:s', time() + (int) $duration_seconds );
		}
		return (bool) $wpdb->update(
			Naber_DB::table( 'members' ),
			array(
				'notify_muted'       => $muted ? 1 : 0,
				'notify_muted_until' => $until,
			),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%d', '%s' ),
			array( '%d', '%d' )
		);
	}

	/**
	 * Bir uyelik satirinin gercekten sessize alinmis sayilip sayilmayacagi.
	 * Suresi gecmis sureli sessize almalar otomatik olarak biter (ayri bir
	 * zamanlanmis is/cron gerekmez, her okumada tazelenir).
	 */
	public static function is_notify_muted( $member ) {
		if ( empty( $member['notify_muted'] ) ) {
			return false;
		}
		$until = $member['notify_muted_until'] ?? null;
		if ( empty( $until ) || '0000-00-00 00:00:00' === $until ) {
			return true;
		}
		return self::ts( $until ) > time();
	}

	/** Sohbeti kullanicinin listesinde sabitler/kaldirir. */
	public static function set_pinned( $conversation_id, $user_id, $pinned ) {
		global $wpdb;
		return (bool) $wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'pinned_at' => $pinned ? Naber_DB::now() : null ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%s' ),
			array( '%d', '%d' )
		);
	}

	/**
	 * Kaybolan mesajlar: sohbetteki mesajlarin kac saniye sonra silinecegi.
	 *
	 * Istege baglidir, varsayilan kapalidir (0). Ayar sohbetin tamamini
	 * ilgilendirdigi icin uye tablosunda degil sohbette tutulur; bir taraf
	 * acinca herkes icin acilir.
	 */
	public static function set_disappearing( $conversation_id, $seconds ) {
		global $wpdb;
		self::touch_conversation( $conversation_id );
		return (bool) $wpdb->update(
			Naber_DB::table( 'conversations' ),
			array( 'disappear_seconds' => max( 0, (int) $seconds ) ),
			array( 'id' => (int) $conversation_id ),
			array( '%d' ),
			array( '%d' )
		);
	}

	/**
	 * Suresi dolan mesajlari siler.
	 *
	 * Ayri bir zamanlanmis gorev yerine sohbet her okundugunda calisir:
	 * ~10 kisilik bir kurulumda ek bir cron'a deger yok, ayrica mesaji
	 * goren herkes ayni anda temizlemis olur.
	 *
	 * @return int silinen mesaj sayisi.
	 */
	public static function purge_disappeared( $conversation_id, $seconds ) {
		global $wpdb;
		$seconds = (int) $seconds;
		if ( $seconds <= 0 ) {
			return 0;
		}

		$messages = Naber_DB::table( 'messages' );
		$cutoff   = gmdate( 'Y-m-d H:i:s', time() - $seconds );

		return (int) $wpdb->query(
			$wpdb->prepare(
				"DELETE FROM {$messages} WHERE conversation_id = %d AND created_at < %s",
				(int) $conversation_id,
				$cutoff
			)
		);
	}

	/** Grubun tamamini kapsayan bahsetme sozcukleri. */
	const MENTION_ALL_TOKENS = array( '@herkes', '@hepsi', '@everyone' );

	/**
	 * Mesaj metninde bahsedilen ("@isim") uyeleri bulur. Saf fonksiyon:
	 * veritabanina dokunmaz, uye listesi disaridan verilir.
	 *
	 * @param string $body    Mesaj metni.
	 * @param array  $members Her biri 'id' ve 'display_name' iceren uyeler.
	 * @return int[] Bahsedilen uye kimlikleri.
	 */
	public static function mentioned_ids( $body, array $members ) {
		$body = (string) $body;
		if ( '' === $body || false === strpos( $body, '@' ) ) {
			return array();
		}

		$haystack = self::fold_for_match( $body );

		foreach ( self::MENTION_ALL_TOKENS as $token ) {
			if ( false !== strpos( $haystack, $token ) ) {
				return array_values( array_unique( array_map(
					function ( $member ) {
						return (int) $member['id'];
					},
					$members
				) ) );
			}
		}

		// Uzun isimler once denenir: "@Ali Veli" yazilmisken "Ali" adli
		// uyenin de bahsedilmis sayilmasi icin eslesen bolum metinden
		// silinir.
		usort(
			$members,
			function ( $a, $b ) {
				return strlen( (string) $b['display_name'] ) - strlen( (string) $a['display_name'] );
			}
		);

		$found = array();
		foreach ( $members as $member ) {
			$name = trim( (string) $member['display_name'] );
			if ( '' === $name ) {
				continue;
			}
			$token  = '@' . self::fold_for_match( $name );
			$offset = strpos( $haystack, $token );
			if ( false === $offset ) {
				continue;
			}
			$found[]  = (int) $member['id'];
			$haystack = substr_replace( $haystack, str_repeat( ' ', strlen( $token ) ), $offset, strlen( $token ) );
		}

		return array_values( array_unique( $found ) );
	}

	/**
	 * Buyuk/kucuk harf ve Turkce harf farklarini yok sayar.
	 *
	 * "@ALI" yazan biri "Ali" adli uyeden bahsetmis sayilmali; ayrica
	 * Turkce klavyesi olmayan biri "@Sukru" yazip "Sukru" adli uyeyi
	 * bulabilmeli.
	 */
	private static function fold_for_match( $text ) {
		$map = array(
			'İ' => 'i', 'I' => 'i', 'ı' => 'i',
			'Ş' => 's', 'ş' => 's',
			'Ğ' => 'g', 'ğ' => 'g',
			'Ü' => 'u', 'ü' => 'u',
			'Ö' => 'o', 'ö' => 'o',
			'Ç' => 'c', 'ç' => 'c',
		);
		$text = strtr( (string) $text, $map );
		return function_exists( 'mb_strtolower' ) ? mb_strtolower( $text, 'UTF-8' ) : strtolower( $text );
	}

	public static function set_role( $conversation_id, $user_id, $role ) {
		global $wpdb;
		self::touch_conversation( $conversation_id );
		return (bool) $wpdb->update(
			Naber_DB::table( 'members' ),
			array( 'role' => $role ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%s' ),
			array( '%d', '%d' )
		);
	}

	/**
	 * Sohbetin en ustune tutturulan mesaj.
	 *
	 * Ayri bir "sabitlenenler" ekrani yok: tek mesaj sohbetin basinda
	 * pano ignesi isaretiyle durur. 0 verilirse sabitleme kaldirilir.
	 */
	public static function set_pinned_message( $conversation_id, $message_id ) {
		global $wpdb;
		$wpdb->update(
			Naber_DB::table( 'conversations' ),
			array( 'pinned_message_id' => (int) $message_id ),
			array( 'id' => (int) $conversation_id ),
			array( '%d' ),
			array( '%d' )
		);
		self::touch_conversation( $conversation_id );
		return true;
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
		if ( isset( $fields['owner_id'] ) ) {
			$data['owner_id'] = (int) $fields['owner_id'];
			$formats[]        = '%d';
		}
		if ( ! $data ) {
			return false;
		}

		$wpdb->update( Naber_DB::table( 'conversations' ), $data, array( 'id' => (int) $conversation_id ), $formats, array( '%d' ) );
		self::touch_conversation( $conversation_id );
		return true;
	}

	/** Sabitlenmis mesajin yuku; silinmisse ya da yoksa null. */
	public static function pinned_message_payload( $row, $viewer_id ) {
		global $wpdb;
		$message_id = (int) ( $row['pinned_message_id'] ?? 0 );
		if ( $message_id <= 0 ) {
			return null;
		}
		$message = $wpdb->get_row(
			$wpdb->prepare(
				'SELECT * FROM ' . Naber_DB::table( 'messages' ) . ' WHERE id = %d AND conversation_id = %d AND deleted = 0',
				$message_id,
				(int) $row['id']
			),
			ARRAY_A
		);
		if ( ! $message ) {
			return null;
		}
		return self::message_payload( $message, 'group' === $row['type'], (int) $viewer_id, array() );
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

	/**
	 * Sohbet listesi.
	 *
	 * @param int $since 0 ise butun liste doner. Sifirdan buyukse yalnizca
	 *                   o zamandan sonra degisen sohbetler doner (delta):
	 *                   liste her yoklamada bastan indirilmez.
	 */
	public static function list_for_user( $user_id, $since = 0 ) {
		global $wpdb;
		$conversations = Naber_DB::table( 'conversations' );
		$members       = Naber_DB::table( 'members' );
		$messages      = Naber_DB::table( 'messages' );
		$user_id       = (int) $user_id;
		$since         = (int) $since;

		$filter = '';
		if ( $since > 0 ) {
			$filter = $wpdb->prepare( ' AND c.updated_at > %s', gmdate( 'Y-m-d H:i:s', $since ) );
		}

		$rows = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT c.*, me.role, me.last_read_id, me.chat_muted, me.notify_muted,
					(SELECT COUNT(*) FROM {$messages} m WHERE m.conversation_id = c.id AND m.id > me.last_read_id AND m.sender_id <> %d AND m.deleted = 0) AS unread,
					(SELECT COUNT(*) FROM {$members} mm WHERE mm.conversation_id = c.id) AS member_count
				 FROM {$conversations} c
				 INNER JOIN {$members} me ON me.conversation_id = c.id AND me.user_id = %d
				 WHERE 1 = 1{$filter}
				 ORDER BY (me.pinned_at IS NOT NULL) DESC, c.updated_at DESC
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
				// Sohbet listesindeki onizleme reaksiyon gostermiyor; gereksiz
				// sorgudan kacinmak icin bos dizi verilir.
				$last = self::message_payload( $last_row, 'group' === $row['type'], 0, array() );
			}
		}

		$unread = isset( $row['unread'] ) ? (int) $row['unread'] : self::unread_in( (int) $row['id'], $user_id );

		$payload = array(
			'id'           => (int) $row['id'],
			'type'         => (string) $row['type'],
			'title'        => 'group' === $row['type'] ? (string) $row['title'] : ( $peer ? $peer['display_name'] : '' ),
			'about'        => (string) $row['about'],
			'avatar'       => 'group' === $row['type'] ? Naber_Media::url_for_id( (int) $row['avatar_media_id'] ) : ( $peer ? $peer['avatar'] : '' ),
			// Imzali adres her istekte degisir; istemci fotografi bu kimlige gore
			// cihazda saklar ve ayni fotografi bir daha indirmez.
			'avatar_id'    => 'group' === $row['type'] ? (int) $row['avatar_media_id'] : ( $peer ? (int) $peer['avatar_id'] : 0 ),
			'peer'         => $peer,
			'owner_id'     => (int) $row['owner_id'],
			'member_count' => isset( $row['member_count'] ) ? (int) $row['member_count'] : count( self::member_ids( (int) $row['id'] ) ),
			'role'         => $member ? (string) $member['role'] : '',
			// Kendi ayrintili yetkilerim; arayuz dugmeleri buna gore acilir.
			'perms'        => $member ? self::perms_for( (string) $member['role'], isset( $member['perms'] ) ? $member['perms'] : '' ) : array(),
			'chat_muted'   => $member ? (bool) (int) $member['chat_muted'] : false,
			'notify_muted' => $member ? self::is_notify_muted( $member ) : false,
			// Sureli sessize almanin ne zaman bitecegi (unix saniye); suresiz ise 0.
			'notify_muted_until' => ( $member && ! empty( $member['notify_muted_until'] ) ) ? self::ts( $member['notify_muted_until'] ) : 0,
			'pinned'       => $member ? ! empty( $member['pinned_at'] ) : false,
			// Sadece gruplarda anlamli; "Kod ile grup bul" ekraninda kullanilir.
			// Bu ozellikten once olusmus gruplarin kodu yoktur; ilk gorulduginde
			// bir kereye mahsus olusturulup kaydedilir.
			'invite_code'  => 'group' === $row['type']
				? ( (string) $row['invite_code'] ?: self::assign_invite_code( (int) $row['id'] ) )
				: '',
			// Kaybolan mesajlar: 0 kapali, degilse mesaj omru (saniye).
			'disappear_seconds' => (int) ( $row['disappear_seconds'] ?? 0 ),
			// Sohbetin en ustune tutturulan mesaj (yoksa null).
			'pinned_message' => self::pinned_message_payload( $row, $user_id ),
			'unread'       => $unread,
			'updated_at'   => self::ts( $row['updated_at'] ),
			'last_message' => $last,
			'read_watermark' => self::read_watermark( (int) $row['id'], $user_id ),
			'delivered_watermark' => self::delivered_watermark( (int) $row['id'], $user_id ),
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
		$table    = Naber_DB::table( 'messages' );
		$limit    = isset( $args['limit'] ) ? max( 1, min( 100, (int) $args['limit'] ) ) : 50;
		$after    = isset( $args['after'] ) ? (int) $args['after'] : 0;
		$is_group = ! empty( $args['is_group'] );
		$user_id  = isset( $args['user_id'] ) ? (int) $args['user_id'] : 0;

		// Kullanicinin "kendimden sil" dedigi mesajlar listede gorunmez.
		$hidden = $user_id > 0
			? $wpdb->prepare( ' AND NOT FIND_IN_SET(%d, hidden_for)', $user_id )
			: '';

		if ( $after > 0 ) {
			$rows = $wpdb->get_results(
				$wpdb->prepare( "SELECT * FROM {$table} WHERE conversation_id = %d AND id > %d{$hidden} ORDER BY id ASC LIMIT %d", $conversation_id, $after, $limit ),
				ARRAY_A
			);
		} else {
			$before = isset( $args['before'] ) ? (int) $args['before'] : 0;
			if ( $before > 0 ) {
				$rows = $wpdb->get_results(
					$wpdb->prepare( "SELECT * FROM {$table} WHERE conversation_id = %d AND id < %d{$hidden} ORDER BY id DESC LIMIT %d", $conversation_id, $before, $limit ),
					ARRAY_A
				);
			} else {
				$rows = $wpdb->get_results(
					$wpdb->prepare( "SELECT * FROM {$table} WHERE conversation_id = %d{$hidden} ORDER BY id DESC LIMIT %d", $conversation_id, $limit ),
					ARRAY_A
				);
			}
			$rows = array_reverse( (array) $rows );
		}

		// Reaksiyonlar her satir icin ayri ayri degil, tek sorguda toplu
		// cekilir; N mesaj icin N sorgu atmaktan kacinilir.
		$ids       = array_column( (array) $rows, 'id' );
		$reactions = Naber_Reactions::summary_for_many( $ids, $user_id );

		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = self::message_payload( $row, $is_group, $user_id, $reactions[ (int) $row['id'] ] ?? array() );
		}
		return $out;
	}

	public static function insert_message( $conversation_id, $sender_id, $receiver_id, $type, $body, $media_id = 0, $client_id = '', $preview = '', $reply_to = 0 ) {
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
				return self::message_payload( $existing, false, (int) $sender_id );
			}
		}

		// Yanitlanan mesaj gercekten bu sohbette mi? Baska sohbetten sizma
		// veya silinmis/yok bir mesaja baglanmaya calisilirsa yok sayilir.
		$reply_to = (int) $reply_to;
		if ( $reply_to > 0 ) {
			$valid = $wpdb->get_var(
				$wpdb->prepare(
					"SELECT id FROM {$messages} WHERE id = %d AND conversation_id = %d AND deleted = 0",
					$reply_to,
					(int) $conversation_id
				)
			);
			if ( ! $valid ) {
				$reply_to = 0;
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
				'preview'         => (string) $preview,
				'client_id'       => (string) $client_id,
				'reply_to_id'     => $reply_to,
				'is_read'         => 0,
				'created_at'      => $now,
			),
			array( '%d', '%d', '%d', '%s', '%s', '%d', '%s', '%s', '%d', '%d', '%s' )
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
		// Yeni olusturulan mesajin henuz reaksiyonu olamaz; sorgu atlanir.
		return self::message_payload( $row, false, 0, array() );
	}

	public static function delete_message( $message_id ) {
		global $wpdb;
		$message = self::get_message( $message_id );
		$done    = (bool) $wpdb->update(
			Naber_DB::table( 'messages' ),
			array( 'deleted' => 1, 'body' => '', 'media_id' => 0 ),
			array( 'id' => (int) $message_id ),
			array( '%d', '%s', '%d' ),
			array( '%d' )
		);

		// Karsi tarafin ekrani kendiliginden tazelensin.
		if ( $message ) {
			self::touch_conversation( (int) $message['conversation_id'] );
			$wpdb->delete( Naber_DB::table( 'reactions' ), array( 'message_id' => (int) $message_id ), array( '%d' ) );
		}
		return $done;
	}

	public static function get_message( $message_id ) {
		global $wpdb;
		return $wpdb->get_row( $wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'messages' ) . ' WHERE id = %d', (int) $message_id ), ARRAY_A );
	}

	/**
	 * Metin mesajini duzenler. Yalnizca metin mesajlarinda anlamlidir;
	 * gorsel/silinmis mesajlar cagiran tarafta elenmis olmali.
	 */
	public static function edit_message( $message_id, $body ) {
		global $wpdb;
		$messages = Naber_DB::table( 'messages' );
		$updated  = (bool) $wpdb->update(
			$messages,
			array( 'body' => (string) $body, 'edited_at' => Naber_DB::now() ),
			array( 'id' => (int) $message_id ),
			array( '%s', '%s' ),
			array( '%d' )
		);

		$row = $wpdb->get_row( $wpdb->prepare( "SELECT * FROM {$messages} WHERE id = %d", (int) $message_id ), ARRAY_A );
		if ( $row ) {
			self::touch_conversation( (int) $row['conversation_id'] );
		}
		return $updated ? self::message_payload( $row, false, (int) $row['sender_id'] ) : null;
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
		// Okundu bilgisini gizleyen kullanici baskalarininkini de goremez.
		if ( Naber_Auth::hides_read( $user_id ) ) {
			return 0;
		}

		$table  = Naber_DB::table( 'members' );
		$hidden = self::hidden_read_filter();
		$value  = $wpdb->get_var(
			$wpdb->prepare( "SELECT MIN(last_read_id) FROM {$table} WHERE conversation_id = %d AND user_id <> %d{$hidden}", (int) $conversation_id, (int) $user_id )
		);
		return null === $value ? 0 : (int) $value;
	}

	/**
	 * Okundu bilgisini gizleyen kullanicilari sorgudan cikaran SQL parcasi.
	 *
	 * Gizleyen biri sohbette varsa MIN(last_read_id) hesabina katilmaz;
	 * birebir sohbette bu, hic okundu bilgisi gonderilmemesi demektir.
	 */
	private static function hidden_read_filter( $column = 'user_id' ) {
		$ids = Naber_Auth::hidden_read_ids();
		if ( ! $ids ) {
			return '';
		}
		return ' AND ' . $column . ' NOT IN (' . implode( ',', array_map( 'intval', $ids ) ) . ')';
	}

	/** Diger uyelerin hepsinin cihazina ulasan en yuksek mesaj kimligi. */
	public static function delivered_watermark( $conversation_id, $user_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'members' );
		$value = $wpdb->get_var(
			$wpdb->prepare( "SELECT MIN(delivered_id) FROM {$table} WHERE conversation_id = %d AND user_id <> %d", (int) $conversation_id, (int) $user_id )
		);
		return null === $value ? 0 : (int) $value;
	}

	/** Kullanicinin sohbetlerindeki diger herkes (tek sorgu). */
	public static function peer_ids( $user_id ) {
		global $wpdb;
		$members = Naber_DB::table( 'members' );
		$ids     = $wpdb->get_col(
			$wpdb->prepare(
				"SELECT DISTINCT other.user_id FROM {$members} me
				 INNER JOIN {$members} other ON other.conversation_id = me.conversation_id AND other.user_id <> me.user_id
				 WHERE me.user_id = %d LIMIT 200",
				(int) $user_id
			)
		);
		return array_map( 'intval', (array) $ids );
	}

	/**
	 * Sohbetlerin son degisiklik zamanlari.
	 * Istemci bunu karsilastirip yalnizca degiseni yeniden ceker; grup adi,
	 * uye listesi veya susturma degistiginde ekran kendiliginden guncellenir.
	 */
	public static function revisions( $user_id ) {
		global $wpdb;
		$conversations = Naber_DB::table( 'conversations' );
		$members       = Naber_DB::table( 'members' );

		$rows = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT c.id, c.meta_rev FROM {$conversations} c
				 INNER JOIN {$members} me ON me.conversation_id = c.id AND me.user_id = %d
				 LIMIT 200",
				(int) $user_id
			),
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = array(
				'id'         => (int) $row['id'],
				'updated_at' => (int) $row['meta_rev'],
			);
		}
		return $out;
	}

	/**
	 * Sohbette yapisal bir degisiklik (ad, uye, rol, susturma) oldugunda
	 * surum numarasini artirir. Yeni mesajlar bu sayaci degistirmez; boylece
	 * uygulamalar yalnizca gercekten degisen sohbeti yeniden ceker.
	 */
	public static function touch_conversation( $conversation_id ) {
		global $wpdb;
		$table = Naber_DB::table( 'conversations' );
		$wpdb->query( $wpdb->prepare( "UPDATE {$table} SET meta_rev = meta_rev + 1 WHERE id = %d", (int) $conversation_id ) );
	}

	/**
	 * Uzun yoklama icin ucuz kontrol: yalnizca en son mesaj ve sinyal kimligi.
	 * Tam yanit ancak gercekten yeni bir sey varsa hazirlanir.
	 *
	 * @return array{message:int,signal:int}
	 */
	public static function probe( $user_id ) {
		global $wpdb;
		$messages = Naber_DB::table( 'messages' );
		$members  = Naber_DB::table( 'members' );
		$signals  = Naber_DB::table( 'signals' );

		$max_message = $wpdb->get_var(
			$wpdb->prepare(
				"SELECT MAX(m.id) FROM {$messages} m
				 INNER JOIN {$members} me ON me.conversation_id = m.conversation_id AND me.user_id = %d",
				(int) $user_id
			)
		);

		$max_signal = $wpdb->get_var(
			$wpdb->prepare( "SELECT MAX(id) FROM {$signals} WHERE receiver_id = %d", (int) $user_id )
		);

		return array(
			'message' => (int) $max_message,
			'signal'  => (int) $max_signal,
		);
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
				 WHERE m.id > %d AND NOT FIND_IN_SET(me.user_id, m.hidden_for)
				 ORDER BY m.id ASC
				 LIMIT %d",
				(int) $user_id,
				(int) $since_id,
				(int) $limit
			),
			ARRAY_A
		);

		$ids       = array_column( (array) $rows, 'id' );
		$reactions = Naber_Reactions::summary_for_many( $ids, $user_id );

		$out = array();
		foreach ( (array) $rows as $row ) {
			$out[] = self::message_payload( $row, 'group' === $row['conversation_type'], $user_id, $reactions[ (int) $row['id'] ] ?? array() );
		}
		return $out;
	}

	/**
	 * Her sohbet icin karsi tarafin teslim ve okuma seviyesi.
	 * Tek tik = sunucuda, gri cift tik = cihaza ulasti, mavi cift tik = okundu.
	 */
	public static function read_states( $user_id ) {
		global $wpdb;
		$members = Naber_DB::table( 'members' );
		// Okundu bilgisini gizleyen kullanici baskalarininkini de goremez.
		if ( Naber_Auth::hides_read( $user_id ) ) {
			return array();
		}
		$hidden = self::hidden_read_filter( 'others.user_id' );
		$rows   = $wpdb->get_results(
			$wpdb->prepare(
				"SELECT mine.conversation_id,
					COALESCE(MIN(others.last_read_id), 0) AS watermark,
					COALESCE(MIN(others.delivered_id), 0) AS delivered
				 FROM {$members} mine
				 LEFT JOIN {$members} others ON others.conversation_id = mine.conversation_id AND others.user_id <> mine.user_id{$hidden}
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
				'delivered'       => (int) $row['delivered'],
			);
		}
		return $out;
	}

	/**
	 * Kullaniciya ulasan mesajlari "iletildi" olarak isaretler.
	 * Olay akisi mesajlari teslim ettiginde cagrilir.
	 */
	public static function mark_delivered( $user_id, array $messages ) {
		if ( ! $messages ) {
			return;
		}

		global $wpdb;
		$by_conversation = array();
		foreach ( $messages as $message ) {
			if ( (int) $message['sender_id'] === (int) $user_id ) {
				continue;
			}
			$conversation_id = (int) $message['conversation_id'];
			$id              = (int) $message['id'];
			if ( ! isset( $by_conversation[ $conversation_id ] ) || $by_conversation[ $conversation_id ] < $id ) {
				$by_conversation[ $conversation_id ] = $id;
			}
		}
		if ( ! $by_conversation ) {
			return;
		}

		$members  = Naber_DB::table( 'members' );
		$messages_table = Naber_DB::table( 'messages' );
		$now      = Naber_DB::now();

		foreach ( $by_conversation as $conversation_id => $max_id ) {
			$wpdb->query(
				$wpdb->prepare(
					"UPDATE {$members} SET delivered_id = %d
					 WHERE conversation_id = %d AND user_id = %d AND delivered_id < %d",
					$max_id,
					$conversation_id,
					(int) $user_id,
					$max_id
				)
			);
			$wpdb->query(
				$wpdb->prepare(
					"UPDATE {$messages_table} SET delivered_at = %s
					 WHERE conversation_id = %d AND id <= %d AND sender_id <> %d AND delivered_at IS NULL",
					$now,
					$conversation_id,
					$max_id,
					(int) $user_id
				)
			);
		}
	}

	/** "Kendimden sil": mesaj yalnizca bu kullanicidan gizlenir. */
	public static function hide_message( $message_id, $user_id ) {
		// Gizleme yalniz bu kullaniciyi ilgilendirir; surum sayaci artmaz.
		global $wpdb;
		$table   = Naber_DB::table( 'messages' );
		$message = self::get_message( $message_id );
		if ( ! $message ) {
			return false;
		}

		$hidden = array_filter( array_map( 'intval', explode( ',', (string) $message['hidden_for'] ) ) );
		if ( ! in_array( (int) $user_id, $hidden, true ) ) {
			$hidden[] = (int) $user_id;
		}

		$wpdb->update(
			$table,
			array( 'hidden_for' => implode( ',', $hidden ) ),
			array( 'id' => (int) $message_id ),
			array( '%s' ),
			array( '%d' )
		);
		return true;
	}

	/** Mesaj bilgisi ekrani: kime ne zaman ulasti, kim okudu. */
	public static function message_info( $message_id, $user_id ) {
		global $wpdb;
		$message = self::get_message( $message_id );
		if ( ! $message ) {
			return null;
		}

		$conversation_id = (int) $message['conversation_id'];
		$members         = Naber_DB::table( 'members' );
		$rows            = $wpdb->get_results(
			$wpdb->prepare( "SELECT user_id, delivered_id, last_read_id FROM {$members} WHERE conversation_id = %d", $conversation_id ),
			ARRAY_A
		);

		$recipients = array();
		foreach ( (array) $rows as $row ) {
			if ( (int) $row['user_id'] === (int) $message['sender_id'] ) {
				continue;
			}
			$user = Naber_Auth::user_payload( (int) $row['user_id'] );
			if ( ! $user ) {
				continue;
			}
			$recipients[] = array(
				'id'        => (int) $row['user_id'],
				'name'      => $user['display_name'],
				'delivered' => (int) $row['delivered_id'] >= (int) $message['id'],
				'read'      => (int) $row['last_read_id'] >= (int) $message['id'],
			);
		}

		$media = (int) $message['media_id'] > 0 ? Naber_Media::get( (int) $message['media_id'] ) : null;

		return array(
			'id'           => (int) $message['id'],
			'type'         => (string) $message['message_type'],
			'sender'       => Naber_Auth::user_payload( (int) $message['sender_id'] ),
			'created_at'   => self::ts( $message['created_at'] ),
			'delivered_at' => self::ts( $message['delivered_at'] ),
			'read_at'      => self::ts( $message['read_at'] ),
			'deleted'      => (bool) (int) $message['deleted'],
			'recipients'   => $recipients,
			'media'        => $media ? array(
				'size'   => (int) $media['size'],
				'width'  => (int) $media['width'],
				'height' => (int) $media['height'],
				'mime'   => (string) $media['mime'],
			) : null,
			'own'          => (int) $message['sender_id'] === (int) $user_id,
		);
	}

	/**
	 * "Yaziyor" durumu sohbet basina tek bir kayitta tutulur
	 * (kullanici basina ayri kayit yerine), boylece her kontrolde tek okuma yapilir.
	 */
	private static function typing_key( $conversation_id ) {
		return 'naber_typing_' . (int) $conversation_id;
	}

	public static function set_typing( $conversation_id, $user_id, $typing = true ) {
		$key   = self::typing_key( $conversation_id );
		$state = get_transient( $key );
		if ( ! is_array( $state ) ) {
			$state = array();
		}

		$now = time();
		foreach ( $state as $uid => $stamp ) {
			if ( $now - (int) $stamp > 10 ) {
				unset( $state[ $uid ] );
			}
		}
		if ( $typing ) {
			$state[ (int) $user_id ] = $now;
		} else {
			unset( $state[ (int) $user_id ] );
		}

		set_transient( $key, $state, 30 );
	}

	public static function typing_state( $conversation_id, $other_user_id ) {
		$state = get_transient( self::typing_key( $conversation_id ) );
		if ( ! is_array( $state ) || ! isset( $state[ (int) $other_user_id ] ) ) {
			return false;
		}
		return ( time() - (int) $state[ (int) $other_user_id ] ) < 8;
	}

	/** Yazma durumunun kisa imzasi (degisiklik tespiti icin). */
	public static function typing_signature( $conversation_id, $user_id ) {
		$users = self::typing_users( $conversation_id, $user_id );
		$ids   = array();
		foreach ( $users as $user ) {
			$ids[] = (int) $user['id'];
		}
		sort( $ids );
		return implode( ',', $ids );
	}

	/** Belirli bir sohbette yazan kisiler (kendisi haric). */
	public static function typing_users( $conversation_id, $user_id ) {
		$state = get_transient( self::typing_key( $conversation_id ) );
		if ( ! is_array( $state ) ) {
			return array();
		}

		$now = time();
		$out = array();
		foreach ( $state as $uid => $stamp ) {
			if ( (int) $uid === (int) $user_id || ( $now - (int) $stamp ) >= 8 ) {
				continue;
			}
			$user = Naber_Auth::user_payload( (int) $uid );
			if ( $user ) {
				$out[] = array( 'id' => (int) $uid, 'name' => $user['display_name'] );
			}
		}
		return $out;
	}

	/**
	 * @param int        $viewer_id "reacted" alaninin kimin gozuyle
	 *                              hesaplanacagi.
	 * @param array|null $reactions Onceden (toplu) hesaplanmis reaksiyon
	 *                              ozeti verilebilir; birden fazla mesaj
	 *                              donduren dongulerde her satir icin ayri
	 *                              sorgu atmamak icin kullanilir. Null ise
	 *                              bu tek satir icin ayrica sorgulanir.
	 */
	public static function message_payload( $row, $is_group = false, $viewer_id = 0, $reactions = null ) {
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
			// Gorselin cok kucuk on izlemesi; mesajla birlikte gelir, aninda cizilir.
			'preview'         => isset( $row['preview'] ) ? (string) $row['preview'] : '',
			'media'           => null,
			'sender_name'     => '',
			'sender_avatar'   => '',
			'edited'          => ! empty( $row['edited_at'] ) && '0000-00-00 00:00:00' !== $row['edited_at'],
			'reply'           => null,
			'reactions'       => array(),
			'poll'            => null,
		);

		if ( 'poll' === $payload['type'] && ! $payload['deleted'] ) {
			$payload['poll'] = Naber_Polls::payload( Naber_Polls::for_message( (int) $row['id'] ), $viewer_id );
		}

		if ( ! $payload['deleted'] ) {
			$payload['reactions'] = ( null !== $reactions ) ? $reactions : Naber_Reactions::summary( (int) $row['id'], $viewer_id );
		}

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

		if ( ! empty( $row['reply_to_id'] ) && (int) $row['reply_to_id'] > 0 ) {
			$payload['reply'] = self::reply_summary( (int) $row['reply_to_id'] );
		}

		return $payload;
	}

	/**
	 * Yanitlanan mesajin kisa ozeti (balonun ustunde gosterilir).
	 * Tam mesaj payload'u degil; gereksiz alanlar (kendi reply'i, medyanin
	 * tamami vb.) tasinmaz.
	 */
	private static function reply_summary( $message_id ) {
		global $wpdb;
		$row = $wpdb->get_row(
			$wpdb->prepare( 'SELECT * FROM ' . Naber_DB::table( 'messages' ) . ' WHERE id = %d', $message_id ),
			ARRAY_A
		);
		if ( ! $row ) {
			return null;
		}

		$sender = Naber_Auth::user_payload( (int) $row['sender_id'] );
		$deleted = (bool) (int) $row['deleted'];

		return array(
			'id'          => (int) $row['id'],
			'sender_id'   => (int) $row['sender_id'],
			'sender_name' => $sender ? $sender['display_name'] : '',
			'type'        => (string) $row['message_type'],
			'body'        => $deleted ? '' : (string) $row['body'],
			'deleted'     => $deleted,
		);
	}

	public static function ts( $mysql_date ) {
		if ( ! $mysql_date || '0000-00-00 00:00:00' === $mysql_date ) {
			return 0;
		}
		return (int) strtotime( $mysql_date . ' UTC' );
	}
}
