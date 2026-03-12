package com.netaudit.alert

import com.netaudit.event.AuditEventBus
import com.netaudit.model.AlertRecord
import com.netaudit.model.AlertRule
import com.netaudit.model.AuditEvent
import com.netaudit.storage.AlertRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * 告警引擎。
 *
 * 监听审计事件流，按规则生成告警并写入存储，同时广播给前端。
 *
 * @param eventBus 审计事件总线
 * @param alertRepository 告警仓储
 * @param scope 协程作用域
 * @param rules 告警规则列表
 */
class AlertEngine(
    private val eventBus: AuditEventBus,
    private val alertRepository: AlertRepository,
    private val scope: CoroutineScope,
    private val rules: List<AlertRule> = DefaultAlertRules.all()
) {
    /**
     * 启动告警引擎，返回协程任务句柄。
     *
     * @return 处理协程的 Job
     */
    fun start(): Job {
        return scope.launch {
            logger.info { "AlertEngine started with ${rules.size} rules" }
            eventBus.auditEvents.collect { event ->
                for (rule in rules) {
                    try {
                        if (rule.condition(event)) {
                            val alert = AlertRecord(
                                id = generateId(),
                                timestamp = Clock.System.now(),
                                level = rule.level,
                                ruleName = rule.name,
                                message = buildMessage(rule, event),
                                auditEventId = event.id,
                                protocol = event.protocol
                            )

                            eventBus.emitAlert(alert)
                            try {
                                alertRepository.save(alert)
                            } catch (e: Exception) {
                                logger.error(e) { "Failed to save alert: ${e.message}" }
                            }

                            logger.warn { "ALERT [${rule.level}] ${rule.name}: ${alert.message}" }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Alert rule '${rule.name}' error: ${e.message}" }
                    }
                }
            }
        }
    }

    private fun buildMessage(rule: AlertRule, event: AuditEvent): String {
        return "${rule.description} | src=${event.srcIp} dst=${event.dstIp} protocol=${event.protocol}"
    }

    private fun generateId(): String = UUID.randomUUID().toString()
}
