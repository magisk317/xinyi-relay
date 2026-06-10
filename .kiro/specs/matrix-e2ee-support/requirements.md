# Requirements Document

## Introduction

为 xinyi-relay 项目的 Matrix 发送通道增加端到端加密（E2EE）支持。当目标 Matrix 房间启用了加密时，消息将自动通过 Olm/Megolm 协议加密后发送，消除 Element 等客户端中显示的"未加密"红色警告。加密功能通过 `matrix-rust-sdk`（`org.matrix.rustcomponents:sdk-android`）实现，以动态功能模块（Play Feature Delivery）或独立 APK 变体（GitHub `-e2ee` 后缀）的形式交付，保持基础 APK 体积不受影响。

## Glossary

- **Crypto_Engine**: 基于 matrix-rust-sdk 的加密引擎组件，负责管理 Olm/Megolm 会话、设备密钥、群组密钥分发以及消息加密操作
- **Crypto_Store**: 加密状态持久化存储层，保存设备密钥、Olm 会话、Megolm 入站/出站会话等加密状态，确保应用重启后会话不丢失
- **E2EE_Sender**: 加密消息发送组件，在房间启用加密时使用 Crypto_Engine 加密消息内容并发送 `m.room.encrypted` 事件
- **Room_Crypto_State**: 房间加密状态检查组件，负责查询目标房间是否启用了端到端加密（通过 `m.room.encryption` 状态事件）
- **Key_Sync**: 密钥同步组件，负责上传本设备公钥至 homeserver、查询房间成员设备密钥、建立 Olm 会话并分发 Megolm 群组密钥
- **Feature_Loader**: 动态功能模块加载组件，负责在运行时检测和加载 E2EE 模块（Play 版本通过 SplitInstallManager，GitHub 版本通过编译时捆绑）
- **Plaintext_Sender**: 现有的明文消息发送组件（MatrixUtils），通过 HTTP PUT 发送 `m.room.message` 事件
- **MatrixSetting**: Matrix 渠道配置数据类，包含 homeserver、accessToken、roomId 等连接参数

## Requirements

### Requirement 1: 房间加密状态检测

**User Story:** 作为消息转发系统，我希望在发送消息前自动检测目标房间是否启用了端到端加密，以便决定使用加密发送还是明文发送。

#### Acceptance Criteria

1. WHEN a message is about to be sent to a Matrix room, THE Room_Crypto_State SHALL query the room's `m.room.encryption` state event via the Matrix Client-Server API (`GET /_matrix/client/v3/rooms/{roomId}/state/m.room.encryption`) within a timeout of 10 seconds to determine if E2EE is enabled
2. WHEN the room has an `m.room.encryption` state event with algorithm `m.megolm.v1.aes-sha2`, THE Room_Crypto_State SHALL report the room as encryption-enabled
3. WHEN the room does not have an `m.room.encryption` state event, THE Room_Crypto_State SHALL report the room as encryption-disabled
4. IF the room has an `m.room.encryption` state event with an algorithm other than `m.megolm.v1.aes-sha2`, THEN THE Room_Crypto_State SHALL report the room as encryption-disabled and log a warning indicating the unsupported algorithm
5. IF the room state query fails due to network timeout, non-2xx HTTP response, or unparseable response body, THEN THE Room_Crypto_State SHALL treat the room as encryption-disabled and allow fallback to plaintext sending
6. THE Room_Crypto_State SHALL cache the encryption status for each room in memory (cleared on app process restart), and reuse the cached result for subsequent messages to the same room without additional API calls
7. WHEN a room's cached encryption status is older than 60 minutes, THE Room_Crypto_State SHALL re-query the room state on the next send attempt and update the cache

### Requirement 2: 加密引擎初始化与状态持久化

**User Story:** 作为消息转发系统，我希望加密引擎在首次使用时自动完成初始化（生成设备密钥、创建 OlmMachine），并将加密状态持久化到本地存储，以确保应用重启后不需要重新建立所有加密会话。

