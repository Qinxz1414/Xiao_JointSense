# JointSense v2 全面升级设计规格

> 状态：已由用户于 2026-08-07 分段确认
>
> 范围：多模块架构、Navigation Compose、Room、协程、交互完善、缺陷修复、中英文、A「关节信号」Logo、测试与文档

## 1. 背景

JointSense 是一款面向 OA 炎症因子照片定量与趋势观察的 Android 应用。现有代码使用 Kotlin、Jetpack Compose、单一 `AndroidViewModel`、手写页面状态导航及 SharedPreferences JSON 持久化。应用已有首页、趋势、报告、个人中心、检测、历史和标准曲线校准页面，但缺少可执行的回归测试和完整构建证据。

本轮不采用局部修补方案。用户明确选择全面迁移到 Navigation Compose、Room、协程和多模块架构，并要求同时完成新 Logo、交互修整、缺陷修复和简体中文/英语切换。

## 2. 已确认的产品决策

- 支持简体中文和英语；默认跟随系统，个人中心可选“跟随系统 / 简体中文 / English”。
- 中文文案采用科研/临床专业表达。
- 非诊断声明只显示在“关于”页面和导出报告中，不占用日常结果页面。
- 保留 Home、Trends、Report、Profile 四个底部目的地与中央检测动作。
- 页面顶部返回、系统返回和预测性返回使用同一返回栈。
- 检测结果页是流程终点；返回时回到发起检测前的页面，不回到因子选择页。
- 采用 A「关节信号」Logo：两端关节、中央滑液检测孔和信号弧。
- “清空全部数据”在单一事务中删除用户检测、内置样例和用户校准。
- 内置 12 板样例只在首次安装初始化；清空后不自动出现，可在个人中心手动恢复。
- 所有最终信息和验证结果必须同步回写 `项目结构需求梳理.md`。

## 3. 分阶段交付

### 阶段 1：架构底座

建立多模块结构、类型安全 Navigation Compose、Room 数据库、旧 SharedPreferences 无损迁移、协程/Flow 状态链路和中英文切换。该阶段完成后，现有核心能力应可在新架构上运行，旧数据保持可访问。

### 阶段 2：检测体验与缺陷修复

重构图片选择、解码、裁剪、分析、保存和校准状态机；补齐权限、错误恢复、幂等写入与生命周期恢复；为已定位的算法和交互问题建立回归测试。

### 阶段 3：品牌与发布质量

接入 A「关节信号」矢量 Logo 和 Android Adaptive Icon；统一首页、报告、个人中心与流程页面的临床工作台视觉；完成双语、PDF、无障碍、Lint、构建和可用设备上的仪器测试。

三个阶段顺序执行，但均属于本次任务范围。

## 4. 目标模块结构

采用适度颗粒度，避免为每个页面建立独立 Gradle 模块。

```text
:app
├── :feature:insights       # Home、Trends、Report
├── :feature:measurement    # 图片选择、裁剪、检测、结果、历史
├── :feature:calibration    # 标准曲线校准流程
├── :feature:settings       # Profile、语言、关于、数据管理
├── :core:domain            # 领域模型、仓库接口、用例
├── :core:analysis          # RGB 特征、标准曲线、校准验证、OA 指数
├── :core:designsystem      # 主题、公共 Compose 组件、Logo、公共资源
├── :core:data              # 仓库实现、样例、旧数据协调迁移、Flow 映射
└── :core:database          # Room entities、DAO、数据库、schema

build-logic                 # Android、Kotlin、Compose、Room 约定插件
```

运行时依赖保持单向：

- `:app` 负责应用装配、NavHost、底栏和根依赖容器。
- 功能模块依赖 `:core:domain`、`:core:designsystem`，需要算法时依赖公开的分析接口。
- 功能模块不得访问 DAO、Room entity 或其他功能模块的内部实现。
- `:core:data` 实现领域仓库，并依赖 `:core:database`。
- `:core:analysis` 保持纯 Kotlin；Compose `Color` 等 UI 类型不得出现在算法 API 中。
- 不引入 Hilt。应用规模使用显式 `AppContainer` 和 ViewModel factory 即可，避免额外代码生成和版本耦合。

## 5. 导航与状态恢复

### 5.1 类型安全路由

路由使用 Kotlin Serialization 支持的 `object` 或 `data class`。路由参数只传稳定标识符，例如 `sessionId`、`resultId`，不传 `Bitmap`、完整数据对象或翻译字符串。

