# OPlusFluidCompat

LSPosed compatibility module for OnePlus/OPlus Live Alerts / Fluid Cloud / FlashViews on OxygenOS.

## What it does

OxygenOS contains the same FlashViewsService used by ColorOS, but SystemUI applies additional support-list, region and caller-certificate checks before a third-party app can bind to it.

This module hooks those checks in com.android.systemui and bypasses them only for apps that already request:

com.oplus.flashback.permission.FLASH_VIEWS_SERVICE

That keeps the scope limited to apps that intentionally integrate the OPlus FlashViews SDK instead of enabling arbitrary apps.

Current hooks:

- SettingsUtils.isValidCaller(Context, String) - caller/signature gate
- ConfigurationManager.isSupportFlashViews(String) - support-list/region gate
- ConfigurationManager.isSupportFlashViews(Context, int) - UID support gate

## Tested target

Initial reverse engineering was performed on OnePlus CPH2573, Android 16, OxygenOS CPH2573_16.0.10.501(EX01), OPlus ROM base V16.1.0.

The device SystemUI already contains com.oplus.flashback.service.FlashViewsService. Meituan and AMap both contain OPlus integration paths on the tested device.

## Build

Artifacts are built only by GitHub Actions. The workflow uploads the installable APK as OPlusFluidCompat-apk.

## Safety / scope

The module does not patch SystemUI or modify its databases. It performs runtime hooks only. If the required OPlus classes are missing, the relevant hook fails closed and the original system behavior remains unchanged.
