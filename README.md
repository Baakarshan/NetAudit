# NetAudit — 实时网络审计系统

基于 Kotlin 协程的实时网络审计系统。通过抓包、协议解析与事件总线，将网络流量转化为可查询的审计事件与告警，并提供 API、SSE 与 WebSocket 的实时访问能力。

## 核心功能与能力
- 在线抓包（网卡）与离线 pcap 回放
- L2-L4 解码生成 `PacketMetadata` 元数据
- TCP 流重组与会话状态维护，UDP 直通解析
- 端口路由与协议解析（HTTP/FTP/TELNET/DNS/SMTP/POP3/TLS）
- TLS/HTTPS 握手解析（SNI/ALPN/版本）
- 事件总线分发（审计事件/告警事件）与下游并行订阅
- 批量写库与告警持久化，支持失败重试
- 实时推送（SSE/WebSocket）与查询统计 API
- 实时指标面板（QPS/近 1 分钟/活跃协议）
- 统一审计事件模型与多态 JSON 序列化（`AppJson`）
- PostgreSQL JSONB 存储与 GIN 索引
- 捕获与告警参数可配置并支持环境变量覆盖
- 流清理与背压监控（超时清理、丢包计数）

## 架构与数据流

端到端数据路径：

```
CaptureEngine → PacketDecoder → TcpStreamTracker → ProtocolParser → AuditEventBus
                                                            ↘ BatchWriter → audit_logs
                                                            ↘ AlertEngine → alerts
                                                            ↘ SSE / WebSocket
```

数据流步骤：
1. `PacketCaptureEngine` 在线抓包或离线读取 pcap，写入 `Channel<Packet>`。
2. `AuditPipeline` 消费 Channel，`PacketDecoder` 解析到 `PacketMetadata`。
3. `TcpStreamTracker` 按 TCP/UDP 分支处理，TCP 进行流重组并维护 `sessionState`。
4. `ParserRegistry` 根据端口路由到 `ProtocolParser`，生成 `AuditEvent`。
5. `AuditEventBus` 分发审计事件，下游并行订阅。
6. `BatchWriter` 批量写入 `audit_logs`。
7. `AlertEngine` 生成 `AlertRecord`，写入 `alerts` 并分发告警流。
8. `SSE/WebSocket` 订阅事件流实现实时推送。

关键约定：
- `AuditEvent` 统一使用客户端视角的 `srcIp/srcPort` 与服务端视角的 `dstIp/dstPort`
- TCP 通过流重组与会话状态解析，UDP 无需重组直接解析
- 事件流由 `AuditEventBus` 分发，多个下游并行订阅

关键控制点：
- 抓包使用 `Channel` 缓冲，满时丢包计数用于背压观测
- 批量写库按数量与时间双触发（`batchSize`/`flushIntervalMs`）
- 定时清理超时 TCP 流（默认 60 秒）
- 离线回放结束关闭 Channel，驱动管道自然退出
- 解析异常在边界捕获，避免阻塞主流程

模块职责：
- `capture`：抓包与离线回放
- `decode`：L2-L4 解码并生成 `PacketMetadata`
- `stream`：TCP 流重组与会话状态管理，UDP 直通
- `parser`：按端口路由并解析协议（含 TLS 握手元数据）
- `event`：审计/告警事件总线
- `storage`：Exposed + PostgreSQL 持久化
- `alert`：告警规则与告警生成
- `api`：HTTP API、SSE、WebSocket
- `pipeline`：捕获与解析的编排入口

## 项目结构（重点路径）

```
net-audit/
├── backend/                      # 后端服务（Ktor + 解析管线）
│   ├── src/main/kotlin/com/netaudit/
│   │   ├── Main.kt               # 启动与模块装配
│   │   ├── pipeline/             # 捕获→解析→分发的管线编排
│   │   ├── capture/              # 抓包与离线回放入口
│   │   ├── decode/               # L2-L4 解码
│   │   ├── stream/               # TCP 流重组与会话状态
│   │   ├── parser/               # 协议解析器（HTTP/FTP/TELNET/DNS/SMTP/POP3）
│   │   ├── event/                # 事件总线（审计/告警）
│   │   ├── alert/                # 告警规则与引擎
│   │   ├── storage/              # 数据库存取与批量写入
│   │   ├── api/                  # HTTP/SSE/WebSocket 路由
│   │   └── model/                # 统一数据模型
│   └── src/main/resources/       # application.conf 与日志配置
├── frontend/                     # 前端展示（Vue 3）
│   └── src/                      # 视图/组件/状态管理/接口封装
├── docker/                       # Docker 构建与测试环境
│   ├── docker-compose.yml        # 全量编排（含测试服务）
│   ├── Dockerfile.backend        # 后端镜像
│   ├── Dockerfile.frontend       # 前端镜像
│   ├── Dockerfile.test-client    # 测试客户端
│   ├── init.sql                  # 数据库初始化脚本
│   └── test-servers/             # 协议测试服务（nginx/ftp/telnet/dns/smtp/pop3）
├── scripts/                      # 测试脚本与辅助脚本
├── test-data/                    # pcap 与期望结果
├── specs/                        # 规格文档
```

