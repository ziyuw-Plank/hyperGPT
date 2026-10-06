package io.github.zyw.powergpt;

import de.robv.android.xposed.XposedBridge;

/** 极简日志：统一带 [PowerGPT] 前缀写入 LSPosed 日志（XposedBridge.log 同时输出到 logcat）。 */
final class Log {
    static final String TAG = "[PowerGPT] ";

    private Log() {}

    static void i(String msg) {
        try {
            XposedBridge.log(TAG + msg);
        } catch (Throwable ignored) {
            // 日志失败不能影响 system_server
        }
    }

    static void e(String msg, Throwable t) {
        try {
            XposedBridge.log(TAG + msg + ": " + t);
        } catch (Throwable ignored) {
        }
    }
}
