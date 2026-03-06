package com.netaudit.parser.ftp

import com.netaudit.model.AuditEvent
import com.netaudit.model.Direction
import com.netaudit.model.ProtocolType
import com.netaudit.model.StreamContext
import com.netaudit.parser.ProtocolParser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

class FtpParser : ProtocolParser {
    override val protocolType = ProtocolType.FTP
    override val ports = setOf(21)

    companion object {
        private const val SESSION_KEY = "ftp.session"
        private val FTP_COMMANDS = setOf(
            "USER", "PASS", "ACCT", "CWD", "CDUP", "QUIT", "REIN",
            "PORT", "PASV", "TYPE", "STRU", "MODE", "RETR", "STOR",
            "STOU", "APPE", "ALLO", "REST", "RNFR", "RNTO", "ABOR",
            "DELE", "RMD", "MKD", "PWD", "LIST", "NLST", "SITE",
            "SYST", "STAT", "HELP", "NOOP", "FEAT", "OPTS", "EPRT", "EPSV"
        )
        private val AUDIT_COMMANDS = setOf(
            "USER", "CWD", "RETR", "STOR", "LIST", "NLST",
            "DELE", "MKD", "RMD", "QUIT"
        )
    }

    override fun parse(context: StreamContext): AuditEvent? {
        val text = context.payloadAsText().trim()
        if (text.isBlank()) return null

        val session = context.sessionState.getOrPut(SESSION_KEY) {
            FtpSessionState()
        } as FtpSessionState

        val lines = text.split("\r\n", "\n").filter { it.isNotBlank() }
        var lastEvent: AuditEvent? = null

        for (line in lines) {
            val event = when (context.direction) {
                Direction.CLIENT_TO_SERVER -> parseCommand(context, session, line)
                Direction.SERVER_TO_CLIENT -> parseResponse(context, session, line)
            }
            if (event != null) lastEvent = event
        }

        return lastEvent
    }

    private fun parseCommand(
        context: StreamContext,
        session: FtpSessionState,
        line: String
    ): AuditEvent.FtpEvent? {
        val parts = line.split(" ", limit = 2)
        val command = parts[0].uppercase()
        val argument = parts.getOrNull(1)?.trim()

        if (command !in FTP_COMMANDS) return null

        logger.debug { "FTP Command: $command ${argument ?: ""}" }

        when (command) {
            "USER" -> {
                session.username = argument
                session.phase = FtpPhase.AUTH
            }
            "PASS" -> {
                session.pendingCommand = "PASS"
            }
            "CWD" -> {
                session.currentDirectory = argument
                session.pendingCommand = "CWD"
                session.pendingArgument = argument
            }
            "RETR", "STOR" -> {
                session.phase = FtpPhase.TRANSFER
                session.pendingCommand = command
                session.pendingArgument = argument
            }
            "QUIT" -> {
                session.phase = FtpPhase.IDLE
            }
            "DELE", "MKD", "RMD" -> {
                session.pendingCommand = command
                session.pendingArgument = argument
            }
        }

        if (command !in AUDIT_COMMANDS) return null

        return AuditEvent.FtpEvent(
            id = generateId(),
            timestamp = context.timestamp,
            srcIp = context.srcIp,
            dstIp = context.dstIp,
            srcPort = context.srcPort,
            dstPort = context.dstPort,
            username = session.username,
            command = command,
            argument = argument,
            responseCode = null,
            currentDirectory = session.currentDirectory
        )
    }

    private fun parseResponse(
        context: StreamContext,
        session: FtpSessionState,
        line: String
    ): AuditEvent.FtpEvent? {
        if (line.length < 3) return null
        val code = line.substring(0, 3).toIntOrNull() ?: return null

        logger.debug { "FTP Response: $code" }

        when (code) {
            230 -> {
                session.phase = FtpPhase.LOGGED_IN
                return AuditEvent.FtpEvent(
                    id = generateId(),
                    timestamp = context.timestamp,
                    srcIp = context.dstIp,
                    dstIp = context.srcIp,
                    srcPort = context.dstPort,
                    dstPort = context.srcPort,
                    username = session.username,
                    command = "LOGIN",
                    argument = "Login successful",
                    responseCode = 230,
                    currentDirectory = session.currentDirectory
                )
            }
            530 -> {
                session.phase = FtpPhase.IDLE
                return AuditEvent.FtpEvent(
                    id = generateId(),
                    timestamp = context.timestamp,
                    srcIp = context.dstIp,
                    dstIp = context.srcIp,
                    srcPort = context.dstPort,
                    dstPort = context.srcPort,
                    username = session.username,
                    command = "LOGIN",
                    argument = "Login failed",
                    responseCode = 530,
                    currentDirectory = null
                )
            }
            226 -> {
                session.phase = FtpPhase.LOGGED_IN
            }
            257 -> {
                val dirMatch = Regex("\"(.+?)\"").find(line)
                if (dirMatch != null) {
                    session.currentDirectory = dirMatch.groupValues[1]
                }
            }
        }

        return null
    }

    private fun generateId(): String = UUID.randomUUID().toString()
}
