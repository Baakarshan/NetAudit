package com.netaudit.parser.telnet

import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.parser.ProtocolParser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Telnet 协议解析器。
 *
 * 解析登录提示与用户输入命令，忽略 Telnet IAC 控制序列。
 */
class TelnetParser : ProtocolParser {
    override val protocolType = ProtocolType.TELNET
    override val ports = setOf(23)

    companion object {
        private const val KEY_INPUT_BUF = "telnet.inputBuffer"
        private const val KEY_SERVER_BUF = "telnet.serverBuffer"
        private const val KEY_USERNAME = "telnet.username"
        private const val KEY_AWAITING_USER = "telnet.awaitingUsername"
        private const val KEY_AWAITING_PASS = "telnet.awaitingPassword"

        private const val IAC: Byte = 0xFF.toByte()

        private val LOGIN_PROMPTS = listOf("login:", "username:", "login :")
        private val PASSWORD_PROMPTS = listOf("password:", "password :")
    }

    override fun parse(context: StreamContext): AuditEvent? {
        if (context.payload.isEmpty()) return null

        return when (context.direction) {
            Direction.CLIENT_TO_SERVER -> handleClientData(context)
            Direction.SERVER_TO_CLIENT -> handleServerData(context)
        }
    }

    /**
     * 处理客户端输入内容，提取命令行并生成事件。
     *
     * @param context 解析上下文
     */
    private fun handleClientData(context: StreamContext): AuditEvent? {
        val filtered = filterIac(context.payload)
        if (filtered.isEmpty()) return null

        val inputBuf = context.sessionState.getOrPut(KEY_INPUT_BUF) {
            StringBuilder()
        } as StringBuilder

        val text = String(filtered, Charsets.UTF_8)
        inputBuf.append(text)

        val bufContent = inputBuf.toString()
        if (!bufContent.contains('\r') && !bufContent.contains('\n')) {
            return null
        }

        val commandLine = bufContent
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: run {
                inputBuf.clear()
                return null
            }

        inputBuf.clear()

        val username = context.sessionState[KEY_USERNAME] as? String

        if (context.sessionState[KEY_AWAITING_USER] == true) {
            context.sessionState[KEY_USERNAME] = commandLine
            context.sessionState[KEY_AWAITING_USER] = false
            logger.debug { "TELNET: Detected username = $commandLine" }
            return null
        }

        if (context.sessionState[KEY_AWAITING_PASS] == true) {
            context.sessionState[KEY_AWAITING_PASS] = false
            return null
        }

        logger.debug { "TELNET Command: '$commandLine'" }

        return AuditEvent.TelnetEvent(
            id = generateId(),
            timestamp = context.timestamp,
            srcIp = context.srcIp,
            dstIp = context.dstIp,
            srcPort = context.srcPort,
            dstPort = context.dstPort,
            username = username,
            commandLine = commandLine,
            direction = Direction.CLIENT_TO_SERVER
        )
    }

    /**
     * 处理服务端输出内容，识别登录/密码提示。
     *
     * @param context 解析上下文
     */
    private fun handleServerData(context: StreamContext): AuditEvent? {
        val filtered = filterIac(context.payload)
        if (filtered.isEmpty()) return null

        val serverBuf = context.sessionState.getOrPut(KEY_SERVER_BUF) {
            StringBuilder()
        } as StringBuilder

        val text = String(filtered, Charsets.UTF_8)
        serverBuf.append(text)

        val bufLower = serverBuf.toString().lowercase()

        for (prompt in LOGIN_PROMPTS) {
            if (bufLower.contains(prompt)) {
                context.sessionState[KEY_AWAITING_USER] = true
                serverBuf.clear()
                logger.debug { "TELNET: Detected login prompt" }
                return null
            }
        }

        for (prompt in PASSWORD_PROMPTS) {
            if (bufLower.contains(prompt)) {
                context.sessionState[KEY_AWAITING_PASS] = true
                serverBuf.clear()
                logger.debug { "TELNET: Detected password prompt" }
                return null
            }
        }

        if (serverBuf.length > 256) {
            serverBuf.delete(0, serverBuf.length - 256)
        }

        return null
    }

    /**
     * 过滤 Telnet IAC 控制序列，仅保留可见文本。
     *
     * @param data 原始字节
     * @return 过滤后的可见文本字节
     */
    private fun filterIac(data: ByteArray): ByteArray {
        val result = mutableListOf<Byte>()
        var i = 0
        while (i < data.size) {
            if (data[i] == IAC) {
                i++
                if (i < data.size) {
                    when (data[i].toInt() and 0xFF) {
                        0xFB, 0xFC, 0xFD, 0xFE -> {
                            i += 2
                        }
                        0xFA -> {
                            i++
                            while (i < data.size - 1) {
                                if (data[i] == IAC && data[i + 1] == 0xF0.toByte()) {
                                    i += 2
                                    break
                                }
                                i++
                            }
                        }
                        0xFF -> {
                            result.add(IAC)
                            i++
                        }
                        else -> i++
                    }
                }
            } else {
                result.add(data[i])
                i++
            }
        }
        return result.toByteArray()
    }

    private fun generateId(): String = UUID.randomUUID().toString()
}
