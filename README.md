# NetAudit — 实时网络审计系统

基于 Kotlin 协程的实时网络审计系统。通过抓包、协议解析与事件总线，将网络流量转化为统一的审计事件与告警，并提供 REST/SSE/WebSocket 的实时访问能力与可视化面板。

本项目定位：
- **真实流量可观测**：支持宿主机网卡抓包，能看到浏览器/系统真实网络行为。
- **可重复演示**：提供 Docker 全协议测试服务与脚本，一键生成可验证流量。
- **统一事件模型**：跨协议字段对齐，前端展示与查询无需了解底层方向差异。

## 实现原理

系统将“原始网络包”流水线式转化为“可查询的审计事件”：

1. **抓包入口**：`PacketCaptureEngine` 使用 Pcap4J 在指定网卡抓包（混杂模式），将 `Packet` 写入 `Channel`，避免阻塞抓包线程。
2. **L2-L4 解码**：`PacketDecoder` 将 `Packet` 解析为 `PacketMetadata`，抽取 MAC、IP、端口、TCP 标志与 payload。
3. **TCP 流重组**：`TcpStreamTracker` 维护 TCP 会话缓冲与状态，按方向追加数据，并保持 `sessionState` 供解析器跨包关联。
4. **协议解析**：`ParserRegistry` 以端口路由解析器（策略模式），由 `ProtocolParser` 生成 `AuditEvent`。
5. **事件分发**：`AuditEventBus` 使用 `SharedFlow` 广播审计事件，存储、告警、推送并行消费。
6. **存储与告警**：`BatchWriter` 按批量写入 `audit_logs`；`AlertEngine` 执行规则并写入 `alerts`。
7. **对外访问**：REST API 提供历史查询；SSE/WebSocket 提供实时事件流。

核心不变式：
- `AuditEvent` 始终以**客户端为 src**、**服务端为 dst**，无论包方向如何，前端查询无需关心方向。
- UDP 直通解析，TCP 需要流重组与状态机。
- 事件总线解耦下游，任何模块故障都不阻塞抓包主链路。

## 核心功能与能力

### 采集与解码
- 在线抓包与离线 pcap 回放
- 兼容 Windows Npcap / Linux libpcap
- L2-L4 解码生成 `PacketMetadata`
- Channel 背压与丢包统计（可观测）

### 流重组与协议解析
- TCP 流重组、会话状态维护、超时清理
- UDP 直通解析
- 协议解析覆盖：HTTP / FTP / TELNET / DNS / SMTP / POP3 / TLS
- TLS 握手解析（SNI / ALPN / 版本），不解密正文

### 存储与查询
- PostgreSQL + Exposed
- JSONB 存储协议特有字段，GIN 索引支持查询
- 批量写库与失败重试
- 统计接口：总量、协议分布、告警分布

### 实时推送与可视化
- SSE 与 WebSocket 双通道推送
- Dashboard 实时统计、协议分布图、流量时间线
- 最近事件表与告警中心

### 运维与测试
- Docker Compose 一键启动全协议测试环境
- 全协议测试脚本与宿主机脚本
- 离线回放测试与覆盖率报告

## 架构与数据流

端到端数据路径：

```
CaptureEngine → PacketDecoder → TcpStreamTracker → ProtocolParser → AuditEventBus
                                                            ↘ BatchWriter → audit_logs
                                                            ↘ AlertEngine → alerts
                                                            ↘ SSE / WebSocket
```

数据流解释：
- **抓包协程**只负责高频读取与入 Channel。
- **解码协程**从 Channel 消费并解析为 `PacketMetadata`。
- **流重组层**决定方向与会话状态，并调用解析器。
- **事件总线**把事件广播给存储、告警与推送。

关键控制点：
- `Channel` 作为背压边界，抓包永不阻塞。
- `BatchWriter` 按“数量/时间”双触发写库。
- `TcpStreamTracker` 定时清理超时会话，避免内存膨胀。
- SSE 使用流式输出，WebSocket 适合双向交互与心跳。

## 项目结构（重点路径）