## 接口总览

HTTP API：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查，返回 `{status: ok}` |
| GET | `/api/audit/logs` | 审计日志分页查询 |
| GET | `/api/audit/recent` | 最近审计事件 |
| GET | `/api/audit/{id}` | 按事件 ID 查询 |
| GET | `/api/alerts/recent` | 最近告警 |
| GET | `/api/alerts/stats` | 告警统计 |
| GET | `/api/stats/dashboard` | 总览统计 |
| GET | `/api/stats/protocols` | 协议统计 |

`/api/audit/logs` 查询参数：
- `page`：页码，默认 0
- `size`：每页数量，默认 50，范围 1-200
- `protocol`：协议名，支持 `HTTP/FTP/TELNET/DNS/SMTP/POP3/TLS`
- `srcIp`：源 IP 精确匹配
- `start`/`end`：ISO-8601 时间，闭区间

筛选优先级：`protocol` → `srcIp` → `start+end` → 全量分页。

示例请求：
- `/api/audit/logs?page=0&size=50&protocol=HTTP`
- `/api/audit/logs?srcIp=192.168.1.10`
- `/api/audit/logs?start=2024-01-01T00:00:00Z&end=2024-01-02T00:00:00Z`
- `/api/stats/dashboard`

`/api/audit/recent` 与 `/api/alerts/recent` 查询参数：
- `limit`：默认 20，范围 1-100

SSE：
- 地址：`GET /api/sse/events`
- 事件类型：`audit`、`alert`
- 数据格式：`AppJson` 序列化后的 JSON

WebSocket：
- 地址：`/ws/capture`
- 入站：发送 `ping` 返回 `pong`
- 出站：`AuditEvent` JSON 文本流

返回结构约定：
- 审计事件：基础字段 `id/timestamp/srcIp/dstIp/srcPort/dstPort/protocol/alertLevel` + 协议特有字段
- 告警事件：`id/timestamp/level/ruleName/message/auditEventId/protocol`
- 统计接口：`{ totalEvents, protocolCounts, alertCounts }`
- 时间字段统一为 ISO-8601（UTC 或带时区偏移）

常见错误：
- `/api/audit/{id}`：id 为空返回 400，不存在返回 404
- 时间参数解析失败会导致 400（`Instant.parse`）

## 数据存储

表结构与索引位于 `docker/init.sql`，核心表如下：

`audit_logs` 字段：
- `event_id`、`protocol`、`src_ip`、`dst_ip`、`src_port`、`dst_port`
- `alert_level`、`captured_at`（事件时间）
- `details`：JSONB，完整事件内容（`AuditEvent`，含 `protocol` 作为多态判别）
- `created_at`：数据库写入时间

`alerts` 字段：
- `alert_id`、`timestamp`（告警时间）、`level`、`rule_name`、`message`
- `audit_event_id`、`protocol`
- `created_at`：数据库写入时间

索引策略：
- `audit_logs` 以 `protocol`、`captured_at`、`src_ip` 索引
- `details` 使用 JSONB GIN 索引（PostgreSQL）
- `alerts` 以 `level` 与 `timestamp` 索引

数据库一致性说明：
- PostgreSQL 下 `details` 强制转换为 JSONB 并创建 GIN 索引
- 测试数据库（H2）以文本存储 `details`，但序列化仍使用 `AppJson`

