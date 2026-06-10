# Implementation Plan: Matrix E2EE 支持

## Overview

基于现有的 `withE2ee` / `noE2ee` 源集架构和 `matrix-rust-sdk` 高层 `Client` API，完善 E2EE 发送链路。主要工作包括：修复存储目录隔离、添加房间加密状态检测、实现错误处理与明文回退、添加 Feature_Loader 模块检测、构建系统双变体配置、UI 状态指示器，以及会话生命周期管理。

## Tasks

- [x] 1. 修复存储目录与 Session 构造
  - [x] 1.1 重写 `MatrixE2eeUtils.getStoreDir()` 实现用户隔离存储
    - 将硬编码路径改为通过 Application Context 获取 `filesDir`
    - 存储路径格式: `{appFilesDir}/matrix-crypto/{sha256(userId).take(16)}/`
    - 从 `MatrixSetting` 中的 `accessToken` 派生用户标识（或通过 `/whoami` API 获取 userId）
    - 添加 `Context` 参数传递机制（通过 `DefaultSenderDispatcher` 已持有的 context）
    - _Requirements: 2.2_

  - [x] 1.2 修复 `Session` 构造中 userId/deviceId 为空的问题
    - 在 `restoreSession` 前调用 `/_matrix/client/v3/account/whoami` 获取真实 userId 和 deviceId
    - 缓存 whoami 结果到存储目录，避免每次调用
    - 处理 whoami 失败的情况（网络错误、token 过期）
    - _Requirements: 2.1, 2.3_

  - [x] 1.3 编写 Property 3: Crypto_Store 路径派生确定性 属性测试
    - **Property 3: Crypto_Store 路径派生确定性**
    - 使用 kotest-property 生成随机 userId 字符串，验证路径派生为纯确定性函数
    - **Validates: Requirements 2.2**

- [x] 2. 实现房间加密状态检测
  - [x] 2.1 创建 `RoomCryptoState` 组件
    - 在 `relay/sender/src/withE2ee/` 下创建 `RoomCryptoState.kt`
    - 实现 `GET /rooms/{roomId}/state/m.room.encryption` HTTP 请求
    - 使用 `ConcurrentHashMap` 内存缓存，TTL 60 分钟
    - 查询超时 10 秒
    - 仅当 HTTP 200 且 algorithm == `m.megolm.v1.aes-sha2` 时返回 `encrypted=true`
    - 其他所有情况（404、网络错误、解析失败、不支持的算法）返回 `encrypted=false`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

  - [x] 2.2 编写 Property 1: 房间加密状态分类正确性 属性测试
    - **Property 1: 房间加密状态分类正确性**
    - 生成随机 HTTP 状态码 + 随机 JSON body → 验证分类逻辑
    - **Validates: Requirements 1.2, 1.3, 1.4, 1.5**

  - [x] 2.3 编写 Property 2: 房间加密状态缓存 TTL 行为 属性测试
    - **Property 2: 房间加密状态缓存 TTL 行为**
    - 生成随机时间戳和房间 ID → 验证缓存命中/过期行为
    - **Validates: Requirements 1.6, 1.7**

- [x] 3. 重构 withE2ee/MatrixE2eeUtils 添加路由逻辑与明文回退
  - [x] 3.1 重构 `MatrixE2eeUtils.sendMsg()` 添加完整路由逻辑
    - 步骤 1: 检查 E2EE 可用性（FeatureLoader） → 不可用则明文发送
    - 步骤 2: 查询 `RoomCryptoState` → 房间未加密则明文发送
    - 步骤 3: 尝试 E2EE 发送（现有 `Client.getRoom().sendRaw()` 逻辑）
    - 步骤 4: 任何异常 → catch → 日志 → `MatrixUtils.sendMsg()` 明文回退
    - 保持与 `noE2ee` 变体相同的方法签名
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [x] 3.2 添加加密消息发送重试逻辑
    - 如果 `room.sendRaw()` 抛出异常，重试一次
    - 重试仍失败则回退明文
    - 记录回退原因日志（模块不可用 / 加密超时 / 加密错误 + 房间 ID）
    - _Requirements: 4.5, 4.6, 5.3, 5.4_

  - [x] 3.3 编写 Property 8: 发送路由决策 属性测试
    - **Property 8: 发送路由决策**
    - 穷举 (e2eeAvailable, roomEncrypted) 四种组合 → 验证路由决策正确
    - **Validates: Requirements 5.1, 5.2, 8.2, 8.3, 8.4, 8.5**

  - [x] 3.4 编写 Property 9: 明文回退保持消息内容不变 属性测试
    - **Property 9: 明文回退保持消息内容不变**
    - 生成随机消息内容 → 触发回退 → 验证传给 PlaintextSender 的内容不变
    - **Validates: Requirements 5.3**

- [-] 4. Checkpoint - 确认核心加密路径可用
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. 实现会话生命周期管理
  - [x] 5.1 添加客户端关闭与切换逻辑
    - 当 `accessToken` 变化时，关闭旧 `Client` 实例（调用 `client.close()` 或等效清理）
    - 在 `getOrCreateClient` 中正确处理旧实例释放
    - 添加 `CryptoEngine` 初始化超时 30 秒限制
    - _Requirements: 2.1, 2.5, 2.6_

  - [x] 5.2 实现 Crypto_Store 损坏恢复
    - 如果 `ClientBuilder.build()` 或 `restoreSession` 抛出存储相关异常
    - 删除存储目录并重新初始化
    - 记录警告日志包含失败原因
    - _Requirements: 2.5_

