# China identity compatibility: evidence and limits

## Goal and acceptance

The requested goal is the native China ColorOS Live Alert app ecosystem on
OxygenOS, not an AMap-only notification wrapper. A visible native AMap navigation
surface and real data from another native app are separate acceptance checks.
Neither a passed build, a larger scope list nor an ordinary capsule meets them.

## Inspected baseline

- OxygenOS 16 on OnePlus CPH2573; Android 16.
- UMS 17.17.0 APK SHA-256:
  `62f23876b5ef09eea5175d9b71904b852a698de6d9fa65de83a51126d0cfb1ba`.
- SystemUIPlugin 16.001.002 retains the existing AMap fingerprint guard.
- RUS seedling-package configuration 2026072310 already contains native AMap
  service IDs 536878018 / 536879184 and several China delivery services.

The existence of those RUS entries is not proof that the corresponding native
services have been loaded, registered, supplied with app data and selected.

## Implemented identity layer

Changes are in memory inside enabled LSPosed scopes. Only these exact region
getters are changed to `CN`:

- `ro.oplus.regionmark`
- `ro.vendor.oplus.regionmark`
- `ro.oplus.pipeline.region`

Only the observed export/OOS feature names in `ChinaCompatibilityPolicy` are
masked. Unknown features, hardware capabilities and permissions are untouched.
The native SDK's `getApplicationInfo("com.oplus.pantanal.ums", GET_META_DATA)`
receives a copy with `IS_EXPORT=false`; the original cached ApplicationInfo and
Bundle are not modified.

SystemUI applies this only to native Live Alert stack frames. The other supported
native host processes apply it locally. A client must both be selected in LSPosed
and declare an OPlus integration: immersive metadata, card authentication metadata,
a Seedling provider, FlashViews permission or FanZai provider. These declarations
identify candidates, not proof of functional support or security trust.

There is no caller/UID override, signature bypass, OCS authentication bypass,
global ASSISTANT grant, synthetic service record, replacement map or new generic
notification conversion. The existing experimental AMap mapping is retained.

## Measured remaining distinctions

1. The inspected UMS region reader uses `ro.oplus.pipeline.region`, not just the
   commonly suggested `ro.oplus.regionmark`.
2. Its export tier resolver maps IN/ID to exportIn and several other regions to
   exportPH. Any unmapped region, including CN, falls back to exportIn. There is
   no documented domestic tier to substitute in this binary.
3. Some UMS export behavior is compiled in. Changing a property or SDK metadata
   must not be reported as changing those constants or supplying missing assets.
4. UMS package scanning expects actual SEEDLING_CARD providers with
   `oplus.seedling.provider` descriptors, then applies repository/auth checks.
   AMap and Meituan do not necessarily use that same provider protocol.
5. The inspected Meituan FanZai provider accepts its own process or the genuine
   signed SceneService caller. It also requires a valid method and actual order
   prerequisites. Those checks are preserved. No order will be placed for testing.

The historical OShin decompile shows broad heuristic registration-hook searches;
that alone is not a verified recipe for this OxygenOS export build.

## Verification record

At source creation, 0.7.0 has not been built, installed or functionally validated.
The installed 0.6.0 baseline is not accepted as full native compatibility by the
device owner. Device-side results must be appended after testing, without
publishing PINs, route coordinates, account details, registration codes or orders.

Required checks: CI tests and APK; exact installed version; enabled LSPosed scopes;
per-process module load; actual identity getter hits; native provider/decision
progress; AMap host token and SurfacePackage; visible map; another real app.

## Rollback

Disable the module, then restart the affected processes or reboot. No property
file, system APK, authentication database or permanent permission is written by
this layer. Native components may have their own persistent caches. The earlier
AMap display-mode diagnostic is documented separately in V041_NATIVE_RESULT.md.
