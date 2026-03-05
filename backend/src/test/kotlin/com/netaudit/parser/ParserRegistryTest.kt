package com.netaudit.parser

import com.netaudit.model.AuditEvent
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserRegistryTest {
    private class FakeHttpParser : ProtocolParser {
        override val protocolType = ProtocolType.HTTP
        override val ports = setOf(80, 8080)
        override fun parse(context: StreamContext): AuditEvent? = null
    }

    private class FakeFtpParser : ProtocolParser {
        override val protocolType = ProtocolType.FTP
        override val ports = setOf(21)
        override fun parse(context: StreamContext): AuditEvent? = null
    }

    @Test
    fun `test parser registration and lookup by port`() {
        val registry = ParserRegistry()
        val httpParser = FakeHttpParser()

        registry.register(httpParser)

        // 测试 findByPort
        assertEquals(httpParser, registry.findByPort(80))
        assertEquals(httpParser, registry.findByPort(8080))
        assertNull(registry.findByPort(21))
    }

    @Test
    fun `test findByEitherPort`() {
        val registry = ParserRegistry()
        val httpParser = FakeHttpParser()

        registry.register(httpParser)

        // 测试双向端口匹配
        assertEquals(httpParser, registry.findByEitherPort(12345, 80))
        assertEquals(httpParser, registry.findByEitherPort(8080, 12345))
        assertNull(registry.findByEitherPort(12345, 21))
    }

    @Test
    fun `test multiple parser registration`() {
        val registry = ParserRegistry()
        val httpParser = FakeHttpParser()
        val ftpParser = FakeFtpParser()

        registry.register(httpParser)
        registry.register(ftpParser)

        // 测试多个 Parser 注册
        assertEquals(httpParser, registry.findByPort(80))
        assertEquals(ftpParser, registry.findByPort(21))

        // 测试 allParsers
        val allParsers = registry.allParsers()
        assertEquals(2, allParsers.size)
        assertTrue(allParsers.contains(httpParser))
        assertTrue(allParsers.contains(ftpParser))

        // 测试 allPorts
        val allPorts = registry.allPorts()
        assertEquals(3, allPorts.size)
        assertTrue(allPorts.containsAll(setOf(80, 8080, 21)))
    }
}
