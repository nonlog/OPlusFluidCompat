# OPlusFluidCompat

LSPosed module for OnePlus/OPlus Live Alerts (Fluid Cloud / 流体云) on OxygenOS, targeting
Chinese third-party apps that ship ColorOS Live Alerts integrations.

## STATUS: does not work. Read this first.

The current SystemUI hook set **does not enable Live Alerts** for any third-party app, and the
on-device evidence below shows it cannot. The module loads correctly and its hooks install, but
the gates they intercept already pass, so the hooks never change a result.

The real blocker is **server-side registration that a device-side module cannot synthesize**.
This repository is kept as a record of a verified negative result.

## Verified environment

| Item | Value |
| --- | --- |
| Device | OnePlus CPH2573 (product CPH2573IN) |
| Android | 16 |
| OxygenOS build | `CPH2573_16.0.10.501(EX01)` |
| OPlus ROM | `ro.build.version.oplusrom = V16.1.0` |
| Root / framework | root + LSPosed, verified |

Relevant packages present on the stock ROM:

| Package | Version | Role |
| --- | --- | --- |
| `com.oplus.ambient.livealert` | 16.500.003 | Live Alerts host |
| `com.android.systemui` | 16.99.12 | FlashViewsService, LiveAlert pipeline |
| `com.oplus.pantanal.ums` | 17.17.0 (stock 16.59.6) | Pantanal UMS service/card registry |
| `com.coloros.ocs.opencapabilityservice` | 16.0.6 | OCS capability authentication |
| Scene plugin (seed) | 17.17.0 | PluginSeedling AOD origin |

The framework is fully present. What is missing is third-party registration.

## Module loading is NOT the problem

`/data/adb/lspd/log/modules_*.log`:

```
21:25:53  hooked SettingsUtils.isValidCaller
21:25:53  hooked ConfigurationManager.isSupportFlashViews(String)
21:25:53  hooked ConfigurationManager.isSupportFlashViews(Context,int)
21:25:53  SystemUI hooks installed: 3/3
22:06:35  SystemUI hooks installed: 3/3
```

Scope and loading are correct. No `unlock ...` lines were ever emitted, which means the
interceptors never altered a return value.

## Why the SystemUI hooks do nothing

`adb shell dumpsys activity service com.android.systemui/com.oplus.flashback.service.FlashViewsService config`

```
FlavorHelper:            region: EXP   regionMark: IN   flavor: 2   support: 4
ConfigurationManager:    FeatureEnable: true   SupportAll: false
  Support App:
    com.autonavi.minimap=RusItemInfo:{multiViews=false, userEnable=true, useWhiteListRule=false}
    com.sankuai.meituan =RusItemInfo:{multiViews=true,  userEnable=true, useWhiteListRule=true}
```

Both targets are **already** in `mSupportAppMap` with `userEnable=true`, so
`ConfigurationManager.isSupportFlashViews(pkg)` already returns `true` without any hook.

`SettingsUtils.isValidCaller` is only consulted inside `FlashViewsService.onBind`, and AMap never
calls it. `dumpsys activity services com.android.systemui` shows the service's only clients are
SystemUI itself:

```
Per-process Connections:
  ConnectionRecord{... CR com.android.systemui/com.oplus.flashback.service.FlashViewsService}  mCallingUid=10260
```

## AMap's actual Live Alerts path (not FlashViews)

`com.autonavi.minimap` 17.00.0.2009 (versionCode 162500) manifest:

```xml
<meta-data android:name="livealert.immersive.card" android:value="1"/>
<meta-data android:name="com.oplus.ocs.card.AUTH_CODE" android:value="..."/>
<uses-permission android:name="com.oplus.flashback.permission.FLASH_VIEWS_SERVICE"/>
<service android:name="com.amap.minimap.immersenavi.AMapImmerseNaviService"/>
```

`AMapImmerseNaviService` is a Messenger service. The **system host must bind it** and supply the
display geometry; AMap then renders an immersive navigation surface. Protocol:

