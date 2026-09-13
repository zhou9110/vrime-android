# VRIME Android 项目约定

本文件统一维护项目方向、开发约定、当前状态与后续计划。用户的新指示优先于本文；功能和验证状态变化时同步更新本文，不再维护重复的计划/交接文档。

## 工作方式

- 现阶段保持最小可用，不 over-engineer。优先解决用户实际反馈，不主动扩展功能、架构层次或依赖。
- **调试包由用户统一安装和运行。代理只执行本地编译和测试，不安装、启动或操作设备，不运行 connectedAndroidTest 等设备测试。**
- **当前不要推送。** 合并、修改和提交保留在本地；用户之后明确要求时再推送。
- 不清空设备数据来验证默认值。手动保存的偏好值不会被新默认值覆盖，可告知用户在设置中调整。
- 区分代码已实现、本地测试通过、用户真机验证通过；不要将其中一种表述为另一种。
- 完成针对改动的必要验证即可，不反复扩大测试范围。保留已有用户改动。

## 项目方向

- 基于 `fcitx5-android/fcitx5-android` 的上游优先薄 fork，面向 Meta Quest 3 的原生 Android 多语种输入法。
- 产品主线保留完整软键盘，也支持物理键盘/Surface keyboard 下的候选栏形态。
- 只走 sideload，不以 Horizon Store 为发布目标；清晰的安装与启用指引是后续交付内容。
- 沿用 Kotlin Android Views、现有手势和主题系统，不引入 Compose 或重写输入引擎绑定。
- 优先复用上游插件生态：中文内置 libime；Rime 方案用 rime 插件，粤语用 jyutping/Rime，注音用 chewing，越南语用 unikey，韩语用 hangul。不要另用 Rime 重做越南语/韩语引擎。
- 尽量新增独立文件，减少对上游文件的侵入。通用修复可单独整理回馈上游，Quest 专属默认值留在本地。

## 分支

| 分支 | 用途 |
|---|---|
| `master` | 上游镜像，不放产品改动 |
| `vrime` | 产品主线，已整合当前功能与文档 |
| `feat/t9-keyboard` | 九键暂存，尚未合入产品主线 |
| `feat/vr-defaults` | 功能已合入 `vrime`，不再继续开发 |
| `docs/wrap-up` | 文档更新已应用到 `vrime`，不再单独维护 |

小修在 `vrime` 做；较大功能使用短期分支，合并后再整理。不增加 develop/release 等长期分支。当前合并只在本地完成，远端 `vrime` 尚未覆盖；后续推送前重新检查远端状态。需要覆盖已分叉历史时使用有明确基线的 `--force-with-lease`。

## 代码入口

主要 Kotlin 包：`app/src/main/java/org/fcitx/fcitx5/android/`。

- `input/FcitxInputMethodService.kt`：输入法生命周期、InputConnection、文字提交、跨屏输入与窗口区域。
- `input/InputView.kt`、`input/BaseInputView.kt`：键盘/候选栏布局、悬浮尺寸和竖屏样式上下文。
- `input/keyboard/`：布局、按键样式和手势；按键动作经 `CommonKeyActionListener` 进入引擎。
- `input/popup/`：预览、长按字符面板、弹出框边界和选字坐标。
- `input/bar/`、`input/status/StatusAreaWindow.kt`：工具栏和 `...` 功能面板入口。
- `input/remote/`、`app/src/main/assets/cross-screen.html`：局域网服务、同步数据模型和浏览器输入页面。
- `data/prefs/AppPrefs.kt`：默认偏好；`data/theme/`：主题。
- `daemon/FcitxDaemon.kt` → `core/Fcitx.kt` → `app/src/main/cpp/`：引擎连接、事件和 JNI。客户端经 daemon 访问引擎，不另建绑定。
- `lib/`：原生核心依赖；`plugin/`：插件 APK；`build-logic/`：构建约定。

## 当前实现与反馈

### 键盘

- 基座已在 Quest 3 运行。
- 横屏默认高度 **77%**（早期 85%，为中文拼音预编辑行留出空间），左右边距 **6dp**，底部边距 **5dp**，工具栏默认展开。
- 普通尺寸下泡泡截断已由用户确认修复。弹出位置受窗口边界限制，显示和滑动选字使用相同偏移。
- `...` → 悬浮键盘：支持缩放、拖动、恢复、尺寸与位置保存；采用竖屏按键边距及副字符排列，泡泡随比例缩小。
- 悬浮仅在系统分配给 IME 的窗口内移动，不承诺缩小 Quest 系统面板。
- 拖拽栏外观优化按用户要求暂缓。

### 跨屏输入

- `...` → 跨屏输入。弹窗最多 **420dp**，不超过输入法窗口宽度的 90%。
- 固定端口 **8765**，访问地址 `http://Quest局域网IP:8765`。IP 可能变化；端口被占用时提示，不自动更换。
- 随机 **4 位数字**配对码；每次服务重新开启时重新生成。连续五次配对错误限流三十秒。
- 连接的是输入法，不是某个固定输入框。清空或切换输入框自动跟随，无需再次配对。
- 双向模式约每 **200ms** 同步文字与选区；网页中文组词结束后才发送。通过 `getExtractedText` 回读完整内容，用最小变更范围和 `setSelection` 更新。冲突时以最新 Quest 状态为准。
- 无法完整回读、密码框或内容超过 32768 个 UTF-16 单元时自动进入单向模式：新文字发送后网页清空，提供退格按钮与 Backspace，不回读/保留已提交文字，不同步选区。
- **单向模式直接向当前输入连接提交文字或退格，不校验回读版本、不在写入前重置 Fcitx。** 旧实现因此误报密码框 409；修复后用户已确认真机通过。
- 单向网络错误不自动重发，防止重复输入。目标应用仍须支持提交文字或按键。
- 网页“断开连接”停止该网页同步；Quest 服务继续运行，其他网页不受影响。迟到响应不能恢复已断开的网页连接。
- 收起 Quest 弹窗后服务继续运行，通过弹窗“关闭服务”停止；IME 服务销毁时也关闭。
- 使用可信局域网内的明文 HTTP，不经云端，不记录输入文字或密码日志。
- 用户已反馈跨屏输入整体效果不错；具体应用回读、选区和原始按键兼容性仍按实际反馈验证。

