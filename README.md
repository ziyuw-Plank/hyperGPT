# hyperGPT

[![Release](https://img.shields.io/github/v/release/ziyuw-Plank/hyperGPT)](https://github.com/ziyuw-Plank/hyperGPT/releases/latest)

> English: an LSPosed module for Xiaomi HyperOS (China ROM). Long-press the power key to open **ChatGPT Voice** instead of Xiao Ai. Scope is System Framework only; the power menu still appears if you keep holding for about 3 seconds.

把 HyperOS「长按电源键 → 唤醒小爱同学」换成打开 **ChatGPT 语音**。

- 只替换「启动小爱」这一步；继续按住约 **3 秒**，关机菜单照常弹出
- ChatGPT 未安装或启动失败时，自动回退原版小爱
- 纯 LSPosed 模块：不改系统分区，无界面、无额外权限

**下载：** [最新 Release](https://github.com/ziyuw-Plank/hyperGPT/releases/latest)

## 要求

- 小米 / 红米，**HyperOS 国行**（国际版长按电源键是 Google 助理，本模块不处理）
- Root + **LSPosed / Vector**
- 已安装并登录过 **ChatGPT**

## 安装

1. 从 [Releases](https://github.com/ziyuw-Plank/hyperGPT/releases/latest) 下载 APK 安装  
   （若装过旧包名 `io.github.zyw.powergpt`，请先在 LSPosed 停用并卸载旧版）
2. LSPosed 管理器 → 启用 **hyperGPT** → 作用域只勾选 **系统框架 / System Framework**（不要勾其它 App）
3. 系统设置：**设置 → 更多设置 → 快捷手势 → 长按电源键 → 唤醒小爱同学**  
   （部分版本在 设置 → 小爱同学 → 电源键唤醒）
4. **重启手机**

之后：长按电源键 → ChatGPT 语音；继续按住约 3 秒 → 关机菜单。

## 可选开关

```sh
# 临时恢复小爱（立即生效，无需重启）；改回 1 重新启用
su -c 'setprop persist.sys.powergpt.enable 0'

# 锁屏时仍用小爱（默认 unlock = 弹解锁界面后进 ChatGPT）
su -c 'setprop persist.sys.powergpt.keyguard xiaoai'
```

## 回滚

在 LSPosed 中停用本模块（或直接卸载）→ 重启即可，不留残留。

临时恢复小爱也可用上面的 `persist.sys.powergpt.enable=0`，无需重启。

## 自行构建

需要 JDK 17。在仓库根目录执行 `./gradlew assembleRelease`，产物在 `app/build/outputs/apk/release/`。

## 链接

- [Releases](https://github.com/ziyuw-Plank/hyperGPT/releases/latest)
- 源码：本仓库
