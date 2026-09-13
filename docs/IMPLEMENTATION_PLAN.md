# VRIME Android 实施计划

> 面向 Meta Quest 3 的原生中文输入法，基于 fcitx5-android 的上游优先薄 fork。
>
> 本文档中标注 ✅ 的事实均已在本仓库源码中核实（附文件路径）；标注 ⚠️ 的为待核实项。
>
> **当前进展、分支清单与本地 patch 台账见 [STATUS.md](STATUS.md)。** 本文件是计划，STATUS 是状态：计划相对稳定，状态随时更新。

---

## 1. 项目定位

VRIME 目前是浏览器内的 PWA（Vue3 + librime/WASM），只能在浏览器里输入、再复制粘贴到别处。本项目做原生 Android 输入法，突破这个上限。

Quest 3 的 **Surface keyboard**（平面/桌面投影键盘，v85 引入）把 QWERTY 投影到桌面用手部追踪盲打，此模式下输入法只需提供候选栏。但它并非常开，关闭时仍需完整软键盘 —— 因此**两种形态都要**。

### 核心约束

| # | 约束 | 影响 |
|---|---|---|
| 1 | **社区广度优先，不只做中文，不单打独斗** | 选型第一权重 → fcitx5-android |
| 2 | **软键盘界面必须保留** | 不做剥离，双模式并存 |
| 3 | **不上 Horizon Store，只走 sideload** | 消除商店政策与 GPL 张力；上手指引成为一等公民交付物 |
| 4 | **Trime / fcitx5-android 已在 Quest 3 实测，与 Surface keyboard 共存** | 平台可行性已由真机确认 |
| 5 | 扩展语种：粤语 / 繁体注音 / 越南语 | 三者上游均有现成插件 |
| 6 | 上游关系：**上游优先的薄 fork** | 加法式开发纪律（见 §4） |
| 7 | 软键盘布局：QWERTY + 九键 T9，可手动切换 | T9 建议作为 VR 默认 |

### 一个必须纠正的前提

Quest 平面键盘上方那条黑色栏**不是输入法应用**，是 Meta 自己的 keyboard assistant bar（VrShell 内 `com/oculus/panelapp/keyboardv2/assistant/KeyboardAssistant.java`、`KeyboardTypeahead.java`，联想来自 `com.oculus.assistant.api.TypeAheadListener`）。它**没有第三方接入点**，Surface Keyboard 至今无开发者 API。

**候选栏必须由我们自己绘制。** 这不影响可行性（约束 4 已确认共存），只是明确了工作边界。

---

## 2. 基座选型：fcitx5-android

**上游：** https://github.com/fcitx5-android/fcitx5-android — Kotlin + C++，LGPL-2.1

**本仓库：** `zhou9110/vrime-android`（fork），当前基线 `a677fd1`

### 为什么不是 Trime

调研中曾一度推荐 Trime，论据是「bar-only 让 fcitx5 的 addon 架构失去价值，插件 APK 在 Quest 上是负资产」。约束 1–3 打掉了这个论据的全部前提：

| 原论据 | 失效原因 |
|---|---|
| 只要候选栏，不要软键盘 | 软键盘要保留 → addon 架构的按语种 UI 重新有价值 |
| N 个插件 APK 不可接受 | 只走 sideload，多装一个插件的边际成本远低于放弃整个生态 |
| Trime 物理键盘模式更成熟 | 那份代码本就是 fcitx5-android 的移植；双模式是它的原生设计 |

叠加约束 1：fcitx5-android 属上游 fcitx 生态，越南语/日语/韩语/粤语/泰语/僧伽罗语各有现成维护者；Trime 实质是中文单语社区。

### 三个目标语种全部已有现成插件 ✅

`plugin/` 目录实测内容：`anthy`、`chewing`、`clipboard-filter`、`hangul`、`jyutping`、`rime`、`sayura`、`thai`、`unikey`

| 语种 | 方案 | 状态 |
|---|---|---|
| 中文拼音/双拼/五笔/仓颉/码表 | `fcitx5-chinese-addons` + `libime` | **内置于基座 APK**（`lib/` 下的模块） |
| 中文 RIME 方案（含 VRIME 九键） | `plugin/rime` | 现成，带 rime-prelude/essay/luna-pinyin/stroke |
| **粤语** | `plugin/jyutping`（libime-jyutping）或 RIME 的 rime-cantonese | 现成，两条路 |
| **繁体注音** | `plugin/chewing`（libchewing）或 RIME 的 bopomofo | 现成，两条路 |
| **越南语** | `plugin/unikey`（fcitx5-unikey，Telex/VNI/VIQR） | 现成 |

