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
import pro.wsmi.roommap.api.config.BackendConfiguration
import pro.wsmi.roommap.api.db.MatrixRoomTags
import pro.wsmi.roommap.api.db.MatrixRooms
import pro.wsmi.roommap.api.db.MatrixRoomsMatrixRoomLanguages
import pro.wsmi.roommap.api.db.MatrixRoomsMatrixRoomTags
import pro.wsmi.roommap.api.matrix.api.MATRIX_API_PUBLIC_ROOMS_PATH
import pro.wsmi.roommap.api.matrix.api.PublicRoomListReq200Response
import pro.wsmi.roommap.api.matrix.api.PublicRoomsChunk
import java.sql.SQLException

@ExperimentalSerializationApi
class MatrixRoom @ExperimentalUnsignedTypes private constructor (
    val id: String,
    var aliases: Set<String>? = null,
    var canonicalAlias: String? = null,
    var name: String? = null,
    var numJoinedMembers: UInt,
    var topic: String? = null,
    var worldReadable: Boolean,
    var guestCanJoin: Boolean,
    var avatarUrl: String? = null,
    var excluded: Boolean = false,
    var languages: List<Language>? = null,
    var tags: Set<MatrixRoomTag>? = null
)
{
    companion object
    {
        private fun String.filterName() : String = this
            .replace(regex = Regex("[\\n\\r\\f\\t]"), "")
            .replace(regex = Regex("^ +"), "")
        private fun String.filterTopic() : String = this.replace(regex = Regex("^[\\n\\r\\f\\t ]+"), "")

        @ExperimentalUnsignedTypes
        fun new(dbCon: Database, matrixServer: MatrixServer, matrixServerRoomChunk: PublicRoomsChunk, excluded: Boolean = false, languages: List<Language>? = null, tags: Set<MatrixRoomTag>? = null) : Result<MatrixRoom>
        {
            val newTag = MatrixRoom(
                id = matrixServerRoomChunk.roomId,
                aliases = matrixServerRoomChunk.aliases?.toSet(),
                canonicalAlias = matrixServerRoomChunk.canonicalAlias,
                name = matrixServerRoomChunk.name?.filterName(),
                numJoinedMembers = matrixServerRoomChunk.numJoinedMembers.toUInt(),
                topic = matrixServerRoomChunk.topic?.filterTopic(),
                worldReadable = matrixServerRoomChunk.worldReadable,
                guestCanJoin = matrixServerRoomChunk.guestCanJoin,
                avatarUrl = matrixServerRoomChunk.avatarUrl,
                excluded = excluded,
                languages = languages,
                tags = tags
            )

            try {
                transaction(dbCon) {
                    MatrixRooms.insert {
                        it[MatrixRooms.id] = newTag.id
                        it[MatrixRooms.server] = matrixServer.id.toInt()
                        it[MatrixRooms.excluded] = newTag.excluded
                    }
                    newTag.languages?.forEach { lang ->
                        MatrixRoomsMatrixRoomLanguages.insert {
                            it[MatrixRoomsMatrixRoomLanguages.room] = newTag.id
                            it[MatrixRoomsMatrixRoomLanguages.language] = lang.name
                        }
                    }
                    newTag.tags?.forEach {tag ->
                        MatrixRoomsMatrixRoomTags.insert {
                            it[MatrixRoomsMatrixRoomTags.room] = newTag.id
                            it[MatrixRoomsMatrixRoomTags.tag] = tag.id
                        }
                    }
                }
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            return Result.success(newTag)
        }

        @ExperimentalUnsignedTypes
        fun getAllRooms(backendCfg: BackendConfiguration, dbConn: Database, matrixServer: MatrixServer, matrixRoomTags: Map<String, MatrixRoomTag>) : Result<List<MatrixRoom>>
        {
            val baseHttpRequest = getBaseRequest(backendCfg, matrixServer.apiURL)

            val publicRoomListReq = baseHttpRequest.uri(baseHttpRequest.uri.path(MATRIX_API_PUBLIC_ROOMS_PATH))
            val publicRoomListResponse = ApacheClient()(publicRoomListReq)

            if (publicRoomListResponse.status != Status.OK) {
                return Result.failure(Exception("The server ${baseHttpRequest.uri.host} returned the HTTP code ${publicRoomListResponse.status.code}."))
            }

            val publicRoomListReq200Response = try {
                Json.decodeFromString(PublicRoomListReq200Response.serializer(), publicRoomListResponse.bodyString())
            } catch (e: SerializationException) {
                return Result.failure(e)
            }

            val roomsSQLReqResult = try {
                transaction(dbConn) {
                    MatrixRooms.select(where = MatrixRooms.server eq matrixServer.id.toInt()).associateBy {
                        it[MatrixRooms.id]
                    }
                }
            } catch (e: SQLException) {
                return Result.failure(e)
            }

            return Result.success(publicRoomListReq200Response.chunk.map { roomChunk ->

                val roomSQLReqResult = roomsSQLReqResult[roomChunk.roomId]

                if (roomSQLReqResult != null)
                {
                    val roomLangs = try {
                        transaction(dbConn) {
                            MatrixRoomsMatrixRoomLanguages
                                .slice(MatrixRoomsMatrixRoomLanguages.language)
                                .select(where = MatrixRoomsMatrixRoomLanguages.room eq roomChunk.roomId).mapNotNull {
                                    Language.valueOf(it[MatrixRoomsMatrixRoomLanguages.language])
                                }
                        }
                    } catch (e: SQLException) {
                        return Result.failure(e)
                    }

                    val roomTags = try {
                        transaction(dbConn) {
                            Join(
                                table = MatrixRoomsMatrixRoomTags, otherTable = MatrixRoomTags,
                                onColumn = MatrixRoomsMatrixRoomTags.tag, otherColumn = MatrixRoomTags.id,
                                joinType = JoinType.INNER,
                                additionalConstraint = { MatrixRoomsMatrixRoomTags.room eq roomChunk.roomId }
                            ).slice(MatrixRoomsMatrixRoomTags.tag).selectAll().mapNotNull {
                                matrixRoomTags[it[MatrixRoomsMatrixRoomTags.tag]]
                            }.toSet()
                        }
                    } catch (e: SQLException) {
                        return Result.failure(e)
                    }

                    MatrixRoom (
                        id = roomChunk.roomId,
                        aliases = roomChunk.aliases?.toSet(),
                        canonicalAlias = roomChunk.canonicalAlias,
                        name = roomChunk.name?.filterName(),
                        numJoinedMembers = roomChunk.numJoinedMembers.toUInt(),
                        topic = roomChunk.topic?.filterTopic(),
                        worldReadable = roomChunk.worldReadable,
                        guestCanJoin = roomChunk.guestCanJoin,
                        avatarUrl = roomChunk.avatarUrl,
                        excluded = roomSQLReqResult[MatrixRooms.excluded],
                        languages = if (roomLangs.isNotEmpty()) roomLangs else null,
                        tags = if (roomTags.isNotEmpty()) roomTags else null
                    )
                }
                else
                    new(dbConn, matrixServer, roomChunk).let {
                        it.getOrElse { e ->
                            return Result.failure(e)
                        }
                    }
            })
        }
    }
}