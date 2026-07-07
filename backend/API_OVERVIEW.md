# API Overview

当前阶段已落地的 Backend API 面向三个客户端：

- Web / Tauri 管理端
- Android Agent
- 本地运维与健康检查

补充：

- `frontend/shared/contracts/openapi.json` 现在由 backend contract assembler 生成
  - 其中 Android agent、device-config、auth/system/read-model/realtime DTO
    schema 片段来自 `relay/contract`，先生成到
    `backend/api/internal/http/openapi_schemas.generated.json`，再由 backend
    assembler 合并进最终 OpenAPI
  - OpenAPI 的 path/method/operationId 路由元数据现在统一从
    `relay/contract/remote/OpenApiRouteContracts.kt` 生成到
    `backend/api/internal/http/openapi_routes.generated.json`，再由 backend
    assembler 合并
- Web / Desktop 的 `console.generated.ts` 再由该 OpenAPI 生成

## Public Endpoints

### Health

- `GET /healthz`
- `GET /api/v1/system/info`

## Web / Tauri Auth

- `POST /api/v1/bootstrap/admin`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/password`
- `GET /api/v1/auth/me`
- `GET /api/v1/auth/desktop/start`
- `POST /api/v1/auth/desktop/exchange`
- `POST /api/v1/auth/desktop/refresh`
- `POST /api/v1/auth/desktop/logout`

说明：

- `bootstrap/admin` 仅允许在数据库还没有用户时使用；正常部署优先使用 `.env` 中的 `RELAY_ADMIN_USERNAME` / `RELAY_ADMIN_PASSWORD` 自动初始化管理员
- 登录成功后会返回 `relay_session` cookie 和 `csrfToken`
- 后续写接口需携带 `X-CSRF-Token`
- Desktop 客户端通过 browser handoff 走 `/auth/desktop/start -> /exchange`
- Desktop 登录成功后会拿到 access/refresh token，并通过 `Authorization: Bearer <desktop_access_token>` 调用管理接口

## Device Management

- `POST /api/v1/devices/bind-codes`
- `GET /api/v1/devices`
- `PATCH /api/v1/devices/{id}`
- `POST /api/v1/devices/{id}/revoke`

## Android Agent

- `POST /api/v1/agent/register`
- `POST /api/v1/agent/heartbeat`
- `POST /api/v1/agent/config/mirror`
- `POST /api/v1/agent/config/commands:pull`
- `POST /api/v1/agent/config/commands:ack`
- `POST /api/v1/agent/records:batch`

说明：

- Agent 使用 `Authorization: Bearer <device_token>`
- `register` 通过 bind code 换取长期 device token
- `heartbeat` 用于设备在线状态与能力更新
- `config/mirror` 由 Android 推送本地配置 mirror
- `config/commands:pull` 由 Android 拉取待执行命令
- `config/commands:ack` 由 Android 回写命令应用结果
- `records:batch` 用于批量上报记录

## Device Config

- `GET /api/v1/devices/{id}/config`
- `POST /api/v1/devices/{id}/config/commands`
- `GET /api/v1/devices/{id}/config/audit`

说明：

- 配置模型已经切到“每台设备一个 mirror + 一个 command queue”
- Web 通过 cookie session + CSRF 调用
- Desktop 通过 desktop bearer token 调用
- 管理端不再直接写 mirror，而是向目标设备提交命令
- 命令基线过期时返回 `409 stale_base_revision`
- 同一设备现在允许积压多条 pending command；新的命令必须以上一条
  pending command 的 `targetRevision` 作为 `baseRevision` 继续排队

## Legacy Snapshot Compatibility

旧的 `/api/v1/config/snapshot` / `/api/v1/config/audit` 共享配置语义已经退出正式 API 面。

- 新的 Web / Desktop 主路径不应再新增对它的依赖
- 兼容性遗留仍可能存在于少量历史实现与迁移代码中，但不应视为当前架构的一部分

## Records

- `GET /api/v1/records`
- `GET /api/v1/records/{id}`

支持：

- `limit`
- `offset`
- `device_id`

## Realtime

- `GET /api/v1/realtime/ws`

当前广播事件：

- `device.registered`
- `device.heartbeat`
- `device.updated`
- `device.revoked`
- `device.config.updated`
- `device.config.command.updated`
- `records.ingested`
