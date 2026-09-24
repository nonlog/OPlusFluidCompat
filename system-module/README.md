# OPlusFluidCompat-System

Magisk/KernelSU companion module for OPlusFluidCompat. Restores the minimum
proven ColorOS domestic Fluid Cloud stack from **PJD110_16.0.10.501(CN01)**:

- `system/product/priv-app/TravelEngine/TravelEngine.apk` — new system app
- `system/product/etc/permissions/` — verbatim CN privapp-permission XMLs
  (UMS + SceneService stanzas included; neither APK is overlaid — see
  `vendor-manifest.json` "excluded" for the observed PMS verdicts)

No `my_region` files are shipped. Root-level module files under `my_region/`
make `hybrid_mount` bind a staging `etc` over `/my_region/etc`, which leaves
that directory empty inside the system_server namespace once ZygiskNext
unmounts module mounts. `OplusAppConfigManager` then cannot read
`/my_region/etc/config/app_v2.xml`, so the region
`<enable pkg="com.coloros.sceneservice" priority="9">` never cancels the
`my_stock` `<disable ... priority="10">`, and `com.coloros.sceneservice` is
dropped from the PMS boot scan. Keeping `/my_region` untouched is what lets
the stock region config restore SceneService. Full evidence chain in
`vendor-manifest.json` "excluded".

Out of scope on purpose: SystemUI replacement, full my_carrier/my_product/
my_stock mirrors, signature-check weakening, app-data clearing.

## Install

1. `pm uninstall-system-updates com.coloros.sceneservice`
   (after pulling rollback APKs — see `docs/COS_COMPAT_MATRIX.md` §4).
   UMS keeps its `/data/app` update; do NOT touch it.
2. Flash the module ZIP, reboot.
3. Verify: `pm path` shows `/product/priv-app/...` (not `/data/app/...`)
   and the APK hashes match `system-module/vendor-manifest.json`.

## Recovery

Disable the module in KernelSU/Magisk manager and reboot. If boot fails,
create the disable marker via recovery/ADB:

```sh
touch /data/adb/modules/oplusfluidcompat_system/disable
```

Then reboot. Original `/my_region` files and factory packages return.
