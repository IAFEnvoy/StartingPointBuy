# Starting Point Buy 测试模组

`spb_test` 是独立的开发测试模组，源码在 `src/testMod`，依赖 `spb` 和 Saturn。它包含原 `example:default` 示例、19 套新增数据包方案及 161 项 GameTest。正式 jar 不携带这些测试和示例。

## 构建与运行

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runTestClient
```

`build/libs/spb-1.0.0.jar` 用于正式部署；`build/libs/spb-1.0.0-test-mod.jar` 需要与主 jar 一起安装。`build` 会执行打包隔离检查，GameTest 需另外运行。

| Gradle 入口 | 装载 | 运行目录 |
| --- | --- | --- |
| `runClient` / `runServer` | 只有正式模组及依赖 | `run` |
| `runTestClient` | 正式模组、test-mod 及依赖 | `run-test-client` |
| `runTestServer` | 正式模组、test-mod 及依赖 | `run-test-server` |
| `runGameTestServer` | 同上，自动运行 `spb_test:*` | `run-game-tests` |

在本机已有资源缓存而资产下载工具不可用时，可使用：

```powershell
.\gradlew.bat build runGameTestServer -x downloadAssets --no-configuration-cache '-Dorg.gradle.java.installations.auto-download=false' --console=plain
```

## 数据包场景

客户端进入允许命令的测试世界。资源、组和选项不再填写 key，公式使用 `selected_spb_test_sword`、`resource_spb_test_budget` 等自动名称；预设请求使用 `preset_1`、`preset_2`。

原 `example:default` 会首次自动开启，先选一个预设并确认。其余方案均不会自动开启，使用 `/starting_point_buy open spb_test:<下表 ID>`。完成后重试可用 `/starting_point_buy unlock <玩家名> <方案 ID>` 清理会话和完成记录。

| ID | 主要覆盖 | 手动检查/预期 |
| --- | --- | --- |
| `linked` | 三资源、职业单选、折扣、动态组/选项上限、预设 | Sword 与 Sage 预设均可完成；Sword 预设后改选 Sage，保留 train×4，动态上限变 2，无法提交 |
| `quota_total` | 以总份数计数组上下限 | 重复选择会增加组计数，超过上限阻止提交 |
| `quota_distinct` | 以不同种类计数 | 同一项重复购买不增加种类数，与 total 方案对照 |
| `rounding` | NumberProvider 小数数量上限/下限 | 下限向上取整，上限向下取整；边界两侧分别通过/失败 |
| `fractional` | 0.25 quantum、小数预算、恰好为零 | 初始 credit=1.5；quarter×2 + half×2 恰好归零；sliver 的非对齐值不能提交 |
| `visibility` | 前置、不可用项、隐藏组、互斥 | unlock 控制后续可用性；撤销前置后保留失效选择，仍可移除且阻止提交 |
| `reserve` | 允许余额 1～3 | 余额为 1、2、3 可提交，0 和 4 不可提交 |
| `bounds` | 资源物理边界、多资源 | 完成规则之外仍检查各资源 min/max，不能用截断掩盖越界 |
| `formulas` | Sum、Expression params、input、折扣重算 | 切换 coupon 后完整重算 scalable 的价格，预算不足阻止提交 |
| `runtime_errors` | 未选中价格预览、公式恢复 | 未选中的坏价格不阻止其他项；选择 coupon 后 fault 价格恢复正常。异常数值行为遵循 Saturn |
| `tags` | 真实 tag 展开、稳定排序、去重 | 重复 ID/tag 不产生重复组/选项；选项按解析后的稳定顺序显示 |
| `rewards` | Plan/Option abilities、公式 repeat、小数 delay | 第一个预设确认后才授予两路能力，延后发 3 面包和 2 苹果；重复提交不重复发奖 |
| `optional` | 无标题方案、JSON 设置提前关闭 | Esc 请求保存草稿并等待服务端回执；重开保留选择，关闭解除伤害免疫 |
| `readonly` | `commit: discard`、初始化、随机 input、reset | 第一个预设提交后真实 budget=1、readonly=7；草稿 readonly=5 不写回；reset/重开不重抽 input |
| `choice_single` | 按种类限选一种，允许每项重复 | 预设 bead×3 可提交；选择 rune×3 替换 bead，保留数量控件 |
| `choice_total` | 按总份数最多一份 | bead×2 留在草稿但不能确认；选择 rune×1 替换后可确认 |
| `choice_pair` | 按种类最多两种 | bead 和 rune 可同时多份；第三种阻止确认，移除后恢复 |
| `choice_dynamic` | 公式上限与自动替换 | wide_choice 将上限从 1 提升至 2，保留原选择；撤销后保留超限草稿供修正 |
| `choice_zero` | 上限为零 | 不选时可完成，任何选择都会阻止确认 |

Plan 不需要版本字段，`title` 可省略；`optional` 省略 title，打开时不显示顶部标题，其他方案显示各自配置的标题。

除 `optional` 外默认禁止提交前关闭；满足点数规则后仍需点击确认。打开期间服务端忽略伤害，确认或允许关闭后恢复。

## 测试结构

```text
java/com/iafenvoy/spb/testmod/
  StartingPointBuyTest.java 独立模组入口
  SelectionGameTests.java   注册测试及原 10 项示例测试
  RegressionTests.java      14 项审查回归测试
  RefreshTests.java         7 项草稿刷新、冲突恢复、请求校验与指纹稳定性测试
  GeneratedKeyTests.java    4 项自动别名、公式、重排稳定性与预设编号测试
  GroupLimitTests.java      4 项动态组上限、种类/份数独立性、替换及预览测试
  ScreenLayoutTests.java    4 项布局、键盘导航、隐藏选择和独立滚动测试
  CheckpointObserver.java   落盘检查点观察器
  DatapackTests.java        JSON fixture 驱动器与 4 项会话测试
  DatapackLoadTests.java    实际注册表加载：有效对照和 25 项拒绝加载
  FixtureResourceManager.java 内存覆盖坏定义，不修改当前世界的数据
  TestPlayers.java         每次独立创建 NeoForge FakePlayer，关闭默认无敌并标记客户端已加载
