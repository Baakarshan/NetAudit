package com.netaudit.parser.email

object EmailHeaderParser {
    data class EmailHeaders(
        val from: String? = null,
        val to: List<String> = emptyList(),
        val subject: String? = null,
        val contentType: String? = null,
        val boundary: String? = null
    )

    data class AttachmentInfo(
        val filename: String,
        val estimatedSize: Int
    )

    fun parseHeaders(headerText: String): EmailHeaders {
        val headerMap = mutableMapOf<String, String>()
        var currentKey = ""
        var currentValue = StringBuilder()

        for (line in headerText.split("\r\n", "\n")) {
            if (line.isBlank()) break

            if (line.startsWith(" ") || line.startsWith("\t")) {
                currentValue.append(" ").append(line.trim())
            } else {
                if (currentKey.isNotBlank()) {
                    headerMap[currentKey.lowercase()] = currentValue.toString()
                }
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    currentKey = line.substring(0, colonIdx).trim()
                    currentValue = StringBuilder(line.substring(colonIdx + 1).trim())
                }
            }
        }
        if (currentKey.isNotBlank()) {
            headerMap[currentKey.lowercase()] = currentValue.toString()
        }

        val contentType = headerMap["content-type"]
        val boundary = contentType?.let { extractBoundary(it) }

        val toList = headerMap["to"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        return EmailHeaders(
            from = headerMap["from"],
            to = toList,
            subject = headerMap["subject"],
            contentType = contentType,
            boundary = boundary
        )
    }

    fun extractAttachments(body: String, boundary: String?): List<AttachmentInfo> {
        if (boundary == null) return emptyList()

        val attachments = mutableListOf<AttachmentInfo>()
        val parts = body.split("--$boundary")

        for (part in parts) {
            val filename = extractFilename(part) ?: continue
            val base64Size = estimateBase64DataSize(part)
            val originalSize = ((base64Size * 3) / 4).toInt()
            attachments.add(AttachmentInfo(filename, originalSize))
        }

        return attachments
    }

    private fun extractBoundary(contentType: String): String? {
        val match = Regex("boundary=\"?([^\"\\s;]+)\"?", RegexOption.IGNORE_CASE)
            .find(contentType)
        return match?.groupValues?.get(1)
    }

    private fun extractFilename(part: String): String? {
        val filenameMatch = Regex("filename=\"?([^\"\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
            .find(part)
        if (filenameMatch != null) return filenameMatch.groupValues[1].trim()

        val nameMatch = Regex("name=\"?([^\"\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
            .find(part)
        return nameMatch?.groupValues?.get(1)?.trim()
    }

    private fun estimateBase64DataSize(part: String): Int {
        val lines = part.split("\r\n", "\n")
        var inBody = false
        var size = 0

        for (line in lines) {
            if (inBody) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("--")) {
                    size += trimmed.length
                }
            } else if (line.isBlank()) {
                inBody = true
            }
        }

        return size
    }
}