顶层目的地为 Home、Trends、Report、Profile。底栏选择记录顶层历史；返回优先回到上一个顶层页面，最终回到 Home。只有 Home 根页面再次返回时才将退出交给系统。

### 5.2 检测流程

```text
ImageSelect → Crop → FactorSelect → Result
```

- 前三步支持逐级返回。
- Result 是已提交终点，返回 `originDestination`。
- “继续检测”创建新的 `draftId`，不复用已提交结果。
- Result 通过 `resultId` 从仓库读取，不依赖进程内 `lastResult`。

### 5.3 校准流程

```text
Select → Crop → Assign → Review → Done
```

校准 ViewModel 作用域绑定到校准导航图。各步骤逐级返回；离开整个图才释放草稿。

### 5.4 可恢复状态

- `SavedStateHandle` 保存 `imageUri`、`cropRect`、`factor`、`originDestination` 和 `draftId`。
- 图片保存在应用临时文件并通过 URI 引用；导航和 ViewModel 不长期持有全尺寸 `Bitmap`。
- 旋转、语言切换和系统重建后恢复当前位置与未提交草稿。
- 页面顶部按钮、系统 Back 和预测性返回统一委托给 NavController/返回分发器。

## 6. Room 数据设计

数据库 v1 包含以下表：

### `test_session`

- `id`：String 主键。
- `name`：会话名称。
- `createdAt`：UTC epoch millis。
- `source`：`USER` 或 `BUILT_IN`。

### `test_result`

- `id`：String 主键。
- `sessionId`：外键，删除会话时级联删除。
- `factor`、`concentration`、`timestamp`。
- `rMean`、`gMean`、`bMean`、`rStd`、`gStd`、`bStd`。
- `rangeStatus`：`UNKNOWN`、`BELOW_RANGE`、`IN_RANGE` 或 `ABOVE_RANGE`；旧记录迁移为 `UNKNOWN`。
- `draftId`：用户新检测必填，旧记录可为 null；对非 null 值建立唯一索引以防止重复提交。

### `calibration`

- `factor`：主键。
- `createdAt`、`version`、`status: ACTIVE | NEEDS_REVIEW` 和可选试剂盒元数据。

### `calibration_knot`

- 复合主键：`factor + position`。
- `concentration`、`rawSignal`、`fittedSignal`、`isBlank`。
- 删除 calibration 时级联删除。

### `app_metadata`

- 键值记录 `legacyMigrationStatus`、`samplesInitialized` 和数据初始化审计信息。

DAO 对连续读取返回 `Flow`，一次性写操作使用 `suspend`。所有跨表写入、统一清空和样例恢复均使用 Room 事务。

## 7. 旧数据迁移与样例策略

首次启动协调器按以下顺序执行：

1. 检查 Room 中的迁移标记。
2. 读取并完整验证 `joint_sense_data` 和 `joint_sense_calibration` 旧 JSON。
3. 在单一事务中导入会话、结果和校准；任一失败则整体回滚。
4. 若没有旧会话且样例从未初始化，写入 12 板 `BUILT_IN` 数据。
5. 成功后记录迁移和样例初始化标记。

旧 SharedPreferences 在本轮升级中保留为只读回退源，不在迁移成功后删除。运行时的新增、修改和删除只写 Room。

迁移失败时不得把旧数据解释为空库并静默写入样例。应用显示可恢复迁移错误，提供重试和经二次确认后从空数据库开始的操作。用户选择从空库开始时，写入 `legacyMigrationStatus=SKIPPED_BY_USER` 和 `samplesInitialized=true`，旧 JSON 仍保持不变且不再自动重试。

旧校准结构完整且通过新验证规则时迁移为 `ACTIVE`；结构可读取但不符合 Blank、动态范围或单调性规则时仍保留全部节点，但标记为 `NEEDS_REVIEW`，不自动应用于新检测，并在个人中心提示重新校准。

“清空全部数据”删除所有会话、结果、用户校准和校准节点，但保留 `samplesInitialized=true`，所以重启不会自动回填。个人中心“恢复样例数据”只恢复 `BUILT_IN` 会话，不恢复用户校准。

## 8. 国际化

