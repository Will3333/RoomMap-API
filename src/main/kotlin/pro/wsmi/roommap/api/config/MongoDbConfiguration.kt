/*
 * Copyright 2020 William Smith
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 */

package pro.wsmi.roommap.api.config

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