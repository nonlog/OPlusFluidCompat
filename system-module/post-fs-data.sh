#!/system/bin/sh
# Force PackageManagerService to re-parse the overlaid system APKs.
#
# PMS caches its parsed package records in /data/system/package_cache and the
# cache validity check does not notice an in-place replacement of a system APK
# at the same path. Observed on PJD110_16.0.10.501: after the module overlaid
# /product/priv-app/SceneService/SceneService.apk with the CN 17.6.0 build, PMS
# kept serving the export parse (versionCode 17003004, and the export
# networkSecurityConfigRes). The app process then loaded the CN resource table
# while ApplicationInfo still carried the export resource id, and crashed with
#
#   Unexpected start tag: found androidx.preference.PreferenceScreen,
#   expected network-security-config
#
# `dumpsys package` kept reporting 17.3.4 while the file on disk hashed to the
# CN build, and the mismatch is invisible from adb because the mount is correct
# in every namespace. Removing the affected cache entries is what makes PMS
# re-read the APK.
#
# Only these packages are dropped, and only once per module versionCode. Do NOT
# wipe the whole directory: a full PMS rescan aborts on an unrelated OOS overlay
# allowlist entry (GmsConfigOverlaySearchSelector) and bootloops system_server.

MODDIR=${0%/*}
TAG=oplusfluidcompat_system
CACHE=/data/system/package_cache
STAMP=/data/adb/oplusfluidcompat_system.cachever

log_msg() {
  echo "[$TAG] $1" > /dev/kmsg 2>/dev/null
  log -t "$TAG" "$1" 2>/dev/null
}

want=$(sed -n 's/^versionCode=//p' "$MODDIR/module.prop" 2>/dev/null)
if [ -z "$want" ]; then
  log_msg "no versionCode in module.prop, skipping cache invalidation"
  exit 0
fi
if [ "$(cat "$STAMP" 2>/dev/null)" = "$want" ]; then
  exit 0
fi
if [ ! -d "$CACHE" ]; then
  log_msg "no $CACHE, skipping cache invalidation"
  exit 0
fi

# /product APKs cache under their base name; /data/app updates cache under the
# package name plus the install-dir suffix.
for dir in "$CACHE"/*/; do
  for pat in SceneService UMS TravelEngine \
             com.coloros.sceneservice com.oplus.pantanal.ums com.oplus.travelengine; do
    for f in "$dir$pat"-* "$dir$pat".*; do
      [ -e "$f" ] || continue
      rm -f "$f" && log_msg "dropped stale package_cache entry $(basename "$f")"
    done
  done
done

echo "$want" > "$STAMP"
log_msg "package_cache invalidated for versionCode $want"