- `MainActivity` 继承 `AppCompatActivity`。
- 通过 `AppCompatDelegate.setApplicationLocales()` 实现 Android 7–12 兼容，并与 Android 13+ 系统应用语言设置同步。
- 启用 AGP 自动 `LocaleConfig`；默认资源使用英语，简体中文使用 `values-zh-rCN`。
- 个人中心提供跟随系统、简体中文、English 三项。
- 所有可见文本、无障碍描述、弹窗、错误、复数和报告文案进入 Android 资源。
- 领域枚举只暴露稳定代码；功能/设计系统模块将代码映射到本地化资源。
- 日期、数字和百分比按当前 Locale 格式化；`pg/mL`、TNF-α、IL-6、IL-1β 等科学表达保持标准形式。
- 语言切换允许 Activity 重建，并依靠 Navigation 和 SavedStateHandle 恢复页面与草稿。

关于页面和导出报告使用以下固定双语声明；结果页不重复显示：

- 中文：“本报告结果基于手机照片色度代理估算，仅供科研与纵向趋势观察，不作为临床诊断、治疗决策或替代经验证实验室检测的依据。”
- English: “Results in this report are estimates derived from smartphone-photo colorimetry for research and longitudinal trend observation only. They are not intended for clinical diagnosis, treatment decisions, or as a substitute for validated laboratory testing.”

## 9. 检测与校准状态机

检测 ViewModel 暴露单一 `StateFlow<MeasurementUiState>`。主要状态为：

```text
AwaitingImage → Decoding → ReadyToCrop → ReadyToAnalyze
              → Analyzing → Persisting → Success
                              ↘ RecoverableError
```

- 相册使用 `ActivityResultContracts.PickVisualMedia`。
- 拍照只在用户选择相机时请求 CAMERA 权限。
- 图片先读取尺寸和方向，再按分析上限采样解码，避免全尺寸 OOM。
- URI/文件读取在 IO dispatcher，RGB 特征和曲线计算在 Default dispatcher。
- Compose 通过 `collectAsStateWithLifecycle` 订阅状态。
- 分析期间禁用重复提交；Repository 依靠唯一 `draftId` 保证落库幂等。
- 临时图片在草稿完成或整个流程放弃后清理。

可恢复错误必须保留有效草稿：

- 权限拒绝：说明原因；永久拒绝时提供系统设置入口。
- 图片损坏、格式不支持或解码失败：停留在选择页并允许重选。
- 裁剪区域无效：停留在裁剪页并指出约束。
- 分析失败：保留裁剪和因子，允许重试。
- 数据写入失败：不得展示成功结果；使用相同 `draftId` 重试。

## 10. 算法与校准修复

已确认的标准曲线正向插值缺陷必须先写失败测试，再修正浓度/信号变量解构。正向插值以 signal 为自变量、concentration 为输出；反向函数以 concentration 为自变量、signal 为输出，二者测试名称和变量名不得混用。

定量工作范围只由实际有效曲线节点定义。signal 低于最低节点时返回 `0 pg/mL + BELOW_RANGE`；位于节点范围内时分段线性插值并返回 `IN_RANGE`；高于最高节点时返回最高节点浓度并标记 `ABOVE_RANGE`，不跳变到独立的 OA `caps`，也不进行无依据外推。OA 归一化 `caps` 只用于组合指数，不能作为定量曲线端点。

校准保存前必须满足：

- 恰好 9 个有效孔读数。
- 有且仅有一个浓度为 0 的 Blank。
- 所有浓度为有限非负数，非 Blank 浓度唯一。
- 所有信号为有限数；扣除 Blank 后 `max(netSignal) - min(netSignal)` 至少为 `8.0` 个 tealness 单位。
- 按浓度排序后的拟合信号单调不减。

对轻微非单调噪声使用池相邻违反者算法生成 `fittedSignal`，同时在复核页展示原始值与拟合值。允许的最大单点调整为 `max(3.0, 原始动态范围 × 15%)` 个 tealness 单位；超过该值或动态范围不足 `8.0` 时阻止保存，并要求重新裁剪、重新拍摄或修正孔位。这两个数值是照片色度流程的工程质量门槛，不宣称临床判定意义。错误文本不得把无效输入自动转成 0。

## 11. 已定位缺陷清单

以下问题纳入本轮回归范围：

