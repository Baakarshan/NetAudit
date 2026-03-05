# NetAudit — 网络审计系统

基于 Kotlin 协程的实时网络审计系统，支持 HTTP/FTP/TELNET/DNS/SMTP/POP3 六种协议的捕获与解析。

## 技术栈

### 后端
- **Kotlin 2.0** - 现代化 JVM 语言
- **Ktor 2.3** - 异步 Web 框架
- **Pcap4J** - 网络包捕获库
- **Exposed ORM** - Kotlin SQL 框架
- **PostgreSQL** - 关系型数据库
- **Kotlin Coroutines** - 协程支持

### 前端
- **Vue 3** - 渐进式前端框架
- **TypeScript** - 类型安全
- **Element Plus** - UI 组件库
- **ECharts** - 数据可视化
- **Vite** - 构建工具

### 部署
- **Docker** - 容器化
- **Docker Compose** - 编排

## 项目结构

```
net-audit/
├── backend/              # Kotlin 后端
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/
│   │   │   │   └── com/netaudit/
│   │   │   └── resources/
│   │   └── test/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── frontend/             # Vue 3 前端
│   ├── src/
│   │   ├── views/
│   │   ├── router/
│   │   ├── App.vue
│   │   └── main.ts
│   ├── package.json
│   └── vite.config.ts
├── docker/               # Docker 配置
│   ├── docker-compose.yml
│   ├── Dockerfile.backend
│   ├── Dockerfile.frontend
│   └── init.sql
├── specs/                # 开发规格文档
├── scripts/              # 测试脚本
├── test-data/            # 测试数据
└── CLAUDE.md             # 项目说明
```

## 快速开始

### 环境要求
- JDK 17+
- Node.js 16+
- Docker & Docker Compose
- Gradle 8+

### 本地开发

#### 后端
```bash
cd backend
./gradlew build
./gradlew run
```

#### 前端
```bash
cd frontend
npm install
npm run dev
```

#### 数据库
```bash
cd docker
docker-compose up postgres
```

### Docker 部署

```bash
cd docker
docker-compose up -d
```

访问：
- 前端: http://localhost:3000
- 后端 API: http://localhost:8080
- 数据库: localhost:5432

## 开发规范

### Kotlin 代码规范
- 使用协程处理异步操作
- 使用密封类表示状态
- 所有 data class 字段使用 `val`
- 使用 kotlin-logging 记录日志
- 测试使用 Kotest + MockK

### 提交前检查
```bash
cd backend
./gradlew build
./gradlew test
```

### Git 提交规范
- `feat:` 新功能
- `fix:` 修复 bug
- `docs:` 文档更新
- `refactor:` 重构
- `test:` 测试相关

## Spec 开发顺序

1. **Spec 1** - 基础架构（必须先完成）
2. **Spec 2, 3** - 可并行开发
3. **Spec 4~8** - 协议解析器（可全并行）
4. **Spec 9, 10** - 可并行开发
5. **Spec 11** - 最终集成（依赖全部）

## 许可证

MIT License