resources/
  META-INF/neoforge.mods.toml
  assets/spb/lang/         示例翻译
  data/example/           原完整示例
  data/spb_test/          新方案、共享选项、资源、能力、tag、空测试结构
  fixtures/
    plans.json            registry/preset 合同测试的方案清单
    evaluations.json      62 项选择集与预期结果
    invalid/index.json    25 项无效定义索引
    invalid/*.json        故意无效的原始定义
```

总计 161 项：示例与回归 24 + JSON 求值 62 + 无效定义 25 + registry/preset 合同 1 + 会话生命周期 4 + 实际加载 26 + 界面布局 4 + 自动名称 4 + 刷新恢复 7 + 组数量限制 4。已删除严格公式求值、简写/嵌套表达式额外语法校验、捕获变量校验、provider 白名单和嵌套随机条件限制对应的旧用例；公式解码与求值直接遵循 Saturn。有效定义由 Minecraft 数据包注册表实际加载。无效定义存放在 `fixtures/invalid`；测试时逐个注入独立 ResourceManager，重新加载 Saturn resource/ability 和 SPB 三个注册表。任意坏定义都必须使 Minecraft 的加载 Future 失败，包括未被方案使用的坏组/选项；有效对照必须成功。不会向运行中世界的数据包目录写入坏定义。

会话测试调用服务端公开入口并覆盖 revision、单选替换、reset、草稿资源提交、冻结奖励和重复提交。`regression_reset_flow` 在三个方案上验证 Reset 后草稿回到 baseline、重选结果与首次一致；`regression_unlock_flow` 验证 `unlock` 把 `commit: replace` 资源回滚到会话 baseline 后再重开。测试玩家统一由 `TestPlayers` 创建，每次使用独立 UUID 的 NeoForge `FakePlayer`，关闭其默认无敌状态，不加入在线玩家列表。延后奖励用真实服务器时间；FakePlayer 的空连接不驱动玩家 tick，测试逐 tick 发布真实玩家 Post 事件驱动已注册监听器。

职业切换会话测试还覆盖错误会话 ID、过期 revision 的数量/预设/重置/预览/提交请求，以及单选替换预览和数量越界预览，验证这些请求不改变已保存选择、草稿资源、revision 或冻结奖励。它们经由与实际客户端相同的 `SessionService.handle` 入口执行。

Holder 回归覆盖默认/指定方案入口、忙碌会话保护、指定/全部解锁，以及旧格式附件映射里混入丢失方案和坏记录时仅过滤相应条目；有效完成记录、能力授予和当前会话资源基线仍保留。`open_plan_reference` 同时验证 action 的失效方案引用在 Codec 解码及真实注册表加载时被拒绝。

`ScreenLayoutTests` 仅使用不依赖客户端的布局与滚动类，可在专服运行。覆盖多列/单列导航、滚动后的卡片命中、隐藏已选项的可达性、侧栏/窄屏弹层切换、尺寸限制、左右独立滚动与拖动边界。真实客户端探针日志 `build/refresh-client-smoke.log` 还覆盖鼠标加减、预设快捷键、预览回包匹配、快照更新保留锚点、关闭锁定，以及定义/资源冲突后点击“刷新草稿”、在同一界面修正数量并确认的完整流程；探针位于 `build`，不进入发布 jar。

## 新增用例

1. 在 `resources/data/spb_test/` 添加 Plan、Group、Option 或 Saturn 定义。可以复用已有资源和选项。
2. 将有效 Plan ID 加入 `fixtures/plans.json`。合同测试会检查它及其全部预设能正确解析、预设能够完成。
3. 在 `fixtures/evaluations.json` 添加独立用例，名称不能重复。例如：

```json
{
  "name": "linked_sword_extra_check",
  "plan": "spb_test:linked",
  "selections": {"spb_test:sword": 1, "spb_test:shot": 1, "spb_test:train": 4},
  "can_complete": true,
  "resources": {"spb_test_budget": 0, "spb_test_tokens": 2, "spb_test_power": 5},
  "max_counts": {"train": 4}
}
```

必填 `name`、`plan`、`selections`、`can_complete`。可选预期字段：`resources`、`max_counts`、`visible`、`enabled`、`groups`、`group_visible`、`has_errors`、`abilities`、`tasks`、`order`。`baseline` 可按完整资源 ID 覆盖初始值。选项预期字段使用选项 ID，省略命名空间时默认 `spb_test`；资源与组预期使用完整 ID 自动生成的别名，例如 `spb_test_budget`、`spb_test_role`。`abilities` 是能力 ID 集合，`tasks` 是展开 repeat 后的任务总数，`order` 同时检查 tag 排序和单组去重。

无效 Plan、Group、Option 放在 `fixtures/invalid/<name>.json`，再写入 `invalid/index.json`：

```json
{
  "name": "unknown_variable_extra_check",
  "file": "fixtures/invalid/unknown_variable.json",
  "stage": "resolve",
  "path": "completion_rules",
  "kind": "plan"
}
```

`kind` 为 `plan`、`group` 或 `option`。`stage: decode` 检查 Codec 拒绝 JSON；`stage: resolve` 检查解析/语义错误的路径包含 `path`，Plan 同时验证结果不能提交。`syntax_typed` 保留对 Saturn 显式 `saturn:expression` Codec 语法错误的测试。每个索引项自动注册一项 `invalid/<name>` 和一项 `loading/<name>`；后者无论错误发生在哪个阶段，都要求实际注册表加载失败。

静态 Codec 覆盖 input 名称、quantum、hard_max、重复资源、资源别名冲突、重复 outcome/action 和空规则 key。资源/组/选项/预设已移除手写 key；资源与组/选项别名由完整 ID 生成，预设按位置编号。`resource_alias_collision`、`group_alias_collision`、`option_alias_collision` 分别覆盖三类名称冲突，均要求实际注册表加载失败。相同内容的预设可以重复出现。Holder 的缺失引用也会在注册表冻结阶段失败。完整 Plan 的变量作用域、预设数量、资源绑定、归属关系和引用 tag 在全部注册表冻结后检查，错误直接阻止进入世界。

运行 `runGameTestServer` 验证，再执行 `build` 检查打包隔离。多人实机重连、极端 GUI scale 和所有第三方 action 的行为仍需相应环境单独验证。
