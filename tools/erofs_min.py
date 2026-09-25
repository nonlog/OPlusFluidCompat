"""Minimal read-only EROFS lister/extractor for ColorOS OTA images.

Handles: compact inodes, shared/inline xattrs (skip only), flat-plain and
flat-inline dir/file data, chunkformat INDEXES. Compressed extents (lz4)
are NOT supported -- caller must detect fmt==5/1 and skip.
"""
import struct

EROFS_SUPER_OFFSET = 1024

FMT_PLAIN = 2
FMT_INLINE = 4


class ErofsImage:
    def __init__(self, path):
        self.f = open(path, 'rb')
        sb = self._read(EROFS_SUPER_OFFSET, 128)
        magic = struct.unpack('<I', sb[0:4])[0]
        assert magic == 0xE0F5E1E2, 'not erofs: %s' % hex(magic)
        blkszbits, self.sb_extslots, self.root_nid = struct.unpack('<BBH', sb[12:16])
        self.blksz = 1 << blkszbits
        self.meta_blkaddr = struct.unpack('<I', sb[28:32])[0]
        self.meta_base = self.meta_blkaddr * self.blksz

    def _read(self, off, n):
        self.f.seek(off)
        return self.f.read(n)

    def _blk(self, blkaddr, n=1):
        return self._read(blkaddr * self.blksz, n * self.blksz)

    def inode(self, nid):
        off = self.meta_base + (nid << 5)
        raw = self._read(off, 32)
        iformat, xattr_icount, mode, nlink, size, u = struct.unpack('<HHHHII', raw[0:16])
        assert not (iformat & 0x1000), 'extended inode unsupported (nid %d)' % nid
        fmt = iformat & 0x000F
        if xattr_icount:
            data_off = off + 32 + (xattr_icount - 1) * 4 + 12
        else:
            data_off = off + 32
        return dict(nid=nid, fmt=fmt, mode=mode, size=size, u=u, data_off=data_off)

    def _payload(self, ino):
        if ino['fmt'] == FMT_INLINE:
            return self._read(ino['data_off'], ino['size'])
        out = bytearray()
        n_full = ino['size'] // self.blksz
        tail = ino['size'] % self.blksz
        if ino['fmt'] == FMT_PLAIN:
            base = ino['u']
        else:  # chunk INDEXES: single-level u32 table at block u
            base = None
        for c in range(n_full):
            if base is not None:
                out += self._blk(base + c)
            else:
                idx = struct.unpack('<I', self._read(ino['u'] * self.blksz + c * 4, 4))[0]
                out += self._blk(idx)
        if tail:
            out += self._read(ino['data_off'], tail)
        return bytes(out[:ino['size']])

    def _parse_dir(self, data):
        entries = []
        pos = 0
        n = len(data)
        first_nameoff = None
        while pos + 12 <= n:
            cnid, nameoff, ftype = struct.unpack('<QHB', data[pos:pos + 11])
            if cnid == 0 and nameoff == 0:
                break
            if nameoff >= n or ftype > 7:
                break
            if first_nameoff is None:
                first_nameoff = nameoff
            if pos >= first_nameoff:
                break
            entries.append((cnid, nameoff, ftype))
            pos += 12
        res = []
        for i, (cnid, nameoff, ftype) in enumerate(entries):
            end = entries[i + 1][1] if i + 1 < len(entries) else n
            name = data[nameoff:end].split(b'\x00')[0].decode(errors='replace')
            res.append((cnid, name, ftype))
        return res

    def listdir(self, nid):
        ino = self.inode(nid)
        assert ino['fmt'] in (FMT_PLAIN, FMT_INLINE), 'dir fmt %d unsupported' % ino['fmt']
        return [(c, n) for c, n, t in self._parse_dir(self._payload(ino))]

    def read_file(self, nid):
        ino = self.inode(nid)
        if ino['fmt'] not in (FMT_PLAIN, FMT_INLINE):
            raise ValueError('file nid %d has compressed/chunked fmt %d' % (nid, ino['fmt']))
        return self._payload(ino)

    def file_info(self, nid):
        return self.inode(nid)

    def resolve(self, path, nid=None):
        nid = self.root_nid if nid is None else nid
        for part in [p for p in path.split('/') if p]:
            for cnid, name in self.listdir(nid):
                if name == part:
                    nid = cnid
                    break
            else:
                raise FileNotFoundError(path)
        return nid

    def walk(self, nid=None, prefix=''):
        nid = self.root_nid if nid is None else nid
        ino = self.inode(nid)
        for cnid, name, ftype in self._parse_dir(self._payload(ino)):
            if name in ('.', '..'):
                continue
            p = prefix + '/' + name
            yield p, cnid, ftype
            if ftype == 4:
                yield from self.walk(cnid, p)
