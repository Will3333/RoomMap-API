package com.la2soft.roommap.backend.matrix.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pro.wsmi.kwsmilib.net.URL

@ExperimentalSerializationApi
@Serializable
data class PublicRoomsChunk (
        val aliases: List<String>? = null,
        @SerialName("canonical_alias")
        val canonicalAlias: String? = null,
        val name: String? = null,
        @SerialName("num_joined_members")
        val numJoinedMembers: Int,
        @SerialName("room_id")
        val roomId: String,
        val topic: String? = null,
        @SerialName("world_readable")
        val worldReadable: Boolean,
        @SerialName("guest_can_join")
        val guestCanJoin: Boolean,
        @SerialName("avatar_url")
        val avatarUrl: String? = null
)