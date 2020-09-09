package com.la2soft.roommap.backend

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import pro.wsmi.kwsmilib.net.URL

@ExperimentalSerializationApi
@Serializable
data class Server (
        @Contextual
        @SerialName("_id")
        val id: Id<Server> = newId(),
        val name: String,
        @SerialName("api_url")
        val apiURL: URL,
        @Transient
        val rooms: MutableList<Room> = mutableListOf()
)