package com.la2soft.roommap.backend.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pro.wsmi.kwsmilib.jvm.serialization.InetSocketAddressSerializer
import java.net.InetSocketAddress

@ExperimentalSerializationApi
@Serializable
data class BackendConfiguration (
        @SerialName("instance_name")
        val instanceName: String,
        @Serializable(with = InetSocketAddressSerializer::class)
        val proxy: InetSocketAddress? = null
)