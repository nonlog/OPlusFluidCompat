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

    static boolean isNavigation(String pkg, String category, String channel,
                                boolean foregroundService, boolean groupSummary) {
        return PACKAGE.equals(pkg) && foregroundService && !groupSummary
                && ("navigation".equals(category) || ROUTE_CHANNEL.equals(channel));
    }

    static boolean isTargetRusId(String id) {
        return id != null && (RUS_ID.equals(id) || id.startsWith(RUS_ID + "_"));
    }

    private AmapCompatibilityPolicy() {}
}
