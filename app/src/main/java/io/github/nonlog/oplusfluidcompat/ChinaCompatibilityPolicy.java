package io.github.nonlog.oplusfluidcompat;

import java.util.Set;

/** Decisions for the process-local China identity layer, independent of Android APIs. */
public final class ChinaCompatibilityPolicy {
    private ChinaCompatibilityPolicy() {}

    public static final String UMS = "com.oplus.pantanal.ums";
    private static final Set<String> HOSTS = Set.of(
            "com.android.systemui", UMS, "com.coloros.sceneservice",
            "com.coloros.assistantscreen", "com.oplus.ambient.livealert");
    private static final Set<String> REGION_KEYS = Set.of(
            "ro.oplus.regionmark", "ro.vendor.oplus.regionmark", "ro.oplus.pipeline.region");
    private static final Set<String> EXPORT_FEATURES = Set.of(
            "oppo.version.exp", "com.oneplus.software.oos", "com.oneplus.software.overseas",
            "com.oplus.software.overseas.afteroos113", "oplus.software.support_gp.region_export");

    public static String packageOfProcess(String process) {
        if (process == null) return "";
        int separator = process.indexOf(':');
        return separator < 0 ? process : process.substring(0, separator);
    }

    public static boolean isNativeHost(String packageName) {
        return packageName != null && HOSTS.contains(packageName);
    }

    public static boolean isOplusManufacturer(String manufacturer) {
        return "OnePlus".equalsIgnoreCase(manufacturer) || "OPPO".equalsIgnoreCase(manufacturer)
                || "realme".equalsIgnoreCase(manufacturer) || "OPlus".equalsIgnoreCase(manufacturer);
    }

    /** This is only a candidate; its manifest must also declare an OPlus integration. */
    public static boolean isClientCandidate(String packageName) {
        if (packageName == null || !packageName.contains(".")) return false;
        for (String prefix : new String[]{"android.", "com.android.", "com.google.",
                "com.oplus.", "com.coloros.", "com.heytap.", "io.github.nonlog.oplusfluidcompat"}) {
            if (packageName.startsWith(prefix)) return false;
        }
        return true;
    }

    public static boolean isRegionKey(String key) {
        return key != null && REGION_KEYS.contains(key);
    }

    public static boolean isExportFeature(String feature) {
        return feature != null && EXPORT_FEATURES.contains(feature);
    }

    public static boolean isExportMetadataTarget(String packageName) {
        return UMS.equals(packageName);
    }

    public static boolean isNativeFrame(String className) {
        if (className == null) return false;
        return className.startsWith("com.pantanal.")
                || className.startsWith("com.oplus.pantanal.")
                || className.startsWith("com.oplus.seedling.")
                || className.startsWith("com.oplus.ums.")
                || className.startsWith("com.oplus.flashback.")
                || className.startsWith("com.oplus.ambient.livealert.")
                || (className.startsWith("com.oplus.systemui.") && className.contains(".livealert."));
    }

    public static boolean hasNativeDeclaration(boolean immersive, boolean cardAuth,
            boolean seedlingProvider, boolean flashViewsPermission, boolean fanZaiProvider) {
        return immersive || cardAuth || seedlingProvider || flashViewsPermission || fanZaiProvider;
    }
}
