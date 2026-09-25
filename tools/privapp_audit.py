#!/usr/bin/env python3
"""Privileged-permission allowlist auditor for OPlusFluidCompat-System (plan 11.2 / 13 Case B).

On a user build with ro.control_privapp_permissions=enforce, a privileged app that
requests a permission carrying PROTECTION_FLAG_PRIVILEGED without a matching
<privapp-permissions> entry makes PermissionManagerServiceImpl throw at
systemReady(), which takes system_server down and bootloops the device.

This tool answers, offline and before any reboot:
    "will this APK bootloop the device if I drop it into /product/priv-app?"

It parses binary AXML itself (APK manifests and on-device permission XMLs are the
same format), so it needs no aapt, no jadx and no device.

Usage:
  # collect the device's declared permissions + allowlists first (see --help-notes)
  python tools/privapp_audit.py --perms-dir out/privapp-audit \
      --apk vendor/PJD110_CN/priv-app/UMS.apk --package com.oplus.pantanal.ums

  # compare two builds of the same package
  python tools/privapp_audit.py --perms-dir out/privapp-audit \
      --apk vendor/PJD110_CN/priv-app/UMS.apk --package com.oplus.pantanal.ums \
      --diff vendor/rollback/UMS-16.59.6-factory.apk
"""

from __future__ import annotations

import argparse
import os
import struct
import sys
import zipfile
from collections import defaultdict

# ---------------------------------------------------------------- AXML parsing

RES_STRING_POOL_TYPE = 0x0001
RES_XML_TYPE = 0x0003
RES_XML_RESOURCE_MAP_TYPE = 0x0180
RES_XML_START_ELEMENT_TYPE = 0x0102
RES_XML_END_ELEMENT_TYPE = 0x0103

TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12
TYPE_STRING = 0x03
TYPE_REFERENCE = 0x01
TYPE_NULL = 0x00

# android.content.pm.PermissionInfo protection flags
FLAG_PRIVILEGED = 0x10
FLAG_DEVELOPMENT = 0x20
FLAG_APPOP = 0x40
FLAG_PRE23 = 0x80
FLAG_INSTALLER = 0x100
FLAG_VERIFIER = 0x200
FLAG_OEM = 0x400
FLAG_SYSTEM_TEXT = 0x800
FLAG_MODULE = 0x1000
FLAG_INSTANT = 0x2000
FLAG_RUNTIME_ONLY = 0x4000
FLAG_ROLE = 0x8000

BASE_LEVELS = {0: "normal", 1: "dangerous", 2: "signature", 3: "signatureOrSystem"}

FLAG_NAMES = [
    (FLAG_PRIVILEGED, "privileged"),
    (FLAG_DEVELOPMENT, "development"),
    (FLAG_APPOP, "appop"),
    (FLAG_PRE23, "pre23"),
    (FLAG_INSTALLER, "installer"),
    (FLAG_VERIFIER, "verifier"),
    (FLAG_OEM, "oem"),
    (FLAG_SYSTEM_TEXT, "system_text"),
    (FLAG_MODULE, "module"),
    (FLAG_INSTANT, "instant"),
    (FLAG_RUNTIME_ONLY, "runtime_only"),
    (FLAG_ROLE, "role"),
]


class AxmlError(Exception):
    pass


class StringPool:
    def __init__(self, data: bytes, offset: int):
        (self.chunk_type, header_size, chunk_size) = struct.unpack_from("<HHI", data, offset)
        if self.chunk_type != RES_STRING_POOL_TYPE:
            raise AxmlError(f"expected string pool at {offset:#x}, got {self.chunk_type:#x}")
        (self.string_count, self.style_count, self.flags,
         self.strings_start, self.styles_start) = struct.unpack_from("<IIIII", data, offset + 8)
        self.utf8 = bool(self.flags & (1 << 8))
        base = offset + header_size
        self.offsets = struct.unpack_from(f"<{self.string_count}I", data, base) if self.string_count else ()
        self.data_start = offset + self.strings_start
        self._data = data
        self._cache: dict[int, str] = {}

    def __len__(self) -> int:
        return self.string_count

    def get(self, index: int) -> str:
        if index == 0xFFFFFFFF:
            return ""
        if index in self._cache:
            return self._cache[index]
        if index >= self.string_count:
            value = f"<bad index {index}>"
            self._cache[index] = value
            return value
        p = self.data_start + self.offsets[index]
        if self.utf8:
            # two varint-ish lengths: character count, then byte count
            _, p = _u8len(self._data, p)
            n, p = _u8len(self._data, p)
            value = self._data[p:p + n].decode("utf-8", "replace")
        else:
            n, p = _u16len(self._data, p)
            value = self._data[p:p + n * 2].decode("utf-16-le", "replace")
        self._cache[index] = value
        return value


