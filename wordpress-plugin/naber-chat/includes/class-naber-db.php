<?php
/**
 * Tablo semasi ve dogrudan sorgu yardimcilari.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_DB {

	const DB_VERSION = '1.0.0';

	public static function table( $name ) {
		global $wpdb;
		return $wpdb->prefix . 'naber_' . $name;
	}

	public static function install() {
		global $wpdb;
		require_once ABSPATH . 'wp-admin/includes/upgrade.php';
		$charset = $wpdb->get_charset_collate();

		$conversations = self::table( 'conversations' );
		$messages      = self::table( 'messages' );
		$media         = self::table( 'media' );
		$calls         = self::table( 'calls' );
		$signals       = self::table( 'signals' );
		$devices       = self::table( 'devices' );

		$sql = array();

		$sql[] = "CREATE TABLE {$conversations} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			user_one bigint(20) unsigned NOT NULL,
			user_two bigint(20) unsigned NOT NULL,
			last_message_id bigint(20) unsigned NOT NULL DEFAULT 0,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			updated_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			UNIQUE KEY pair (user_one,user_two),
			KEY user_one (user_one),
			KEY user_two (user_two),
			KEY updated_at (updated_at)
		) {$charset};";

		$sql[] = "CREATE TABLE {$messages} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			conversation_id bigint(20) unsigned NOT NULL,
			sender_id bigint(20) unsigned NOT NULL,
			receiver_id bigint(20) unsigned NOT NULL,
			message_type varchar(20) NOT NULL DEFAULT 'text',
			body longtext NULL,
			media_id bigint(20) unsigned NOT NULL DEFAULT 0,
			client_id varchar(64) NOT NULL DEFAULT '',
			is_read tinyint(1) NOT NULL DEFAULT 0,
			read_at datetime NULL,
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			KEY conversation_id (conversation_id,id),
			KEY receiver_unread (receiver_id,is_read),
			KEY client_id (client_id)
		) {$charset};";

		$sql[] = "CREATE TABLE {$media} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			owner_id bigint(20) unsigned NOT NULL,
			bucket varchar(191) NOT NULL DEFAULT '',
			file_name varchar(500) NOT NULL DEFAULT '',
			file_id varchar(191) NOT NULL DEFAULT '',
			mime varchar(100) NOT NULL DEFAULT '',
			size bigint(20) unsigned NOT NULL DEFAULT 0,
			width int(11) NOT NULL DEFAULT 0,
			height int(11) NOT NULL DEFAULT 0,
			status varchar(20) NOT NULL DEFAULT 'pending',
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			PRIMARY KEY  (id),
			KEY owner_id (owner_id),
			KEY status (status)
		) {$charset};";

		$sql[] = "CREATE TABLE {$calls} (
			id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
			conversation_id bigint(20) unsigned NOT NULL DEFAULT 0,
			caller_id bigint(20) unsigned NOT NULL,
			callee_id bigint(20) unsigned NOT NULL,
			status varchar(20) NOT NULL DEFAULT 'ringing',
			created_at datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
			answered_at datetime NULL,
			ended_at datetime NULL,
			duration int(11) NOT NULL DEFAULT 0,
			end_reason varchar(40) NOT NULL DEFAULT '',
			PRIMARY KEY  (id),
			KEY caller_id (caller_id),
			KEY callee_id (callee_id),
			KEY status (status)
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

		foreach ( $sql as $statement ) {
			dbDelta( $statement );
		}

		update_option( 'naber_db_version', self::DB_VERSION );
	}

	public static function maybe_upgrade() {
		if ( get_option( 'naber_db_version' ) !== self::DB_VERSION ) {
			self::install();
		}
	}

	/** Kullanici silindiginde ilgili satirlari temizler. */
	public static function purge_user( $user_id ) {
		global $wpdb;
		$user_id = (int) $user_id;
		$wpdb->delete( self::table( 'devices' ), array( 'user_id' => $user_id ), array( '%d' ) );
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'messages' ) . ' WHERE sender_id = %d OR receiver_id = %d', $user_id, $user_id ) );
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'conversations' ) . ' WHERE user_one = %d OR user_two = %d', $user_id, $user_id ) );
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'calls' ) . ' WHERE caller_id = %d OR callee_id = %d', $user_id, $user_id ) );
		$wpdb->query( $wpdb->prepare( 'DELETE FROM ' . self::table( 'signals' ) . ' WHERE sender_id = %d OR receiver_id = %d', $user_id, $user_id ) );
	}

	public static function now() {
		return current_time( 'mysql', true );
	}
}