```
net-audit/
├── backend/                          # 后端（Ktor + 解析管线）
│   ├── src/main/kotlin/com/netaudit/
│   │   ├── Main.kt                   # 启动与模块装配入口
│   │   ├── config/                   # 配置加载与默认值
│   │   ├── capture/                  # 抓包入口与来源工厂
│   │   ├── decode/                   # L2-L4 解码与统一元数据
│   │   ├── stream/                   # TCP 流重组与会话状态
│   │   ├── parser/                   # 协议解析器实现
│   │   ├── event/                    # 审计/告警事件总线
│   │   ├── alert/                    # 告警规则与告警引擎
│   │   ├── storage/                  # Exposed 仓储与批量写入
│   │   ├── api/                      # REST/SSE/WebSocket 路由
│   │   └── model/                    # 统一事件与枚举模型
│   └── src/main/resources/           # application.conf 与日志配置
├── frontend/                         # 前端展示（Vue 3 + Pinia + ECharts）
│   ├── src/views/                    # Dashboard/Audit/Alerts 页面
│   ├── src/components/               # 统计卡片/图表/表格组件
│   ├── src/stores/                   # 状态管理与时间线
│   └── src/api/                      # REST API 封装
├── docker/                           # Docker 与测试环境
│   ├── docker-compose.yml            # 全量编排（含测试服务）
│   ├── Dockerfile.backend            # 后端镜像
│   ├── Dockerfile.frontend           # 前端镜像
│   ├── Dockerfile.test-client        # 测试客户端镜像
│   ├── init.sql                      # 数据库初始化
│   └── test-servers/                 # 协议测试服务
├── scripts/                          # 全协议脚本与宿主机脚本
├── test-data/                        # pcap 与测试资源
├── specs/                            # 规格文档（需求参考）
└── README.md
```

## 接口总览

### REST API

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
- `start` / `end`：ISO-8601 时间，闭区间

筛选优先级：`protocol` → `srcIp` → `start+end` → 全量分页。

### SSE
- 地址：`GET /api/sse/events`
- 事件类型：`audit`、`alert`
- 数据格式：`AppJson` 多态 JSON

### WebSocket
- 地址：`/ws/capture`
- 入站：发送 `ping` 返回 `pong`
- 出站：`AuditEvent` JSON 文本流

## 数据存储

表结构与索引位于 `docker/init.sql`，核心表如下：

`audit_logs`：
- `event_id`、`protocol`、`src_ip`、`dst_ip`、`src_port`、`dst_port`
- `alert_level`、`captured_at`
- `details`：JSONB，完整事件内容（含 `protocol` 作为多态判别）
- `created_at`

`alerts`：
- `alert_id`、`timestamp`、`level`、`rule_name`、`message`
- `audit_event_id`、`protocol`
- `created_at`

索引策略：
- `audit_logs`: `protocol`、`captured_at`、`src_ip` 索引
- `details`: JSONB GIN 索引
- `alerts`: `level`、`timestamp` 索引

数据库一致性说明：
- PostgreSQL 下 `details` 强制转换为 JSONB 并创建 GIN 索引。
- H2 测试库仍可运行，但 `details` 以文本存储。

## 设计取舍与模式落点（功能/原因/实现/收益）

1. `PacketCaptureEngine`（模板方法）
- 功能：统一在线/离线抓包生命周期。
- 原因：避免重复的 open/loop/close 逻辑。
- 实现：`startLive/startOffline/stop` 内部复用 `captureLoop`。
- 收益：流程一致、可替换数据源且更易测试。

2. `PacketSourceFactory`（工厂方法）
- 功能：统一创建在线/离线抓包源。
- 原因：隔离 Pcap4J 细节与异常处理。
- 实现：`openLive/openOffline` 返回 `PacketSource`。
- 收益：调用端解耦，未来可扩展新来源。

3. `PacketDecoder`（适配器）
- 功能：将 Pcap4J 包转为 `PacketMetadata`。
- 原因：上层不依赖底层包结构。
- 实现：按 Ethernet/IPv4/TCP/UDP 解析。
- 收益：上层解析器逻辑统一，测试简单。

