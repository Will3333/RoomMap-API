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
import org.http4k.client.ApacheClient
import org.http4k.core.Status
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import pro.wsmi.kwsmilib.language.Language
import pro.wsmi.kwsmilib.net.URL
import pro.wsmi.roommap.api.config.BackendConfiguration
import pro.wsmi.roommap.api.db.*
import pro.wsmi.roommap.api.matrix.api.PublicRoomListReq200Response

@ExperimentalSerializationApi
class BusinessData(private val backendCfg: BackendConfiguration)
{
    private val dbConn = Database.connect (
        url = "jdbc:postgresql://${if (backendCfg.dbCfg.credentials != null) backendCfg.dbCfg.credentials.username + ":" + backendCfg.dbCfg.credentials.password + "@" else ""}${backendCfg.dbCfg.server.hostString}:${backendCfg.dbCfg.server.port}/${backendCfg.dbCfg.dbName}",
        driver = "org.postgresql.Driver"
    )

    var matrixRoomTags: Map<String, MatrixRoomTag> = MatrixRoomTag.getAllTags(this.dbConn)

    @ExperimentalUnsignedTypes
    var matrixServers: List<MatrixServer> = listOf()

    @ExperimentalUnsignedTypes
    fun updateMatrixServers()
    {
        this.matrixServers = transaction(this.dbConn) {
            MatrixServers.select(where = not(MatrixServers.disabled)).mapNotNull {

                val apiUrl = URL.parseURL(it[MatrixServers.apiUrl])
                if (apiUrl != null)
                {
                    val initialServer = MatrixServer(
                        id = it[MatrixServers.id].value.toUInt(),
                        name = it[MatrixServers.name],
                        apiURL = apiUrl,
                        updateFreq = it[MatrixServers.updateFrequency],
                        disabled = it[MatrixServers.disabled],
                    )

                    updateMatrixRoomList(backendCfg, initialServer, this@BusinessData.matrixRoomTags, this@BusinessData.dbConn).getOrThrow()
                }
                else null
            }
        }
    }

    companion object
    {
        @ExperimentalUnsignedTypes
        private fun updateMatrixRoomList(backendCfg: BackendConfiguration, matrixServer: MatrixServer, matrixRoomTags: Map<String, MatrixRoomTag>, dbConn: Database) : Pair<MatrixServer, Exception?>
        {
            val baseHttpRequest = getBaseRequest(backendCfg, matrixServer.apiURL)

            val publicRoomListReq = baseHttpRequest.uri(baseHttpRequest.uri.path(MATRIX_API_PUBLIC_ROOMS_PATH))
            val httpClient = ApacheClient()
            val publicRoomListResponse = httpClient(publicRoomListReq)
            if (publicRoomListResponse.status != Status.OK)
            {
                val disabled = matrixServer.tryBeforeDisabling <= 1u
                if (disabled)
                    transaction(dbConn) {
                        MatrixServers.update({MatrixServers.id eq matrixServer.id.toInt()}) {

                        }
                    }
                matrixServer.copy(disabled = disabled, tryBeforeDisabling = matrixServer.tryBeforeDisabling-1u)
                return Result.failure(Exception("The server ${baseHttpRequest.uri.host} returned the HTTP code ${publicRoomListResponse.status.code}."))
            }


            val publicRoomListReq200Response = try {
                Json.decodeFromString(PublicRoomListReq200Response.serializer(), publicRoomListResponse.bodyString())
            } catch (e: SerializationException) {
                return Result.failure(e)
            }


            val roomsSQLReqResult = transaction(dbConn) {
                MatrixRooms.select(where = MatrixRooms.server eq matrixServer.id.toInt()).associateBy {
                    it[MatrixRooms.id]
                }
            }


            return Result.success(matrixServer.copy(
                rooms = publicRoomListReq200Response.chunk.map { roomChunk ->

                    val roomSQLResult = roomsSQLReqResult[roomChunk.roomId]

                    if (roomSQLResult == null) {
                        transaction(dbConn) {
                            MatrixRooms.insert {
                                it[id] = roomChunk.roomId
                                it[server] = matrixServer.id.toInt()
                            }
                        }
                    }

                    val excludedRoom = if (roomSQLResult != null) roomSQLResult[MatrixRooms.excluded] else false

                    val roomLangs = transaction(dbConn) {
                        MatrixRoomsMatrixRoomLanguages
                            .slice(MatrixRoomsMatrixRoomLanguages.language)
                            .select(where = MatrixRoomsMatrixRoomLanguages.room eq roomChunk.roomId).mapNotNull {
                                Language.valueOf(it[MatrixRoomsMatrixRoomLanguages.language])
                            }
                    }

                    val roomTags = transaction(dbConn) {
                        Join(
                            table = MatrixRoomsMatrixRoomTags, otherTable = MatrixRoomTags,
                            onColumn = MatrixRoomsMatrixRoomTags.tag, otherColumn = MatrixRoomTags.id,
                            joinType = JoinType.INNER,
                            additionalConstraint = { MatrixRoomsMatrixRoomTags.room eq roomChunk.roomId }
                        ).slice(MatrixRoomsMatrixRoomTags.tag).selectAll().mapNotNull {
                            matrixRoomTags[it[MatrixRoomsMatrixRoomTags.tag]]
                        }.toSet()
                    }

                    MatrixRoom (
                        id = roomChunk.roomId,
                        aliases = roomChunk.aliases?.toSet(),
                        canonicalAlias = roomChunk.canonicalAlias,
                        name = roomChunk.name?.replace(regex = Regex("[\\n\\r\\f\\t]"), "")?.replace(regex = Regex("^ +"), ""),
                        numJoinedMembers = roomChunk.numJoinedMembers,
                        topic = roomChunk.topic?.replace(regex = Regex("^[\\n\\r\\f\\t ]+"), ""),
                        worldReadable = roomChunk.worldReadable,
                        guestCanJoin = roomChunk.guestCanJoin,
                        avatarUrl = roomChunk.avatarUrl,
                        excluded = excludedRoom,
                        languages = if (roomLangs.isNotEmpty()) roomLangs else null,
                        tags = if (roomTags.isNotEmpty()) roomTags else null
                    )
                }
            ))
        }
    }
}