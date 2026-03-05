# Docker 环境配置

复制 `.env.example` 创建 `.env` 文件：

```bash
cp .env.example .env
```

根据需要修改 `.env` 中的配置值。

## 环境变量说明

- `POSTGRES_DB`: PostgreSQL 数据库名
- `POSTGRES_USER`: PostgreSQL 用户名
- `POSTGRES_PASSWORD`: PostgreSQL 密码
- `DB_HOST`: 数据库主机（容器内使用服务名）
- `DB_PORT`: 数据库端口
- `BACKEND_PORT`: 后端服务端口
- `FRONTEND_PORT`: 前端服务端口
- `VITE_API_URL`: 前端 API 地址
- `VITE_WS_URL`: 前端 WebSocket 地址
