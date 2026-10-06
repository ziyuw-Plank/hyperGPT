# HyperOS 长按电源键 → ChatGPT 语音（LSPosed 模块）

把 HyperOS「长按电源键唤醒小爱同学」换成打开 **ChatGPT 语音助手**
（`com.openai.chatgpt/com.openai.voice.assistant.AssistantActivity`）。

- 纯 LSPosed 模块：**不改 system / vendor / boot 等任何 AVB 校验分区**，不需要 Magisk 系统覆盖，不写任何系统文件。
- 只在 **系统框架（system_server）** 中运行；无 Activity、无权限、无网络、不执行 su / shell。
- 只替换「启动小爱」这一个动作。**按键事件、长按时长、关机菜单逻辑一律不碰**，长按约 3 秒仍然弹出关机菜单。
- 任何一步失败（ChatGPT 未安装、组件不存在、启动异常、Hook 点找不到）都会**自动回退到原版小爱**，不会让电源键失灵。

> ⚠️ 诚实说明：代码已在 HyperOS 1.0 / 2.0 / 3.0 官方 ROM 的 `miui-services.jar` 反编译结果上逐项核对（见「原理」），
> 并已成功编译；但**没有在真机上运行过**。请先按「验证」一节确认日志。

---

## 1. 安装

1. 安装 `out/hyperos-power-gpt-1.0.0.apk`（自签名；直接点开安装或 `adb install`）。
2. 打开 LSPosed 管理器 → 模块 → 启用 **「HyperOS 电源键 → ChatGPT」**。
3. 作用域 **只勾选「系统框架 / System Framework」**（模块已声明推荐作用域，启用时会自动勾上）。
   **不要**勾选小爱、ChatGPT、设置或其它任何 App——本模块不需要，而且即使勾了，代码也会立即返回不做任何事。
4. **重启手机**（system_server 的 Hook 必须重启才生效，「软重启」也可以）。

## 2. 需要的 HyperOS 设置

模块接管的是「长按电源键 → 唤醒小爱」这条原生路径，所以必须让系统先走这条路：

- **设置 → 更多设置 → 快捷手势 → 长按电源键 → 选「唤醒小爱同学」**
  （部分版本在 **设置 → 小爱同学 → 电源键唤醒**，或 小爱同学 App 内「唤醒方式 → 电源键唤醒」。）
- 对应的系统键值：`Settings.System long_press_power_key = launch_voice_assistant`，可用下面命令确认：

```sh
su -c 'settings get system long_press_power_key'      # 应输出 launch_voice_assistant
```

- 设成「无」或「关机菜单」时，长按电源键不会经过小爱，模块也不会做任何事（这是预期行为）。
- 小爱同学 App **不要卸载/冻结**也可以，模块并不依赖它；但如果冻结了小爱，系统设置里可能不再显示该选项。
- 手机里需已安装 ChatGPT，并且至少手动打开、登录过一次。

## 3. 验证

**LSPosed 日志**：LSPosed 管理器 → 日志 → 「模块日志」，搜索 `[PowerGPT]`。开机后应看到一行：

```
[PowerGPT] hooked private boolean com.miui.server.input.util.ShortCutActionsUtils.launchVoiceAssistant(java.lang.String,android.os.Bundle)
```

长按电源键后应看到：

```
[PowerGPT] launched ChatGPT voice (long_press_power_key, user 0)
```

**logcat**（电脑 adb 或手机终端）：

```sh
su -c 'logcat -v time | grep -E "PowerGPT|launchVoiceAssistant"'
```

- 出现 `[PowerGPT] launched ChatGPT voice ...` → 已接管成功。
- 小爱被拉起、且没有 `launched ChatGPT` 这一行 → 说明这次走了原版小爱，往上找 `[PowerGPT]` 给出的原因
  （例如 `not resolvable` = ChatGPT 未安装或组件名变了）。系统自己的 `launchVoiceAssistant startForegroundServiceAsUser`
  日志（MiuiInputLog）在部分版本上也会出现，可作辅助判断，但它受系统日志级别控制，不一定可见。
