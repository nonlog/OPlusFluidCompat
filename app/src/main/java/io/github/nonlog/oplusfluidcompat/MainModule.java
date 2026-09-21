package io.github.nonlog.oplusfluidcompat;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

public final class MainModule extends XposedModule {
    private static final String TAG = "OPlusFluidCompat";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String AMAP_PACKAGE = "com.autonavi.minimap";
    private static final String AMAP_ROUTE_CHANNEL = "ROUTE_CHANNEL_ID";
    private static final String AMAP_LIVE_ALERT_SERVICE_URI =
            "intent:#Intent;action=com.amap.minimap.immersenavi.AMapImmerseNaviService;"
                    + "component=com.autonavi.minimap/com.autonavi.minimap.immersenavi.AMapImmerseNaviService;end";

    private static final String KEY_LIVE_ALERT_SERVICE = "liveAlertService";
    private static final String KEY_IMMERSIVE_CARD_TYPE = "immersiveCardType";
    private static final int IMMERSIVE_CARD_TYPE_SURFACE = 2;

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
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                StatusBarNotification sbn = getStatusBarNotification(chain.getArg(0));
                if (isAmapNavigation(sbn)) {
                    logOnce("amap-eligibility", "allow native Live Alert eligibility for AMap navigation");
                    return true;
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
     * ColorOS supplies the native immersive-service mapping as part of its Live Alert registration
     * data. OxygenOS lacks the production AMap entry even though AMap declares
     * livealert.immersive.card=1 and exports AMapImmerseNaviService. Restore only those missing
     * metadata fields. The OPlus IntentMessenger still performs the service bind and AMap itself
     * renders the SurfacePackage; this does not synthesize a generic Live Alert UI.
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
                if (isAmapNavigation(sbn)) {
                    Notification notification = sbn.getNotification();
                    Bundle extras = notification != null ? notification.extras : null;
                    if (extras != null) {
                        String existingService = extras.getString(KEY_LIVE_ALERT_SERVICE, "");
                        if (existingService == null || existingService.isEmpty()) {
                            extras.putString(KEY_LIVE_ALERT_SERVICE, AMAP_LIVE_ALERT_SERVICE_URI);
                        }
                        if (extras.getInt(KEY_IMMERSIVE_CARD_TYPE, -1) == -1) {
                            extras.putInt(KEY_IMMERSIVE_CARD_TYPE, IMMERSIVE_CARD_TYPE_SURFACE);
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
        if (sbn == null || !AMAP_PACKAGE.equals(sbn.getPackageName())) return false;
        Notification notification = sbn.getNotification();
        if (notification == null) return false;
        return Notification.CATEGORY_NAVIGATION.equals(notification.category)
                || AMAP_ROUTE_CHANNEL.equals(notification.getChannelId());
    }

    private void logOnce(String key, String message) {
        if (loggedEvents.add(key)) log(Log.INFO, TAG, message);
    }
}
