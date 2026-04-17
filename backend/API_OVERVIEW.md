# API Overview

当前阶段已落地的 Backend API 面向三个客户端：

- Web / Tauri 管理端
- Android Agent
- 本地运维与健康检查

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
- `POST /api/v1/agent/records:batch`
- `GET /api/v1/config/snapshot`
- `PUT /api/v1/config/snapshot`

说明：

- Agent 使用 `Authorization: Bearer <device_token>`
- `register` 通过 bind code 换取长期 device token
- `heartbeat` 用于设备在线状态与能力更新
- `records:batch` 用于批量上报记录

## Shared Config

- `GET /api/v1/config/snapshot`
- `PUT /api/v1/config/snapshot`
- `GET /api/v1/config/audit`

说明：

- Web 通过 cookie session + CSRF 调用
- Desktop 通过 desktop bearer token 调用
- Agent 通过 device token 调用
- `PUT` 必须带 `base_revision`
- revision 不一致时返回 `409` 与当前云端快照

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
- `config.updated`
- `records.ingested`
