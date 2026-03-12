package com.netaudit.parser.email

/**
 * 邮件头解析与附件提取工具。
 */
object EmailHeaderParser {
    /**
     * 邮件头字段聚合结果。
     */
    data class EmailHeaders(
        val from: String? = null,
        val to: List<String> = emptyList(),
        val subject: String? = null,
        val contentType: String? = null,
        val boundary: String? = null
    )

    /**
     * 解析到的附件信息。
     */
    data class AttachmentInfo(
        val filename: String,
        val estimatedSize: Int
    )

    /**
     * 解析邮件头文本。
     *
     * 支持多行折叠字段，并提取常见字段与 boundary。
     *
     * @param headerText 邮件头原始文本
     * @return 解析后的头字段集合
     */
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

    /**
     * 基于 boundary 从邮件正文中提取附件信息。
     *
     * @param body 邮件正文
     * @param boundary boundary 标识
     * @return 附件信息列表
     */
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

    /**
     * 从 Content-Type 中提取 boundary。
     *
     * @param contentType Content-Type 头
     * @return boundary 字符串；不存在则返回 null
     */
    private fun extractBoundary(contentType: String): String? {
        val match = Regex("boundary=\"?([^\"\\s;]+)\"?", RegexOption.IGNORE_CASE)
            .find(contentType)
        return match?.groupValues?.get(1)
    }

    /**
     * 从 MIME 片段中提取附件文件名。
     *
     * @param part MIME 片段
     * @return 文件名；不存在则返回 null
     */
    private fun extractFilename(part: String): String? {
        val filenameMatch = Regex("filename=\"?([^\"\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
            .find(part)
        if (filenameMatch != null) return filenameMatch.groupValues[1].trim()

        val nameMatch = Regex("name=\"?([^\"\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
            .find(part)
        return nameMatch?.groupValues?.get(1)?.trim()
    }

    /**
     * 估算 base64 数据长度，用于推算附件大小。
     *
     * @param part MIME 片段
     * @return base64 数据长度
     */
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
