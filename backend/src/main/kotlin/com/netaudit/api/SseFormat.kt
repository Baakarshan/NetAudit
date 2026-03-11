package com.netaudit.api

/**
 * 生成 SSE 注释行。
 *
 * 注释用于心跳或调试信息，不会触发前端事件。
 */
internal fun sseComment(message: String): String = ": $message\n\n"

/**
 * 生成 SSE 事件文本。
 *
 * @param eventName 事件名称
 * @param data 事件数据（应为已序列化字符串）
 */
internal fun sseEvent(eventName: String, data: String): String =
    "event: $eventName\n" +
        "data: $data\n\n"
