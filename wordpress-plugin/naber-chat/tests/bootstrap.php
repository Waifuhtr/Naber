<?php
/**
 * Testler icin kucuk WordPress taklidi.
 * Amac: eklenti mantigini WordPress kurulumu olmadan calistirabilmek.
 */

define( 'ABSPATH', __DIR__ . '/' );
define( 'HOUR_IN_SECONDS', 3600 );
define( 'DAY_IN_SECONDS', 86400 );
define( 'NABER_CHAT_NS', 'naber/v1' );

class WP_Error {
	private $code;
	private $message;
	private $data;

	public function __construct( $code = '', $message = '', $data = array() ) {
		$this->code    = $code;
		$this->message = $message;
		$this->data    = $data;
	}

	public function get_error_code() {
		return $this->code;
	}

	public function get_error_message() {
		return $this->message;
	}

	public function get_error_data() {
		return $this->data;
	}
}

function is_wp_error( $thing ) {
	return $thing instanceof WP_Error;
}

function wp_json_encode( $data ) {
	return json_encode( $data );
}

function wp_generate_password( $length = 12, $special = true, $extra = true ) {
	$chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
	$out   = '';
	for ( $i = 0; $i < $length; $i++ ) {
		$out .= $chars[ random_int( 0, strlen( $chars ) - 1 ) ];
	}
	return $out;
}

$GLOBALS['naber_test_transients'] = array();
$GLOBALS['naber_test_options']    = array();

function get_transient( $key ) {
	return isset( $GLOBALS['naber_test_transients'][ $key ] ) ? $GLOBALS['naber_test_transients'][ $key ] : false;
}

function set_transient( $key, $value, $ttl = 0 ) {
	$GLOBALS['naber_test_transients'][ $key ] = $value;
	return true;
}

function delete_transient( $key ) {
	unset( $GLOBALS['naber_test_transients'][ $key ] );
	return true;
}

function get_option( $key, $default = false ) {
	return isset( $GLOBALS['naber_test_options'][ $key ] ) ? $GLOBALS['naber_test_options'][ $key ] : $default;
}

function update_option( $key, $value ) {
	$GLOBALS['naber_test_options'][ $key ] = $value;
	return true;
}

function sanitize_text_field( $value ) {
	return trim( strip_tags( (string) $value ) );
}

function trailingslashit( $value ) {
	return rtrim( (string) $value, '/\\' ) . '/';
}

function wp_strip_all_tags( $value ) {
	return trim( strip_tags( (string) $value ) );
}

function email_exists( $email ) {
	return isset( $GLOBALS['naber_test_emails'] ) && in_array( strtolower( $email ), $GLOBALS['naber_test_emails'], true );
}

$GLOBALS['naber_test_emails'] = array();

require_once dirname( __DIR__ ) . '/includes/class-naber-settings.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-b2.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-turn.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-media.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-db.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-auth.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-chat-repo.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-pokes.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-reactions.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-blocks.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-polls.php';
require_once dirname( __DIR__ ) . '/includes/class-naber-rest.php';

/**
 * Backblaze API'sini taklit eden HTTP katmani.
 * Testler gercek ag baglantisi kurmaz.
 */
class Naber_Fake_B2_Http {

	public $calls        = array();
	public $auth_status  = 200;
	public $capabilities = array( 'listBuckets', 'readFiles', 'writeFiles', 'deleteFiles' );
	public $restricted_bucket = '';
	public $buckets      = array();
	public $upload_status = 200;
	public $delete_status = 200;

	public function __construct( $buckets = array( array( 'bucketId' => 'a1b2c3d4e5f60718293a4b5c', 'bucketName' => 'naber-medya', 'bucketType' => 'allPrivate' ) ) ) {
		$this->buckets = $buckets;
	}

