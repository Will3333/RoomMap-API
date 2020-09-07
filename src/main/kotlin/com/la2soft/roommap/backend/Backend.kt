package com.la2soft.roommap.backend

import com.la2soft.roommap.backend.matrix.api.ServerDiscoveryInformationReq200Response
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@ExperimentalSerializationApi
fun main() = runBlocking {

    val httpClient = HttpClient(Apache)

//    val response = httpClient.get<ServerDiscoveryInformationReq200Response>("https://matrix.la2soft.com:8448/.well-known/matrix/client")

    val httpResponse = httpClient.get<HttpResponse>("https://matrix.la2soft.com:8448/.well-known/matrix/client")

    val serverDiscoveryInformationReq200Response = if (httpResponse.status == HttpStatusCode.OK) {
        Json.decodeFromString(ServerDiscoveryInformationReq200Response.serializer(), httpResponse.readText())
    } else
        null

    if (serverDiscoveryInformationReq200Response != null)
        println(serverDiscoveryInformationReq200Response)
}