**越南语工作量因此降为零代码。** 其输入范式是声调符号原地重写（`Tieengs Vieet` → `Tiếng Việt`），几乎不需要候选列表，而候选栏的可见性判据本来就正确处理候选为空的情况。

**明确排除：不要用 RIME schema 做越南语。** 现有 schema 已废弃（`gkovacs/rime-vietnamese`：12★、7 commits、无 license），且范式错误 —— RIME 是「码 → 候选列表」引擎，Telex 是有状态原地重写。硬凑会导致：本该无候选处冒出菜单、词典外字符串（人名/外文/代码）行为错误、声调可置于音节任意位置无法处理。演示能过，日用不能。韩语同理，走 `plugin/hangul`（libhangul）。

---

## 3. 已核实的事实基线

### 3.1 工具链 ✅

`build-logic/convention/src/main/kotlin/Versions.kt`：

```kotlin
val java = JavaVersion.VERSION_11
const val compileSdk = 36
const val minSdk = 23
const val targetSdk = 36
const val defaultCMake = "3.31.6"
const val defaultNDK = "28.0.13004108"
const val defaultBuildTools = "36.1.0"
const val baseVersionCode = 11
const val baseVersionName = "0.1.3"
val supportedABIs = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
const val fallbackABI = "arm64-v8a"
```

> ⚠️ 注意：早期调研资料里的 NDK 25 / CMake 3.22.1 / SDK 35 **已过时**，以上为仓库实际值。

**单 ABI 构建**：属性名是 `buildABI`（`ProjectExtensions.kt:60` → `epn("BUILD_ABI", "buildABI")`），即环境变量 `BUILD_ABI` 或 Gradle 属性 `buildABI`。Quest 3 只需 `arm64-v8a`。ABI splits 在 `NativeBaseConventionPlugin.kt:44`。

**应用标识**：`app/build.gradle.kts:13,16` 均为 `org.fcitx.fcitx5.android`。

**宿主依赖**：`extra-cmake-modules`、GNU gettext（仓库提供 `flake.nix` / `shell.nix`）。

### 3.2 librime 版本 = 1.12.0 ✅

`plugin/rime/src/main/cpp/CMakeLists.txt`：`set(Rime_VERSION "1.12.0")`

**librime 不是 submodule**，来自 `lib/fcitx5/src/main/cpp/prebuilt`（submodule，pin 于 `86ce2c9`，指向 https://github.com/fcitx5-android/prebuilt ），提供逐 ABI 的 `librime.a` 及依赖（glog / leveldb / lua / marisa / opencc / yaml-cpp）。

静态链接的自注册模块用 `set(RIME_TARGET $<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>)` 保住。

**🔴 关键差异**：VRIME 现在把 `rime_version` 钉在 **1.8.5**（`librime_patch` 注释「keep 1.8.5 so no need to rehash schemas」）。**现有 `.bin` 不能直接复用**，必须按 1.12.0 重编，否则会被判定过期而触发设备端重新编译词典。

**schema 数据安装位置**：同一 CMakeLists 里 `install(FILES ... DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data" COMPONENT prebuilt-assets)` —— 加 VRIME 的 T9 schema 就是加文件 + 加一条 install 规则。

### 3.3 UI 层规模与接入点 ✅

`app/src/main/java/org/fcitx/fcitx5/android/input/`：**116 个 Kotlin 文件，13934 行**。我们要碰的是极小一部分。

**键盘注册点** —— `input/keyboard/KeyboardWindow.kt`：

```kotlin
private val keyboards: HashMap<String, BaseKeyboard> by lazy {
    hashMapOf(
        TextKeyboard.Name to TextKeyboard(context, theme),
        NumberKeyboard.Name to NumberKeyboard(context, theme)
    )
}
```

加九键 = **新增一个文件 + 这个 map 加一行**。切换由 `KeyAction.LayoutSwitchAction` → `switchLayout(to: String)` 驱动。

