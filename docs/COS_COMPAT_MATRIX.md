# PJD110 CN vs CPH2573 OOS 组件兼容性矩阵

Source firmware: `PJD110_16.0.10.501(CN01)`, OTA MD5 `ec2001b7d8e6b945308f152aa773a26b`.
Blobs (ignored): `vendor/PJD110_CN/`. Device: CPH2573_16.0.10.501(EX01), KernelSU.
Date: 2026-09-23 (updated 2026-09-24). Toolchain note: offline EROFS work done via on-device
`/system/bin/fsck.erofs --extract`; `tools/erofs_min.py` kept as-is (dirs +
uncompressed files only).

## 1. 版本对照

| 包名 | OOS factory | OOS active (/data/app) | PJD110 CN (my_stock) |
|---|---|---|---|
| `com.oplus.pantanal.ums` | 16.59.6 / 16059006 | 17.17.0 / 17017000 | **16.59.6 / 16059006** |
| `com.coloros.sceneservice` | 17.3.4 / 17003004 | 17.7.10 / 17007010 | **17.6.0 / 17006000** |
| `com.oplus.travelengine` | absent | absent | **15.0.25 / 15000025** (targetSdk 34) |

Package names match exactly for UMS and SceneService. TravelEngine is new.

## 2. 签名

All five APKs share one cert (OPPO ColorOS `AndroidTeam`, OU=ColorOS):

```text
SHA256: 64:AA:FA:F1:D5:BC:91:55:A9:E4:17:A8:49:E4:F8:ED:A1:D0:D1:34:16:67:C2:8E:D7:C4:43:C7:6F:82:0B:9A
```

- CN UMS / CN SceneService / OOS active UMS / OOS active SceneService /
  OOS factory UMS / OOS factory SceneService: identical, same lineage.
- Exception: TravelEngine is signed by the **Oplus OSTeam** cert
  (`B0:A9:BB:FC:...:C8:18`), different from the OPPO cert above.
  New package install (no conflict), but note for `privapp-permissions` mapping.

No signature blocker for UMS/SceneService replacement. No global
signature-check weakening required (§4 rule 10 holds).

## 3. APK 哈希 (SHA-256)

```text
9950ab45338bdefa49b9d38f87624de6b1464903015c0b8e14d6f43549845aa8  UMS.apk (109 MB)
6264a89c61e5a17dd020f5a7581ebf60c99c46edf134bc2d2cefeab23719561e  SceneService.apk (70 MB)
243fd5b5c8a4f3e36fc8441254a63e23afe7721fd29ff7330213182099864d64  TravelEngine.apk (8 MB)
b4b5f74d5084b29cfa6c44218d93877ee3022e6fff6dd5566b59ca4171bb846d  my_region/build.prop
ca311d9b135ab43429c8c73d2ba1f32c36807c5fe26523ee17e4874d1c6866db  com.oplus.app-features.xml
8c61f268e21af72fbb636b479b20831d8234fa26be9fef6e296c55f663f89e12  com.oplus.oplus-feature.xml
```

Shipped by the companion module: **TravelEngine.apk only** (plus the two
privapp-permissions XMLs). The UMS, SceneService and `my_region` entries above
are archived references from the CN firmware and are deliberately not
overlaid — see §8 for why the `my_region` overlay had to be removed.

## 4. 版本升降级分析（关键）

- **UMS**: CN 16059006 < active 17017000, and CN == factory 16059006 exactly.
  After `uninstall-system-updates`, the factory entry disappears from
  `/data/app` and the CN overlay (same versionCode, same cert) becomes the
  system package with no downgrade path involved.
- **SceneService**: CN 17006000 > factory 17003004, < active 17007010.
  Same procedure: roll back the `/data/app` update, CN overlay (higher than
  factory) becomes active.
- **TravelEngine**: fresh `com.oplus.travelengine` system install, no conflict.

So the §3.5/§8.5 `uninstall-system-updates` path is mandatory, and it is
sufficient — no version spoofing needed.

**Actual device state (2026-09-24, after the §8 fix + reboot):**

```text
com.oplus.pantanal.ums    -> /data/app/~~SU3lTJb.../com.oplus.pantanal.ums-.../base.apk
                             (17.17.0 update, untouched, active)
com.coloros.sceneservice  -> /product/priv-app/SceneService/SceneService.apk
                             (factory 17.3.4 / 17003004; NO /data/app copy exists)
com.oplus.travelengine    -> /product/priv-app/TravelEngine/TravelEngine.apk
                             (module overlay, 15.0.25)
```

