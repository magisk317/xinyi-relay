# XposedSmsCode 项目重构与优化汇总文档 (REFACTORING.md)

本文档旨在记录并汇总近期对 `XposedSmsCode` 项目进行的系统性重构与性能优化工作。所有改进均严格遵循 Google 官方的 Jetpack Compose 最佳实践及 Kotlin 编程规范。

---

## 1. 界面与主题架构 (UI & Theme)

### 1.1 Material 3 Expressive 深度迁移
- **Material 3 Expressive 深度迁移**：全面升级至 `androidx.compose.material3:material3:1.5.0-alpha12`，引入原生支持的表现力 (Expressive) 组件、形状、动效及现代化的排版系统。
- **反馈系统重塑**：弃用传统的 `Toast`，在所有主要 Screen (`RuleList`, `RuleEdit`, `Settings`) 中引入 `SnackbarHostState`，实现异步且风格一致的交互反馈。
- **组件规范化**：重构 `ComposeSettingsScreen.kt` 中的自定义项，全面基于 M3 `ListItem` 标准实现，确保点击波纹、内边距及字体比例完美符合规范。

### 1.2 高级交互组件
- **Swipe-to-Dismiss**：在规则列表 (`RuleListScreen`) 中实现滑动删除，提升管理效率。
- **Exposed Dropdown Menus**：在规则编辑页 (`RuleEditScreen`) 的快速选择功能中使用官方 `ExposedDropdownMenuBox`，替换非标准的自定义按钮。

---

## 2. 文本交互与排版优化 (Text & Typography)

### 2.1 交互性增强
- **可选择文本**：在短信记录列表 (`CodeRecordScreen`) 中为短信内容应用 `SelectionContainer`，方便用户快速复制验证码。
- **长文本排版**：对 FAQ 等长段落应用 `LineBreak.Paragraph` 和渐进式连接符，优化中英文混排的视觉平衡。

### 2.2 视觉引导
- **高亮展示**：使用 `AnnotatedString` 为列表中的正则关键字添加主色高亮，增强界面扫描效率。
- **输入引导**：在正则输入框中使用 `prefix` 特性（显示 "RE: "），明确输入意图。
- **自动跑马灯**：为长标题应用 `basicMarquee` 效果，避免文本截断。

---

## 3. 图形与无障碍优化 (Graphics & Accessibility)

### 3.1 资源管理
- **异步图标加载**：封装 `AppIconImage` 模块，通过 `LaunchedEffect` 在 IO 线程加载应用图标，并提供占位符与淡入动效，防止 UI 线程阻塞。
- **规范剪裁**：使用 `Modifier.clip` 统一应用标准的 M3 圆角形状。

### 3.2 无障碍合规
- **语义增强**：补全所有图标及关键组件的 `contentDescription`，为 `Checkbox` 添加动态状态描述。
- **对比度优化**：基于 M3 调色板重新校准颜色使用。

---

## 4. 动画体验优化 (Animation)

### 4.1 容器与列表动效
- **平滑状态切换**：使用 `AnimatedContent` 包装 Screen 级状态切换（加载中/空数据/内容区），消除界面跳变。
- **列表自适应动效**：在 `LazyColumn` 中引入 `Modifier.animateItem()`，使列表项在增删改时具备自然的物理移动感。

### 4.2 细节打磨
- **组件显隐动画**：使用 `AnimatedVisibility` 配合 `expandVertically` 等过渡效果处理校验信息及进度条的显示。
- **自定义 Reveal 动效**：在切换主题时，利用 `graphicsLayer` 实现基于点击坐标的圆形揭露动效。

---

## 5. 性能加固与稳定性 (Performance & Stability)

### 5.1 数据模型稳定性
- **完全不可变性**：将核心实体类 (`SmsMsg`, `SmsCodeRule`, `AppInfo`) 的所有属性从 `var` 改为 `val`。
- **编译期跳过 (Skipping)**：添加 `@Immutable` 注解，允许 Compose 编译器在重组过程中安全地跳过未变化的数据项，极大降低重组频率。

### 5.2 列表性能极致优化
- **集合稳定性包装**：引入 `ImmutableListWrapper<T>` 自定义包装类，解决标准 `List` 接口在 Compose 编译器中被视为“不稳定”的问题，确保列表滚动时零冗余重组。

### 5.3 渲染阶段延迟 (Phase Deferral)
- **绘制逻辑下放**：审计复杂自定义动效，确保高频变化的数值读取（如 `revealAnim.value`）仅在绘制阶段（lambda 内部）进行，避开开销巨大的重组阶段。

---

## 6. Kotlin for Compose 最佳实践 (Kotlin Best Practices)

### 6.1 状态声明标准化
- **属性代理 (Property Delegation)**：全面普及 `by remember { mutableStateOf(...) }` 语法，淘汰繁杂的 `.value` 手写访问，使代码逻辑更简洁。
- **基本类型优化**：在计数、索引等场景引入 `mutableIntStateOf` 等专用状态函数，避免 JVM 自动装箱带来的额外内存开销。

### 6.2 强规范组件定义
- **Modifier 链标准**：确保所有内部 Composable 组件遵循 `modifier: Modifier = Modifier` 惯例，并作为首个可选参数，赋予组件高度的外部扩展性。
- **Trailing Lambdas**：严格遵守 Kotlin lambda 结尾写法，提升 DSL 风格代码的可读性。

---

> [!TIP]
> **后续开发建议**：
> 1. 新增数据实体时，请务必保持属性不可变（val）并添加 `@Immutable` 标签。
> 2. 展示列表数据前，应使用 `ImmutableListWrapper` 进行包装。
> 3. 避免在 Composable 内部进行复杂的、未记住的逻辑计算。
