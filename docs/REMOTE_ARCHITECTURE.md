# 远程架构

最后更新：2026-05-23

本文档描述信驿 Relay 当前正式采用的远程架构：

`Android Agent + Backend + Web Frontend + Tauri Desktop`

## 总览

- Android 端继续负责短信、通知、来电、自动输入、Xposed/LSPosed 相关能力。
- 本地嵌入式 WebUI 已退出主运行链，避免 Android 端自带 HTTPS 服务的稳定性问题。
- 控制台能力改为远程 Backend 提供，Web 和 Desktop 共用同一套 API。
- 部署策略优先本地，可在需要时平滑扩展到公网。
- 远程 API 合同以 `shared/contracts/openapi.json` 为中心，Backend / Android Agent / Desktop / Web 均已有 drift test。

## 角色划分

### Android Agent

- 采集短信、应用通知、来电等事件。
- 执行验证码自动输入与本地交互。
- 本地缓存待上报记录与配置快照。
- 与 Backend 建立安全连接。
- 上报心跳、记录与配置快照。
- 接收远程配置并下发到现有 runtime / Xposed 链路。

边界：

- Android Agent 不直接服务 Web / Desktop 静态资源。
- Android Agent 不把本地 Room、Provider、DataStore 暴露给远程控制台。
- 远程配置落地必须先合并本地完整配置，再写入本地 repository / prefs 链路。

### Backend

- 用户 / 设备 / Token 管理。
- 配置存储、历史记录存储、审计日志。
- 实时推送（WebSocket）。
- Web / Desktop 共用的管理 API。
- 为本地部署与公网部署提供统一入口。

边界：

- Backend 不进入 Android Gradle 构建图。
- Backend 的路由和 DTO 字段必须与 `shared/contracts/openapi.json` 保持一致。
- 新 endpoint 必须同步 OpenAPI schema、Backend test、Android/TS DTO 或类型测试。

### Web Frontend

- 主控制台。
- 管理发送器、规则、设备、记录、概览和设置。
- 复用 `shared/contracts/console.ts` 中的远程类型。
- 复用 `shared/configSnapshot.ts` 中的 config snapshot normalize / clone / conflict helper，减少 Web / Desktop
  配置编辑基础逻辑漂移。
- 通过 lint、typecheck、build 与 OpenAPI contract test 保证基础一致性。

后续重点：

- 继续抽出共享配置编辑器和 sender 表单布局元数据，减少 Web / Desktop 双份实现；API client、认证状态模型
  和 sender 字段 schema 已有共享入口。

### Tauri Desktop

- 复用远程控制台能力。
- 增加桌面集成功能：托盘、通知、导出、诊断入口。
- 使用 `shared/contracts/console.ts` 与 `shared/contracts/openapi.json` 做类型/合同测试。

边界：

- Desktop 只保留桌面运行时 adapter、Tauri command、窗口/托盘/系统集成。
- 业务 API 类型和配置编辑模型优先与 Web 共享。

## 部署模式

### 本地优先

- Docker Compose 在局域网主机运行 Backend。
- Android Agent / Web / Desktop 优先连接局域网地址。
- 证书先使用内网自签 / 内部 CA。
- Android Agent 通过安装内部 CA 到“用户证书”来信任本地 HTTPS；App 已显式允许 `user` trust anchors。

### 保留公网能力

- 后续增加域名与公网入口。
- 保持同一套 API 与数据模型。
- Android Agent 可按“本地地址优先，公网地址兜底”的策略连接。

## 技术选择

### Backend

- Go。
- PostgreSQL。
- Caddy。
- Docker Compose。

理由：

- Go 适合做单二进制 API 服务，部署简单。
- PostgreSQL 适合设备、配置、记录、审计这类结构化数据。
- Caddy 更适合本地优先并保留公网扩展能力。
- Docker Compose 便于 NAS / 迷你主机 / 本地开发统一部署。

### Web / Desktop

- Web：React / Vite / TypeScript。
- Desktop：Tauri + React / Vite / TypeScript。
- 共享合同：`shared/contracts/openapi.json`、`shared/contracts/console.ts`。

理由：

- Web 和 Desktop 可以共享主要控制台模型。
- Tauri 保留轻量桌面壳和系统集成，不引入 Electron。
- TypeScript 合同让前端和 OpenAPI 字段 drift 更容易被 CI 捕捉。

## 当前实现状态

- `backend/` 已提供正式的本地优先 Compose 部署入口。
- Backend 已包含设备绑定、配置快照、记录上报、配置审计与实时事件。
- Web 控制台已接入设备、记录、发送器、应用与设置视图。
- Tauri Desktop 已提供桌面壳、托盘与内嵌控制台能力。
- Android Agent 已包含远程绑定、心跳、配置同步与记录上报链路。
- `runtime/data/remote/RemoteApiClient.kt` 已把 HTTP 调用从 `RemoteAgentRepository` 中抽出。

## 合同与漂移测试

已落地：

- Backend：`backend/api/internal/http/openapi_contract_test.go` 校验路由和 DTO 字段。
- Android Agent：`runtime/src/test/.../RemoteApiDtosContractTest.kt` 校验 agent wire DTO 字段。
- Desktop：`desktop/src/test/ConsoleOpenApiContract.test.ts` 校验共享 TypeScript 类型。
- Web：`webui/src/__tests__/ConsoleOpenApiContract.test.ts` 校验共享 TypeScript 类型，并在 CI 中执行 lint/typecheck/test/build。

后续要求：

- 新增 API 时同时更新 `shared/contracts/openapi.json`、Backend DTO test、Android DTO test、Desktop/Web 类型测试。
- OpenAPI 只能描述已实现或同一变更中落地的 endpoint，不提前写“未来接口”。

## 对现有工程的影响

- 当前 Android Gradle 工程保持不变，不将 Backend 纳入 Android 构建图。
- `webui/` 作为远程控制台前端保留。
- Android 主运行链已停止启动旧内嵌 WebUI 服务。
- Android 端远程同步继续落在 `runtime`，由 `RuntimeGraph` 装配。

## 后续演进

1. 继续抽共享配置编辑器和 sender 表单布局元数据；React hook 需要先有明确前端 package / workspace
   边界，不直接放仓库根 `shared/`。
2. 继续收敛 Web / Desktop 与手机端的功能 parity。
3. 逐步把前端静态资源也纳入远端镜像链，减少宿主机对本地 `webui/dist` 的依赖。
4. 扩展 OpenAPI schema 覆盖新增 endpoint，并把合同测试纳入对应 CI。