| Message | Meaning |
| --- | --- |
| 11 | bind host token; extras `hostToken` + Bundle `livealert.immersive.display` {width,height} |
| 12 | surface change |
| 13 | state change |
| 14 | unbind host token |
| 10000003 | open/close overview |
| 10000004 | universal JSON command |
| reply 21 | returns `SurfacePackage` after map render completes |

AMap builds `SurfaceControlViewHost.fromViewHostToken()` and never self-initiates.

AMap *also* bundles the legacy `com.oplus.flashbacksdk` SDK (classes4.dex, classes7.dex). Its
`xu4.isSupport()` requires Secure settings `Setting_AodEnable`, `Setting_AodSceneInfoSwitchEnable`,
`flash_views_for_airview_enable`, `air_view_toggle` — **all four already `1`** on this device — yet
AMap never binds FlashViewsService. Measured: logcat cleared, `androidamap://navi` launched,
12 s of navigation — zero FlashViewsService, `onBind`, `OPPOAodConnectManager` or `flashbacksdk`
log lines.

## The actual failure point

SystemUI's LiveAlert feed is empty. The chain from logcat:

```
OplusUserContext ... content://0@com.oplus.pantanal.ums.decision/services
SG::DecisionResultProvider: query call: .../decision/services?platformVersion=3000021,
    caller=com.android.systemui, entrance=[16, 4, 8]
PantaCard.SysUi.BaseTrackEvent  entrance_name=aod  filtered_services=[]  serviceIds=      <-- EMPTY
PluginSeedling--Origin: LiveAlertInteractorImpl registerDataListener ENTRY_AOD
LiveAlertInteractorImpl-->EntryDataListener,onDataChanged originData:[] data:[]             <-- EMPTY
```

UMS's AOD decision provider returns no services, so SystemUI receives empty origin data and nothing
can render.

## The three missing registrations

Inspected from `ums_local_repository_14.db` (UMS 17.17.0) and OCS `authentication.db`:

| Registry | AMap (uid 10364) | Meituan (uid 10618) |
| --- | --- | --- |
| UMS `service_info` | absent | absent |
| UMS `card_info` | absent | absent |
| UMS `repo_info` | absent | absent |
| UMS `apk_info` (30 rows, OPlus system apps only) | absent | absent |
| UMS `ocs_cache_table` (35 rows) | absent | absent |
| OCS `a_e` capability rows | **0** | **0** |

For reference, apps that *are* registered in UMS `apk_info`: `com.oplus.eyeprotect`,
`com.coloros.colordirectservice`, `com.oplus.screenrecorder`, `com.oplus.securepay`,
`com.oplus.safecenter`, `com.oplus.camera`, `com.oplus.wallpapers`, `com.oplus.securitypermission`,
`com.oplus.remotecontrol`, `com.oplus.battery`, `com.oneplus.deskclock`, `com.heytap.mydevices`,
`com.heytap.accessory`, `com.android.systemui`, `com.coloros.floatassistant`, `com.oneplus.oshare`,
`com.oplus.aimemory`, `com.oneplus.note`, `com.coloros.sceneservice`, `com.oplus.metis`,
`com.oplus.gleanerservice`, `com.oplus.gesture`, `com.oneplus.soundrecorder`,
`com.oplus.wirelesssettings`, `com.oneplus.filemanager`, `com.oplus.linker`, `com.oplus.melody`,
`com.oplus.ambient.livealert`, `net.oneplus.weather`, `com.oplus.pantanal.ums`.

OCS gate (`com.oplus.ocs.out.OpenCapabilityProvider`, method `capability_permission_method`):

1. Caller must hold `com.oplus.permission.safe.AUTHENTICATE`. None of AMap, Meituan, JD or Taobao do.
2. `AuthenticationDb` is keyed by **uid + capability client name**. Registered clients are
   `CARD_CLIENT` (6 rows), `DISPLAY_CLIENT` (53), `OLK_CLIENT` (483), covering only OPlus/OnePlus
   system apps (uids 10168, 10190, 10155, 10214, 10222, 10227, 10237, 10351, 10592).

