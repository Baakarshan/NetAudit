-- NetAudit 数据库初始化脚本

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 设置时区
SET timezone = 'Asia/Shanghai';

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    event_id      VARCHAR(36) UNIQUE NOT NULL,
    protocol      VARCHAR(10) NOT NULL,
    src_ip        VARCHAR(45) NOT NULL,
    dst_ip        VARCHAR(45) NOT NULL,
    src_port      INTEGER NOT NULL,
    dst_port      INTEGER NOT NULL,
    alert_level   VARCHAR(10) NOT NULL DEFAULT 'INFO',
    captured_at   TIMESTAMPTZ NOT NULL,
    details       JSONB NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_protocol ON audit_logs(protocol);
CREATE INDEX IF NOT EXISTS idx_audit_captured_at ON audit_logs(captured_at);
CREATE INDEX IF NOT EXISTS idx_audit_src_ip ON audit_logs(src_ip);
CREATE INDEX IF NOT EXISTS idx_audit_details ON audit_logs USING GIN (details);

-- 告警表
CREATE TABLE IF NOT EXISTS alerts (
    id             BIGSERIAL PRIMARY KEY,
    alert_id       VARCHAR(36) UNIQUE NOT NULL,
    timestamp      TIMESTAMPTZ NOT NULL,
    level          VARCHAR(10) NOT NULL,
    rule_name      VARCHAR(100) NOT NULL,
    message        TEXT NOT NULL,
    audit_event_id VARCHAR(36) NOT NULL,
    protocol       VARCHAR(10) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alert_level ON alerts(level);
CREATE INDEX IF NOT EXISTS idx_alert_timestamp ON alerts(timestamp);

COMMENT ON TABLE audit_logs IS '审计日志（所有协议统一存储）';
COMMENT ON TABLE alerts IS '告警记录表';