#### Acceptance Criteria

1. WHEN the E2EE module is loaded for the first time (no existing Crypto_Store data for the current user), THE Crypto_Engine SHALL initialize an OlmMachine instance via matrix-rust-sdk with the user's userId and a generated deviceId within 30 seconds
2. WHEN the Crypto_Engine initializes, THE Crypto_Store SHALL create a persistent storage directory in the app's internal files area at path `matrix-crypto/{userId-hash}/`
3. WHEN the Crypto_Engine has been previously initialized and valid state exists in Crypto_Store, THE Crypto_Engine SHALL restore the OlmMachine from persisted state using the previously generated deviceId without generating new keys
4. THE Crypto_Store SHALL persist all Olm sessions, Megolm inbound/outbound group sessions, device keys, and tracked user lists across app restarts
5. IF the Crypto_Store data fails integrity verification on load (file I/O errors, deserialization failures, or schema version mismatch), THEN THE Crypto_Engine SHALL delete the corrupted store directory and reinitialize from scratch, logging a warning that includes the failure reason
6. IF the Crypto_Engine initialization fails due to a runtime error (insufficient disk space, SDK library load failure, or OlmMachine creation error), THEN THE Crypto_Engine SHALL report the failure to the caller and the system SHALL fall back to plaintext sending via Plaintext_Sender

### Requirement 3: 设备密钥上传与同步

**User Story:** 作为消息转发系统，我希望本设备的公钥能自动上传到 homeserver，并能查询房间内其他成员的设备密钥，以建立加密通信所需的 Olm 会话。

#### Acceptance Criteria

1. WHEN the Crypto_Engine initializes or detects keys have not been uploaded, THE Key_Sync SHALL upload the device's identity keys and a batch of 50 one-time keys to the homeserver via `/keys/upload` API
2. WHEN sending an encrypted message to a room, THE Key_Sync SHALL query the device keys of all room members via `/keys/query` API and cache the results for subsequent messages to the same room
3. WHEN the homeserver reports the remaining one-time key count falls below 25, THE Key_Sync SHALL generate and upload a new batch of one-time keys to restore the count to 50
4. IF the key upload request fails, THEN THE Key_Sync SHALL retry the upload on the next send attempt without blocking the current message and fall back to plaintext sending via Plaintext_Sender if encryption cannot proceed
5. IF the `/keys/query` request fails due to network error or invalid response, THEN THE Key_Sync SHALL fall back to plaintext sending via Plaintext_Sender for the current message and log the failure reason

### Requirement 4: 加密消息发送

**User Story:** 作为消息转发系统，我希望在目标房间启用加密时，消息内容自动通过 Megolm 加密后以 `m.room.encrypted` 事件发送，消除接收端的"未加密"警告。

#### Acceptance Criteria

1. WHEN a message needs to be sent to an encryption-enabled room and the Crypto_Engine is available, THE E2EE_Sender SHALL encrypt the message content using the Megolm outbound group session for that room
2. WHEN the encrypted ciphertext is produced, THE E2EE_Sender SHALL send an `m.room.encrypted` event via HTTP PUT to `/_matrix/client/v3/rooms/{roomId}/send/m.room.encrypted/{txnId}` with algorithm `m.megolm.v1.aes-sha2`, using a UUID-based txnId unique per message, with a request timeout of 30 seconds
3. WHEN no outbound Megolm session exists for the room, THE E2EE_Sender SHALL create a new outbound session and distribute the group key to all room members' devices via Olm-encrypted to-device messages before encrypting the message
4. THE E2EE_Sender SHALL include `sender_key`, `session_id`, `device_id`, and `ciphertext` fields in the encrypted event payload
5. IF the encryption operation fails, THEN THE E2EE_Sender SHALL fall back to plaintext sending via Plaintext_Sender and log the encryption failure reason
6. IF the HTTP PUT request for the encrypted event fails or times out, THEN THE E2EE_Sender SHALL retry the send once with the same txnId, and if the retry also fails, SHALL fall back to plaintext sending via Plaintext_Sender and log the HTTP failure reason
7. IF key distribution to one or more room members' devices fails during outbound session creation, THEN THE E2EE_Sender SHALL proceed with sending the encrypted message to devices that received the key and log a warning listing the devices that could not be reached

