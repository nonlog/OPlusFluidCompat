package io.github.nonlog.oplusfluidcompat;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

public final class MainModule extends XposedModule {
    private static final String TAG = "OPlusFluidCompat";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String PLUGIN_PACKAGE = "com.oplus.systemui.plugins";
    private static final String PLUGIN_SHA256 =
            "d7c0a5dc11f40e7c89b2687a5a89a7db1fa2365ddb3fb3110214b2e7f19862db";
    private static final String AMAP_LIVE_ALERT_SERVICE_URI = AmapCompatibilityPolicy.SERVICE_URI;

    private static final String KEY_LIVE_ALERT_SERVICE = "liveAlertService";
    private static final String KEY_IMMERSIVE_CARD_TYPE = "immersiveCardType";
    private final Set<ClassLoader> examinedLoaders = ConcurrentHashMap.newKeySet();
    private final Set<ClassLoader> nativeLoaders = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Boolean> suppressCompatibilityRus = ThreadLocal.withInitial(() -> false);

    private static final Set<String> loggedEvents =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

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
        installed += hookLiveAlertEligibility(cl) ? 1 : 0;
        installed += hookAmapNativeImmersiveMetadata(cl) ? 1 : 0;
        log(Log.INFO, TAG, "native Live Alert hooks installed: " + installed + "/2");
    }

    /**
     * OxygenOS asks the dynamically loaded Seedling plugin whether each notification is allowed
     * to become a Live Alert. Its RUS table contains com.amap.navi.demo on the tested ROM but not
     * the production AMap package. Bypass only that missing package entry, and only for AMap's
     * real navigation foreground notification.
     */
    private boolean hookLiveAlertEligibility(ClassLoader cl) {
        try {
            Class<?> entryClass = Class.forName(
                    "com.android.systemui.statusbar.notification.collection.NotificationEntry",
                    false,
                    cl);
            Class<?> filterClass = Class.forName(
                    "com.oplus.systemui.statusbar.notification.livealert.data.repository.OplusLiveAlertFilterByPlugin",
                    false,
                    cl);
            Method method = filterClass.getDeclaredMethod("shouldFilter", entryClass);
            Field pluginField = filterClass.getDeclaredField("plugin");
            pluginField.setAccessible(true);
            Class<?> pluginInterface = Class.forName(
                    "com.android.systemui.plugins.statusbar.SeedlingPlugin", false, cl);
            Constructor<?> constructor = filterClass.getDeclaredConstructor(pluginInterface);
            hook(constructor).intercept(chain -> {
                Object plugin = chain.getArg(0);
                if (plugin != null) installSeedlingHooks(plugin.getClass().getClassLoader());
                return chain.proceed();
            });
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                StatusBarNotification sbn = getStatusBarNotification(chain.getArg(0));
                if (isAmapLiveAlertNotification(sbn)) {
                    Object plugin = pluginField.get(chain.getThisObject());
                    ClassLoader loader = plugin == null ? null : plugin.getClass().getClassLoader();
                    installSeedlingHooks(loader);
                    if (loader == null || !nativeLoaders.contains(loader)) return chain.proceed();
                    logOnce("amap-eligibility", "allow native Live Alert eligibility for AMap navigation");
                    return true;
                }
                if (sbn != null && AmapCompatibilityPolicy.PACKAGE.equals(sbn.getPackageName())) {
                    // Do not let our fallback RUS profile promote unrelated or uninitialized notifications.
                    boolean previous = suppressCompatibilityRus.get();
                    suppressCompatibilityRus.set(true);
                    try {
                        return chain.proceed();
                    } finally {
                        suppressCompatibilityRus.set(previous);
                    }
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked OplusLiveAlertFilterByPlugin.shouldFilter");
            return true;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "failed to hook native Live Alert eligibility", t);
            return false;
        }
    }

    /**
     * Restore the mapping to the service AMap actually exports. The exact origin of the missing
     * metadata on China ROMs is not established. Native IntentMessenger, permission checks and
     * AMap's SurfacePackage renderer remain in charge; a generic capsule alone is not success.
     */
    private boolean hookAmapNativeImmersiveMetadata(ClassLoader cl) {
        try {
            Class<?> entryClass = Class.forName(
                    "com.android.systemui.statusbar.notification.collection.NotificationEntry",
                    false,
                    cl);
            Class<?> repositoryClass = Class.forName(
                    "com.oplus.systemui.statusbar.notification.livealert.data.repository.OplusLiveAlertNotificationsRepository",
                    false,
                    cl);
            Method method = repositoryClass.getDeclaredMethod(
                    "entryToLiveAlert", entryClass, int.class, boolean.class);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                StatusBarNotification sbn = getStatusBarNotification(chain.getArg(0));
                if (!nativeLoaders.isEmpty() && isAmapLiveAlertNotification(sbn)) {
                    Notification notification = sbn.getNotification();
                    Bundle extras = notification != null ? notification.extras : null;
                    if (extras != null) {
                        String existingService = extras.getString(KEY_LIVE_ALERT_SERVICE, "");
                        if (existingService == null || existingService.isEmpty()) {
                            extras.putString(KEY_LIVE_ALERT_SERVICE, AMAP_LIVE_ALERT_SERVICE_URI);
                        }
                        int type = extras.getInt(KEY_IMMERSIVE_CARD_TYPE, -1);
                        if (AMAP_LIVE_ALERT_SERVICE_URI.equals(extras.getString(KEY_LIVE_ALERT_SERVICE))
                                && (type == -1 || type == 2)) {
                            extras.putInt(KEY_IMMERSIVE_CARD_TYPE,
                                    AmapCompatibilityPolicy.REMOTE_IMMERSIVE_TYPE);
                        }
                        logOnce(
                                "amap-native-service",
                                "restore AMap native immersive service mapping: "
                                        + extras.getString(KEY_LIVE_ALERT_SERVICE, "")
                                        + ", immersiveCardType="
                                        + extras.getInt(KEY_IMMERSIVE_CARD_TYPE, -1));
                    }
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked OplusLiveAlertNotificationsRepository.entryToLiveAlert");
            return true;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "failed to hook AMap native immersive metadata", t);
            return false;
        }
    }

    /**
     * This profile is deliberately pinned to the APK actually inspected on the test device.
     * Obfuscated names are NOT assumed stable across ROM updates. Unknown APKs fail closed.
     * Hook discovery happens through the real Seedling instance, not SystemUI's parent loader.
     */
    private synchronized void installSeedlingHooks(ClassLoader loader) {
        if (loader == null || examinedLoaders.contains(loader)) return;
        try {
            Context application = (Context) Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication").invoke(null);
            if (application == null) {
                logOnce("application-pending", "application not ready; defer native profile lookup");
                return;
            }
            examinedLoaders.add(loader);
            String apk = application.getPackageManager()
                    .getApplicationInfo(PLUGIN_PACKAGE, 0).sourceDir;
            if (!PLUGIN_SHA256.equals(sha256(apk))) {
                log(Log.WARN, TAG, "unsupported Seedling APK; native profile not installed");
                return;
            }
            Class<?> managerClass = Class.forName("n5.o", false, loader);
            Class<?> dataClass = Class.forName("n5.f", false, loader);
            Method lookup = managerClass.getDeclaredMethod("f", String.class);
            if (lookup.getReturnType() != dataClass) throw new NoSuchMethodException("RUS lookup");
            Field mapField = managerClass.getDeclaredField("e");
            if (mapField.getType() != ConcurrentHashMap.class) {
                throw new NoSuchFieldException("RUS configuration map");
            }
            mapField.setAccessible(true);
            Class<?>[] types = {String.class, int.class, int.class, int.class, String.class,
                    int.class, int.class, int.class, int.class, int.class, int.class, long.class,
                    int.class, int.class, String.class, int.class, float.class, int.class,
                    int.class, List.class, int.class, int.class, int.class, int.class, Integer.class};
            Constructor<?> dataConstructor = dataClass.getDeclaredConstructor(types);
            dataConstructor.setAccessible(true);
            Field[] fields = new Field[types.length];
            for (int index = 0; index < fields.length; index++) {
                fields[index] = dataClass.getDeclaredField(String.valueOf((char) ('a' + index)));
                fields[index].setAccessible(true);
                if (fields[index].getType() != types[index]) {
                    throw new NoSuchFieldException("RUS schema at index " + index);
                }
            }
            Object[] cache = new Object[2]; // last immutable template and its package-specific copy
            hook(lookup).intercept(chain -> {
                Object existing = chain.proceed();
                if (existing != null || suppressCompatibilityRus.get()
                        || !AmapCompatibilityPolicy.isTargetRusId((String) chain.getArg(0))) {
                    return existing;
                }
                try {
                    Map<?, ?> configurations = (Map<?, ?>) mapField.get(chain.getThisObject());
                    Object template = configurations.get("laid_com.amap.navi.demo");
                    if (!dataClass.isInstance(template)) return existing;
                    synchronized (cache) {
                        if (cache[0] != template) {
                            Object[] args = new Object[fields.length];
                            for (int index = 0; index < fields.length; index++) {
                                args[index] = fields[index].get(template);
                            }
                            args[0] = AmapCompatibilityPolicy.RUS_ID;
                            // Copy the ROM's existing navigation profile; never mutate it or the DB.
                            cache[1] = dataConstructor.newInstance(args);
                            cache[0] = template;
                            logOnce("amap-rus", "restored missing AMap native navigation RUS profile; "
                                    + "lockImmersiveEnable=" + args[22]
                                    + ", lockImmersiveDefault=" + args[23]);
                        }
                        return cache[1];
                    }
                } catch (Throwable error) {
                    logOnce("amap-rus-failed", "native RUS profile unavailable: " + error);
                    return existing;
                }
            });
            nativeLoaders.add(loader);
            log(Log.INFO, TAG, "Seedling 16.001.002 fingerprint and RUS schema verified; hook installed");
            installNativeInitialMode(loader);
            installSurfaceDiagnostics(loader);
        } catch (Throwable error) {
            examinedLoaders.add(loader);
            log(Log.ERROR, TAG, "native Seedling profile not installed", error);
        }
    }

    /** Prefer the native map on initial creation, without editing saved display preferences. */
    private void installNativeInitialMode(ClassLoader loader) {
        try {
            Class<?> infoClass = Class.forName("o5.j", false, loader);
            Method initialMode = Class.forName("w5.c", false, loader)
                    .getDeclaredMethod("c", infoClass);
            if (initialMode.getReturnType() != int.class) throw new NoSuchMethodException("initial mode");
            Method pkg = infoClass.getMethod("f");
            Method uri = infoClass.getDeclaredMethod("B");
            Method defaultEnabled = infoClass.getDeclaredMethod("C");
            Method supported = infoClass.getDeclaredMethod("y");
            Method permission = infoClass.getDeclaredMethod("M");
            hook(initialMode).intercept(chain -> {
                Object original = chain.proceed();
                Object info = chain.getArg(0);
                try {
                    if (AmapCompatibilityPolicy.PACKAGE.equals(pkg.invoke(info))
                            && AMAP_LIVE_ALERT_SERVICE_URI.equals(uri.invoke(info))
                            && Boolean.TRUE.equals(defaultEnabled.invoke(info))
                            && Boolean.TRUE.equals(supported.invoke(info))
                            && Boolean.TRUE.equals(permission.invoke(info))) {
                        logOnce("native-initial-mode", "prefer ready AMap native map on initial card creation; "
                                + "saved preferences untouched");
                        // This method returns a request flag, NOT the persisted MODE_IMMERSIVE ordinal (2).
                        // Verified call site: NotificationController enters immersion when c(info) == 0.
                        return 0;
                    }
                } catch (Throwable error) {
                    logOnce("native-initial-mode-error", "native initial mode unavailable: " + error.getClass().getName());
                }
                return original;
            });
        } catch (Throwable error) {
            log(Log.WARN, TAG, "native initial mode unavailable", error);
        }
    }

    private void installSurfaceDiagnostics(ClassLoader loader) {
        try {
            Method bind = Class.forName("z5.h", false, loader)
                    .getDeclaredMethod("c", String.class, Bundle.class, IBinder.class);
            hook(bind).intercept(chain -> {
                String key = (String) chain.getArg(0);
                if (isAmapCardKey(key)) {
                    logOnce("amap-host-token", "native IntentMessenger sends AMap host token (message 11)");
                }
                return chain.proceed();
            });
            Class<?> receiverClass = Class.forName("z5.b", false, loader);
            Field hostReference = receiverClass.getDeclaredField("b");
            hostReference.setAccessible(true);
            Field serviceUri = Class.forName("z5.h", false, loader).getDeclaredField("c");
            serviceUri.setAccessible(true);
            Method receive = receiverClass.getDeclaredMethod("handleMessage", Message.class);
            hook(receive).intercept(chain -> {
                Message message = (Message) chain.getArg(0);
                if (message != null && message.what == 21) {
                    Bundle data = message.getData();
                    try {
                        Object reference = hostReference.get(chain.getThisObject());
                        Object host = reference instanceof WeakReference ? ((WeakReference<?>) reference).get() : null;
                        if (host != null && AMAP_LIVE_ALERT_SERVICE_URI.equals(serviceUri.get(host))) {
                            logOnce("amap-surface-reply", "AMap returned native surface (message 21), "
                                    + "hasSurfacePackage=" + data.containsKey("SurfacePackage"));
                        }
                    } catch (Throwable error) {
                        logOnce("surface-diagnostic-error", "surface diagnostic: " + error.getClass().getName());
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            log(Log.WARN, TAG, "surface diagnostics unavailable (native behavior unchanged)", error);
        }
    }

    private static boolean isAmapCardKey(String key) {
        return key != null && key.contains("|" + AmapCompatibilityPolicy.PACKAGE + "|");
    }

    private static String sha256(String file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString();
    }

    private StatusBarNotification getStatusBarNotification(Object notificationEntry) {
        if (notificationEntry == null) return null;
        try {
            Method getSbn = notificationEntry.getClass().getMethod("getSbn");
            Object value = getSbn.invoke(notificationEntry);
            return value instanceof StatusBarNotification ? (StatusBarNotification) value : null;
        } catch (Throwable t) {
            logOnce("get-sbn-failed", "failed to read NotificationEntry.getSbn: " + t);
            return null;
        }
    }

    private static boolean isAmapNavigation(StatusBarNotification sbn) {
        if (sbn == null) return false;
        Notification notification = sbn.getNotification();
        if (notification == null) return false;
        return AmapCompatibilityPolicy.isNavigation(sbn.getPackageName(), notification.category,
                notification.getChannelId(),
                (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0,
                (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0);
    }

    /**
     * AMap navigation that the plugin has already mapped to a live alert. The prebuilt ID comes
     * from the plugin's own mapping, so this needs nothing from AMap's process and therefore no
     * module scope inside com.autonavi.minimap.
     */
    private static boolean isAmapLiveAlertNotification(StatusBarNotification sbn) {
        if (!isAmapNavigation(sbn)) return false;
        Notification notification = sbn.getNotification();
        return AmapCompatibilityPolicy.isAmapLiveAlertNotification(sbn.getPackageName(),
                notification.category, notification.getChannelId(),
                (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0,
                (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0,
                sbn.getId());
    }

    private void logOnce(String key, String message) {
        if (loggedEvents.add(key)) log(Log.INFO, TAG, message);
    }
}
