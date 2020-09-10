package pro.wsmi.roommap.backend

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import pro.wsmi.kwsmilib.net.URL

@ExperimentalSerializationApi
@Serializable
data class MatrixServer (
        @Contextual
        @SerialName("_id")
        val id: Id<MatrixServer> = newId(),
        val name: String,
        @SerialName("api_url")
        val apiURL: URL,
        @SerialName("update_frequency")
        val updateFreq: Long = 3600000L,
        @Transient
        var matrixRooms: List<MatrixRoom> = listOf()
)