def _u8len(data: bytes, p: int) -> tuple[int, int]:
    v = data[p]
    if v & 0x80:
        return ((v & 0x7F) << 8) | data[p + 1], p + 2
    return v, p + 1


def _u16len(data: bytes, p: int) -> tuple[int, int]:
    (v,) = struct.unpack_from("<H", data, p)
    if v & 0x8000:
        (v2,) = struct.unpack_from("<H", data, p + 2)
        return ((v & 0x7FFF) << 16) | v2, p + 4
    return v, p + 2


def _typed_value(data: bytes, data_type: int, value: int, pool: StringPool) -> str | None:
    if data_type == TYPE_STRING:
        return pool.get(value)
    if data_type == TYPE_INT_DEC:
        return str(value)
    if data_type == TYPE_INT_HEX:
        return f"0x{value:x}"
    if data_type == TYPE_INT_BOOLEAN:
        return "true" if value else "false"
    if data_type in (TYPE_NULL, TYPE_REFERENCE):
        return None
    return str(value)


END = "\x00end"


def iter_start_elements(path_or_bytes, _name: str = ""):
    """Yield (element_name, {attribute_name: value}) for every start element.

    End elements are yielded as (END, {"name": <element being closed>}) so callers
    can maintain a nesting stack.
    """
    data = path_or_bytes if isinstance(path_or_bytes, bytes) else open(path_or_bytes, "rb").read()
    (chunk_type, header_size, total) = struct.unpack_from("<HHI", data, 0)
    if chunk_type != RES_XML_TYPE:
        raise AxmlError(f"not a binary XML document (chunk type {chunk_type:#x})")

    pool: StringPool | None = None
    offset = header_size
    end = min(total, len(data))

    while offset + 8 <= end:
        (ctype, cheader, csize) = struct.unpack_from("<HHI", data, offset)
        if csize < 8 or offset + csize > len(data):
            break
        if ctype == RES_STRING_POOL_TYPE:
            pool = StringPool(data, offset)
        elif ctype == RES_XML_START_ELEMENT_TYPE:
            if pool is None:
                raise AxmlError("start element before string pool")
            (line, _comment, _ns, name_idx) = struct.unpack_from("<IIII", data, offset + 8)
            (attr_start, attr_size, attr_count,
             _id_idx, _class_idx, _style_idx) = struct.unpack_from("<HHHHHH", data, offset + 24)
            attrs: dict[str, str] = {}
            # attributeStart is relative to ResXMLTree_attrExt (offset 16), not the chunk
            ap = offset + 16 + attr_start
            for _ in range(attr_count):
                if ap + 20 > len(data):
                    break
                (ans, aname, araw, _asize, _ares0, adtype, adata) = struct.unpack_from("<IIIHBBI", data, ap)
                key = pool.get(aname)
                if araw != 0xFFFFFFFF:
                    val = pool.get(araw)
                else:
                    val = _typed_value(data, adtype, adata, pool)
                if key and val is not None:
                    attrs[key] = val
                ap += attr_size or 20
            yield pool.get(name_idx), attrs
        elif ctype == RES_XML_END_ELEMENT_TYPE:
            if pool is not None:
                (_line, _comment, _ns, name_idx) = struct.unpack_from("<IIII", data, offset + 8)
                yield END, {"name": pool.get(name_idx)}
        offset += csize


def apk_manifest(apk_path: str) -> bytes:
    with zipfile.ZipFile(apk_path) as z:
        return z.read("AndroidManifest.xml")


