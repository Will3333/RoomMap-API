plugins {
    kotlin("jvm") version "1.4.0"
    kotlin("plugin.serialization") version "1.4.0"
}

group = "com.la2soft"
version = "0.1"

val COROUTINES_VERSION = "1.3.9"
val SERIALIZATION_VERSION = "1.0.0-RC"
val KAML_VERSION = "0.21.0"
val CLIKT_VERSION = "3.0.0"
val KWSMILIB_VERSION = "0.5.0"
val KTOR_VERSION = "1.4.0"

repositories {
    mavenCentral()
    jcenter()
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUTINES_VERSION")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$SERIALIZATION_VERSION")
    implementation("com.charleskorn.kaml:kaml:$KAML_VERSION")
    implementation("com.github.ajalt.clikt:clikt:$CLIKT_VERSION")
    implementation("pro.wsmi:kwsmilib-jvm:$KWSMILIB_VERSION")
    implementation("io.ktor:ktor-client-core:$KTOR_VERSION")
    implementation("io.ktor:ktor-client-serialization-jvm:$KTOR_VERSION")
    implementation("io.ktor:ktor-client-apache:$KTOR_VERSION")
}
