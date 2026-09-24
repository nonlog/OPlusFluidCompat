#!/system/bin/sh
# OPlusFluidCompat-System post-mount hook.
# Currently performs no mounts; see the note at the end of this file.
MODDIR=${0%/*}
TAG=oplusfluidcompat_system

log_msg() {
  echo "[$TAG] $1" > /dev/kmsg 2>/dev/null
  log -t "$TAG" "$1" 2>/dev/null
}

bind_one() {
  src="$1"
  dst="$2"
  if [ ! -f "$src" ]; then
    log_msg "skip $dst (module file missing)"
    return 0
  fi
  if [ ! -f "$dst" ]; then
    log_msg "skip $dst (target missing)"
    return 0
  fi
  if mountpoint -q "$dst" 2>/dev/null; then
    umount "$dst" 2>/dev/null
  fi
  if mount -o bind "$src" "$dst"; then
    log_msg "mounted $dst"
  else
    log_msg "FAILED $dst"
  fi
}

# No binds are performed. The my_region overlays were removed because they
# broke /my_region/etc for system_server - see vendor-manifest.json "excluded"
# for the full evidence chain. bind_one() is kept for future use.
:

