<?php
/**
 * REST API: /wp-json/naber/v1/...
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_REST {

	/** On izleme base64 metninin ust siniri (yaklasik 6 KB ikili veri). */
	const MAX_PREVIEW_CHARS = 8000;
	/** Bir mesaj gonderimden sonra en fazla bu kadar saniye duzenlenebilir. */
	const EDIT_WINDOW_SECONDS = 900;

	/**
	 * Kaybolan mesajlar icin izin verilen sureler (saniye).
	 *
	 * Serbest sayi kabul edilmez: kullanici arayuzunde de bu secenekler
	 * var, boylece "1 saniye" gibi veriyi aninda yok eden degerler ya da
	 * ay suren bekleyisler olusmaz.
	 */
	const DISAPPEAR_OPTIONS = array( 0, 3600, 86400, 604800, 2592000 );

	public function register_routes() {
		$ns = NABER_CHAT_NS;

		$public = array( $this, 'open' );
		$user   = array( $this, 'require_user' );
		$admin  = array( $this, 'require_admin' );

		// --- Hesap ---
		$this->route( $ns, '/register', 'POST', 'register_user', $public );
		$this->route( $ns, '/login', 'POST', 'login', $public );
		$this->route( $ns, '/logout', 'POST', 'logout', $user );
		$this->route( $ns, '/me', 'GET', 'get_me', $user );
		$this->route( $ns, '/me', 'POST', 'update_me', $user );
		$this->route( $ns, '/me/privacy', 'POST', 'update_privacy', $user );

		// --- Kisiler ---
		$this->route( $ns, '/users', 'GET', 'list_users', $user );
		$this->route( $ns, '/users/lookup', 'GET', 'lookup_user', $user );
		$this->route( $ns, '/users/(?P<id>\d+)', 'GET', 'get_user_profile', $user );
		$this->route( $ns, '/users/(?P<id>\d+)/poke', 'POST', 'poke_user', $user );
		$this->route( $ns, '/users/(?P<id>\d+)/block', 'POST', 'block_user', $user );
		$this->route( $ns, '/contacts', 'GET', 'list_contacts', $user );
		$this->route( $ns, '/contacts', 'POST', 'add_contact', $user );
		$this->route( $ns, '/contacts/(?P<id>\d+)', 'DELETE', 'remove_contact', $user );

		// --- Sohbetler ---
		$this->route( $ns, '/chats', 'GET', 'list_chats', $user );
		$this->route( $ns, '/chats', 'POST', 'open_chat', $user );
		$this->route( $ns, '/groups', 'POST', 'create_group', $user );
		$this->route( $ns, '/groups/join', 'POST', 'join_group_by_code', $user );
		$this->route( $ns, '/groups/(?P<id>\d+)/invite-code/regenerate', 'POST', 'regenerate_invite_code', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)', 'GET', 'chat_info', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)', 'POST', 'update_chat', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/members', 'POST', 'add_members', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/members/(?P<user>\d+)', 'DELETE', 'remove_member', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/members/(?P<user>\d+)/role', 'POST', 'set_member_role', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/members/(?P<user>\d+)/mute', 'POST', 'mute_member', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/leave', 'POST', 'leave_chat', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/notifications', 'POST', 'toggle_notifications', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/pin', 'POST', 'toggle_pin', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/disappearing', 'POST', 'set_disappearing', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/messages', 'GET', 'list_messages', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/read', 'POST', 'mark_read', $user );
		$this->route( $ns, '/chats/(?P<id>\d+)/typing', 'POST', 'typing', $user );

		// --- Mesajlar ---
		$this->route( $ns, '/messages', 'POST', 'send_message', $user );
		$this->route( $ns, '/messages/(?P<id>\d+)', 'DELETE', 'delete_message', $user );
		$this->route( $ns, '/messages/(?P<id>\d+)', 'POST', 'edit_message', $user );
		$this->route( $ns, '/messages/(?P<id>\d+)/info', 'GET', 'message_info', $user );
		$this->route( $ns, '/messages/(?P<id>\d+)/react', 'POST', 'react_to_message', $user );
		$this->route( $ns, '/messages/(?P<id>\d+)/forward', 'POST', 'forward_message', $user );
		$this->route( $ns, '/polls/(?P<id>\d+)/vote', 'POST', 'vote_poll', $user );

		// --- Medya ---
		$this->route( $ns, '/media/find', 'POST', 'media_find', $user );
		$this->route( $ns, '/media/upload-url', 'POST', 'media_upload_url', $user );
		$this->route( $ns, '/media/complete', 'POST', 'media_complete', $user );
		$this->route( $ns, '/media/upload', 'POST', 'media_proxy_upload', $user );
		$this->route( $ns, '/media/(?P<id>\d+)/url', 'GET', 'media_url', $user );

		// --- Cihaz & olaylar ---
		$this->route( $ns, '/devices', 'POST', 'register_device', $user );
		$this->route( $ns, '/devices', 'DELETE', 'delete_device', $user );
		$this->route( $ns, '/events', 'GET', 'events', $user );
		$this->route( $ns, '/presence', 'POST', 'set_presence', $user );
		$this->route( $ns, '/ice-servers', 'GET', 'ice_servers', $user );

		// --- Aramalar ---
		$this->route( $ns, '/calls', 'GET', 'call_history', $user );
		$this->route( $ns, '/calls/start', 'POST', 'call_start', $user );
		$this->route( $ns, '/calls/(?P<id>\d+)', 'GET', 'call_info', $user );
		$this->route( $ns, '/calls/(?P<id>\d+)/(?P<action>accept|reject|end|join|leave)', 'POST', 'call_action', $user );
		$this->route( $ns, '/calls/(?P<id>\d+)/signal', 'POST', 'call_signal', $user );
		$this->route( $ns, '/calls/(?P<id>\d+)/signals', 'GET', 'call_signals', $user );
		$this->route( $ns, '/calls/(?P<id>\d+)/participants/(?P<user>\d+)/mute', 'POST', 'call_mute_participant', $user );
		$this->route( $ns, '/calls/(?P<id>\d+)/participants/(?P<user>\d+)/kick', 'POST', 'call_kick_participant', $user );

		// --- Yonetim (her biri sunucu tarafinda rol kontrolunden gecer) ---
		$this->route( $ns, '/admin/stats', 'GET', 'admin_stats', $admin );
		$this->route( $ns, '/admin/users', 'GET', 'admin_users', $admin );
		$this->route( $ns, '/admin/users/(?P<id>\d+)', 'POST', 'admin_update_user', $admin );
		$this->route( $ns, '/admin/users/(?P<id>\d+)', 'DELETE', 'admin_delete_user', $admin );
		$this->route( $ns, '/admin/chats', 'GET', 'admin_chats', $admin );
		$this->route( $ns, '/admin/chats/(?P<id>\d+)', 'DELETE', 'admin_delete_chat', $admin );
		$this->route( $ns, '/admin/storage/test', 'POST', 'admin_storage_test', $admin );
		$this->route( $ns, '/admin/push/test', 'POST', 'admin_push_test', $admin );
		$this->route( $ns, '/admin/turn/test', 'POST', 'admin_turn_test', $admin );
		$this->route( $ns, '/admin/settings', 'GET', 'admin_settings', $admin );
	}

	private function route( $ns, $path, $method, $callback, $permission ) {
		register_rest_route( $ns, $path, array(
			'methods'             => $method,
			'callback'            => array( $this, $callback ),
			'permission_callback' => $permission,
		) );
	}

	public function open() {
		return true;
	}

	// ------------------------------------------------------------------
	// Yetki
	// ------------------------------------------------------------------

	public function require_user() {
		$user_id = get_current_user_id();
		if ( ! $user_id ) {
			return new WP_Error( 'naber_unauthorized', 'Oturum acmaniz gerekiyor.', array( 'status' => 401 ) );
		}
		if ( Naber_Auth::is_banned( $user_id ) ) {
			$reason = Naber_Auth::ban_reason( $user_id );
			return new WP_Error( 'naber_banned', 'Hesabiniz yasaklandi.' . ( $reason ? ' Sebep: ' . $reason : '' ), array( 'status' => 403 ) );
		}
		if ( Naber_Auth::is_disabled( $user_id ) ) {
			return new WP_Error( 'naber_disabled', 'Hesabiniz devre disi birakilmis.', array( 'status' => 403 ) );
		}
		Naber_Auth::touch_presence( $user_id );
		return true;
	}

	public function require_admin() {
		$allowed = $this->require_user();
		if ( is_wp_error( $allowed ) ) {
			return $allowed;
		}
		if ( ! Naber_Auth::is_admin_user( get_current_user_id() ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu islem icin yonetici yetkisi gerekli.', array( 'status' => 403 ) );
		}
		return true;
	}

	/** Sohbete erisim kontrolu. */
	private function authorized_conversation( $conversation_id ) {
		$conversation = Naber_Chat_Repo::get_conversation( $conversation_id );
		if ( ! $conversation ) {
			return new WP_Error( 'naber_chat_not_found', 'Sohbet bulunamadi.', array( 'status' => 404 ) );
		}
		if ( ! Naber_Chat_Repo::member( (int) $conversation['id'], get_current_user_id() ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu sohbete erisim yetkiniz yok.', array( 'status' => 403 ) );
		}
		return $conversation;
	}

	// ------------------------------------------------------------------
	// Hesap
	// ------------------------------------------------------------------

	public function register_user( WP_REST_Request $request ) {
		if ( ! Naber_Settings::get( 'allow_registration' ) ) {
			return new WP_Error( 'naber_registration_closed', 'Yeni kayitlar kapali.', array( 'status' => 403 ) );
		}

		$username = sanitize_user( (string) $request->get_param( 'username' ), true );
		$password = (string) $request->get_param( 'password' );
		$display  = sanitize_text_field( (string) $request->get_param( 'display_name' ) );

		if ( strlen( $username ) < 3 ) {
			return new WP_Error( 'naber_bad_username', 'Kullanici adi en az 3 karakter olmali.', array( 'status' => 400 ) );
		}
		if ( strlen( $password ) < 6 ) {
			return new WP_Error( 'naber_bad_password', 'Sifre en az 6 karakter olmali.', array( 'status' => 400 ) );
		}
		if ( username_exists( $username ) ) {
			return new WP_Error( 'naber_username_taken', 'Bu kullanici adi zaten alinmis.', array( 'status' => 409 ) );
		}

		// Kullaniciya uygulamaya ozel bir Naber adresi uretilir: kullaniciadi@naber.com
		$email = Naber_Auth::build_naber_email( $username );

		$user_id = wp_insert_user( array(
			'user_login'   => $username,
			'user_pass'    => $password,
			'user_email'   => $email,
			'display_name' => '' !== $display ? $display : $username,
			'role'         => 'subscriber',
		) );

		if ( is_wp_error( $user_id ) ) {
			return $user_id;
		}

		return $this->session_response( $user_id, $request );
	}

	public function login( WP_REST_Request $request ) {
		$identifier = trim( (string) $request->get_param( 'username' ) );
		$password   = (string) $request->get_param( 'password' );

		// Kullanici adi ya da Naber adresi ile giris.
		if ( is_email( $identifier ) ) {
			$found = get_user_by( 'email', $identifier );
			if ( $found ) {
				$identifier = $found->user_login;
			}
		}

		$user = wp_authenticate( $identifier, $password );
		if ( is_wp_error( $user ) ) {
			return new WP_Error( 'naber_bad_credentials', 'Kullanici adi veya sifre hatali.', array( 'status' => 401 ) );
		}
		if ( Naber_Auth::is_banned( $user->ID ) ) {
			$reason = Naber_Auth::ban_reason( $user->ID );
			return new WP_Error( 'naber_banned', 'Hesabiniz yasaklandi.' . ( $reason ? ' Sebep: ' . $reason : '' ), array( 'status' => 403 ) );
		}
		if ( Naber_Auth::is_disabled( $user->ID ) ) {
			return new WP_Error( 'naber_disabled', 'Hesabiniz devre disi birakilmis.', array( 'status' => 403 ) );
		}

		return $this->session_response( $user->ID, $request );
	}

	private function session_response( $user_id, WP_REST_Request $request ) {
		$token = Naber_Auth::issue_token( $user_id, (string) $request->get_param( 'device' ) );
		Naber_Auth::touch_presence( $user_id );
		return rest_ensure_response( array(
			'token' => $token,
			'user'  => Naber_Auth::user_payload( $user_id, true ),
		) );
	}

	public function logout( WP_REST_Request $request ) {
		$token = Naber_Auth::bearer_token();
		if ( $token ) {
			Naber_Auth::revoke_token( get_current_user_id(), $token );
		}
		$device = (string) $request->get_param( 'device_token' );
		if ( '' !== $device ) {
			Naber_Push::unregister_device( $device );
		}
		return rest_ensure_response( array( 'ok' => true ) );
	}

	public function get_me() {
		return rest_ensure_response( array(
			'user'         => Naber_Auth::user_payload( get_current_user_id(), true ),
			'unread_total' => Naber_Chat_Repo::unread_total( get_current_user_id() ),
		) );
	}

	/**
	 * Gizlilik ayarlari: son gorulme ve okundu bilgisini gizleme.
	 *
	 * Ikisi de simetriktir: gizleyen kisi baskalarininkini de goremez.
	 */
	public function update_privacy( WP_REST_Request $request ) {
		$user_id = get_current_user_id();
		Naber_Auth::set_privacy(
			$user_id,
			rest_sanitize_boolean( $request->get_param( 'hide_last_seen' ) ),
			rest_sanitize_boolean( $request->get_param( 'hide_read' ) )
		);
		return rest_ensure_response( array( 'user' => Naber_Auth::user_payload( $user_id, true ) ) );
	}

	public function update_me( WP_REST_Request $request ) {
		$user_id = get_current_user_id();

		$display = $request->get_param( 'display_name' );
		if ( null !== $display ) {
			$display = sanitize_text_field( (string) $display );
			if ( '' !== $display ) {
				wp_update_user( array( 'ID' => $user_id, 'display_name' => $display ) );
			}
		}

		$about = $request->get_param( 'about' );
		if ( null !== $about ) {
			update_user_meta( $user_id, 'naber_about', sanitize_text_field( (string) $about ) );
		}

		$media_id = (int) $request->get_param( 'avatar_media_id' );
		if ( $media_id > 0 ) {
			$media = Naber_Media::get( $media_id );
			if ( $media && (int) $media['owner_id'] === $user_id ) {
				// Yalnizca medya kimligi saklanir; adres her istekte tazelenir.
				update_user_meta( $user_id, Naber_Auth::META_AVATAR, $media_id );
				delete_user_meta( $user_id, 'naber_avatar_url' );
			}
		}

		Naber_Auth::flush_payload_cache( $user_id );
		return rest_ensure_response( array( 'user' => Naber_Auth::user_payload( $user_id, true ) ) );
	}

	// ------------------------------------------------------------------
	// Kisiler
	// ------------------------------------------------------------------

	public function list_users( WP_REST_Request $request ) {
		$search = sanitize_text_field( (string) $request->get_param( 'search' ) );
		$args   = array(
			'number'  => 100,
			'orderby' => 'display_name',
			'order'   => 'ASC',
			'exclude' => array( get_current_user_id() ),
		);
		if ( '' !== $search ) {
			$args['search']         = '*' . $search . '*';
			$args['search_columns'] = array( 'user_login', 'display_name', 'user_nicename', 'user_email' );
		}

		$contacts = Naber_Auth::contacts( get_current_user_id() );
		$users    = array();
		foreach ( get_users( $args ) as $user ) {
			if ( Naber_Auth::is_disabled( $user->ID ) || Naber_Auth::is_banned( $user->ID ) ) {
				continue;
			}
			$payload                = Naber_Auth::user_payload( $user );
			$payload['is_contact']  = in_array( (int) $user->ID, $contacts, true );
			$users[]                = $payload;
		}
		return rest_ensure_response( array( 'users' => $users ) );
	}

	/** Naber adresi (veya kullanici adi) ile tek kullanici bulur. */
	public function lookup_user( WP_REST_Request $request ) {
		$query = trim( (string) $request->get_param( 'q' ) );
		if ( '' === $query ) {
			return new WP_Error( 'naber_query_required', 'Aranacak adres veya kullanici adi gerekli.', array( 'status' => 400 ) );
		}

		$user = is_email( $query ) ? get_user_by( 'email', $query ) : get_user_by( 'login', $query );
		if ( ! $user ) {
			// Adres yazilirken alan adi unutulmus olabilir.
			$user = get_user_by( 'email', $query . '@' . Naber_Auth::email_domain() );
		}
		if ( ! $user || (int) $user->ID === get_current_user_id() ) {
			return new WP_Error( 'naber_user_not_found', 'Bu adrese sahip bir kullanici bulunamadi.', array( 'status' => 404 ) );
		}
		if ( Naber_Auth::is_banned( $user->ID ) || Naber_Auth::is_disabled( $user->ID ) ) {
			return new WP_Error( 'naber_user_not_found', 'Bu kullanici su anda erisilebilir degil.', array( 'status' => 404 ) );
		}

		$payload               = Naber_Auth::user_payload( $user );
		$payload['is_contact'] = in_array( (int) $user->ID, Naber_Auth::contacts( get_current_user_id() ), true );
		return rest_ensure_response( array( 'user' => $payload ) );
	}

	/** Kimlige gore tek bir kullanicinin profil bilgisi (durtme ekrani icin). */
	public function get_user_profile( WP_REST_Request $request ) {
		$target_id = (int) $request['id'];
		if ( $target_id === get_current_user_id() ) {
			return new WP_Error( 'naber_self_profile', 'Kendi profilinizi buradan goremezsiniz.', array( 'status' => 400 ) );
		}

		$target = get_userdata( $target_id );
		if ( ! $target || Naber_Auth::is_banned( $target_id ) || Naber_Auth::is_disabled( $target_id ) ) {
			return new WP_Error( 'naber_user_not_found', 'Kullanici bulunamadi.', array( 'status' => 404 ) );
		}

		$payload               = Naber_Auth::user_payload( $target );
		$payload['is_contact'] = in_array( $target_id, Naber_Auth::contacts( get_current_user_id() ), true );
		$payload['poke_cooldown'] = Naber_Pokes::cooldown_remaining( get_current_user_id(), $target_id );
		// "blocked": bu kisiyi ben engelledim mi. Karsi tarafin beni
		// engelleyip engellemedigi bilgisi verilmez; engelleme sessizdir.
		$payload['blocked'] = Naber_Blocks::has_blocked( get_current_user_id(), $target_id );
		return rest_ensure_response( array( 'user' => $payload ) );
	}

	/** Bir kullaniciyi engeller veya engeli kaldirir. */
	public function block_user( WP_REST_Request $request ) {
		$target_id = (int) $request['id'];
		$blocked   = rest_sanitize_boolean( $request->get_param( 'blocked' ) );
		$result    = Naber_Blocks::set( get_current_user_id(), $target_id, $blocked );
		if ( is_wp_error( $result ) ) {
			return $result;
		}
		return rest_ensure_response( array( 'blocked' => (bool) $blocked ) );
	}

	/** Bir kullaniciyi durtme; sohbet acmaz, yalnizca bildirim gonderir. */
	public function poke_user( WP_REST_Request $request ) {
		$target_id = (int) $request['id'];
		$target    = get_userdata( $target_id );
		if ( ! $target || Naber_Auth::is_banned( $target_id ) || Naber_Auth::is_disabled( $target_id ) ) {
			return new WP_Error( 'naber_user_not_found', 'Kullanici bulunamadi.', array( 'status' => 404 ) );
		}

		$user_id = get_current_user_id();
		if ( Naber_Blocks::between( $user_id, $target_id ) ) {
			return Naber_Blocks::blocked_error( 'poke' );
		}

		$result = Naber_Pokes::poke( $user_id, $target_id );
		if ( is_wp_error( $result ) ) {
			return $result;
		}

		$sender = Naber_Auth::user_payload( $user_id );
		Naber_Push::send_to_user(
			$target_id,
			array( 'title' => 'Naber', 'body' => $sender['display_name'] . ' seni durttu!' ),
			array(
				'type'        => 'poke',
				'from_id'     => $user_id,
				'from_name'   => $sender['display_name'],
			)
		);

		return rest_ensure_response( $result );
	}

	public function list_contacts() {
		$user_id = get_current_user_id();
		$out     = array();
		foreach ( Naber_Auth::contacts( $user_id ) as $contact_id ) {
			$payload = Naber_Auth::user_payload( $contact_id );
			if ( $payload && ! Naber_Auth::is_banned( $contact_id ) ) {
				$payload['is_contact'] = true;
				$out[]                 = $payload;
			}
		}
		return rest_ensure_response( array( 'users' => $out ) );
	}

	public function add_contact( WP_REST_Request $request ) {
		$user_id = get_current_user_id();
		$target  = (int) $request->get_param( 'user_id' );

		if ( ! $target ) {
			$email  = trim( (string) $request->get_param( 'email' ) );
			$found  = is_email( $email ) ? get_user_by( 'email', $email ) : get_user_by( 'email', $email . '@' . Naber_Auth::email_domain() );
			$target = $found ? (int) $found->ID : 0;
		}

		if ( $target <= 0 || $target === $user_id || ! get_userdata( $target ) ) {
			return new WP_Error( 'naber_user_not_found', 'Kullanici bulunamadi.', array( 'status' => 404 ) );
		}
		if ( Naber_Auth::is_banned( $target ) ) {
			return new WP_Error( 'naber_user_not_found', 'Bu kullanici su anda erisilebilir degil.', array( 'status' => 404 ) );
		}

		Naber_Auth::add_contact( $user_id, $target );
		$conversation_id = Naber_Chat_Repo::ensure_conversation( $user_id, $target );

		$payload               = Naber_Auth::user_payload( $target );
		$payload['is_contact'] = true;

		return rest_ensure_response( array(
			'user'            => $payload,
			'conversation_id' => is_wp_error( $conversation_id ) ? 0 : $conversation_id,
		) );
	}

	public function remove_contact( WP_REST_Request $request ) {
		Naber_Auth::remove_contact( get_current_user_id(), (int) $request['id'] );
		return rest_ensure_response( array( 'ok' => true ) );
	}

	// ------------------------------------------------------------------
	// Sohbetler
	// ------------------------------------------------------------------

	/**
	 * Sohbet listesi.
	 *
	 * "since" verilirse yalnizca o zamandan sonra degisen sohbetler doner
	 * (delta); istemci elindeki listeyi bunlarla gunceller, hepsini
	 * yeniden indirmez.
	 *
	 * Donen "sync_time" bir sonraki istekte "since" olarak kullanilir.
	 * Sunucu saati baz alinir: telefon saati yanlissa istemcinin kendi
	 * zamanini kullanmasi degisiklikleri atlamasina yol acardi.
	 */
	public function list_chats( WP_REST_Request $request ) {
		$user_id = get_current_user_id();
		$since   = max( 0, (int) $request->get_param( 'since' ) );

		// Iki istek arasinda ayni saniye icinde olan degisiklikler
		// kacmasin diye bir saniye geriden baslanir.
		$sync_time = time();

		return rest_ensure_response( array(
			'chats'        => Naber_Chat_Repo::list_for_user( $user_id, $since > 0 ? $since - 1 : 0 ),
			'unread_total' => Naber_Chat_Repo::unread_total( $user_id ),
			'partial'      => $since > 0,
			'sync_time'    => $sync_time,
		) );
	}

	public function open_chat( WP_REST_Request $request ) {
		$peer_id = (int) $request->get_param( 'user_id' );
		if ( $peer_id <= 0 || ! get_userdata( $peer_id ) ) {
			return new WP_Error( 'naber_user_not_found', 'Kullanici bulunamadi.', array( 'status' => 404 ) );
		}
		$conversation_id = Naber_Chat_Repo::ensure_conversation( get_current_user_id(), $peer_id );
		if ( is_wp_error( $conversation_id ) ) {
			return $conversation_id;
		}
		return rest_ensure_response( array(
			'id'   => $conversation_id,
			'chat' => Naber_Chat_Repo::conversation_payload( $conversation_id, get_current_user_id() ),
		) );
	}

	public function create_group( WP_REST_Request $request ) {
		$user_id = get_current_user_id();
		$members = (array) $request->get_param( 'members' );
		$title   = (string) $request->get_param( 'title' );

		$conversation_id = Naber_Chat_Repo::create_group(
			$user_id,
			$title,
			$members,
			(int) $request->get_param( 'avatar_media_id' ),
			(string) $request->get_param( 'about' )
		);
		if ( is_wp_error( $conversation_id ) ) {
			return $conversation_id;
		}

		$me = Naber_Auth::user_payload( $user_id );
		foreach ( Naber_Chat_Repo::member_ids( $conversation_id ) as $member_id ) {
			if ( $member_id !== $user_id ) {
				Naber_Push::send_to_user(
					$member_id,
					array( 'title' => $title, 'body' => $me['display_name'] . ' sizi gruba ekledi' ),
					array( 'type' => 'group_added', 'conversation_id' => $conversation_id )
				);
			}
		}

		return rest_ensure_response( array( 'chat' => Naber_Chat_Repo::conversation_payload( $conversation_id, $user_id, true ) ) );
	}

	/**
	 * Davet kodu ile gruba katilir.
	 * Grup davet linkinden farkli olarak kod disaridan tiklanabilir bir URL
	 * degildir; yalnizca uygulamaya zaten giris yapmis, kodu bilen kisi
	 * "Kod ile grup bul" ekranindan katilabilir.
	 */
	public function join_group_by_code( WP_REST_Request $request ) {
		$user_id = get_current_user_id();
		$code    = (string) $request->get_param( 'code' );

		$conversation_id = Naber_Chat_Repo::join_by_code( $code, $user_id );
		if ( is_wp_error( $conversation_id ) ) {
			return $conversation_id;
		}

		Naber_Groups::system_message(
			$conversation_id,
			Naber_Groups::display_name( $user_id ) . ' davet koduyla gruba katildi.'
		);

		return rest_ensure_response( array( 'chat' => Naber_Chat_Repo::conversation_payload( $conversation_id, $user_id, true ) ) );
	}

	/** Grup yoneticisi eski kodu gecersiz kilip yenisini uretir. */
	public function regenerate_invite_code( WP_REST_Request $request ) {
		$conversation_id = (int) $request['id'];
		$conversation    = Naber_Chat_Repo::get_conversation( $conversation_id );
		if ( ! $conversation || 'group' !== $conversation['type'] ) {
			return new WP_Error( 'naber_group_not_found', 'Grup bulunamadi.', array( 'status' => 404 ) );
		}

		$user_id = get_current_user_id();
		if ( ! Naber_Chat_Repo::is_group_admin( $conversation_id, $user_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Yalnizca yoneticiler kodu yenileyebilir.', array( 'status' => 403 ) );
		}

		$code = Naber_Chat_Repo::assign_invite_code( $conversation_id );
		return rest_ensure_response( array( 'invite_code' => $code ) );
	}

	public function chat_info( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		return rest_ensure_response( array(
			'chat' => Naber_Chat_Repo::conversation_payload( $conversation, get_current_user_id(), true ),
		) );
	}

	public function update_chat( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		if ( 'group' !== $conversation['type'] ) {
			return new WP_Error( 'naber_not_group', 'Yalnizca gruplar duzenlenebilir.', array( 'status' => 400 ) );
		}
		if ( ! Naber_Chat_Repo::has_perm( (int) $conversation['id'], get_current_user_id(), 'edit_group' ) ) {
			return new WP_Error( 'naber_forbidden', 'Grubu duzenleme yetkiniz yok.', array( 'status' => 403 ) );
		}

		$fields = array();
		foreach ( array( 'title', 'about' ) as $key ) {
			$value = $request->get_param( $key );
			if ( null !== $value ) {
				$fields[ $key ] = (string) $value;
			}
		}
		$avatar = (int) $request->get_param( 'avatar_media_id' );
		if ( $avatar > 0 ) {
			$media = Naber_Media::get( $avatar );
			if ( $media && (int) $media['owner_id'] === get_current_user_id() ) {
				$fields['avatar_media_id'] = $avatar;
			}
		}

		Naber_Chat_Repo::update_group( (int) $conversation['id'], $fields );
		return rest_ensure_response( array(
			'chat' => Naber_Chat_Repo::conversation_payload( (int) $conversation['id'], get_current_user_id(), true ),
		) );
	}

	public function add_members( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		if ( 'group' !== $conversation['type'] ) {
			return new WP_Error( 'naber_not_group', 'Yalnizca gruba uye eklenebilir.', array( 'status' => 400 ) );
		}
		if ( ! Naber_Chat_Repo::has_perm( (int) $conversation['id'], get_current_user_id(), 'add_member' ) ) {
			return new WP_Error( 'naber_forbidden', 'Uye eklemek icin yetkiniz yok.', array( 'status' => 403 ) );
		}

		foreach ( (array) $request->get_param( 'members' ) as $member_id ) {
			$member_id = (int) $member_id;
			if ( $member_id > 0 && get_userdata( $member_id ) && ! Naber_Auth::is_banned( $member_id ) ) {
				$already = (bool) Naber_Chat_Repo::member( (int) $conversation['id'], $member_id );
				Naber_Chat_Repo::add_member( (int) $conversation['id'], $member_id, 'member' );
				if ( ! $already ) {
					Naber_Groups::system_message(
						(int) $conversation['id'],
						Naber_Groups::display_name( $member_id ) . ' gruba eklendi.'
					);
				}
			}
		}

		return rest_ensure_response( array(
			'chat' => Naber_Chat_Repo::conversation_payload( (int) $conversation['id'], get_current_user_id(), true ),
		) );
	}

	public function remove_member( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		$conversation_id = (int) $conversation['id'];
		$target          = (int) $request['user'];
		$actor           = get_current_user_id();

		// Saka savunmasi: grup sahibini atmaya kalkisan kisi kendini attirir.
		// Kaybettigi rol ve yetkiler saklanir, 10 saniye sonra geri alinir.
		if ( 'group' === $conversation['type']
			&& 'owner' === Naber_Chat_Repo::role_of( $conversation_id, $target )
			&& $actor !== $target ) {
			$prank = Naber_Groups::prank_kick( $conversation_id, $actor );
			if ( $prank ) {
				return rest_ensure_response( array(
					'prank' => array(
						'message'         => $prank['message'],
						'restore_seconds' => $prank['restore_seconds'],
						// Saldirgan bir anligina "kazandim" sansin diye
						// sahibin etiketi once yok gosterilir.
						'victim_id'       => $target,
					),
				) );
			}
		}

		$check = $this->can_moderate( $conversation, $target, 'remove_member' );
		if ( is_wp_error( $check ) ) {
			return $check;
		}

		Naber_Chat_Repo::remove_member( $conversation_id, $target );
		Naber_Groups::system_message(
			$conversation_id,
			Naber_Groups::display_name( $target ) . ' gruptan cikarildi.'
		);
		return rest_ensure_response( array(
			'chat' => Naber_Chat_Repo::conversation_payload( $conversation_id, $actor, true ),
		) );
	}

	public function set_member_role( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		if ( 'owner' !== Naber_Chat_Repo::role_of( (int) $conversation['id'], get_current_user_id() ) ) {
			return new WP_Error( 'naber_forbidden', 'Yonetici atamayi yalnizca grup sahibi yapabilir.', array( 'status' => 403 ) );
		}

		$role = sanitize_key( (string) $request->get_param( 'role' ) );
		if ( ! in_array( $role, array( 'owner', 'admin', 'member' ), true ) ) {
			return new WP_Error( 'naber_bad_role', 'Gecersiz rol.', array( 'status' => 400 ) );
		}

		$conversation_id = (int) $conversation['id'];
		$target          = (int) $request['user'];
		if ( ! Naber_Chat_Repo::member( $conversation_id, $target ) ) {
			return new WP_Error( 'naber_not_member', 'Kullanici bu grubun uyesi degil.', array( 'status' => 404 ) );
		}

		// Sahiplik devri: eski sahip yonetici olur, grup sahibi degisir.
		if ( 'owner' === $role ) {
			Naber_Groups::transfer_ownership( $conversation_id, $target, get_current_user_id() );
			return rest_ensure_response( array(
				'chat' => Naber_Chat_Repo::conversation_payload( $conversation_id, get_current_user_id(), true ),
			) );
		}

		$previous = Naber_Chat_Repo::role_of( $conversation_id, $target );
		Naber_Chat_Repo::set_role( $conversation_id, $target, $role );

		// Ayrintili yetkiler: "yetkili uye" tanimlamak icin. Yonetici
		// zaten hepsine sahip oldugu icin liste yalnizca duz uyede anlamli.
		$perms = $request->get_param( 'perms' );
		if ( null !== $perms ) {
			Naber_Chat_Repo::set_perms( $conversation_id, $target, $perms );
		} elseif ( 'member' === $role && 'member' !== $previous ) {
			// Yoneticilikten dusurulen uyede eski yetki artigi kalmasin.
			Naber_Chat_Repo::set_perms( $conversation_id, $target, array() );
		}

		if ( $previous !== $role ) {
			Naber_Groups::system_message(
				$conversation_id,
				'admin' === $role
					? Naber_Groups::display_name( $target ) . ' artik yonetici.'
					: Naber_Groups::display_name( $target ) . ' yoneticilikten alindi.'
			);
		}

		return rest_ensure_response( array(
			'chat' => Naber_Chat_Repo::conversation_payload( $conversation_id, get_current_user_id(), true ),
		) );
	}

	/** Grup yoneticisi bir uyeyi sohbette susturabilir / susturmayi kaldirabilir. */
	public function mute_member( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		$target = (int) $request['user'];
		$check  = $this->can_moderate( $conversation, $target );
		if ( is_wp_error( $check ) ) {
			return $check;
		}

		$muted = rest_sanitize_boolean( $request->get_param( 'muted' ) );
		Naber_Chat_Repo::set_chat_muted( (int) $conversation['id'], $target, $muted );

		return rest_ensure_response( array(
			'chat' => Naber_Chat_Repo::conversation_payload( (int) $conversation['id'], get_current_user_id(), true ),
		) );
	}

	/**
	 * Yonetici mudahalesi icin ortak kontrol.
	 *
	 * $perm verilirse yonetici olmayan ama o yetkisi acilmis uyeler de
	 * islemi yapabilir; yalnizca duz uyelere mudahale edebilirler.
	 */
	private function can_moderate( $conversation, $target_id, $perm = '' ) {
		if ( 'group' !== $conversation['type'] ) {
			return new WP_Error( 'naber_not_group', 'Bu islem yalnizca gruplarda yapilabilir.', array( 'status' => 400 ) );
		}
		$conversation_id = (int) $conversation['id'];
		$actor_id        = get_current_user_id();
		$actor_role      = Naber_Chat_Repo::role_of( $conversation_id, $actor_id );
		$target_role     = Naber_Chat_Repo::role_of( $conversation_id, $target_id );

		if ( ! $target_role ) {
			return new WP_Error( 'naber_not_member', 'Kullanici bu grubun uyesi degil.', array( 'status' => 404 ) );
		}
		if ( $target_id === $actor_id ) {
			return new WP_Error( 'naber_self_action', 'Bu islemi kendinize uygulayamazsiniz.', array( 'status' => 400 ) );
		}
		if ( Naber_Chat_Repo::can_act_on( $actor_role, $target_role ) ) {
			return true;
		}
		// Yetkili uye: yalnizca duz uyelere.
		if ( '' !== $perm
			&& 'member' === $target_role
			&& Naber_Chat_Repo::has_perm( $conversation_id, $actor_id, $perm ) ) {
			return true;
		}
		return new WP_Error( 'naber_forbidden', 'Bu uye uzerinde yetkiniz yok.', array( 'status' => 403 ) );
	}

	public function leave_chat( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		if ( 'group' !== $conversation['type'] ) {
			return new WP_Error( 'naber_not_group', 'Birebir sohbetten ayrilinamaz.', array( 'status' => 400 ) );
		}

		$user_id = get_current_user_id();
		$role    = Naber_Chat_Repo::role_of( (int) $conversation['id'], $user_id );

		Naber_Chat_Repo::remove_member( (int) $conversation['id'], $user_id );
		Naber_Groups::system_message(
			(int) $conversation['id'],
			Naber_Groups::display_name( $user_id ) . ' gruptan ayrildi.'
		);

		// Sahip ayrilirsa yonetim kalan uyelerden rastgele birine gecer.
		if ( 'owner' === $role ) {
			Naber_Groups::auto_transfer_ownership( (int) $conversation['id'], $user_id );
		}

		return rest_ensure_response( array( 'ok' => true ) );
	}

	/**
	 * Sohbeti sessize alir/acar. duration_seconds 0 ise suresiz;
	 * verilirse o kadar sure sonra kendiliginden acilir.
	 */
	public function toggle_notifications( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		$muted    = rest_sanitize_boolean( $request->get_param( 'muted' ) );
		$duration = max( 0, (int) $request->get_param( 'duration_seconds' ) );
		Naber_Chat_Repo::set_notify_muted( (int) $conversation['id'], get_current_user_id(), $muted, $duration );

		$chat = Naber_Chat_Repo::conversation_payload( (int) $conversation['id'], get_current_user_id() );
		return rest_ensure_response( array( 'muted' => $muted, 'chat' => $chat ) );
	}

	/** Sohbeti kullanicinin kendi listesinde sabitler/kaldirir. */
	public function toggle_pin( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		$pinned = rest_sanitize_boolean( $request->get_param( 'pinned' ) );
		Naber_Chat_Repo::set_pinned( (int) $conversation['id'], get_current_user_id(), $pinned );

		$chat = Naber_Chat_Repo::conversation_payload( (int) $conversation['id'], get_current_user_id() );
		return rest_ensure_response( array( 'pinned' => $pinned, 'chat' => $chat ) );
	}

	/**
	 * Kaybolan mesajlari acar/kapatir.
	 *
	 * Ayar sohbetin tamamini ilgilendirir: grupta yalnizca yonetici,
	 * birebir sohbette iki taraftan biri degistirebilir.
	 */
	public function set_disappearing( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}

		$user_id = get_current_user_id();
		if ( 'group' === $conversation['type'] && ! Naber_Chat_Repo::is_group_admin( (int) $conversation['id'], $user_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu ayari yalnizca grup yoneticisi degistirebilir.', array( 'status' => 403 ) );
		}

		$seconds = self::sanitize_disappear_seconds( $request->get_param( 'seconds' ) );
		Naber_Chat_Repo::set_disappearing( (int) $conversation['id'], $seconds );
		// Ayar acilir acilmaz suresi zaten dolmus olan mesajlar temizlenir.
		Naber_Chat_Repo::purge_disappeared( (int) $conversation['id'], $seconds );

		$chat = Naber_Chat_Repo::conversation_payload( (int) $conversation['id'], $user_id );
		return rest_ensure_response( array( 'seconds' => $seconds, 'chat' => $chat ) );
	}

	/** Ankette oy verir veya oyu geri ceker. */
	public function vote_poll( WP_REST_Request $request ) {
		$poll = Naber_Polls::get( (int) $request['id'] );
		if ( ! $poll ) {
			return new WP_Error( 'naber_poll_not_found', 'Anket bulunamadi.', array( 'status' => 404 ) );
		}

		$conversation = $this->authorized_conversation( (int) $poll['conversation_id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}

		$user_id = get_current_user_id();
		$result  = Naber_Polls::vote( (int) $poll['id'], $user_id, (int) $request->get_param( 'option' ) );
		if ( is_wp_error( $result ) ) {
			return $result;
		}

		return rest_ensure_response( array( 'poll' => Naber_Polls::payload( Naber_Polls::get( (int) $poll['id'] ), $user_id ) ) );
	}

	public function list_messages( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}

		$user_id  = get_current_user_id();
		$is_group = 'group' === $conversation['type'];

		// Kaybolan mesajlar aciksa suresi dolanlar listelenmeden once silinir;
		// boylece hem sunucuda kalmazlar hem de istemciye hic gitmezler.
		Naber_Chat_Repo::purge_disappeared(
			(int) $conversation['id'],
			(int) ( $conversation['disappear_seconds'] ?? 0 )
		);

		$messages = Naber_Chat_Repo::messages( (int) $conversation['id'], array(
			'limit'    => (int) $request->get_param( 'limit' ),
			'before'   => (int) $request->get_param( 'before' ),
			'after'    => (int) $request->get_param( 'after' ),
			'is_group' => $is_group,
			'user_id'  => $user_id,
		) );

		Naber_Chat_Repo::mark_delivered( $user_id, $messages );

		return rest_ensure_response( array(
			'messages' => $messages,
			'chat'     => Naber_Chat_Repo::conversation_payload( $conversation, $user_id, $is_group ),
			'typing'   => Naber_Chat_Repo::typing_users( (int) $conversation['id'], $user_id ),
		) );
	}

	public function send_message( WP_REST_Request $request ) {
		$user_id  = get_current_user_id();
		$type     = sanitize_key( (string) $request->get_param( 'type' ) );
		$type     = in_array( $type, array( 'text', 'image', 'location', 'poll', 'audio' ), true ) ? $type : 'text';
		$body     = (string) $request->get_param( 'body' );
		$media_id = (int) $request->get_param( 'media_id' );
		$client   = sanitize_text_field( (string) $request->get_param( 'client_id' ) );

		$conversation_id = (int) $request->get_param( 'conversation_id' );
		if ( $conversation_id > 0 ) {
			$conversation = $this->authorized_conversation( $conversation_id );
			if ( is_wp_error( $conversation ) ) {
				return $conversation;
			}
		} else {
			$receiver_id = (int) $request->get_param( 'receiver_id' );
			if ( $receiver_id <= 0 || ! get_userdata( $receiver_id ) ) {
				return new WP_Error( 'naber_user_not_found', 'Alici bulunamadi.', array( 'status' => 404 ) );
			}
			$conversation_id = Naber_Chat_Repo::ensure_conversation( $user_id, $receiver_id );
			if ( is_wp_error( $conversation_id ) ) {
				return $conversation_id;
			}
			$conversation = Naber_Chat_Repo::get_conversation( $conversation_id );
		}

		$conversation_id = (int) $conversation['id'];
		$member          = Naber_Chat_Repo::member( $conversation_id, $user_id );

		// Grup yoneticisi tarafindan susturulan uye mesaj gonderemez.
		if ( $member && (int) $member['chat_muted'] === 1 ) {
			return new WP_Error( 'naber_muted', 'Bu sohbette yonetici tarafindan susturuldunuz.', array( 'status' => 403 ) );
		}

		$is_group    = 'group' === $conversation['type'];
		$receiver_id = $is_group ? 0 : Naber_Chat_Repo::other_user( $conversation, $user_id );

		// Engelleme yalnizca birebir sohbetleri kapatir; gruplar etkilenmez.
		if ( ! $is_group && $receiver_id && Naber_Blocks::between( $user_id, $receiver_id ) ) {
			return Naber_Blocks::blocked_error( 'message' );
		}

		if ( 'audio' === $type ) {
			// Sesli mesajin govdesi saniye cinsinden suresidir; balonda
			// ses inmeden once sure gosterilebilsin diye tasinir.
			$media = Naber_Media::get( $media_id );
			if ( ! $media || (int) $media['owner_id'] !== $user_id ) {
				return new WP_Error( 'naber_media_invalid', 'Gecersiz ses kaydi.', array( 'status' => 400 ) );
			}
			$body = (string) max( 0, min( 600, (int) $body ) );
		}

		if ( 'image' === $type ) {
			$media = Naber_Media::get( $media_id );
			if ( ! $media || (int) $media['owner_id'] !== $user_id ) {
				return new WP_Error( 'naber_media_invalid', 'Gecersiz medya.', array( 'status' => 400 ) );
			}
			$body = sanitize_text_field( $body );
		} else {
			$body = wp_kses_post( trim( $body ) );
			if ( '' === $body ) {
				return new WP_Error( 'naber_empty_message', 'Bos mesaj gonderilemez.', array( 'status' => 400 ) );
			}
			if ( mb_strlen( $body ) > 5000 ) {
				$body = mb_substr( $body, 0, 5000 );
			}
			$media_id = 0;
		}

		// Gorselin cok kucuk on izlemesi (base64 JPEG). Mesajla birlikte tasindigi
		// icin alici, asil dosya inmeden once bulanik bir goruntu gorebilir.
		if ( 'poll' === $type ) {
			// Anket govdesi sorunun kendisidir; secenekler ayri gelir ve
			// mesaj olusmadan once dogrulanir, yoksa sohbette secenegi
			// olmayan bos bir anket kalirdi.
			$question = Naber_Polls::sanitize_question( $request->get_param( 'question' ) );
			$options  = Naber_Polls::sanitize_options( $request->get_param( 'options' ) );
			if ( '' === $question ) {
				return new WP_Error( 'naber_poll_question', 'Anket sorusu bos olamaz.', array( 'status' => 400 ) );
			}
			if ( ! $options ) {
				return new WP_Error(
					'naber_poll_options',
					sprintf( 'Anket icin en az %d farkli secenek gerekiyor.', Naber_Polls::MIN_OPTIONS ),
					array( 'status' => 400 )
				);
			}
			$body     = $question;
			$media_id = 0;
		}

		if ( 'location' === $type ) {
			// Konum mesajinin govdesi "enlem,boylam" bicimindedir; bozuk
			// deger gelirse mesaj hic olusturulmaz.
			$body = self::sanitize_location( $body );
			if ( '' === $body ) {
				return new WP_Error( 'naber_location_invalid', 'Konum bilgisi gecersiz.', array( 'status' => 400 ) );
			}
			$media_id = 0;
		}

		$thumb    = 'image' === $type ? self::sanitize_preview( $request->get_param( 'preview' ) ) : '';
		$reply_to = (int) $request->get_param( 'reply_to' );
		$message  = Naber_Chat_Repo::insert_message( $conversation_id, $user_id, $receiver_id, $type, $body, $media_id, $client, $thumb, $reply_to );
		if ( $is_group ) {
			$message['sender_name'] = Naber_Auth::user_payload( $user_id )['display_name'];
		}

		if ( 'poll' === $type ) {
			$poll_id = Naber_Polls::create(
				(int) $message['id'],
				$conversation_id,
				$user_id,
				$body,
				$request->get_param( 'options' ),
				rest_sanitize_boolean( $request->get_param( 'multiple' ) )
			);
			if ( is_wp_error( $poll_id ) ) {
				return $poll_id;
			}
			$message['poll'] = Naber_Polls::payload( Naber_Polls::get( $poll_id ), $user_id );
		}

		// "Yaziyor" bilgisi mesaj gidince kendiliginden dusmeli. Istemci
		// bunun icin ayri bir istek atiyordu; o istek gonderimden hemen
		// once bir PHP isciligi daha tutuyor ve gecikmeye ekleniyordu.
		Naber_Chat_Repo::set_typing( $conversation_id, $user_id, false );

		$sender  = Naber_Auth::user_payload( $user_id );
		$preview = 'image' === $type ? 'Fotograf' : ( 'location' === $type ? 'Konum' : ( 'audio' === $type ? 'Sesli mesaj' : wp_trim_words( $body, 12, '...' ) ) );
		if ( 'poll' === $type ) {
			$preview = 'Anket: ' . $preview;
		}
		$title   = $is_group ? (string) $conversation['title'] : $sender['display_name'];
		$text    = $is_group ? $sender['display_name'] . ': ' . $preview : $preview;

		// Bahsedilen uyeler sohbeti sessize almis olsa da bildirim alir;
		// bahsetmenin amaci zaten dikkat cekmek.
		$mentioned = $is_group
			? Naber_Chat_Repo::mentioned_ids( $body, Naber_Chat_Repo::members( $conversation_id ) )
			: array();

		self::notify_conversation_members( $conversation_id, $user_id, $sender['display_name'], $title, $text, (int) $message['id'], $mentioned );

		return rest_ensure_response( array( 'message' => $message ) );
	}

	/**
	 * Yeni mesaj bildirimini sohbetin butun uyelerine gonderir (gonderen
	 * ve sessize almis olanlar haric). send_message ve forward_message
	 * arasinda paylasilir.
	 */
	private static function notify_conversation_members( $conversation_id, $sender_id, $sender_name, $title, $text, $message_id, array $mentioned = array() ) {
		foreach ( Naber_Chat_Repo::member_ids( $conversation_id ) as $member_id ) {
			if ( $member_id === (int) $sender_id ) {
				continue;
			}

			$is_mentioned  = in_array( (int) $member_id, $mentioned, true );
			$target_member = Naber_Chat_Repo::member( $conversation_id, $member_id );
			// Sessize alinmis sohbet susar, ama kisiden bahsedildiyse susmaz.
			if ( ! $is_mentioned && $target_member && Naber_Chat_Repo::is_notify_muted( $target_member ) ) {
				continue;
			}

			$body = $is_mentioned ? sprintf( '%s sizden bahsetti: %s', $sender_name, $text ) : $text;

			Naber_Push::send_to_user(
				$member_id,
				array( 'title' => $title, 'body' => $body ),
				array(
					'type'            => 'message',
					'conversation_id' => (int) $conversation_id,
					'message_id'      => (int) $message_id,
					'sender_id'       => (int) $sender_id,
					'sender_name'     => $sender_name,
					'preview'         => $body,
					'mention'         => $is_mentioned,
				)
			);
		}
	}

	/**
	 * Mesaj silme.
	 * scope=me  -> yalnizca bu kullanicidan gizlenir
	 * scope=all -> herkesten silinir (kendi mesajin ya da grup yoneticisiysen)
	 */
	/**
	 * Bir mesaji baska bir sohbete iletir.
	 *
	 * Gorseller icin ayni medya kaydi (media_id) tekrar kullanilir; dosya
	 * tekrar yuklenmez. Medyanin sahibi olmasa bile, mesaji gorebilen
	 * (kaynak sohbetin uyesi olan) herkes iletebilir.
	 */
	public function forward_message( WP_REST_Request $request ) {
		$message = Naber_Chat_Repo::get_message( (int) $request['id'] );
		if ( ! $message || (int) $message['deleted'] === 1 ) {
			return new WP_Error( 'naber_message_not_found', 'Mesaj bulunamadi.', array( 'status' => 404 ) );
		}

		$user_id = get_current_user_id();
		if ( ! Naber_Chat_Repo::member( (int) $message['conversation_id'], $user_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu mesaja erisim yetkiniz yok.', array( 'status' => 403 ) );
		}

		$target = $this->authorized_conversation( (int) $request->get_param( 'conversation_id' ) );
		if ( is_wp_error( $target ) ) {
			return $target;
		}
		$target_id = (int) $target['id'];

		$target_member = Naber_Chat_Repo::member( $target_id, $user_id );
		if ( $target_member && (int) $target_member['chat_muted'] === 1 ) {
			return new WP_Error( 'naber_muted', 'Bu sohbette yonetici tarafindan susturuldunuz.', array( 'status' => 403 ) );
		}

		$is_group    = 'group' === $target['type'];
		$receiver_id = $is_group ? 0 : Naber_Chat_Repo::other_user( $target, $user_id );

		$forwarded = Naber_Chat_Repo::insert_message(
			$target_id,
			$user_id,
			$receiver_id,
			(string) $message['message_type'],
			(string) $message['body'],
			(int) $message['media_id'],
			'',
			isset( $message['preview'] ) ? (string) $message['preview'] : ''
		);
		if ( $is_group ) {
			$forwarded['sender_name'] = Naber_Auth::user_payload( $user_id )['display_name'];
		}

		$sender  = Naber_Auth::user_payload( $user_id );
		$preview = 'image' === $message['message_type'] ? 'Fotograf' : wp_trim_words( (string) $message['body'], 12, '...' );
		$title   = $is_group ? (string) $target['title'] : $sender['display_name'];
		$text    = $is_group ? $sender['display_name'] . ': ' . $preview : $preview;

		self::notify_conversation_members( $target_id, $user_id, $sender['display_name'], $title, $text, (int) $forwarded['id'] );

		return rest_ensure_response( array( 'message' => $forwarded ) );
	}

	public function delete_message( WP_REST_Request $request ) {
		$message = Naber_Chat_Repo::get_message( (int) $request['id'] );
		if ( ! $message ) {
			return new WP_Error( 'naber_message_not_found', 'Mesaj bulunamadi.', array( 'status' => 404 ) );
		}

		$user_id      = get_current_user_id();
		$conversation = Naber_Chat_Repo::get_conversation( (int) $message['conversation_id'] );

		if ( ! $conversation || ! Naber_Chat_Repo::member( (int) $conversation['id'], $user_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu sohbete erisim yetkiniz yok.', array( 'status' => 403 ) );
		}

		$scope = sanitize_key( (string) $request->get_param( 'scope' ) );
		if ( 'me' === $scope ) {
			Naber_Chat_Repo::hide_message( (int) $message['id'], $user_id );
			return rest_ensure_response( array( 'ok' => true, 'scope' => 'me' ) );
		}

		$is_own = (int) $message['sender_id'] === $user_id;
		// Grup yoneticisi ya da "mesaj silme" yetkisi verilmis uye,
		// herhangi bir uyenin mesajini herkesten silebilir.
		$is_moderator = 'group' === $conversation['type']
			&& Naber_Chat_Repo::has_perm( (int) $conversation['id'], $user_id, 'delete_message' );

		if ( ! $is_own && ! $is_moderator && ! Naber_Auth::is_admin_user( $user_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu mesaji herkesten silemezsiniz.', array( 'status' => 403 ) );
		}

		Naber_Chat_Repo::delete_message( (int) $message['id'] );
		return rest_ensure_response( array( 'ok' => true, 'scope' => 'all' ) );
	}

	/**
	 * Metin mesajini duzenler.
	 * Yalnizca gonderen, yalnizca metin mesajlari ve gonderimden sonraki
	 * 15 dakika icinde duzenleyebilir (WhatsApp'taki gibi bir sinir).
	 */
	public function edit_message( WP_REST_Request $request ) {
		$message = Naber_Chat_Repo::get_message( (int) $request['id'] );
		if ( ! $message || (int) $message['deleted'] === 1 ) {
			return new WP_Error( 'naber_message_not_found', 'Mesaj bulunamadi.', array( 'status' => 404 ) );
		}

		$user_id = get_current_user_id();
		if ( (int) $message['sender_id'] !== $user_id ) {
			return new WP_Error( 'naber_forbidden', 'Yalnizca kendi mesajinizi duzenleyebilirsiniz.', array( 'status' => 403 ) );
		}
		if ( 'text' !== $message['message_type'] ) {
			return new WP_Error( 'naber_not_editable', 'Yalnizca metin mesajlari duzenlenebilir.', array( 'status' => 400 ) );
		}

		if ( ! self::within_edit_window( time() - Naber_Chat_Repo::ts( $message['created_at'] ) ) ) {
			return new WP_Error( 'naber_edit_expired', 'Bu mesaj artik duzenlenemez (15 dakikayi gecti).', array( 'status' => 403 ) );
		}

		$body = wp_kses_post( trim( (string) $request->get_param( 'body' ) ) );
		if ( '' === $body ) {
			return new WP_Error( 'naber_empty_message', 'Bos mesaj birakilamaz.', array( 'status' => 400 ) );
		}
		if ( mb_strlen( $body ) > 5000 ) {
			$body = mb_substr( $body, 0, 5000 );
		}

		$conversation = Naber_Chat_Repo::get_conversation( (int) $message['conversation_id'] );
		$updated      = Naber_Chat_Repo::edit_message( (int) $message['id'], $body );
		if ( ! $updated ) {
			return new WP_Error( 'naber_edit_failed', 'Mesaj duzenlenemedi.', array( 'status' => 500 ) );
		}
		if ( $conversation && 'group' === $conversation['type'] ) {
			$updated['sender_name'] = Naber_Auth::user_payload( $user_id )['display_name'];
		}

		return rest_ensure_response( array( 'message' => $updated ) );
	}

	/**
	 * Mesaja emoji reaksiyonu birakir/kaldirir. Ayni emojiye tekrar
	 * basmak reaksiyonu kaldirir; farkli bir emoji basmak degistirir.
	 */
	public function react_to_message( WP_REST_Request $request ) {
		$message = Naber_Chat_Repo::get_message( (int) $request['id'] );
		if ( ! $message || (int) $message['deleted'] === 1 ) {
			return new WP_Error( 'naber_message_not_found', 'Mesaj bulunamadi.', array( 'status' => 404 ) );
		}

		$user_id = get_current_user_id();
		if ( ! Naber_Chat_Repo::member( (int) $message['conversation_id'], $user_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu mesaja erisim yetkiniz yok.', array( 'status' => 403 ) );
		}

		$emoji = self::sanitize_emoji( $request->get_param( 'emoji' ) );
		if ( '' === $emoji ) {
			return new WP_Error( 'naber_invalid_emoji', 'Gecerli bir emoji gerekli.', array( 'status' => 400 ) );
		}

		$result = Naber_Reactions::toggle( (int) $message['id'], $user_id, $emoji );
		Naber_Chat_Repo::touch_conversation( (int) $message['conversation_id'] );

		return rest_ensure_response(
			array(
				'action'    => $result['action'],
				'reactions' => Naber_Reactions::summary( (int) $message['id'], $user_id ),
			)
		);
	}

	/** Emoji girisi: bos degil, DB sutununa (varchar 16) sigacak kadar kisa. */
	public static function sanitize_emoji( $value ) {
		$emoji = trim( (string) $value );
		if ( '' === $emoji || strlen( $emoji ) > 16 ) {
			return '';
		}
		return $emoji;
	}

	public function message_info( WP_REST_Request $request ) {
		$message = Naber_Chat_Repo::get_message( (int) $request['id'] );
		if ( ! $message ) {
			return new WP_Error( 'naber_message_not_found', 'Mesaj bulunamadi.', array( 'status' => 404 ) );
		}

		$user_id = get_current_user_id();
		if ( ! Naber_Chat_Repo::member( (int) $message['conversation_id'], $user_id ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu mesaja erisim yetkiniz yok.', array( 'status' => 403 ) );
		}

		return rest_ensure_response( array( 'info' => Naber_Chat_Repo::message_info( (int) $message['id'], $user_id ) ) );
	}

	public function mark_read( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		$count = Naber_Chat_Repo::mark_read( (int) $conversation['id'], get_current_user_id() );
		return rest_ensure_response( array( 'updated' => $count ) );
	}

	public function typing( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		$typing = $request->get_param( 'typing' );
		$typing = ( null === $typing ) ? true : rest_sanitize_boolean( $typing );

		Naber_Chat_Repo::set_typing( (int) $conversation['id'], get_current_user_id(), $typing );
		return rest_ensure_response( array( 'ok' => true, 'typing' => $typing ) );
	}

	// ------------------------------------------------------------------
	// Medya
	// ------------------------------------------------------------------

	/**
	 * Ayni dosya daha once yuklendi mi?
	 *
	 * Istemci dosyanin SHA-256 ozetini gonderir; ayni ozet bulunursa dosya
	 * tekrar yuklenmez, var olan medya kaydi kullanilir. Boylece ayni gorseli
	 * ikinci kez paylasmak aninda tamamlanir ve depolama kotasi bosa gitmez.
	 */
	public function media_find( WP_REST_Request $request ) {
		$hash  = self::sanitize_hash( $request->get_param( 'hash' ) );
		$media = '' === $hash ? null : Naber_Media::find_by_hash( $hash, get_current_user_id() );
		if ( ! $media ) {
			return rest_ensure_response( array( 'media' => null ) );
		}
		return rest_ensure_response( array( 'media' => Naber_Media::payload( $media ) ) );
	}

	public function media_upload_url( WP_REST_Request $request ) {
		$mime = strtolower( sanitize_text_field( (string) $request->get_param( 'mime' ) ) );
		$size = (int) $request->get_param( 'size' );
		$hash = self::sanitize_hash( $request->get_param( 'hash' ) );

		// Ayni dosya zaten yuklenmisse yeniden yuklemeye gerek yok.
		if ( '' !== $hash ) {
			$existing = Naber_Media::find_by_hash( $hash, get_current_user_id() );
			if ( $existing ) {
				return rest_ensure_response( array(
					'media_id'  => (int) $existing['id'],
					'duplicate' => true,
					'media'     => Naber_Media::payload( $existing ),
				) );
			}
		}

		if ( ! Naber_Media::is_allowed_mime( $mime ) ) {
			return new WP_Error( 'naber_bad_mime', 'Bu dosya turu desteklenmiyor.', array( 'status' => 415 ) );
		}
		if ( $size <= 0 || $size > Naber_Media::max_bytes() ) {
			return new WP_Error( 'naber_too_large', sprintf( 'Dosya boyutu en fazla %d MB olabilir.', (int) Naber_Settings::get( 'b2_max_upload_mb', 25 ) ), array( 'status' => 413 ) );
		}
		if ( ! Naber_Settings::b2_ready() ) {
			return new WP_Error( 'naber_storage_unconfigured', 'Depolama ayarlari yapilmamis. Yonetici WordPress panelinden Backblaze bilgilerini girmeli.', array( 'status' => 503 ) );
		}

		$user_id   = get_current_user_id();
		$file_name = Naber_Media::build_file_name( $user_id, $mime );
		$b2        = new Naber_B2();
		$target    = $b2->get_upload_url();
		if ( is_wp_error( $target ) ) {
			return $target;
		}

		$media_id = Naber_Media::create_pending(
			$user_id,
			$file_name,
			$mime,
			$size,
			(int) $request->get_param( 'width' ),
			(int) $request->get_param( 'height' ),
			$hash
		);

		return rest_ensure_response( array(
			'media_id'   => $media_id,
			'upload_url' => $target['upload_url'],
			'token'      => $target['token'],
			'file_name'  => $file_name,
			'mime'       => $mime,
		) );
	}

	public function media_complete( WP_REST_Request $request ) {
		$media = Naber_Media::complete(
			(int) $request->get_param( 'media_id' ),
			get_current_user_id(),
			sanitize_text_field( (string) $request->get_param( 'file_id' ) ),
			(int) $request->get_param( 'size' )
		);
		if ( is_wp_error( $media ) ) {
			return $media;
		}
		return rest_ensure_response( array( 'media' => Naber_Media::payload( $media ) ) );
	}

	public function media_proxy_upload( WP_REST_Request $request ) {
		$files = $request->get_file_params();
		if ( empty( $files['file'] ) || ! isset( $files['file']['tmp_name'] ) ) {
			return new WP_Error( 'naber_no_file', 'Dosya alinamadi.', array( 'status' => 400 ) );
		}

		$file = $files['file'];
		if ( (int) $file['size'] > Naber_Media::max_bytes() ) {
			return new WP_Error( 'naber_too_large', 'Dosya cok buyuk.', array( 'status' => 413 ) );
		}

		$check = wp_check_filetype_and_ext( $file['tmp_name'], $file['name'] );
		$mime  = $check['type'] ? $check['type'] : ( function_exists( 'mime_content_type' ) ? mime_content_type( $file['tmp_name'] ) : '' );
		if ( ! Naber_Media::is_allowed_mime( $mime ) ) {
			return new WP_Error( 'naber_bad_mime', 'Bu dosya turu desteklenmiyor.', array( 'status' => 415 ) );
		}

		$user_id = get_current_user_id();
		$content = file_get_contents( $file['tmp_name'] );
		$hash    = hash( 'sha256', $content );

		// Ayni dosya daha once yuklendiyse tekrar yukleme; var olani dondur.
		$existing = Naber_Media::find_by_hash( $hash, $user_id );
		if ( $existing ) {
			return rest_ensure_response( array( 'media' => Naber_Media::payload( $existing ), 'duplicate' => true ) );
		}

		$file_name = Naber_Media::build_file_name( $user_id, $mime );
		$b2        = new Naber_B2();
		$uploaded  = $b2->upload( $file_name, $content, $mime );
		if ( is_wp_error( $uploaded ) ) {
			return $uploaded;
		}

		$size     = getimagesize( $file['tmp_name'] );
		$media_id = Naber_Media::create_pending( $user_id, $file_name, $mime, strlen( $content ), $size ? (int) $size[0] : 0, $size ? (int) $size[1] : 0, $hash );
		$media    = Naber_Media::complete( $media_id, $user_id, $uploaded['file_id'], strlen( $content ) );

		return rest_ensure_response( array( 'media' => Naber_Media::payload( $media ) ) );
	}

	public function media_url( WP_REST_Request $request ) {
		$media = Naber_Media::get( (int) $request['id'] );
		if ( ! $media ) {
			return new WP_Error( 'naber_media_not_found', 'Medya bulunamadi.', array( 'status' => 404 ) );
		}

		global $wpdb;
		$user_id = get_current_user_id();
		$allowed = (int) $media['owner_id'] === $user_id;

		if ( ! $allowed ) {
			$messages = Naber_DB::table( 'messages' );
			$members  = Naber_DB::table( 'members' );
			$allowed  = (bool) $wpdb->get_var(
				$wpdb->prepare(
					"SELECT COUNT(*) FROM {$messages} m
					 INNER JOIN {$members} me ON me.conversation_id = m.conversation_id AND me.user_id = %d
					 WHERE m.media_id = %d",
					$user_id,
					(int) $media['id']
				)
			);
		}

		if ( ! $allowed ) {
			return new WP_Error( 'naber_forbidden', 'Bu medyaya erisim yetkiniz yok.', array( 'status' => 403 ) );
		}
		return rest_ensure_response( array( 'media' => Naber_Media::payload( $media ) ) );
	}

	// ------------------------------------------------------------------
	// Cihaz / olaylar
	// ------------------------------------------------------------------

	public function register_device( WP_REST_Request $request ) {
		$token = sanitize_text_field( (string) $request->get_param( 'token' ) );
		if ( '' === $token ) {
			return new WP_Error( 'naber_no_token', 'Cihaz jetonu gerekli.', array( 'status' => 400 ) );
		}
		$platform = sanitize_key( (string) $request->get_param( 'platform' ) );
		Naber_Push::register_device( get_current_user_id(), $token, $platform ? $platform : 'android' );
		return rest_ensure_response( array( 'ok' => true ) );
	}

	public function delete_device( WP_REST_Request $request ) {
		Naber_Push::unregister_device( sanitize_text_field( (string) $request->get_param( 'token' ) ) );
		return rest_ensure_response( array( 'ok' => true ) );
	}

	/**
	 * Canli olay akisi (long-polling).
	 *
	 * Istek sunucuda en fazla 25 saniye bekler. Bekleme sirasinda yalnizca kucuk
	 * kontrol sorgulari calisir; tam yanit ancak gercekten bir sey degistiginde
	 * hazirlanir. Istemci elindeki durumun imzalarini gonderir, sunucu farkli
	 * bir durum gorurse hemen doner. Boylece yeni mesaj, cevrimici/son gorulme,
	 * "yaziyor" ve grup degisiklikleri ekranda kendiliginden guncellenir.
	 */
	public function events( WP_REST_Request $request ) {
		// Saka savunmasinda gecici atilanlarin suresi doldu mu? Yoklama
		// istegi surekli geldigi icin burasi zamanlayici gorevi gorur.
		Naber_Groups::restore_pranks();

		$user_id   = get_current_user_id();
		$since_msg = (int) $request->get_param( 'since_message_id' );
		$since_sig = (int) $request->get_param( 'since_signal_id' );
		$max_wait  = max( 0, min( 30, (int) Naber_Settings::get( 'poll_wait', 20 ) ) );
		$wait      = max( 0, min( $max_wait, (int) $request->get_param( 'wait' ) ) );
		$conv_id   = (int) $request->get_param( 'conversation_id' );
		$deadline  = microtime( true ) + $wait;

		// 500 ms: her turda bir probe() sorgusu atiliyor, 250 ms'de 10 kisi
		// saniyede 40 sorgu demek. Paylasimli MySQL'de bu butun istekleri
		// yavaslatiyordu; yarim saniyelik fark kullanici tarafinda farkedilmez.
		$interval_ms = max( 100, min( 2000, (int) Naber_Settings::get( 'poll_interval_ms', 500 ) ) );
		$slow_every  = max( 1, (int) round( 1000 / $interval_ms ) );

		// Istemcinin elindeki durum; eski surumler gondermezse karsilastirma yapilmaz.
		$client_typing   = $request->get_param( 'typing_signature' );
		$client_presence = $request->get_param( 'presence_signature' );
		$client_revision = $request->get_param( 'revision_signature' );
		// Acik sohbetteki grup aramasinin durumu; degisirse "Katil"
		// dugmesi beklemeden gorunsun/kaybolsun.
		$client_call = $request->get_param( 'conversation_call_signature' );

		$iteration = 0;

		while ( true ) {
			$probe   = Naber_Chat_Repo::probe( $user_id );
			$has_new = $probe['message'] > $since_msg || $probe['signal'] > $since_sig;
			$changed = false;

			// Saniyede bir: gelen arama ve "yaziyor". Iki saniyede bir: cevrimici ve grup degisiklikleri.
			if ( ! $has_new && 0 === $iteration % $slow_every ) {
				if ( Naber_Calls::active_incoming( $user_id ) ) {
					$changed = true;
				}
				if ( ! $changed && null !== $client_typing ) {
					$changed = Naber_Chat_Repo::typing_signature( $conv_id, $user_id ) !== (string) $client_typing;
				}
				if ( ! $changed && null !== $client_call && $conv_id > 0 ) {
					$changed = self::conversation_call_signature( $conv_id ) !== (string) $client_call;
				}
			}

			if ( ! $has_new && ! $changed && 0 === $iteration % ( $slow_every * 2 ) ) {
				if ( null !== $client_presence ) {
					$changed = self::presence_signature( $user_id ) !== (string) $client_presence;
				}
				if ( ! $changed && null !== $client_revision ) {
					$changed = self::revision_signature( $user_id ) !== (string) $client_revision;
				}
			}

			if ( $has_new || $changed || microtime( true ) >= $deadline ) {
				return rest_ensure_response( $this->build_events_payload( $user_id, $since_msg, $since_sig, $conv_id ) );
			}

			if ( 0 === $iteration % ( $slow_every * 10 ) ) {
				Naber_Auth::touch_presence( $user_id );
			}

			$iteration++;
			usleep( $interval_ms * 1000 );
		}
	}

	/** @var array|null Istek suresince gecerli kisi listesi. */
	private static $peer_ids = null;

	/** Gecersiz deger kapali (0) sayilir. (saf fonksiyon) */
	public static function sanitize_disappear_seconds( $value ) {
		$seconds = (int) $value;
		return in_array( $seconds, self::DISAPPEAR_OPTIONS, true ) ? $seconds : 0;
	}

	/**
	 * Konum govdesini dogrular: "enlem,boylam" (saf fonksiyon).
	 *
	 * Gecersizse bos doner. Ondalik 6 haneye kirpilir; bu yaklasik 10 cm
	 * hassasiyettir, daha fazlasi hem gereksiz hem de gercekte yok.
	 */
	public static function sanitize_location( $value ) {
		$parts = explode( ',', trim( (string) $value ) );
		if ( 2 !== count( $parts ) ) {
			return '';
		}

		$lat = trim( $parts[0] );
		$lon = trim( $parts[1] );
		if ( ! is_numeric( $lat ) || ! is_numeric( $lon ) ) {
			return '';
		}

		$lat = (float) $lat;
		$lon = (float) $lon;
		if ( $lat < -90 || $lat > 90 || $lon < -180 || $lon > 180 ) {
			return '';
		}

		return sprintf( '%.6f,%.6f', $lat, $lon );
	}

	/** Gecen saniyeye gore mesaj hala duzenlenebilir mi? (saf fonksiyon) */
	public static function within_edit_window( $elapsed_seconds ) {
		return (int) $elapsed_seconds <= self::EDIT_WINDOW_SECONDS;
	}

	/**
	 * Dosya ozeti: yalnizca 64 haneli onaltilik SHA-256 kabul edilir.
	 * Gecersiz deger sessizce bos doner; ozet yoksa normal yukleme yapilir.
	 */
	public static function sanitize_hash( $value ) {
		$hash = strtolower( trim( (string) $value ) );
		if ( 64 !== strlen( $hash ) || ! ctype_xdigit( $hash ) ) {
			return '';
		}
		return $hash;
	}

	/**
	 * Gorsel on izlemesi: base64 kodlu cok kucuk bir JPEG.
	 * Mesaj satirini sismemesi icin boyutu sinirlanir; bozuk veri atilir.
	 */
	public static function sanitize_preview( $value ) {
		$raw = trim( (string) $value );
		if ( '' === $raw ) {
			return '';
		}
		// "data:image/jpeg;base64," onekiyle gelirse ayiklanir.
		$comma = strpos( $raw, ',' );
		if ( 0 === strpos( $raw, 'data:' ) && false !== $comma ) {
			$raw = substr( $raw, $comma + 1 );
		}
		$raw = preg_replace( '/\s+/', '', $raw );
		if ( '' === $raw || strlen( $raw ) > self::MAX_PREVIEW_CHARS ) {
			return '';
		}
		if ( ! preg_match( '#^[A-Za-z0-9+/]+={0,2}$#', $raw ) ) {
			return '';
		}
		return $raw;
	}

	private static function peer_ids( $user_id ) {
		if ( null === self::$peer_ids ) {
			self::$peer_ids = Naber_Chat_Repo::peer_ids( $user_id );
		}
		return self::$peer_ids;
	}

	/** Cevrimici durumlarinin kisa imzasi. */
	private static function presence_signature( $user_id ) {
		$parts = array();
		foreach ( Naber_Auth::presence_of( self::peer_ids( $user_id ) ) as $id => $state ) {
			$parts[] = $id . ':' . ( $state['online'] ? '1' : '0' );
		}
		sort( $parts );
		return implode( ',', $parts );
	}

	/** Sohbet degisiklik damgalarinin kisa imzasi. */
	private static function revision_signature( $user_id ) {
		$parts = array();
		foreach ( Naber_Chat_Repo::revisions( $user_id ) as $row ) {
			$parts[] = $row['id'] . ':' . $row['updated_at'];
		}
		sort( $parts );
		return md5( implode( ',', $parts ) );
	}

	/**
	 * Sohbetteki grup aramasinin kimlik + durum imzasi.
	 * Arama baslar, biter ya da katilimcilar degisirse imza degisir.
	 */
	public static function conversation_call_signature( $conv_id ) {
		if ( $conv_id <= 0 ) {
			return '';
		}
		$row = Naber_Calls::active_group_call( (int) $conv_id );
		if ( ! $row ) {
			return '0';
		}
		$joined = Naber_Calls::joined_user_ids( (int) $row['id'] );
		sort( $joined );
		return (int) $row['id'] . ':' . (string) $row['status'] . ':' . implode( ',', $joined );
	}

	private function build_events_payload( $user_id, $since_msg, $since_sig, $conv_id ) {
		Naber_Auth::touch_presence( $user_id );

		$messages = Naber_Chat_Repo::messages_since( $user_id, $since_msg );
		$signals  = Naber_Calls::signals_for( $user_id, $since_sig );
		$incoming = Naber_Calls::active_incoming( $user_id );

		// Mesajlar cihaza ulasti: gonderen gri cift tiki gorsun.
		Naber_Chat_Repo::mark_delivered( $user_id, $messages );

		$typing = array();
		if ( $conv_id > 0 && Naber_Chat_Repo::member( $conv_id, $user_id ) ) {
			$typing = Naber_Chat_Repo::typing_users( $conv_id, $user_id );
		}

		$presence = array();
		foreach ( Naber_Auth::presence_of( self::peer_ids( $user_id ) ) as $id => $state ) {
			$presence[] = array(
				'id'        => (int) $id,
				'online'    => (bool) $state['online'],
				'last_seen' => (int) $state['last_seen'],
			);
		}

		// Acik sohbette suren grup aramasi: sohbet basliginda "Katil"
		// dugmesi ve katilimci seridi bunu kullanir.
		$conversation_call = null;
		if ( $conv_id > 0 && Naber_Chat_Repo::member( $conv_id, $user_id ) ) {
			$row = Naber_Calls::active_group_call( $conv_id );
			if ( $row ) {
				$conversation_call = Naber_Calls::payload( $row, true );
			}
		}

		return array(
			'messages'               => $messages,
			'signals'                => $signals,
			'incoming_call'          => $incoming,
			'conversation_call'      => $conversation_call,
			'read_states'            => Naber_Chat_Repo::read_states( $user_id ),
			'typing'                 => $typing,
			'typing_conversation_id' => $conv_id,
			'presence'               => $presence,
			'chat_revisions'         => Naber_Chat_Repo::revisions( $user_id ),
			'typing_signature'       => Naber_Chat_Repo::typing_signature( $conv_id, $user_id ),
			'conversation_call_signature' => self::conversation_call_signature( $conv_id ),
			'presence_signature'     => self::presence_signature( $user_id ),
			'revision_signature'     => self::revision_signature( $user_id ),
			'unread_total'           => Naber_Chat_Repo::unread_total( $user_id ),
			'server_time'            => time(),
			'since_message_id'       => $messages ? (int) $messages[ count( $messages ) - 1 ]['id'] : $since_msg,
			'since_signal_id'        => $signals ? (int) $signals[ count( $signals ) - 1 ]['id'] : $since_sig,
		);
	}

	/** Uygulama on planda mi arka planda mi oldugunu bildirir. */
	public function set_presence( WP_REST_Request $request ) {
		$user_id = get_current_user_id();
		$online  = $request->get_param( 'online' );
		$online  = ( null === $online ) ? true : rest_sanitize_boolean( $online );

		if ( $online ) {
			Naber_Auth::touch_presence( $user_id, true );
		} else {
			Naber_Auth::set_offline( $user_id );
		}

		return rest_ensure_response( array( 'online' => $online ) );
	}

	public function ice_servers() {
		return rest_ensure_response( array( 'ice_servers' => ( new Naber_Turn() )->ice_servers() ) );
	}

	// ------------------------------------------------------------------
	// Aramalar
	// ------------------------------------------------------------------

	public function call_start( WP_REST_Request $request ) {
		$user_id         = get_current_user_id();
		$conversation_id = (int) $request->get_param( 'conversation_id' );
		$callee_id       = (int) $request->get_param( 'user_id' );

		if ( $conversation_id > 0 ) {
			$conversation = $this->authorized_conversation( $conversation_id );
			if ( is_wp_error( $conversation ) ) {
				return $conversation;
			}
			if ( 'group' === $conversation['type'] ) {
				$call = Naber_Calls::start_group( $user_id, $conversation_id );
			} else {
				$other = (int) Naber_Chat_Repo::other_user( $conversation, $user_id );
				if ( Naber_Blocks::between( $user_id, $other ) ) {
					return Naber_Blocks::blocked_error( 'call' );
				}
				$call = Naber_Calls::start_direct( $user_id, $other );
			}
		} else {
			if ( $callee_id <= 0 || $callee_id === $user_id || ! get_userdata( $callee_id ) ) {
				return new WP_Error( 'naber_user_not_found', 'Aranacak kullanici bulunamadi.', array( 'status' => 404 ) );
			}
			if ( Naber_Blocks::between( $user_id, $callee_id ) ) {
				return Naber_Blocks::blocked_error( 'call' );
			}
			$call = Naber_Calls::start_direct( $user_id, $callee_id );
		}

		if ( is_wp_error( $call ) ) {
			return $call;
		}

		$caller   = Naber_Auth::user_payload( $user_id );
		$is_group = 'group' === $call['type'];
		$title    = $is_group ? Naber_Calls::payload( $call )['group_title'] : $caller['display_name'];

		foreach ( Naber_Calls::participants( (int) $call['id'] ) as $participant ) {
			if ( (int) $participant['id'] === $user_id || 'ringing' !== $participant['call_status'] ) {
				continue;
			}
			Naber_Push::send_to_user(
				(int) $participant['id'],
				array( 'title' => $title, 'body' => $is_group ? $caller['display_name'] . ' grup aramasi baslatti' : 'Sesli arama' ),
				array(
					'type'          => 'call',
					'call_id'       => $call['id'],
					'call_type'     => $call['type'],
					'caller_id'     => $user_id,
					'caller_name'   => $caller['display_name'],
					'caller_avatar' => (string) $caller['avatar'],
					'group_title'   => $title,
				),
				true
			);
		}

		return rest_ensure_response( array(
			'call'        => Naber_Calls::payload( $call, true ),
			'peers'       => Naber_Calls::joined_user_ids( (int) $call['id'], $user_id ),
			'ice_servers' => ( new Naber_Turn() )->ice_servers(),
		) );
	}

	private function authorized_call( $call_id ) {
		$call = Naber_Calls::get( $call_id );
		if ( ! $call ) {
			return new WP_Error( 'naber_call_not_found', 'Arama bulunamadi.', array( 'status' => 404 ) );
		}
		if ( ! Naber_Calls::is_participant( $call, get_current_user_id() ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu aramaya erisim yetkiniz yok.', array( 'status' => 403 ) );
		}
		return $call;
	}

	public function call_info( WP_REST_Request $request ) {
		$call = $this->authorized_call( (int) $request['id'] );
		if ( is_wp_error( $call ) ) {
			return $call;
		}
		return rest_ensure_response( array(
			'call'  => Naber_Calls::payload( $call, true ),
			'peers' => Naber_Calls::joined_user_ids( (int) $call['id'], get_current_user_id() ),
		) );
	}

	public function call_action( WP_REST_Request $request ) {
		$call = $this->authorized_call( (int) $request['id'] );
		if ( is_wp_error( $call ) ) {
			return $call;
		}

		$action   = (string) $request['action'];
		$user_id  = get_current_user_id();
		$call_id  = (int) $call['id'];
		$is_group = 'group' === $call['type'];

		$participant = Naber_Calls::participant( $call_id, $user_id );
		if ( $participant && 'kicked' === $participant['status'] && in_array( $action, array( 'accept', 'join' ), true ) ) {
			return new WP_Error( 'naber_kicked', 'Bu aramadan cikarildiniz.', array( 'status' => 403 ) );
		}

		switch ( $action ) {
			case 'accept':
			case 'join':
				Naber_Calls::set_participant_status( $call_id, $user_id, 'joined' );
				$call = Naber_Calls::set_status( $call_id, 'active' );
				Naber_Calls::broadcast_state( $call_id, $user_id, array( 'status' => 'joined', 'user_id' => $user_id ) );
				break;

			case 'reject':
				Naber_Calls::set_participant_status( $call_id, $user_id, 'rejected' );
				if ( ! $is_group ) {
					$call = Naber_Calls::set_status( $call_id, 'rejected', 'declined' );
				}
				Naber_Calls::broadcast_state( $call_id, $user_id, array( 'status' => $is_group ? 'left' : 'rejected', 'user_id' => $user_id ) );
				break;

			case 'end':
			case 'leave':
			default:
				Naber_Calls::set_participant_status( $call_id, $user_id, 'left' );
				Naber_Calls::broadcast_state( $call_id, $user_id, array( 'status' => 'left', 'user_id' => $user_id ) );

				$remaining = Naber_Calls::joined_user_ids( $call_id, 0 );
				if ( ! $is_group || count( $remaining ) < 2 ) {
					$reason = ( 'ringing' === $call['status'] && (int) $call['caller_id'] === $user_id ) ? 'cancelled' : 'hangup';
					$status = ( 'ringing' === $call['status'] && ! $is_group && (int) $call['caller_id'] === $user_id ) ? 'missed' : 'ended';
					$call   = Naber_Calls::set_status( $call_id, $status, $reason );
					Naber_Calls::broadcast_state( $call_id, $user_id, array( 'status' => 'ended' ) );
				} else {
					$call = Naber_Calls::get( $call_id );
				}
				break;
		}

		Naber_Calls::cleanup_signals();

		return rest_ensure_response( array(
			'call'  => Naber_Calls::payload( $call, true ),
			'peers' => Naber_Calls::joined_user_ids( $call_id, $user_id ),
		) );
	}

	public function call_signal( WP_REST_Request $request ) {
		$call = $this->authorized_call( (int) $request['id'] );
		if ( is_wp_error( $call ) ) {
			return $call;
		}

		$type = sanitize_key( (string) $request->get_param( 'type' ) );
		if ( ! in_array( $type, array( 'offer', 'answer', 'ice', 'state' ), true ) ) {
			return new WP_Error( 'naber_bad_signal', 'Gecersiz signaling turu.', array( 'status' => 400 ) );
		}

		$payload = $request->get_param( 'payload' );
		if ( is_array( $payload ) ) {
			$payload = wp_json_encode( $payload );
		}
		$payload = (string) $payload;
		if ( strlen( $payload ) > 60000 ) {
			return new WP_Error( 'naber_signal_too_large', 'Signaling verisi cok buyuk.', array( 'status' => 413 ) );
		}

		$user_id = get_current_user_id();
		$target  = (int) $request->get_param( 'to' );

		if ( $target <= 0 ) {
			// Birebir aramada hedef bellidir.
			$target = (int) $call['caller_id'] === $user_id ? (int) $call['callee_id'] : (int) $call['caller_id'];
		}
		if ( $target <= 0 || ! Naber_Calls::participant( (int) $call['id'], $target ) ) {
			return new WP_Error( 'naber_bad_target', 'Signaling hedefi aramada degil.', array( 'status' => 400 ) );
		}

		$id = Naber_Calls::add_signal( (int) $call['id'], $user_id, $target, $type, $payload );
		return rest_ensure_response( array( 'id' => $id ) );
	}

	public function call_signals( WP_REST_Request $request ) {
		$call = $this->authorized_call( (int) $request['id'] );
		if ( is_wp_error( $call ) ) {
			return $call;
		}
		return rest_ensure_response( array(
			'signals' => Naber_Calls::signals_for( get_current_user_id(), (int) $request->get_param( 'since' ), (int) $call['id'] ),
			'call'    => Naber_Calls::payload( $call, true ),
			'peers'   => Naber_Calls::joined_user_ids( (int) $call['id'], get_current_user_id() ),
		) );
	}

	/** Grup aramasinda yonetici bir katilimciyi susturur (sureseiz) veya acar. */
	public function call_mute_participant( WP_REST_Request $request ) {
		$call = $this->authorized_call( (int) $request['id'] );
		if ( is_wp_error( $call ) ) {
			return $call;
		}
		$target = (int) $request['user'];
		$check  = $this->can_moderate_call( $call, $target );
		if ( is_wp_error( $check ) ) {
			return $check;
		}

		$muted = rest_sanitize_boolean( $request->get_param( 'muted' ) );
		Naber_Calls::set_participant_muted( (int) $call['id'], $target, $muted );

		// Susturulan kisiye ozel bildirim, herkese guncel katilimci listesi.
		Naber_Calls::add_signal(
			(int) $call['id'],
			get_current_user_id(),
			$target,
			'state',
			wp_json_encode( array( 'status' => $muted ? 'force_muted' : 'force_unmuted', 'user_id' => $target ) )
		);
		Naber_Calls::broadcast_state(
			(int) $call['id'],
			get_current_user_id(),
			array( 'status' => 'participant_updated', 'user_id' => $target, 'muted' => $muted ),
			true
		);

		return rest_ensure_response( array( 'call' => Naber_Calls::payload( $call, true ) ) );
	}

	/** Grup aramasinda yonetici bir katilimciyi sesten atar. */
	public function call_kick_participant( WP_REST_Request $request ) {
		$call = $this->authorized_call( (int) $request['id'] );
		if ( is_wp_error( $call ) ) {
			return $call;
		}
		$target = (int) $request['user'];
		$check  = $this->can_moderate_call( $call, $target );
		if ( is_wp_error( $check ) ) {
			return $check;
		}

		Naber_Calls::set_participant_status( (int) $call['id'], $target, 'kicked' );
		Naber_Calls::add_signal( (int) $call['id'], get_current_user_id(), $target, 'state', wp_json_encode( array( 'status' => 'kicked', 'user_id' => $target ) ) );
		Naber_Calls::broadcast_state(
			(int) $call['id'],
			get_current_user_id(),
			array( 'status' => 'left', 'user_id' => $target ),
			true
		);

		return rest_ensure_response( array( 'call' => Naber_Calls::payload( $call, true ) ) );
	}

	private function can_moderate_call( $call, $target_id ) {
		if ( 'group' !== $call['type'] ) {
			return new WP_Error( 'naber_not_group_call', 'Bu islem yalnizca grup aramalarinda yapilabilir.', array( 'status' => 400 ) );
		}
		$conversation = Naber_Chat_Repo::get_conversation( (int) $call['conversation_id'] );
		if ( ! $conversation ) {
			return new WP_Error( 'naber_chat_not_found', 'Grup bulunamadi.', array( 'status' => 404 ) );
		}
		return $this->can_moderate( $conversation, $target_id );
	}

	public function call_history() {
		return rest_ensure_response( array( 'calls' => Naber_Calls::history( get_current_user_id() ) ) );
	}

	// ------------------------------------------------------------------
	// Yonetim
	// ------------------------------------------------------------------

	public function admin_stats() {
		global $wpdb;
		$messages = Naber_DB::table( 'messages' );
		$storage  = Naber_Media::storage_stats();

		$total_messages = (int) $wpdb->get_var( "SELECT COUNT(*) FROM {$messages}" );
		$today          = (int) $wpdb->get_var( $wpdb->prepare( "SELECT COUNT(*) FROM {$messages} WHERE created_at >= %s", gmdate( 'Y-m-d 00:00:00' ) ) );
		$conversations  = (int) $wpdb->get_var( 'SELECT COUNT(*) FROM ' . Naber_DB::table( 'conversations' ) . " WHERE type = 'direct'" );
		$groups         = (int) $wpdb->get_var( 'SELECT COUNT(*) FROM ' . Naber_DB::table( 'conversations' ) . " WHERE type = 'group'" );

		$recent = array();
		foreach ( get_users( array( 'number' => 10, 'orderby' => 'registered', 'order' => 'DESC' ) ) as $user ) {
			$recent[] = Naber_Auth::user_payload( $user, true );
		}

		$online = 0;
		$banned = 0;
		foreach ( get_users( array( 'number' => 500, 'fields' => 'ID' ) ) as $uid ) {
			if ( Naber_Auth::is_online( $uid ) ) {
				$online++;
			}
			if ( Naber_Auth::is_banned( $uid ) ) {
				$banned++;
			}
		}

		return rest_ensure_response( array(
			'users'           => array(
				'total'  => (int) count_users()['total_users'],
				'online' => $online,
				'banned' => $banned,
			),
			'messages'        => array(
				'total' => $total_messages,
				'today' => $today,
			),
			'conversations'   => $conversations,
			'groups'          => $groups,
			'storage'         => $storage,
			'calls'           => Naber_Calls::stats(),
			'recent_users'    => $recent,
			'storage_ready'   => Naber_Settings::b2_ready(),
			'push_ready'      => Naber_Push::is_configured(),
			'devices'         => Naber_Push::device_count(),
			'push_last_error' => Naber_Push::last_error(),
			'turn_configured' => '' !== (string) Naber_Settings::get( 'turn_urls' ),
			'server'          => home_url(),
			'plugin_version'  => NABER_CHAT_VERSION,
			'email_domain'    => Naber_Auth::email_domain(),
		) );
	}

	public function admin_users( WP_REST_Request $request ) {
		$search = sanitize_text_field( (string) $request->get_param( 'search' ) );
		$args   = array( 'number' => 300, 'orderby' => 'registered', 'order' => 'DESC' );
		if ( '' !== $search ) {
			$args['search']         = '*' . $search . '*';
			$args['search_columns'] = array( 'user_login', 'display_name', 'user_email' );
		}

		$users = array();
		foreach ( get_users( $args ) as $user ) {
			$payload           = Naber_Auth::user_payload( $user, true );
			$payload['unread'] = Naber_Chat_Repo::unread_total( $user->ID );
			$users[]           = $payload;
		}
		return rest_ensure_response( array( 'users' => $users ) );
	}

	public function admin_update_user( WP_REST_Request $request ) {
		$target = (int) $request['id'];
		$user   = get_userdata( $target );
		if ( ! $user ) {
			return new WP_Error( 'naber_user_not_found', 'Kullanici bulunamadi.', array( 'status' => 404 ) );
		}
		if ( $target === get_current_user_id() ) {
			return new WP_Error( 'naber_self_action', 'Bu islemi kendi hesabinizda yapamazsiniz.', array( 'status' => 400 ) );
		}

		$banned = $request->get_param( 'banned' );
		if ( null !== $banned ) {
			Naber_Auth::set_banned( $target, rest_sanitize_boolean( $banned ), (string) $request->get_param( 'reason' ) );
		}

		$disabled = $request->get_param( 'disabled' );
		if ( null !== $disabled ) {
			if ( rest_sanitize_boolean( $disabled ) ) {
				update_user_meta( $target, 'naber_disabled', '1' );
				Naber_Auth::revoke_all_tokens( $target );
			} else {
				delete_user_meta( $target, 'naber_disabled' );
			}
		}

		$make_admin = $request->get_param( 'admin' );
		if ( null !== $make_admin ) {
			$wp_user = new WP_User( $target );
			if ( rest_sanitize_boolean( $make_admin ) ) {
				$wp_user->set_role( 'administrator' );
			} else {
				$wp_user->set_role( 'subscriber' );
			}
		}

		$display = $request->get_param( 'display_name' );
		if ( null !== $display && '' !== trim( (string) $display ) ) {
			wp_update_user( array( 'ID' => $target, 'display_name' => sanitize_text_field( (string) $display ) ) );
		}

		$password = (string) $request->get_param( 'password' );
		if ( strlen( $password ) >= 6 ) {
			wp_set_password( $password, $target );
			Naber_Auth::revoke_all_tokens( $target );
		}

		if ( rest_sanitize_boolean( $request->get_param( 'logout' ) ) ) {
			Naber_Auth::revoke_all_tokens( $target );
		}

		Naber_Auth::flush_payload_cache( $target );
		return rest_ensure_response( array( 'user' => Naber_Auth::user_payload( $target, true ) ) );
	}

	public function admin_delete_user( WP_REST_Request $request ) {
		$target = (int) $request['id'];
		if ( $target === get_current_user_id() ) {
			return new WP_Error( 'naber_self_action', 'Kendi hesabinizi silemezsiniz.', array( 'status' => 400 ) );
		}
		if ( ! get_userdata( $target ) ) {
			return new WP_Error( 'naber_user_not_found', 'Kullanici bulunamadi.', array( 'status' => 404 ) );
		}
		require_once ABSPATH . 'wp-admin/includes/user.php';
		wp_delete_user( $target );
		return rest_ensure_response( array( 'ok' => true ) );
	}

	public function admin_chats() {
		global $wpdb;
		$conversations = Naber_DB::table( 'conversations' );
		$messages      = Naber_DB::table( 'messages' );
		$members       = Naber_DB::table( 'members' );

		$rows = $wpdb->get_results(
			"SELECT c.*,
				(SELECT COUNT(*) FROM {$messages} m WHERE m.conversation_id = c.id) AS message_count,
				(SELECT COUNT(*) FROM {$members} me WHERE me.conversation_id = c.id) AS member_count
			 FROM {$conversations} c ORDER BY c.updated_at DESC LIMIT 100",
			ARRAY_A
		);

		$out = array();
		foreach ( (array) $rows as $row ) {
			$title = (string) $row['title'];
			if ( 'group' !== $row['type'] ) {
				$one   = Naber_Auth::user_payload( (int) $row['user_one'] );
				$two   = Naber_Auth::user_payload( (int) $row['user_two'] );
				$title = ( $one ? $one['display_name'] : '?' ) . ' - ' . ( $two ? $two['display_name'] : '?' );
			}
			$out[] = array(
				'id'            => (int) $row['id'],
				'type'          => (string) $row['type'],
				'title'         => $title,
				'member_count'  => (int) $row['member_count'],
				'message_count' => (int) $row['message_count'],
				'updated_at'    => Naber_Chat_Repo::ts( $row['updated_at'] ),
			);
		}
		return rest_ensure_response( array( 'chats' => $out ) );
	}

	public function admin_delete_chat( WP_REST_Request $request ) {
		global $wpdb;
		$conversation_id = (int) $request['id'];
		if ( ! Naber_Chat_Repo::get_conversation( $conversation_id ) ) {
			return new WP_Error( 'naber_chat_not_found', 'Sohbet bulunamadi.', array( 'status' => 404 ) );
		}
		$wpdb->delete( Naber_DB::table( 'messages' ), array( 'conversation_id' => $conversation_id ), array( '%d' ) );
		$wpdb->delete( Naber_DB::table( 'members' ), array( 'conversation_id' => $conversation_id ), array( '%d' ) );
		$wpdb->delete( Naber_DB::table( 'conversations' ), array( 'id' => $conversation_id ), array( '%d' ) );
		return rest_ensure_response( array( 'ok' => true ) );
	}

	/** Uygulama ici yonetim panelinde gosterilen sunucu ayarlari (sirlar haric). */
	public function admin_settings() {
		$all = Naber_Settings::all();
		return rest_ensure_response( array(
			'server'             => home_url(),
			'plugin_version'     => NABER_CHAT_VERSION,
			'bucket_name'        => (string) $all['b2_bucket_name'],
			'bucket_id'          => (string) $all['b2_bucket_id'],
			'key_id'             => Naber_Settings::mask( (string) $all['b2_key_id'] ),
			'path_prefix'        => (string) $all['b2_path_prefix'],
			'max_upload_mb'      => (int) $all['b2_max_upload_mb'],
			'link_ttl'           => (int) $all['b2_link_ttl'],
			'public_base_url'    => (string) $all['b2_public_base_url'],
			'turn_urls'          => (string) $all['turn_urls'],
			'stun_urls'          => (string) $all['stun_urls'],
			'fcm_project_id'     => (string) $all['fcm_project_id'],
			'metered_app'        => Naber_Turn::normalize_subdomain( $all['metered_subdomain'] ),
			'metered_ready'      => ( new Naber_Turn() )->metered_configured(),
			'email_domain'       => Naber_Auth::email_domain(),
			'allow_registration' => (bool) $all['allow_registration'],
			'storage_ready'      => Naber_Settings::b2_ready(),
			'push_ready'         => Naber_Push::is_configured(),
		) );
	}

	/** Yonetim panelinden TURN (Metered) yapilandirmasini sinar. */
	public function admin_turn_test() {
		$turn   = new Naber_Turn();
		$result = $turn->test();
		unset( $result['servers'] );
		return rest_ensure_response( $result );
	}

	/** Yoneticinin kendi cihazina deneme bildirimi gonderir, FCM hatasini aynen gosterir. */
	public function admin_push_test() {
		$user_id = get_current_user_id();
		$result  = Naber_Push::send_test( $user_id );
		return rest_ensure_response( $result );
	}

	public function admin_storage_test( WP_REST_Request $request ) {
		$overrides = array();
		foreach ( array( 'b2_key_id', 'b2_app_key', 'b2_bucket_name', 'b2_bucket_id', 'b2_path_prefix' ) as $field ) {
			$value = $request->get_param( $field );
			if ( null !== $value && '' !== $value ) {
				$overrides[ $field ] = sanitize_text_field( (string) $value );
			}
		}

		$write_test = $request->get_param( 'write_test' );
		$write_test = ( null === $write_test ) ? true : rest_sanitize_boolean( $write_test );

		$settings = array_merge( Naber_Settings::all(), $overrides );
		$b2       = new Naber_B2( $settings );
		$result   = $b2->test_connection( $write_test );

		if ( $result['ok'] && $result['bucket_id'] && empty( $overrides ) ) {
			Naber_Settings::update( array( 'b2_bucket_id' => $result['bucket_id'] ) );
		}

		return rest_ensure_response( $result );
	}
}
