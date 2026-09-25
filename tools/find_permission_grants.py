"""Report every (allowlist file, package) grant for a set of permissions.

Answers "who is allowed to hold permission P, and where is that written?"
across both the device's live permission XMLs and the extracted CN firmware ones.
"""
import glob
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import privapp_audit as P  # noqa: E402

WANTED = [
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.START_ACTIVITIES_FROM_BACKGROUND",
    "android.permission.SUBSTITUTE_NOTIFICATION_APP_NAME",
    "android.permission.READ_PRIVILEGED_PHONE_STATE",
]


def grants(path: str) -> list[tuple[str, str]]:
    out = []
    stack: list[str] = []
    for name, attrs in P.iter_elements(path):
        if name == P.END:
            if stack and attrs.get("name") == "privapp-permissions":
                stack.pop()
            continue
        if name == "privapp-permissions":
            stack.append(attrs.get("package", ""))
        elif name == "permission" and stack:
            out.append((stack[-1], attrs.get("name", "")))
    return out


def main() -> int:
    patterns = sys.argv[1:] or ["out/cn-perms/**/*.xml", "out/privapp-audit/**/*.xml"]
    files: list[str] = []
    for pat in patterns:
        files += sorted(glob.glob(pat, recursive=True))

    index: dict[str, list[tuple[str, str]]] = {p: [] for p in WANTED}
    for f in files:
        try:
            for pkg, perm in grants(f):
                if perm in index:
                    index[perm].append((f.replace("\\", "/"), pkg))
        except Exception:  # noqa: BLE001
            pass

    for perm in WANTED:
        print(f"### {perm}")
        rows = index[perm]
        if not rows:
            print("    (no grant found in any source)")
        for f, pkg in rows:
            print(f"    {pkg:56s} {f}")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
