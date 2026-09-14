<?php
/**
 * REST API: /wp-json/naber/v1/...
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_REST {

	public function register_routes() {
		$ns = NABER_CHAT_NS;

		register_rest_route( $ns, '/register', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'register_user' ),
			'permission_callback' => '__return_true',
		) );

		register_rest_route( $ns, '/login', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'login' ),
			'permission_callback' => '__return_true',
		) );

		register_rest_route( $ns, '/logout', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'logout' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/me', array(
			array(
				'methods'             => 'GET',
				'callback'            => array( $this, 'get_me' ),
				'permission_callback' => array( $this, 'require_user' ),
			),
			array(
				'methods'             => 'POST',
				'callback'            => array( $this, 'update_me' ),
				'permission_callback' => array( $this, 'require_user' ),
			),
		) );

		register_rest_route( $ns, '/users', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'list_users' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/chats', array(
			array(
				'methods'             => 'GET',
				'callback'            => array( $this, 'list_chats' ),
				'permission_callback' => array( $this, 'require_user' ),
			),
			array(
				'methods'             => 'POST',
				'callback'            => array( $this, 'open_chat' ),
				'permission_callback' => array( $this, 'require_user' ),
			),
		) );

		register_rest_route( $ns, '/chats/(?P<id>\d+)/messages', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'list_messages' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/chats/(?P<id>\d+)/read', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'mark_read' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/chats/(?P<id>\d+)/typing', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'typing' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/messages', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'send_message' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/media/upload-url', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'media_upload_url' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/media/complete', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'media_complete' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/media/upload', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'media_proxy_upload' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/media/(?P<id>\d+)/url', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'media_url' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/devices', array(
			array(
				'methods'             => 'POST',
				'callback'            => array( $this, 'register_device' ),
				'permission_callback' => array( $this, 'require_user' ),
			),
			array(
				'methods'             => 'DELETE',
				'callback'            => array( $this, 'delete_device' ),
				'permission_callback' => array( $this, 'require_user' ),
			),
		) );

		register_rest_route( $ns, '/events', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'events' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/ice-servers', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'ice_servers' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/calls', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'call_history' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/calls/start', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'call_start' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/calls/(?P<id>\d+)/(?P<action>accept|reject|end)', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'call_action' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/calls/(?P<id>\d+)/signal', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'call_signal' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		register_rest_route( $ns, '/calls/(?P<id>\d+)/signals', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'call_signals' ),
			'permission_callback' => array( $this, 'require_user' ),
		) );

		// --- Yonetim uclari: her biri sunucu tarafinda rol kontrolunden gecer. ---
		register_rest_route( $ns, '/admin/stats', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'admin_stats' ),
			'permission_callback' => array( $this, 'require_admin' ),
		) );

		register_rest_route( $ns, '/admin/users', array(
			'methods'             => 'GET',
			'callback'            => array( $this, 'admin_users' ),
			'permission_callback' => array( $this, 'require_admin' ),
		) );

		register_rest_route( $ns, '/admin/users/(?P<id>\d+)', array(
			array(
				'methods'             => 'POST',
				'callback'            => array( $this, 'admin_update_user' ),
				'permission_callback' => array( $this, 'require_admin' ),
			),
			array(
				'methods'             => 'DELETE',
				'callback'            => array( $this, 'admin_delete_user' ),
				'permission_callback' => array( $this, 'require_admin' ),
			),
		) );

		register_rest_route( $ns, '/admin/storage/test', array(
			'methods'             => 'POST',
			'callback'            => array( $this, 'admin_storage_test' ),
			'permission_callback' => array( $this, 'require_admin' ),
		) );
	}

	// ------------------------------------------------------------------
	// Yetki kontrolleri
	// ------------------------------------------------------------------

	public function require_user() {
		$user_id = get_current_user_id();
		if ( ! $user_id ) {
			return new WP_Error( 'naber_unauthorized', 'Oturum acmaniz gerekiyor.', array( 'status' => 401 ) );
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
		$email    = sanitize_email( (string) $request->get_param( 'email' ) );

		if ( strlen( $username ) < 3 ) {
			return new WP_Error( 'naber_bad_username', 'Kullanici adi en az 3 karakter olmali.', array( 'status' => 400 ) );
		}
		if ( strlen( $password ) < 6 ) {
			return new WP_Error( 'naber_bad_password', 'Sifre en az 6 karakter olmali.', array( 'status' => 400 ) );
		}
		if ( username_exists( $username ) ) {
			return new WP_Error( 'naber_username_taken', 'Bu kullanici adi zaten alinmis.', array( 'status' => 409 ) );
		}
		if ( '' === $email ) {
			$email = $username . '@naber.local';
		}
		if ( email_exists( $email ) ) {
			return new WP_Error( 'naber_email_taken', 'Bu e-posta zaten kayitli.', array( 'status' => 409 ) );
		}

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
		$username = (string) $request->get_param( 'username' );
		$password = (string) $request->get_param( 'password' );

		$user = wp_authenticate( $username, $password );
		if ( is_wp_error( $user ) ) {
			return new WP_Error( 'naber_bad_credentials', 'Kullanici adi veya sifre hatali.', array( 'status' => 401 ) );
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
				update_user_meta( $user_id, 'naber_avatar_url', Naber_Media::url_for( $media ) );
				update_user_meta( $user_id, 'naber_avatar_media_id', $media_id );
			}
		}

		return rest_ensure_response( array( 'user' => Naber_Auth::user_payload( $user_id, true ) ) );
	}

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
			$args['search_columns'] = array( 'user_login', 'display_name', 'user_nicename' );
		}

		$users = array();
		foreach ( get_users( $args ) as $user ) {
			if ( Naber_Auth::is_disabled( $user->ID ) ) {
				continue;
			}
			$users[] = Naber_Auth::user_payload( $user );
		}
		return rest_ensure_response( array( 'users' => $users ) );
	}

	// ------------------------------------------------------------------
	// Sohbet & mesaj
	// ------------------------------------------------------------------

	public function list_chats() {
		return rest_ensure_response( array(
			'chats'        => Naber_Chat_Repo::list_for_user( get_current_user_id() ),
			'unread_total' => Naber_Chat_Repo::unread_total( get_current_user_id() ),
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
			'peer' => Naber_Auth::user_payload( $peer_id ),
		) );
	}

	/** Sohbete erisim kontrolu: kullanici katilimci degilse 403. */
	private function authorized_conversation( $conversation_id ) {
		$conversation = Naber_Chat_Repo::get_conversation( $conversation_id );
		if ( ! $conversation ) {
			return new WP_Error( 'naber_chat_not_found', 'Sohbet bulunamadi.', array( 'status' => 404 ) );
		}
		if ( ! Naber_Chat_Repo::is_participant( $conversation, get_current_user_id() ) ) {
			return new WP_Error( 'naber_forbidden', 'Bu sohbete erisim yetkiniz yok.', array( 'status' => 403 ) );
		}
		return $conversation;
	}

	public function list_messages( WP_REST_Request $request ) {
		$conversation = $this->authorized_conversation( (int) $request['id'] );
		if ( is_wp_error( $conversation ) ) {
			return $conversation;
		}
		$messages = Naber_Chat_Repo::messages( (int) $conversation['id'], array(
			'limit'  => (int) $request->get_param( 'limit' ),
			'before' => (int) $request->get_param( 'before' ),
			'after'  => (int) $request->get_param( 'after' ),
		) );
		$peer_id = Naber_Chat_Repo::other_user( $conversation, get_current_user_id() );
		return rest_ensure_response( array(
			'messages' => $messages,
			'peer'     => Naber_Auth::user_payload( $peer_id ),
			'typing'   => Naber_Chat_Repo::typing_state( (int) $conversation['id'], $peer_id ),
		) );
	}

	public function send_message( WP_REST_Request $request ) {
		$user_id  = get_current_user_id();
		$type     = sanitize_key( (string) $request->get_param( 'type' ) );
		$type     = in_array( $type, array( 'text', 'image' ), true ) ? $type : 'text';
		$body     = (string) $request->get_param( 'body' );
		$media_id = (int) $request->get_param( 'media_id' );
		$client   = sanitize_text_field( (string) $request->get_param( 'client_id' ) );

		$conversation_id = (int) $request->get_param( 'conversation_id' );
		if ( $conversation_id > 0 ) {
			$conversation = $this->authorized_conversation( $conversation_id );
			if ( is_wp_error( $conversation ) ) {
				return $conversation;
			}
			$receiver_id = Naber_Chat_Repo::other_user( $conversation, $user_id );
		} else {
			$receiver_id = (int) $request->get_param( 'receiver_id' );
			if ( $receiver_id <= 0 || ! get_userdata( $receiver_id ) ) {
				return new WP_Error( 'naber_user_not_found', 'Alici bulunamadi.', array( 'status' => 404 ) );
			}
			$conversation_id = Naber_Chat_Repo::ensure_conversation( $user_id, $receiver_id );
			if ( is_wp_error( $conversation_id ) ) {
				return $conversation_id;
			}
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

		$message = Naber_Chat_Repo::insert_message( $conversation_id, $user_id, $receiver_id, $type, $body, $media_id, $client );

		$sender  = Naber_Auth::user_payload( $user_id );
		$preview = 'image' === $type ? 'Fotograf' : wp_trim_words( $body, 12, '...' );
		Naber_Push::send_to_user(
			$receiver_id,
			array( 'title' => $sender['display_name'], 'body' => $preview ),
			array(
				'type'            => 'message',
				'conversation_id' => $conversation_id,
				'message_id'      => $message['id'],
				'sender_id'       => $user_id,
				'sender_name'     => $sender['display_name'],
				'preview'         => $preview,
			)
		);

		return rest_ensure_response( array( 'message' => $message ) );
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
		Naber_Chat_Repo::set_typing( (int) $conversation['id'], get_current_user_id() );
		return rest_ensure_response( array( 'ok' => true ) );
	}

	// ------------------------------------------------------------------
	// Medya
	// ------------------------------------------------------------------

	public function media_upload_url( WP_REST_Request $request ) {
		$mime = strtolower( sanitize_text_field( (string) $request->get_param( 'mime' ) ) );
		$size = (int) $request->get_param( 'size' );

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
			(int) $request->get_param( 'height' )
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

	/** Dogrudan yukleme calismazsa dosya WordPress uzerinden B2'ye aktarilir. */
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

		$user_id   = get_current_user_id();
		$file_name = Naber_Media::build_file_name( $user_id, $mime );
		$content   = file_get_contents( $file['tmp_name'] );
		$b2        = new Naber_B2();
		$uploaded  = $b2->upload( $file_name, $content, $mime );
		if ( is_wp_error( $uploaded ) ) {
			return $uploaded;
		}

		$size  = getimagesize( $file['tmp_name'] );
		$media_id = Naber_Media::create_pending( $user_id, $file_name, $mime, strlen( $content ), $size ? (int) $size[0] : 0, $size ? (int) $size[1] : 0 );
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
		// Sahibi ya da bu medyanin gonderildigi sohbetin katilimcisi gorebilir.
		$allowed = (int) $media['owner_id'] === $user_id;
		if ( ! $allowed ) {
			$allowed = (bool) $wpdb->get_var(
				$wpdb->prepare(
					'SELECT COUNT(*) FROM ' . Naber_DB::table( 'messages' ) . ' WHERE media_id = %d AND (sender_id = %d OR receiver_id = %d)',
					(int) $media['id'],
					$user_id,
					$user_id
				)
			);
		}
		if ( ! $allowed ) {
			return new WP_Error( 'naber_forbidden', 'Bu medyaya erisim yetkiniz yok.', array( 'status' => 403 ) );
		}
		return rest_ensure_response( array( 'media' => Naber_Media::payload( $media ) ) );
	}

	// ------------------------------------------------------------------
	// Cihaz / bildirim
	// ------------------------------------------------------------------

	public function register_device( WP_REST_Request $request ) {
		$token = sanitize_text_field( (string) $request->get_param( 'token' ) );
		if ( '' === $token ) {
			return new WP_Error( 'naber_no_token', 'Cihaz jetonu gerekli.', array( 'status' => 400 ) );
		}
		Naber_Push::register_device( get_current_user_id(), $token, sanitize_key( (string) $request->get_param( 'platform' ) ) ?: 'android' );
		return rest_ensure_response( array( 'ok' => true ) );
	}

	public function delete_device( WP_REST_Request $request ) {
		Naber_Push::unregister_device( sanitize_text_field( (string) $request->get_param( 'token' ) ) );
		return rest_ensure_response( array( 'ok' => true ) );
	}

	// ------------------------------------------------------------------
	// Canli olay akisi (long-polling)
	// WordPress hostinglerinde WebSocket calismadigi icin, tek baglantida
	// en fazla ~25 saniye bekleyen hafif bir uzun yoklama kullanilir.
	// ------------------------------------------------------------------

	public function events( WP_REST_Request $request ) {
		$user_id     = get_current_user_id();
		$since_msg   = (int) $request->get_param( 'since_message_id' );
		$since_sig   = (int) $request->get_param( 'since_signal_id' );
		$since_read  = (int) $request->get_param( 'since_read' );
		$wait        = max( 0, min( 25, (int) $request->get_param( 'wait' ) ) );
		$conv_id     = (int) $request->get_param( 'conversation_id' );
		$deadline    = time() + $wait;

		if ( $since_read <= 0 ) {
			$since_read = time() - 60;
		}

		do {
			Naber_Auth::touch_presence( $user_id );

			$messages = Naber_Chat_Repo::messages_since( $user_id, $since_msg );
			$signals  = Naber_Calls::signals_for( $user_id, $since_sig );
			$incoming = Naber_Calls::active_incoming( $user_id );
			$receipts = Naber_Chat_Repo::read_receipts( $user_id, $since_read );

			$typing = null;
			if ( $conv_id > 0 ) {
				$conversation = Naber_Chat_Repo::get_conversation( $conv_id );
				if ( $conversation && Naber_Chat_Repo::is_participant( $conversation, $user_id ) ) {
					$peer   = Naber_Chat_Repo::other_user( $conversation, $user_id );
					$typing = array(
						'conversation_id' => $conv_id,
						'user_id'         => $peer,
						'typing'          => Naber_Chat_Repo::typing_state( $conv_id, $peer ),
						'online'          => Naber_Auth::is_online( $peer ),
					);
				}
			}

			$has_data = $messages || $signals || $incoming || $receipts || ( $typing && $typing['typing'] );
			if ( $has_data || time() >= $deadline ) {
				return rest_ensure_response( array(
					'messages'         => $messages,
					'signals'          => $signals,
					'incoming_call'    => $incoming,
					'read_receipts'    => $receipts,
					'typing'           => $typing,
					'unread_total'     => Naber_Chat_Repo::unread_total( $user_id ),
					'server_time'      => time(),
					'since_message_id' => $messages ? (int) $messages[ count( $messages ) - 1 ]['id'] : $since_msg,
					'since_signal_id'  => $signals ? (int) $signals[ count( $signals ) - 1 ]['id'] : $since_sig,
				) );
			}

			// Uzun yoklama sirasinda veritabani baglantisini mesgul etmemek icin kisa uyku.
			usleep( 1500000 );
		} while ( true );
	}

	public function ice_servers() {
		return rest_ensure_response( array( 'ice_servers' => Naber_Settings::ice_servers() ) );
	}

	// ------------------------------------------------------------------
	// Arama
	// ------------------------------------------------------------------

	public function call_start( WP_REST_Request $request ) {
		$callee_id = (int) $request->get_param( 'user_id' );
		$user_id   = get_current_user_id();
		if ( $callee_id <= 0 || $callee_id === $user_id || ! get_userdata( $callee_id ) ) {
			return new WP_Error( 'naber_user_not_found', 'Aranacak kullanici bulunamadi.', array( 'status' => 404 ) );
		}

		$call = Naber_Calls::start( $user_id, $callee_id );
		if ( is_wp_error( $call ) ) {
			return $call;
		}

		$caller = Naber_Auth::user_payload( $user_id );
		Naber_Push::send_to_user(
			$callee_id,
			array( 'title' => $caller['display_name'], 'body' => 'Sesli arama' ),
			array(
				'type'        => 'call',
				'call_id'     => $call['id'],
				'caller_id'   => $user_id,
				'caller_name' => $caller['display_name'],
				'caller_avatar' => (string) $caller['avatar'],
			),
			true
		);

		return rest_ensure_response( array(
			'call'        => Naber_Calls::payload( $call ),
			'ice_servers' => Naber_Settings::ice_servers(),
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

	public function call_action( WP_REST_Request $request ) {
		$call = $this->authorized_call( (int) $request['id'] );
		if ( is_wp_error( $call ) ) {
			return $call;
		}

		$action  = (string) $request['action'];
		$user_id = get_current_user_id();

		if ( 'accept' === $action ) {
			if ( (int) $call['callee_id'] !== $user_id ) {
				return new WP_Error( 'naber_forbidden', 'Aramayi yalnizca aranan kisi kabul edebilir.', array( 'status' => 403 ) );
			}
			$call = Naber_Calls::set_status( (int) $call['id'], 'active' );
		} elseif ( 'reject' === $action ) {
			if ( (int) $call['callee_id'] !== $user_id ) {
				return new WP_Error( 'naber_forbidden', 'Aramayi yalnizca aranan kisi reddedebilir.', array( 'status' => 403 ) );
			}
			$call = Naber_Calls::set_status( (int) $call['id'], 'rejected', 'declined' );
		} else {
			$reason = 'ringing' === $call['status'] && (int) $call['caller_id'] === $user_id ? 'cancelled' : 'hangup';
			$status = 'ringing' === $call['status'] && (int) $call['caller_id'] === $user_id ? 'missed' : 'ended';
			$call   = Naber_Calls::set_status( (int) $call['id'], $status, $reason );
		}

		$other = (int) $call['caller_id'] === $user_id ? (int) $call['callee_id'] : (int) $call['caller_id'];
		Naber_Calls::add_signal( (int) $call['id'], $user_id, $other, 'state', wp_json_encode( array( 'status' => $call['status'] ) ) );
		Naber_Calls::cleanup_signals();

		return rest_ensure_response( array( 'call' => Naber_Calls::payload( $call ) ) );
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
		$other   = (int) $call['caller_id'] === $user_id ? (int) $call['callee_id'] : (int) $call['caller_id'];
		$id      = Naber_Calls::add_signal( (int) $call['id'], $user_id, $other, $type, $payload );

		return rest_ensure_response( array( 'id' => $id ) );
	}

	public function call_signals( WP_REST_Request $request ) {
		$call = $this->authorized_call( (int) $request['id'] );
		if ( is_wp_error( $call ) ) {
			return $call;
		}
		return rest_ensure_response( array(
			'signals' => Naber_Calls::signals_for( get_current_user_id(), (int) $request->get_param( 'since' ), (int) $call['id'] ),
			'call'    => Naber_Calls::payload( $call ),
		) );
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
		$conversations  = (int) $wpdb->get_var( 'SELECT COUNT(*) FROM ' . Naber_DB::table( 'conversations' ) );

		$recent = array();
		foreach ( get_users( array( 'number' => 10, 'orderby' => 'registered', 'order' => 'DESC' ) ) as $user ) {
			$recent[] = Naber_Auth::user_payload( $user, true );
		}

		$online = 0;
		foreach ( get_users( array( 'number' => 200, 'fields' => 'ID' ) ) as $uid ) {
			if ( Naber_Auth::is_online( $uid ) ) {
				$online++;
			}
		}

		return rest_ensure_response( array(
			'users'          => array(
				'total'  => (int) count_users()['total_users'],
				'online' => $online,
			),
			'messages'       => array(
				'total' => $total_messages,
				'today' => $today,
			),
			'conversations'  => $conversations,
			'storage'        => $storage,
			'calls'          => Naber_Calls::stats(),
			'recent_users'   => $recent,
			'storage_ready'  => Naber_Settings::b2_ready(),
			'push_ready'     => Naber_Push::is_configured(),
			'turn_configured'=> '' !== (string) Naber_Settings::get( 'turn_urls' ),
		) );
	}

	public function admin_users() {
		$users = array();
		foreach ( get_users( array( 'number' => 200, 'orderby' => 'registered', 'order' => 'DESC' ) ) as $user ) {
			$payload            = Naber_Auth::user_payload( $user, true );
			$payload['unread']  = Naber_Chat_Repo::unread_total( $user->ID );
			$users[]            = $payload;
		}
		return rest_ensure_response( array( 'users' => $users ) );
	}

	public function admin_update_user( WP_REST_Request $request ) {
		$target = (int) $request['id'];
		$user   = get_userdata( $target );
		if ( ! $user ) {
			return new WP_Error( 'naber_user_not_found', 'Kullanici bulunamadi.', array( 'status' => 404 ) );
		}

		$disabled = $request->get_param( 'disabled' );
		if ( null !== $disabled ) {
			if ( $target === get_current_user_id() ) {
				return new WP_Error( 'naber_self_action', 'Kendi hesabinizi devre disi birakamazsiniz.', array( 'status' => 400 ) );
			}
			if ( rest_sanitize_boolean( $disabled ) ) {
				update_user_meta( $target, 'naber_disabled', '1' );
				delete_user_meta( $target, Naber_Auth::META_TOKENS );
			} else {
				delete_user_meta( $target, 'naber_disabled' );
			}
		}

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

	/**
	 * Uygulama icindeki yonetim panelinden Backblaze ayarlarini sinar.
	 * Istege bagli olarak gonderilen degerlerle deneme yapilabilir
	 * (kaydetmeden once "dogru mu?" kontrolu).
	 */
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

		// Test sirasinda bulunan bucket ID'sini kalici olarak sakla.
		if ( $result['ok'] && $result['bucket_id'] && empty( $overrides ) ) {
			Naber_Settings::update( array( 'b2_bucket_id' => $result['bucket_id'] ) );
		}

		return rest_ensure_response( $result );
	}
}
