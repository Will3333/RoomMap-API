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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import pro.wsmi.kwsmilib.language.Language
import pro.wsmi.roommap.api.db.MatrixRooms
import pro.wsmi.roommap.api.db.MatrixRoomsMatrixRoomLanguages
import pro.wsmi.roommap.api.db.MatrixRoomsMatrixRoomTags
import pro.wsmi.roommap.api.matrix.api.PublicRoomsChunk

@ExperimentalSerializationApi
class MatrixRoom @ExperimentalUnsignedTypes
private constructor (
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
        @ExperimentalUnsignedTypes
        fun new(dbCon: Database, matrixServer: MatrixServer, matrixServerRoomChunk: PublicRoomsChunk, excluded: Boolean = false, languages: List<Language>? = null, tags: Set<MatrixRoomTag>? = null) : MatrixRoom
        {
            val newTag = MatrixRoom(
                id = matrixServerRoomChunk.roomId,
                aliases = matrixServerRoomChunk.aliases?.toSet(),
                canonicalAlias = matrixServerRoomChunk.canonicalAlias,
                name = matrixServerRoomChunk.name
                    ?.replace(regex = Regex("[\\n\\r\\f\\t]"), "")
                    ?.replace(regex = Regex("^ +"), ""),
                numJoinedMembers = matrixServerRoomChunk.numJoinedMembers.toUInt(),
                topic = matrixServerRoomChunk.topic
                    ?.replace(regex = Regex("^[\\n\\r\\f\\t ]+"), ""),
                worldReadable = matrixServerRoomChunk.worldReadable,
                guestCanJoin = matrixServerRoomChunk.guestCanJoin,
                avatarUrl = matrixServerRoomChunk.avatarUrl,
                excluded = excluded,
                languages = languages,
                tags = tags
            )

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
            
            return newTag
        }
    }
}