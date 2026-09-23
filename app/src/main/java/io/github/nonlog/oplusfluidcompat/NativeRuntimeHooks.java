package io.github.nonlog.oplusfluidcompat;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Native regional decisions which are independent of the property getter layer. */
final class NativeRuntimeHooks {
    private static final String TAG = "OPlusFluidCompat";
    private static final String FLASH_VIEWS_PERMISSION =
            "com.oplus.flashback.permission.FLASH_VIEWS_SERVICE";
    private static final String SEEDLING_ACTION = "com.oplus.seedling.action.SEEDLING_CARD";
    private static final String SEEDLING_METADATA = "oplus.seedling.provider";
    private static final String IMMERSIVE_METADATA = "livealert.immersive.card";
    private static final String CARD_AUTH_METADATA = "com.oplus.ocs.card.AUTH_CODE";
    private final XposedModule module;
    private final Set<String> logged = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Boolean> flashViewsClients = new ConcurrentHashMap<>();
    private volatile Boolean umsRuntimeSupported;

    NativeRuntimeHooks(XposedModule module) {
        this.module = module;
    }

    void installSystemUi(ClassLoader loader) {
        if (!ChinaCompatibilityPolicy.supportsFlashbackProfile(Build.VERSION.SDK_INT, Build.DISPLAY)) {
            logOnce("unsupported", "unverified SystemUI build; native regional profile left unchanged");
            return;
        }
        installFlashBackRegionalHooks(loader);
        installFlashViewsRegistrationBypass(loader);
    }

    private void installFlashBackRegionalHooks(ClassLoader loader) {
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

    private void installFlashViewsRegistrationBypass(ClassLoader loader) {
        try {
            Class<?> configuration = Class.forName(
                    "com.oplus.flashback.manager.ConfigurationManager", false, loader);
            Method supportByPackage = configuration.getDeclaredMethod("isSupportFlashViews", String.class);
            Method configForPackage = configuration.getDeclaredMethod("getInfoMayNull", String.class);
            Method validCaller = Class.forName(
                    "com.oplus.flashback.settings.utils.SettingsUtils", false, loader)
                    .getDeclaredMethod("isValidCaller", Context.class, String.class);

            module.hook(supportByPackage).intercept(chain -> {
                Object original = chain.proceed();
                if (Boolean.TRUE.equals(original)) return original;
                String packageName = (String) chain.getArg(0);
                Application application = currentApplication();
                if (application != null
                        && isEligibleFlashViewsClient(application, packageName, configForPackage)) {
                    logOnce("flash-support-" + packageName,
                            "bypass FlashViews support allowlist for declared client " + packageName);
                    return true;
                }
                return original;
            });

            module.hook(validCaller).intercept(chain -> {
                Object original = chain.proceed();
                if (Boolean.TRUE.equals(original)) return original;
                Context context = (Context) chain.getArg(0);
                String packageName = (String) chain.getArg(1);
                // Existing RUS entries already carry the expected signer fingerprints. Do not
                // turn a real signature mismatch into success. This hook only restores the
                // registration path for native clients that have no RUS entry at all.
                Object config = packageName == null ? null : configForPackage.invoke(null, packageName);
                if (config == null && context != null
                        && isStrongManifestFlashViewsClient(context, packageName)) {
                    logOnce("flash-caller-" + packageName,
                            "bypass missing FlashViews registration entry for declared client " + packageName);
                    return true;
                }
                return original;
            });
            logOnce("flash-bypass-installed", "native FlashViews registration hooks installed: 2/2");
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "native FlashViews registration hooks unavailable", error);
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
        installUmsRegistrationBypass(loader);
    }

    private void installUmsRegistrationBypass(ClassLoader loader) {
        try {
            Class<?> scanner = Class.forName("com.pantanal.server.content.scan.Scanner", false, loader);
            Method queryAccessPackages = scanner.getDeclaredMethod("h", String.class);
            Field applicationField = scanner.getDeclaredField("f19899b");
            applicationField.setAccessible(true);

            Class<?> packageEntry = Class.forName("ff.a", false, loader);
            Constructor<?> packageEntryConstructor = packageEntry.getDeclaredConstructor(
                    String.class, List.class, long.class, long.class, String.class, ResolveInfo.class);
            packageEntryConstructor.setAccessible(true);
            Field packageNameField = packageEntry.getDeclaredField("f22749a");
            packageNameField.setAccessible(true);

            Class<?> packageUtils = Class.forName("com.pantanal.server.common.utils.k", false, loader);
            Method updateTime = packageUtils.getDeclaredMethod("a", Application.class, String.class);
            Method versionCode = packageUtils.getDeclaredMethod("b", Application.class, String.class);
            Method versionName = packageUtils.getDeclaredMethod("c", Application.class, String.class);

            module.hook(queryAccessPackages).intercept(chain -> {
                Object original = chain.proceed();
                if (!(original instanceof List<?>)) return original;
                Application application = (Application) applicationField.get(null);
                if (application == null || !supportsUmsRuntime(application)) return original;

                ArrayList<Object> augmented = new ArrayList<>((List<?>) original);
                HashSet<String> existing = new HashSet<>();
                for (Object entry : augmented) {
                    Object packageName = packageNameField.get(entry);
                    if (packageName instanceof String) existing.add((String) packageName);
                }

                String requestedPackage = (String) chain.getArg(0);
                Intent intent = new Intent(SEEDLING_ACTION);
                if (requestedPackage != null) intent.setPackage(requestedPackage);
                List<ResolveInfo> providers = application.getPackageManager()
                        .queryIntentContentProviders(intent, PackageManager.GET_META_DATA);
                int added = 0;
                for (ResolveInfo resolved : providers) {
                    ProviderInfo provider = resolved == null ? null : resolved.providerInfo;
                    Bundle metadata = provider == null ? null : provider.metaData;
                    String descriptor = metadata == null ? null : metadata.getString(SEEDLING_METADATA);
                    String packageName = provider == null || provider.applicationInfo == null
                            ? null : provider.applicationInfo.packageName;
                    if (packageName == null || descriptor == null || existing.contains(packageName)) continue;

                    ArrayList<String> descriptorPaths = new ArrayList<>();
                    for (String path : descriptor.split(";")) {
                        if (ChinaCompatibilityPolicy.isSeedlingDescriptorPath(path)) {
                            descriptorPaths.add(path.trim());
                        }
                    }
                    long packageUpdateTime = ((Number) updateTime.invoke(null, application, packageName)).longValue();
                    long packageVersionCode = ((Number) versionCode.invoke(null, application, packageName)).longValue();
                    String packageVersionName = String.valueOf(versionName.invoke(null, application, packageName));
                    Object entry = packageEntryConstructor.newInstance(packageName, descriptorPaths,
                            packageUpdateTime, packageVersionCode, packageVersionName, resolved);
                    augmented.add(entry);
                    existing.add(packageName);
                    added++;
                    logOnce("ums-register-" + packageName,
                            "bypass UMS local-repository registration gate for native Seedling provider "
                                    + packageName + "; descriptors=" + descriptorPaths.size());
                }
                if (added == 0) return original;
                return augmented;
            });
            logOnce("ums-registration-installed", "native UMS Seedling registration hook installed");
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "UMS Seedling registration hook unavailable", error);
        }
    }

