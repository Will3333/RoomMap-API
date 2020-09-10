package pro.wsmi.roommap.backend.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pro.wsmi.kwsmilib.jvm.serialization.InetSocketAddressSerializer
import java.net.InetSocketAddress

@ExperimentalSerializationApi
@Serializable
data class MongoDbConfiguration (
    @Serializable(with = InetSocketAddressSerializer::class)
    val server: InetSocketAddress,
    @SerialName("database_name")
    val dbName: String,
    val credentials: MongoDbCredentials? = null
)