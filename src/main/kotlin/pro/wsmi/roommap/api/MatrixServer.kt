/*
 * Copyright 2020 William Smith
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 */

package pro.wsmi.roommap.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import pro.wsmi.kwsmilib.net.URL

@ExperimentalUnsignedTypes
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
        @SerialName("disabled")
        var disabled: Boolean = false,
        @Transient
        var tryBeforeDisabling: UInt = 3u,
        @Transient
        var matrixRooms: List<MatrixRoom> = listOf()
)