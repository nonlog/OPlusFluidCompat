package io.github.nonlog.oplusfluidcompat;

import org.junit.Test;
import static org.junit.Assert.*;

public class AmapCompatibilityPolicyTest {
    @Test public void realNavigationIsAccepted() {
        assertTrue(AmapCompatibilityPolicy.isNavigation(
                AmapCompatibilityPolicy.PACKAGE, "navigation", null, true, false));
    }

    @Test public void routeChannelIsAccepted() {
        assertTrue(AmapCompatibilityPolicy.isNavigation(AmapCompatibilityPolicy.PACKAGE,
                null, AmapCompatibilityPolicy.ROUTE_CHANNEL, true, false));
    }

    @Test public void otherPackagesAreNeverAccepted() {
        for (String pkg : new String[]{null, "com.sankuai.meituan", "com.autonavi.minimap.fake"}) {
            assertFalse(AmapCompatibilityPolicy.isNavigation(pkg, "navigation",
                    AmapCompatibilityPolicy.ROUTE_CHANNEL, true, false));
        }
    }

    @Test public void nonNavigationAndNonForegroundNotificationsAreRejected() {
        assertFalse(AmapCompatibilityPolicy.isNavigation(AmapCompatibilityPolicy.PACKAGE,
                "message", "other", true, false));
        assertFalse(AmapCompatibilityPolicy.isNavigation(AmapCompatibilityPolicy.PACKAGE,
                "navigation", AmapCompatibilityPolicy.ROUTE_CHANNEL, false, false));
    }

    @Test public void groupSummariesAreRejected() {
        assertFalse(AmapCompatibilityPolicy.isNavigation(AmapCompatibilityPolicy.PACKAGE,
                "navigation", AmapCompatibilityPolicy.ROUTE_CHANNEL, true, true));
    }

    @Test public void rusMatchUsesPackageBoundary() {
        assertTrue(AmapCompatibilityPolicy.isTargetRusId(AmapCompatibilityPolicy.RUS_ID));
        assertTrue(AmapCompatibilityPolicy.isTargetRusId(AmapCompatibilityPolicy.RUS_ID + "_route"));
        for (String id : new String[]{null, "", "laid_com.sankuai.meituan",
                AmapCompatibilityPolicy.RUS_ID + ".fake", AmapCompatibilityPolicy.RUS_ID + "2"}) {
            assertFalse(AmapCompatibilityPolicy.isTargetRusId(id));
        }
    }

    @Test public void nativeSurfaceMustNotSelectTemplateTypeTwo() {
        assertEquals(1, AmapCompatibilityPolicy.REMOTE_IMMERSIVE_TYPE);
        assertTrue(AmapCompatibilityPolicy.SERVICE_URI.contains(
                "component=com.autonavi.minimap/com.autonavi.minimap.immersenavi.AMapImmerseNaviService"));
    }

    @Test public void pluginMappedIdsAreRecognised() {
        // 99910001 is the observed AMap navigation prebuiltId advertised by the plugin.
        assertTrue(AmapCompatibilityPolicy.isPrebuiltLiveAlertId(99910001));
        assertTrue(AmapCompatibilityPolicy.isPrebuiltLiveAlertId(
                AmapCompatibilityPolicy.NOTIFICATION_ID_BASE + 1));
    }

    @Test public void unrelatedNotificationIdsAreRejected() {
        for (int id : new int[]{0, -1, 1001, 99910000,
                AmapCompatibilityPolicy.NOTIFICATION_ID_BASE + AmapCompatibilityPolicy.NOTIFICATION_ID_WINDOW}) {
            assertFalse("id " + id, AmapCompatibilityPolicy.isPrebuiltLiveAlertId(id));
        }
    }

    @Test public void liveAlertMatchNeedsNavigationAndPluginId() {
        assertTrue(AmapCompatibilityPolicy.isAmapLiveAlertNotification(
                AmapCompatibilityPolicy.PACKAGE, "navigation",
                AmapCompatibilityPolicy.ROUTE_CHANNEL, true, false, 99910001));
        // navigation, but not a plugin-mapped ID
        assertFalse(AmapCompatibilityPolicy.isAmapLiveAlertNotification(
                AmapCompatibilityPolicy.PACKAGE, "navigation",
                AmapCompatibilityPolicy.ROUTE_CHANNEL, true, false, 1001));
        // plugin ID, but not navigation
        assertFalse(AmapCompatibilityPolicy.isAmapLiveAlertNotification(
                AmapCompatibilityPolicy.PACKAGE, "message", "other", true, false, 99910001));
        assertFalse(AmapCompatibilityPolicy.isAmapLiveAlertNotification(
                "com.sankuai.meituan", "navigation",
                AmapCompatibilityPolicy.ROUTE_CHANNEL, true, false, 99910001));
    }
}
