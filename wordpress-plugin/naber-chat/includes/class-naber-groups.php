<?php
/**
 * Grup yonetimi: sistem mesajlari, sahiplik devri ve "saka savunmasi".
 *
 * Grup ozel kurallari burada toplanir; REST katmani yalnizca cagirir.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Groups {

	/** Gecici atmanin ne kadar sonra geri alinacagi. */
	const PRANK_RESTORE_SECONDS = 10;

	/** Bekleyen geri almalarin tutuldugu secenek adi. */
	const PRANK_OPTION = 'naber_prank_restores';

	/**
	 * Yoneticiyi gruptan atmaya kalkisana gosterilen mesajlar.
	 * Rastgele biri secilir ki her seferinde ayni sey cikmasin.
	 */
	const PRANK_MESSAGES = array(
		'Yoneticiyi atmaya calistin. Tebrikler, kendini attin. 10 saniye dusun bakalim.',
		'Darbe girisimi basarisiz. Gruba 10 saniye sonra geri alinacaksin.',
		'Yonetici koltugu senlik degil. 10 saniyelik zorunlu mola.',
		'O dugmeye basmamalidin. Gorusuruz, 10 saniye sonra.',
		'Sistem: "yonetici atilamaz" dedi ve seni atti. Az sonra donersin.',
	);

	/** Sistem mesaji: sohbette ortalanmis kucuk bilgi satiri olarak gorunur. */
	public static function system_message( $conversation_id, $body ) {
		$body = trim( (string) $body );
		if ( '' === $body || (int) $conversation_id <= 0 ) {
			return null;
		}
		return Naber_Chat_Repo::insert_message( (int) $conversation_id, 0, 0, 'system', $body );
	}

	/** Sistem mesajlarinda kullanilan gorunen ad. */
	public static function display_name( $user_id ) {
		$user = get_userdata( (int) $user_id );
		if ( ! $user ) {
			return 'Bir uye';
		}
		$name = trim( (string) $user->display_name );
		return '' !== $name ? $name : (string) $user->user_login;
	}

	/**
	 * Kalan uyeler arasindan rastgele yeni sahip secer.
	 * Saf fonksiyon: testte dogrudan cagrilabilir.
	 */
	public static function pick_random_owner( $member_ids, $exclude = 0 ) {
		$pool = array();
		foreach ( (array) $member_ids as $id ) {
			$id = (int) $id;
			if ( $id > 0 && $id !== (int) $exclude ) {
				$pool[] = $id;
			}
		}
		if ( ! $pool ) {
			return 0;
		}
		return (int) $pool[ random_int( 0, count( $pool ) - 1 ) ];
	}

	/** Sahipligi baska bir uyeye devreder; eski sahip yonetici olur. */
	public static function transfer_ownership( $conversation_id, $new_owner_id, $old_owner_id = 0 ) {
		$conversation_id = (int) $conversation_id;
		$new_owner_id    = (int) $new_owner_id;
		if ( $conversation_id <= 0 || $new_owner_id <= 0 ) {
			return false;
		}
		if ( ! Naber_Chat_Repo::member( $conversation_id, $new_owner_id ) ) {
			return false;
		}

		if ( (int) $old_owner_id > 0 && Naber_Chat_Repo::member( $conversation_id, (int) $old_owner_id ) ) {
			Naber_Chat_Repo::set_role( $conversation_id, (int) $old_owner_id, 'admin' );
		}
		Naber_Chat_Repo::set_role( $conversation_id, $new_owner_id, 'owner' );
		Naber_Chat_Repo::update_group( $conversation_id, array( 'owner_id' => $new_owner_id ) );

		self::system_message(
			$conversation_id,
			self::display_name( $new_owner_id ) . ' artik grubun sahibi.'
		);
		return true;
	}

	/**
	 * Sahip gruptan ayrildi: kalanlardan rastgele biri sahip olur.
	 * Kullanicinin istegi: "yonetici gruptan cikarsa rastgele verilsin".
	 */
	public static function auto_transfer_ownership( $conversation_id, $leaving_user_id = 0 ) {
		$remaining = Naber_Chat_Repo::member_ids( (int) $conversation_id );
		$new_owner = self::pick_random_owner( $remaining, $leaving_user_id );
		if ( $new_owner <= 0 ) {
			return 0;
		}
		Naber_Chat_Repo::set_role( (int) $conversation_id, $new_owner, 'owner' );
		Naber_Chat_Repo::update_group( (int) $conversation_id, array( 'owner_id' => $new_owner ) );
		self::system_message(
			(int) $conversation_id,
			self::display_name( $new_owner ) . ' rastgele secilerek grubun yeni sahibi oldu.'
		);
		return $new_owner;
	}

	/** Rastgele saka mesaji. */
	public static function random_prank_message() {
		$list = self::PRANK_MESSAGES;
		return (string) $list[ random_int( 0, count( $list ) - 1 ) ];
	}

	/**
	 * Saka savunmasi: sahibi cikarmaya calisan kisi gecici olarak atilir.
	 * Rolu ve yetkileri saklanir, sure dolunca aynen geri verilir.
	 */
	public static function prank_kick( $conversation_id, $attacker_id ) {
		$conversation_id = (int) $conversation_id;
		$attacker_id     = (int) $attacker_id;
		$member          = Naber_Chat_Repo::member( $conversation_id, $attacker_id );
		if ( ! $member ) {
			return null;
		}

		$entry = array(
			'conversation_id' => $conversation_id,
			'user_id'         => $attacker_id,
			'role'            => (string) $member['role'],
			'perms'           => isset( $member['perms'] ) ? (string) $member['perms'] : '',
			'due'             => time() + self::PRANK_RESTORE_SECONDS,
		);

		$pending = self::pending();
		// Ayni kisi icin ikinci bir kayit birikmesin.
		$pending = array_values( array_filter(
			$pending,
			static function ( $row ) use ( $conversation_id, $attacker_id ) {
				return ! ( (int) $row['conversation_id'] === $conversation_id && (int) $row['user_id'] === $attacker_id );
			}
		) );
		$pending[] = $entry;
		update_option( self::PRANK_OPTION, $pending, false );

		Naber_Chat_Repo::remove_member( $conversation_id, $attacker_id );

		$message = self::random_prank_message();
		self::system_message(
			$conversation_id,
			self::display_name( $attacker_id ) . ' grup sahibini atmaya calisti ve kendini attirdi.'
		);

		return array(
			'message'         => $message,
			'restore_seconds' => self::PRANK_RESTORE_SECONDS,
		);
	}

	// ------------------------------------------------------------------
	// Uyelik onayi
	// ------------------------------------------------------------------

	/** Katilma istegi olusturur (ayni kisi icin ikinci kayit acilmaz). */
	public static function request_join( $conversation_id, $user_id ) {
		global $wpdb;
		$wpdb->query(
			$wpdb->prepare(
				'INSERT IGNORE INTO ' . Naber_DB::table( 'join_requests' ) . ' (conversation_id, user_id, created_at) VALUES (%d, %d, %s)',
				(int) $conversation_id,
				(int) $user_id,
				Naber_DB::now()
			)
		);
		return true;
	}

	/** Bekleyen katilma istekleri (kullanici bilgisiyle birlikte). */
	public static function join_requests( $conversation_id ) {
		global $wpdb;
		$rows = $wpdb->get_results(
			$wpdb->prepare(
				'SELECT * FROM ' . Naber_DB::table( 'join_requests' ) . ' WHERE conversation_id = %d ORDER BY id ASC',
				(int) $conversation_id
			),
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$user = Naber_Auth::user_payload( (int) $row['user_id'] );
			if ( ! $user ) {
				continue;
			}
			$user['requested_at'] = Naber_Chat_Repo::ts( $row['created_at'] );
			$out[]                = $user;
		}
		return $out;
	}

	public static function pending_request_count( $conversation_id ) {
		global $wpdb;
		return (int) $wpdb->get_var(
			$wpdb->prepare(
				'SELECT COUNT(*) FROM ' . Naber_DB::table( 'join_requests' ) . ' WHERE conversation_id = %d',
				(int) $conversation_id
			)
		);
	}

	public static function has_request( $conversation_id, $user_id ) {
		global $wpdb;
		return (bool) $wpdb->get_var(
			$wpdb->prepare(
				'SELECT id FROM ' . Naber_DB::table( 'join_requests' ) . ' WHERE conversation_id = %d AND user_id = %d',
				(int) $conversation_id,
				(int) $user_id
			)
		);
	}

	public static function clear_request( $conversation_id, $user_id ) {
		global $wpdb;
		$wpdb->delete(
			Naber_DB::table( 'join_requests' ),
			array( 'conversation_id' => (int) $conversation_id, 'user_id' => (int) $user_id ),
			array( '%d', '%d' )
		);
		return true;
	}

	/** Istegi onaylar: kisi uye olur ve sohbete sistem mesaji dusulur. */
	public static function approve_request( $conversation_id, $user_id ) {
		if ( ! self::has_request( $conversation_id, $user_id ) ) {
			return false;
		}
		self::clear_request( $conversation_id, $user_id );
		Naber_Chat_Repo::add_member( (int) $conversation_id, (int) $user_id, 'member' );
		self::system_message(
			(int) $conversation_id,
			self::display_name( $user_id ) . ' katilma istegi onaylandi ve gruba eklendi.'
		);
		return true;
	}

	/** Bekleyen geri alma kayitlari. */
	public static function pending() {
		$stored = get_option( self::PRANK_OPTION, array() );
		return is_array( $stored ) ? array_values( $stored ) : array();
	}

	/**
	 * Suresi dolanlari ayirir.
	 * Saf fonksiyon: [0] geri alinacaklar, [1] beklemeye devam edenler.
	 */
	public static function split_due( $entries, $now ) {
		$due  = array();
		$wait = array();
		foreach ( (array) $entries as $entry ) {
			if ( ! is_array( $entry ) || empty( $entry['user_id'] ) ) {
				continue;
			}
			if ( (int) $entry['due'] <= (int) $now ) {
				$due[] = $entry;
			} else {
				$wait[] = $entry;
			}
		}
		return array( $due, $wait );
	}

	/**
	 * Suresi dolan gecici atmalari geri alir.
	 * Her yoklama isteginde cagrilir; bekleyen yoksa tek secenek okumasi
	 * disinda hicbir sey yapmaz.
	 */
	public static function restore_pranks() {
		$pending = self::pending();
		if ( ! $pending ) {
			return 0;
		}

		list( $due, $wait ) = self::split_due( $pending, time() );
		if ( ! $due ) {
			return 0;
		}

		foreach ( $due as $entry ) {
			$conversation_id = (int) $entry['conversation_id'];
			$user_id         = (int) $entry['user_id'];
			Naber_Chat_Repo::add_member( $conversation_id, $user_id, (string) $entry['role'] );
			if ( ! empty( $entry['perms'] ) ) {
				Naber_Chat_Repo::set_perms( $conversation_id, $user_id, (string) $entry['perms'] );
			}
			self::system_message(
				$conversation_id,
				self::display_name( $user_id ) . ' cezasini cekti, gruba geri alindi.'
			);
		}

		update_option( self::PRANK_OPTION, $wait, false );
		return count( $due );
	}
}
