package io.github.nonlog.oplusfluidcompat;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedModule;

/** Process-local identity compatibility; does not manufacture services or grant permissions. */
final class ChinaIdentityHooks {
    private static final String TAG = "OPlusFluidCompat";
    private static final String SYSTEM_UI = "com.android.systemui";
    private final XposedModule module;
    private final String processPackage;
    private final Set<String> loggedEvents = ConcurrentHashMap.newKeySet();
    private volatile boolean chinaIdentityInstalled;

    ChinaIdentityHooks(XposedModule module, String processName) {
        this.module = module;
        this.processPackage = ChinaCompatibilityPolicy.packageOfProcess(processName);
    }

    void install() {
        if (ChinaCompatibilityPolicy.isNativeHost(processPackage)) installChinaIdentityHooks();
        if (ChinaCompatibilityPolicy.isNativeHost(processPackage)
                || ChinaCompatibilityPolicy.isClientCandidate(processPackage)) installApplicationProbe();
    }

    /**
     * Native clients are discovered from their own manifest, not a hard-coded AMap allowlist.
     * LSPosed scope remains the user's opt-in boundary. No android/system_server hook exists.
     */
    private void installApplicationProbe() {
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            module.hook(attach).intercept(chain -> {
                Context context = (Context) chain.getArg(0);
                try {
                    if (context != null && processPackage.equals(context.getPackageName())) {
                        if (!chinaIdentityInstalled && declaresNativeIntegration(context)) {
                            installChinaIdentityHooks();
                        }
                        if (ChinaCompatibilityPolicy.UMS.equals(processPackage)) {
                            auditNativeProviders(context);
                        }
                    }
                } catch (Throwable error) {
                    logOnce("manifest-probe-error", "native declaration probe failed: "
                            + error.getClass().getSimpleName());
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "application probe unavailable; client identity left unchanged", error);
        }
    }

    private boolean declaresNativeIntegration(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(processPackage,
                PackageManager.GET_META_DATA | PackageManager.GET_PROVIDERS | PackageManager.GET_PERMISSIONS);
        Bundle metadata = info.applicationInfo == null ? null : info.applicationInfo.metaData;
        Object immersiveValue = metadata == null ? null : metadata.get("livealert.immersive.card");
        boolean immersive = "1".equals(String.valueOf(immersiveValue))
                || Boolean.TRUE.equals(immersiveValue);
        Object auth = metadata == null ? null : metadata.get("com.oplus.ocs.card.AUTH_CODE");
        boolean cardAuth = auth instanceof String && !((String) auth).isEmpty();
        boolean flashViews = false;
        if (info.requestedPermissions != null) {
            for (String permission : info.requestedPermissions) {
                if ("com.oplus.flashback.permission.FLASH_VIEWS_SERVICE".equals(permission)) flashViews = true;
            }
        }
        boolean seedling = false;
        boolean fanZai = false;
        if (info.providers != null) {
            for (ProviderInfo provider : info.providers) {
                Object descriptor = provider.metaData == null ? null
                        : provider.metaData.get("oplus.seedling.provider");
                if (descriptor instanceof String && !((String) descriptor).isEmpty()) seedling = true;
                if (provider.authority != null && provider.authority.endsWith(".oppofanzaiprovider")) fanZai = true;
            }
        }
        boolean eligible = ChinaCompatibilityPolicy.hasNativeDeclaration(
                immersive, cardAuth, seedling, flashViews, fanZai);
        // Only booleans are recorded: never log registration credentials, routes or order data.
        logOnce("native-declarations", "native client declarations: immersive=" + immersive
                + " cardAuth=" + cardAuth + " seedling=" + seedling
                + " flashViews=" + flashViews + " fanZai=" + fanZai + " eligible=" + eligible);
        return eligible;
    }

    private boolean isNativeIdentityCall() {
        if (!SYSTEM_UI.equals(processPackage)) return true;
        // SystemUI hosts many unrelated features. Do not change its global skin/GMS decisions.
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (ChinaCompatibilityPolicy.isNativeFrame(frame.getClassName())) return true;
        }
        return false;
    }