    private boolean supportsUmsRuntime(Application application) {
        Boolean cached = umsRuntimeSupported;
        if (cached != null) return cached;
        boolean supported = false;
        long version = -1L;
        try {
            PackageInfo info = application.getPackageManager()
                    .getPackageInfo(ChinaCompatibilityPolicy.UMS, 0);
            version = info.getLongVersionCode();
            supported = ChinaCompatibilityPolicy.supportsUmsRegistrationProfile(
                    Build.VERSION.SDK_INT, Build.DISPLAY, version);
        } catch (Throwable ignored) {
        }
        umsRuntimeSupported = supported;
        if (!supported) {
            logOnce("ums-profile-unsupported", "unverified UMS registration profile; version=" + version
                    + "; native scanner left unchanged");
        }
        return supported;
    }

    private boolean isEligibleFlashViewsClient(
            Context context, String packageName, Method configForPackage) {
        if (!declaresFlashViewsPermission(context, packageName)) return false;
        try {
            if (configForPackage.invoke(null, packageName) != null) return true;
        } catch (Throwable ignored) {
        }
        return isStrongManifestFlashViewsClient(context, packageName);
    }

    private boolean isStrongManifestFlashViewsClient(Context context, String packageName) {
        if (!declaresFlashViewsPermission(context, packageName)) return false;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_META_DATA | PackageManager.GET_PROVIDERS
                            | PackageManager.GET_PERMISSIONS);
            Bundle metadata = info.applicationInfo == null ? null : info.applicationInfo.metaData;
            if (metadata != null && (metadata.containsKey(IMMERSIVE_METADATA)
                    || metadata.containsKey(CARD_AUTH_METADATA))) return true;
            if (info.providers != null) {
                for (ProviderInfo provider : info.providers) {
                    if (provider == null) continue;
                    if (provider.metaData != null && provider.metaData.containsKey(SEEDLING_METADATA)) {
                        return true;
                    }
                    if (provider.authority != null
                            && provider.authority.endsWith(".oppofanzaiprovider")) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean declaresFlashViewsPermission(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return false;
        Boolean cached = flashViewsClients.get(packageName);
        if (cached != null) return cached;
        boolean declared = false;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_PERMISSIONS);
            ApplicationInfo applicationInfo = info.applicationInfo;
            if (applicationInfo == null || (applicationInfo.flags
                    & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                flashViewsClients.put(packageName, false);
                return false;
            }
            if (info.requestedPermissions != null) {
                for (String permission : info.requestedPermissions) {
                    if (FLASH_VIEWS_PERMISSION.equals(permission)) {
                        declared = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        flashViewsClients.put(packageName, declared);
        return declared;
    }

    private static Application currentApplication() {
        try {
            return (Application) Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void logOnce(String key, String message) {
        if (logged.add(key)) module.log(Log.INFO, TAG, message);
    }
}
