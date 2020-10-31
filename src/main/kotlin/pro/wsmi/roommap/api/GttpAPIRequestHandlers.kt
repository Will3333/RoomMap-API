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
import pro.wsmi.roommap.api.engine.MatrixServer
import pro.wsmi.roommap.lib.api.*
import pro.wsmi.roommap.lib.api.http.*

@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
fun handleHttpAPIMatrixRoomPublicDataListReq(debugMode: Boolean, engine: Engine) : HttpHandler = { req: Request ->

    val frozenMatrixServerList = engine.matrixServers.toList()

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val roomListReq = try {
        jsonSerializer.decodeFromString(MatrixRoomPublicDataListReq.serializer(), req.bodyString())
    } catch (e: SerializationException) {
        null
    }

    if (roomListReq != null)
    {
        val apiRoomList = mutableListOf<MatrixRoomPublicData>()
        frozenMatrixServerList.forEach { server ->
            if(roomListReq.serverFiltering.isNullOrEmpty() || roomListReq.serverFiltering!!.contains(server.id.toInt()))
            {
                apiRoomList.addAll(
                    server.rooms.mapNotNull { room ->
                        if (room.excluded)
                            null
                        else
                            MatrixRoomPublicData (
                                roomId = room.id,
                                serverId = server.id.toInt(),
                                aliases = room.aliases, room.canonicalAlias,
                                name = room.name,
                                numJoinedMembers = room.numJoinedMembers.toInt(),
                                topic = room.topic,
                                worldReadable = room.worldReadable,
                                guestCanJoin = room.guestCanJoin,
                                avatarUrl = room.avatarUrl,
                                languages = room.languages,
                                tagIds = room.tags?.mapNotNull { tag ->
                                    if (tag.unavailable) null else tag.id
                                }?.toSet()
                            )
                    }
                )
            }
        }

        val filteredRoomList = when(roomListReq.elementSorting)
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
                val serversById = frozenMatrixServerList.associateBy { it.id }
                if (roomListReq.descendingSort)
                    apiRoomList.sortedByDescending { serversById[it.serverId.toUInt()]?.name }
                else
                    apiRoomList.sortedBy { serversById[it.serverId.toUInt()]?.name }
            }
        }.let {
            if (roomListReq.languageFiltering != null && roomListReq.languageFiltering!!.isNotEmpty())
                it.filter { matrixRoom ->
                    if (matrixRoom.languages.isNullOrEmpty())
                        false
                    else {
                        var resultOfFiltering = false
                        for(roomLang in matrixRoom.languages!!)
                        {
                            resultOfFiltering = roomListReq.languageFiltering!!.contains(roomLang)
                            if (resultOfFiltering)
                                break
                        }
                        resultOfFiltering
                    }
                }
            else
                it
        }.let {
            if (roomListReq.tagFiltering != null && roomListReq.tagFiltering!!.isNotEmpty())
                it.filter { matrixRoom ->
                    if (matrixRoom.tagIds.isNullOrEmpty())
                        false
                    else {
                        //TODO prendre en, compte les tags parents

                        var resultOfFiltering = false
                        for(roomTag in matrixRoom.tagIds!!)
                        {
                            resultOfFiltering = roomListReq.tagFiltering!!.contains(roomTag)
                            if (resultOfFiltering)
                                break
                        }
                        resultOfFiltering
                    }
                }
            else
                it
        }.let {
            if (roomListReq.numJoinedMembersGTEFiltering != null)
                it.filter { matrixRoom -> matrixRoom.numJoinedMembers >= roomListReq.numJoinedMembersGTEFiltering!! }
            else
                it
        }.let {
            if (roomListReq.numJoinedMembersLTEFiltering != null)
                it.filter { matrixRoom -> matrixRoom.numJoinedMembers <= roomListReq.numJoinedMembersLTEFiltering!! }
            else
                it
        }.let {
            when(roomListReq.guestCanJoinFiltering)
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
            when(roomListReq.worldReadableFiltering)
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
            filteredRoomList.slice(IntRange(roomListReq.start, if (roomListReq.end!! < roomsTotalNum-1) roomListReq.end!! else roomsTotalNum-1))
        else
            filteredRoomList

        if (slicedAPIRoomList.isNotEmpty())
        {
            val roomListReqResponse = MatrixRoomPublicDataListReqResponse(slicedAPIRoomList.toList(), roomsTotalNum)

            val responseBodyStr = try {
                jsonSerializer.encodeToString(MatrixRoomPublicDataListReqResponse.serializer(), roomListReqResponse)
            } catch (e: SerializationException) {
                //TODO Add error logger
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
fun handleHttpAPIMatrixServerPublicDataListReq(debugMode: Boolean, engine: Engine) : HttpHandler = {

    val frozenMatrixServerList = engine.matrixServers.toList()

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val serverList = frozenMatrixServerList.map {
        MatrixServerPublicData(id = it.id.toInt(), name = it.name, apiURL = it.apiUrl, updateFreq = it.roomUpdateFreq.toLong())
    }

    val apiServerListReqResponse = MatrixServerPublicDataListReqResponse(serverList)

    val responseBodyStr = try {
        jsonSerializer.encodeToString(MatrixServerPublicDataListReqResponse.serializer(), apiServerListReqResponse)
    } catch (e : SerializationException) {
        //TODO Add error logger
        null
    }

    if (responseBodyStr != null)
        Response(Status.OK).body(responseBodyStr)
    else
        Response(Status.INTERNAL_SERVER_ERROR)
}

@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
fun handleHttpAPIMatrixServerPublicDataReq(debugMode: Boolean, engine: Engine) : HttpHandler = { req ->

    val frozenMatrixServerList = engine.matrixServers.toList()

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val serverReq = try {
        jsonSerializer.decodeFromString(MatrixServerPublicDataReq.serializer(), req.bodyString())
    } catch (e: SerializationException) {
        null
    }

    if (serverReq != null)
    {
        var foundServer : MatrixServer? = null
        for (server in frozenMatrixServerList)
        {
            if (server.id == serverReq.id.toUInt())
            {
                foundServer = server
                break
            }
        }

        if (foundServer != null)
        {
            val apiServerReqResponse = MatrixServerPublicDataReqResponse(server = MatrixServerPublicData(
                id = foundServer.id.toInt(),
                name = foundServer.name,
                apiURL = foundServer.apiUrl,
                updateFreq = foundServer.roomUpdateFreq.toLong()
            ))

            val responseBodyStr = try {
                jsonSerializer.encodeToString(MatrixServerPublicDataReqResponse.serializer(), apiServerReqResponse)
            } catch (e: SerializationException) {
                //TODO Add error logger
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

@ExperimentalSerializationApi
@ExperimentalUnsignedTypes
fun handleHttpAPIMatrixRoomTagPublicDataListReq(debugMode: Boolean, engine: Engine) : HttpHandler = {

    val frozenMatrixRoomTagList = engine.matrixRoomTags.toMap()

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val tagList = frozenMatrixRoomTagList.map {
        MatrixRoomTagPublicData(id = it.key, parentId = it.value.parent?.id)
    }.toSet()

    val apiRoomTagListReqResponse = MatrixRoomTagPublicDataListReqResponse(tags = tagList)

    val responseBodyStr = try {
        jsonSerializer.encodeToString(MatrixRoomTagPublicDataListReqResponse.serializer(), apiRoomTagListReqResponse)
    } catch (e : SerializationException) {
        //TODO Add error logger
        null
    }

    if (responseBodyStr != null)
        Response(Status.OK).body(responseBodyStr)
    else
        Response(Status.INTERNAL_SERVER_ERROR)
}