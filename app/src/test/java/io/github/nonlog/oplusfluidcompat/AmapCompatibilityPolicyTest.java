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
}
