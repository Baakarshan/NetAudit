-- NetAudit 数据库初始化脚本

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 设置时区
SET timezone = 'Asia/Shanghai';

-- 创建数据包表（基础表）
CREATE TABLE IF NOT EXISTS packets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp TIMESTAMP NOT NULL,
    protocol VARCHAR(20) NOT NULL,
    src_ip VARCHAR(45) NOT NULL,
    dst_ip VARCHAR(45) NOT NULL,
    src_port INTEGER,
    dst_port INTEGER,
    payload_size INTEGER NOT NULL,
    raw_data BYTEA,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_packets_timestamp ON packets(timestamp);
CREATE INDEX idx_packets_protocol ON packets(protocol);
CREATE INDEX idx_packets_src_ip ON packets(src_ip);
CREATE INDEX idx_packets_dst_ip ON packets(dst_ip);

-- HTTP 会话表
CREATE TABLE IF NOT EXISTS http_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    packet_id UUID REFERENCES packets(id),
    method VARCHAR(10),
    url TEXT,
    host VARCHAR(255),
    user_agent TEXT,
    status_code INTEGER,
    content_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- FTP 会话表
CREATE TABLE IF NOT EXISTS ftp_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    packet_id UUID REFERENCES packets(id),
    command VARCHAR(20),
    argument TEXT,
    response_code INTEGER,
    response_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 其他协议表将在后续 Spec 中定义

COMMENT ON TABLE packets IS '网络数据包基础表';
COMMENT ON TABLE http_sessions IS 'HTTP 协议会话表';
COMMENT ON TABLE ftp_sessions IS 'FTP 协议会话表';
