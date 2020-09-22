package pro.wsmi.roommap.api.config

import kotlinx.serialization.Serializable

@Serializable
data class MongoDbCredentials (
    val username: String,
    val password: String
)