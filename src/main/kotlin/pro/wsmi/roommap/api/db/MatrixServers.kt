/*
 * Copyright 2020 William Smith
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 */

package pro.wsmi.roommap.api.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

object MatrixServers : IntIdTable(name = "matrix_server")
{
    val name: Column<String> = varchar(name = "name", 255).uniqueIndex()
    val apiUrl: Column<String> = varchar(name = "api_url", 255).uniqueIndex()
    @ExperimentalUnsignedTypes
    val updateFrequency: Column<ULong> = ulong(name = "update_frequency").default(defaultValue = 3600000u)
    val disabled: Column<Boolean> = bool(name = "disabled").default(defaultValue = false)
    @ExperimentalUnsignedTypes
    val tryBeforeDisabling: Column<UInt> = uinteger(name = "try_before_disabling").default(defaultValue = 3u)
}