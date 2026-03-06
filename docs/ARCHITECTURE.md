# NetAudit 架构概览

## 总体目标
NetAudit 是一个基于 Kotlin 协程的实时网络审计系统。系统从网卡抓包开始，完成 L2-L7 解码、协议识别与解析，最终输出审计事件并提供 API / WebSocket 访问。

## 模块划分
- capture：负责 Pcap4J 抓包，输出原始 Packet 流
- decode：负责 L2-L4 解码，产出 PacketMetadata
- stream：负责 TCP 流重组与会话状态管理
- parser：按端口分发并解析各类应用层协议
- event：审计事件与告警事件的共享总线
- storage：审计与告警数据持久化（Exposed + PostgreSQL）
- api：HTTP API 与 WebSocket 推送

## 数据流管道
1. CaptureEngine 抓取原始包并写入 Channel
2. PacketDecoder 将包解码为 PacketMetadata
3. TcpStreamTracker 对 TCP 包做流重组，对 UDP 包直接分发
4. ProtocolParser 解析为 AuditEvent
5. AuditEventBus 负责事件流转与订阅

## 数据存储
- packets 表：保存基础包元数据
- 各协议会话表：保存协议特定字段（HTTP / FTP 等）
- 通过 Exposed DSL 统一定义，数据库使用 PostgreSQL

## 运行时配置
- `application.conf` 提供数据库与捕获配置
- `AppConfig` 做类型安全映射
- 日志使用 kotlin-logging + logback

## 与 Spec 对齐
- Spec 01：项目骨架、数据模型、接口与序列化
- Spec 02：捕获引擎、解码链、流重组与分发
- Spec 03+：存储、协议解析、接口与前端展示
