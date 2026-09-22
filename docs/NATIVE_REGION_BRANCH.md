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

## Validation and rollback

CI and device results must be appended after they actually occur. This document
does not claim universal ColorOS application support. Real AMap surface display
and another app with authentic activity/order data remain distinct checks.

Disable the module and restart affected processes to remove the runtime hooks.
No firmware partition, property file, authentication database or security grant
is modified. Private device logs and screenshots are not repository inputs.