def iter_elements(source):
    """Dispatch on file format: binary AXML, or plain-text XML (very common under
    /vendor/etc/permissions and /odm/etc/permissions on this device)."""
    data = source if isinstance(source, bytes) else open(source, "rb").read()
    if len(data) >= 4 and struct.unpack_from("<H", data, 0)[0] == RES_XML_TYPE:
        yield from iter_start_elements(data)
    else:
        yield from _iter_text_elements(data)


def _iter_text_elements(data: bytes):
    import xml.etree.ElementTree as ET

    root = ET.fromstring(data)

    def walk(elem):
        tag = elem.tag.split("}")[-1]
        yield tag, {k.split("}")[-1]: v for k, v in elem.attrib.items()}
        for child in elem:
            yield from walk(child)
        yield END, {"name": tag}

    yield from walk(root)


def protection_level_str(value: str | None) -> str | None:
    """Normalise an android:protectionLevel value to a canonical string, or None."""
    if value is None:
        return None
    value = value.strip()
    if not value:
        return None
    if value.lower().startswith("0x"):
        raw = int(value, 16)
        parts = BASE_LEVELS.get(raw & 0xF, f"base{raw & 0xF}")
        for bit, nm in FLAG_NAMES:
            if raw & bit:
                parts += "|" + nm
        return parts
    if value.isdigit():
        raw = int(value)
        parts = BASE_LEVELS.get(raw & 0xF, f"base{raw & 0xF}")
        for bit, nm in FLAG_NAMES:
            if raw & bit:
                parts += "|" + nm
        return parts
    return value


def is_privileged_level(level: str | None) -> bool:
    if not level:
        return False
    return "privileged" in level.split("|") or level == "signatureOrSystem"


# ---------------------------------------------------------------- collectors


class DevicePermissions:
    """protectionLevel declarations + privapp allowlists harvested from device XMLs."""

    def __init__(self):
        self.levels: dict[str, str] = {}
        self.level_source: dict[str, str] = {}
        self.allowlist: dict[str, set[str]] = defaultdict(set)
        self.allowlist_source: dict[tuple[str, str], str] = {}
        self.declared_by: dict[str, set[str]] = defaultdict(set)
        self.files = 0
        self.errors: list[str] = []
        self.root: str = ""

    def load_dir(self, root: str) -> None:
        self.root = root
        for dirpath, _dirnames, filenames in os.walk(root):
            for fn in sorted(filenames):
                if not fn.lower().endswith(".xml"):
                    continue
                self.load_file(os.path.join(dirpath, fn))

    def load_file(self, path: str) -> None:
        try:
            elements = list(iter_elements(path))
        except Exception as exc:  # noqa: BLE001 - report and continue
            self.errors.append(f"{path}: {exc}")
            return
        self.files += 1
        # keep the path relative to the harvest root so the partition a grant came
        # from is still recoverable (basename alone collides across partitions)
        base = os.path.relpath(path, self.root).replace(os.sep, "/") if self.root else path
        stack: list[str] = []
        for name, attrs in elements:
            if name == END:
                if stack and attrs.get("name") == "privapp-permissions":
                    stack.pop()
                continue
            if name == "privapp-permissions":
                stack.append(attrs.get("package", ""))
            elif name == "permission":
                perm = attrs.get("name")
                if not perm:
                    continue
                if stack:
                    # child of <privapp-permissions package="..."> -> an allowlist grant
                    pkg = stack[-1]
                    if pkg:
                        self.allowlist[pkg].add(perm)
                        self.allowlist_source[(pkg, perm)] = base
                else:
                    # a top-level <permission> declaration -> carries protectionLevel
                    self.declared_by[perm].add(base)
                    level = protection_level_str(attrs.get("protectionLevel"))
                    if level is not None and perm not in self.levels:
                        self.levels[perm] = level
                        self.level_source[perm] = base