## 设计模式落点（功能/原因/实现/收益）
1. `capture/PacketCaptureEngine`：模板方法。功能是统一抓包生命周期；这样设计是为了复用在线/离线流程骨架；当前通过 `startLive/startOffline/stop` 串起 open/loop/close；收益是流程一致、实现可替换且易测试。
2. `capture/PacketSourceFactory`：工厂方法。功能是创建在线/离线抓包源；这样设计是为了隔离 Pcap4J 细节；当前用 `openLive/openOffline` 返回 `PacketSource`；收益是调用端解耦、扩展新来源更容易。
3. `decode/PacketDecoder`：适配器。功能是把 Pcap4J 包转换为 `PacketMetadata`；这样设计是为了统一上层数据结构；当前解析 Ethernet/IPv4/TCP/UDP 并做兜底；收益是上层解析器不依赖底层库。
4. `parser/ParserRegistry`：注册表。功能是端口→解析器路由；这样设计是为了 O(1) 路由与集中注册；当前维护 `portToParser` 映射；收益是路由快、扩展解析器成本低。
5. `parser/ProtocolParser`：策略模式（协议）。功能是按协议分离解析逻辑；这样设计是为了避免巨大分支与提升可扩展性；当前每个协议一个实现；收益是协议并行开发与独立测试。
6. `alert/DefaultAlertRules`：策略模式（告警）。功能是以规则条件驱动告警；这样设计是为了配置化告警逻辑；当前规则集合逐条匹配事件；收益是新增/调整规则不影响主流程。
7. `event/AuditEventBus`：发布订阅。功能是事件分发；这样设计是为了让存储、告警、推送解耦；当前用 `SharedFlow` 多订阅者并行消费；收益是下游可独立扩展而不改上游。
8. `pipeline/AuditPipeline`：管道模式。功能是串联捕获→解码→流重组→解析→分发；这样设计是为了清晰线性数据流；当前协程消费 Channel 并分发到 TCP/UDP 分支；收益是链路可观测且易定位问题。
9. `storage/BatchWriter`：批处理。功能是批量写库；这样设计是为了提升吞吐并减少数据库压力；当前按数量与时间双触发并带重试；收益是写入稳定且性能更优。
10. `storage/*Repository`：仓储模式。功能是隔离存储实现与查询接口；这样设计是为了便于替换数据库与测试；当前用 Exposed 实现；收益是业务层不绑定具体 ORM。
11. `model/AuditEvent`：领域模型。功能是统一多协议事件表示；这样设计是为了统一序列化与前端消费；当前用 sealed class + `AppJson` 多态；收益是跨协议字段一致且扩展子类成本低。
12. `Application.module`：轻量依赖注入。功能是集中装配依赖；这样设计是为了测试替换与可配置启动；当前通过参数注入 repo/bus/pipeline；收益是测试可控、减少全局单例耦合。

## 开发规范

编码规范：
- 遵循 Kotlin 官方代码风格
- 单一职责与清晰边界，避免业务逻辑混杂
- 协程采用结构化并发，避免全局协程泄漏
- 解析器只做协议解析，不做网络/数据库 I/O
- 统一使用 `AppJson` 进行序列化与反序列化
- 事件方向统一：`src` 始终为客户端，`dst` 始终为服务端

配置规范：
- `application.conf` 作为默认配置源
- 环境变量覆盖：`DATABASE_*`、`CAPTURE_*`、`ALERT_ENABLED`
- 线上部署优先使用环境变量注入敏感信息

日志规范：
- 统一使用 `kotlin-logging`
- 关键路径输出结构化日志，错误日志必须含上下文
- 抓包异常与解析异常只记录，不影响主流程

测试规范：
- 单元测试使用 `kotest` 与 `mockk`
- 覆盖关键路径：解析、存储、事件分发、API
- 协议相关优先使用离线 pcap 回放与脚本回放

提交规范：
- `feat:` 新功能
- `fix:` 修复问题
- `docs:` 文档更新
- `refactor:` 重构
- `test:` 测试相关
- `chore:` 工具或配置变更

## 二次开发指南

新增协议解析器：
- 实现 `ProtocolParser`，补齐解析与会话状态
- 明确 `ports` 集合，确保路由可命中
- 遵守方向约定：事件 `src` 为客户端、`dst` 为服务端
- 在 `Main.kt` 注册解析器
- 添加单元测试与样例输入

扩展审计事件模型：
- 在 `AuditEvent` 新增字段或子类
- 同步 `AppJson` 多态配置
- 更新前端渲染与查询逻辑

新增告警规则：
- 在 `DefaultAlertRules` 增加规则
- 保证输出信息包含 src/dst 与协议

新增 API：
- 在 `api/` 增加路由文件并挂载
- 约束输入边界与错误码
- 返回结构应保持可序列化与稳定字段名

数据库变更：
- 修改 `storage/tables` 与 `docker/init.sql`
- 保持 JSONB 类型与索引一致
- 如有字段新增，补齐迁移与回滚方案

前端扩展：
- 在 `frontend/src/api` 添加接口封装
- 在 `views` 增加页面，在 `stores` 管理状态
- 组件拆分保持复用，避免巨型页面组件

## 二次开发大模型提示词（示例）

以下提示词适用于交接给 Codex 等模型进行二次开发：