**键盘子类的成本**：

| 文件 | 行数 | 作用 |
|---|---|---|
| `BaseKeyboard.kt` | 504 | 手势、长按弹出、按键分发 —— 全部由基类承担 |
| `TextKeyboard.kt` | 260 | QWERTY |
| `NumberKeyboard.kt` | **70** | 数字小键盘 —— **T9 的直接模板** |
| `KeyDefPreset.kt` | 331 | 按键定义预设，含 `NumPadKey` |

子类只需向构造函数传 `List<List<KeyDef>>` 布局，外加最多 5 个可选覆盖：`onAttach()`、`onReturnDrawableUpdate()`、`onPunctuationUpdate()`、`onInputMethodUpdate()`、`onDetach()`。

**🎯 关键契合**：`NumberKeyboard.kt` 里 `NumPadKey` 发的是 **X11 Numpad keysym** `0xffb0`–`0xffb9`（= `XK_KP_0`–`XK_KP_9`），而 VRIME 的 `xiaobai_simp` 方案恰好需要 Numpad keycode。映射天然对齐。

### 3.4 双模式已是默认行为 ✅

`input/InputDeviceManager.kt` + `input/candidates/floating/FloatingCandidatesMode.kt`：

```kotlin
enum class FloatingCandidatesMode(override val stringRes: Int) : ManagedPreferenceEnum {
    SystemDefault(R.string.system_default),
    InputDevice(R.string.follow_input_device),
    Disabled(R.string.disabled)
}
```

`data/prefs/AppPrefs.kt:285` 默认值为 **`FloatingCandidatesMode.InputDevice`** —— 即「跟随输入设备」：插物理键盘自动切候选条模式，拔掉回软键盘。

> 上游比 Trime 少一个 `AlwaysShow`（强制只出候选条）。仅当 Phase 0 查出 Surface keyboard **不**注册为 input device 时才需要补，那是几行的枚举分支，天然适合上游化。

### 3.5 VR 尺寸：现成偏好项可能就够 ✅

`data/prefs/AppPrefs.kt` 实测已有：

| 偏好项 | 键名 | 默认 | 范围 |
|---|---|---|---|
| 键盘高度百分比 | `keyboard_height_percent`（+ `_landscape`） | — | PInt |
| 高度基准 | `keyboard_height_percent_base` | `DisplayMetrics` | 枚举 |
| **候选栏字号** | `candidates_window_font_size` | 20 | **4–64 sp** |
| 候选栏内边距 | `candidates_window_padding` | 4 | 0–32 dp |
| **候选项内边距** | `candidates_item_padding_vertical` / `_horizontal` | 2 / 4 | **0–64 dp** |
| 候选栏最小宽度 | `candidates_window_min_width` | 0 | 0–640 dp |
| 候选栏圆角 | `candidates_window_radius` | 0 | 0–48 dp |
| 候选栏方向 | `candidates_window_orientation` | `Automatic` | 枚举 |

字号能开到 64sp、内边距能开到 64dp —— **VR 可读性大概率只是一份默认配置 profile 的事，不需要改代码。**

### 3.6 主题系统

`data/theme/`：`Theme.kt`、`ThemeManager.kt`、`ThemePreset.kt`、`CustomThemeSerializer.kt`、`ThemePrefs.kt`、`ThemeMonet.kt`、`ThemeFilesManager.kt` —— 自定义主题是可序列化数据，VR 主题作为新增预设即可。

---

## 4. 架构决策：加法式开发纪律

### 为什么不能「在上面套一层」

Android IME **做不到真正分层**：`InputMethodService` 必须在同一个 APK 内，fcitx5-android 的插件机制只挂引擎 addon，没有 UI 插件点。所以「外面包一层」在技术上不成立。

### 替代方案：fork 内部的加法纪律

1. **所有 VRIME 专属内容以新文件形式存在** —— T9Keyboard、VR 主题预设、默认配置 profile、schema 数据
2. **对上游现有文件只做单行级接入**，每个接入点单独一个 commit
3. **把接入点本身作为扩展缝提给上游** —— 例如让 `keyboards` map 支持外部注册。上游接了之后连那一行都不用改，本地 patch 变成纯新增

效果等同于独立层，但冲突面几乎为零、rebase 成本接近零，且更容易被上游接纳。

