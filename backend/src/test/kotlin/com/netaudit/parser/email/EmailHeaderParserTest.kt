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
    fun `test recipients filter blanks`() {
        val headers = EmailHeaderParser.parseHeaders(
            "To: a@test.com, ,  ,b@test.com\r\n\r\n"
        )
        assertEquals(listOf("a@test.com", "b@test.com"), headers.to)
    }

    @Test
    fun `test boundary extraction`() {
        val headers = EmailHeaderParser.parseHeaders(
            "Content-Type: multipart/mixed; boundary=\"----=_Part_123\"\r\n\r\n"
        )
        assertEquals("----=_Part_123", headers.boundary)
    }

    @Test
    fun `test boundary missing returns null`() {
        val headers = EmailHeaderParser.parseHeaders(
            "Content-Type: text/plain\r\n\r\n"
        )
        assertEquals("text/plain", headers.contentType)
        assertEquals(null, headers.boundary)
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

    @Test
    fun `test attachment extraction skips unnamed`() {
        val boundary = "----=_Part_789"
        val body =
            "--$boundary\r\n" +
                "Content-Type: application/pdf\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" +
                "SGVsbG8=\r\n" +
                "--$boundary--\r\n"

        val attachments = EmailHeaderParser.extractAttachments(body, boundary)
        assertTrue(attachments.isEmpty())
    }

    @Test
    fun `test attachment size ignores boundary lines`() {
        val boundary = "----=_Part_999"
        val body =
            "--$boundary\r\n" +
                "Content-Type: application/pdf; name=\"report.pdf\"\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" +
                "\r\n" +
                "--not-a-boundary\r\n" +
                "SGVsbG8=\r\n" +
                "--$boundary--\r\n"

        val attachments = EmailHeaderParser.extractAttachments(body, boundary)
        assertEquals(1, attachments.size)
        assertTrue(attachments[0].estimatedSize > 0)
    }

    @Test
    fun `test headers without recipients`() {
        val headers = EmailHeaderParser.parseHeaders(
            "From: alice@test.com\r\n\r\n"
        )
        assertEquals("alice@test.com", headers.from)
        assertTrue(headers.to.isEmpty())
        assertEquals(null, headers.boundary)
        assertEquals(null, headers.contentType)
        assertEquals("alice@test.com", headers.component1())
        assertTrue(headers.component2().isEmpty())
        assertEquals(null, headers.component3())
    }

    @Test
    fun `test empty header text`() {
        val headers = EmailHeaderParser.parseHeaders("\r\n")
        assertEquals(null, headers.from)
        assertTrue(headers.to.isEmpty())
    }

    @Test
    fun `test tab folded header`() {
        val headers = EmailHeaderParser.parseHeaders(
            "Subject: Hello\r\n\tWorld\r\n\r\n"
        )
        assertEquals("Hello World", headers.subject)
    }

    @Test
    fun `test header without colon is ignored`() {
        val headers = EmailHeaderParser.parseHeaders(
            "BadHeader\r\nFrom: alice@test.com\r\n\r\n"
        )
        assertEquals("alice@test.com", headers.from)
    }

    @Test
    fun `test boundary without quotes`() {
        val headers = EmailHeaderParser.parseHeaders(
            "Content-Type: Multipart/Mixed; boundary=abc123\r\n\r\n"
        )
        assertEquals("abc123", headers.boundary)
    }

    @Test
    fun `test attachment extraction uses name field`() {
        val boundary = "----=_Part_456"
        val body =
            "--$boundary\r\n" +
                "Content-Type: application/pdf; name=\"report.pdf\"\r\n" +
                "Content-Transfer-Encoding: base64\r\n\r\n" +
                "SGVsbG8=\r\n" +
                "--$boundary--\r\n"

        val attachments = EmailHeaderParser.extractAttachments(body, boundary)
        assertEquals(1, attachments.size)
        assertEquals("report.pdf", attachments[0].filename)
    }

    @Test
    fun `test attachment extraction without boundary`() {
        val attachments = EmailHeaderParser.extractAttachments("body", null)
        assertTrue(attachments.isEmpty())
    }
}
