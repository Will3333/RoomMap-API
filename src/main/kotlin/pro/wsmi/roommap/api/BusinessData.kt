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
import pro.wsmi.roommap.api.config.BackendConfiguration


@ExperimentalSerializationApi
class BusinessData private constructor(private val backendCfg: BackendConfiguration, private val dbConn: Database, matrixRoomTags: Map<String, MatrixRoomTag>, matrixServers: List<MatrixServer>)
{
    var matrixRoomTags: Map<String, MatrixRoomTag> = matrixRoomTags
        private set

    @ExperimentalUnsignedTypes
    var matrixServers: List<MatrixServer> = matrixServers
        private set

    @ExperimentalUnsignedTypes
    fun updateMatrixServerRoomLists() : Result<List<MatrixServer>>
    {
        this.matrixServers = matrixServers.map { server ->

            server.update(
                dbConn = this.dbConn,
                rooms = MatrixRoom.getAllRooms(this.backendCfg, this.dbConn, server, this.matrixRoomTags).getOrElse { e ->
                    return Result.failure(e)
                }
            ).getOrElse { e ->
                return Result.failure(e)
            }
        }

        return Result.success(this.matrixServers)
    }

    companion object
    {
        @ExperimentalUnsignedTypes
        fun new(backendCfg: BackendConfiguration) : Result<BusinessData>
        {
            val dbConn = try {
                Database.connect (
                    url = "jdbc:postgresql://${if (backendCfg.dbCfg.credentials != null) backendCfg.dbCfg.credentials.username + ":" + backendCfg.dbCfg.credentials.password + "@" else ""}${backendCfg.dbCfg.server.hostString}:${backendCfg.dbCfg.server.port}/${backendCfg.dbCfg.dbName}",
                    driver = "org.postgresql.Driver"
                )
            } catch (e: Exception) {
                return Result.failure(e)
            }

            val tags = MatrixRoomTag.getAllTags(dbConn).getOrElse { e ->
                return Result.failure(e)
            }

            val matrixServersWithoutRooms = MatrixServer.getAllServers(dbConn, notDisabled = true).getOrElse { e ->
                return Result.failure(e)
            }

            return Result.success(BusinessData(backendCfg = backendCfg, dbConn = dbConn, matrixRoomTags = tags, matrixServers = matrixServersWithoutRooms))
        }
    }
}