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

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.exposed.sql.Database
import pro.wsmi.roommap.api.config.BackendConfiguration


@ExperimentalSerializationApi
class Engine private constructor(private val backendCfg: BackendConfiguration, private val dbConn: Database, matrixRoomTags: Map<String, MatrixRoomTag>, matrixServers: List<MatrixServer>)
{
    var matrixRoomTags: Map<String, MatrixRoomTag> = matrixRoomTags
        private set

    @ExperimentalUnsignedTypes
    var matrixServers: List<MatrixServer> = matrixServers
        private set


    @ExperimentalUnsignedTypes
    fun startMatrixServerRoomListUpdatingLoops() //TODO Gérer les jobs de manière fonctionelle (et enventuellement créer une fonction qui crée un job pour un server spécifique)
    {
        this.matrixServers.map { server ->
            GlobalScope.launch(start = CoroutineStart.LAZY) {
                while (server.tryBeforeDisabling > 0u)
                {
                    val rooms = MatrixRoom.getAllRooms(this@Engine.backendCfg, this@Engine.dbConn, server, this@Engine.matrixRoomTags).getOrElse {
                        //TODO log error(s)
                        null
                    }

                    val serverIndex = this@Engine.matrixServers.indexOf(server)
                    val newServerTryBeforeDisabling = if (rooms == null && server.tryBeforeDisabling > 0u) server.tryBeforeDisabling-1u else server.tryBeforeDisabling
                    val newServerDisabled = if (newServerTryBeforeDisabling > 0u) server.disabled else true
                    val newServer = server.update (
                        dbConn = this@Engine.dbConn,
                        tryBeforeDisabling = newServerTryBeforeDisabling,
                        disabled = newServerDisabled,
                        rooms = rooms ?: server.rooms
                    ).getOrElse {
                        //TODO log error(s)
                        null
                    }

                    if (newServer != null)
                    {
                        this@Engine.matrixServers = this@Engine.matrixServers.toMutableList().let {
                            it.remove(server)
                            it.add(index = serverIndex, element = newServer)
                            it
                        }

                        delay(newServer.updateFreq.toLong())
                    }
                    else
                        delay(server.updateFreq.toLong())
                }
            }
        }
    }

    companion object
    {
        @ExperimentalUnsignedTypes
        fun new(backendCfg: BackendConfiguration) : Result<Engine>
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

            return Result.success(Engine(backendCfg = backendCfg, dbConn = dbConn, matrixRoomTags = tags, matrixServers = matrixServersWithoutRooms))
        }
    }
}