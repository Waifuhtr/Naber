<?php
/**
 * WordPress yonetim ekrani: Backblaze B2 bucket bilgileri, TURN ve FCM ayarlari.
 * Bucket bilgileri buradan girilir; "Baglantiyi test et" dugmesi girilen
 * degerleri kaydetmeden once Backblaze uzerinde gercekten dener.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class Naber_Admin_Page {

	const PAGE  = 'naber-chat';
	const NONCE = 'naber_chat_settings';

	private static $instance = null;

	public static function instance() {
		if ( null === self::$instance ) {
			self::$instance = new self();
		}
		return self::$instance;
	}

	private function __construct() {
		add_action( 'admin_menu', array( $this, 'menu' ) );
		add_action( 'admin_post_naber_save_settings', array( $this, 'save' ) );
		add_action( 'wp_ajax_naber_test_b2', array( $this, 'ajax_test_b2' ) );
	}

	public function menu() {
		add_menu_page(
			'Naber Chat',
			'Naber Chat',
			'manage_options',
			self::PAGE,
			array( $this, 'render' ),
			'dashicons-format-chat',
			58
		);
	}

	private function field( $name, $label, $value, $type = 'text', $description = '', $errors = array() ) {
		$has_error = isset( $errors[ $name ] );
		?>
		<tr class="<?php echo $has_error ? 'naber-field-error' : ''; ?>">
			<th scope="row"><label for="<?php echo esc_attr( $name ); ?>"><?php echo esc_html( $label ); ?></label></th>
			<td>
				<?php if ( 'textarea' === $type ) : ?>
					<textarea id="<?php echo esc_attr( $name ); ?>" name="<?php echo esc_attr( $name ); ?>" rows="6" class="large-text code"><?php echo esc_textarea( $value ); ?></textarea>
				<?php else : ?>
					<input type="<?php echo esc_attr( $type ); ?>" id="<?php echo esc_attr( $name ); ?>" name="<?php echo esc_attr( $name ); ?>"
						value="<?php echo esc_attr( $value ); ?>" class="regular-text" autocomplete="off" />
				<?php endif; ?>
				<?php if ( $description ) : ?>
					<p class="description"><?php echo wp_kses_post( $description ); ?></p>
				<?php endif; ?>
				<?php if ( $has_error ) : ?>
					<p class="naber-error"><strong><?php echo esc_html( $errors[ $name ] ); ?></strong></p>
				<?php endif; ?>
			</td>
		</tr>
		<?php
	}

	public function render() {
		if ( ! current_user_can( 'manage_options' ) ) {
			wp_die( 'Yetkiniz yok.' );
		}

		$settings = Naber_Settings::all();
		$errors   = get_transient( 'naber_settings_errors_' . get_current_user_id() );
		$errors   = is_array( $errors ) ? $errors : array();
		delete_transient( 'naber_settings_errors_' . get_current_user_id() );
		$saved = isset( $_GET['naber_saved'] );
		?>
		<div class="wrap">
			<h1>Naber Chat</h1>

			<?php if ( $saved && ! $errors ) : ?>
				<div class="notice notice-success is-dismissible"><p>Ayarlar kaydedildi.</p></div>
			<?php elseif ( $errors ) : ?>
				<div class="notice notice-error"><p>Bazi alanlar kaydedilemedi, asagidaki uyarilara bakin.</p></div>
			<?php endif; ?>

			<style>
				.naber-error { color: #b32d2e; }
				.naber-field-error input { border-color: #b32d2e; }
				#naber-test-result { margin-top: 12px; max-width: 760px; }
				#naber-test-result li { margin: 4px 0; list-style: none; }
				#naber-test-result .ok::before { content: "\2714"; color: #1a7f37; margin-right: 6px; }
				#naber-test-result .fail::before { content: "\2718"; color: #b32d2e; margin-right: 6px; }
			</style>

			<form method="post" action="<?php echo esc_url( admin_url( 'admin-post.php' ) ); ?>" id="naber-settings-form">
				<input type="hidden" name="action" value="naber_save_settings" />
				<?php wp_nonce_field( self::NONCE ); ?>

				<h2>Backblaze B2 depolama</h2>
				<p>Gorseller WordPress sunucusunda degil, Backblaze B2 bucket'inda saklanir.
					Anahtarlar yalnizca burada tutulur, Android uygulamasina gonderilmez.</p>
				<table class="form-table" role="presentation">
					<?php
					$this->field( 'b2_key_id', 'Application Key ID', $settings['b2_key_id'], 'text', 'Backblaze panelinde <em>App Keys</em> bolumundeki <code>keyID</code>.', $errors );
					$this->field( 'b2_app_key', 'Application Key', '', 'password', $settings['b2_app_key'] ? 'Kayitli: <code>' . esc_html( Naber_Settings::mask( $settings['b2_app_key'] ) ) . '</code>. Degistirmek istemiyorsaniz bos birakin.' : 'Anahtar yalnizca olusturuldugu anda gosterilir; kaybederseniz yeni anahtar uretin.', $errors );
					$this->field( 'b2_bucket_name', 'Bucket adi', $settings['b2_bucket_name'], 'text', 'Ornek: <code>naber-medya</code>. Kucuk harf, rakam ve tire; 6-50 karakter.', $errors );
					$this->field( 'b2_bucket_id', 'Bucket ID (istege bagli)', $settings['b2_bucket_id'], 'text', 'Bos birakirsaniz baglanti testi otomatik bulup doldurur.', $errors );
					$this->field( 'b2_path_prefix', 'Klasor oneki', $settings['b2_path_prefix'], 'text', 'Bucket icinde dosyalarin yazilacagi klasor. Ornek: <code>naber/</code>', $errors );
					$this->field( 'b2_public_base_url', 'Genel erisim adresi (istege bagli)', $settings['b2_public_base_url'], 'text', 'Bucket <em>public</em> ise veya bir CDN kullaniyorsaniz: <code>https://f003.backblazeb2.com/file/bucket-adi</code>. Bos birakilirsa ozel bucket kabul edilip kisa omurlu imzali adres uretilir.', $errors );
					$this->field( 'b2_link_ttl', 'Imzali adres suresi (saniye)', $settings['b2_link_ttl'], 'number', '60 - 604800 arasi.', $errors );
					$this->field( 'b2_max_upload_mb', 'Azami dosya boyutu (MB)', $settings['b2_max_upload_mb'], 'number', '1 - 200 arasi.', $errors );
					?>
					<tr>
						<th scope="row">Baglanti testi</th>
						<td>
							<button type="button" class="button button-secondary" id="naber-test-b2">Baglantiyi test et</button>
							<label style="margin-left:12px"><input type="checkbox" id="naber-test-write" checked /> Deneme dosyasi yazip sil</label>
							<p class="description">Formdaki degerlerle deneme yapar; kaydetmeniz gerekmez.</p>
							<div id="naber-test-result"></div>
						</td>
					</tr>
				</table>

				<h2>Sesli arama (WebRTC)</h2>
				<p>WordPress yalnizca signaling yapar. TURN sunucusu harici bir servistir.</p>
				<table class="form-table" role="presentation">
					<?php
					$this->field( 'stun_urls', 'STUN adresleri', $settings['stun_urls'], 'text', 'Virgul veya bosluk ile ayirin.', $errors );
					$this->field( 'turn_urls', 'TURN adresleri', $settings['turn_urls'], 'text', 'Ornek: <code>turn:turn.ornek.com:3478?transport=udp</code>', $errors );
					$this->field( 'turn_username', 'TURN kullanici adi', $settings['turn_username'], 'text', '', $errors );
					$this->field( 'turn_credential', 'TURN sifresi', $settings['turn_credential'], 'password', $settings['turn_credential'] ? 'Kayitli. Degistirmek istemiyorsaniz bos birakin.' : '', $errors );
					?>
				</table>

				<h2>Bildirimler (Firebase)</h2>
				<table class="form-table" role="presentation">
					<?php
					$this->field( 'fcm_project_id', 'Firebase proje kimligi', $settings['fcm_project_id'], 'text', 'Firebase konsolundaki <code>project_id</code>.', $errors );
					$this->field( 'fcm_service_account', 'Servis hesabi JSON', $settings['fcm_service_account'] ? '(kayitli)' : '', 'textarea', 'Firebase > Proje ayarlari > Servis hesaplari > Yeni ozel anahtar. JSON icerigini yapistirin. "(kayitli)" yazisini degistirmezseniz mevcut deger korunur.', $errors );
					?>
					<tr>
						<th scope="row">Yeni kayitlar</th>
						<td>
							<label>
								<input type="checkbox" name="allow_registration" value="1" <?php checked( (int) $settings['allow_registration'], 1 ); ?> />
								Uygulama uzerinden yeni kullanici kaydina izin ver
							</label>
						</td>
					</tr>
				</table>

				<?php submit_button( 'Ayarlari kaydet' ); ?>
			</form>

			<script>
			(function () {
				var button = document.getElementById('naber-test-b2');
				if (!button) { return; }
				var box = document.getElementById('naber-test-result');
				button.addEventListener('click', function () {
					button.disabled = true;
					box.innerHTML = '<p>Test ediliyor...</p>';
					var form = document.getElementById('naber-settings-form');
					var data = new FormData();
					data.append('action', 'naber_test_b2');
					data.append('_ajax_nonce', '<?php echo esc_js( wp_create_nonce( 'naber_test_b2' ) ); ?>');
					['b2_key_id', 'b2_app_key', 'b2_bucket_name', 'b2_bucket_id', 'b2_path_prefix'].forEach(function (name) {
						data.append(name, form.elements[name] ? form.elements[name].value : '');
					});
					data.append('write_test', document.getElementById('naber-test-write').checked ? '1' : '0');

					fetch(ajaxurl, { method: 'POST', body: data, credentials: 'same-origin' })
						.then(function (r) { return r.json(); })
						.then(function (res) {
							button.disabled = false;
							var payload = res.data || res;
							var html = '<ul>';
							(payload.steps || []).forEach(function (step) {
								html += '<li class="' + (step.ok ? 'ok' : 'fail') + '"><strong>' + step.label + ':</strong> ' + step.message + '</li>';
							});
							html += '</ul>';
							html += '<div class="notice notice-' + (payload.ok ? 'success' : 'error') + ' inline"><p>' + (payload.message || '') + '</p></div>';
							if (payload.bucket_id) {
								var idField = form.elements['b2_bucket_id'];
								if (idField && !idField.value) { idField.value = payload.bucket_id; }
							}
							box.innerHTML = html;
						})
						.catch(function (err) {
							button.disabled = false;
							box.innerHTML = '<div class="notice notice-error inline"><p>Test istegi basarisiz: ' + err + '</p></div>';
						});
				});
			})();
			</script>
		</div>
		<?php
	}

	public function save() {
		if ( ! current_user_can( 'manage_options' ) ) {
			wp_die( 'Yetkiniz yok.' );
		}
		check_admin_referer( self::NONCE );

		$current = Naber_Settings::all();
		$input   = array(
			'b2_key_id'          => isset( $_POST['b2_key_id'] ) ? sanitize_text_field( wp_unslash( $_POST['b2_key_id'] ) ) : '',
			'b2_app_key'         => isset( $_POST['b2_app_key'] ) && '' !== $_POST['b2_app_key'] ? sanitize_text_field( wp_unslash( $_POST['b2_app_key'] ) ) : $current['b2_app_key'],
			'b2_bucket_name'     => isset( $_POST['b2_bucket_name'] ) ? sanitize_text_field( wp_unslash( $_POST['b2_bucket_name'] ) ) : '',
			'b2_bucket_id'       => isset( $_POST['b2_bucket_id'] ) ? sanitize_text_field( wp_unslash( $_POST['b2_bucket_id'] ) ) : '',
			'b2_path_prefix'     => isset( $_POST['b2_path_prefix'] ) ? sanitize_text_field( wp_unslash( $_POST['b2_path_prefix'] ) ) : 'naber/',
			'b2_public_base_url' => isset( $_POST['b2_public_base_url'] ) ? esc_url_raw( wp_unslash( $_POST['b2_public_base_url'] ) ) : '',
			'b2_link_ttl'        => isset( $_POST['b2_link_ttl'] ) ? (int) $_POST['b2_link_ttl'] : 3600,
			'b2_max_upload_mb'   => isset( $_POST['b2_max_upload_mb'] ) ? (int) $_POST['b2_max_upload_mb'] : 25,
		);

		$check  = Naber_Settings::validate_b2( $input );
		$values = $check['values'];

		// B2 alanlari bos birakilmissa (henuz kurulmadiysa) hata gostermeden kaydet.
		$b2_untouched = '' === $input['b2_key_id'] && '' === $input['b2_app_key'] && '' === $input['b2_bucket_name'];
		if ( $b2_untouched ) {
			$check['errors'] = array();
			$values          = array_merge( $values, array( 'b2_key_id' => '', 'b2_app_key' => '', 'b2_bucket_name' => '' ) );
		}

		if ( $check['errors'] ) {
			set_transient( 'naber_settings_errors_' . get_current_user_id(), $check['errors'], 60 );
			// Hatali B2 alanlarini eski degerleriyle birak.
			foreach ( array_keys( $check['errors'] ) as $bad ) {
				$values[ $bad ] = $current[ $bad ];
			}
		}

		$service_account = isset( $_POST['fcm_service_account'] ) ? trim( wp_unslash( $_POST['fcm_service_account'] ) ) : '';
		if ( '(kayitli)' === $service_account ) {
			$service_account = $current['fcm_service_account'];
		}

		$values['stun_urls']          = isset( $_POST['stun_urls'] ) ? sanitize_text_field( wp_unslash( $_POST['stun_urls'] ) ) : '';
		$values['turn_urls']          = isset( $_POST['turn_urls'] ) ? sanitize_text_field( wp_unslash( $_POST['turn_urls'] ) ) : '';
		$values['turn_username']      = isset( $_POST['turn_username'] ) ? sanitize_text_field( wp_unslash( $_POST['turn_username'] ) ) : '';
		$values['turn_credential']    = isset( $_POST['turn_credential'] ) && '' !== $_POST['turn_credential'] ? sanitize_text_field( wp_unslash( $_POST['turn_credential'] ) ) : $current['turn_credential'];
		$values['fcm_project_id']     = isset( $_POST['fcm_project_id'] ) ? sanitize_text_field( wp_unslash( $_POST['fcm_project_id'] ) ) : '';
		$values['fcm_service_account'] = $service_account;
		$values['allow_registration'] = isset( $_POST['allow_registration'] ) ? 1 : 0;

		Naber_Settings::update( $values );
		Naber_B2::forget_auth();

		wp_safe_redirect( add_query_arg( array( 'page' => self::PAGE, 'naber_saved' => 1 ), admin_url( 'admin.php' ) ) );
		exit;
	}

	/** Yonetim ekranindaki "Baglantiyi test et" dugmesi. */
	public function ajax_test_b2() {
		if ( ! current_user_can( 'manage_options' ) ) {
			wp_send_json_error( array( 'ok' => false, 'steps' => array(), 'message' => 'Yetkiniz yok.' ), 403 );
		}
		check_ajax_referer( 'naber_test_b2' );

		$current  = Naber_Settings::all();
		$settings = array_merge( $current, array(
			'b2_key_id'      => isset( $_POST['b2_key_id'] ) ? sanitize_text_field( wp_unslash( $_POST['b2_key_id'] ) ) : '',
			'b2_app_key'     => isset( $_POST['b2_app_key'] ) && '' !== $_POST['b2_app_key'] ? sanitize_text_field( wp_unslash( $_POST['b2_app_key'] ) ) : $current['b2_app_key'],
			'b2_bucket_name' => isset( $_POST['b2_bucket_name'] ) ? sanitize_text_field( wp_unslash( $_POST['b2_bucket_name'] ) ) : '',
			'b2_bucket_id'   => isset( $_POST['b2_bucket_id'] ) ? sanitize_text_field( wp_unslash( $_POST['b2_bucket_id'] ) ) : '',
			'b2_path_prefix' => isset( $_POST['b2_path_prefix'] ) ? sanitize_text_field( wp_unslash( $_POST['b2_path_prefix'] ) ) : 'naber/',
		) );

		$write_test = ! isset( $_POST['write_test'] ) || '1' === (string) $_POST['write_test'];

		$b2     = new Naber_B2( $settings );
		$result = $b2->test_connection( $write_test );

		// Bulunan bucket ID'si kaydedilmis ayarlarla ayniysa kalici olarak sakla.
		if ( $result['ok'] && $result['bucket_id'] && $settings['b2_key_id'] === $current['b2_key_id'] && $settings['b2_bucket_name'] === $current['b2_bucket_name'] ) {
			Naber_Settings::update( array( 'b2_bucket_id' => $result['bucket_id'] ) );
		}

		wp_send_json_success( $result );
	}
}
