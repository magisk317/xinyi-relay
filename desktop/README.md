# Xinyi Relay Desktop

`desktop/` 现在不再只是内嵌 `iframe` 的远程壳，而是一个独立的 Tauri 2 桌面客户端：

- React + TypeScript 桌面 UI
- Rust 负责 backend 连接、desktop auth handoff、session 持久化、tray、诊断导出、连接监控
- 与 `webui` 共享一套前端契约定义，避免 response shape 漂移

## 当前定位

桌面端的目标是成为 `webui` 的超集：

- 保留 Web 端管理能力：
  - overview
  - devices / bind codes
  - records
  - config snapshot editor
  - senders
  - apps
  - analytics
  - advanced / settings
- 叠加桌面特有能力：
  - browser-delegated login
  - 本地 backend profile 管理
  - tray 快捷入口
  - 原生通知
  - 本地诊断包导出
  - 本地自签 TLS override 开关

## 架构

### Frontend

- 入口：`desktop/src/main.tsx`
- 路由：`desktop/src/App.tsx`
- 状态层：`desktop/src/state/DesktopContext.tsx`
- 页面：
  - `desktop/src/pages/LoginPage.tsx`
  - `desktop/src/pages/OverviewPage.tsx`
  - `desktop/src/pages/DevicesPage.tsx`
  - `desktop/src/pages/RecordsPage.tsx`
  - `desktop/src/pages/ConfigPage.tsx`
  - `desktop/src/pages/SendersPage.tsx`
  - `desktop/src/pages/AppsPage.tsx`
  - `desktop/src/pages/AnalyticsPage.tsx`
  - `desktop/src/pages/AdvancedPage.tsx`

### Native runtime

- 入口：`desktop/src-tauri/src/main.rs`
- 核心能力：
  - backend profile 持久化
  - keyring session 存储
  - browser handoff + loopback callback
  - desktop token refresh / logout
  - tray 导航和 reconnect action
  - 连接监控和原生通知
  - 诊断导出

### Shared contract

- `shared/contracts/console.ts`

`webui` 和 `desktop` 共用这份类型定义，便于后端 API 变动时统一收敛。

## Browser-delegated login

桌面端登录链路：

1. 桌面端启动本地 loopback callback。
2. 桌面端打开系统浏览器到 backend 的 `/api/v1/auth/desktop/start`。
3. 用户在浏览器里复用已有 session 或直接登录。
4. backend 将 one-time code 回跳到 `127.0.0.1` callback。
5. 桌面 runtime 调用 `/api/v1/auth/desktop/exchange` 换取 desktop access/refresh token。

## Local Docker + self-signed HTTPS

默认提供一个本地 profile：

- `Local Docker Backend`
- `https://localhost:8443`

这个 profile 默认打开 `allowSelfSigned`，方便本地 Docker + Caddy 自签 HTTPS 场景。

当前实现已经支持：

- 对 profile 级别启用本地 TLS override
- native runtime 通过 Rust `reqwest` 访问 backend，而不是浏览器 `fetch`

## 开发

```bash
cd desktop
npm install
npm run build
npm run tauri:check:linux
npm run tauri:dev
```

桌面端的 Linux 构建脚本会自动强制系统 `pkg-config` 优先：

```bash
./scripts/with-system-pkg-config.sh
```

这会在 Linux 下自动：

- 导出 `TAURI_LINUX_AYATANA_APPINDICATOR=1`
- 把 `PATH` 前置到 `/usr/bin`
- 导出 `PKG_CONFIG=/usr/bin/pkg-config`
- 把系统 `pkg-config` 搜索路径前置到 `PKG_CONFIG_PATH`

这样即使本机同时装了 Homebrew，也不会误用 Homebrew 的 `pkg-config`，并且会强制 Tauri bundler 在 Linux 上使用 `libayatana-appindicator`。

## 构建目标

- Linux：AppImage / `.deb`
- Windows：NSIS
- macOS：`.dmg`

CI 会保留 `desktop-ci.yml` 做日常跨平台构建校验，并提供独立 `desktop-release.yml` 做 Release 打包上传。

## Windows 自签名证书

仓库内置了当前 Release 使用的公开 Windows 自签名证书：

- [desktop/certs/windows-codesign.cer](/home/lzc/wqk/xinyi-relay/desktop/certs/windows-codesign.cer)

当前 Windows 安装包使用仓库自管的自签名证书，而不是公有 CA 证书。因此首次安装时，Windows 仍可能提示未知发布者或 SmartScreen 警告。对于愿意继续使用的用户，可以先导入上面的 `.cer` 证书，再运行安装包。

建议导入方式：

1. 双击 `windows-codesign.cer`。
2. 选择“安装证书”。
3. 当前用户即可，若你希望整机信任也可以选择本地计算机。
4. 证书存储位置选“受信任的根证书颁发机构”。
5. 完成导入后重新打开安装包。

说明：

- 这只是面向小众分发场景的自签方案，不等同于商业代码签名。
- 如果你不信任该证书，请不要导入。
