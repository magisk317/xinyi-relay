# Desktop Shell

这个目录承载远程控制台的 Tauri 桌面壳。

当前阶段提供：

- 一个最小 Tauri 工程骨架
- 本地保存 Backend 地址
- 一键在系统浏览器打开远程控制台
- 托盘常驻与窗口隐藏/唤起

## 开发

```bash
cd desktop
npm install
npm run tauri:dev
```

Linux 如果同时装了 Homebrew 和 apt 版依赖，建议在检查或构建时显式使用系统 `pkg-config`：

```bash
cd desktop
npm run tauri:check:linux
PKG_CONFIG=/usr/bin/pkg-config npm run tauri:build
```

默认后端地址示例：

- `https://localhost:8443`

## 目标

- 后续逐步复用远程 Web 控制台能力
- 增加托盘、通知、连接状态与日志导出