### 上游 / 本地划分

**提上游 PR（对手机用户同样有价值）**

- [ ] 九键 T9 键盘布局 —— 手机上同样是刚需，最有希望被接纳
- [ ] 候选栏位置/尺寸的进一步可配置化（若现有偏好项不足）
- [ ] 大目标命中优化（更大热区、内边距与视觉边界解耦）—— 对无障碍与平板同样有益
- [ ] 物理键盘模式切换的健壮性修复（Phase 0/2 中发现的时序问题）
- [ ] `FloatingCandidatesMode.AlwaysShow`（若 Phase 0 证明需要）
- [ ] `keyboards` map 的外部注册缝
- [ ] `SHOW_FORCED` 废弃后的降级路径

**留本地（尽量小）**

- [ ] VR 主题预设（理想情况纯数据，零代码）
- [ ] Quest 默认配置 profile（候选栏位置、字号、页大小、选词标签）
- [ ] VRIME 九键 schema 数据包（`yuyan_t9_pinyin`、`xiaobai_simp`）
- [ ] 侧载与启用指引（含 adb 脚本 / 应用内引导页）
- [ ] 品牌（applicationId、应用名、图标）与发布物料

**维护纪律**：本地 patch 保持可 rebase 的独立 commit 序列，不与上游代码交织。每次跟随上游前，先检查本地 patch 能否再上游化一批。

---

## 5. VRIME 现有资产的复用边界

**不需要移植 `wasm/api.cpp`** —— 它只有 208 行、8 个导出函数，而 fcitx5-android 的 native 层（`app/src/main/cpp/` 下 `native-lib.cpp` + `androidfrontend`/`androidkeyboard`/`androidnotification`/`androidaddonloader`）加 `plugin/rime` 已是更完整的绑定。

### 要带过来的

- [ ] `schemas.json` + `schema-name.json` / `schema-files.json` / `schema-target.json` / `dependency-map.json` / `target-files.json` —— 平台中立的 schema 构建与清单系统
- [ ] `scripts/install_schemas.ts` 的编译流程（产出 `.table.bin` / `.prism.bin` / `.reverse.bin`），**编译目标改为 librime 1.12.0**
- [ ] 两个九键 schema 的 `speller/algebra`：
  - `yuyan_t9_pinyin` —— 约 250 条手写 `derive` 规则，dict 复用 `luna_pinyin`，`page_size: 50`
  - `xiaobai_simp` —— 自带 35 MB `xiaobai` 词库，非标准数字映射（`a→8, p→1, t→2, w→3`），`page_size: 9`
- [ ] `src/utils/t9Pinyin.ts` → Kotlin（它本就是从 `gurecn/yuyansdk` 的 `T9PinYinUtils.kt` 移植来的，属回退移植）
- [ ] 键盘布局定义（`SimpleKeyboard.vue` / `T9Keyboard.vue` 的行字符串可直接转录）

### 丢弃

IDBFS / `syncfs` 持久化层、`EM_ASM(_deployStatus…)` upcall、`leveldb_patch`（单线程 hack）、`ENABLE_THREADING=OFF`、全部 `.vue` + simple-keyboard + Naive UI、`MainView.vue` 里的合成 `KeyboardEvent` 适配器（现在是「按钮 token → 合成 DOM 事件 → X11 keysym 字符串 → `simulate_key_sequence`」三层有损转换，原生侧直接用 keycode + mask）、Android-Chromium workaround、PWA 配置。

### 不要带过去的已知缺陷

`T9Keyboard.vue` 的 `onPinyinClick` 靠 `setTimeout(50ms)` 等 `{esc}` 处理完再逐字符重放拼音（注释「Small delay to ensure Escape is processed first」）—— 竞态设计。原生侧应改为顺序等待引擎响应。

### 数据层面的硬问题

**词库体积**：`public/ime/` 共 186 MB，其中 `yuyan_t9_pinyin.table.bin` **67.9 MB**、`xiaobai.table.bin` **35.2 MB**。librime 用 mmap 读取 `.table.bin`，必须解压成真实文件，不能留在压缩 assets 里。**首次运行按需下载是唯一可行路径。**

