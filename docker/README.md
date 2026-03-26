# Docker 环境配置

在 `docker/` 目录复制 `.env.example` 创建 `.env` 文件：

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

## 默认网络策略

当前 `docker-compose.yml` 默认已使用镜像站与 npm 镜像源，通常直接执行即可：

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

## 镜像源覆盖（按需）

若你需要手工指定镜像，可在 `docker/.env` 覆盖：

```env
POSTGRES_IMAGE=docker.m.daocloud.io/library/postgres:15-alpine
BACKEND_BUILDER_IMAGE=docker.m.daocloud.io/library/gradle:8.10.2-jdk21-alpine
BACKEND_RUNTIME_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine
FRONTEND_NODE_IMAGE=docker.m.daocloud.io/library/node:20-alpine
FRONTEND_NGINX_IMAGE=docker.m.daocloud.io/library/nginx:alpine
TEST_NGINX_IMAGE=docker.m.daocloud.io/library/nginx:alpine
TEST_FTP_IMAGE=docker.m.daocloud.io/fauria/vsftpd:latest
TEST_TELNET_ALPINE_IMAGE=docker.m.daocloud.io/library/alpine:3.19
TEST_DNS_PYTHON_IMAGE=docker.m.daocloud.io/library/python:3.12-alpine
TEST_SMTP_PYTHON_IMAGE=docker.m.daocloud.io/library/python:3.12-alpine
TEST_POP3_PYTHON_IMAGE=docker.m.daocloud.io/library/python:3.12-alpine
TEST_CLIENT_ALPINE_IMAGE=docker.m.daocloud.io/library/alpine:3.19
```

前端构建（`npm ci`）默认先用 `FRONTEND_NPM_REGISTRY`，失败后自动回退到 `FRONTEND_NPM_REGISTRY_FALLBACK`。
若你所在网络访问 npm 官方源不稳定，可直接改成：

```env
FRONTEND_NPM_REGISTRY=https://registry.npmmirror.com
FRONTEND_NPM_REGISTRY_FALLBACK=https://registry.npmjs.org
```
