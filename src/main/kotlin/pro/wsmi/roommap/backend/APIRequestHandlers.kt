package pro.wsmi.roommap.backend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import pro.wsmi.roommap.lib.api.*

@ExperimentalSerializationApi
fun handleAPIRoomListReq(debugMode: Boolean, matrixServers: List<MatrixServer>) : HttpHandler = { req: Request ->

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val roomListReq = jsonSerializer.decodeFromString(APIRoomListReq.serializer(), req.bodyString())


    val apiRoomList = mutableListOf<pro.wsmi.roommap.lib.api.MatrixRoom>()
    matrixServers.forEach { server ->
        apiRoomList.addAll(
            server.matrixRooms.map {room ->
                pro.wsmi.roommap.lib.api.MatrixRoom(room.roomId, server.id.toString(), room.aliases, room.canonicalAlias, room.name, room.numJoinedMembers, room.topic, room.worldReadable, room.guestCanJoin, room.avatarUrl)
            }
        )
    }

    val serversById = matrixServers.associateBy {
        it.id.toString()
    }

    val sortedAPIRoomList = when(roomListReq.sortingElement)
    {
        MatrixRoomListSortingElement.NUM_JOINED_MEMBERS -> {
            if (roomListReq.descendingSort)
                apiRoomList.sortedByDescending {
                    it.numJoinedMembers
                }
            else
                apiRoomList.sortedBy {
                    it.numJoinedMembers
                }
        }
        MatrixRoomListSortingElement.ROOM_NAME -> {
            if (roomListReq.descendingSort)
                apiRoomList.sortedByDescending {
                    it.name
                }
            else
                apiRoomList.sortedBy {
                    it.name
                }
        }
        MatrixRoomListSortingElement.SERVER_NAME -> {
            if (roomListReq.descendingSort)
                apiRoomList.sortedByDescending {
                    serversById[it.serverId]?.name
                }
            else
                apiRoomList.sortedBy {
                    serversById[it.serverId]?.name
                }
        }
    }

    val filteredAPIRoomList = if (roomListReq.end != null)
        sortedAPIRoomList.slice(IntRange(roomListReq.start, roomListReq.end!!))
    else
        sortedAPIRoomList

    val apiRoomListReqResponse = APIRoomListReqResponse(filteredAPIRoomList.toList())

    Response(Status.OK).body(jsonSerializer.encodeToString(APIRoomListReqResponse.serializer(), apiRoomListReqResponse))
}

@ExperimentalSerializationApi
fun handleAPIServerListReq(debugMode: Boolean, matrixServers: List<MatrixServer>) : HttpHandler = {

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val serverList = matrixServers.associateBy(
        keySelector = {
            it.id.toString()
        },
        valueTransform = {
            pro.wsmi.roommap.lib.api.MatrixServer(it.name, it.apiURL, it.updateFreq)
        }
    )

    val apiServerListReqResponse = APIServerListReqResponse(serverList)

    Response(Status.OK).body(jsonSerializer.encodeToString(APIServerListReqResponse.serializer(), apiServerListReqResponse))
}

@ExperimentalSerializationApi
fun handleAPIServerReq(debugMode: Boolean, matrixServers: List<MatrixServer>) : HttpHandler = { req ->

    val jsonSerializer = Json {
        prettyPrint = debugMode
    }

    val serverReq = jsonSerializer.decodeFromString(APIServerReq.serializer(), req.bodyString())

    var foundServer : MatrixServer? = null
    for (server in matrixServers)
    {
        if (server.id.toString() == serverReq.serverId)
        {
            foundServer = server
            break
        }
    }

    if (foundServer != null)
    {
        val apiServerReqResponse = APIServerReqResponse(foundServer.id.toString(), pro.wsmi.roommap.lib.api.MatrixServer(foundServer.name, foundServer.apiURL, foundServer.updateFreq))
        Response(Status.OK).body(jsonSerializer.encodeToString(APIServerReqResponse.serializer(), apiServerReqResponse))
    }
    else
        Response(Status.NOT_FOUND)
}