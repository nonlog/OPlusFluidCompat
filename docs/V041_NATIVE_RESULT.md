# 0.4.1 native rendering result and 0.5.0 readiness gate

Device: CPH2573, OxygenOS CPH2573_16.0.10.501(EX01), Android 16.
AMap: 17.00.0.2009 (162500). SystemUIPlugin: 16.001.002 (16001002).
Installed test artifact: CI20, version 0.4.1 / code 5, SHA-256
`346c70e4ccb57d4e573f57bfb1df2b74e3a02b6e5b268a75e3097219fafbcbb9`.

## Cycling: native lockscreen map visibly demonstrated

On 2026-09-22 at 09:06-09:07 (UTC+8), a real cycling navigation session produced:

1. AMap's own native AJX initialization, configuration length 105.
2. SystemUI binding AMapImmerseNaviService and sending host-token message 11.
3. AMap receiving a valid token and reply messenger with display size 1440 x 3168.
4. AMap creating its own native map view and replying with SurfacePackage, message 21.
5. Native SurfaceControlViewHost attached under the lockscreen SurfaceView.
6. A visibly rendered AMap map, navigation route and position marker under the
   lockscreen clock and navigation card after a sleep/wake cycle.

Private local evidence includes `logs/v041-surface-wake.png`,
`logs/v041-native-working-modules.txt`, `logs/v041-native-working-services.txt`,
and `logs/v041-native-surfaces.txt`. Do not publish these files; they contain routes
and location data. This is a real native map result, not just a generic capsule.

## Display-mode prerequisite and explicit diagnostic change

SystemUI had saved `laid_com.autonavi.minimap=0` (list mode) in
`/data/user_de/0/com.android.systemui/shared_prefs/immersiveLastState.xml`.
That explicit preference overrides the native profile's default immersion flag.
Only that entry was temporarily changed to 2 (immersive mode), with a local backup.
It was restored to 0 and verified during a UI-only activation test; tapping the
observed card icon did not switch it. It was then set to 2 again for cold tests.

The successful rendering test therefore has a display-mode prerequisite. Normal
icon activation from saved list mode is NOT verified, and manual XML editing is
NOT part of the module implementation. The host preference is separate from UMS/
OCS registration and no permission/database authentication records were changed.

## Driving: distinct unresolved failure, not native support

A cold real driving session at 09:13 reached the same host binding, but the app
reported `hasAjxContext=false`, `configLength=0`, `missing-ajx-init` and a null map
view. Driving must not be described as fixed or inferred from the cycling result.
The reason the driving frontend does not initialize this renderer is unresolved.

Stopping AMap via force-stop removed its Live Alert and service entry. This is a
process-stop cleanup check, not a completed test of the app's normal exit button.

## 0.5.0 change (not yet device-verified at commit time)

The AMap scope now marks only its navigation notifications backed by an actual
initialized native context and nonempty configuration. The known driving ID is
excluded to prevent stale cycling state from relabeling a driving notification.
If initialization occurs after the foreground notification, the app's existing
notification is refreshed with the same ID/tag/content, with only the readiness
marker added. No route, view, configuration or rendering context is fabricated.

SystemUI requires this marker, the exact AMap package, foreground-service state and
navigation category/channel before applying compatibility. Other notifications
run their original eligibility path without the fallback RUS override. Nine pure
policy tests cover the allowlist, navigation boundaries and readiness/drive gate.

Both SystemUI and AMap scopes are required. Meituan, JD and Taobao are not implemented.