- 开机后完全没有 `[PowerGPT]` → 模块没加载：检查是否启用、作用域是否勾了「系统框架」、是否重启。
- 有 `launchVoiceAssistant not found, hooked fallback ...` → 你的 ROM 改了方法名，模块已自动改用备用 Hook 点 `triggerFunction`。
- 有 `no known hook point ... module idle` → 两个 Hook 点都没找到，模块不做任何事（请把 ROM 版本发给作者适配）。

## 4. 可选开关（系统属性，无需 UI，立即生效，不用重启）

```sh
# 临时关闭模块逻辑（完全恢复原版小爱），改回 1 即重新启用
su -c 'setprop persist.sys.powergpt.enable 0'
su -c 'setprop persist.sys.powergpt.enable 1'

# 锁屏时的行为：
#   unlock（默认）= 启动 ChatGPT 并弹出解锁界面，验证通过后显示 ChatGPT（不会绕过锁屏验证）
#   xiaoai        = 锁屏时仍然用原版小爱
su -c 'setprop persist.sys.powergpt.keyguard xiaoai'
```

## 5. 回滚 / 出问题怎么办

1. **正常回滚**：LSPosed 管理器里关闭本模块 → 重启。（或者直接卸载 APK → 重启。）不留任何残留：模块没有写过任何设置或文件。
2. **只想临时恢复小爱**：`su -c 'setprop persist.sys.powergpt.enable 0'`，不用重启。
3. **万一无法开机（理论上不应发生，所有 Hook 都包在 try/catch 里）**：
   - **Magisk 安全模式**：开机过程中（出现开机 logo 后）**按住或连按「音量-」**，Magisk 会禁用所有 Magisk 模块（包括 LSPosed/Zygisk），系统即可正常进入；进入后在 LSPosed 中禁用本模块，再在 Magisk 中把 LSPosed 重新启用并重启。
   - **KernelSU / APatch**：开机时**连按 3 次「音量-」**进入安全模式，作用同上。
   - **adb 可用时**：`adb shell su -c 'touch /data/adb/modules/zygisk_lsposed/disable'` 后重启，即可整体停用 LSPosed（目录名以你实际安装的 LSPosed 模块为准，可 `ls /data/adb/modules` 查看）。
   - 以上都不涉及刷写分区；Android 自带的「安全模式」**不会**禁用 Xposed 模块，别依赖它。

---

## 6. 原理（为什么这样 Hook，以及为什么关机菜单不受影响）

以下调用链来自对官方 ROM `system_ext/framework/miui-services.jar` 的反编译（jadx），三个版本完全一致：

| ROM | 机型 | Android | 来源 |
|---|---|---|---|
| HyperOS 1.0 `V816.0.6.0.UNCCNXM` | Xiaomi 14 (houji) 国行 | 14 | dumps.tadiphone.dev/dumps/xiaomi/houji |
| HyperOS 2.0 `OS2.0.4.0.VOCCNDM` | dada 国行 | 15 | dumps.tadiphone.dev/dumps/xiaomi/dada |
| HyperOS 3.0 `OS3.0.5.0.WOCCNXM` | dada 国行 | 16 | 同上 |

### 6.1 长按电源键 → 小爱

