package io.github.nonlog.oplusfluidcompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

public final class MainModule extends XposedModule {
    private static final String TAG = "OPlusFluidCompat";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String FLASH_PERMISSION = "com.oplus.flashback.permission.FLASH_VIEWS_SERVICE";

    private static volatile Context systemUiContext;
    private static final Set<String> loggedPackages = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!SYSTEM_UI.equals(param.getPackageName())) return;
        installSystemUiHooks(param.getClassLoader());
    }

    private void installSystemUiHooks(ClassLoader cl) {
        int installed = 0;
        installed += hookValidCaller(cl) ? 1 : 0;
        installed += hookSupportByPackage(cl) ? 1 : 0;
        installed += hookSupportByUid(cl) ? 1 : 0;
        log(Log.INFO, TAG, "SystemUI hooks installed: " + installed + "/3");
    }

    private boolean hookValidCaller(ClassLoader cl) {
        try {
            Class<?> cls = Class.forName("com.oplus.flashback.settings.utils.SettingsUtils", false, cl);
            Method method = cls.getDeclaredMethod("isValidCaller", Context.class, String.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Context context = (Context) chain.getArg(0);
                String pkg = (String) chain.getArg(1);
                rememberContext(context);
                if (isEligible(context, pkg)) { logUnlocked(pkg, "caller certification"); return true; }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked SettingsUtils.isValidCaller");
            return true;
        } catch (Throwable t) { log(Log.ERROR, TAG, "failed to hook SettingsUtils.isValidCaller", t); return false; }
    }

    private boolean hookSupportByPackage(ClassLoader cl) {
        try {
            Class<?> cls = Class.forName("com.oplus.flashback.manager.ConfigurationManager", false, cl);
            Method method = cls.getDeclaredMethod("isSupportFlashViews", String.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                String pkg = (String) chain.getArg(0);
                Context context = systemUiContext;
                if (context != null && isEligible(context, pkg)) { logUnlocked(pkg, "support list / region gate"); return true; }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked ConfigurationManager.isSupportFlashViews(String)");
            return true;
        } catch (Throwable t) { log(Log.ERROR, TAG, "failed to hook package support check", t); return false; }
    }

    private boolean hookSupportByUid(ClassLoader cl) {
        try {
            Class<?> cls = Class.forName("com.oplus.flashback.manager.ConfigurationManager", false, cl);
            Method method = cls.getDeclaredMethod("isSupportFlashViews", Context.class, int.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Context context = (Context) chain.getArg(0);
                int uid = (Integer) chain.getArg(1);
                rememberContext(context);
                if (hasEligiblePackageForUid(context, uid)) return true;
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked ConfigurationManager.isSupportFlashViews(Context,int)");
            return true;
        } catch (Throwable t) { log(Log.ERROR, TAG, "failed to hook uid support check", t); return false; }
    }

    private static void rememberContext(Context context) {
        if (context != null) { Context app = context.getApplicationContext(); systemUiContext = app != null ? app : context; }
    }

    private boolean hasEligiblePackageForUid(Context context, int uid) {
        if (context == null) return false;
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages == null) return false;
            for (String pkg : packages) if (isEligible(context, pkg)) { logUnlocked(pkg, "uid support gate"); return true; }
        } catch (Throwable t) { log(Log.WARN, TAG, "uid eligibility check failed: " + t); }
        return false;
    }

    private boolean isEligible(Context context, String pkg) {
        if (context == null || pkg == null || pkg.isEmpty() || SYSTEM_UI.equals(pkg)) return false;
        try { return context.getPackageManager().checkPermission(FLASH_PERMISSION, pkg) == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { log(Log.WARN, TAG, "permission check failed for " + pkg + ": " + t); return false; }
    }

    private void logUnlocked(String pkg, String gate) {
        if (pkg != null && loggedPackages.add(pkg + "@" + gate)) log(Log.INFO, TAG, "unlock " + gate + " for " + pkg);
    }
}
