package pro.wsmi.roommap.backend.matrix.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@ExperimentalSerializationApi
@Serializable
data class PublicRoomsListReq200Response (
        val chunk: List<PublicRoomsChunk>,
        @SerialName("next_batch")
        val nextBatch: String? = null,
        @SerialName("prev_batch")
        val prevBatch: String? = null,
        @SerialName("total_room_count_estimate")
        val totalRoomCountEstimate: Int? = null
)