package pro.wsmi.roommap.api.config

import kotlinx.serialization.Serializable

@Serializable
data class APIHttpServer (
    val port: Int = 80,
    val compression: Boolean
)