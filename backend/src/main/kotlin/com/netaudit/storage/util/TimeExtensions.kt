package com.netaudit.storage.util

import kotlinx.datetime.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * kotlinx.datetime.Instant → java.time.OffsetDateTime (UTC)
 *
 * @return UTC 时区的 OffsetDateTime
 */
fun Instant.toJavaOffsetDateTime(): OffsetDateTime =
    OffsetDateTime.ofInstant(
        java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()),
        ZoneOffset.UTC
    )

/**
 * java.time.OffsetDateTime → kotlinx.datetime.Instant
 *
 * @return Kotlin Instant（UTC）
 */
fun OffsetDateTime.toKotlinxInstant(): Instant =
    Instant.fromEpochSeconds(toEpochSecond(), nano)
