# OPlusFluidCompat-System — activation result

Date: 2026-09-25, device PJD110 on PJD110_16.0.10.501(CN01) over OOS 16.0.10.501.
Module `oplusfluidcompat_system` v0.2.2 (versionCode 4), LSPosed companion
`io.github.nonlog.oplusfluidcompat` enabled.

## Result

The overlay half works: the three CN builds are active, stable, and inside
privapp enforcement, and they survive reboot. The Fluid Cloud half does **not**
work yet — see "Not achieved" below.

## Overlay state

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

## Not achieved — no native card

An earlier revision of this document claimed the AMap capsule was the native
Fluid Cloud card. That was wrong. What appears on the status bar is the
**ordinary notification capsule** — the shade still shows
`高德地图持续为您导航` — which the plan explicitly says does not count.

The tell was in the log line that was quoted as evidence:

```
curState:1 curKeyArray: ["0|com.autonavi.minimap|99910001|null|10364"]
serviceId=laid_com.autonavi.minimap
updateJson {"enable":0,"curState":1,...}
```

- `enable:0` — the capsule feature is off for this entry.
- `laid_com.autonavi.minimap` / `99910001` — a notification-derived capsule,
  not the cloud service `536878000` that `package_mapping_config.json` maps
  AMap to.
- `CardRootViewModel-->not card state: false` — no card was built; the capsule
  never entered card state.

So the CN overlay is active but nothing is driving the native card path.

### Root cause: the RUS profile the module clones is a 3-attribute stub

`MainModule.java:229-245` clones the ROM's `laid_com.amap.navi.demo` RUS entry,
copies all 25 fields, and overrides only `args[0]` (the id). The RUS table lives
in the plugin APK, `com.oplus.systemui.plugins`, at `res/3C.xml`; extracted with
`tools/privapp_audit.py`'s binary-AXML walker (`out/evidence/rus-3C.xml`).

The demo entry has three attributes:

```
service: id=laid_com.amap.navi.demo, lockImmersiveEnable=1, lockImmersiveDefault=1
```

Every working local profile in the same table has eight, and the five extra are
exactly the live-update channel:

```
service: id=laid_com.oneplus.deskclock, update_state_enable=1, canDelete=0,
         remind_always=1, la_score=6.3, la_tlevel=2, lu_settings=1, service_type=1
```

The runtime `RusBaseData` reproduces the stub field for field - every absent
attribute shows up as its default:

| runtime field | demo attribute | working profile |
| --- | --- | --- |
| `updateStateEnable=0` | absent | `update_state_enable=1` |
| `luSettings=[]` | absent | `lu_settings=1` |
| `liveAlertScore=-1.0` | absent | `la_score=6.3` |
| `liveAlertLevel=-1` | absent | `la_tlevel=2` |
| `serviceType=null` | absent | `service_type=1` |
| `lockImmersiveEnable=1` | present | present |

The clone is faithful; the template has no channel to carry maneuver, distance
or road into the capsule, which leaves the notification's static text as the
only content available. That is the whole of the reported symptom.

Note the export ROM has **no** complete AMap profile anywhere. Its AMap entries
are cloud ids `536878018` and `536879184`, and those carry only the same two
immersive flags. `com.baidu.BaiduMap` is `536877940`, likewise minimal.

### The cloud path is NOT the mechanism (hypothesis tested and rejected)

An earlier revision of this section suggested the card might be supposed to come
through the cloud path (`536878000` via UMS) rather than a local RUS profile.
Tested on device with AMap actually navigating, and rejected:

- UMS `decision_result` holds exactly one row, `536877097`
  (`application_suggestions`). No AMap decision exists even mid-navigation:

  ```
  SG::DecisionResultProvider: [UMS.Seedling] query decision result,
    sql: SELECT * FROM decision_result WHERE (available = 1) and ...
  merge start, list size:1 -> serviceId=536877097
  ```

- The provider itself works - SystemUI queries it as `caller=com.android.systemui`
  and gets that row back - so an absent AMap row means nothing ever told UMS
  AMap was navigating.
- UMS can only carry a service an app reports, and AMap declares
  `seedling=false`, so AMap never feeds UMS by that route.
- AMap does declare `immersive=true`, and `AMapImmerseNaviService` connected
  and returned `hasSurfacePackage=true`.

So the immersive/RUS path the module already drives is the correct layer, and
the fix is to give the cloned profile the live-update fields it lacks. The CN
plugin's real profile would confirm the exact values but is not needed to know
*which* fields are missing.

### The user-visible symptom, confirmed

With navigation running, the status-bar capsule shows only the app name, and
tapping it expands to a card reading `高德地图 / 正在导航` - the notification's
own text, with no map surface, maneuver, distance, road or ETA. That is the
notification rendered as a card, which is consistent with a live alert whose
only content source is the notification.

Not settled, because the CN plugin is stored as `COMPRESSED_COMPACT`
(`iformat=0x0006`, datalayout 3) and `tools/erofs_min.py` reads only flat
plain/inline.

The on-disk format is now fully understood, from `fs/erofs/erofs_fs.h` and
`fs/erofs/zmap.c` at tag v6.12:

- map header at `ALIGN(erofs_iloc + inode_isize + xattr_isize, 8)`, 8 bytes;
  index starts at `ebase = header_off + 8`
- `vi->z_logical_clusterbits = sb->s_blocksize_bits + (h->h_clusterbits & 7)`,
  so `h_clusterbits=0` is normal - it means lclustersize equals the block size.
  An earlier note in this file called `clusterbits=0` invalid; that was wrong.
- the plugin's `h_advise=0x0007` is `COMPACTED_2B | BIG_PCLUSTER_1 |
  BIG_PCLUSTER_2`, so the index is the compacted mixed layout, not 8-byte
  entries: `compacted_4b_initial = (32 - ebase % 32) / 4` entries of 4 bytes,
  then `compacted_2b = rounddown(totalidx - compacted_4b_initial, 16)` entries
  of 2 bytes, then 4-byte entries (`z_erofs_load_compact_lcluster`,
  `unpack_compacted_index`, `decode_compactedbits`)

Routes ruled out, so nobody retries them:

- 7-Zip 26.03 supports ext4 and SquashFS but not EROFS
- loop-mounting the image on the device fails with EIO before erofs ever runs:
  `/data` is file-based-encrypted, so the loop kernel thread reads ciphertext
- WSL is not installed on this host, and installing it needs a reboot plus a
  Windows feature
- `my_manifest.img` holds only `build.prop` and `etc`, carries no file hashes,
  and is itself compressed

What remains is porting `unpack_compacted_index` / `decode_compactedbits` plus
the pcluster-length rules, with LZ4 block decode from the `lz4` Python package.
That is a project, not a patch, which is why it is not done here.

It is now **optional**. Rejecting the cloud-path hypothesis established which
fields are missing, and the working profiles in the export ROM show what a
complete profile looks like, so the CN profile would only confirm exact values
for `la_score` / `la_tlevel` rather than identify the gap.

## Not done

- §12.2 (second, non-AMap card): `com.baidu.BaiduMap` is not installed.
- Rollback has not been executed end to end; the four APKs are preserved in
  `vendor/rollback/` and re-verified on device at `/data/local/tmp/rollback/`.
