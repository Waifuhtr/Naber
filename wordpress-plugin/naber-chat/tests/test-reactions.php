<?php
/**
 * Emoji reaksiyonu ile ilgili saf mantik testleri.
 *
 * Calistirma: php wordpress-plugin/naber-chat/tests/test-reactions.php
 *
 * Not: veritabani gerektiren kisimlar (Naber_Reactions::toggle/summary)
 * burada test edilmez; yalnizca girdi dogrulama (sanitize_emoji) ve
 * Naber_Reactions::summary_for_many'nin bos girdi davranisi test edilir.
 */

require_once __DIR__ . '/bootstrap.php';

Naber_Tests::group( 'Emoji girisi dogrulama' );

Naber_Tests::equals( '👍', Naber_REST::sanitize_emoji( '👍' ), 'Gecerli emoji aynen kabul ediliyor' );
Naber_Tests::equals( '👍', Naber_REST::sanitize_emoji( '  👍  ' ), 'Bastaki sondaki bosluklar temizleniyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_emoji( '' ), 'Bos deger reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_emoji( null ), 'Null reddediliyor' );
Naber_Tests::equals( '', Naber_REST::sanitize_emoji( str_repeat( 'a', 17 ) ), '16 bayti asan girdi reddediliyor' );
Naber_Tests::equals( 'aaaaaaaaaaaaaaaa', Naber_REST::sanitize_emoji( str_repeat( 'a', 16 ) ), 'Tam 16 bayt kabul ediliyor' );

Naber_Tests::group( 'Toplu reaksiyon ozeti - bos girdi' );

Naber_Tests::equals( array(), Naber_Reactions::summary_for_many( array() ), 'Bos mesaj listesi bos sonuc donuyor (veritabanina gitmiyor)' );

Naber_Tests::summary();
