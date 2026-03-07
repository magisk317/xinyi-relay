# Xposed 运行时接入说明

## 入口模型
- 主入口：`io.github.magisk317.relay.xp.RelayXposedModule`
- `xposed_init` 指向主入口，由 libxposed 框架实例化。
- 业务 hook 调度统一通过 `HookEntry`，不再由 `HookEntry` 直接作为框架入口。

## Hook 兼容层
- 统一兼容层包：`io.github.magisk317.relay.xp.compat`
- 主要组件：
  - `XposedBridge`（hook 注册与日志）
  - `XposedHelpers`（反射与方法查找）
  - `XC_MethodHook` / `XC_MethodReplacement`（回调模型）
  - `XC_LoadPackage.LoadPackageParam`（包加载参数桥接）

## 配置读取链路
- `PrefsReader` 固定优先级：
  - `remote_libxposed -> provider -> shared_prefs -> default`
- `RuntimeBridge` 在 Debug 模式会输出一次能力日志，便于排查运行时能力与回退路径。

## 双包过渡发布
- `newapi`：主线包，公开发布（Play / GitHub）。
- `legacy`：过渡包，仅附件/内测用途，不进入公开 Play 轨道。
- 默认过渡周期：1 个小版本周期；周期结束后移除 legacy 工作流。
