# 当前状态与本地 patch 台账

> 本文件是**状态**，随时更新；[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) 是**计划**，相对稳定。
>
> 上游：`fcitx5-android/fcitx5-android`（LGPL-2.1）。本仓库是它的**上游优先薄 fork**。

---

## 1. 分支结构

按计划 §4 的加法纪律，**可上游的改动与本地专属改动分在不同 topic 分支**，都从 `master` 拉出。混在一条 commit 序列里会导致以后无法把可上游部分单独摘出来提 PR。

| 分支 | 内容 | 性质 | 基线 |
|---|---|---|---|
| `master` | 保持等同上游，不放任何我们的改动 | 镜像 | `upstream/master` |
| `vrime` | 产品集成分支，合并各 topic 分支 | 本地 | — |
| `feat/vr-defaults` | 横屏键盘尺寸 + 工具栏默认值 | **本地专属**，不可上游 | `c3edd3f` |
| `feat/t9-keyboard` | 九键布局 + `use_t9_layout` 开关 | **可上游** | `c3edd3f` |
| `docs/wrap-up` | 本文件与计划更新 | 本地 | `vrime` |

跟随上游：

```sh
git fetch upstream
git checkout master && git merge --ff-only upstream/master && git push origin master
git checkout feat/xxx && git rebase master      # 逐条 topic 分支
git checkout vrime && git rebase master && git merge feat/vr-defaults feat/t9-keyboard
```

合起来测：

```sh
git checkout vrime && git merge feat/vr-defaults feat/t9-keyboard
```

---

## 2. 本地 patch 台账

维护纪律：**每次跟随上游前，先检查本地 patch 能否再上游化一批。**

### 可上游（应尽快提 PR，减少本地负担）

| 改动 | 文件 | 说明 |
|---|---|---|
| 九键 T9 布局 | `input/keyboard/T9Keyboard.kt`（新增） | 纯新增文件。手机用户同样是刚需，最有希望被接纳 |
| 注册 + `use_t9_layout` 开关 | `KeyboardWindow.kt`、`AppPrefs.kt`、`values/strings.xml` | 单行级接入；进一步可提议把 `keyboards` map 做成可外部注册的扩展缝，接了之后连这一行都不用改 |
| `lastSymbolType` 修正 | `KeyboardWindow.kt` | T9 是文本布局而非符号布局，不应成为符号键的返回目标。属 bug 修复，独立可上游 |

### 本地专属（不可上游）

| 改动 | 文件 | 为什么不可上游 |
|---|---|---|
| 横屏键盘高度 49% → 85% | `AppPrefs.kt` | Quest 是固定横屏面板；85% 对手机横屏是荒谬的 |
| 横屏两侧边距 0 → 6dp | `AppPrefs.kt` | 同上 |
| 横屏底部边距 0 → 5dp | `AppPrefs.kt` | 同上 |
| 工具栏默认展开 false → true | `AppPrefs.kt` | Quest 面板宽、额外高度不心疼，而射线/手势多一次点击代价高 |

**注意**：本地专属改动全部集中在 `AppPrefs.kt` 的默认值字面量上，每次上游改动该文件都可能冲突。冲突时只需重新应用这四个数值，不要把上游的其它变更改掉。

---

## 3. 已完成

- [x] 选型与调研（结论见计划 §2，含为什么不是 Trime）
- [x] fork 建立，`vrime-android` 可构建
- [x] 基座 APK 在 Quest 3 上跑通
- [x] 九键布局代码完成（未在真机验证输入行为）
- [x] VR 尺寸默认值真机实测并落地

## 4. 待办（按建议顺序）

1. **继续实测其余 UI 默认值** —— 候选栏字号、候选项内边距、长按延迟、按键弹出预览。纯数值、零风险，且直接决定"装上就好用"
2. **Phase 4：接 rime 插件** —— 需要先解决 CMake 3.24+（见计划 Phase 1 的构建实况）。这是让九键真正出中文的前提
3. **用 librime 1.12.0 重编 VRIME 的 schema** —— 现有 `.bin` 是 1.8.5 编的，版本不匹配会触发设备端重新编译词典
4. **T9 自动切换** —— 待 rime 插件上真机、能打印 `InputMethodEntry` 实际内容后再实现
5. **Phase 5：侧载与启用指引** —— 只走 sideload，指引质量等于产品质量

---

## 5. 踩过的坑（避免重复）

**CMake 版本**：`Versions.kt` 要 3.31.6，SDK 常见只带 3.18.1 / 3.22.1。只构建 `:app` 时可用 `-PcmakeVersion=3.22.1` 绕过（唯一需要 3.24+ 的是 `plugin/rime` 的 `$<LINK_LIBRARY:WHOLE_ARCHIVE,...>`），但装 rime 插件时必须补上 3.31.6。**覆盖值写 `~/.gradle/gradle.properties`，不要改 `Versions.kt`。**

**IME ID 不要手拼**：debug 构建的 `applicationIdSuffix = ".debug"` 只作用于 applicationId，服务类名不变。用 `adb shell ime list -a -s` 读。

**改默认值在旧设备上看不到效果**：`ManagedPreference.getValue()` 是 `sharedPreferences.getInt(key, defaultValue)`，默认值只是回退、不写入存储。手动调过的设置会永久盖过新默认值。验证全新安装体验要 `adb shell pm clear <applicationId>`（会连 fcitx 数据目录一起清掉，重走首次部署）。

**九键现在不会出中文，这是预期**：基座内置的是 `libime` 拼音，期望字母输入、不认数字。T9 按下去发的是数字 `2`–`9`，所以内置拼音只会把它们当数字上屏。要出中文必须先接 rime 插件 + 九键方案。

**Quest 上那条黑色栏不是我们的**：平面键盘上方的是 Meta 自己的 keyboard assistant bar（VrShell 内 `KeyboardAssistant.java` / `KeyboardTypeahead.java`），无第三方接入点。候选栏必须我们自己画。

---

## 6. 未决问题

| 问题 | 影响 | 如何解决 |
|---|---|---|
| RIME 方案如何暴露成 fcitx 输入法条目？ | 决定 T9 能否自动切换 | 装 rime 插件后在真机打印 `InputMethodEntry` |
| Surface keyboard 在 Android 输入栈里的身份？ | 决定候选条模式能否自动切换（`FloatingCandidatesMode.InputDevice`） | 真机对比 Surface keyboard 开/关时的 `Configuration.keyboard`、`InputDevice` 列表、`KeyEvent.source` |
| `.bin` 跨架构可移植性 | 决定 schema 能否在 CI 交叉编译后分发 | 用 librime 1.12.0 交叉编译到 arm64-v8a 后真机验证 |
| 上游是否接受 T9 布局 PR | 决定它留在本地还是回归上游 | 提 PR 沟通 |
| 工具栏与候选栏互斥的交互 | `KawaiiBarComponent` 里两者互斥，展开工具栏时候选栏让位。接上 rime 出中文候选后，这个交互在 VR 里是否还合适需重看 | 真机体验后决定 |