def apk_requested_permissions(apk_path: str) -> tuple[set[str], set[str]]:
    """Return (uses-permission names, uses-permission-sdk-23 names)."""
    normal: set[str] = set()
    sdk23: set[str] = set()
    for name, attrs in iter_elements(apk_manifest(apk_path)):
        pname = attrs.get("name")
        if not pname:
            continue
        if name == "uses-permission":
            normal.add(pname)
        elif name == "uses-permission-sdk-23":
            sdk23.add(pname)
    return normal, sdk23


def apk_self_declared(apk_path: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for name, attrs in iter_elements(apk_manifest(apk_path)):
        if name == "permission" and attrs.get("name"):
            out[attrs["name"]] = protection_level_str(attrs.get("protectionLevel")) or "normal"
    return out


# ---------------------------------------------------------------- reporting


def audit(apk: str, package: str, dev: DevicePermissions, assume_privileged: bool) -> int:
    requested, sdk23 = apk_requested_permissions(apk)
    declared = apk_self_declared(apk)
    allowed = dev.allowlist.get(package, set())

    print(f"APK      : {apk}")
    print(f"package  : {package}")
    print(f"privileged app path: {assume_privileged}")
    print(f"requested perms: {len(requested)} (+{len(sdk23)} sdk-23)")
    print(f"allowlist entries for this package from device XMLs: {len(allowed)}")
    print()

    violations: list[tuple[str, str, str]] = []
    unknown: list[str] = []

    for perm in sorted(requested):
        level = dev.levels.get(perm) or declared.get(perm)
        if level is None:
            unknown.append(perm)
            continue
        if not is_privileged_level(level):
            continue
        if perm in allowed:
            continue
        violations.append((perm, level, dev.level_source.get(perm, "self-declared")))

    if violations:
        print("VIOLATIONS (privileged permission requested, not in allowlist):")
        for perm, level, src in violations:
            print(f"  !! {perm}")
            print(f"       protectionLevel={level}  (declared in {src})")
        print()
    else:
        print("No privileged-permission violations found.")
        print()

    if unknown:
        print(f"UNKNOWN protectionLevel for {len(unknown)} requested permission(s) "
              f"(no declaration in the collected XMLs):")
        for perm in unknown:
            print(f"  ?  {perm}")
        print()

    return len(violations)


def diff_permissions(left: str, right: str, left_label: str, right_label: str) -> None:
    lp, ls = apk_requested_permissions(left)
    rp, rs = apk_requested_permissions(right)
    added = sorted(rp - lp)
    removed = sorted(lp - rp)
    print(f"--- permission diff: {left_label} -> {right_label}")
    print(f"    {left_label}: {len(lp)} perms, {right_label}: {len(rp)} perms")
    if added:
        print(f"    ADDED in {right_label} ({len(added)}):")
        for p in added:
            print(f"      + {p}")
    if removed:
        print(f"    REMOVED in {right_label} ({len(removed)}):")
        for p in removed:
            print(f"      - {p}")
    if not added and not removed:
        print("    identical")
    print()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--perms-dir", required=True,
                    help="directory tree of device permission XMLs (framework-res manifest + etc/permissions)")
    ap.add_argument("--apk", required=True, help="APK to audit, or the 'new' side of a --diff")
    ap.add_argument("--package", help="package name the APK will be installed as")
    ap.add_argument("--not-privileged", action="store_true",
                    help="app lands outside priv-app (informational only)")
    ap.add_argument("--diff", metavar="OLD_APK", help="compare against this build of the same package")
    args = ap.parse_args()

    dev = DevicePermissions()
    dev.load_dir(args.perms_dir)
    print(f"harvested {dev.files} XML file(s); "
          f"{len(dev.levels)} permission declarations, "
          f"{len(dev.allowlist)} allowlisted packages")
    if dev.errors:
        print(f"  ({len(dev.errors)} file(s) skipped)")
        for e in dev.errors[:5]:
            print(f"   - {e}")
    print()

    rc = 0
    if args.diff:
        diff_permissions(args.diff, args.apk, os.path.basename(args.diff), os.path.basename(args.apk))
    if args.package:
        rc = audit(args.apk, args.package, dev, not args.not_privileged)
    return 1 if rc else 0


if __name__ == "__main__":
    sys.exit(main())