	public function __invoke( $method, $url, $headers = array(), $body = null ) {
		$this->calls[] = array( 'method' => $method, 'url' => $url, 'headers' => $headers );

		if ( false !== strpos( $url, 'b2_authorize_account' ) ) {
			if ( 200 !== $this->auth_status ) {
				return $this->fail( $this->auth_status, 'unauthorized' );
			}
			$storage = array(
				'apiUrl'       => 'https://api003.backblazeb2.com',
				'downloadUrl'  => 'https://f003.backblazeb2.com',
				'capabilities' => $this->capabilities,
				'bucketId'     => '',
				'bucketName'   => '',
			);
			if ( '' !== $this->restricted_bucket ) {
				$storage['bucketName'] = $this->restricted_bucket;
				$storage['bucketId']   = 'restricted00000000000001';
			}
			return $this->ok( array(
				'accountId'          => 'acc123',
				'authorizationToken' => 'AUTH_TOKEN',
				'apiInfo'            => array( 'storageApi' => $storage ),
			) );
		}

		if ( false !== strpos( $url, 'b2_list_buckets' ) ) {
			return $this->ok( array( 'buckets' => $this->buckets ) );
		}

		if ( false !== strpos( $url, 'b2_get_upload_url' ) ) {
			return $this->ok( array(
				'uploadUrl'          => 'https://pod-000.backblaze.com/b2api/v3/b2_upload_file/xyz',
				'authorizationToken' => 'UPLOAD_TOKEN',
			) );
		}

		if ( false !== strpos( $url, 'b2_upload_file' ) ) {
			if ( 200 !== $this->upload_status ) {
				return $this->fail( $this->upload_status, 'upload denied' );
			}
			return $this->ok( array(
				'fileId'        => 'file-123',
				'fileName'      => rawurldecode( isset( $headers['X-Bz-File-Name'] ) ? $headers['X-Bz-File-Name'] : 'test.txt' ),
				'contentLength' => strlen( (string) $body ),
				'contentType'   => isset( $headers['Content-Type'] ) ? $headers['Content-Type'] : 'text/plain',
			) );
		}

		if ( false !== strpos( $url, 'b2_delete_file_version' ) ) {
			if ( 200 !== $this->delete_status ) {
				return $this->fail( $this->delete_status, 'delete denied' );
			}
			return $this->ok( array( 'fileId' => 'file-123' ) );
		}

		if ( false !== strpos( $url, 'b2_get_download_authorization' ) ) {
			return $this->ok( array( 'authorizationToken' => 'DOWNLOAD_TOKEN' ) );
		}

		return $this->fail( 404, 'unknown endpoint' );
	}

	private function ok( $body ) {
		return array( 'ok' => true, 'status' => 200, 'body' => $body, 'error' => '' );
	}

	private function fail( $status, $message ) {
		return array( 'ok' => false, 'status' => $status, 'body' => array( 'message' => $message ), 'error' => $message );
	}
}

/** Cok kucuk test kosucusu. */
class Naber_Tests {

	private static $passed = 0;
	private static $failed = 0;
	private static $group  = '';

	public static function group( $name ) {
		self::$group = $name;
		echo "\n== {$name} ==\n";
	}

	public static function ok( $condition, $label ) {
		if ( $condition ) {
			self::$passed++;
			echo "  [GECTI] {$label}\n";
		} else {
			self::$failed++;
			echo "  [KALDI] {$label}\n";
		}
	}

	public static function equals( $expected, $actual, $label ) {
		$same = $expected === $actual;
		if ( ! $same ) {
			$label .= sprintf( ' (beklenen: %s, gelen: %s)', var_export( $expected, true ), var_export( $actual, true ) );
		}
		self::ok( $same, $label );
	}

	/**
	 * Ozet yazar ve surecten cikar.
	 *
	 * Cikis kodu onemli: surekli tumlestirme (CI) yalnizca buna bakar.
	 * Yalnizca deger dondurulseydi basarisiz test bile "gecti" sayilirdi.
	 */
	public static function summary() {
		$total = self::$passed + self::$failed;
		echo "\n------------------------------------------\n";
		echo sprintf( "Toplam: %d  Gecti: %d  Kaldi: %d\n", $total, self::$passed, self::$failed );
		exit( self::$failed === 0 ? 0 : 1 );
	}
}
