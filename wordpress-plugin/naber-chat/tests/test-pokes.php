<?php
/**
 * Durtme (poke) ozelligi testleri.
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-pokes.php
 *
 * Not: gercek veritabani gerektiren kisimlar (Naber_Pokes::poke,
 * cooldown_remaining) burada test edilmez; yalnizca veritabanina
 * dokunmayan saf mantik dogrulanir.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Kendini durtme engeli' );

$result = Naber_Pokes::poke( 5, 5 );
Naber_Tests::ok( is_wp_error( $result ), 'Kendini durtme WP_Error donduruyor' );
Naber_Tests::equals( 'naber_self_poke', $result->get_error_code(), 'Dogru hata kodu donuyor' );

Naber_Tests::group( 'Bekleme suresi hesaplama (saf fonksiyon)' );

Naber_Tests::equals( 60, Naber_Pokes::remaining_from_elapsed( 0 ), 'Az once durtulduyse tam bekleme suresi kaliyor' );
Naber_Tests::equals( 30, Naber_Pokes::remaining_from_elapsed( 30 ), 'Yarisi gectiyse yarisi kaliyor' );
Naber_Tests::equals( 1, Naber_Pokes::remaining_from_elapsed( 59 ), 'Suresi bitmek uzereyken 1 saniye kaliyor' );
Naber_Tests::equals( 0, Naber_Pokes::remaining_from_elapsed( 60 ), 'Tam sure geçtiginde bekleme bitiyor' );
Naber_Tests::equals( 0, Naber_Pokes::remaining_from_elapsed( 120 ), 'Cok uzun sure gectiyse negatif degil sifir donuyor' );
Naber_Tests::equals( 60, Naber_Pokes::COOLDOWN_SECONDS, 'Bekleme suresi sabiti 60 saniye' );

Naber_Tests::summary();
