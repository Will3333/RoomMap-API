package pro.wsmi.roommap.backend.matrix.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupportedSpecVersionReq200Response (
        val versions : List<String>,
        @SerialName("unstable_features")
        val unstableFeatures : Map<String, Boolean>? = null
)