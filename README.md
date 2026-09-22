# OPlusFluidCompat

Experimental LSPosed module for native OPlus Live Alerts on OxygenOS. The target
is native China ColorOS app compatibility, not notification conversion. AMap's
native renderer is the first acceptance test; other native clients are included
in the compatibility investigation, not declared working merely by being scoped.

## Current status: 0.7.0 China identity experiment, functional verification pending

0.7.0 adds a process-local China identity layer for the native Live Alert hosts
and opted-in clients whose manifests declare OPlus integration. It masks three
observed region properties, five export/OOS feature flags, and the UMS `IS_EXPORT`
metadata seen by native SDK callers. It does not alter the ROM, model, fingerprint,
device identifiers, system-server, caller UID, package signatures, or permissions.
Within SystemUI, identity changes are restricted to native Live Alert call stacks.

**This is not yet an all-app unlock.** The inspected UMS 17.17.0 export binary has
compiled-in export branches and an export-only region tier resolver. `CN` falls
back to `exportIn`; changing properties cannot by itself supply missing native
service definitions. The module logs actual identity hits and native provider
inventory so this remaining gate can be distinguished from a hook-loading failure.
See [China identity findings and validation](docs/CHINA_IDENTITY.md).

The existing AMap renderer mapping is retained unchanged. Its earlier cycling
result does not establish driving support or compatibility with other apps.

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

No notification-listener app, replacement map UI, broad permission bypass, UMS/OCS
database edit, or all-app unlock is included. An ordinary capsule, passing CI, and
installed hooks are not proof that native immersive navigation works.

## Compatibility and scope

Development device: OnePlus CPH2573, Android 16,
OxygenOS `CPH2573_16.0.10.501(EX01)`.

The recommended 0.7.0 scopes include SystemUI, Pantanal UMS, SceneService,
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
