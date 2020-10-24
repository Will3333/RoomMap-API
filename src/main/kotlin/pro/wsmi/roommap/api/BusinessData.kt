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
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import pro.wsmi.kwsmilib.net.URL
import pro.wsmi.roommap.api.config.DbConfiguration
import pro.wsmi.roommap.api.db.MatrixServers

@ExperimentalSerializationApi
class BusinessData(private val dbCfg: DbConfiguration)
{
    private val database = Database.connect (
        url = "jdbc:postgresql://${if (dbCfg.credentials != null) dbCfg.credentials.username + ":" + dbCfg.credentials.password + "@" else ""}${dbCfg.server.hostString}:${dbCfg.server.port}/${dbCfg.dbName}",
        driver = "org.postgresql.Driver"
    )

    @ExperimentalUnsignedTypes
    var matrixServers: List<MatrixServer> = listOf()

    @ExperimentalUnsignedTypes
    fun updateMatrixServers()
    {
        this.matrixServers = transaction(database) {
            MatrixServers.select(where = not(MatrixServers.disabled)).mapNotNull {

                val apiUrl = URL.parseURL(it[MatrixServers.apiUrl])
                if (apiUrl != null)
                {
                    MatrixServer(
                        id = it[MatrixServers.id].value.toUInt(),
                        name = it[MatrixServers.name],
                        apiURL = apiUrl,
                        updateFreq = it[MatrixServers.updateFrequency],
                        disabled = it[MatrixServers.disabled],
                        tryBeforeDisabling = it[MatrixServers.tryBeforeDisabling]
                    )
                }
                else null
            }
        }
    }
}