**`.bin` 跨架构可移植性未定论** ⚠️：VRIME 现在是 x86-64 native 编译、wasm32 消费，经验上支持小端跨架构；但 librime CHANGELOG 明确处理过 ARM 对齐（v1.2「新二进制格式」、v1.2.10「no conditional compilation on arm」），且文件是 mmap 的（见 [librime#323](https://github.com/rime/librime/issues/323)）。**安全做法：用与设备端相同的 librime 1.12.0 交叉编译到 arm64-v8a，在 CI 的模拟器或真机上跑一次生成 `.bin`，分发那批产物。**

---

## 6. 实施阶段

### Phase 0 — 事实核查（半天）

平台可行性已由约束 4 确认。源码层面的两项已在 §3 完成 ✅。剩下**必须上头显**的两项：

- [ ] **Surface keyboard 在 Android 输入栈里的身份** —— 决定模式切换是自动还是手动
  - 开启/关闭 Surface keyboard 两态下分别读取 `Configuration.keyboard`（是否 `!= KEYBOARD_NOKEYS`）、`Configuration.hardKeyboardHidden`
  - 枚举 `InputDevice.getDeviceIds()` 看是否出现新设备
  - 对收到的 `KeyEvent` 打印 `event.device`、`event.deviceId`、`event.source`
  - → 表现为硬件键盘：`FloatingCandidatesMode.InputDevice` 自动生效，**零工作量**
  - → 否则：需要手动切换入口（候选栏按钮 / 手势）+ 新增 `AlwaysShow` 枚举
- [ ] **`Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD` 的值**，以及 `requestShowSelf(SHOW_FORCED)` 是否仍生效（API 33 起废弃且被逐步削弱）

同时固化可复现记录：

- [ ] `adb shell ime list -a` 输出与我们的 IME ID
- [ ] 场景矩阵：Home space 2D panel / Quest Browser / 沉浸式应用 × Surface keyboard 开关

### Phase 1 — 构建打通

- [x] 加 upstream remote：`git remote add upstream https://github.com/fcitx5-android/fcitx5-android.git`
- [x] `git submodule update --init --recursive`
- [x] 装齐宿主依赖（`extra-cmake-modules` + gettext，或直接用仓库的 `nix develop` / `nix-shell`）
- [x] 单 ABI 构建，基座 APK 装到 Quest 并起得来
- [ ] 装 `plugin/rime`，确认 RIME 引擎可用（列出 schema、能输入）
- [ ] 装 `plugin/unikey`，确认越南语可用 —— **这一步就验证了多语种路线，零代码**

**构建实况（已踩过的坑）**

`Versions.kt` 要求 **CMake 3.31.6**，而 Android SDK 常见只带 3.18.1 / 3.22.1，配置阶段直接失败（`[CXX1300]`）。两条路：

1. **正解**：`sdkmanager "cmake;3.31.6"`
2. **临时可用**：全部 CMakeLists 都只声明 `cmake_minimum_required(VERSION 3.18)`，**唯一**需要更高版本的是 `plugin/rime/src/main/cpp/CMakeLists.txt:38` 的 `$<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>`（要 CMake **3.24+**）。而 `:app` 只依赖 `:lib:*` 与 `:codegen`、**不依赖任何 plugin 模块**，所以只构建基座时可以降级：
   ```sh
   ./gradlew :app:assembleDebug -PbuildABI=arm64-v8a -PcmakeVersion=3.22.1
   ```
   **这条路到装 rime 插件时失效**，届时 3.24+ 绕不过去。

**覆盖值放机器级配置，不要改 `Versions.kt`** —— 后者会变成每次 rebase 都冲突的本地 patch。这些值本就支持外部覆盖（`ProjectExtensions.kt` 读 Gradle 属性或环境变量），写进 `~/.gradle/gradle.properties`：

```properties
cmakeVersion=3.22.1
buildABI=arm64-v8a
```

**安装与启用**

产物在 `app/build/outputs/apk/debug/`，ABI split 开启且无 universal 包。debug 构建带 `applicationIdSuffix = ".debug"`，可与官方 fcitx5-android 共存安装。

**不要手拼 IME ID**（debug 后缀只加在 applicationId 上，服务类名不变），从设备读：

```sh
adb shell ime list -a -s
adb shell ime disable com.oculus.vrshell/com.oculus.panelapp.keyboardv2.KeyboardInputMethodService
adb shell ime enable  <读到的完整 ID>
adb shell ime set     <读到的完整 ID>
```

**验证默认值必须先清数据**：`ManagedPreference.getValue()` 是 `sharedPreferences.getInt(key, defaultValue)`，默认值只是回退、不写入存储。手动调过的设置会永久盖过新默认值，所以验证全新安装体验要 `adb shell pm clear <applicationId>`。

### Phase 2 — 九键布局（推荐的起点）

纯新增，不依赖真机结论，写完即可编译验证。

- [x] 新建 `input/keyboard/T9Keyboard.kt`，以 `NumberKeyboard.kt`（70 行）为模板
  - 实际做成 **4×4** 网格（第四列是退格 / 重输 / `ABC` / 回车），底排为 `!?#` / 空格 / `123` / 回车
  - 三个 T9 专属按键定义 **file-private**，`KeyDefPreset.kt` 一行未动
  - 空格复用上游 `SpaceKey` 类型（`BaseKeyboard` 按 `is SpaceKey` 挂滑动移光标与音效），其 `percentWidth = 0f` 恰好填满第四列
- [x] `KeyboardWindow.kt` 的 `keyboards` map 加一行（单独 commit）
- [x] 顺手修掉 `switchLayout` 把 T9 记入 `lastSymbolType` 的潜在 bug（T9 是文本布局，不是符号布局）
- [ ] 移植 `t9Pinyin.ts` → `T9PinyinUtils.kt`（用于拼音消歧候选）
- [ ] 补齐 VRIME 侧缺失的：符号面板（Web 版是 `disabled` 状态）、长按 / 多次点击
- [ ] 修掉 `onPinyinClick` 的 `setTimeout` 竞态，改为顺序等待引擎响应
- [ ] 编译验证 + 装到 Quest 手动验证

**按键映射的分歧**：标准九键（`yuyan_t9_pinyin`）发**普通数字** `FcitxKey_1`–`9`，`1` 位无字母故复用为音节分隔符 `'`；而 `xiaobai_simp` 数字重排（7/8/9 在上排）且发 **Numpad** keysym `FcitxKey_KP_0 + n`。当前实现只做了标准映射，xiaobai 变体待加。

**入口暂用偏好开关**：理想行为是「选中九键方案自动切九键」，钩子是 `KeyboardWindow.onImeUpdate(ime: InputMethodEntry)`。但这取决于 **RIME 方案如何暴露成 fcitx 输入法条目** —— fcitx5-rime 可能只暴露单个 `rime` 条目而把方案切换留在 rime 内部。未验证，不应靠猜实现，故先用 `use_t9_layout` 开关，待 rime 插件上真机后再补。

### Phase 3 — VR 适配

- [x] **先只调偏好项** —— 结论：现成旋钮足够，**尺寸适配零代码**，只是改默认值
- [x] 真机实测并沉淀为默认值（横屏，见 `feat/vr-defaults`）：键盘高度 49% → **85%**、两侧边距 0 → **6dp**、底部边距 0 → **5dp**、工具栏默认展开 false → **true**
- [ ] 继续实测其余默认值：候选栏字号（20sp，可开到 64）、候选项内边距（2/4dp，可开到 64）、长按延迟（300ms，范围 100–700）、按键弹出预览（VR 无触觉反馈，价值高于手机）
- [ ] VR 主题预设（新增数据）
- [ ] 候选栏位置：**固定角落，不要用跟随光标** —— 依赖目标应用实现 `CursorAnchorInfo`，Quest 上「应用」可能是合成器里的 2D panel，上报完全未经测试
- [ ] 射线点击适配：Quest 的「触摸」是控制器射线或手部捏合投递的 touch 事件。验证命中率，必要时加大热区（此改动上游化）
- [ ] 模式切换：按 Phase 0 结论走自动或补手动入口。切换必须在 Surface keyboard 开关瞬间生效，不残留旧模式视图
- [ ] 软键盘布局默认值：**建议 T9 为 VR 默认**（射线点击命中率高），QWERTY 可切换
- [ ] **不要引入 Compose** —— 上游与 Trime 都刻意避开（`InputMethodService` 窗口内的重组延迟与 window-token/lifecycle 摩擦）

### Phase 4 — 词库与部署

- [ ] 用 librime 1.12.0 重新编译全部 schema，产出新 `.bin`
- [ ] 两个 T9 schema 接入 `plugin/rime` 数据目录 + install 规则
- [ ] 按需下载：首启只下当前 schema 及其 `dependency-map.json` 传递依赖，md5 校验（复用 `target-files.json`）
- [ ] 设 librime `RimeTraits.prebuilt_data_dir`（上游未设），从 assets / 下载目录填充预编译产物，**避免设备端首次编译词典** —— Quest 上比手机更慢，且候选栏没有好的进度 UI
- [ ] 验证 `.bin` 在 arm64-v8a 上加载正常且不触发重编

### Phase 5 — 侧载与启用体验（一等公民）

只走 sideload，**指引质量 = 产品质量**。

- [ ] 品牌化：applicationId、应用名、图标（注意不要与上游 `org.fcitx.fcitx5.android` 冲突，否则无法共存安装）
- [ ] 启用路径文档化：
  ```sh
  adb shell ime list -a
  adb shell ime disable com.oculus.vrshell/com.oculus.panelapp.keyboardv2.KeyboardInputMethodService
  adb shell ime enable  <our-ime-id>
  adb shell ime set     <our-ime-id>
  ```
- [ ] 无 PC 路径实测并选定一条推荐方案：第三方启动器（Lightning Launcher / Microsoft Launcher）、Quest Game Optimizer、或 `adb shell am start -n com.android.settings/...` 进 AOSP 设置页
  - Horizon OS 的 `Settings > 键盘` **没有 Android 输入法选择器**，必须先关掉 Meta 的输入法
- [ ] 测已知怪癖：Gboard 需「启用 → 关闭 → 再启用」才生效，我们的包是否也有
- [ ] 交付物：图文/视频指引 + adb 一键脚本 + SideQuest 上架
- [ ] 应用内引导页：检测自身是否已启用，未启用则给出对应固件的操作步骤
- [ ] **验收标准**：在一台未做任何配置的 Quest 3 上，只照文档操作（不看代码、不即兴发挥），从零走到能输入中文

### Phase 6 — 社区化

「不单打独斗」不会自动发生。

- [ ] 按 §4 清单逐项提上游 PR，从最小最独立的开始
- [ ] 在 fcitx5-android 开一个 VR/Quest 支持的 tracking issue，公开设计与真机数据
- [ ] README 写清「哪些改动在上游、哪些在本地、为什么」
- [ ] 保留 F-Droid 兼容性（不引入闭源依赖），为未来上架留门

### Phase 7（可选）— 沉浸式应用支持

有第三方 IME 在沉浸式 VR 应用内不被合成显示的报告（Meta 商店某付费中文输入 app 页面写明「third-party keyboards cannot pop up inside other games」）。若成为需求：

- [ ] 参照 `mvpdz1/369VRshurufa`：保留真 `InputMethodService` 拿 `InputConnection`，但把 UI 画到 `SYSTEM_ALERT_WINDOW` 悬浮窗（其 manifest 注释：「悬浮窗口权限，Quest系统必需」）
- 只走 sideload 时该权限无商店障碍

---

## 7. 风险登记

| 风险 | 严重度 | 处置 |
|---|---|---|
| Surface keyboard 不表现为硬件键盘 → 模式切换无法自动 | 🟠 高 | Phase 0。退路是手动切换入口 + `AlwaysShow` 枚举，可用但体验降级 |
| 第三方 IME 权限被平台收回 | 🟠 高 | 有来源称此能力曾损坏、v85 恢复（未证实）。非契约稳定能力，无法缓解，保留 Web 版作为退路 |
| 启用路径门槛（无 IME 选择器） | 🟠 高 | Phase 5 是一等公民交付物，不是文档附录 |
| `SHOW_FORCED` 已废弃且被削弱 | 🟡 中 | 降级路径：候选栏常驻 `VISIBLE`（内容为空），不依赖按需唤起。适合上游化 |
| 上游不接受某些 PR（尤其 T9 布局） | 🟡 中 | 接受留在本地，但保证是独立可 rebase 的 commit |
| `.bin` 跨架构不兼容 / 版本错配 | 🟡 中 | 用 librime 1.12.0 交叉编译 + CI 真机/模拟器验证 |
| 词库 68 MB 单文件 | 🟡 中 | 首启按需下载 + md5 校验 |
| 插件 ABI 与宿主锁步 | 🟡 中 | 插件 `.so` 必须与宿主 ABI 及 libc++/fcitx5 ABI 精确匹配。**自建宿主必须与自建插件同批发布，不能混用官方插件 APK** |
| 自带键盘的应用（DeoVR / Wooorld / Wolvic）绕过输入法 | 🟢 低 | 无法解决，接受 |

### 许可证

- fcitx5-android 基座：**LGPL-2.1**（源文件带 `SPDX-License-Identifier: LGPL-2.1-or-later`）
- 插件多为 GPL 系：`fcitx5-unikey` 为 **GPL-2.0-or-later AND LGPL-2.0-or-later**（"or-later" 关键，GPL-2.0-only 会与 AGPL-3.0 不兼容）；`fcitx5-chinese-addons` 为 **GPL-2.0-or-later AND LGPL-2.1-or-later**
- librime 本身：**BSD-3-Clause**
- VRIME 现为 AGPL-3.0-or-later。GPLv3 §13 与 AGPLv3 §13 是互为对应的兼容条款，允许组合分发
- **待办**：逐文件核对 SPDX 头；保留 LICENSE、版权头与 aboutLibraries 归属页；公开完整对应源码
- 不上商店消除了 DRM / 「不得附加额外限制」的经典冲突

---

## 8. 验证方式

**Phase 0**
```sh
adb shell ime list -a
adb shell settings get secure enabled_input_methods
adb shell settings get secure show_ime_with_hard_keyboard
```
用一个打印 `KeyEvent.device/source` 的调试 IME，在 Surface keyboard 开/关两态下记录 `Configuration.keyboard`、`hardKeyboardHidden`、`InputDevice` 列表差异。

**Phase 1** — 基座 + rime + unikey 装到 Quest：能列出 schema、中文能输入、越南语 `Tieengs Vieet` → `Tiếng Việt` 正确。

**Phase 2** — `./gradlew compileDebugKotlin` 通过；T9 布局在设备上可切换、按键映射正确（尤其 `xiaobai_simp` 的 Numpad 路径）。

**Phase 3** — 场景矩阵回归：Home space 2D panel / Quest Browser / 沉浸式应用 × Surface keyboard 开关 × 布局（QWERTY / T9）。逐格记录：模式是否正确切换、候选栏是否渲染、射线点击是否命中、字号是否可读。

**Phase 4** — 两个九键 schema 逐一抽测，候选顺序与 Web 版 VRIME 同 schema 对照；冷启动部署耗时对比（设 `prebuilt_data_dir` 前后）；按需下载在断网/弱网/中断续传三种情况下的表现。

**Phase 5** — 未配置的 Quest 3 上照文档从零走通。

**Phase 6** — 每个上游 PR 独立可编译、独立可回退；本地 patch set 能干净 rebase 到 upstream/master。

---

## 9. 待核实项汇总 ⚠️

- [ ] Surface keyboard 在输入栈里的身份（Phase 0，需真机）
- [ ] `SHOW_IME_WITH_HARD_KEYBOARD` 的值与 `SHOW_FORCED` 是否生效（Phase 0，需真机）
- [ ] `.bin` 跨架构可移植性（需 CI 实验）
- [ ] 上游是否愿意接受 T9 布局 PR（需沟通）
- [ ] 逐文件 SPDX 头核对（需人工审阅）
- [ ] 无 PC 启用路径中哪一条最可靠（Phase 5，需真机）

### 调研可信度说明

Quest 平台调研中，`developers.meta.com` / `meta.com` / reddit / uploadvr 等域名被出口代理拦截，**Meta 官方文档的引文来自搜索引擎提取而非直接读取**，措辞按转述看待。经直接读取核实的一手材料：phwd/quest-tracker 反编译的 VrShell manifest 与 `KeyboardLocale.java`（约 2021 年 Quest 2 快照，非现役固件证据）、mvpdz1/369VRshurufa 的 manifest、bytedance/Fastbot_Android#288、OctoNezd/OculusLayouts。

**§3 全部事实均在本仓库源码中直接核实。** 平台可行性由真机实测确认。其余标 ⚠️ 者待验。
