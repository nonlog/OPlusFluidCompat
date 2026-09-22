package io.github.nonlog.oplusfluidcompat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChinaCompatibilityPolicyTest {
    @Test public void otherManufacturersAreNotSpoofed() {
        assertTrue(ChinaCompatibilityPolicy.isOplusManufacturer("OnePlus"));
        assertTrue(ChinaCompatibilityPolicy.isOplusManufacturer("oppo"));
        assertFalse(ChinaCompatibilityPolicy.isOplusManufacturer("Google"));
        assertFalse(ChinaCompatibilityPolicy.isOplusManufacturer(null));
    }

    @Test public void secondaryProcessesKeepTheirPackageBoundary() {
        assertEquals("com.sankuai.meituan", ChinaCompatibilityPolicy.packageOfProcess("com.sankuai.meituan:fanZaiProcess"));
        assertEquals("", ChinaCompatibilityPolicy.packageOfProcess(null));
    }

    @Test public void systemServerIsNeverATarget() {
        for (String name : new String[]{"android", "system", "system_server", null, ""}) {
            assertFalse(ChinaCompatibilityPolicy.isNativeHost(name));
            assertFalse(ChinaCompatibilityPolicy.isClientCandidate(name));
        }
    }

    @Test public void platformHostNamesMustMatchExactly() {
        assertTrue(ChinaCompatibilityPolicy.isNativeHost(ChinaCompatibilityPolicy.UMS));
        assertFalse(ChinaCompatibilityPolicy.isNativeHost(ChinaCompatibilityPolicy.UMS + ".fake"));
        assertFalse(ChinaCompatibilityPolicy.isNativeHost("com.nearme.instant.platform"));
    }

    @Test public void clientsAreNotAnAmapOnlyAllowlist() {
        assertTrue(ChinaCompatibilityPolicy.isClientCandidate("com.sankuai.meituan"));
        assertTrue(ChinaCompatibilityPolicy.isClientCandidate("example.native.client"));
        assertFalse(ChinaCompatibilityPolicy.isClientCandidate("com.android.settings"));
        assertFalse(ChinaCompatibilityPolicy.isClientCandidate("com.google.android.gms"));
    }

    @Test public void onlyExactObservedRegionPropertiesChange() {
        assertTrue(ChinaCompatibilityPolicy.isRegionKey("ro.oplus.pipeline.region"));
        assertTrue(ChinaCompatibilityPolicy.isRegionKey("ro.oplus.regionmark"));
        for (String key : new String[]{null, "ro.build.fingerprint", "ro.product.model", "ro.serialno",
                "persist.sys.locale", "ro.oplus.pipeline.region.extra"}) {
            assertFalse(ChinaCompatibilityPolicy.isRegionKey(key));
        }
    }

    @Test public void capabilitiesAndPermissionsAreNotGranted() {
        assertTrue(ChinaCompatibilityPolicy.isExportFeature("oppo.version.exp"));
        for (String key : new String[]{null, "android.hardware.nfc", "com.oplus.permission.safe.ASSISTANT",
                "com.oplus.permission.safe.AUTHENTICATE", "oppo.version.exp.fake"}) {
            assertFalse(ChinaCompatibilityPolicy.isExportFeature(key));
        }
    }

    @Test public void exportMetadataOnlyAppliesToUms() {
        assertTrue(ChinaCompatibilityPolicy.isExportMetadataTarget(ChinaCompatibilityPolicy.UMS));
        assertFalse(ChinaCompatibilityPolicy.isExportMetadataTarget("com.sankuai.meituan"));
        assertFalse(ChinaCompatibilityPolicy.isExportMetadataTarget(null));
    }

    @Test public void systemUiSkinAndUnrelatedFramesAreNotNativeFlows() {
        assertTrue(ChinaCompatibilityPolicy.isNativeFrame("com.pantanal.server.content.sdk.StaticSdk"));
        assertTrue(ChinaCompatibilityPolicy.isNativeFrame("com.oplus.systemui.statusbar.notification.livealert.Filter"));
        assertFalse(ChinaCompatibilityPolicy.isNativeFrame("com.android.systemui.theme.ThemeController"));
        assertFalse(ChinaCompatibilityPolicy.isNativeFrame("com.oplus.systemui.qs.Tile"));
        assertFalse(ChinaCompatibilityPolicy.isNativeFrame(null));
    }

    @Test public void ordinaryAppsNeedNativeDeclarationsEvenWhenScoped() {
        assertFalse(ChinaCompatibilityPolicy.hasNativeDeclaration(false, false, false, false, false));
        assertTrue(ChinaCompatibilityPolicy.hasNativeDeclaration(true, false, false, false, false));
        assertTrue(ChinaCompatibilityPolicy.hasNativeDeclaration(false, true, false, false, false));
        assertTrue(ChinaCompatibilityPolicy.hasNativeDeclaration(false, false, true, false, false));
        assertTrue(ChinaCompatibilityPolicy.hasNativeDeclaration(false, false, false, true, false));
        assertTrue(ChinaCompatibilityPolicy.hasNativeDeclaration(false, false, false, false, true));
    }
}
