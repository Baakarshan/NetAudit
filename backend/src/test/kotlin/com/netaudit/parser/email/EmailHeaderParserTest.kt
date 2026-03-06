package com.netaudit.parser.email

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmailHeaderParserTest {
    @Test
    fun `test basic headers`() {
        val headers = EmailHeaderParser.parseHeaders(
            "From: alice@test.com\r\n" +
                "To: bob@test.com\r\n" +
                "Subject: Hello World\r\n\r\n"
        )
        assertEquals("alice@test.com", headers.from)
        assertEquals(listOf("bob@test.com"), headers.to)
        assertEquals("Hello World", headers.subject)
    }

    @Test
    fun `test folded subject`() {
        val headers = EmailHeaderParser.parseHeaders(
            "Subject: This is a\r\n" +
                " very long subject\r\n\r\n"
        )
        assertEquals("This is a very long subject", headers.subject)
    }

    @Test
    fun `test multiple recipients`() {
        val headers = EmailHeaderParser.parseHeaders(
            "To: a@test.com, b@test.com, c@test.com\r\n\r\n"
        )
        assertEquals(3, headers.to.size)
    }

    @Test
    fun `test boundary extraction`() {
        val headers = EmailHeaderParser.parseHeaders(
            "Content-Type: multipart/mixed; boundary=\"----=_Part_123\"\r\n\r\n"
        )
        assertEquals("----=_Part_123", headers.boundary)
    }

    @Test
    fun `test attachment extraction`() {
        val boundary = "----=_Part_123"
        val body =
            "--$boundary\r\n" +
                "Content-Type: application/pdf; name=\"report.pdf\"\r\n" +
                "Content-Disposition: attachment; filename=\"report.pdf\"\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" +
                "SGVsbG8=\r\n" +
                "--$boundary--\r\n"

        val attachments = EmailHeaderParser.extractAttachments(body, boundary)
        assertEquals(1, attachments.size)
        assertEquals("report.pdf", attachments[0].filename)
        assertTrue(attachments[0].estimatedSize > 0)
    }
}