The SceneService 17.7.10 `/data/app` update is gone entirely — no
`/data/app/*sceneservice*` directory remains. It disappeared while the package
was blacklisted (§8); once the region config restored the package, PMS
re-registered the factory `/product/priv-app` copy only. So the CN 17.6.0
overlay is not needed to make SceneService exist or to make it the active
copy, and it is deliberately not shipped.

## 5. 特权许可 (privapp-permissions)

From `my_stock/etc/permissions/` (PJD110 CN):

- `com.oplus.pantanal.ums.privapp_permissions.xml` (dedicated file):
  `INTERACT_ACROSS_USERS`, `BIND_APPWIDGET`.
- `privapp-permissions-oplus.xml`:
  - `com.oplus.travelengine`: `WRITE_SECURE_SETTINGS`, `REAL_GET_TASKS`,
    `LOCAL_MAC_ADDRESS`.
  - `com.coloros.sceneservice`: `MOUNT_UNMOUNT_FILESYSTEMS`,
    `WRITE_SECURE_SETTINGS`, `BATTERY_STATS`, `SCHEDULE_EXACT_ALARM`.
- No SceneService/TravelEngine entries in `etc/sysconfig/` (none exists there).

Module must ship the UMS file verbatim plus extracted
travelengine/sceneservice stanzas. OOS-side allowlist diff still to verify
post-install via `dumpsys package`.

## 6. 国内业务能力确认 (§7)

- CN UMS contains `calculate/package_mapping_config.json`,
  `order_mapping_config.json`, `calculate_strategy_config.json`,
  `host_less_card_config.json`, `serviceId/service_info_config.json`.
- `package_mapping`: AMap (`com.autonavi.minimap` → service `536878000`),
  Baidu Map (`268439607`). Meituan/Taobao/JD/Alipay absent from the json
  mapping but their strings (`meituan`, `taobao`, `jingdong`, `alipay`,
  `eleme`) exist in UMS + SceneService dex; TravelEngine dex references
  meituan/taobao/eleme. Order/logistics path likely via SceneService UPKs
  (`assets/TextIntentSeedling.upk` present) rather than UMS package mapping.
- SceneService CN has `seedling-core_domesticRelease` components;
  UMS bundles `SeedlingSdk.apk` (lite + standard).

## 7. 模块路径修正

PJD110 keeps the stack in **`my_stock/priv-app/`**, not
`system/product/priv-app/` as in the OnePlus 13 reference. The companion
module must overlay the paths the OOS runtime actually resolves.

**Resolved 2026-09-24:** overlaying `product/` in the module tree (module root
`product/...` → `/product/...`) is the correct stake. Verified:
`pm path com.oplus.travelengine` →
`/product/priv-app/TravelEngine/TravelEngine.apk`.

**Hazard found:** `hybrid_mount` treats a module that provides files at a
partition *root* differently from one that provides only subdirectories. A
root-level file (e.g. `my_region/build.prop`) triggers its "shallow overlay"
path, which mounts an extra overlay on the partition root and binds a staging
`etc` over `<partition>/etc`. Do not place root-level files under a partition
directory in this module — see §8.

## 8. `com.coloros.sceneservice` 从 PMS 消失的根因（2026-09-24 定位并修复）

### 8.1 机制

`com.android.server.pm.OplusAppConfigManager`（`oplus-services.jar`，
version 1.0.12）在 PMS boot scan 期间调用
`isSkipAppInBootScanStage(ParsedPackage, boolean)`。命中黑名单的预装系统包
直接返回 true，PMS 因此**完全不注册**该包：

```java
if (this.mBlackSystemAppsList.contains(packageName)
        || customizePmsFeature.isUninstalledByCustomize(packageName)) {
    Slog.w(TAG, "package " + packageName + " locate " + codePath + " ignored.");
    return true;
}
```

所以 `locate ... ignored` 不是「被禁用」，是「根本没安装」——这也解释了为什么
`pm install-existing com.coloros.sceneservice` 报 `NameNotFoundException`，
而 `pm list packages -u` 里也没有它。

黑名单由 `loadAppConfigFromFiles()` 构建，`findXmlFile()` 会读取每个分区
`<partition>/etc/config/` 下的**所有** `*.xml`（不只是 `app_v2.xml`）。
分区默认优先级：

