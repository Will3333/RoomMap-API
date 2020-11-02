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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsConsumer
import org.http4k.websocket.WsMessage
import pro.wsmi.roommap.api.engine.Engine
import pro.wsmi.roommap.lib.api.websocket.*

@ExperimentalSerializationApi
val MatrixRoomPublicDataListChangeWebsocketAPISubscribers: MutableMap<String, MatrixRoomPublicDataListChangeWebsocketAPISubscriber> = mutableMapOf()

@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
fun handleWebsocketAPIConnection(debugMode: Boolean, engine: Engine) : WsConsumer = { wsConn ->
    wsConn.onMessage { wsMsg ->

        val messageJsonSerializer = Json {
            prettyPrint = debugMode
            serializersModule = Message.serializerModule
        }

        val receivedMessage = try {
            messageJsonSerializer.decodeFromString<Message>(string = wsMsg.bodyString())
        } catch (e: SerializationException) {
            //TODO add error logger
            if (debugMode)
                e.printStackTrace()
            null
        }

        GlobalScope.launch {

            val messageResponse = if (receivedMessage == null)
                GlobalError(code = 404)
            else
            {
                when(receivedMessage)
                {
                    is Request -> {
                        when(receivedMessage)
                        {
                            is MatrixRoomPublicDataListChangeSubReq -> {
                                handleMatrixRoomPublicDataListChangeSubReq(engine = engine, wsConn = wsConn, msg = receivedMessage)

                            }
                            else -> GlobalError(code = 404)
                        }
                    }
                    else -> {
                        GlobalError(code = 404)

                    }
                }
            }

            wsConn.send(WsMessage(messageJsonSerializer.encodeToString(messageResponse)))
        }
    }
}

@ExperimentalUnsignedTypes
@ExperimentalSerializationApi
suspend fun handleMatrixRoomPublicDataListChangeSubReq(engine: Engine, wsConn: Websocket, msg: MatrixRoomPublicDataListChangeSubReq) : Response
{
    val newSubId = MatrixRoomPublicDataListChangeWebsocketAPISubscriber.generateSubId()
    val newSub = MatrixRoomPublicDataListChangeWebsocketAPISubscriber(
        matrixServerListChangeHandler = { newList ->

            val jsonSerializer = Json {  }

            val roomList = newList.toMatrixRoomPublicDataList()

            val notif = MatrixRoomPublicDataNewListNotif (
                subId = newSubId,
                rooms = roomList,
                roomsTotalNum = roomList.size
            )

            wsConn.send(WsMessage(value = jsonSerializer.encodeToString(notif)))
        }
    )

    MatrixRoomPublicDataListChangeWebsocketAPISubscribers[newSubId] = newSub
    engine.addMatrixServerListChangeListener(listener = newSub.matrixServerListChangeHandler)

    val roomList = engine.matrixServers.toList().toMatrixRoomPublicDataList()

    return MatrixRoomPublicDataListChangeSubReqPositiveResponse(
        reqId = msg.reqId,
        subId = newSubId,
        rooms = roomList,
        roomsTotalNum = roomList.size
    )
}