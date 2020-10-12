import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    kotlin("plugin.serialization") version "1.4.10"
    application
}

group = "pro.wsmi"
version = "0.0.1"

val ROOMMAP_LIB_VERSION = "0.1.0"
val COROUTINES_VERSION = "1.3.9"
val SERIALIZATION_VERSION = "1.0.0"
val KAML_VERSION = "0.21.0"
val CLIKT_VERSION = "3.0.0"
val KWSMILIB_VERSION = "0.10.0"
val KMONGO_VERSION= "4.1.2"
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
    implementation("org.litote.kmongo:kmongo:$KMONGO_VERSION")
    implementation("org.litote.kmongo:kmongo-coroutine-serialization:$KMONGO_VERSION")
    implementation("org.litote.kmongo:kmongo-id-serialization:$KMONGO_VERSION")
    implementation("org.http4k:http4k-core:$HTTP4K_VERSION")
    implementation("org.http4k:http4k-client-apache:$HTTP4K_VERSION")
    implementation("org.http4k:http4k-server-jetty:$HTTP4K_VERSION")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()

application {
    mainClassName = "pro.wsmi.roommap.api.BackendKt"
}