## 本地构建与测试

工具链以 `build-logic/convention/src/main/kotlin/Versions.kt` 和 `gradle/libs.versions.toml` 为准，README 的 SDK/NDK/CMake 版本较旧。

当前配置：compile/target SDK 36、min SDK 23、NDK 28.0.13004108、CMake 3.31.6、Build Tools 36.1.0。Java 源码目标为 11；运行 Gradle 的 JDK 需兼容当前插件。本地 SDK 已有 CMake 3.31.6。宿主依赖包括 extra-cmake-modules、gettext。

Quest 构建使用 arm64-v8a：

```sh
./gradlew :app:assembleDebug -PbuildABI=arm64-v8a --offline
./gradlew :app:testDebugUnitTest --tests '*CrossScreenServerTest' --tests '*TextChangeTest' --tests '*PopupPlacementTest' -PbuildABI=arm64-v8a --offline
node app/src/test/js/cross-screen.test.cjs
```

- APK 位于 `app/build/outputs/apk/debug/`，具体文件名以 `output-metadata.json` 为准。
- 默认 applicationId 为 `org.fcitx.fcitx5.android`，debug 增加 `.debug`；服务类名不加后缀。IME ID 应由用户从设备查询，不手拼。
- 本轮功能定向测试、网页逻辑测试和 APK 构建已通过。网页测试模拟 DOM/服务，不代表设备端到端验证。
- 已知已有失败：`ThemeSerializationTest.version2` 仍断言 2.0 数据无需迁移，但 serializer 已有 2.1 迁移；不要把此失败归因于当前功能。
- 已知 AndroidTest 资源链接问题：测试包缺少 `ic_launcher_debug`、`ic_launcher_round_debug`、`app_name_debug`。曾在安装前失败，未执行设备安装；现在按用户约定只做本地测试。
- 机器级版本覆盖用 Gradle 属性或环境变量，不为本机环境改 `Versions.kt`。只构建基座可临时用 CMake 3.22.1，但 Rime 的 WHOLE_ARCHIVE 需 3.24+。

## 后续计划（暂缓，待用户安排）

当前优先修复使用反馈。以下是保留的方向，不是立即执行的任务：

1. 悬浮拖拽栏外观、VR 主题、候选栏字号/内边距与射线点击命中优化；优先用现有配置。
2. Surface keyboard 自动模式切换验证：用户在开启/关闭状态下记录输入设备、KeyEvent 来源、Configuration 和窗口表现。现有 `InputDeviceManager` 由按键/触摸事件驱动，不应直接等同于插拔检测。需要证据后再考虑手动候选栏入口或 AlwaysShow。
3. 九键：独立分支已有标准 T9 布局及 `use_t9_layout` 开关，但尚未合入。普通数字映射不能直接用于 `xiaobai_simp` 的重排/Numpad 方案；内置 libime 不识别九键数字拼音。Rime 可用后再验证方案暴露方式和自动布局切换，不能猜 `InputMethodEntry`。
4. Rime/schema：现有插件使用预编译 librime **1.12.0**；历史 VRIME 的 1.8.5 `.bin` 需重编。复用 schema 清单、依赖图和编译流程；不移植 WASM/DOM/IDBFS 绑定。九键消歧移植不得复用 Web 版 setTimeout 顺序竞态。
5. 大词库按需下载和真实文件部署，评估 `prebuilt_data_dir` 避免设备端重编。验证 arm64 加载、跨架构格式及中断恢复；不能凭旧 WASM 经验认定二进制兼容。
6. 多语种插件、品牌 applicationId/图标、侧载启用指引及无 PC 路径。主包与插件应按同批 ABI/工具链发布。最终以用户在未配置 Quest 上照指引走通为验收标准。
7. 将通用改动回馈上游，保留许可证、SPDX 和依赖归属；发布前核对对应源码及插件许可证。
8. 沉浸式应用支持属于可选探索，不默认增加悬浮窗权限。绕过系统输入法的应用不承诺兼容。

历史调研认为 Meta 自带黑色 keyboard assistant bar 无第三方候选接入口，因此候选 UI 由本应用绘制；此结论含旧固件/反编译资料，不能当作现役固件 API 保证。平台细节、SHOW_FORCED 行为、Surface keyboard 身份和无 PC 启用路径仍需实际验证。

## 用户真机回归参考

- 顶部字母键、多行长按面板，以及悬浮四角缩放后的泡泡显示与选字。
- 中文预编辑行、候选栏、符号页和工具栏；恢复尺寸及重启后位置保存。
- 两端中文、换行、Emoji、删除、清空、选区、切换输入框，中文组词期间不被轮询覆盖。
- 单向模式及密码框的输入、退格；断网时不重复发送。
- 网页断开、重连、迟到响应，固定端口重启与占用提示。
