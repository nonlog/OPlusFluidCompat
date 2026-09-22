# OPlusFluidCompat

Experimental LSPosed module for native OPlus Live Alerts on OxygenOS. The primary
target is AMap (`com.autonavi.minimap`), which already exports the native immersive
navigation renderer. Meituan remains a secondary, unimplemented target.

## Current status: cycling map demonstrated; 0.5.1 verification pending

0.4.1 has visibly displayed the real AMap cycling map on the OxygenOS lockscreen.
The host bound AMap's service, passed the display token, and received its native
SurfacePackage. The map, route and location marker are rendered by AMap, not by
this module. The successful test required selecting immersive display mode for
AMap; the original stored list-mode preference prevented automatic expansion.

Driving is NOT fixed: a separate cold driving test reached the native service but
AMap had no initialized AJX context/config and returned no map. Meituan is also
unimplemented. Do not interpret the cycling result as support for every mode/app.

0.5.0 adds an app-side native-readiness marker and only restores the native host
mapping for genuinely initialized navigation. Ordinary driving notifications are
left on their original path, rather than presented as a misleading generic capsule.
CI21 (0.5.0) passed nine policy tests, installed and correctly detected native
cycling initialization. Its automatic display was still blocked by the old saved
list-mode preference. 0.5.1 chooses native immersion at initial card creation only
for the ready AMap service, retaining the original default-enable, super-power and
permission checks. It does not edit preference files. This revision awaits CI and
real-device verification from the saved list-mode baseline.

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
instance. The 0.5.0 readiness publisher and probes require `com.autonavi.minimap`. Do not add Instant
Platform or UMS scopes for this implementation.

The obfuscated profile is pinned to SystemUIPlugin **16.001.002 / 16001002**, SHA-256
`d7c0a5dc11f40e7c89b2687a5a89a7db1fa2365ddb3fb3110214b2e7f19862db`.
Unknown plugin APKs fail closed. This is not a cross-ROM compatibility claim.

## Build, install and verification

APKs are built exclusively by GitHub Actions, including nine policy unit tests.
Install the CI-produced APK, enable SystemUI and AMap scopes, and restart both apps.
Start real AMap navigation and inspect the lockscreen immersive map as well as the
status-bar UI. Capture host service binding and the returned native SurfacePackage.

Detailed evidence and outstanding checks are in [Native path findings](docs/NATIVE_PATH_FINDINGS.md).
The [cycling and driving comparison](docs/V041_NATIVE_RESULT.md) records the actual
rendering result, display-mode prerequisite, and remaining limitations.
The [historical FlashViews investigation](docs/LEGACY_FLASHVIEWS_ANALYSIS.md) preserves
older evidence but its former impossibility conclusion is not authoritative.

## Rollback

Disable the module and restart SystemUI and AMap. Module code does not edit system
APKs, UMS/OCS data, app accounts or permanent permissions. The diagnostic test changed
only AMap's saved SystemUI display mode, with its original value recorded separately;
that display preference is not automatically reverted by disabling the module.
