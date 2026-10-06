package io.github.zyw.powergpt;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 入口（legacy Xposed API，见 assets/xposed_init）。
 *
 * 作用域约定：只在「系统框架」(packageName == "android" 且 processName == "android"，
 * 即 system_server) 中安装 Hook；即使用户误勾选了其它 App，也会在这里直接返回，不做任何事。
 */
public final class MainHook implements IXposedHookLoadPackage {

    /** 系统框架在 LSPosed 中对应的包名 / 进程名。 */
    private static final String SYSTEM_FRAMEWORK = "android";

    /** 防止同一进程内被重复安装。 */
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 严格限制作用域：非 system_server 一律立即返回。
        if (lpparam == null
                || !SYSTEM_FRAMEWORK.equals(lpparam.packageName)
                || !SYSTEM_FRAMEWORK.equals(lpparam.processName)) {
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            PowerKeyHook.install(lpparam.classLoader);
        } catch (Throwable t) {
            // 任何异常都只记录日志，绝不抛回 system_server（避免卡开机 / 软重启）。
            Log.e("install failed, module stays idle", t);
        }
    }
}