This is the "developer registration / app registration / whitelist" layer that OShin v16.10
build 1451 removed for three days and reverted in build 1452 — and it is why that bypass could not
be restored from public sources.

## Conclusion

A Chinese ColorOS app can only render Live Alerts on OxygenOS 16 EX when all three are true:

1. The app is registered in UMS `service_info` / `card_info` / `repo_info` — server-delivered,
   China-region only, and carrying card layout plus a signature hash that a module cannot forge.
2. The app has an OCS capability record keyed by uid + `CARD_CLIENT`.
3. The `com.oplus.ambient.livealert` / PluginSeedling host binds the app's
   `livealert.immersive.card` service and supplies `livealert.immersive.display`.

A SystemUI-only LSPosed hook addresses none of these. Extending scope to
`com.oplus.pantanal.ums` / `com.coloros.assistantscreen` does not help either, because there is no
local service collection to add to, and no signature to satisfy `repo_info`.

## Hooks currently present (inert)

`app/src/main/java/io/github/nonlog/oplusfluidcompat/MainModule.java`, scope `com.android.systemui`:

- `com.oplus.flashback.settings.utils.SettingsUtils.isValidCaller(Context,String)`
- `com.oplus.flashback.manager.ConfigurationManager.isSupportFlashViews(String)`
- `com.oplus.flashback.manager.ConfigurationManager.isSupportFlashViews(Context,int)`

The module also declares a diagnostic `IntelligentIntentProvider` content provider which logs and
answers the T/TAF `querySupportIntent` / `shareIntent` / `deleteIntent` protocol for
`com.autonavi.minimap`. It is diagnostic only; that path was never observed being called.

These classes and methods do exist in the stock SystemUI, but see above: they are not the gate.

## Target packages

| App | Package |
| --- | --- |
| 高德地图 | `com.autonavi.minimap` |
| 美团 | `com.sankuai.meituan` |
| 京东 | `com.jingdong.app.mall` |
| 淘宝 | `com.taobao.taobao` |

Verified apps: **none render Live Alerts.** 高德 navigation was tested repeatedly with the module
enabled and produced no Live Alerts output.

## Known limitations

- The module does not and cannot enable Live Alerts for the listed apps on this ROM.
- UMS registration data is China-region server content; it is not present on this device.
- OCS capability records require a registration handshake the apps cannot complete here because
  they lack `com.oplus.permission.safe.AUTHENTICATE`.
- `ConfigurationManager.isSupportFlashViews(Context,int)` is also gated by
  `RusItemInfo.isSupport(versionCode)`; the existing hook returns `true` without that check, which
  diverges from stock behavior even though it changes nothing observable today.
- The module performs runtime hooks only. It does not patch SystemUI or modify any database.
- Enabling FlashViewsService debug logging (`dumpsys activity service ... log all 1`) was performed
  on the test device during diagnosis and is harmless; it does not persist any module state.

## Rollback

```bash
# Disable in LSPosed, or remove the module entirely:
adb shell su -c 'rm -rf /data/adb/lspd/config/modules_config.db'   # only if LSPosed is being reset
adb uninstall io.github.nonlog.oplusfluidcompat
```

Normal rollback is simply disabling scope for `com.android.systemui` in the LSPosed manager and
uninstalling the package. No system files, databases or SELinux contexts are modified, so nothing
else needs restoring. If FlashViewsService debug logging was enabled, restart SystemUI or the
device to clear it.

## Build

Artifacts are built only by GitHub Actions; no local or VPS Android builds.

Commits use author and committer `Codex <codex@openai.com>`.

## Analysis assets

Local reverse-engineering material lives outside this repository, in `D:\Workspace\LiveAlertsBypass`
and the `amap6-jadx` / `ocs-jadx` directories, including `ums_local_repository_14.db` copies and
JADX output for AMap `classes6.dex` (`AMapImmerseNaviService`) and
`OpenCapabilityService.apk` (`OpenCapabilityProvider`).
