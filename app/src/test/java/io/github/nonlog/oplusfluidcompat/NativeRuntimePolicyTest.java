package io.github.nonlog.oplusfluidcompat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class NativeRuntimePolicyTest {
    @Test public void inspectedSystemUiBuildOnly() {
        assertTrue(ChinaCompatibilityPolicy.supportsFlashbackProfile(36, "CPH2573_16.0.10.501(EX01)"));
        assertFalse(ChinaCompatibilityPolicy.supportsFlashbackProfile(35, "CPH2573_16.0.10.501(EX01)"));
        assertFalse(ChinaCompatibilityPolicy.supportsFlashbackProfile(36, "CPH2573_16.0.10.502(EX01)"));
        assertFalse(ChinaCompatibilityPolicy.supportsFlashbackProfile(36, null));
    }

    @Test public void diagnosticsDoNotMatchAuthenticationOrUserDataApis() {
        for (String name : new String[]{"findSeedlingService", "queryDomainEnable",
                "queryDomainEnableGroup", "queryDomainEnableV3", "queryServicePkgMeta", "downloadUpkFile"}) {
            assertTrue(ChinaCompatibilityPolicy.isNativeDiscoveryMethod(name));
        }
        for (String name : new String[]{"authenticate", "checkPermissions", "requestData",
                "registerUserShadowDataProviderProxy", "registerBehaviorDataProviderProxy", "", null}) {
            assertFalse(ChinaCompatibilityPolicy.isNativeDiscoveryMethod(name));
        }
    }

    @Test public void umsRegistrationBypassIsPinnedToInspectedRuntime() {
        assertTrue(ChinaCompatibilityPolicy.supportsUmsRegistrationProfile(
                36, "CPH2573_16.0.10.501(EX01)", 17017000L));
        assertFalse(ChinaCompatibilityPolicy.supportsUmsRegistrationProfile(
                36, "CPH2573_16.0.10.501(EX01)", 17017001L));
        assertFalse(ChinaCompatibilityPolicy.supportsUmsRegistrationProfile(
                36, "CPH2573_16.0.10.502(EX01)", 17017000L));
    }

    @Test public void seedlingDescriptorPathsOnlyAcceptNativePackageAssets() {
        assertTrue(ChinaCompatibilityPolicy.isSeedlingDescriptorPath("cards/order.upk"));
        assertTrue(ChinaCompatibilityPolicy.isSeedlingDescriptorPath("cards/order.PACKAGE"));
        assertFalse(ChinaCompatibilityPolicy.isSeedlingDescriptorPath("cards/order.json"));
        assertFalse(ChinaCompatibilityPolicy.isSeedlingDescriptorPath(".up"));
        assertFalse(ChinaCompatibilityPolicy.isSeedlingDescriptorPath(null));
    }
}