4. `ParserRegistry`（注册表）
- 功能：端口→解析器路由。
- 原因：避免多层 if/else 判断协议。
- 实现：`portToParser` Map 直达。
- 收益：路由快、可扩展性好。

5. `ProtocolParser`（策略模式）
- 功能：协议解析行为可替换。
- 原因：协议复杂度高、逻辑独立。
- 实现：各协议实现 `parse`。
- 收益：协议并行开发与测试。

6. `AuditEventBus`（发布订阅）
- 功能：事件分发解耦下游。
- 原因：存储/告警/推送需要并行消费。
- 实现：`SharedFlow` 多订阅者。
- 收益：扩展下游无需改上游。

7. `AuditPipeline`（管道模式）
- 功能：串联捕获→解码→流重组→解析→分发。
- 原因：链路清晰，便于观测与排错。
- 实现：单通道消费 + TCP/UDP 分支。
- 收益：结构稳定，可插拔。

8. `BatchWriter`（批处理）
- 功能：提升写库吞吐、减少数据库压力。
- 原因：事件高频产生，单条写入开销大。
- 实现：按批量/时间双触发，失败重试。
- 收益：写入稳定、吞吐可控。

9. `Repository`（仓储模式）
- 功能：隔离存储实现与业务调用。
- 原因：未来可替换数据库或实现缓存层。
- 实现：接口 + Exposed 实现。
- 收益：测试与扩展更容易。

10. `AuditEvent`（统一领域模型）
- 功能：跨协议统一展示/存储。
- 原因：前端查询/展示需要稳定字段结构。
- 实现：sealed interface + `AppJson` 多态序列化。
- 收益：跨协议可比性强，扩展成本低。

## 开发规范

编码规范：
- 遵循 Kotlin 官方风格，KDoc 注释优先说明“意图/约束”。
- 单一职责与清晰边界，解析器不做 I/O。
- 协程采用结构化并发，避免全局协程泄漏。
- 事件方向统一：`src` 为客户端，`dst` 为服务端。

配置规范：
- `application.conf` 作为默认配置源。
- 环境变量覆盖：`DATABASE_*`、`CAPTURE_*`、`ALERT_ENABLED`。

## 二次开发指南

新增协议解析器：
- 实现 `ProtocolParser`，补齐会话状态与解析逻辑。
- 明确 `ports` 集合并注册到 `ParserRegistry`。
- 遵守方向约定：事件 `src`=客户端，`dst`=服务端。
- 添加单元测试与最小可复现样例。

扩展审计事件模型：
- 在 `AuditEvent` 新增字段或子类。
- 同步 `AppJson` 的多态配置。
- 更新前端渲染与查询逻辑。

新增告警规则：
- 在 `DefaultAlertRules` 增加规则。
- 保证输出包含 src/dst 与协议，便于定位。

## 二次开发大模型提示词（示例）

```
你是项目协作者，请在 E:/CodeSpace/net-audit 工作。
要求：
1. 保证中文不乱码，输出简体中文。
2. 必须使用 MCP 工具读取/写入文件与获取信息。
3. 修改前先读相关代码与文档，避免猜测。
4. 每次小步修改要有清晰提交记录，提交信息使用中文 Conventional Commit。
5. 不在提交信息中泄露内部规格编号。
6. 避免破坏性 git 操作，除非明确要求。
7. 变更后优先运行相关测试，并在回复中说明结果。
8. 遵循架构链路：Capture → Decode → Stream → Parser → EventBus → Storage/API。
```

## 保姆级使用指南

### 方式 A：Docker 一键启动（推荐）

适用场景：答辩演示、端到端联调。

1. 准备环境文件。

```
cd docker
cp .env.example .env
```

2. 构建并启动服务。

```
docker compose up -d --build
```

若拉取镜像受限，可使用已构建镜像启动：

```
docker compose up -d --no-build
```

3. 查看服务状态。

```
docker compose ps
```

4. 访问前端与健康检查。

```
http://localhost:5173
http://localhost:8080/health
```

5. 生成全协议测试流量。