```
PhoneWindowManager 单键手势检测（SingleKeyGestureDetector，按下即开始计时）
 └─ com.android.server.input.shortcut.singlekeyrule.PowerKeyRule  extends MiuiSingleKeyRule
     ├─ onMiuiLongPress()            —— 长按超时 = 规则 JSON 中的 longPressTimeOut，缺省为
     │                                   Settings.Secure long_press_timeout（通常约 0.4~0.5 s）
     │   └─ supportMiuiLongPress() 为真（long_press_power_key 不是 none）→ setPowerKeyHandled(true)
     │       └─ triggerLongPress(): long_press_power_key == "launch_voice_assistant"
     │           └─ postTriggerFunction("long_press_power_key", "launch_voice_assistant",
     │                 {extra_long_press_power_function="long_press_power_key", powerGuide=false})
     │               └─ Handler.post → ShortCutActionsUtils.triggerFunction(function, action, extras, haptic)
     │                   ├─ 前置检查：系统就绪 / 儿童空间 / 开机向导完成 / 埋点
     │                   ├─ launchVoiceAssistant(action, extras)      ← ★ 本模块唯一的 Hook 点
     │                   │     原实现：startForegroundServiceAsUser(小爱 VoiceService, ACTION_ASSIST)
     │                   └─ triggerHapticFeedback(triggered, ...)   —— 震动反馈仍按返回值执行
     └─ onMiuiLongPressKeyUp() → workAtPowerUp()
         └─ 再次 launchVoiceAssistant("long_press_power_key", {extra_long_press_power_function="power_up"})
            （通知小爱「松手了」，用于按住说话）
```

模块在 `launchVoiceAssistant(String shortcut, Bundle extra)`（private，返回 boolean）的 **before** 回调里判断：

- `shortcut == "long_press_power_key"` 且不是 `power_up`、不是 `powerGuide=true`（未设置时系统弹出的小爱引导）
  → 启动 ChatGPT；**成功**才 `setResult(true)` 跳过原方法；**失败返回 null，原方法照常启动小爱**。
- `shortcut == "long_press_power_key"` 且 `power_up`：仅当这次长按确实被 ChatGPT 接管时吞掉（否则松手会把小爱拉起来）；没接管就原样放行。
- `shortcut == "imperceptible_press_power_key"`（部分机型的「轻按住电源键」助手入口，只有长按设为「无」且该入口设为小爱时才出现）→ 同样换成 ChatGPT。
- 其它来源（`ai_key` 实体 AI 键、`keyboard` 外接键盘小爱键等）**一律不碰**。

如果某个 ROM 把 `launchVoiceAssistant` 改名/删除，才退回 Hook `triggerFunction`（优先 5 参版本，因为 4 参版本只是转调它），
且只匹配 `function == "launch_voice_assistant"` + 电源键来源。两者都没有就什么都不做。
为防止 ART 把私有方法内联进 `triggerFunction` 导致 Hook 失效，模块会调用 LSPosed 提供的 `XposedBridge.deoptimizeMethod` 反优化 `triggerFunction`（不存在则忽略）。

### 6.2 关机菜单 —— 完全不经过 Hook 点

```
PowerKeyRule
 ├─ miuiSupportVeryLongPress() = supportMiuiLongPress()      （长按设了功能时，开启“超长按”）
 ├─ getMiuiVeryLongPressTimeoutMs() = 3000 ms                 （MiuiSingleKeyRule.DEFAULT_VERY_LONG_PRESS_TIME_OUT）
 └─ onMiuiVeryLongPress()
     └─ OriginalPowerKeyRuleBridge.onVeryLongPress()
         └─ AOSP PhoneWindowManager.PowerKeyRule.onVeryLongPress() → powerVeryLongPress()
             └─ Settings.Global power_button_very_long_press = 1（由 MiuiShortcutTriggerHelper
                .setVeryLongPressPowerBehavior() 在长按设了功能时写入）→ 关机菜单 (Global Actions)
```

- HyperOS 的设计是：**约 0.5 s 触发助手，继续按住到 3 s 弹出关机菜单**。两条路径由按键手势检测器分别计时、分别回调。
- 本模块**没有** Hook `interceptKeyBeforeQueueing` / `interceptKeyBeforeDispatching`、`PowerKeyRule`、`MiuiSingleKeyRule`、
  任何 `get*TimeoutMs`、`postKeyFunction`、`setPowerKeyHandled`、也不修改任何 Settings 键值，**不消费任何按键事件**。