### Requirement 5: 明文回退机制

**User Story:** 作为消息转发系统，我希望在加密模块不可用或加密操作失败时，自动回退到明文发送，确保消息始终能被投递。

#### Acceptance Criteria

1. WHEN the E2EE module is not installed or not loaded, THE Plaintext_Sender SHALL send messages as `m.room.message` events without any encryption attempt
2. WHEN the target room does not have encryption enabled, THE Plaintext_Sender SHALL send messages as `m.room.message` events regardless of E2EE module availability
3. IF the Crypto_Engine encounters an error during encryption that is not resolvable by retry (including but not limited to: missing Olm session that cannot be established, Megolm session creation failure, or encryption operation exceeding 30 seconds), THEN THE E2EE_Sender SHALL delegate to Plaintext_Sender to complete the message delivery with the original message content unchanged
4. WHEN a plaintext fallback occurs for an encryption-enabled room, THE E2EE_Sender SHALL log a warning that includes the fallback trigger type (module unavailable, encryption timeout, or encryption error) and the target room identifier
5. IF the Plaintext_Sender fails to deliver the message after fallback (network error or server rejection), THEN THE E2EE_Sender SHALL propagate the delivery failure to the caller without further retry

### Requirement 6: 动态功能模块加载（Play 版本）

**User Story:** 作为 Play 版本用户，我希望 E2EE 功能通过 Play Feature Delivery 按需下载，以保持基础安装包体积较小。

#### Acceptance Criteria

1. WHEN the user enables E2EE in Matrix settings on the Play flavor, THE Feature_Loader SHALL request module installation via SplitInstallManager
2. WHEN the dynamic feature module is successfully installed, THE Feature_Loader SHALL load the Crypto_Engine classes and make E2EE functionality available for subsequent sends within 5 seconds of installation completion
3. WHILE the dynamic feature module is downloading, THE Feature_Loader SHALL report download progress as a percentage value (0–100) to the UI layer, updating at least once per second or on each percentage point change
4. IF the dynamic feature module download fails, THEN THE Feature_Loader SHALL display an error message indicating the failure reason, allow the user to retry the download, and continue using plaintext sending until the module is successfully installed
5. WHEN the app process restarts after module installation, THE Feature_Loader SHALL detect the already-installed module and load it without re-downloading
6. IF the dynamic feature module is installed but fails to load (class loading error or Crypto_Engine initialization failure), THEN THE Feature_Loader SHALL report the error to the UI layer, fall back to plaintext sending, and allow the user to retry module loading

### Requirement 7: GitHub 变体分发

**User Story:** 作为 GitHub 版本用户，我希望能选择下载包含 E2EE 支持的 APK 变体（带 `-e2ee` 后缀），E2EE 功能编译时即捆绑无需额外下载。

#### Acceptance Criteria

1. THE build system SHALL produce two APK variants for the GitHub flavor: a default variant without E2EE and an E2EE variant whose filename contains a `-e2ee` suffix before the file extension
2. WHEN building the E2EE variant, THE build system SHALL bundle the matrix-rust-sdk native libraries (arm64-v8a only) into the APK, and SHALL exclude these native libraries from the default variant
3. WHEN running the default GitHub variant, THE Feature_Loader SHALL report E2EE as unavailable, and THE app SHALL route all messages through Plaintext_Sender regardless of room encryption state
4. WHEN running the E2EE GitHub variant, THE Feature_Loader SHALL detect the bundled crypto libraries during app initialization and report E2EE as available before the first message send is attempted
5. THE default GitHub variant SHALL display an info banner in the Matrix configuration UI indicating that E2EE is not available in this variant and suggesting the user download the E2EE variant for encrypted room support
6. IF the E2EE GitHub variant fails to load the bundled crypto libraries at startup, THEN THE Feature_Loader SHALL report E2EE as unavailable, log the failure reason, and THE app SHALL fall back to plaintext sending

