# 远程架构草案

本文档用于规划信驿 Relay 从“Android 端嵌入式 WebUI”逐步迁移到
“Android Agent + 云端 Backend + Web Frontend + Tauri Desktop”的目标架构。

## 目标

- Android 端继续负责短信、通知、自动输入、Xposed/LSPosed 相关能力。
- 本地嵌入式 WebUI 逐步退场，避免 Android 端自带 HTTPS 服务的稳定性问题。
- 控制台能力改为远程 Backend 提供，Web 和 Desktop 共用同一套 API。
- 部署策略优先本地，可在需要时平滑扩展到公网。

## 角色划分

### Android Agent

- 采集短信、应用通知、来电等事件
- 执行验证码自动输入与本地交互
- 本地缓存待上报记录与配置快照
- 与 Backend 建立安全连接
- 接收远程配置并下发到现有 runtime / xposed 链路

### Backend

- 用户 / 设备 / Token 管理
- 配置存储、历史记录存储、审计日志
- 实时推送（WebSocket / SSE）
- Web / Desktop 共用的管理 API
- 为本地部署与公网部署提供统一入口

### Web Frontend

- 主控制台
- 管理发送器、规则、设备、记录、概览
- 复用现有 WebUI 交互模型，逐步演进为远程控制台

### Tauri Desktop

- 复用 Web Frontend
- 增加桌面集成功能：托盘、通知、导出、诊断入口

## 部署模式

### 本地优先

- Docker Compose 在局域网主机运行 Backend
- Android Agent / Web / Desktop 优先连接局域网地址
- 证书先使用内网自签 / 内部 CA
- Android Agent 通过安装内部 CA 到“用户证书”来信任本地 HTTPS；App 已显式允许 `user` trust anchors

### 保留公网能力

- 后续增加域名与公网入口
- 保持同一套 API 与数据模型
- Android Agent 可按“本地地址优先，公网地址兜底”的策略连接

## 初始技术选择

### Backend

- Go
- PostgreSQL
- Caddy
- Docker Compose

理由：

- Go 适合做单二进制 API 服务，部署简单
- PostgreSQL 适合设备、配置、记录、审计这类结构化数据
- Caddy 更适合本地优先并保留公网扩展能力
- Docker Compose 便于 NAS / 迷你主机 / 本地开发统一部署

## 迁移路线

### M0：基础脚手架

- 新增 `backend/` 目录
- 提供本地优先的 Docker Compose 基础服务
- 提供最小 Go API（健康检查 / 系统信息）
- 明确环境变量和本地启动方式

### M1：设备与认证基础

- 用户模型、设备模型、绑定 Token
- Android Agent 与 Backend 首次配对
- 基础会话与认证能力

### M2：配置同步

- 远程配置读写 API
- Android Agent 拉取配置并应用到本地
- Web / Desktop 控制台修改配置

### M3：记录与事件上报

- 记录模型统一
- Android Agent 上报短信 / 通知 / 来电记录
- Web / Desktop 控制台查询与筛选

### M4：实时能力

- WebSocket / SSE
- 设备在线状态
- 实时事件推送

## 对现有工程的影响

- 当前 Android Gradle 工程保持不变，不将 Backend 纳入 Android 构建图
- `webui/` 仍保留现有前端资源，后续逐步演进为远程控制台
- Android 主运行链已停止启动旧内嵌 WebUI 服务，远程控制台成为后续演进主线
- 历史 `webui-core` 代码仍暂留仓库，作为过渡期参考与清理对象，不再参与主流程启用
