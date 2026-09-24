# OPlusFluidCompat-System

Magisk/KernelSU companion module for OPlusFluidCompat. Overlays the ColorOS
domestic Fluid Cloud stack from **PJD110_16.0.10.501(CN01)** onto OOS:

- `system/product/priv-app/UMS/UMS.apk` — CN 16.59.6, adds the domestic
  `assets/calculate/` configs (AMap/Baidu package mapping, order mapping)
  that the export build does not have at all
- `system/product/priv-app/SceneService/SceneService.apk` — CN 17.6.0 with
  `components-seedling-core_domesticRelease` and `assets/TextIntentSeedling.upk`
- `system/product/priv-app/TravelEngine/TravelEngine.apk` — new system app
- `system/product/etc/permissions/` — verbatim CN privapp-permission XMLs

`hybrid_mount` promotes the module's `system/product/...` paths to
`/product/...` on this device, which is what `pm path` must report.

No `my_region` files are shipped: a root-level module file under `my_region/`
makes `hybrid_mount` bind a staging `etc` over `/my_region/etc`, leaving that
directory empty inside the `system_server` namespace once ZygiskNext unmounts
module mounts. `OplusAppConfigManager` then cannot read
`/my_region/etc/config/app_v2.xml`, so the region
`<enable pkg="com.coloros.sceneservice" priority="9">` never cancels the
`my_stock` `<disable ... priority="10">` and SceneService is dropped from the
PMS boot scan. Full evidence chain in `vendor-manifest.json` and
`docs/COS_COMPAT_MATRIX.md` §8.

Out of scope on purpose: SystemUI replacement, full my_carrier/my_product/
my_stock mirrors, signature-check weakening, app-data clearing.

## Install

1. Preserve the rollback APKs first — `vendor/rollback/` already holds them,
   hash-verified against the device:
   `UMS-17.17.0-active.apk`, `UMS-16.59.6-factory.apk`,
   `SceneService-17.3.4-factory.apk`, `SceneService-17.7.10-active.apk`.
2. `pm uninstall-system-updates com.oplus.pantanal.ums`

   The CN UMS is versionCode 16059006 — the *same* as the OOS factory build —
   so the 17.17.0 `/data/app` update outranks it and must be removed or the
   overlay never becomes active. SceneService needs no equivalent step: its
   `/data/app` update is already gone and CN 17.6.0 (17006000) is above the
   factory 17003004.
3. Flash the module ZIP and reboot.
4. Verify: `pm path` reports `/product/priv-app/...` for all three packages,
   `dumpsys package` shows the CN versionCodes, and the installed APK hashes
   match `system-module/vendor-manifest.json`.

## Recovery

Disable the module in KernelSU/Magisk manager and reboot. If boot fails,
create the disable marker via recovery/ADB:

```sh
touch /data/adb/modules/oplusfluidcompat_system/disable
```

Then reboot. Original `/my_region` files and factory packages return. If UMS
was rolled back, reinstall `vendor/rollback/UMS-17.17.0-active.apk`.
