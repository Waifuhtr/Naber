<?php
/**
 * Tablo semasi, kurulum ve surum yukseltme.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_DB {

	const DB_VERSION = '1.19.0';

	public static function table( $name ) {
		global $wpdb;
		return $wpdb->prefix . 'naber_' . $name;
	}

	public static function install() {
		global $wpdb;
		require_once ABSPATH . 'wp-admin/includes/upgrade.php';
		$charset = $wpdb->get_charset_collate();

		$conversations = self::table( 'conversations' );
		$members       = self::table( 'members' );
		$messages      = self::table( 'messages' );
		$media         = self::table( 'media' );
		$calls         = self::table( 'calls' );
		$participants  = self::table( 'call_participants' );
		$signals       = self::table( 'signals' );
		$devices       = self::table( 'devices' );
		$pokes         = self::table( 'pokes' );
		$reactions     = self::table( 'reactions' );
		$blocks        = self::table( 'blocks' );
		$polls         = self::table( 'polls' );
		$poll_votes    = self::table( 'poll_votes' );
		$join_requests = self::table( 'join_requests' );
		$stickers      = self::table( 'stickers' );

		$sql = array();

		// Hem birebir sohbetler hem gruplar ayni tabloda; ayrimi "type" yapar.
		$sql[] = "CREATE TABLE {$conversations} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			type varchar(10) NOT NULL DEFAULT 'direct',
			pair_key varchar(64) NOT NULL DEFAULT '',
			title varchar(191) NOT NULL DEFAULT '',
			about varchar(255) NOT NULL DEFAULT '',
			avatar_media_id bigint(20) unsigned NOT NULL DEFAULT 0,
			owner_id bigint(20) unsigned NOT NULL DEFAULT 0,
			user_one bigint(20) unsigned NOT NULL DEFAULT 0,
			user_two bigint(20) unsigned NOT NULL DEFAULT 0,
			last_message_id bigint(20) unsigned NOT NULL DEFAULT 0,
			pinned_message_id bigint(20) unsigned NOT NULL DEFAULT 0,
			meta_rev bigint(20) unsigned NOT NULL DEFAULT 1,
			invite_code varchar(10) NOT NULL DEFAULT '',
			disappear_seconds int(10) unsigned NOT NULL DEFAULT 0,
			mention_all_admins tinyint(1) NOT NULL DEFAULT 0,
			require_approval tinyint(1) NOT NULL DEFAULT 0,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			updated_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY pair_key (pair_key),
			KEY type (type),
			KEY updated_at (updated_at),
			KEY invite_code (invite_code)
		) {$charset};";

		// Her sohbetin katilimcilari: birebir sohbette 2, grupta N kisi.
		$sql[] = "CREATE TABLE {$members} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			conversation_id bigint(20) unsigned NOT NULL,
			user_id bigint(20) unsigned NOT NULL,
			role varchar(10) NOT NULL DEFAULT 'member',
			perms varchar(191) NOT NULL DEFAULT '',
			chat_muted tinyint(1) NOT NULL DEFAULT 0,
			notify_muted tinyint(1) NOT NULL DEFAULT 0,
			notify_muted_until datetime NULL,
			pinned_at datetime NULL,
			last_read_id bigint(20) unsigned NOT NULL DEFAULT 0,
			delivered_id bigint(20) unsigned NOT NULL DEFAULT 0,
			joined_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY member (conversation_id,user_id),
			KEY user_id (user_id)
		) {$charset};";

		// Uyelik onayi acik gruplarda davet koduyla katilmak isteyenler.
		$sql[] = "CREATE TABLE {$join_requests} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			conversation_id bigint(20) unsigned NOT NULL,
			user_id bigint(20) unsigned NOT NULL,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY request (conversation_id,user_id),
			KEY user_id (user_id)
		) {$charset};";

		// Cikartma paketleri ve ozel emojiler (statik + hareketli).
		$sql[] = "CREATE TABLE {$stickers} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			kind varchar(10) NOT NULL DEFAULT 'sticker',
			pack varchar(60) NOT NULL DEFAULT '',
			name varchar(60) NOT NULL DEFAULT '',
			media_id bigint(20) unsigned NOT NULL DEFAULT 0,
			animated tinyint(1) NOT NULL DEFAULT 0,
			uploader_id bigint(20) unsigned NOT NULL DEFAULT 0,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY kind_name (kind,name),
			KEY pack (kind,pack),
			KEY uploader_id (uploader_id)
		) {$charset};";

		$sql[] = "CREATE TABLE {$messages} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			conversation_id bigint(20) unsigned NOT NULL,
			sender_id bigint(20) unsigned NOT NULL,
			receiver_id bigint(20) unsigned NOT NULL DEFAULT 0,
			message_type varchar(20) NOT NULL DEFAULT 'text',
			body longtext NULL,
			media_id bigint(20) unsigned NOT NULL DEFAULT 0,
			preview mediumtext NULL,
			client_id varchar(64) NOT NULL DEFAULT '',
			reply_to_id bigint(20) unsigned NOT NULL DEFAULT 0,
			edited_at datetime NULL,
			is_read tinyint(1) NOT NULL DEFAULT 0,
			read_at datetime NULL,
			delivered_at datetime NULL,
			deleted tinyint(1) NOT NULL DEFAULT 0,
			hidden_for varchar(255) NOT NULL DEFAULT '',
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			KEY conversation_id (conversation_id,id),
			KEY receiver_unread (receiver_id,is_read),
			KEY sender_id (sender_id),
			KEY client_id (client_id),
			KEY reply_to_id (reply_to_id)
		) {$charset};";

		$sql[] = "CREATE TABLE {$media} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			owner_id bigint(20) unsigned NOT NULL,
			bucket varchar(191) NOT NULL DEFAULT '',
			file_name varchar(500) NOT NULL DEFAULT '',
			file_id varchar(191) NOT NULL DEFAULT '',
			mime varchar(100) NOT NULL DEFAULT '',
			hash varchar(64) NOT NULL DEFAULT '',
			size bigint(20) unsigned NOT NULL DEFAULT 0,
			width int(11) NOT NULL DEFAULT 0,
			height int(11) NOT NULL DEFAULT 0,
			status varchar(20) NOT NULL DEFAULT 'pending',
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			KEY owner_id (owner_id),
			KEY hash (hash),
			KEY status (status)
		) {$charset};";

		$sql[] = "CREATE TABLE {$calls} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			conversation_id bigint(20) unsigned NOT NULL DEFAULT 0,
			type varchar(10) NOT NULL DEFAULT 'direct',
			caller_id bigint(20) unsigned NOT NULL,
			callee_id bigint(20) unsigned NOT NULL DEFAULT 0,
			status varchar(20) NOT NULL DEFAULT 'ringing',
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			answered_at datetime NULL,
			ended_at datetime NULL,
			duration int(11) NOT NULL DEFAULT 0,
			end_reason varchar(40) NOT NULL DEFAULT '',
			PRIMARY KEY  (id),
			KEY caller_id (caller_id),
			KEY callee_id (callee_id),
			KEY conversation_id (conversation_id),
			KEY status (status)
		) {$charset};";

		// Grup aramalarinda her katilimcinin durumu ve yonetici kararlari.
		$sql[] = "CREATE TABLE {$participants} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			call_id bigint(20) unsigned NOT NULL,
			user_id bigint(20) unsigned NOT NULL,
			status varchar(12) NOT NULL DEFAULT 'ringing',
			muted tinyint(1) NOT NULL DEFAULT 0,
			joined_at datetime NULL,
			left_at datetime NULL,
			PRIMARY KEY  (id),
			UNIQUE KEY participant (call_id,user_id),
			KEY user_id (user_id)
		) {$charset};";

		$sql[] = "CREATE TABLE {$signals} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			call_id bigint(20) unsigned NOT NULL,
			sender_id bigint(20) unsigned NOT NULL,
			receiver_id bigint(20) unsigned NOT NULL,
			type varchar(20) NOT NULL DEFAULT '',
			payload longtext NULL,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			KEY receiver_call (receiver_id,call_id,id)
		) {$charset};";

		$sql[] = "CREATE TABLE {$devices} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			user_id bigint(20) unsigned NOT NULL,
			token varchar(255) NOT NULL,
			platform varchar(20) NOT NULL DEFAULT 'android',
			updated_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY token (token),
			KEY user_id (user_id)
		) {$charset};";

		// Durtme (poke): kim kimi ne zaman durttu. Bekleme suresi kontrolu ve
		// ileride "kim seni durttu" gecmisi icin ayri satirlar tutulur.
		$sql[] = "CREATE TABLE {$pokes} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			from_id bigint(20) unsigned NOT NULL,
			to_id bigint(20) unsigned NOT NULL,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			KEY pair_time (from_id,to_id,created_at),
			KEY to_id (to_id,created_at)
		) {$charset};";

		// Anket: secenekler ayri bir tablo yerine JSON olarak saklanir.
		// ~10 kisilik bir kurulumda secenek basina satir tutmanin getirisi
		// yok; oy sayimi zaten oy tablosundan yapiliyor.
		$sql[] = "CREATE TABLE {$polls} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			message_id bigint(20) unsigned NOT NULL,
			conversation_id bigint(20) unsigned NOT NULL,
			question varchar(255) NOT NULL DEFAULT '',
			options text NOT NULL,
			multiple tinyint(1) NOT NULL DEFAULT 0,
			created_by bigint(20) unsigned NOT NULL DEFAULT 0,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY message_id (message_id),
			KEY conversation_id (conversation_id)
		) {$charset};";

		// Oylar secenek sirasina gore tutulur; ayni kisi ayni secenege
		// iki kez oy veremez.
		$sql[] = "CREATE TABLE {$poll_votes} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			poll_id bigint(20) unsigned NOT NULL,
			user_id bigint(20) unsigned NOT NULL,
			option_index smallint(5) unsigned NOT NULL,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY vote (poll_id,user_id,option_index),
			KEY poll_id (poll_id)
		) {$charset};";

		// Engelleme: A, B'yi engellerse ikisi de birbirine birebir mesaj
		// gonderemez, arayamaz, durtemez. Gruplar etkilenmez.
		$sql[] = "CREATE TABLE {$blocks} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			blocker_id bigint(20) unsigned NOT NULL,
			blocked_id bigint(20) unsigned NOT NULL,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY pair (blocker_id,blocked_id),
			KEY blocked_id (blocked_id)
		) {$charset};";

		// Emoji reaksiyonu: kullanici basina mesaj basina en fazla bir emoji
		// (WhatsApp'taki gibi); ayni emojiye tekrar basmak kaldirir.
		$sql[] = "CREATE TABLE {$reactions} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			message_id bigint(20) unsigned NOT NULL,
			user_id bigint(20) unsigned NOT NULL,
			emoji varchar(16) NOT NULL DEFAULT '',
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY message_user (message_id,user_id),
			KEY message_id (message_id)
		) {$charset};";

		$previous = get_option( 'naber_db_version', '' );

		// 1.0.0'da user_one/user_two uzerinde UNIQUE index vardi; gruplar icin kaldirilmali.
		if ( $previous && version_compare( $previous, '1.2.0', '<' ) ) {
			$wpdb->hide_errors();
			$wpdb->query( "ALTER TABLE {$conversations} DROP INDEX pair" );
			$wpdb->show_errors();
		}

		foreach ( $sql as $statement ) {
			dbDelta( $statement );
		}

		if ( $previous && version_compare( $previous, '1.2.0', '<' ) ) {
			self::migrate_to_members();
		}

		update_option( 'naber_db_version', self::DB_VERSION );
	}

	/** Eski birebir sohbetleri yeni uyelik tablosuna tasir. */
	private static function migrate_to_members() {
		global $wpdb;
		$conversations = self::table( 'conversations' );
		$members       = self::table( 'members' );
		$now           = self::now();

		$rows = $wpdb->get_results( "SELECT id, user_one, user_two FROM {$conversations} WHERE type = 'direct'", ARRAY_A );
		foreach ( (array) $rows as $row ) {
			$wpdb->query(
				$wpdb->prepare(
					"UPDATE {$conversations} SET pair_key = %s WHERE id = %d AND pair_key = ''",
					self::direct_key( $row['user_one'], $row['user_two'] ),
					$row['id']
				)
			);
			foreach ( array( $row['user_one'], $row['user_two'] ) as $user_id ) {
				if ( ! $user_id ) {
					continue;
				}
				$wpdb->query(
					$wpdb->prepare(
						"INSERT IGNORE INTO {$members} (conversation_id, user_id, role, joined_at) VALUES (%d, %d, 'member', %s)",
						$row['id'],
						$user_id,
						$now
					)
				);
			}
		}
	}

	public static function direct_key( $user_a, $user_b ) {
		$one = min( (int) $user_a, (int) $user_b );
		$two = max( (int) $user_a, (int) $user_b );
		return 'd:' . $one . ':' . $two;
	}

	public static function maybe_upgrade() {
		if ( get_option( 'naber_db_version' ) !== self::DB_VERSION ) {
			self::install();
		}
	}

	/** Grubu ve ona bagli butun kayitlari siler (yalnizca grup sahibi icin). */
	public static function purge_conversation( $conversation_id ) {
		global $wpdb;
		$conversation_id = (int) $conversation_id;
		if ( $conversation_id <= 0 ) {
			return false;
		}

		$message_ids = $wpdb->get_col(
			$wpdb->prepare( 'SELECT id FROM ' . self::table( 'messages' ) . ' WHERE conversation_id = %d', $conversation_id )
		);
		foreach ( (array) $message_ids as $message_id ) {
			$wpdb->delete( self::table( 'reactions' ), array( 'message_id' => (int) $message_id ), array( '%d' ) );
		}

		$wpdb->delete( self::table( 'messages' ), array( 'conversation_id' => $conversation_id ), array( '%d' ) );
		$wpdb->delete( self::table( 'members' ), array( 'conversation_id' => $conversation_id ), array( '%d' ) );
		$wpdb->delete( self::table( 'join_requests' ), array( 'conversation_id' => $conversation_id ), array( '%d' ) );
		$wpdb->delete( self::table( 'conversations' ), array( 'id' => $conversation_id ), array( '%d' ) );
		return true;
	}

	/** Kullanici silindiginde ilgili satirlari temizler. */
	public static function purge_user( $user_id ) {
		global $wpdb;
		$user_id = (int) $user_id;

		$wpdb->delete( self::table( 'devices' ), array( 'user_id' => $user_id ), array( '%d' ) );
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'pokes' ) . ' WHERE from_id = %d OR to_id = %d', $user_id, $user_id ) );
		$wpdb->delete( self::table( 'reactions' ), array( 'user_id' => $user_id ), array( '%d' ) );
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'blocks' ) . ' WHERE blocker_id = %d OR blocked_id = %d', $user_id, $user_id ) );
		$wpdb->delete( self::table( 'poll_votes' ), array( 'user_id' => $user_id ), array( '%d' ) );
		$wpdb->delete( self::table( 'members' ), array( 'user_id' => $user_id ), array( '%d' ) );
		$wpdb->delete( self::table( 'join_requests' ), array( 'user_id' => $user_id ), array( '%d' ) );
		$wpdb->delete( self::table( 'stickers' ), array( 'uploader_id' => $user_id ), array( '%d' ) );
		$wpdb->delete( self::table( 'call_participants' ), array( 'user_id' => $user_id ), array( '%d' ) );
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'signals' ) . ' WHERE sender_id = %d OR receiver_id = %d', $user_id, $user_id ) );
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'calls' ) . ' WHERE caller_id = %d OR callee_id = %d', $user_id, $user_id ) );

		// Birebir sohbetleri tamamen sil, gruplarda yalnizca mesajlari birak.
		$direct = $wpdb->get_col( $wpdb->prepare( 'SELECT id FROM ' . self::table( 'conversations' ) . " WHERE type = 'direct' AND (user_one = %d OR user_two = %d)", $user_id, $user_id ) );
		foreach ( (array) $direct as $conversation_id ) {
			$wpdb->delete( self::table( 'messages' ), array( 'conversation_id' => (int) $conversation_id ), array( '%d' ) );
			$wpdb->delete( self::table( 'members' ), array( 'conversation_id' => (int) $conversation_id ), array( '%d' ) );
			$wpdb->delete( self::table( 'conversations' ), array( 'id' => (int) $conversation_id ), array( '%d' ) );
		}

		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'messages' ) . ' WHERE sender_id = %d', $user_id ) );
	}

	public static function now() {
		return current_time( 'mysql', true );
	}
}
