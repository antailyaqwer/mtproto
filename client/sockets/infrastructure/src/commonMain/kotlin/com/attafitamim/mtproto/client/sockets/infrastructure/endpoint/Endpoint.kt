package com.attafitamim.mtproto.client.sockets.infrastructure.endpoint

sealed interface Endpoint {

    data class Url(
        val urlString: String
    ) : Endpoint

    data class Address(
        val host: String,
        val port: Int? = null,
        val path: String? = null
    ) : Endpoint
}