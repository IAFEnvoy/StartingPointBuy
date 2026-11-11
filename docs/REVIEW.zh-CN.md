# Starting Point Buy 实现审查

> 修复更新（2026-11-10）：下列 7 项已修复，新增 11 个正式回归测试，连同原 10 项共 21/21 通过，`build` 通过。当前实现还加入默认不可提前关闭及打开期间伤害免疫；配置与规则见设计稿 §15。下文保留修复前审查证据，行号对应审查时源码。

审查日期：2026-11-10。范围：当前目录中的实现、设计稿、Saturn 1.2.6 接口、目标版本 NeoForge 源码，以及 fastutil 用法。当前目录没有 Git 元数据，按现有实现审查，未修改正式源码或现有设计稿。

结论：构建与现有 10 个 GameTest 通过，但发现下列 7 个需要修复的问题。原有测试主要覆盖示例和求值的正常路径，未覆盖其中的提交状态、错误诊断及多方案授予问题。

## 1. [P1] 普通玩家可通过 refresh 重开已完成方案并再次领取奖励

位置：`src/main/java/com/iafenvoy/spb/command/SpbCommands.java:85`，尤其第 93～95 行；关联 `runtime/SpbSessions.java:154`、`:360`。

`/spb refresh` 没有权限要求，也不检查当前会话是否已提交或完成，直接改为 `NEEDS_REFRESH`。`open()` 对相同方案先处理刷新分支，绕过后面的 `attachment.isCompleted()`；`rebaseline()` 再将会话改为 `EDITING`，而 `submit()` 不重新检查完成记录。

复现：提交战士预设并完成后执行 `/spb refresh`。保留战士，清空力量和弓术，再选择负债者，可以再次达到 0 点并提交；新 batch ID 和开局面包任务再次生成。这个路径不需要管理员权限。对 COMMITTED/APPLYING/NEEDS_REVIEW 也能调用，可能覆盖原来的待执行工作。

修复方向：refresh 仅接受提交前的合法状态；已提交状态禁止重新变为 EDITING；提交入口额外检查完成/提交记录。清除完成记录应只经过管理员的明确操作。

验证：临时 GameTest 已复现重复提交及新任务生成。

## 2. [P1] 开启后续方案会丢失先前方案的永久能力

位置：`src/main/java/com/iafenvoy/spb/event/SpbServerEvents.java:52`；关联 `runtime/SpbAttachment.java`、`runtime/SpbSessions.java:146`。

能力收集仅从 `attachment.session().commit()` 读取授予。开始新方案会覆盖这个唯一 session，而 `CompletionRecord` 仅保留 batch ID、时间和布尔值，没有先前能力的来源与列表。此后 Saturn 的 `AbilityGrantService.recalculate()` 会把不再贡献的旧来源撤销。

完成方案 A 后开始方案 B，即使 A 的完成记录还在，A 的能力声明也会消失；重登或正常授予重建后实际能力被撤销。

修复方向：将每个已提交/已完成方案的冻结能力来源单独持久化，收集全部有效授予记录；活动草稿与永久授予账本分开。

验证：临时 GameTest 验证了切换前贡献一项能力、切换后贡献为空且原完成记录仍存在；撤销行为已对照 Saturn 的来源重建逻辑确认。

## 3. [P1] 运行期公式错误会成为合法的零消耗

位置：`src/main/java/com/iafenvoy/spb/runtime/SelectionEvaluator.java:187`，尤其第 189～195 行；同类问题还在 `ceil/floor`、`Sink.number()` 和 inputs/initial 捕获中。

Saturn Expression/Sum 遇到异常或非有限结果会记录日志并返回 `0`。SPB 只在调用 `evaluate()` 之后检查 `Double.isFinite()`，无法识别这个回退值。加载阶段的语法与变量校验无法覆盖依赖选择数量的除零或定义域错误。

复现：将弓术点数贡献设置为 `-3 / selected_scholar`，基准点数为 6；选择战士、力量 4、弓术。公式加载校验通过，但运行时 `selected_scholar=0`，弓术变成零消耗，最终点数为 0 且 `canComplete=true`。

修复方向：在影响提交的数值字段使用能保留错误状态的求值机制，不能以吞错后的 0 判断成功；提交和比较规则必须同样处理。相关设计稿已在实现阶段改写成接受这个限制，但限制本身会改变点数和准入规则的实际结果，仍需修复。

验证：临时 GameTest 已复现语法/变量检查通过、除零后零消耗并允许完成。

## 4. [P1] 加载发现的定义错误没有纳入完成校验

位置：`src/main/java/com/iafenvoy/spb/runtime/SelectionEvaluator.java:53`；完成判定在第 116～117 行。

求值器新建空 `issues`，没有合并 `ResolvedPlan.issues()`；提交入口也没有拒绝 `plan.hasErrors()`。因此 schema 不支持、重复 key、阶段变量违规、缺失或被跳过的定义等问题可能只留在解析结果里，不进入界面错误和 `canComplete`。

历史复现：使用非法的数据格式版本可使解析器返回 error，但战士组合的求值结果仍然允许完成；变量白名单错误同样缺少统一的提交屏障。该问题已修复，且当前 Plan 已移除版本字段。

修复方向：将解析错误合并进 EvaluationResult，并在会话初始化/提交处设置不可绕过的定义有效性检查；允许展示诊断，但不允许提交错误定义。

验证：临时 GameTest 已复现 `plan.hasErrors=true`、`result.canComplete=true`。

## 5. [P1] 延后任务的 persist 只更新内存，没有在副作用前保存存档

