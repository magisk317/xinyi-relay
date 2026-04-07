# 远程架构

本文档描述信驿 Relay 当前正式采用的远程架构：

`Android Agent + Backend + Web Frontend + Tauri Desktop`

## 总览

- Android 端继续负责短信、通知、自动输入、Xposed/LSPosed 相关能力。
- 本地嵌入式 WebUI 已退出主运行链，避免 Android 端自带 HTTPS 服务的稳定性问题。
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
- 复用现有交互模型，作为远程控制台正式前端

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

## 技术选择

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

## 当前实现状态

- `backend/` 已提供正式的本地优先 Compose 部署入口
- Backend 已包含设备绑定、配置快照、记录上报、配置审计与实时事件
- Web 控制台已接入设备、记录、发送器、应用与设置视图
- Tauri Desktop 已提供桌面壳、托盘与内嵌控制台能力
- Android Agent 已包含远程绑定、心跳、配置同步与记录上报链路

## 对现有工程的影响

- 当前 Android Gradle 工程保持不变，不将 Backend 纳入 Android 构建图
- `webui/` 作为远程控制台前端保留
- Android 主运行链已停止启动旧内嵌 WebUI 服务

## 后续演进

- 继续收敛 Web / Desktop 与手机端的功能 parity
- 逐步把前端静态资源也纳入远端镜像链，减少宿主机对本地 `webui/dist` 的依赖