- [x] 6. 实现 FeatureLoader 模块检测
  - [x] 6.1 定义 `MatrixE2eeAvailability` 接口
    - 在 `relay/sender/api` 模块定义接口和 `E2eeModuleStatus` 枚举
    - 状态: AVAILABLE, NOT_INSTALLED, DOWNLOADING, INSTALL_FAILED, LOAD_FAILED, NOT_APPLICABLE
    - _Requirements: 6.1, 7.3, 7.4_

  - [x] 6.2 实现 GitHub 变体 FeatureLoader
    - `withE2ee` 源集: 通过 `Class.forName("...MatrixE2eeFeature")` 检测 → AVAILABLE
    - `noE2ee` 源集: 直接返回 NOT_APPLICABLE
    - 加载失败 → LOAD_FAILED，回退明文
    - _Requirements: 7.3, 7.4, 7.6_

  - [x] 6.3 实现 Play 变体 FeatureLoader（基础框架）
    - 通过 `SplitInstallManager` 检测 `matrix-e2ee` 模块安装状态
    - 支持触发安装、监听进度回调
    - 安装成功后通过 `SplitCompat` 加载模块
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 7. 构建系统配置
  - [x] 7.1 确认 GitHub 双变体 APK 构建配置
    - 验证 `assembleGithubWithE2eeDebug` 和 `assembleGithubNoE2eeDebug` 产出正确
    - 确保 `withE2ee` APK 包含 arm64-v8a native lib
    - 确保 `noE2ee` APK 不包含 native lib
    - 配置 APK 文件名规则: `XinyiRelay_v{version}_e2ee_{buildType}.apk`
    - _Requirements: 7.1, 7.2_

  - [x] 7.2 配置 Play flavor DFM 关联
    - 确保 `play` flavor 的 `:app` 正确引用 `feature/matrix-e2ee` DFM
    - 禁止 `play × withE2ee` 组合（仅使用 DFM 按需下载）
    - _Requirements: 6.1, 6.5_

- [-] 8. Checkpoint - 确认构建系统双变体正常
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. UI 状态扩展
  - [-] 9.1 扩展 `MatrixConfigForm` 展示 E2EE 状态指示器
    - E2EE 已启用 → 显示绿色 "E2EE 已启用" 状态卡片
    - E2EE 不可用（GitHub noE2ee） → 保留现有 info banner
    - Play 未安装 → 显示安装按钮
    - 下载中 → 显示进度条（0-100%），禁用安装按钮
    - 下载失败 → 重新启用按钮，显示错误信息
    - 下载成功 → 同 session 内刷新为 "已启用" 状态
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [x] 9.2 编写 UI 状态显示单元测试
    - 验证各种 `E2eeModuleStatus` 对应正确的 Compose 组件渲染
    - _Requirements: 10.1, 10.2, 10.3_

- [ ] 10. 密钥同步与 to-device 处理
  - [-] 10.1 确保 SDK Client 自动处理密钥同步
    - 验证 `matrix-rust-sdk` 的 `Client` 高层 API 自动处理密钥上传、同步和 to-device 事件
    - 如果 `Client` 不自动处理（需要手动 sync），添加发送前的 `client.sync()` 或等效一次性同步调用
    - 确保 one-time key 补充由 SDK 自动管理
    - _Requirements: 3.1, 3.2, 3.3, 9.1, 9.2, 9.3, 9.4, 9.5_

  - [x] 10.2 编写 Property 10: to-device 事件处理先于加密操作 属性测试
    - **Property 10: to-device 事件处理先于加密操作**
    - 通过执行顺序追踪验证 sync/to-device 处理先于 encrypt 调用
    - **Validates: Requirements 9.4**

- [ ] 11. 集成测试与端到端验证
  - [x] 11.1 编写加密发送链路集成测试
    - 使用 MockWebServer 模拟 homeserver
    - 测试完整链路: 初始化 → 房间状态检测 → 加密发送 / 明文回退
    - 测试连续发送复用 Client 实例
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 8.2, 8.3, 8.4_

  - [x] 11.2 编写 noE2ee 变体行为验证测试
    - 确认 noE2ee 变体的 `MatrixE2eeUtils.sendMsg` 直接委托给 `MatrixUtils.sendMsg`
    - 确认不触发任何加密相关代码路径
    - _Requirements: 7.3, 8.3_

- [x] 12. Final checkpoint - 确认所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- 现有 `withE2ee/MatrixE2eeUtils.kt` 已使用 `matrix-rust-sdk` 高层 `Client` API（`room.sendRaw()` 自动处理加密），无需降级到底层 `OlmMachine` API
- 设计文档描述的 `OlmMachine` 底层方案仅作为参考，实际实现利用 SDK 自动加密能力
- `DefaultSenderDispatcher` 已持有 `Context`，可传递给 `MatrixE2eeUtils` 用于获取存储路径
- Property 4 (OTK 阈值)、Property 5 (URL 构造)、Property 6 (Payload 结构)、Property 7 (txnId 幂等) 由 SDK 高层 API 内部保证，无需额外属性测试
- 每个属性测试使用 `io.kotest:kotest-property` 库，最少 100 次迭代

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "6.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "2.2", "2.3", "6.2", "6.3"] },
    { "id": 2, "tasks": ["3.1", "5.1", "7.1", "7.2"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4", "5.2"] },
    { "id": 4, "tasks": ["9.1", "10.1"] },
    { "id": 5, "tasks": ["9.2", "10.2", "11.1", "11.2"] }
  ]
}
```