| 分区 | 默认 priority |
|---|---|
| `/my_stock` | 10 |
| `/my_region` | 9 |
| `/my_carrier`、`/my_company` | 8 |
| `/my_product` | 7 |
| `/my_product/cust/<ro.oplus.pipeline.region>`、`/my_carrier/cust`、`/my_manifest` | 6 |

`parseOneItem` 对同一包名**保留最小的 priority**；`enable` 只有在
`enablePriority <= disablePriority` 时才把该包从 `disableMap` 移除：

```java
if (disableMap.containsKey(name)
        && enableEntry.getValue().intValue() <= disableMap.get(name).intValue()) {
    disableMap.remove(name);
}
```

本机（IN / 16 GB）：

- `/my_stock/etc/config/app_v2.xml`：
  `<disable pkg="com.coloros.sceneservice" priority="10"/>`
- `/my_region/etc/config/app_v2.xml`：
  `<enable pkg="com.coloros.sceneservice" priority="9" memory="8,12,16,24,32"/>`

`memory=` 门限通过：MemTotal 15568920 kB = 14.85 GB，
`getTotalProcMemInfoGB()` 上取整到 16，命中列表。按源码 9 ≤ 10，
sceneservice **不应**进黑名单。

### 8.2 实际发生的事

模块曾提供 root 级文件 `my_region/build.prop`，`hybrid_mount` 因此对
`/my_region` 走 "shallow overlay" 路径：

```text
overlay mount success: /my_region/etc   (lowerdir = 模块 my_region/etc | /my_region/etc)
shallow overlay prepare: index=0, target=/my_region
overlay mount success: /my_region       (lowerdir = 只含 build.prop 的 staging | /my_region)
bind mount: src=./etc, dst=/my_region/etc
```

随后 ZygiskNext 在 zygote/system_server 命名空间卸载模块挂载：

```text
zn-daemon64: Unmounted (/my_region)
zn-daemon64: Unmounted (/my_region/etc)
```

暴露出来的正是那个 staging `etc`，于是 system_server 看到的
`/my_region/etc` 是空的 —— 同一进程、同一瞬间的两条日志互为佐证：

```text
23:15:19.156 OplusAppConfigManager: No directory /my_region/etc/config, skipping
23:15:19.150 CustomizeStateHelper:  No directory /my_region/etc/extension, skipping
```

`/my_region/etc/config/app_v2.xml` 读不到 → region 的 `enable priority=9`
进不了 `enableMap` → my_stock 的 `disable priority=10` 存活 → 命中黑名单：

```text
Black List version : 2
    ...
    com.coloros.sceneservice
    ...
package com.coloros.sceneservice locate /product/priv-app/SceneService/SceneService.apk ignored.
```

对照组印证：所有靠 **my_region** enable 救回的包全部消失
（`com.coloros.sceneservice`、`com.heytap.cloud`、`com.oplus.pay`、
`com.oplus.themestore`、`com.redteamobile.roaming`），而靠
**my_stock / my_product** enable 救回的包都在（`com.google.android.euicc`@6、
`com.oplus.aimemory`@7、`com.oplus.omoji`@7、`com.oplus.appbooster`@7）。

### 8.3 该 overlay 本身也无效

- init 在 KernelSU 挂载**之前**读完 `ro.*`，bind 的 `my_region/build.prop`
  改不了 region。实测运行时仍是 `ro.oplus.pipeline.region=IN`、
  `ro.oplus.image.my_region.type=IN_all`。
- `OsFeatureConfiguration` 在 `08:18:32` 读
  `/my_region/etc/extension/*.xml`，而 hybrid_mount 在 `08:18:37` 才挂
  overlay —— 它读到的是原版文件。
- ZygiskNext 会在 system_server 命名空间卸载模块挂载，模块提供的
  `my_region/etc/extension/*` 同样到不了该进程。

### 8.4 修复与验证

模块整体移除 `my_region/` 树（`build.prop` + 2 个 extension XML）及
`post-mount.sh` 的三条 bind，`/my_region` 保持原生。重启后：

```text
OplusAppConfigManager: xml file exists in /my_region/etc/config/app_v2.xml
Black List version : 2          （不再含 sceneservice 及其余 4 个 region enable 包）
pm path com.coloros.sceneservice
package:/product/priv-app/SceneService/SceneService.apk
ps -A → com.coloros.sceneservice、com.oplus.pantanal.ums、com.oplus.pantanal.ums:cardconfig 均在运行
```

黑名单精确减少 5 项，全部是依赖 region `<enable>` 的包。SceneService 进程随后
正常处理场景事件（`SceneService: notifyAppSceneChanged ...`）。