```
你是项目协作者，请在 e:/CodeSpace/net-audit 工作。
要求：
1. 保证中文不乱码，输出简体中文。
2. 必须使用 MCP 工具读取/写入文件与获取信息。
3. 修改前先读相关代码与文档，避免猜测。
4. 每次小步修改都要提交，提交信息使用中文 Conventional Commit（如 docs/feat/fix）。
5. 提交信息与正文中不要出现规格编号或内部代号。
6. 避免破坏性 git 操作，除非明确要求。
7. 变更后优先运行相关测试，并在回复中说明结果。
8. 遵循现有架构：Capture → Decode → Stream → Parser → EventBus → Storage/API。
9. 如需新增协议解析器，必须注册并补齐测试。
```

## 保姆级使用指南

### 方式 A：Docker 一键启动（推荐）

适用场景：答辩演示、端到端联调、无需本机安装数据库与抓包依赖。

1. 准备环境文件。

```
cd docker
cp .env.example .env
```

2. 可选修改 `.env`（端口/数据库账号）。不改也能直接启动。

3. 先生成后端 fat jar（Docker 后端镜像使用本地产物，避免网络下载依赖失败）。

```
cd backend
./gradlew shadowJar -x test
```

Windows 使用 `gradlew.bat shadowJar -x test`。

4. 构建并启动全部服务（包含 6 个协议测试服务）。

```
cd docker
docker compose up -d --build
```

5. 等待服务就绪。

```
docker compose ps
docker compose logs -f backend
```

看到 `NetAudit starting` 与 `Capture started` 即就绪。

6. 打开页面与接口验证。

```
http://localhost:5173
http://localhost:8080/health
```

7. 生成测试流量（全协议一键）。

```
docker exec netaudit-test-client bash /scripts/test-all-protocols.sh
```

8. 观察统计是否变化（判定是否入库）。

```
curl http://localhost:8080/api/stats/dashboard
```

若 `totalEvents` > 0，说明抓包与入库正常。Dashboard 中的实时指标（QPS/近 1 分钟/活跃协议）会同步变化。

9. 停止与清理。

```
docker compose down -v
```

关键说明：
- `docker-compose.yml` 已启用抓包所需权限（`NET_RAW`/`NET_ADMIN`）。
- 测试服务包含 nginx/ftp/telnet/dns/smtp/pop3 与 test-client。
- `test-client` 使用 `network_mode: service:backend` 共享网络栈，确保后端能捕获它产生的流量。
- 如 `test-client` 只看到 `lo` 网卡，需重建它以绑定到后端网络栈：

```
docker compose up -d --no-deps --force-recreate test-client
```

- 默认端口：前端 5173、后端 8080、数据库 5432（可在 `.env` 覆盖）。

### 方式 B：本地开发（含真实实时抓包 / 操作A）

本地依赖：
- JDK 21
- Gradle 8+
- PostgreSQL 15+
- libpcap/Npcap
- Node.js 20+

适用：要抓**宿主机浏览器流量**，必须走本方式；Docker 方案只能抓容器内流量。

1. 启动数据库（可用 Docker）。

```
cd docker
docker compose up -d postgres
```

2. 设置抓包网卡（Windows 示例）。

先查出 Npcap 设备名：

```
Get-NetAdapter | Where-Object { $_.Status -eq "Up" } | ForEach-Object { "$($_.Name) -> \\Device\\NPF_{$($_.InterfaceGuid)}" }
```

然后设置环境变量：

```
$env:CAPTURE_INTERFACE="\\Device\\NPF_{GUID}"
```

Linux/Mac 通常直接使用 `eth0`。

3. 关闭浏览器 QUIC（否则 HTTPS 走 UDP 443，当前不会解析）。

- Chrome/Edge：打开 `chrome://flags/#enable-quic`，设为 `Disabled`，重启浏览器。

4. 启动后端（如端口 8080 被占用，改为 8081）。

```
cd backend
$env:JAVA_TOOL_OPTIONS="-Dktor.deployment.port=8081"
./gradlew.bat run
```

Linux/Mac：

```
export JAVA_TOOL_OPTIONS="-Dktor.deployment.port=8081"
./gradlew run
```

5. 启动前端并指向后端端口。

方式 1：直接指定 API 基址。

```
cd frontend
npm install
$env:VITE_API_BASE="http://127.0.0.1:8081"
$env:VITE_WS_URL="ws://127.0.0.1:8081"
npm run dev -- --host 127.0.0.1 --port 5174
```

方式 2：使用 Vite 代理（`vite.config.ts` 代理到 8081）。

```
$env:VITE_API_BASE="/"
$env:VITE_WS_URL="ws://127.0.0.1:8081"
```

