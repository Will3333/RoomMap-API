package pro.wsmi.roommap.backend.matrix.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@ExperimentalSerializationApi
@Serializable
data class ServerDiscoveryInformationReq200Response (
    @SerialName("m.homeserver")
    val homeserver : HomeserverInformation,
    @SerialName("m.identity_server")
    val identityServer : IdentityServerInformation? = null
)