```
docker exec netaudit-test-client bash /scripts/test-all-protocols.sh
```

6. 验证统计变化。

```
curl http://localhost:8080/api/stats/dashboard
```

看到 `totalEvents` 增长即表示链路通。

7. 关闭与清理。

```
docker compose down -v
```

说明：
- `test-client` 使用 `network_mode: service:backend`，确保后端能抓到它产生的流量。
- 端口映射已包含：HTTP(18080)、FTP(2121)、TELNET(2323)、DNS(1053)、SMTP(2525)、POP3(2110)。

### 方式 B：本地开发（真实流量）

适用场景：需要抓宿主机浏览器/系统真实流量。

1. 启动数据库（可用 Docker）。

```
cd docker
docker compose up -d postgres
```

2. 配置抓包网卡。

Windows：

```
Get-NetAdapter | Where-Object { $_.Status -eq "Up" } | ForEach-Object { "$($_.Name) -> \\Device\\NPF_{$($_.InterfaceGuid)}" }
$env:CAPTURE_INTERFACE="\\Device\\NPF_{GUID}"
```

Linux/Mac 通常用 `eth0` 或 `en0`。

3. 关闭浏览器 QUIC（HTTPS 默认走 UDP 443，会影响解析）。

- Chrome/Edge：`chrome://flags/#enable-quic` → Disabled → 重启浏览器。

4. 启动后端（端口可改为 8081）。

```
cd backend
$env:JAVA_TOOL_OPTIONS="-Dktor.deployment.port=8081"
./gradlew.bat run
```

5. 启动前端并指向后端。

```
cd frontend
npm install
$env:VITE_API_BASE="http://127.0.0.1:8081"
$env:VITE_WS_URL="ws://127.0.0.1:8081"
npm run dev -- --host 127.0.0.1 --port 5174
```

6. 验证实时链路。

```
Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/stats/dashboard"
curl.exe -s -N --max-time 3 http://127.0.0.1:8081/api/sse/events
```

### 方式 C：双通道合并（真实流量 + 脚本流量）

适用场景：既看真实浏览器流量，又看 Docker 测试脚本流量。

1. 启动 Docker 测试服务（不启动后端也可，端口映射已暴露）。

```
cd docker
docker compose up -d --build test-nginx test-ftp test-telnet test-dns test-smtp test-pop3
```

2. 启动本地后端（例：8081）并指向真实网卡。

3. 前端聚合双后端：

```
cd frontend
$env:VITE_API_BASES="http://127.0.0.1:8081,http://127.0.0.1:8080"
npm run dev -- --host 127.0.0.1 --port 5174
```

4. 生成脚本流量（宿主机打到 Docker 测试端口）：

```
cd scripts
./test-all-protocols-host.ps1
```

5. 此时 5174 页面会同时展示真实流量与脚本流量。

## 协议显示与解析边界（重要）

- HTTPS/QUIC 流量是加密的，系统不会解析 URL/正文。
- HTTPS 通常只产生 TLS 事件（SNI/ALPN/版本），DNS 事件会较多。
- 要看到 HTTP 事件，可访问明文站点：

```
http://example.com
http://neverssl.com
```

## 测试与质量

后端测试：
```
cd backend
./gradlew test
./gradlew jacocoTestReport
```

覆盖率报告：
- `backend/build/reports/jacoco/test/html/index.html`

后端构建：
```
cd backend
./gradlew build
```

前端构建：
```
cd frontend
npm run build
npm run lint
```

## 常见问题

1. **Docker 构建失败（拉取镜像受限）**
- 使用 `docker compose up -d --no-build` 启动已构建镜像。

2. **抓包权限不足**
- Linux 需 root 或授予抓包权限；Docker 已启用 `NET_RAW/NET_ADMIN`。

3. **前端无数据**
- 检查 `/health`、`/api/stats/dashboard` 是否可访问。
- 确保有真实流量或运行测试脚本。

4. **HTTPS 无 HTTP 事件**
- 这是预期行为，HTTPS 会转为 TLS 事件。

## 许可证

MIT License
