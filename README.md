# OPlusFluidCompat

Experimental LSPosed module for native OPlus Live Alerts on OxygenOS. The primary
target is AMap (`com.autonavi.minimap`), which already exports the native immersive
navigation renderer. Meituan remains a secondary, unimplemented target.

## Current status: 0.4.1 renderer diagnostics; native map not yet working

0.4.0 has now been installed and tested: the native service binding and host-token
request succeed, but the map surface is absent. See [device results](docs/V04_DEVICE_RESULT.md).
0.4.1 adds read-only renderer diagnostics. Enable **SystemUI and AMap** scopes for
this diagnostic build; AMap is not an additional compatibility bypass.

The installed 0.3.0 baseline was tested with real navigation. It displays an ordinary
navigation capsule/card, but does **not** bind AMap's immersive map service. That is
not functional completion.

0.4.0 addresses two measured defects: wrong immersive card type (template instead
of remote surface) and missing native navigation RUS configuration. It reuses the
ROM's existing navigation profile only for the missing production AMap entry. The
original OPlus host must bind the service and display the surface rendered by AMap.

No notification-listener app, replacement map UI, broad permission bypass, UMS/OCS
database edit, or all-app unlock is included. An ordinary capsule, passing CI, and
installed hooks are not proof that native immersive navigation works.

## Compatibility and scope

Development device: OnePlus CPH2573, Android 16,
OxygenOS `CPH2573_16.0.10.501(EX01)`.

The compatibility implementation uses LSPosed scope `com.android.systemui`. The embedded Seedling plugin
runs inside SystemUI; the module obtains its real classloader from the plugin
instance. The 0.4.1 probes also require `com.autonavi.minimap`. Do not add Instant
Platform or UMS scopes for this implementation.

The obfuscated profile is pinned to SystemUIPlugin **16.001.002 / 16001002**, SHA-256
`d7c0a5dc11f40e7c89b2687a5a89a7db1fa2365ddb3fb3110214b2e7f19862db`.
Unknown plugin APKs fail closed. This is not a cross-ROM compatibility claim.

## Build, install and verification

APKs are built exclusively by GitHub Actions, including seven policy unit tests.
Install the CI-produced APK, enable the module for SystemUI, and restart SystemUI.
Start real AMap navigation and inspect the lockscreen immersive map as well as the
status-bar UI. Capture host service binding and the returned native SurfacePackage.

Detailed evidence and outstanding checks are in [Native path findings](docs/NATIVE_PATH_FINDINGS.md).
The [historical FlashViews investigation](docs/LEGACY_FLASHVIEWS_ANALYSIS.md) preserves
older evidence but its former impossibility conclusion is not authoritative.

## Rollback

Disable this module or remove its SystemUI scope, then restart SystemUI. No permanent
permission, UMS/OCS data, app account, or system APK modifications are made by 0.4.0.
