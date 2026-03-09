package com.netaudit.api

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorPluginsTest {
    @Test
    fun `illegal argument handled by status pages`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        application {
            configurePlugins()
            routing {
                get("/bad") {
                    throw IllegalArgumentException("bad request")
                }
            }
        }

        val response = client.get("/bad")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("bad request"))
    }

    @Test
    fun `unexpected error handled by status pages`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        application {
            configurePlugins()
            routing {
                get("/boom") {
                    error("boom")
                }
            }
        }

        val response = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(response.bodyAsText().contains("boom"))
    }

    @Test
    fun `unexpected error without message uses default`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        application {
            configurePlugins()
            routing {
                get("/boom") {
                    throw RuntimeException()
                }
            }
        }

        val response = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(response.bodyAsText().contains("Internal Server Error"))
    }

    @Test
    fun `illegal argument without message uses default`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        application {
            configurePlugins()
            routing {
                get("/bad") {
                    throw IllegalArgumentException()
                }
            }
        }

        val response = client.get("/bad")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Bad Request"))
    }

    @Test
    fun `cors preflight returns allow origin`() = testApplication {
        environment { config = MapApplicationConfig("ktor.application.modules.size" to "0") }
        application {
            configurePlugins()
            routing {
                get("/ok") {
                    call.respondText("ok")
                }
            }
        }

        val response = client.options("/ok") {
            header(HttpHeaders.Origin, "http://example.com")
            header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Get.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.AccessControlAllowOrigin] != null)
    }
}
