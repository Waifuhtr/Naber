package com.naber.app.data

/**
 * Grupta tek tek verilebilen yetkiler.
 *
 * Birini yonetici yapmadan da "uyeleri cikarabilsin ama grup adini
 * degistiremesin" gibi ayarlar yapilabilsin diye var. Sahip ve
 * yoneticiler listeye bakilmaksizin hepsine sahiptir; sunucu da ayni
 * kurali uygular, buradaki liste yalnizca arayuz icindir.
 */
data class GroupPermission(val key: String, val title: String, val description: String)

val GROUP_PERMISSIONS = listOf(
    GroupPermission("remove_member", "Uye cikarma", "Gruptan uye atabilir"),
    GroupPermission("delete_message", "Mesaj silme", "Herkesin mesajini silebilir"),
    GroupPermission("edit_group", "Grubu duzenleme", "Ad, aciklama ve fotografi degistirebilir"),
    GroupPermission("add_member", "Uye ekleme", "Gruba yeni kisi ekleyebilir"),
    GroupPermission("pin_message", "Mesaj sabitleme", "Sohbetin en ustune mesaj tutturabilir")
)