- 被替换的只是长按回调最末端「启动哪个 App」的那一步；`PowerKeyRule` 里决定长按/超长按、`mIsXiaoaiServiceTriggeredByLongPressPower`
  等状态的代码照常运行（`postTriggerFunction` 返回的是 `Handler.post` 的结果，与我们是否接管无关）。
- 所以：短长按 → ChatGPT；**继续按住到约 3 秒 → 关机菜单照常弹出**（会盖在 ChatGPT 上面，与原版小爱时的表现一致）。
- HyperOS 3.0 在超长按时可能先弹一次「电源键说话引导」（`launch_global_power_guide`，guide_type=3，有次数上限），这也不是本模块的路径，保持原样。
- 另外，长按设为「无」时还有 `BaseMiuiPhoneWindowManager.postPowerLongPress()` 的 `imperceptible_press_power_key` 与 `dumpsys` 超长按路径，同样不受影响。

### 6.3 启动 ChatGPT 的方式

在 system_server 内（`Binder.clearCallingIdentity()` 后）：

1. `ActivityManager.getCurrentUser()` 取得当前用户（支持第二空间/多用户，需该用户下装有 ChatGPT）。
2. `PackageManager.resolveActivityAsUser()` 确认 `com.openai.chatgpt/com.openai.voice.assistant.AssistantActivity` 存在且已启用，否则回退小爱。
3. `Context.startActivityAsUser(intent{component, FLAG_ACTIVITY_NEW_TASK}, 当前用户)` ——等价于你验证过的 `am start -n ...`，但不走 shell/su。
4. 若处于锁屏：调用 `IWindowManager.dismissKeyguard()` 弹出解锁界面（只是请求，仍需你本人验证）。
5. 700 ms 防抖，避免连续触发。

## 7. 遵循的 LSPosed 开发规范

- **Legacy Xposed API**（`de.robv.android.xposed:api:82`，来自 https://api.xposed.info/），`compileOnly`，**不打包进 APK**（已用 dexdump 确认 APK 里只有本模块的类）。
- 入口：`assets/xposed_init` → `io.github.zyw.powergpt.MainHook`（实现 `IXposedHookLoadPackage`）。
- `AndroidManifest.xml` meta-data：`xposedmodule=true`、`xposeddescription`、`xposedminversion=93`、`xposedscope=@array/xposed_scope`。
- 作用域：`res/values/arrays.xml` 中 `xposed_scope` **只有一项 `android`**（legacy API 中「系统框架」的写法）。
  若改用 modern libxposed API，则应改为 `META-INF/xposed/scope.list` 写 `system`——本模块用的是 legacy API，所以不需要 scope.list。
- `handleLoadPackage` 首行即判断 `packageName == "android" && processName == "android"`（LSPosed 对 system_server 就是这样传参），否则立即返回；并用原子标志防止重复安装。
- 不 Hook 作用域以外的任何进程；所有 Hook 回调和安装过程都 `try/catch (Throwable)`，异常只记日志、不抛回 system_server。
- 混淆规则 `app/proguard-rules.pro`：保留入口类与 `XC_MethodHook` 子类，关闭优化与混淆，`-dontwarn de.robv.android.xposed.**`。
- 日志统一 `XposedBridge.log("[PowerGPT] ...")`，只在安装和每次按键时各打一行。

## 8. 已知限制 / 版本差异

- **只在长按电源键设置为「唤醒小爱同学」(`launch_voice_assistant`) 时生效**。国际版/海外版 HyperOS 的长按选项通常是
  「Google 助理」(`launch_google_search`)，不会经过小爱路径，本模块不处理（这是刻意的，避免误伤）。
