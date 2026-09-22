# Native AMap path: 2026-09-22 investigation

## Verified baseline (0.3.0 / CI16)

Device: CPH2573, OxygenOS 16. SystemUIPlugin 16.001.002 (16001002).
Plugin APK SHA-256:
`d7c0a5dc11f40e7c89b2687a5a89a7db1fa2365ddb3fb3110214b2e7f19862db`.

The installed 0.3.0 APK matches CI16. Both SystemUI hooks load and are hit by real
AMap navigation. A controlled navigation session enters `liveAlertList` as foreground
notification 99910001, category `navigation`, channel `ROUTE_CHANNEL_ID`.

This produces only the ordinary navigation capsule/card, NOT the native immersive map.
Do not call that result functional success.

Observed host log:

```
rusBaseData: null, immersiveCardType:2
NofificationInfoConvert-->setImmersiveEnable, enable=false, serviceEnable=true
```

No AMapImmerseNaviService binding exists in the captured services dump.
Private logs and screenshots remain in local `logs/v03-navigation-*`; they are not
committed because they can contain routes, notification contents and other personal data.

## Concrete defects

1. In the inspected plugin's NotificationController (`w5.i`), type **2** requests a
   template immersive view. The alternate branch with a nonempty `liveAlertService`
   constructs `z5.i` / `a6.l` and communicates with the app's remote SurfaceView.
   Use type **1** for that immersive remote surface, not type 2.
2. `o5.j.y()` requires RUS `lockImmersiveEnable`; `o5.j.C()` uses
   `lockImmersiveDefault`. The production AMap RUS entry is absent. The ROM already
   includes `laid_com.amap.navi.demo` with both flags enabled. The actual service
   permission check succeeds from AMap's `livealert.immersive.card=1` metadata.

## 0.4.0 implementation and safety boundary

- Resolve the real Seedling classloader from the SystemUI filter's plugin instance.
- Pin the obfuscated RUS profile to the exact inspected plugin APK hash and validate
  its method, field and constructor schema. An unknown plugin fails closed rather
  than applying unverified obfuscated hooks.
- Only missing `laid_com.autonavi.minimap` configurations (including notification-tag
  variants) receive a copy of the ROM's existing native navigation profile. Keep the
  production package ID in the copy. Never mutate the source profile, database or
  existing production configuration.
- Eligibility override is limited to AMap foreground navigation notifications,
  excluding group summaries. Other packages and notification types remain unchanged.
- Preserve the original super-power, permission, user-state and lifecycle checks.
- Log native host-token message 11 and SurfacePackage reply 21, without route content.
- Static LSPosed scope remains only `com.android.systemui`.

The historical UMS/OCS empty repositories describe another integration path. They do
not establish impossibility for this native SystemUIPlugin host path. Historical
FlashViews-only hooks and Instant scope-only tests are not repeated.

## Acceptance status

0.4.0 is **not yet functionally validated** at the time this change is authored.
GitHub Actions must build the APK, which must then be installed. A real navigation
session must bind AMap's service, receive its surface, and visibly show the native map.
Meituan has a different integration and remains unimplemented/unvalidated.

Rollback: disable this module's SystemUI scope (or the module) and restart SystemUI.
No UMS/OCS/Instant data migration or permanent permission change is made.
