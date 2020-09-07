package com.la2soft.roommap.backend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pro.wsmi.kwsmilib.jvm.serialization.URLSerializer
import java.net.URL

@ExperimentalSerializationApi
@Serializable
data class Server (
        val id: Long,
        val name: String,
        @Serializable(with = URLSerializer::class)
        @SerialName("api_url")
        val apiURL: URL,
        @SerialName("supported_specification_versions")
        val supportedSpecVersions: List<String>
)