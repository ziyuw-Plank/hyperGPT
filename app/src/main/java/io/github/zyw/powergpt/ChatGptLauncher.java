package io.github.zyw.powergpt;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.SystemClock;
import android.os.UserHandle;

import java.lang.reflect.Method;

/**
 * 在 system_server 内直接启动 ChatGPT 语音助手界面：
 *   com.openai.chatgpt/com.openai.voice.assistant.AssistantActivity
 * 等价于 `am start -n ...`，但不执行任何 shell / su 命令。
 *
 * 任何一步失败都返回 false，让调用方回退到原版小爱。
 */
final class ChatGptLauncher {

    static final ComponentName CHATGPT_ASSISTANT = new ComponentName(
            "com.openai.chatgpt", "com.openai.voice.assistant.AssistantActivity");

    /** 防抖：避免极短时间内重复拉起。 */
    private static final long DEBOUNCE_MS = 700;
    private static volatile long sLastLaunch;

    private ChatGptLauncher() {}

    static boolean launch(Context ctx, String reason) {
        if (ctx == null) {
            Log.i("no context, fall back to XiaoAi");
            return false;
        }
        long now = SystemClock.uptimeMillis();
        if (now - sLastLaunch < DEBOUNCE_MS) {
            return true; // 刚刚已经拉起过，视为已处理
        }

        final long ident = Binder.clearCallingIdentity();
        try {
            int userId = currentUserId();
            Intent intent = new Intent()
                    .setComponent(CHATGPT_ASSISTANT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // 1) ChatGPT 未安装 / 组件被禁用 / 版本里没有这个 Activity → 回退小爱
            if (!isResolvable(ctx, intent, userId)) {
                Log.i("ChatGPT AssistantActivity not resolvable for user " + userId
                        + ", fall back to XiaoAi");
                return false;
            }

            // 2) 锁屏处理
            boolean locked = false;
            try {
                KeyguardManager km = ctx.getSystemService(KeyguardManager.class);
                locked = km != null && km.isKeyguardLocked();
            } catch (Throwable ignored) {
            }
            if (locked && Config.fallbackToXiaoAiOnKeyguard()) {
                Log.i("keyguard locked and keyguard=xiaoai, fall back to XiaoAi");
                return false;
            }

            // 3) 以当前用户身份启动 Activity
            startActivityAsUser(ctx, intent, userId);
            sLastLaunch = now;
            Log.i("launched ChatGPT voice (" + reason + ", user " + userId
                    + (locked ? ", keyguard" : "") + ")");

            // 4) 锁屏时请求弹出解锁界面（需要用户自己验证，不绕过任何安全机制），
            //    解锁后即显示刚启动的 ChatGPT。失败无所谓。
            if (locked) requestDismissKeyguard();
            return true;
        } catch (Throwable t) {
            Log.e("launch ChatGPT failed, fall back to XiaoAi", t);
            return false;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /** ActivityManager.getCurrentUser() 为隐藏 API，反射调用；失败按 user 0。 */
    private static int currentUserId() {
        try {
            Object r = Class.forName("android.app.ActivityManager")
                    .getMethod("getCurrentUser").invoke(null);
            if (r instanceof Integer) return (Integer) r;
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static boolean isResolvable(Context ctx, Intent intent, int userId) {
        PackageManager pm = ctx.getPackageManager();
        ResolveInfo ri = null;
        try {
            // 隐藏 API：PackageManager#resolveActivityAsUser(Intent, int, int)
            Method m = PackageManager.class.getMethod("resolveActivityAsUser",
                    Intent.class, int.class, int.class);
            ri = (ResolveInfo) m.invoke(pm, intent, 0, userId);
        } catch (NoSuchMethodException e) {
            ri = pm.resolveActivity(intent, 0);
        } catch (Throwable t) {
            Log.e("resolveActivityAsUser failed", t);
            return false;
        }
        if (ri == null || ri.activityInfo == null) return false;
        ActivityInfo ai = ri.activityInfo;
        return CHATGPT_ASSISTANT.getPackageName().equals(ai.packageName)
                && CHATGPT_ASSISTANT.getClassName().equals(ai.name)
                && ai.enabled
                && (ai.applicationInfo == null || ai.applicationInfo.enabled);
    }

    /** Context#startActivityAsUser(Intent, UserHandle) 为隐藏/系统 API，反射调用。 */
    private static void startActivityAsUser(Context ctx, Intent intent, int userId)
            throws Throwable {
        Method m = Context.class.getMethod("startActivityAsUser", Intent.class, UserHandle.class);
        try {
            m.invoke(ctx, intent, userHandleOf(userId));
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /** UserHandle.of(int) 不在公开 SDK 中，反射获取；再不行用 UserHandle.CURRENT。 */
    private static UserHandle userHandleOf(int userId) throws Throwable {
        try {
            return (UserHandle) UserHandle.class.getMethod("of", int.class).invoke(null, userId);
        } catch (NoSuchMethodException e) {
            return (UserHandle) UserHandle.class.getField("CURRENT").get(null);
        }
    }

    /**
     * IWindowManager#dismissKeyguard(IKeyguardDismissCallback, CharSequence)：
     * 显示锁屏验证界面（密码 / 指纹 / 人脸），只是「请求解锁」，不会跳过验证。
     */
    private static void requestDismissKeyguard() {
        try {
            Object wms = Class.forName("android.view.WindowManagerGlobal")
                    .getMethod("getWindowManagerService").invoke(null);
            if (wms == null) return;
            for (Method m : wms.getClass().getMethods()) {
                if ("dismissKeyguard".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    m.invoke(wms, null, null);
                    return;
                }
            }
        } catch (Throwable t) {
            Log.e("dismissKeyguard failed (ignored)", t);
        }
    }
}
