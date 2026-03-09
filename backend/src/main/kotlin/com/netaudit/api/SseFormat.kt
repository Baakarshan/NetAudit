package com.netaudit.api

internal fun sseComment(message: String): String = ": $message\n\n"

internal fun sseEvent(eventName: String, data: String): String =
    "event: $eventName\n" +
        "data: $data\n\n"
