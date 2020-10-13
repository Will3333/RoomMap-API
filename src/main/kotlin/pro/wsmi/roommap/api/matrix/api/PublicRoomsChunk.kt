/*
 * Copyright 2020 William Smith
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 */

package pro.wsmi.roommap.api.matrix.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@ExperimentalSerializationApi
@Serializable
data class PublicRoomsChunk (
        val aliases: List<String>? = null,
        @SerialName("canonical_alias")
        val canonicalAlias: String? = null,
        val name: String? = null,
        @SerialName("num_joined_members")
        val numJoinedMembers: Int,
        @SerialName("room_id")
        val roomId: String,
        val topic: String? = null,
        @SerialName("world_readable")
        val worldReadable: Boolean,
        @SerialName("guest_can_join")
        val guestCanJoin: Boolean,
        @SerialName("avatar_url")
        val avatarUrl: String? = null
)