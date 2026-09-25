# OPlusFluidCompat-System — activation result

Date: 2026-09-25, device PJD110 on PJD110_16.0.10.501(CN01) over OOS 16.0.10.501.
Module `oplusfluidcompat_system` v0.2.2 (versionCode 4), LSPosed companion
`io.github.nonlog.oplusfluidcompat` enabled.

## Result

The three overlaid CN builds are active and stable, and SystemUI's native
Seedling (Fluid Cloud) subsystem is running a real AMap capsule.

Status bar on the home screen, 12:40:

> black capsule, navigation arrow + `高德地图`

AMap was in turn-by-turn navigation at the time, showing `21m 无名道路`
(maneuver arrow, distance, road name) — the structured fields the capsule
carries.

Seedling log at the moment the capsule formed:

```
WindowController-->curState:1 curKeyArray: ["0|com.autonavi.minimap|99910001|null|10364"]
CapsuleBubbleEx-->getDuration: customDuration=-1 serviceId=laid_com.autonavi.minimap
CapsuleVisEx-->onStateChanged: sourceStateSTART_STATE, targetState:CAPSULE_STATE,
                                contentState:NORMAL
SimpleSceneBuilder-->afterAction APP_TO_CAPSULE_WITH_NONE
OplusFloorRefreshRateController: onSeedlingCardStateChange state=1
```

Evidence files: `out/evidence/capsule-home.png` (capsule),
`out/evidence/amap-nav.png` (navigation state),
`out/evidence/seedling-capsule.txt` (logs). `out/` is gitignored, so these are
local artifacts and are not part of the commit.

## Active package state

| package | versionCode | version | path |
| --- | --- | --- | --- |
| `com.coloros.sceneservice` | 17006000 | 17.6.0 | `/product/priv-app/SceneService/SceneService.apk` |
| `com.oplus.pantanal.ums` | 16059006 | 16.59.6 | `/product/priv-app/UMS/UMS.apk` |
| `com.oplus.travelengine` | 15000025 | 15.0.25_eccf83b_202607012159 | `/product/priv-app/TravelEngine/TravelEngine.apk` |

APK hashes match `system-module/vendor-manifest.json`. All three have
`pkgFlags=[SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA]` — none is an updated system
app, so all three are inside privapp allowlist enforcement.

## Why the CN UMS is the point

`assets/calculate/package_mapping_config.json` exists only in the CN build:

| APK | entries | `calculate/` assets |
| --- | --- | --- |
| CN `UMS.apk` | 3816 | 3 |
| OOS factory 16.59.6 | 2963 | 0 |
| OOS export 17.17.0 | 3640 | 0 |

It maps `536878000` → `com.autonavi.minimap` and `268439607` →
`com.baidu.BaiduMap`. Neither OOS build carries any `calculate/` asset, so the
domestic map path cannot work on the export stack at all.

## Two blockers found and fixed

Both are recorded in full in the module sources; the short version:

1. **Privapp allowlist.** With `ro.control_privapp_permissions=enforce` and
   `ro.build.type=user`, `PermissionManagerServiceImpl.onSystemReady()` throws
   for any privileged permission a privileged system package requests without
   an allowlist entry, taking `system_server` down. The export UMS survived
   only because it was a `/data/app` updated system app, and
   `pm uninstall-system-updates` removes exactly that exemption. Fixed by
   `privapp-permissions-oplusfluid.xml`, sourced with
   `tools/privapp_audit.py`.

2. **Stale `PackageCacher` parse.** PMS kept serving a cached parse of the
   export APK and never re-read the overlaid file, so SceneService ran with the
   export `versionCode` and resource ids against the CN resource table and died
   with `Unexpected start tag: found androidx.preference.PreferenceScreen,
   expected network-security-config`. Fixed by `post-fs-data.sh`, which drops
   just the affected cache entries once per `versionCode`.

Both were invisible from `adb shell`: the mounts are correct in every
namespace, including `system_server`'s —
`sha256sum /proc/$(pidof system_server)/root/product/priv-app/...` returned the
CN hash while `dumpsys package` still reported the export build.

## Not done

- §12.2 (second, non-AMap card): `com.baidu.BaiduMap` is not installed.
- Rollback has not been executed end to end; the four APKs are preserved in
  `vendor/rollback/` and re-verified on device at `/data/local/tmp/rollback/`.
