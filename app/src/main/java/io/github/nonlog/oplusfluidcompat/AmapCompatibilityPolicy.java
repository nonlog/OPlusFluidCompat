package io.github.nonlog.oplusfluidcompat;

/** Pure policy: no Android or ROM implementation details. */
final class AmapCompatibilityPolicy {
    static final String PACKAGE = "com.autonavi.minimap";
    static final String RUS_ID = "laid_" + PACKAGE;
    static final String ROUTE_CHANNEL = "ROUTE_CHANNEL_ID";
    static final String SERVICE_URI =
            "intent:#Intent;action=com.amap.minimap.immersenavi.AMapImmerseNaviService;"
                    + "component=com.autonavi.minimap/com.autonavi.minimap.immersenavi.AMapImmerseNaviService;end";

    // SystemUIPlugin 16.001.002: 1 is the remote immersive surface; 2 is a template card.
    static final int REMOTE_IMMERSIVE_TYPE = 1;

    // AMap 17.00.0.2009 posts its navigation foreground notification with a prebuilt ID.
    // The plugin assigns that prebuiltId when it maps the live alert, so the ID is what
    // identifies a live-alert-capable AMap notification without cooperating with AMap.
    // Observed driving ID is 99910001; the window leaves room for AMap's other modes.
    static final int NOTIFICATION_ID_BASE = 99910000;
    static final int NOTIFICATION_ID_WINDOW = 64;

    static boolean isNavigation(String pkg, String category, String channel,
                                boolean foregroundService, boolean groupSummary) {
        return PACKAGE.equals(pkg) && foregroundService && !groupSummary
                && ("navigation".equals(category) || ROUTE_CHANNEL.equals(channel));
    }

    static boolean isTargetRusId(String id) {
        return id != null && (RUS_ID.equals(id) || id.startsWith(RUS_ID + "_"));
    }

    /**
     * Identify an AMap navigation notification that the plugin already mapped to a live alert by
     * prebuilt ID, so compatibility applies without hooking AMap's own process.
     */
    static boolean isAmapLiveAlertNotification(String pkg, String category, String channel,
                                               boolean foregroundService, boolean groupSummary,
                                               int notificationId) {
        return isNavigation(pkg, category, channel, foregroundService, groupSummary)
                && isPrebuiltLiveAlertId(notificationId);
    }

    static boolean isPrebuiltLiveAlertId(int notificationId) {
        return notificationId > NOTIFICATION_ID_BASE
                && notificationId < NOTIFICATION_ID_BASE + NOTIFICATION_ID_WINDOW;
    }

    private AmapCompatibilityPolicy() {}
}
