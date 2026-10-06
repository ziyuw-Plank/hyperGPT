# LSPosed 通过 assets/xposed_init 按类名反射加载入口类，必须保留类名与无参构造。
-keep class io.github.zyw.powergpt.MainHook { <init>(); *; }
# 保留实现的 Xposed 接口方法签名
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage {
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}
# Xposed API 是 compileOnly，不在 APK 中
-dontwarn de.robv.android.xposed.**
# 日志里保留可读的类名，方便排查
-keepattributes SourceFile,LineNumberTable
-dontobfuscate
# Hook 回调类原样保留，不做类合并 / 优化（system_server 内运行，宁可稳妥）
-keep class * extends de.robv.android.xposed.XC_MethodHook { *; }
-dontoptimize
