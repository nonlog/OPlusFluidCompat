# OPlusFluidCompat

Experimental LSPosed module for native OPlus Live Alerts on OxygenOS. The target
is native China ColorOS app compatibility, not notification conversion. AMap's
native renderer is the first acceptance test; other native clients are included
in the compatibility investigation, not declared working merely by being scoped.

## Current status: 0.8.1 native registration compatibility candidate

0.8.x extends the verified 0.7.1 China-region work into two native registration
gates found on the inspected ROM. It is intentionally not described as universal
compatibility until the CI artifact is installed and exercised with real client data.

The first 0.8.0 startup check showed that SystemUI itself also declares the
FlashViews permission and therefore hit the new support hook unnecessarily. 0.8.1
restricts that compatibility override to non-system applications; native system
packages retain their original framework decisions.

For FlashViews, SystemUI keeps the ROM's native service, renderer, rate limiting and
existing signer verification. A package already known to the native RUS table may
pass the disabled `userEnable` support gate when it really requests
`com.oplus.flashback.permission.FLASH_VIEWS_SERVICE`. A package missing from the RUS
table is admitted through the missing-registration check only when it requests that
permission **and** carries another native OPlus integration marker (immersive/OCS,
Seedling provider, or FanZai provider). An existing RUS signer mismatch is never
converted to success.

For Seedling, the OOS UMS scanner already discovers real
`com.oplus.seedling.action.SEEDLING_CARD` providers and their
`oplus.seedling.provider` metadata, but drops a provider when its package is absent
from the local `service_info` repository. 0.8.x restores that provider to the native
scanner result using its own `.upk`/`.package` descriptors and installed package
metadata. It does not synthesize service IDs, card contents, order state or network
discovery responses. This path is pinned to the inspected UMS 17.17.0 and ROM build.

The installed-app manifest audit on the development phone found direct native
FlashViews evidence in AMap and Meituan. The installed JD and Taobao builds did not
declare the inspected OPlus markers; Baidu Map was not installed. OCS auth metadata
alone (seen in apps such as Alipay, QQ Music and Xiaohongshu) is not treated as proof
of Fluid Cloud support.

### Verified 0.7.1 checkpoint

0.7.1 was built by GitHub Actions run 27, installed as versionCode 10, and checked
on the rooted development device. The native FlashBack initializer now receives
export=false. Its service dump reports region=CN and regionMark=CN, while the
original device-family flavor=2 and support mask=4 remain unchanged.

This fixes a measured defect in 0.7.0: its property hooks reported CN but the
native engine still retained region=EXP. The new hooks run before the native
asynchronous initializer, not just after a displayed region string is computed.

**This is not an all-app unlock.** A real UMS queryDomainEnableGroup invocation
was observed in the export binary, whose implementation returns an empty list.
That establishes an active missing discovery API, not that it is the sole cause
of every client's failure. No cloud service records or order data were invented.

The existing AMap native renderer mapping is unchanged. Earlier cycling surface
screenshots are historical evidence, not a new 0.7.1 navigation acceptance test.
Driving, Meituan order rendering and universal native-client compatibility remain
unverified. The attempted fresh phone UI test did not produce a navigation result.

The process-local identity layer continues to cover opted-in clients with genuine
OPlus manifest declarations. It does not alter system-server, ROM partitions,
model/fingerprint, caller identity or Android permission grants. 0.8.x adds the
scoped native registration checks described above; it does not globally disable
signature enforcement.
See [native region branch and CI/device evidence](docs/NATIVE_REGION_BRANCH.md)
and [the earlier identity investigation](docs/CHINA_IDENTITY.md).

## Historical AMap implementation through 0.6.0

0.4.1 has visibly displayed the real AMap cycling map on the OxygenOS lockscreen.
The host bound AMap's service, passed the display token, and received its native
SurfacePackage. The map, route and location marker are rendered by AMap, not by
this module.