1. `MainActivity.kt` 存在重复 `FlowScreen.CALIBRATION` when 分支。
2. `StandardCurve.concentrationFor()` 正向插值把浓度和信号变量反向解构。
3. 标准曲线末节点与独立最大浓度映射含义不一致。
4. 无 Blank 校准会静默把第 1 孔当作 Blank。
5. 非数字校准输入被静默转换为 0。
6. 图片在主线程全尺寸解码，存在卡顿和 OOM 风险。
7. 同步分析使“Analyzing”状态难以及时渲染。
8. 相机权限、图片损坏和解码异常缺少用户可见反馈。
9. 相机返回期间 `photoUri` 和校准 `remember` 状态可能丢失。
10. 手写 JSON 解析任一异常即返回空列表，可能被误当首次启动并覆盖为样例。
11. 清空后重启会重新写入样例，与“清空”语义冲突。
12. 会话名称使用 `sessions.size + 1`，删除后可能重复。
13. PDF 使用单行 `drawText`，长行与中文内容可能裁切。
14. 算法层引用 Compose `Color`，阻碍纯 Kotlin 模块隔离。
15. 当前 UI 文案和无障碍描述几乎全部硬编码为英语。

每个可自动化问题都要有能够在旧实现上失败、在修复后通过的测试。

## 12. 品牌与界面系统

A「关节信号」标记由两端关节、中央检测孔和两段分析信号弧组成。资产以矢量为源，派生：

- 应用内全彩 Logo。
- Adaptive Icon 前景。
- 单色/通知可用版本。
- 深色和浅色表面均可读的组合。

关键图形必须位于 Android Adaptive Icon 安全区，圆形和圆角矩形裁切不丢失。旧紫色医疗芯片 Logo 和紫色品牌用色全部移除。

界面沿用需求文档中的视觉令牌：

- `InkText #0E2841`：结构锚点和顶栏。
- `PrimaryAccent #156082`：主操作和 OA 趋势。
- `CyanAccent #0F9ED5`：次级分析强调。
- `BioGreen #196B24`：有效、成功和生物解释。
- 炎症因子和 0–4 等级保持现有稳定映射。
- ELISA WellPalette 只用于实验信号，不用于通用应用 chrome。

首页优先呈现 OA 指数、等级、最新三因子定量值、最近趋势和开始检测。个人中心集中语言、校准、历史、样例恢复、统一清空和关于入口。所有触控目标至少 48dp；颜色编码同时配套文字、数值或形状；动态字体下不得裁切关键内容。

## 13. 报告与导出

报告生成器使用结构化报告模型，不从 Composable 拼接字符串。屏幕分享、文本分享和 PDF 使用同一经本地化的报告数据源。

PDF 必须：

- 支持中英文、自动换行、分页和页边距。
- 包含生成时间、因子名称、数值、单位、OA 指数、等级和趋势摘要。
- 包含非诊断声明。
- 文件写入使用关闭安全的资源管理；分享失败给出用户可见反馈。

## 14. 测试与验收

### JVM 单测

- 标准曲线低端、高端、节点、中点、重复信号和范围状态。
- OA 权重、缺失因子重归一化和 0–4 阈值。
- 校准 Blank、浓度、动态范围、单调拟合和拒绝条件。
- 报告模型和双语数值格式化。

### Room 与迁移测试

- 旧会话、结果和校准完整导入。
- 损坏 JSON 不产生半迁移状态。
- 外键级联、唯一 draftId 和事务回滚。
- 样例只初始化一次、统一清空后不回填、手动恢复可重复且不产生副本。

### ViewModel 与协程测试

- 状态顺序、dispatcher 边界、取消、错误恢复和双击幂等。
- 旋转/重建所需的 SavedStateHandle 数据。

### Compose/仪器测试

- 所有检测和校准步骤逐级返回。
- Result 返回来源页。
- 顶层历史和 Home 退出边界。
- 语言切换后页面、导航和草稿恢复。
- 清空与恢复样例确认流程。

### 构建门槛

- 完整 JVM 测试通过。
- Android Lint 无阻断问题。
- Debug APK 构建成功。
- 有可用模拟器或真机时，运行全部仪器测试。

最终报告必须区分已实际执行的验证与因环境无法执行的验证。没有新鲜命令输出时不得声称测试或构建通过。

## 15. 文档同步

`项目结构需求梳理.md` 是项目交接主文档。实施期间每完成一个阶段即更新模块树、数据流、迁移策略、交互、已修缺陷和验证结果；最终状态不得只存在于本设计规格或代码注释中。

## 16. 非目标

- 不把照片色度代理宣称为临床级 OD450 检测。
- 不启用占位 `PredictionModel` 或引入未经验证的机器学习权重。
- 不在本轮引入云端账户、同步、远程分析或后端服务。
- 不改成动态特性下载模块。
- 不在没有实测依据时重新发明炎症权重、曲线节点或试剂盒工作范围。
