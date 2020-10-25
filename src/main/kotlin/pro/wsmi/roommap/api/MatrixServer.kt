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

import kotlinx.serialization.ExperimentalSerializationApi
import pro.wsmi.kwsmilib.net.URL

@ExperimentalSerializationApi
@ExperimentalUnsignedTypes
data class MatrixServer (
        val id: UInt,
        val name: String,
        val apiURL: URL,
        var updateFreq: ULong = 3600000u,
        var disabled: Boolean = false,
        val tryBeforeDisabling: UInt = 3u,
        val rooms: List<MatrixRoom> = listOf()
)