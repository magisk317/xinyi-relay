# 更新日志

## [v0.0.1-alpha.3]

- 修复模块激活状态判定：优先使用 libxposed service 运行时信号，并保留最近激活回退。
- 清理旧 `getModuleVersion` / `ModuleUtilsHook` 链路，统一到新 API 判定路径。
- 截图资源收敛为单图拼接（通用目录），并放宽发布校验的最少截图数量要求。

## [v0.0.1-alpha.2]

- 切换到 libxposed 新入口模型，并提供兼容 Hook Bridge，降低迁移风险。
- 重构偏好读取链路为可插拔 Source Chain，补齐 Runtime 能力探测与回退路径。
- 发布链路收敛为 libxposed 新 API 单轨工作流，简化发布校验。

## [v0.0.1-alpha]

- 初始 Alpha 版本发布。
- 完成信驿 Relay 独立化（包名与发布链路）。
- 提供短信/通知转发、记录、WebUI 等核心能力。
