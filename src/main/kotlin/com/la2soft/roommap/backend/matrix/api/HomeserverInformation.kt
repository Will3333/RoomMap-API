package com.la2soft.roommap.backend.matrix.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pro.wsmi.kwsmilib.jvm.serialization.URLSerializer
import java.net.URL

@ExperimentalSerializationApi
@Serializable
data class HomeserverInformation (
    @SerialName("base_url")
    @Serializable(with = URLSerializer::class)
    val baseUrl: URL
)