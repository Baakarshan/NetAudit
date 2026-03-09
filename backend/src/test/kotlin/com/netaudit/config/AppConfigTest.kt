package com.netaudit.config

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigTest {
    @Test
    fun `loadConfig uses application config when env missing`() {
        val config = MapApplicationConfig(
            "database.url" to "jdbc:h2:mem:test",
            "database.driver" to "org.h2.Driver",
            "database.user" to "user",
            "database.password" to "pass",
            "database.maxPoolSize" to "5",
            "capture.interface" to "eth0",
            "capture.promiscuous" to "true",
            "capture.snapshotLength" to "65536",
            "capture.readTimeout" to "100",
            "capture.channelBufferSize" to "1024",
            "alert.enabled" to "true"
        )

        val appConfig = loadConfig(config, emptyMap())
        assertEquals("jdbc:h2:mem:test", appConfig.database.url)
        assertEquals("org.h2.Driver", appConfig.database.driver)
        assertEquals("user", appConfig.database.user)
        assertEquals("pass", appConfig.database.password)
        assertEquals(5, appConfig.database.maxPoolSize)
        assertEquals("eth0", appConfig.capture.interfaceName)
        assertTrue(appConfig.capture.promiscuous)
        assertEquals(65536, appConfig.capture.snapshotLength)
        assertEquals(100, appConfig.capture.readTimeoutMs)
        assertEquals(1024, appConfig.capture.channelBufferSize)
        assertTrue(appConfig.alertEnabled)
    }

    @Test
    fun `loadConfig env overrides application config`() {
        val config = MapApplicationConfig(
            "database.url" to "jdbc:h2:mem:test",
            "database.driver" to "org.h2.Driver",
            "database.user" to "user",
            "database.password" to "pass",
            "database.maxPoolSize" to "5",
            "capture.interface" to "eth0",
            "capture.promiscuous" to "true",
            "capture.snapshotLength" to "65536",
            "capture.readTimeout" to "100",
            "capture.channelBufferSize" to "1024",
            "alert.enabled" to "true"
        )

        val env = mapOf(
            "DATABASE_URL" to "jdbc:postgresql://localhost:5432/netaudit",
            "DATABASE_DRIVER" to "org.postgresql.Driver",
            "DATABASE_USER" to "netaudit",
            "DATABASE_PASSWORD" to "netaudit",
            "DATABASE_MAX_POOL_SIZE" to "20",
            "CAPTURE_INTERFACE" to "lo",
            "CAPTURE_PROMISCUOUS" to "false",
            "CAPTURE_SNAPSHOT_LENGTH" to "2048",
            "CAPTURE_READ_TIMEOUT" to "250",
            "CAPTURE_CHANNEL_BUFFER_SIZE" to "2048",
            "ALERT_ENABLED" to "false"
        )

        val appConfig = loadConfig(config, env)
        assertEquals("jdbc:postgresql://localhost:5432/netaudit", appConfig.database.url)
        assertEquals("org.postgresql.Driver", appConfig.database.driver)
        assertEquals("netaudit", appConfig.database.user)
        assertEquals("netaudit", appConfig.database.password)
        assertEquals(20, appConfig.database.maxPoolSize)
        assertEquals("lo", appConfig.capture.interfaceName)
        assertFalse(appConfig.capture.promiscuous)
        assertEquals(2048, appConfig.capture.snapshotLength)
        assertEquals(250, appConfig.capture.readTimeoutMs)
        assertEquals(2048, appConfig.capture.channelBufferSize)
        assertFalse(appConfig.alertEnabled)
    }
}
