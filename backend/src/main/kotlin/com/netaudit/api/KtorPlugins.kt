package com.netaudit.api

import com.netaudit.model.AppJson
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond

private val logger = KotlinLogging.logger {}

fun Application.configurePlugins() {
    install(ContentNegotiation) {
        json(AppJson)
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception: ${cause.message}" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal Server Error"))
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Bad Request"))
            )
        }
    }
}
