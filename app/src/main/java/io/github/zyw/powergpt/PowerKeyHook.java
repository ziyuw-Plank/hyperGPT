package io.github.zyw.powergpt;

import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 只替换「启动小爱」这一个动作，不碰按键事件本身。
 *
 * HyperOS 1.0 / 2.0 / 3.0（Android 14 / 15 / 16）的 miui-services.jar 中，长按电源键的调用链为
 * （均已对官方 ROM 反编译核实，见 README_zh.md「原理」一节）：
 *
 *   PhoneWindowManager 单键手势检测 (SingleKeyGestureDetector)
 *     └─ com.android.server.input.shortcut.singlekeyrule.PowerKeyRule#onMiuiLongPress
 *          └─ triggerLongPress()：Settings.System "long_press_power_key" == "launch_voice_assistant"
 *               └─ MiuiSingleKeyRule#postTriggerFunction → Handler.post(...)
 *                    └─ com.miui.server.input.util.ShortCutActionsUtils
 *                         #triggerFunction("launch_voice_assistant", "long_press_power_key", extras, haptic)
 *                           └─ private boolean launchVoiceAssistant(String shortcut, Bundle extra)   ← 这里
 *                                └─ startForegroundServiceAsUser(小爱 VoiceService)
 *
 *   松手时：PowerKeyRule#onMiuiLongPressKeyUp → workAtPowerUp() 会再调用一次
 *   launchVoiceAssistant("long_press_power_key", {extra_long_press_power_function="power_up"})
 *   通知小爱「松手了」（按住说话）。如果长按已被我们换成 ChatGPT，这个松手通知也要吞掉，
 *   否则会把小爱拉起来。
 *
 *   关机菜单走的是另一条路：PowerKeyRule#onMiuiVeryLongPress（固定 3000ms）
 *     → OriginalPowerKeyRuleBridge#onVeryLongPress → AOSP PhoneWindowManager.PowerKeyRule
 *     → powerVeryLongPress()，行为由 Settings.Global "power_button_very_long_press"=1 决定 → 关机菜单。
 *   这条路径不经过 launchVoiceAssistant，本模块完全不涉及，长按约 3 秒仍会弹出关机菜单。
 *
 * 因此 Hook 点选在最末端的 launchVoiceAssistant(String, Bundle)：
 *   - 不改按键拦截、长按超时、规则分发、震动、儿童空间 / 开机向导等前置检查（它们都在调用它之前）；
 *   - 返回值仍交给 triggerFunction 原逻辑去决定震动反馈；
 *   - 启动 ChatGPT 失败时什么也不改，原方法照常执行（启动小爱）。
 *
 * 仅当该私有方法在未来版本中被改名/删除时，才退回 Hook triggerFunction（只匹配
 * function == "launch_voice_assistant" 且来源为电源键），其它所有快捷功能一律放行。
 */
final class PowerKeyHook {

    static final String CLASS_SHORTCUT_ACTIONS = "com.miui.server.input.util.ShortCutActionsUtils";

    // 以下常量与 HyperOS ShortCutActionsUtils / PowerKeyRule 中的字面量一致
    static final String FUNCTION_VOICE_ASSISTANT = "launch_voice_assistant";
    static final String SOURCE_LONG_PRESS_POWER = "long_press_power_key";
    /** 部分机型的「短按住电源键」助手入口（仅在 long_press_power_key 为 none 时由系统使用）。 */
    static final String SOURCE_IMPERCEPTIBLE_POWER = "imperceptible_press_power_key";
    static final String EXTRA_LONG_PRESS_POWER_FUNCTION = "extra_long_press_power_function";
    static final String EXTRA_POWER_GUIDE = "powerGuide";
    static final String ACTION_POWER_UP = "power_up";

    /** 最近一次电源键长按是否已由 ChatGPT 接管（用于配对吞掉松手通知）。 */
    private static final AtomicBoolean sLongPressTakenOver = new AtomicBoolean(false);

    private PowerKeyHook() {}

