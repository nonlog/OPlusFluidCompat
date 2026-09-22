package io.github.nonlog.oplusfluidcompat;

import android.app.Application;
import android.os.Build;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Native regional decisions which are independent of the property getter layer. */
final class NativeRuntimeHooks {
    private static final String TAG = "OPlusFluidCompat";
    private final XposedModule module;
    private final Set<String> logged = ConcurrentHashMap.newKeySet();

    NativeRuntimeHooks(XposedModule module) {
        this.module = module;
    }

    void installSystemUi(ClassLoader loader) {
        if (!ChinaCompatibilityPolicy.supportsFlashbackProfile(Build.VERSION.SDK_INT, Build.DISPLAY)) {
            logOnce("unsupported", "unverified SystemUI build; native regional profile left unchanged");
            return;
        }
        try {
            Class<?> flavors = Class.forName("com.oplus.flashback.manager.FlavorsHelper", false, loader);
            Field export = flavors.getDeclaredField("isExpRegion");
            if (export.getType() != boolean.class || !Modifier.isStatic(export.getModifiers())) {
                throw new NoSuchFieldException("FlavorsHelper.isExpRegion schema");
            }
            Method load = Class.forName("com.oplus.flashback.FlashBackModule", false, loader)
                    .getDeclaredMethod("load", Application.class, boolean.class, int.class,
                            boolean.class, boolean.class);
            Method setFlavor = flavors.getDeclaredMethod("setFlavor", int.class, boolean.class);
            if (load.getReturnType() != void.class || setFlavor.getReturnType() != void.class
                    || !Modifier.isStatic(setFlavor.getModifiers())) {
                throw new NoSuchMethodException("FlashBack regional profile schema");
            }
            // Change the argument before it is captured by the native asynchronous initializer.
            // Changing a displayed region string after initialization does not run this branch.
            module.hook(load).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                boolean wasExport = Boolean.TRUE.equals(args[3]);
                args[3] = false;
                logOnce("load", "native FlashBack initialization: export=" + wasExport
                        + " -> false; flavor=" + args[2] + "; feature/tablet flags unchanged");
                return chain.proceed(args);
            });
            // Preserve the device-family support mask and all native RUS parsing/security logic.
            module.hook(setFlavor).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                args[1] = false;
                Object result = chain.proceed(args);
                logOnce("flavor", "native FlashBack effective region: CN; flavor=" + args[0]);
                return result;
            });
            logOnce("installed", "native FlashBack regional hooks installed: 2/2");
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "native regional profile unavailable", error);
        }
    }

    void installUmsDiagnostics(ClassLoader loader) {
        try {
            Class<?> connect = Class.forName(
                    "com.pantanal.server.connect.support.ConnectManager", false, loader);
            int count = 0;
            for (Method method : connect.getDeclaredMethods()) {
                String name = method.getName();
                if (!ChinaCompatibilityPolicy.isNativeDiscoveryMethod(name)) continue;
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    logOnce("discovery-" + name, "native UMS discovery invoked: " + name
                            + "; original implementation retained (no fabricated service result)");
                    return result;
                });
                count++;
            }
            logOnce("ums-diagnostics", "native UMS discovery diagnostics installed: " + count);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "UMS discovery diagnostics unavailable; behavior unchanged", error);
        }
    }

    private void logOnce(String key, String message) {
        if (logged.add(key)) module.log(Log.INFO, TAG, message);
    }
}
