package pro.wsmi.roommap.api.matrix.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pro.wsmi.kwsmilib.net.URL

@ExperimentalSerializationApi
@Serializable
data class IdentityServerInformation (
    @SerialName("base_url")
    val baseUrl: URL
)