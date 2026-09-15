<?php
/**
 * Naber adresleri (yapay e-posta), grup yetkileri ve sohbet anahtarlari testleri.
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-users-groups.php
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Settings::set_override( array( 'email_domain' => 'naber.com' ) );

// ------------------------------------------------- yapay e-posta uretimi
Naber_Tests::group( 'Naber adresi (kullaniciadi@naber.com)' );

Naber_Tests::equals( 'mehmet', Naber_Auth::email_local_part( 'mehmet' ), 'Duz kullanici adi oldugu gibi kullaniliyor' );
Naber_Tests::equals( 'aysegul', Naber_Auth::email_local_part( 'Ayşegül' ), 'Turkce karakterler sadelestiriliyor' );
Naber_Tests::equals( 'cigdem_01', Naber_Auth::email_local_part( 'Çiğdem_01' ), 'Buyuk harf ve Turkce karakter birlikte cozuluyor' );
Naber_Tests::equals( 'test.user', Naber_Auth::email_local_part( 'test.user' ), 'Nokta korunuyor' );
Naber_Tests::equals( 'adsoyad', Naber_Auth::email_local_part( 'ad soyad' ), 'Bosluklar atiliyor' );
Naber_Tests::equals( 'kullanici', Naber_Auth::email_local_part( '..kullanici..' ), 'Bastaki/sondaki noktalar temizleniyor' );
Naber_Tests::equals( 'naber', Naber_Auth::email_local_part( '###' ), 'Tamamen gecersiz ad icin yedek deger uretiliyor' );
Naber_Tests::equals( 40, strlen( Naber_Auth::email_local_part( str_repeat( 'a', 80 ) ) ), 'Cok uzun adlar 40 karaktere kisaltiliyor' );

$taken = array( 'mert@naber.com', 'mert2@naber.com' );
$exists = function ( $candidate ) use ( $taken ) {
	return in_array( strtolower( $candidate ), $taken, true );
};

Naber_Tests::equals( 'zeynep@naber.com', Naber_Auth::build_naber_email( 'zeynep', $exists ), 'Bos adres dogrudan veriliyor' );
Naber_Tests::equals( 'mert3@naber.com', Naber_Auth::build_naber_email( 'mert', $exists ), 'Dolu adreslerde sayi ekleniyor' );
Naber_Tests::equals( 'aysegul@naber.com', Naber_Auth::build_naber_email( 'Ayşegül', $exists ), 'Turkce adlardan gecerli adres uretiliyor' );
Naber_Tests::ok( is_email_like( Naber_Auth::build_naber_email( 'kullanici adi!', $exists ) ), 'Uretilen adres gecerli bicimde' );

Naber_Tests::ok( Naber_Auth::is_naber_email( 'ali@naber.com' ), 'Naber adresi taniniyor' );
Naber_Tests::ok( ! Naber_Auth::is_naber_email( 'ali@gmail.com' ), 'Disaridan e-posta Naber adresi sayilmiyor' );

Naber_Settings::set_override( array( 'email_domain' => 'sohbet.app' ) );
Naber_Tests::equals( 'veli@sohbet.app', Naber_Auth::build_naber_email( 'veli', $exists ), 'Alan adi ayarlardan okunuyor' );
Naber_Settings::set_override( array( 'email_domain' => 'naber.com' ) );

// ------------------------------------------------- grup yetkileri
Naber_Tests::group( 'Grup yonetici yetkileri' );

Naber_Tests::ok( Naber_Chat_Repo::can_manage( 'owner' ), 'Grup sahibi yonetebilir' );
Naber_Tests::ok( Naber_Chat_Repo::can_manage( 'admin' ), 'Yonetici yonetebilir' );
Naber_Tests::ok( ! Naber_Chat_Repo::can_manage( 'member' ), 'Duz uye yonetemez' );
Naber_Tests::ok( ! Naber_Chat_Repo::can_manage( '' ), 'Uye olmayan yonetemez' );

Naber_Tests::ok( Naber_Chat_Repo::can_act_on( 'owner', 'admin' ), 'Sahip, yoneticiye mudahale edebilir' );
Naber_Tests::ok( Naber_Chat_Repo::can_act_on( 'owner', 'member' ), 'Sahip, uyeye mudahale edebilir' );
Naber_Tests::ok( ! Naber_Chat_Repo::can_act_on( 'owner', 'owner' ), 'Sahip, baska bir sahibe mudahale edemez' );
Naber_Tests::ok( Naber_Chat_Repo::can_act_on( 'admin', 'member' ), 'Yonetici, uyeye mudahale edebilir' );
Naber_Tests::ok( ! Naber_Chat_Repo::can_act_on( 'admin', 'admin' ), 'Yonetici, baska yoneticiye mudahale edemez' );
Naber_Tests::ok( ! Naber_Chat_Repo::can_act_on( 'admin', 'owner' ), 'Yonetici, grup sahibine mudahale edemez' );
Naber_Tests::ok( ! Naber_Chat_Repo::can_act_on( 'member', 'member' ), 'Duz uye kimseye mudahale edemez' );
Naber_Tests::ok( ! Naber_Chat_Repo::can_act_on( '', 'member' ), 'Grup disindan mudahale edilemez' );

// ------------------------------------------------- sohbet anahtari
Naber_Tests::group( 'Birebir sohbet anahtari' );

Naber_Tests::equals( 'd:3:7', Naber_DB::direct_key( 3, 7 ), 'Anahtar kucukten buyuge siralaniyor' );
Naber_Tests::equals( Naber_DB::direct_key( 7, 3 ), Naber_DB::direct_key( 3, 7 ), 'Sira degisse de ayni anahtar uretiliyor (tek sohbet)' );
Naber_Tests::ok( Naber_DB::direct_key( 3, 7 ) !== Naber_DB::direct_key( 3, 8 ), 'Farkli kisiler farkli anahtar aliyor' );

exit( Naber_Tests::summary() );

function is_email_like( $value ) {
	return (bool) preg_match( '/^[a-z0-9._-]+@[a-z0-9.-]+\.[a-z]{2,}$/', (string) $value );
}
