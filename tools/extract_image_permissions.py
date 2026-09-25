"""Extract etc/permissions/*.xml from ColorOS EROFS partition images.

Used to answer: "on real CN firmware, which allowlist file grants package X
permission Y?" -- the answer decides whether the companion module may ship an
invented allowlist entry or must mirror a verbatim CN stanza.

Files whose extents are lz4-compressed cannot be read by erofs_min and are
reported as skipped rather than silently omitted.
"""
import os
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from erofs_min import ErofsImage  # noqa: E402

SUBDIR = "etc/permissions"


def extract(image_path: str, out_root: str) -> tuple[int, int]:
    name = pathlib.Path(image_path).stem
    out_dir = pathlib.Path(out_root) / name
    out_dir.mkdir(parents=True, exist_ok=True)

    img = ErofsImage(image_path)
    try:
        nid = img.resolve(SUBDIR)
    except FileNotFoundError:
        print(f"{name}: no /{SUBDIR}")
        return 0, 0

    ok = skipped = 0
    for cnid, fname, ftype in img._parse_dir(img._payload(img.inode(nid))):
        if ftype != 1 or not fname.endswith(".xml"):
            continue
        try:
            data = img.read_file(cnid)
        except Exception as exc:  # noqa: BLE001
            print(f"  SKIP {name}/{fname}: {exc}")
            skipped += 1
            continue
        (out_dir / fname).write_bytes(data)
        ok += 1
    print(f"{name}: extracted {ok}, skipped {skipped} -> {out_dir}")
    return ok, skipped


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    out_root = sys.argv[1]
    total_ok = total_skip = 0
    for image in sys.argv[2:]:
        ok, skip = extract(image, out_root)
        total_ok += ok
        total_skip += skip
    print(f"\ntotal: {total_ok} extracted, {total_skip} skipped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