位置：`src/main/java/com/iafenvoy/spb/runtime/SpbSessions.java:726`、`:733`；调用位置 `runtime/SpbOutcomes.java:86`。

任务设置 RUNNING 后调用的 `persist()` 最终只有 `player.setData(...)`。NeoForge 的实现只是替换附件 Map 中的值并请求同步，不调用玩家文件保存；该附件还没有配置网络同步。现有实现中没有显式的玩家存档写入。

因此“先持久化 RUNNING、崩溃后转 UNCERTAIN”的恢复前提没有成立。在自动存档之间执行任务后崩溃，磁盘上仍可能是 PENDING；若 action 改变的世界数据已经被保存，重启便会重放该副作用。实际风险覆盖世界操作、实体生成和背包满时掉落物等跨存档效果。

修复方向：在副作用前通过真实的持久化边界保存任务状态，并明确保存失败时的处理。仍需保留普通 action 无法保证跨文件 exactly-once 的说明；真实持久化是 RUNNING 恢复协议成立的前提。

验证：已核对当前调用链和目标 NeoForge `AttachmentHolder.setData()` 源码；未通过强杀进程模拟崩溃。

## 6. [P2] 缺失引用的错误提示自身抛异常

位置：`src/main/java/com/iafenvoy/spb/data/PlanIndex.java:219`；同类用法包括第 98 行、`:139`、`:276` 和 `runtime/SelectionEvaluator.java:155`。

`Component.translatable()` 的参数在当前版本只接受 Component、Number、Boolean 或 String；实现直接传入 `Identifier`。缺失 ability/resource、未知项或资源基准缺失时，构造诊断就会抛 `IllegalArgumentException`，无法走设计中的“显示问题并阻止提交”。

方案自动选择和登录恢复会调用解析器，错误数据包可能因此让玩家反复在登录/每 tick 路径遇到异常。

修复方向：所有标识符参数转为字符串或 Component，检查相同 API 的全部调用点。

验证：临时 GameTest 已复现缺失 ability 及缺失基准两条路径的异常。

## 7. [P2] fastutil 预设 Codec 静默丢弃错误数量

位置：`src/main/java/com/iafenvoy/spb/data/Preset.java:26`。

`CollectionCodecs.intMap()` 并非严格解码；其内部 Saturn `TolerantMapCodec` 对解析失败的条目仅记录日志然后丢弃，整体仍然成功。fastutil Map 本身没有问题，问题来自此处选用的 Codec。

复现：`selections` 写成 `{"example:warrior":"not_a_number"}`，Preset 解码成功，但 selections 变成空表。后续校验已看不到错误条目，应用该预设会整体清空玩家选择。

修复方向：用严格 map Codec 校验非负整数，再通过 `xmap` 转为 `Object2IntOpenHashMap`。不要为了直接得到 fastutil 集合而连带采用容错吞项语义。

验证：临时 GameTest 已确认解码结果无 error 且错误项消失。

## fastutil 专项结论

- `SpbSessions.checkRealResources()` 在 `getDouble()` 前先 `containsKey()`，正确区分缺失值和真实的 0；`materialise()` 也检查了存在性。
- selections 的缺失默认 `0` 符合“未选”语义。
- `Map` 类型的装箱 getter 对缺失键返回 `null`，目前求值器的基准缺失检查没有因为底层 fastutil Map 而失效；已运行验证。上面的错误提示抛异常是另一个问题。
- 会话构造函数会复制解码集合，没有发现把不可变 empty map 当作可写容器的当前调用路径。
- 主要的 fastutil 相关正确性问题是第 7 项 Codec 语义。
- 性能改进可在之后进行：SelectionData、数量统计和若干持久化转换使用普通 `Map<..., Integer/Double>` 接口，又创建 LinkedHashMap，仍有装箱。若要优化，优先让数量/余额计算保持 primitive map 类型，使用 primitive getter、entry 与 consumer；`FormulaContext` 的变量 Map 则需遵守 Saturn 接口，无需盲目替换。当前没有基准数据证明这些转换是性能瓶颈，不作为阻断问题。

## 验证和复现材料

正式源码原样执行 `gradlew build` 通过。GameTest 辅助资源任务需要本机缺少的 Java 21，因此使用已缓存资源，执行：

```powershell
$env:JAVA_HOME = 'C:\Users\tanks\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2'
.\gradlew.bat build runGameTestServer -x downloadAssets --no-configuration-cache '-Dorg.gradle.java.installations.auto-download=false' --console=plain
```

正式源码的最终结果：原有 10/10 GameTest 通过，BUILD SUCCESSFUL。

审查复现使用 `build/review-src/` 内的源码副本，以及 `build/review-tests.init.gradle`。副本仅增加 7 个复现测试，并关闭给无协议协商 mock player 发送的客户端展示包；正式源码没有改动。17/17 通过表示原有 10 项通过、另 7 项成功断言现有问题/fastutil 行为，**不表示这些问题已经修复**。

```powershell
.\gradlew.bat runGameTestServer -x downloadAssets --no-configuration-cache '-Dorg.gradle.java.installations.auto-download=false' -I build\review-tests.init.gradle --console=plain
```

复现日志：`build/review-reproductions-final.log`。正式源码最终验证日志：`build/review-original-final.log`。临时复现材料位于 build 下，`clean` 会删除。

最后已用正式源码重新构建并运行原有测试，避免把复现副本的编译输出作为最终成品。此次未启动客户端，GUI 的实际渲染、点击、不同窗口尺寸和 GUI scale 尚未在本次审查中验证。
