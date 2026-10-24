# Backend

这个目录承载信驿 Relay 正式支持的远程 Backend，部署策略为“本地优先、保留公网能力”。

当前包含：

- Docker Compose 基础编排
- Go API 服务（内嵌 WebUI）
- PostgreSQL
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
curl http://localhost:8088/healthz
curl http://localhost:8088/api/v1/system/info
```

4. 跑一遍本地闭环冒烟：

```bash
backend/scripts/smoke_local.sh
```

说明：

- `api` 容器内监听 `:8080`，compose 默认映射到宿主机 `:8088`
- WebUI 已内嵌进 API 二进制，控制台页面与 `/api/*` 使用同一入口
- `api` 默认直接拉取 `docker.io/alpha317/xinyi-relay-backend:beta`
- 默认入口是明文 HTTP，仅适合本机或可信内网；需要 HTTPS 时请在 API 前自行部署反向代理，并把 `RELAY_LOCAL_BASE_URL` / `RELAY_CORS_ORIGIN` 改成对外地址
- 管理员账户可通过 `.env` 中的 `RELAY_ADMIN_USERNAME` / `RELAY_ADMIN_PASSWORD` 在首次启动时自动初始化

如果你要临时切到某个固定版本或私有镜像，可以在 `backend/.env` 里覆盖：

```text
RELAY_API_IMAGE=docker.io/alpha317/xinyi-relay-backend:beta
RELAY_API_PULL_POLICY=always
```

## 数据库迁移

后端在启动时（`database.Open` -> `migrate`）自动执行版本化迁移，无需手动操作。

- 迁移文件位于 `backend/api/internal/database/migrations/`，通过 `embed.FS` 打包进二进制。
- 文件命名为 `NNNN_描述.sql`（如 `0001_init.sql`），按前缀数字升序执行。
- 已执行的迁移记录在 `schema_migrations` 表（version / name / checksum / applied_at）。
- 每个迁移在单个事务中执行 DDL 并写入版本记录；失败会整体回滚，下次启动重试。
- 多实例并发启动时通过 `pg_advisory_lock` 串行化，避免重复执行。
- 已执行的迁移是**不可变**的：再次启动时会校验 checksum，改动历史迁移会导致启动报错。

### 新增一条迁移

1. 在 `migrations/` 下新建递增编号的文件，例如：

   ```text
   backend/api/internal/database/migrations/0002_add_login_attempts.sql
   ```

2. 写入向前变更（新增表 / 加列 / 加索引等），例如：

   ```sql
   ALTER TABLE users ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0;
   ```

   > 基线 `0001_init.sql` 为兼容历史库使用了 `IF NOT EXISTS`；后续迁移应表达明确的向前变更，不要依赖 `IF NOT EXISTS`。

3. 重新部署即可，启动时自动应用。`0001_init.sql` 与旧的启动建表逻辑等价，旧库升级时会被识别为已就绪并记录为已执行，不会重复建表。

### 针对真实数据库运行迁移测试（可选）

默认 `go test ./...` 不需要数据库；集成测试仅在设置 `RELAY_TEST_DATABASE_URL` 时运行：

```bash
cd backend/api
RELAY_TEST_DATABASE_URL="postgres://relay:relay@localhost:5432/relay?sslmode=disable" \
  go test ./internal/database/ -run TestMigrate -v
# 记录保留清理的集成测试（store 包）：
RELAY_TEST_DATABASE_URL="postgres://relay:relay@localhost:5432/relay?sslmode=disable" \
  go test ./internal/store/ -run TestPrune -v
```

## 记录保留（relay_records 清理）

设备每次上报记录入库后，后端会按用户裁剪 `relay_records`，避免短信正文 / 验证码明文无限堆积。清理为尽力而为：失败只记日志，不影响上报本身。三条规则相互独立、都会生效（取各自更严的约束）：

- `RELAY_RECORDS_FOLLOW_DEVICE_LIMITS`（默认 `true`）：跟随手机端的分类历史条数。手机端在 config 快照里同步的 `records.{code,plainSms,appNotify,callNotify}HistoryLimit` 分别对应 `record_type` 的 `sms_code / sms_plain / app_notify / call`，各类型仅保留最新 N 条；某类为 `0`/负数表示该类不限（与手机端语义一致）。无 config 快照时此项不生效。
- `RELAY_RECORDS_MAX_PER_USER`（默认 `0`=关）：每用户记录总条数上限（跨所有类型），仅保留最新 N 条。
- `RELAY_RECORDS_RETENTION_DAYS`（默认 `0`=关）：删除 `occurred_at` 早于 N 天前的记录。

保留以「最新优先」按 `(occurred_at DESC, id DESC)` 排序，与读取顺序一致。不做正文/验证码掩码，Web 控制台与 App 历史仍可查看完整内容。

## 日志配置

后端支持将日志输出到文件，便于问题排查和运维监控。

### 环境变量

在 `backend/.env` 中配置以下变量：

```bash
# 日志文件路径（容器内路径，留空则只输出到 stdout）
RELAY_LOG_FILE=/var/log/relay/backend.log

# 日志级别：debug, info, warn, error
RELAY_LOG_LEVEL=info

# 日志目录（宿主机路径，用于持久化日志）
RELAY_LOG_DIR=./logs
```

### 日志位置

**Docker 部署**：
- 容器内路径：`/var/log/relay/backend.log`
- 宿主机路径：`backend/logs/backend.log`

**直接运行**：
- 由 `RELAY_LOG_FILE` 环境变量指定
- 默认输出到 stdout

### 查看日志

```bash
# 查看实时日志
tail -f backend/logs/backend.log

# 查看容器日志
docker logs -f relay-backend-api

# 搜索错误日志
grep -i "error\|fatal\|panic" backend/logs/backend.log
```

### 日志格式

日志格式为：`[日期 时间.微秒] 文件:行号: 消息`

示例：
```
2026/05/04 12:34:56.789123 main.go:45: relay backend starting on :8080
```

## 反向代理与 HTTPS（可选）

compose 默认不包含 Caddy，API 直接监听宿主机 `8088`。如需 HTTPS，请在 API 前自行部署反向代理（Caddy、Nginx 等），并把 `RELAY_LOCAL_BASE_URL` 与 `RELAY_CORS_ORIGIN` 改成反向代理的对外地址，例如 `https://relay.example.com`。

Android Agent 使用自签证书时，需要把反向代理使用的根证书安装到 Android 的“用户证书”；当前 `network_security_config` 已信任 `system + user` 证书锚。

## 后续演进

- 继续收敛 Web / Desktop 与手机端的功能 parity
- 持续完善部署文档与多架构镜像发布流程

## 容器镜像托管

仓库已经提供 GitLab CI 容器发布任务：

```text
.gitlab-ci.yml
```

它会把 `backend/api` 构建并发布到：

- GitLab Container Registry
- Docker Hub

GitLab 默认镜像名：

```text
registry.gitlab.com/magisk3171/xinyi-relay/backend
```

Docker Hub 默认镜像名：

```text
docker.io/alpha317/xinyi-relay-backend
```

如果 CI 设置了 `BACKEND_GITLAB_IMAGE` 或 `DOCKERHUB_IMAGE`，则以对应覆盖值为准。

触发方式：

- 推送 `beta` 分支
- 推送 `v*` tag

发布后的常见标签：

- `beta`
- `v0.4.0` 这类 tag 名
- `latest`：仅在 `v*` tag 发布时附带

架构：

- `linux/amd64`
- `linux/arm64`

每个架构会先推送 `<tag>-amd64` / `<tag>-arm64` 的临时标签；只有两个架构都构建成功，才会生成不带架构后缀的合并 tag。若某个架构任务失败，该发布 tag 不会出现，请检查对应流水线的 amd64/arm64 构建结果。

拉取示例：

```bash
docker pull registry.gitlab.com/magisk3171/xinyi-relay/backend:beta
docker pull registry.gitlab.com/magisk3171/xinyi-relay/backend:latest
docker pull docker.io/alpha317/xinyi-relay-backend:beta
docker pull docker.io/alpha317/xinyi-relay-backend:latest
```

## 文档

- 系统架构：`docs/ARCHITECTURE.md`
- API 概览：`backend/API_OVERVIEW.md`
