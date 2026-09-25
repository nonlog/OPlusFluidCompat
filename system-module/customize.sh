#!/system/bin/sh
# OPlusFluidCompat-System installer. No odex/vdex shipped; ART compiles on device.
MODDIR=${0%/*}

ui_print() { echo "$1"; }

set_perm_recursive $MODPATH 0 0 0755 0644

# Do NOT wipe /data/system/package_cache: a forced full PMS rescan aborts
# on an unrelated OOS overlay allowlist entry (GmsConfigOverlaySearchSelector),
# killing system_server in a bootloop (tombstones 2026-09-23 22:39-22:40).
#
# Overlaid priv-apps are NOT picked up on their own. PMS reuses a cached parse
# of the export APK and never re-reads the overlaid file, so the package keeps
# its old versionCode and resource ids while the app process loads the CN
# resource table - SceneService then dies with "Unexpected start tag: found
# androidx.preference.PreferenceScreen, expected network-security-config".
# post-fs-data.sh drops just the affected cache entries, once per versionCode.
set_perm $MODPATH/post-fs-data.sh 0 0 0755
set_perm $MODPATH/post-mount.sh 0 0 0755

ui_print "Installed. Reboot, then verify both lines:"
ui_print "  pm path com.coloros.sceneservice   # /product/priv-app/SceneService/SceneService.apk"
ui_print "  dumpsys package com.coloros.sceneservice | grep versionCode   # 17006000"
ui_print "If UMS still shows a /data/app path, run as root:"
ui_print "  pm uninstall-system-updates com.oplus.pantanal.ums"
