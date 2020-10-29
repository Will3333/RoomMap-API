/*
 * Copyright 2020 William Smith
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the License.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    kotlin("plugin.serialization") version "1.4.10"
    application
}

group = "pro.wsmi"
version = "0.1.0-beta"

val ROOMMAP_LIB_VERSION = "0.1.1-beta"
val COROUTINES_VERSION = "1.3.9"
val SERIALIZATION_VERSION = "1.0.0"
val KAML_VERSION = "0.21.0"
val CLIKT_VERSION = "3.0.0"
val KWSMILIB_VERSION = "0.10.1"
val EXPOSED_VERSION= "0.28.1"
val POSTGRESQL_JDBC_VERSION = "42.2.18"
val HTTP4K_VERSION = "3.260.0"


repositories {
    mavenCentral()
    jcenter()
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("pro.wsmi:roommap-lib-jvm:$ROOMMAP_LIB_VERSION")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUTINES_VERSION")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$SERIALIZATION_VERSION")
    implementation("com.charleskorn.kaml:kaml:$KAML_VERSION")
    implementation("com.github.ajalt.clikt:clikt:$CLIKT_VERSION")
    implementation("pro.wsmi:kwsmilib-jvm:$KWSMILIB_VERSION")
    implementation("org.jetbrains.exposed:exposed-core:$EXPOSED_VERSION")
    implementation("org.jetbrains.exposed:exposed-jdbc:$EXPOSED_VERSION")
    implementation("org.postgresql:postgresql:$POSTGRESQL_JDBC_VERSION")
    implementation("org.http4k:http4k-core:$HTTP4K_VERSION")
    implementation("org.http4k:http4k-client-apache:$HTTP4K_VERSION")
    implementation("org.http4k:http4k-server-jetty:$HTTP4K_VERSION")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = JavaVersion.VERSION_1_8.toString()
    freeCompilerArgs = freeCompilerArgs.toMutableList().let {
        it.add("-Xallow-result-return-type")
        it
    }.toList()
}


application {
    mainClassName = "pro.wsmi.roommap.api.BackendKt"
}