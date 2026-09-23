# Native region branch investigation

## Measured 0.7.0 mismatch

On CPH2573_16.0.10.501(EX01), installed 0.7.0 / versionCode 9 came from
successful CI run 26 (cf81f2a). Its property and UMS metadata hooks did execute.
However, the native FlashViewsService configuration dump still reported:

```text
region: EXP
regionMark: CN
flavor: 2
support: 4
```

`FlavorsHelper.isExpRegion` is assigned from `FlashBackModule.load`'s fourth
argument, captured in its asynchronous initializer. It is not recomputed from
the displayed regionMark. The native initializer skips its domestic service
subscription branch while that argument is true.

The built-in `config region 0` diagnostic changed the effective region to CN
without changing flavor 2 or support mask 4. That experiment did NOT add the
missing cloud service descriptors or prove that another app rendered a card.

## 0.7.1 change

The native module load argument and subsequent `FlavorsHelper.setFlavor` export
argument are changed to false, before native configuration/subscription work.
Feature availability, device-family flavor, tablet mode, RUS parser, user app
switches, caller checks, signatures and OCS authentication are preserved.
This profile fails closed outside the exact inspected Android 16 build.

The existing real AMap surface implementation remains unchanged. No generic
notification converter, synthetic order or fake service response was added.

## Separate UMS export implementation

The inspected UMS 17.17.0 `ConnectManager` implements cloud discovery/domain
queries with empty results and metadata/download methods with failure results.
These are method bodies, not a property-backed region decision. Merely changing
CN or `IS_EXPORT` does not replace them.

`MetaManagerImpl` also exists in the binary, but its presence does not prove it
is called: its ONet service dependency was not found on the inspected device.
0.7.1 logs which public ConnectManager discovery methods actually execute,
without logging parameters, changing their results, enabling unrelated ONet
data collection or installing any replacement system APK.

## 0.8.0 native registration gates

The next compatibility layer targets gates observed in the shipped binaries rather
than fabricating the empty `ConnectManager` cloud responses.

SystemUI's `FlashViewsService.onBind` first calls
`SettingsUtils.isValidCaller(...)`, then requires
`ConfigurationManager.isSupportFlashViews(package)`. The latter requires a RUS entry
with `userEnable=true`, while the former rejects an unregistered third-party package
and verifies configured signers for registered packages. The 0.8.0 hook changes only
the missing-registration/support decisions for real FlashViews clients. Existing RUS
signer mismatches stay rejected, and the native service/data/rendering implementation
remains unchanged.

UMS `Scanner.h(package)` independently exposes another registration gate. It queries
real `com.oplus.seedling.action.SEEDLING_CARD` providers and reads each provider's
`oplus.seedling.provider` manifest metadata, but only accepts the package when it is
already present in the local service repository. Otherwise it logs
`queryAccessPackages noScanLocal:<package>` and discards it. 0.8.0 augments the
scanner result with that real provider and its own `.upk`/`.package` descriptor list;
it does not create a service ID, remote response or business payload.

The SystemUIPlugin's bundled `local_guaranteed_service_info_list_json.json` was also
checked. On this build it contains only local system services (weather, application
suggestions and tips), not AMap, Meituan, JD, Taobao or Baidu Map. Therefore switching
that asset is not a third-party China-service repository replacement.

Both new runtime profiles fail closed outside Android 16 build
`CPH2573_16.0.10.501(EX01)`; the UMS scanner profile additionally requires UMS
versionCode `17017000` (17.17.0).

## Validation and rollback

The verified checkpoint below is not universal ColorOS application support.
Real AMap surface display and another app with authentic activity/order data
remain distinct checks.

Disable the module and restart affected processes to remove the runtime hooks.
No firmware partition, property file, authentication database or security grant
is modified. Private device logs and screenshots are not repository inputs.


## CI 27 and device checkpoint

- Code commit: 7423955fdf0ee569aabddddeb3d761b8c4218d9f.
- Author and committer: Codex <codex@openai.com>.
- GitHub Actions run: 35727615394 / run number 27 / success.
- Release: ci-27, OPlusFluidCompat.apk, 2,121,375 bytes.
- Verified APK SHA-256:
  bb7f31895d8028498089b6a2dafcfdbe33767c62e19cd5bee8e77b1e6404242f.
- In-place ADB install succeeded; installed versionName=0.7.1/versionCode=10.
- Only the relevant UMS and SystemUI processes were restarted. The earlier dump
  experiment had been restored to EXP before this install/restart, so the new
  CN result is not residue from the temporary diagnostic command.

Actual module events after restart:

```text
native FlashBack regional hooks installed: 2/2
native FlashBack initialization: export=true -> false; flavor=2
native FlashBack effective region: CN; flavor=2
native UMS discovery diagnostics installed: 6
native UMS discovery invoked: queryDomainEnableGroup
```

The native service dump confirms region=CN, regionMark=CN, flavor=2, support=4,
FeatureEnable=true. The UMS diagnostic ran in the real main process; no argument
or business payload was logged. Only queryDomainEnableGroup is proven invoked
at this checkpoint. Empty discovery is an active implementation gap, not yet a
proven explanation for every target app's observed behavior.

The attempted fresh AMap UI test encountered the lockscreen and did not produce
a new real-navigation surface result. Do not relabel the historical CI26 cycling
screenshot as CI27 acceptance. No Meituan order or fake notification was created.
A separate manual validation setup enabled Meituan's existing per-app secure
switch from 0 to 1; the module itself preserves user switches. No screen timeout
change was applied (the device still reports 60000).

Private evidence remains in the development checkout's logs directory:
ci27-startup.txt, ci27-lspd.txt and ci27-flash-config.txt. These contain device
context and are intentionally not committed. No phone unlock secret is included.

## Next evidence required

Trace genuine client activity through admission, native data delivery and host
rendering; do not treat a successful identity hook as a successful app test.
For APIs compiled out of this export UMS, obtain a compatible, OEM-signed China
implementation for comparison before deciding whether to restore a call path,
a local provider admission gate or a missing component. Public APK listings alone
do not establish that a candidate is a China variant or safe to replace in place.
