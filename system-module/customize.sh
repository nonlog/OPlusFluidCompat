#!/system/bin/sh
# OPlusFluidCompat-System installer. No odex/vdex shipped; ART compiles on device.
MODDIR=${0%/*}

ui_print() { echo "$1"; }

set_perm_recursive $MODPATH 0 0 0755 0644

# Do NOT wipe /data/system/package_cache: a forced full PMS rescan aborts
# on an unrelated OOS overlay allowlist entry (GmsConfigOverlaySearchSelector),
# killing system_server in a bootloop (tombstones 2026-09-23 22:39-22:40).
# Overlaid priv-apps are picked up without a cache wipe.
ui_print "Installed. Reboot, then verify: pm path com.coloros.sceneservice"
