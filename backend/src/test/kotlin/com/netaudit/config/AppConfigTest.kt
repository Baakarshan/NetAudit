package com.netaudit.config

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigTest {
    @Test
    fun `loadConfig uses environment overrides`() {
        val config = MapApplicationConfig(
            "database.url" to "jdbc:h2:mem:cfg;DB_CLOSE_DELAY=-1",
            "database.driver" to "org.h2.Driver",
            "database.user" to "sa",
            "database.password" to "",
            "database.maxPoolSize" to "4",
            "capture.interface" to "eth0",
            "capture.promiscuous" to "true",
            "capture.snapshotLength" to "65536",
            "capture.readTimeout" to "200",
            "capture.channelBufferSize" to "128",
            "alert.enabled" to "true"
        )

        val env = mapOf(
            "DATABASE_URL" to "jdbc:h2:mem:env;DB_CLOSE_DELAY=-1",
            "CAPTURE_PROMISCUOUS" to "false",
            "ALERT_ENABLED" to "false"
        )

        val loaded = loadConfig(config, env)
        assertEquals("jdbc:h2:mem:env;DB_CLOSE_DELAY=-1", loaded.database.url)
        assertFalse(loaded.capture.promiscuous)
        assertFalse(loaded.alertEnabled)
    }

    @Test
    fun `loadConfig falls back to config values`() {
        val config = MapApplicationConfig(
            "database.url" to "jdbc:h2:mem:cfg;DB_CLOSE_DELAY=-1",
            "database.driver" to "org.h2.Driver",
            "database.user" to "sa",
            "database.password" to "",
            "database.maxPoolSize" to "4",
            "capture.interface" to "lo",
            "capture.promiscuous" to "true",
            "capture.snapshotLength" to "65536",
            "capture.readTimeout" to "100",
            "capture.channelBufferSize" to "16",
            "alert.enabled" to "true"
        )

        val loaded = loadConfig(config, emptyMap())
        assertEquals("jdbc:h2:mem:cfg;DB_CLOSE_DELAY=-1", loaded.database.url)
        assertEquals("org.h2.Driver", loaded.database.driver)
        assertEquals("sa", loaded.database.user)
        assertEquals("", loaded.database.password)
        assertEquals(4, loaded.database.maxPoolSize)
        assertEquals("lo", loaded.capture.interfaceName)
        assertTrue(loaded.capture.promiscuous)
        assertEquals(65536, loaded.capture.snapshotLength)
        assertEquals(100, loaded.capture.readTimeoutMs)
        assertEquals(16, loaded.capture.channelBufferSize)
        assertTrue(loaded.alertEnabled)
    }
}
