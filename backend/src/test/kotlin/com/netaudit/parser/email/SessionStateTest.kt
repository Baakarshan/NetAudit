package com.netaudit.parser.email

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionStateTest {
    @Test
    fun `smtp session defaults are initialized`() {
        val session = SmtpSessionState()
        assertEquals(SmtpPhase.CONNECTED, session.phase)
        assertEquals(null, session.from)
        assertTrue(session.to.isEmpty())
        assertEquals(null, session.subject)
        assertTrue(session.dataBuffer.isEmpty())
        assertFalse(session.inDataMode)
        assertTrue(session.attachmentNames.isEmpty())
        assertTrue(session.attachmentSizes.isEmpty())
    }

    @Test
    fun `smtp phase terminal detection`() {
        assertTrue(SmtpPhase.COMPLETED.isTerminal())
        assertFalse(SmtpPhase.GREETED.isTerminal())
    }

    @Test
    fun `pop3 session defaults are initialized`() {
        val session = Pop3SessionState()
        assertEquals(null, session.username)
        assertFalse(session.inRetrMode)
        assertTrue(session.retrBuffer.isEmpty())
    }
}
