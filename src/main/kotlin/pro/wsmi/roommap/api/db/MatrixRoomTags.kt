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

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object MatrixRoomTags : Table(name = "matrix_room_tag")
{
    val id: Column<String> = varchar(name = "id", length = 255)
    val unavailable : Column<Boolean> = bool("unavailable").default(defaultValue = false)
    val parent = reference(name = "parent", MatrixRoomTags.id).nullable().default(null)
    override val primaryKey = PrimaryKey(id)
}