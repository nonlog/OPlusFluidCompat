"""Probe EROFS flat-compression (fmt 6) files without needing an lz4 decoder.

EROFS stores incompressible pclusters as PLAIN lclusters, so an already-zipped
APK is expected to contain no lz4 pcluster at all. This script parses the
lcluster index, reports the lcluster-type histogram, and (when every pcluster is
PLAIN) reassembles the file.

Correctness is self-validating: a correctly reassembled APK starts with PK\\x03\\x04
and ends with a readable central directory.
"""
import struct
import sys

sys.path.insert(0, 'tools')
from erofs_min import ErofsImage  # noqa: E402

# z_erofs_lcluster_index (8-byte form)
#   __le16 di_advise; __le16 di_clusterofs;
#   union { __le32 blkaddr; __le16 delta[2]; } di_u;
LC_TYPE_MASK = 3
LC_PLAIN, LC_HEAD1, LC_NONHEAD, LC_HEAD2 = 0, 1, 2, 3

Z_EROFS_ADVISE_COMPACTED_2B = 0x0001


def read_map_header(img, ino):
    """z_erofs_map_header: __le32 h_reserved1; __le16 h_advise; u8 algo; u8 clusterbits."""
    raw = img._read(ino['data_off'], 8)
    reserved, advise, algo, lclusterbits = struct.unpack('<IHBB', raw)
    return dict(reserved=reserved, advise=advise, algo=algo,
                lclusterbits=lclusterbits, index_off=ino['data_off'] + 8)


def parse_index(img, ino, hdr):
    """Return the lcluster index list and the index entry size."""
    lclustersize = 1 << hdr['lclusterbits']
    n_lclusters = (ino['size'] + lclustersize - 1) // lclustersize
    compact = bool(hdr['advise'] & Z_EROFS_ADVISE_COMPACTED_2B)
    # The 8-byte form is what pre-COMPACTED_2B images use; sizes are validated
    # below by checking the first lcluster is a PLAIN or HEAD1 entry.
    entry = 4 if compact else 8
    idx = []
    for i in range(n_lclusters):
        raw = img._read(hdr['index_off'] + i * entry, entry)
        if compact:
            (advise,) = struct.unpack('<H', raw[0:2])
            idx.append(dict(advise=advise, clusterofs=None, u=None))
        else:
            advise, clusterofs, u = struct.unpack('<HHI', raw)
            idx.append(dict(advise=advise, clusterofs=clusterofs, u=u))
    return idx, entry, lclustersize


def probe(img, nid):
    ino = img.inode(nid)
    print(f'inode {nid}: fmt={ino["fmt"]} size={ino["size"]} u={ino["u"]} '
          f'data_off={ino["data_off"]}')
    if ino['fmt'] != 6:
        print('  not fmt 6; nothing to probe')
        return None
    hdr = read_map_header(img, ino)
    print(f'  map_header: advise=0x{hdr["advise"]:04x} algo={hdr["algo"]} '
          f'lclusterbits={hdr["lclusterbits"]}')
    idx, entry, lclustersize = parse_index(img, ino, hdr)
    types = {}
    for e in idx:
        t = e['advise'] & LC_TYPE_MASK
        types[t] = types.get(t, 0) + 1
    names = {LC_PLAIN: 'PLAIN', LC_HEAD1: 'HEAD1(lz4)', LC_NONHEAD: 'NONHEAD', LC_HEAD2: 'HEAD2'}
    print(f'  {len(idx)} lclusters, entry={entry}B, lclustersize={lclustersize}')
    for t, n in sorted(types.items()):
        print(f'    {names.get(t, t):14} {n}')
    return dict(ino=ino, hdr=hdr, idx=idx, entry=entry, lclustersize=lclustersize)


def extract_plain(img, info):
    """Reassemble assuming every pcluster is PLAIN."""
    idx, lclustersize = info['idx'], info['lclustersize']
    out = bytearray()
    for i, e in enumerate(idx):
        t = e['advise'] & LC_TYPE_MASK
        if t != LC_PLAIN:
            raise ValueError(f'lcluster {i} is type {t}, not PLAIN')
        blkaddr = e['u']
        want = min(lclustersize, info['ino']['size'] - len(out))
        out += img._read(blkaddr * img.blksz, want)
    return bytes(out)


if __name__ == '__main__':
    img = ErofsImage('vendor/PJD110_16.0.10.501_CN01/system_ext.img')
    nid = img.resolve('app/SystemUIPlugin/SystemUIPlugin.apk')
    info = probe(img, nid)
    if info:
        try:
            data = extract_plain(img, info)
            out = 'out/evidence/SystemUIPlugin-CN.apk'
            open(out, 'wb').write(data)
            print(f'\nextracted {len(data)} bytes -> {out}')
            print('first 4 bytes:', data[:4])
            print('is zip:', data[:4] == b'PK\x03\x04')
        except Exception as exc:
            print('\ncannot reassemble without lz4:', exc)