### Requirement 8: 发送路径透明切换

**User Story:** 作为消息转发系统，我希望加密与明文发送路径的切换对上层业务逻辑透明，现有的 MatrixUtils.sendMsg 调用方无需修改代码。

#### Acceptance Criteria

1. THE E2EE_Sender SHALL expose the same method name, parameter types (MatrixSetting and MsgInfo), and return type (send result indicating success or failure) as the existing Plaintext_Sender, such that callers may use either implementation without modification
2. IF Feature_Loader reports E2EE module as available AND Room_Crypto_State reports the target room as encryption-enabled, THEN THE sender dispatch logic SHALL route the message through E2EE_Sender
3. IF Feature_Loader reports E2EE module as unavailable, THEN THE sender dispatch logic SHALL route all messages through Plaintext_Sender regardless of room encryption state
4. IF Feature_Loader reports E2EE module as available AND Room_Crypto_State reports the target room as encryption-disabled, THEN THE sender dispatch logic SHALL route the message through Plaintext_Sender
5. THE sender dispatch logic SHALL evaluate Feature_Loader availability status and Room_Crypto_State for the target room on each send invocation to determine routing, requiring no state configuration from the caller
6. WHEN the sender dispatch logic routes a message through either path, THE sender dispatch logic SHALL return the same result type to the caller regardless of which path was selected

### Requirement 9: to-device 消息处理

**User Story:** 作为消息转发系统，我希望能接收和处理其他设备发来的 to-device 消息（如密钥请求），以维持加密会话的正常运作。

#### Acceptance Criteria

1. WHEN preparing to send an encrypted message, THE Key_Sync SHALL call `/sync` with a filter that includes only to-device events (excluding room timeline, presence, and account data), using the persisted `since` token from the previous sync response and a timeout of 0 seconds for non-blocking polling
2. WHEN to-device events are received, THE Crypto_Engine SHALL pass all received events to the OlmMachine for processing to update Olm/Megolm session state before returning control to the encryption flow
3. WHEN the Crypto_Engine produces outgoing to-device messages (as returned by OlmMachine after processing), THE Key_Sync SHALL send each message via the `/sendToDevice` API, and IF the send request fails, THEN THE Key_Sync SHALL log the failure and continue with the encryption attempt using the current session state
4. THE Crypto_Engine SHALL process to-device events before attempting message encryption to ensure the latest session state is used
5. IF the `/sync` call for to-device events fails due to network error or non-2xx response, THEN THE Key_Sync SHALL log the failure and proceed with message encryption using the existing session state without blocking the send operation

### Requirement 10: 配置 UI 扩展

**User Story:** 作为用户，我希望在 Matrix 配置界面中看到 E2EE 功能状态（已启用/未安装/下载中），并能触发模块安装操作。

#### Acceptance Criteria

1. WHEN E2EE module is installed and active, THE MatrixConfigForm SHALL display an "E2EE 已启用" status indicator and SHALL NOT display the download button or info banner
2. WHEN E2EE module is not installed (Play flavor), THE MatrixConfigForm SHALL display a button labeled with install intent that, when tapped, triggers module download via Feature_Loader
3. WHEN E2EE module is not available (default GitHub variant), THE MatrixConfigForm SHALL display the existing info banner suggesting the E2EE variant and SHALL NOT display the download button
4. WHILE module download is in progress (Play flavor), THE MatrixConfigForm SHALL display a progress indicator showing download percentage (0–100%) and SHALL disable the install button to prevent duplicate requests
5. IF module download fails (Play flavor), THEN THE MatrixConfigForm SHALL re-enable the install button and display an error message indicating the download failure reason
6. WHEN module download completes successfully (Play flavor), THE MatrixConfigForm SHALL update the display from the progress indicator to the "E2EE 已启用" status indicator within the same session without requiring a page reload
