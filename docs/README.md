# xinyi-relay 文档索引

- [ARCHITECTURE.md](ARCHITECTURE.md) — 系统架构、代码分层、重构历程、平台兼容性（主文档）
- [CHANGELOG.md](CHANGELOG.md) — 版本变更记录
- [xinyi-relay-android17-impact.md](xinyi-relay-android17-impact.md) — Android 17 影响详细分析（摘要已合并至 ARCHITECTURE.md）

当前工作模式采用单 APK 自适应模型：Xposed runtime 有效时为 Enhanced，否则为
Standard；权限、Root 与 Xposed-only 能力分别门控。发行 flavor 只表达 Play/GitHub、
E2EE、Billing 等分发策略，不再承载 `full` / `lite` 运行模式。