**0.6.0 removes the module's dependency on hooking AMap's own process.** The
`com.autonavi.minimap` scope added at 48f44e7 and made load-bearing at 798a339 is
gone. That scope existed only to attach an `NATIVE_RENDERER_READY` marker from
AMap's in-process renderer state, and the compatibility path then refused to run
without it. AMap gates live-alert eligibility on its own native readiness, so the
marker added nothing and only made the working 0.4.x behaviour conditional on
AMap cooperation.

0.6.0 instead identifies a plugin-mapped AMap live alert by the notification ID the
Seedling plugin itself supplies (prebuiltId `99910001` in AMap 17.00.0.2009, matched
over `99910000..99910063`). No AMap hook, no marker, no cooperation from the app.

The SystemUI-side mapping is still required and is still load-bearing: AMap's APK
contains neither `liveAlertService` nor `immersiveCardType`, so this module must
supply both. Static LSPosed scope is `com.android.systemui` alone, as in 0.4.x.

Driving is NOT fixed: a cold driving test reached the native service but AMap had no
initialized AJX context/config and returned no map. Meituan is also unimplemented.
Do not interpret the cycling result as support for every mode/app.

0.5.0 added the app-side readiness marker described above; 0.5.1 chose native
immersion at initial card creation. 0.6.0 keeps the 0.5.1 initial-mode behaviour,
retaining the original default-enable, super-power and permission checks, and does
not edit preference files. It is otherwise a return to the 0.4.x scope model.

The installed 0.3.0 baseline was tested with real navigation. It displays an ordinary
navigation capsule/card, but does **not** bind AMap's immersive map service. That is
not functional completion.

0.4.0 addresses two measured defects: wrong immersive card type (template instead
of remote surface) and missing native navigation RUS configuration. It reuses the
ROM's existing navigation profile only for the missing production AMap entry. The
original OPlus host must bind the service and display the surface rendered by AMap.

No notification-listener app, replacement map UI, Android permission grant, UMS/OCS
database edit, or generic notification conversion is included. The new registration
bypass is constrained to genuine native declarations on the inspected runtime. An
ordinary capsule, passing CI, and installed hooks are not proof that native immersive
navigation works.

## Compatibility and scope

Development device: OnePlus CPH2573, Android 16,
OxygenOS `CPH2573_16.0.10.501(EX01)`.

The recommended 0.7.x scopes include SystemUI, Pantanal UMS, SceneService,
AssistantScreen and AmbientLiveAlert, plus the listed native client apps.
Recommendations do not automatically change LSPosed's enabled scopes. Additional
client apps may be selected, but the identity layer activates only when an OPlus
integration declaration is present. There is no Instant Platform or system-server
hook. The embedded Seedling plugin still runs inside SystemUI; its original
classloader and pinned profile are used for the separate AMap renderer mapping.

The obfuscated profile is pinned to SystemUIPlugin **16.001.002 / 16001002**, SHA-256
`d7c0a5dc11f40e7c89b2687a5a89a7db1fa2365ddb3fb3110214b2e7f19862db`.
Unknown plugin APKs fail closed. This is not a cross-ROM compatibility claim.

## Build, install and verification

APKs are built exclusively by GitHub Actions, including policy unit tests.
Install the CI-produced APK, enable the relevant recommended scopes, and restart
only those target processes (or reboot after saving other work).
Start real AMap navigation and inspect the lockscreen immersive map as well as the
status-bar UI. Capture host service binding and the returned native SurfacePackage.

Detailed evidence and outstanding checks are in [Native path findings](docs/NATIVE_PATH_FINDINGS.md).
The [cycling and driving comparison](docs/V041_NATIVE_RESULT.md) records the actual
rendering result, display-mode prerequisite, and remaining limitations.
The [historical FlashViews investigation](docs/LEGACY_FLASHVIEWS_ANALYSIS.md) preserves
older evidence but its former impossibility conclusion is not authoritative.

## Rollback

Disable the module and restart all processes in which it was enabled (or reboot).
Module code does not edit system APKs, UMS/OCS databases, app accounts or permanent
permissions. Native components can maintain their own caches; disabling hooks does
not promise to undo data written by the components themselves.
The diagnostic test changed only AMap's saved SystemUI display mode, with its original
value recorded separately; that display preference is not automatically reverted by
disabling the module.
