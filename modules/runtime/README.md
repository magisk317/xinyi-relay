# 自定义消息广播接口 (Custom Broadcast)

信驿 Relay 开放了安全的广播入口，允许第三方自动化工具（如 Tasker）将消息喂入 Relay 管线。

## Intent 参数
- Action: `io.github.magisk317.relay.ACTION_INGEST_CUSTOM_MESSAGE`
- 必填参数：
  - `ipc_token`: (String) 用于鉴权，必须与本地生成的 Token 匹配。
  - `message`: (String) 消息正文。
- 选填参数：
  - `title`: (String) 消息标题
  - `app_name`: (String) 模拟来源应用名称
  - `package_name`: (String) 模拟来源包名
  - `notify_channel_id`: (String) 通知通道ID
  - `event_id`: (String) 唯一事件ID
  - `target_sender_ids`: (long[]) 仅向指定 senderId 列表分发

## 示例 (ADB)

```bash
adb shell am broadcast \
  -a io.github.magisk317.relay.ACTION_INGEST_CUSTOM_MESSAGE \
  -n io.github.magisk317.xinyi.relay/io.github.magisk317.relay.platform.ipc.CustomMessageReceiver \
  --es ipc_token YOUR_IPC_TOKEN \
  --es title "自定义消息" \
  --es message "Hello from adb" \
  --es app_name "ADB" \
  --es package_name "com.example.custom"
```