    static void install(ClassLoader cl) {
        Class<?> clazz = XposedHelpers.findClassIfExists(CLASS_SHORTCUT_ACTIONS, cl);
        if (clazz == null) {
            Log.i(CLASS_SHORTCUT_ACTIONS + " not found (not HyperOS/MIUI?) - module idle");
            return;
        }

        // 首选：private boolean launchVoiceAssistant(String shortcut, Bundle extra)
        Method launch = findBooleanMethod(clazz, "launchVoiceAssistant",
                String.class, Bundle.class);
        if (launch != null) {
            XposedBridge.hookMethod(launch, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Boolean r = decide(param.thisObject,
                                (String) param.args[0], (Bundle) param.args[1]);
                        if (r != null) param.setResult(r);
                    } catch (Throwable t) {
                        // 出错 = 不干预，原方法照常启动小爱
                        Log.e("launchVoiceAssistant hook error, fall back to original", t);
                    }
                }
            });
            Log.i("hooked " + launch);
            // 防御 ART 把私有方法内联进调用方导致 Hook 不生效：把调用方 triggerFunction 反优化
            // （仅在快捷手势触发时调用，解释执行的开销可以忽略）。
            deoptimize(clazz, "triggerFunction");
            return;
        }

        // 回退：triggerFunction。HyperOS 1/2/3 中 4 参版本只是转调 5 参版本，
        // 所以优先只 Hook 5 参版本，避免同一次触发被处理两次。
        Method trigger = findBooleanMethod(clazz, "triggerFunction",
                String.class, String.class, Bundle.class, boolean.class, String.class);
        if (trigger == null) {
            trigger = findBooleanMethod(clazz, "triggerFunction",
                    String.class, String.class, Bundle.class, boolean.class);
        }
        if (trigger == null) {
            Log.i("no known hook point in " + CLASS_SHORTCUT_ACTIONS + " - module idle");
            return;
        }
        XposedBridge.hookMethod(trigger, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (!FUNCTION_VOICE_ASSISTANT.equals(param.args[0])) return;
                    Boolean r = decide(param.thisObject,
                            (String) param.args[1], (Bundle) param.args[2]);
                    if (r != null) param.setResult(r);
                } catch (Throwable t) {
                    Log.e("triggerFunction hook error, fall back to original", t);
                }
            }
        });
        Log.i("launchVoiceAssistant not found, hooked fallback " + trigger);
    }

    /**
     * 决定如何处理一次「启动语音助手」请求。
     *
     * @return null  = 不干预，执行原方法（启动小爱）；
     *         TRUE  = 已由本模块处理，跳过原方法（等同于原方法成功返回 true）。
     */
    private static Boolean decide(Object thiz, String source, Bundle extra) {
        if (!Config.enabled()) return null;

        if (SOURCE_LONG_PRESS_POWER.equals(source)) {
            String fn = extra != null ? extra.getString(EXTRA_LONG_PRESS_POWER_FUNCTION) : null;

            // 松手通知：只有当这次长按确实由 ChatGPT 接管时才吞掉，否则交还小爱。
            if (ACTION_POWER_UP.equals(fn)) {
                return sLongPressTakenOver.getAndSet(false) ? Boolean.TRUE : null;
            }
            // 「长按电源键唤醒小爱」引导弹窗（用户尚未设置时系统弹出的引导），不接管。
            if (extra != null && extra.getBoolean(EXTRA_POWER_GUIDE, false)) {
                return null;
            }
            boolean ok = ChatGptLauncher.launch(getContext(thiz), "long_press_power_key");
            sLongPressTakenOver.set(ok);
            return ok ? Boolean.TRUE : null;
        }

        if (SOURCE_IMPERCEPTIBLE_POWER.equals(source)) {
            return ChatGptLauncher.launch(getContext(thiz), source) ? Boolean.TRUE : null;
        }

        // 其它来源（AI 键 "ai_key"、外接键盘 "keyboard"、手势等）一律不碰。
        return null;
    }

    private static Context getContext(Object thiz) {
        try {
            Object c = XposedHelpers.getObjectField(thiz, "mContext");
            if (c instanceof Context) return (Context) c;
        } catch (Throwable ignored) {
        }
        // 兜底：system_server 的 system context
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Object c = at.getMethod("getSystemContext").invoke(thread);
            if (c instanceof Context) return (Context) c;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * LSPosed 在其 XposedBridge 中额外提供 deoptimizeMethod(Member)（原版 API 82 没有），
     * 这里反射调用；不存在或失败都忽略。
     */
    private static void deoptimize(Class<?> clazz, String name) {
        Method deopt;
        try {
            deopt = XposedBridge.class.getMethod("deoptimizeMethod", java.lang.reflect.Member.class);
        } catch (Throwable t) {
            return;
        }
        for (Method m : clazz.getDeclaredMethods()) {
            if (!name.equals(m.getName())) continue;
            try {
                deopt.invoke(null, m);
            } catch (Throwable t) {
                Log.e("deoptimize " + name + " failed (ignored)", t);
            }
        }
    }

    /** 查找返回 boolean、非 static 的声明方法；不存在返回 null（不抛异常）。 */
    private static Method findBooleanMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            Method m = clazz.getDeclaredMethod(name, params);
            if (m.getReturnType() != boolean.class || Modifier.isStatic(m.getModifiers())) {
                Log.i("unexpected signature, skip: " + m);
                return null;
            }
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }
}
