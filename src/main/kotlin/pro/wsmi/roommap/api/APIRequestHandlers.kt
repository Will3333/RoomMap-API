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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import pro.wsmi.roommap.api.engine.Engine
import pro.wsmi.roommap.lib.api.*
import pro.wsmi.roommap.lib.api.MatrixRoom

@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
fun handleAPIRoomListReq(debugMode: Boolean, engine: Engine) : HttpHandler = { req: Request ->

    val frozenMatrixServerList = engine.matrixServers.toList()

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val roomListReq = try {
        jsonSerializer.decodeFromString(APIRoomListReq.serializer(), req.bodyString())
    } catch (e: SerializationException) {
        null
    }

    if (roomListReq != null)
    {
        val apiRoomList = mutableListOf<MatrixRoom>()
        frozenMatrixServerList.forEach { server ->
            apiRoomList.addAll(
                    server.rooms.map {room ->
                        MatrixRoom(room.id, server.id.toString(), room.aliases, room.canonicalAlias, room.name, room.numJoinedMembers.toInt(), room.topic, room.worldReadable, room.guestCanJoin, room.avatarUrl)
                    }
            )
        }

        val filteredRoomList = when(roomListReq.sortingElement)
        {
            MatrixRoomListSortingElement.NUM_JOINED_MEMBERS -> {
                if (roomListReq.descendingSort)
                    apiRoomList.sortedByDescending { it.numJoinedMembers }
                else
                    apiRoomList.sortedBy { it.numJoinedMembers }
            }
            MatrixRoomListSortingElement.ROOM_NAME -> {
                if (roomListReq.descendingSort)
                    apiRoomList.sortedByDescending { it.name }
                else
                    apiRoomList.sortedBy { it.name }
            }
            MatrixRoomListSortingElement.SERVER_NAME -> {
                val serversById = frozenMatrixServerList.associateBy { it.id.toString() }
                if (roomListReq.descendingSort)
                    apiRoomList.sortedByDescending { serversById[it.serverId]?.name }
                else
                    apiRoomList.sortedBy { serversById[it.serverId]?.name }
            }
        }.let {
            if (roomListReq.filteringNumJoinedMembersGTE != null)
                it.filter { matrixRoom -> matrixRoom.numJoinedMembers >= roomListReq.filteringNumJoinedMembersGTE!! }
            else
                it
        }.let {
            if (roomListReq.filteringNumJoinedMembersLTE != null)
                it.filter { matrixRoom -> matrixRoom.numJoinedMembers <= roomListReq.filteringNumJoinedMembersLTE!! }
            else
                it
        }.let {
            when(roomListReq.filteringGuestCanJoin)
            {
                MatrixRoomGuestCanJoinFilter.NO_FILTER -> it
                MatrixRoomGuestCanJoinFilter.CAN_JOIN -> it.filter { matrixRoom ->
                    matrixRoom.guestCanJoin
                }
                MatrixRoomGuestCanJoinFilter.CAN_NOT_JOIN -> it.filterNot {matrixRoom ->
                    matrixRoom.guestCanJoin
                }
            }
        }.let {
            when(roomListReq.filteringWorldReadable)
            {
                MatrixRoomWorldReadableFilter.NO_FILTER -> it
                MatrixRoomWorldReadableFilter.IS_WORLD_READABLE -> it.filter { matrixRoom ->
                    matrixRoom.worldReadable
                }
                MatrixRoomWorldReadableFilter.IS_NOT_WORLD_READABLE -> it.filterNot { matrixRoom ->
                    matrixRoom.worldReadable
                }
            }
        }

        val roomsTotalNum = filteredRoomList.size

        val slicedAPIRoomList = if (roomListReq.end != null)
            filteredRoomList.slice(IntRange(roomListReq.start, if (roomListReq.end!! < (roomsTotalNum-1)) roomListReq.end!! else roomsTotalNum-1))
        else
            filteredRoomList

        if (slicedAPIRoomList.isNotEmpty())
        {
            val apiRoomListReqResponse = APIRoomListReqResponse(slicedAPIRoomList.toList(), roomsTotalNum)

            val responseBodyStr = try {
                jsonSerializer.encodeToString(APIRoomListReqResponse.serializer(), apiRoomListReqResponse)
            } catch (e: SerializationException) {
                null
            }

            if (responseBodyStr != null)
                Response(Status.OK).body(responseBodyStr)
            else
                Response(Status.INTERNAL_SERVER_ERROR)
        }
        else Response(Status.NOT_FOUND)
    }
    else
        Response(Status.NOT_FOUND)
}

@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
fun handleAPIServerListReq(debugMode: Boolean, engine: Engine) : HttpHandler = {

    val frozenMatrixServerList = engine.matrixServers.toList()

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val serverList = frozenMatrixServerList.associateBy(
        keySelector = {
            it.id.toString()
        },
        valueTransform = {
            MatrixServer(it.name, it.apiUrl, it.updateFreq.toLong())
        }
    )

    val apiServerListReqResponse = APIServerListReqResponse(serverList)

    val responseBodyStr = try {
        jsonSerializer.encodeToString(APIServerListReqResponse.serializer(), apiServerListReqResponse)
    } catch (e : SerializationException) {
        null
    }

    if (responseBodyStr != null)
        Response(Status.OK).body(responseBodyStr)
    else
        Response(Status.INTERNAL_SERVER_ERROR)
}

@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
fun handleAPIServerReq(debugMode: Boolean, engine: Engine) : HttpHandler = { req ->

    val frozenMatrixServerList = engine.matrixServers.toList()

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val serverReq = try {
        jsonSerializer.decodeFromString(APIServerReq.serializer(), req.bodyString())
    } catch (e: SerializationException) {
        null
    }

    if (serverReq != null)
    {
        var foundServer : pro.wsmi.roommap.api.engine.MatrixServer? = null
        for (server in frozenMatrixServerList)
        {
            if (server.id.toString() == serverReq.serverId)
            {
                foundServer = server
                break
            }
        }

        if (foundServer != null)
        {
            val apiServerReqResponse = APIServerReqResponse(foundServer.id.toString(), MatrixServer(foundServer.name, foundServer.apiUrl, foundServer.updateFreq.toLong()))

            val responseBodyStr = try {
                jsonSerializer.encodeToString(APIServerReqResponse.serializer(), apiServerReqResponse)
            } catch (e: SerializationException) {
                null
            }

            if (responseBodyStr != null)
                Response(Status.OK).body(responseBodyStr)
            else
                Response(Status.INTERNAL_SERVER_ERROR)
        }
        else
            Response(Status.NOT_FOUND)
    }
    else
        Response(Status.NOT_FOUND)
}