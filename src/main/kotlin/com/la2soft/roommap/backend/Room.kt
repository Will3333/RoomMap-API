package com.la2soft.roommap.backend

data class Room (
    val roomId: String,
    val aliases: List<String>? = null,
    val canonicalAlias: String? = null,
    val name: String? = null,
    val numJoinedMembers: Int,
    val topic: String? = null,
    val worldReadable: Boolean,
    val guestCanJoin: Boolean,
    val avatarUrl: String? = null
)