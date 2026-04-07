# Backend

这个目录承载信驿 Relay 正式支持的远程 Backend，部署策略为“本地优先、保留公网能力”。

当前包含：

- Docker Compose 基础编排
- Go API 服务
- PostgreSQL
- Caddy 反向代理
- Web 控制台托管入口
- API 概览文档

## 快速开始

1. 复制环境变量模板：

```bash
cp backend/.env.example backend/.env
```

2. 启动服务：

```bash
docker compose --env-file backend/.env -f backend/docker-compose.yml up -d
```

3. 检查健康状态：

```bash
curl -k https://localhost:8443/healthz
curl -k https://localhost:8443/api/v1/system/info
```

4. 跑一遍本地闭环冒烟：

```bash
backend/scripts/smoke_local.sh
```

说明：

- `api` 容器仅在内部网络监听 `:8080`
- `api` 默认直接拉取 `ghcr.io/magisk317/xinyi-relay-backend:beta`
- 对外入口统一走 Caddy `https://localhost:8443`
- 当前使用 `tls internal` 自签发证书；本地调试可先使用 `curl -k`
- `webui/dist` 会由 Caddy 直接托管，同一入口同时提供前端页面和 `/api/*`
- 管理员账户可通过 `.env` 中的 `RELAY_ADMIN_USERNAME` / `RELAY_ADMIN_PASSWORD` 在首次启动时自动初始化

如果你要临时切到某个固定版本或私有镜像，可以在 `backend/.env` 里覆盖：

```text
RELAY_API_IMAGE=ghcr.io/magisk317/xinyi-relay-backend:beta
RELAY_API_PULL_POLICY=always
```

## Android Agent 接入本地 HTTPS

如果 Android Agent 需要直连本地 `https://localhost:8443` 风格的自签发入口：

1. 先启动一次 Caddy，让内部 CA 生成根证书
2. 根证书默认位于：

```text
backend/caddy-data/caddy/pki/authorities/local/root.crt
```

3. 把这张证书安装到 Android 的“用户证书”
4. 当前 Android `network_security_config` 已显式信任 `system + user` 证书锚，装入后即可用于 Remote Agent 的本地 HTTPS 连接

如果后续切到公网域名和正式证书，就不再需要这个步骤。

## 后续演进

- 继续收敛 Web / Desktop 与手机端的功能 parity
- 继续减少历史嵌入式 WebUI 代码残留
- 逐步把前端静态资源也纳入远端镜像链，减少宿主机对本地 `webui/dist` 的依赖

## 容器镜像托管

仓库已经提供一条 GitHub Actions 工作流：

```text
.github/workflows/backend-publish-ghcr.yml
```

它会把 `backend/api` 构建并发布到：

- GitHub Container Registry
- Docker Hub（当仓库配置了 Docker Hub secrets 时）

默认镜像名：

```text
ghcr.io/<owner>/<repo>-backend
```

例如当前仓库会发布成：

```text
ghcr.io/magisk317/xinyi-relay-backend
```

Docker Hub 默认镜像名：

```text
docker.io/<DOCKERHUB_USERNAME>/xinyi-relay-backend
```

如果仓库设置了 `DOCKERHUB_IMAGE`，则以该值为准。

触发方式：

- 手动触发 `workflow_dispatch`
- 推送 `beta` 分支且命中 `backend/**`
- 推送 `v*` tag

发布后的常见标签：

- `beta`
- `v0.0.4` 这类 tag 名
- `latest`：仅在 `v*` tag 发布时附带

架构：

- `linux/amd64`
- `linux/arm64`

拉取示例：

```bash
docker pull ghcr.io/magisk317/xinyi-relay-backend:beta
docker pull ghcr.io/magisk317/xinyi-relay-backend:latest
docker pull docker.io/magisk317/xinyi-relay-backend:beta
docker pull docker.io/magisk317/xinyi-relay-backend:latest
```

## 文档

- 远程架构：`docs/REMOTE_ARCHITECTURE.md`
- API 概览：`backend/API_OVERVIEW.md`
