<?php
/**
 * Plugin Name: Naber Chat
 * Plugin URI:  https://github.com/Waifuhtr/Naber
 * Description: Naber mesajlasma uygulamasi icin backend: kullanicilar, sohbetler, mesajlar, Backblaze B2 medya, WebRTC signaling, FCM bildirimleri ve admin istatistikleri.
 * Version:     1.7.0
 * Author:      Naber
 * License:     GPL-2.0-or-later
 * Text Domain: naber-chat
 * Requires PHP: 7.4
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

define( 'NABER_CHAT_VERSION', '1.7.0' );
define( 'NABER_CHAT_FILE', __FILE__ );
define( 'NABER_CHAT_DIR', plugin_dir_path( __FILE__ ) );
define( 'NABER_CHAT_URL', plugin_dir_url( __FILE__ ) );
define( 'NABER_CHAT_NS', 'naber/v1' );

require_once NABER_CHAT_DIR . 'includes/class-naber-db.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-settings.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-b2.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-turn.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-auth.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-push.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-media.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-chat-repo.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-calls.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-pokes.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-rest.php';
require_once NABER_CHAT_DIR . 'includes/class-naber-admin-page.php';

register_activation_hook( __FILE__, array( 'Naber_DB', 'install' ) );

add_action( 'plugins_loaded', function () {
	Naber_DB::maybe_upgrade();
	Naber_Auth::init();
} );

add_action( 'rest_api_init', function () {
	( new Naber_REST() )->register_routes();
} );

add_action( 'init', function () {
	Naber_Settings::instance();
} );

if ( is_admin() ) {
	add_action( 'plugins_loaded', function () {
		Naber_Admin_Page::instance();
	} );
}

// Kullanici silindiginde ona ait kayitlari temizle.
add_action( 'deleted_user', array( 'Naber_DB', 'purge_user' ) );
