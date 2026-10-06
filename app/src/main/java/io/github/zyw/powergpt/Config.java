package io.github.zyw.powergpt;

import java.lang.reflect.Method;

/**
 * 可选开关，均为系统属性（无需额外权限、无 UI、无文件读写），每次按键时实时读取：
 *
 *   persist.sys.powergpt.enable   = 0 / false  → 关闭模块逻辑，完全走原版小爱（默认开启）
 *   persist.sys.powergpt.keyguard = xiaoai     → 锁屏状态下仍走原版小爱
 *                                   unlock     → （默认）启动 ChatGPT 并弹出解锁界面，解锁后显示 ChatGPT
 *
 * 例：su -c 'setprop persist.sys.powergpt.enable 0'
 */
final class Config {
    private static final String PROP_ENABLE = "persist.sys.powergpt.enable";
    private static final String PROP_KEYGUARD = "persist.sys.powergpt.keyguard";

    private static volatile Method sGet;

    private Config() {}

    static boolean enabled() {
        String v = get(PROP_ENABLE, "1").trim();
        return !("0".equals(v) || "false".equalsIgnoreCase(v) || "off".equalsIgnoreCase(v));
    }

    /** 锁屏时是否回退到原版小爱。 */
    static boolean fallbackToXiaoAiOnKeyguard() {
        return "xiaoai".equalsIgnoreCase(get(PROP_KEYGUARD, "unlock").trim());
    }

    /** android.os.SystemProperties 是隐藏 API，这里用反射读取；失败返回默认值。 */
    private static String get(String key, String def) {
        try {
            Method m = sGet;
            if (m == null) {
                m = Class.forName("android.os.SystemProperties")
                        .getMethod("get", String.class, String.class);
                sGet = m;
            }
            Object r = m.invoke(null, key, def);
            return r instanceof String ? (String) r : def;
        } catch (Throwable t) {
            return def;
        }
    }
}