- 已核对：HyperOS 1.0 (A14)、2.0 (A15)、3.0 (A16) 国行 ROM 的方法名和签名完全一致
  （`launchVoiceAssistant(String,Bundle)Z`、`triggerFunction` 4/5 参、`mContext:Context`）。其它小版本/机型（尤其平板、折叠屏、
  定制 ROM）理论上相同，但未逐一验证。
- **未在真机上运行过**；在真机上可能的不确定点：ChatGPT 的 `AssistantActivity` 能否在锁屏上直接显示（大概率不能，所以默认先弹解锁界面）；
  ChatGPT 以后改了 Activity 名时会自动回退小爱，需要更新模块里的组件名。
- 息屏状态下长按电源键：系统会先亮屏并进入锁屏，按上面的锁屏逻辑处理（默认弹解锁界面）；具体表现未经真机验证。
- 小爱的「按住说话、松手结束」语义不会传给 ChatGPT（ChatGPT 语音界面自己管理会话）；松手不会关闭 ChatGPT。
- 继续按住到 3 秒会弹出关机菜单（原版行为，刻意保留）。
- 儿童空间、开机向导未完成、系统未就绪时，系统自己就不会触发（检查在 Hook 点之前）。
- 带实体 AI 键的机型，AI 键唤醒小爱（`ai_key`）不受影响。

## 9. 自行构建

```sh
# 方式一：Gradle（AGP 9.4.1 / Gradle 9.6 / JDK 17 / compileSdk 36）
gradle assembleRelease            # 产物 app/build/outputs/apk/release/app-release.apk

# 方式二：不用 Gradle（aapt2 + javac + d8 + apksigner）
./build.sh                        # 产物 out/hyperos-power-gpt-1.0.0-raw.apk
```

签名信息放在本地的 `keystore.properties` + `keystore/`，两者都在 `.gitignore` 里，**不会提交到仓库**。
第一次运行 `./build.sh` 时如果没有 `keystore.properties`，会自动生成一把随机密码的自签名 key；之后 Gradle 和 `build.sh` 共用它，可以互相覆盖安装。
没有 `keystore.properties` 时，Gradle 的 debug 包用默认 debug key，release 包不签名。
注意：用不同 key 签的 APK 不能覆盖安装，需要先卸载旧版本。`build.sh` 会在缺少时自动下载 Xposed API 到 `libs/`（仅编译用）。

## 10. 参考

- 官方 ROM 反编译（反编译代码不随仓库发布）：dumps.tadiphone.dev 上 houji `V816.0.6.0.UNCCNXM`、dada `OS2.0.4.0.VOCCNDM`、dada `OS3.0.5.0.WOCCNXM` 的 `system_ext/framework/miui-services.jar`
- HyperCeiler `SystemLockApp.java`：Hook `ShortCutActionsUtils.triggerFunction` 4/5 参、`PowerKeyRule.getMiuiLongPressTimeoutMs`、`BaseMiuiPhoneWindowManager.postKeyFunction`（https://github.com/ReChronoRain/HyperCeiler）
- Gemini-mi `PowerKeyOverlayHook.java`：Hook `ShortCutActionsUtils#launchVoiceAssistant(String, Bundle)` 的 `long_press_power_key` 路径（https://github.com/SherlockChiang/Gemini-mi）
- Eta / fuck-andes `HYPEROS_SYSTEM_ENTRY.md`：`triggerFunction` + `launch_voice_assistant` + `long_press_power_key / imperceptible_press_power_key`（https://github.com/wowohut/fuck-andes）
- MiCTS：早期版本 Hook `triggerFunction` 拦截 `launchVoiceAssistant`（长按 Home），后改 `MiuiSingleKeyRule.onLongPress`（https://github.com/parallelcc/MiCTS）
- LSPosed Wiki：Module Scope、Develop Xposed Modules Using Modern Xposed API；LSPosed `StartBootstrapServicesHooker`（system_server 的 packageName/processName = "android"）
- AOSP `PhoneWindowManager.powerLongPress / powerVeryLongPress`