    private synchronized void installChinaIdentityHooks() {
        if (chinaIdentityInstalled) return;
        if (!ChinaCompatibilityPolicy.isNativeHost(processPackage)
                && !ChinaCompatibilityPolicy.isClientCandidate(processPackage)) return;
        if (!ChinaCompatibilityPolicy.isOplusManufacturer(Build.MANUFACTURER)) {
            logOnce("not-oplus", "OPlus framework unavailable; China identity disabled");
            return;
        }
        chinaIdentityInstalled = true;
        int properties = 0;
        int features = 0;
        int metadata = 0;
        try {
            Class<?> propertiesClass = Class.forName("android.os.SystemProperties");
            for (Method method : propertiesClass.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (!method.getName().equals("get") || method.getReturnType() != String.class
                        || !Modifier.isStatic(method.getModifiers())
                        || (params.length != 1 && params.length != 2) || params[0] != String.class) continue;
                module.hook(method).intercept(chain -> {
                    Object original = chain.proceed();
                    String key = (String) chain.getArg(0);
                    if (ChinaCompatibilityPolicy.isRegionKey(key) && isNativeIdentityCall()) {
                        logOnce("region-" + key, "China identity property " + key + ": " + original + " -> CN");
                        return "CN";
                    }
                    return original;
                });
                properties++;
            }
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "region getter hooks incomplete", error);
        }
        for (String name : new String[]{"android.app.ApplicationPackageManager",
                "com.oplus.content.OplusFeatureConfigManager"}) {
            try {
                for (Method method : Class.forName(name).getDeclaredMethods()) {
                    Class<?>[] params = method.getParameterTypes();
                    if ((method.getName().equals("hasSystemFeature") || method.getName().equals("hasFeature"))
                            && method.getReturnType() == boolean.class
                            && params.length > 0 && params[0] == String.class) {
                        module.hook(method).intercept(chain -> {
                            Object original = chain.proceed();
                            String feature = (String) chain.getArg(0);
                            if (ChinaCompatibilityPolicy.isExportFeature(feature) && isNativeIdentityCall()) {
                                logOnce("feature-" + feature, "China identity feature " + feature + ": " + original + " -> false");
                                return false;
                            }
                            return original;
                        });
                        features++;
                    }
                    if (name.equals("android.app.ApplicationPackageManager")
                            && (method.getName().equals("getApplicationInfo")
                            || method.getName().equals("getApplicationInfoAsUser"))
                            && method.getReturnType() == ApplicationInfo.class
                            && params.length > 0 && params[0] == String.class) {
                        module.hook(method).intercept(chain -> {
                            Object original = chain.proceed();
                            if (!(original instanceof ApplicationInfo)
                                    || !ChinaCompatibilityPolicy.isExportMetadataTarget((String) chain.getArg(0))
                                    || !isNativeIdentityCall()) return original;
                            ApplicationInfo info = (ApplicationInfo) original;
                            if (info.metaData == null || !Boolean.TRUE.equals(info.metaData.get("IS_EXPORT"))) return original;
                            ApplicationInfo copy = new ApplicationInfo(info);
                            copy.metaData = new Bundle(info.metaData);
                            copy.metaData.putBoolean("IS_EXPORT", false);
                            logOnce("export-metadata", "native UMS IS_EXPORT metadata: true -> false (copy only)");
                            return copy;
                        });
                        metadata++;
                    }
                }
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "identity hooks incomplete for " + name, error);
            }
        }
        module.log(Log.INFO, TAG, "China identity hooks: properties=" + properties + " features="
                + features + " metadata=" + metadata + "; no permission/authentication changes");
    }

    private void auditNativeProviders(Context context) {
        if (loggedEvents.contains("ums-provider-audit")) return;
        try {
            List<ResolveInfo> providers = context.getPackageManager().queryIntentContentProviders(
                    new Intent("com.oplus.seedling.action.SEEDLING_CARD"), PackageManager.GET_META_DATA);
            int descriptors = 0;
            for (ResolveInfo resolved : providers) {
                ProviderInfo provider = resolved.providerInfo;
                if (provider != null && provider.metaData != null
                        && provider.metaData.containsKey("oplus.seedling.provider")) descriptors++;
            }
            String[] exportAssets = context.getAssets().list("export_tier");
            logOnce("ums-provider-audit", "UMS native provider audit: discovered=" + providers.size()
                    + " withDescriptor=" + descriptors + " exportAssetTiers="
                    + (exportAssets == null ? 0 : exportAssets.length)
                    + "; compiled export branches and missing service descriptors are not fabricated");
        } catch (Throwable error) {
            logOnce("ums-provider-audit-error", "UMS provider audit failed: " + error.getClass().getSimpleName());
        }
    }

    private void logOnce(String key, String message) {
        if (loggedEvents.add(key)) module.log(Log.INFO, TAG, message);
    }
}
