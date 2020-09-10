package pro.wsmi.roommap.backend.config

import kotlinx.serialization.Serializable

@Serializable
data class MongoDbCredentials (
    val username: String,
    val password: String
)