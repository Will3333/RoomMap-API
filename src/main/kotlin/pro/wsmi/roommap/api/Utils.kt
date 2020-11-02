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
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import pro.wsmi.kwsmilib.net.URL
import pro.wsmi.roommap.api.config.BackendConfiguration
import pro.wsmi.roommap.api.engine.MatrixServer
import pro.wsmi.roommap.lib.api.MatrixRoomPublicData

@ExperimentalSerializationApi
fun getBaseRequest(backendCfg: BackendConfiguration, url: URL) : Request = Request (

    method = Method.GET,
    uri = Uri(
        scheme = url.protocol,
        userInfo = "",
        host = url.host,
        port = url.port,
        path = "",
        query = "",
        fragment = ""
    )
).replaceHeader("User-Agent", "$APP_NAME/$APP_VERSION (${backendCfg.instanceName})")

@ExperimentalSerializationApi
fun Collection<MatrixServer>.toMatrixRoomPublicDataList() : List<MatrixRoomPublicData>
{
    val roomList = mutableListOf<MatrixRoomPublicData>()
    this.forEach { server ->
        roomList.addAll(server.rooms.map { room ->
            MatrixRoomPublicData (
                roomId = room.id,
                serverId = server.id.toInt(),
                aliases = room.aliases,
                canonicalAlias = room.canonicalAlias,
                name = room.name,
                numJoinedMembers = room.numJoinedMembers.toInt(),
                topic = room.topic,
                worldReadable = room.worldReadable,
                guestCanJoin = room.guestCanJoin,
                avatarUrl = room.avatarUrl,
                languages = room.languages,
                tagIds = room.tags?.map { tag ->
                    tag.id
                }?.toSet()
            )
        })
    }

    return roomList
}