6. 验证实时链路。

```
Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/stats/dashboard"
curl.exe -s -N --max-time 3 http://127.0.0.1:8081/api/sse/events
```

看到 `: connected` 且 `totalEvents` 增长，即实时链路 OK。

7. 浏览器访问 HTTPS 站点后检查 TLS 事件。

```
Invoke-WebRequest -Uri "https://example.com" -UseBasicParsing | Out-Null
Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/audit/recent?limit=5"
```

预期：出现 `protocol=TLS` 事件，字段包含 `serverName`/`alpn`/`clientVersion`。

### 方式 C：离线回放（无需真实网络流量）

适用场景：开发调试、协议解析回归测试。

1. 准备 pcap 文件（示例：`test-data/sample.pcap`）。

2. 运行离线回放测试（需要可用数据库）。

```
cd backend
./gradlew test --tests "*PcapReplayTest*"
```

3. 如需完整链路入库验证，执行集成测试（需设置环境变量）。

```
$env:INTEGRATION_DATABASE_URL="jdbc:postgresql://localhost:5432/netaudit"
$env:INTEGRATION_DATABASE_USER="netaudit"
$env:INTEGRATION_DATABASE_PASSWORD="netaudit"
./gradlew test --tests "*FullPipelineIntegrationTest*"
```

## 运行配置

默认值见 `backend/src/main/resources/application.conf`。

可用环境变量覆盖：
- `DATABASE_URL`
- `DATABASE_DRIVER`
- `DATABASE_USER`
- `DATABASE_PASSWORD`
- `DATABASE_MAX_POOL_SIZE`
- `CAPTURE_INTERFACE`（默认 `eth0`，Windows 建议使用 Pcap4J 列表中的 `\Device\NPF_{GUID}`，也可用描述关键词）
- `CAPTURE_PROMISCUOUS`（默认 `true`）
- `CAPTURE_SNAPSHOT_LENGTH`（默认 `65536`）
- `CAPTURE_READ_TIMEOUT`（默认 `100`，毫秒）
- `CAPTURE_CHANNEL_BUFFER_SIZE`（默认 `4096`）
- `ALERT_ENABLED`（默认 `true`）

## 协议显示与解析边界（重要）

当前解析协议范围：HTTP、FTP、TELNET、DNS、SMTP、POP3、TLS。

HTTPS/QUIC 流量会被加密，系统不会还原 URL 或正文，因此你通常会看到 DNS 事件占比很高。这是正常现象。
系统会输出 TLS 事件（SNI/ALPN/版本），但不会解密正文。

要看到 HTTP 事件，请访问明文 HTTP 站点，例如：

```
http://example.com
http://neverssl.com
```

## 协议流量验证（确保各协议可显示）

方式 1：Docker 一键生成（推荐，覆盖全部协议）

```
cd docker
docker compose up -d --build
docker exec netaudit-test-client bash /scripts/test-all-protocols.sh
```

然后验证协议统计是否全部出现：

```
curl http://localhost:8080/api/stats/protocols
```

方式 2：本地手动生成（Windows 示例）

HTTP：
```
Invoke-WebRequest -Uri "http://example.com" -UseBasicParsing | Out-Null
```

TLS（SNI/ALPN）：
```
Invoke-WebRequest -Uri "https://example.com" -UseBasicParsing | Out-Null
```

DNS：
```
Resolve-DnsName "openai.com" | Out-Null
```

TELNET（需启用 Windows Telnet 客户端）：
```
telnet 127.0.0.1 23
```

FTP（需本地或 Docker 启动测试 FTP 服务）：
```
ftp 127.0.0.1 21
```

SMTP/POP3（推荐使用 Docker 测试服务）：
- `test-smtp` 与 `test-pop3` 在 docker 编排中已包含。

## 测试与质量

后端测试：
```
cd backend
./gradlew test
./gradlew jacocoTestReport
```

后端构建：
```
cd backend
./gradlew build
```

覆盖率报告：
- `backend/build/reports/jacoco/test/html/index.html`

前端构建与检查：
```
cd frontend
npm run build
npm run lint
```

Docker 构建与联调：
```
cd docker
docker compose build
docker compose up -d
```

## 常见问题

权限不足无法抓包：
- Linux 需 root 或为进程赋予抓包权限
- Docker 已通过 `cap_add` 启用抓包能力

数据库连接失败：
- 确认 `DATABASE_URL` 与端口映射
- 先检查 `docker compose ps` 是否健康

前端无数据：
- 确认后端 `GET /health` 正常
- 确认是否有抓包流量或运行测试脚本

## 许可证

MIT License
