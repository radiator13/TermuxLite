#!/usr/bin/env python3
"""Fail if an APK is not signed with the Termux debug key."""
import struct
import hashlib
import sys

# Official Termux community debug key (testkey_untrusted.jks)
TERMUX_DEBUG_CERT_SHA256 = "b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1"


def u32(buf, off):
    ln = struct.unpack_from("<I", buf, off)[0]
    return buf[off + 4 : off + 4 + ln], off + 4 + ln


def certs(path):
    data = open(path, "rb").read()
    eocd = data.rfind(b"PK\x05\x06")
    cd = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd - 16 : cd] != b"APK Sig Block 42":
        raise SystemExit(f"{path}: no APK Signature Block v2")
    size = struct.unpack_from("<Q", data, cd - 24)[0]
    first = cd - 8 - size
    pairs = data[first + 8 : cd - 24]
    out = []
    p = 0
    while p + 12 <= len(pairs):
        plen = struct.unpack_from("<Q", pairs, p)[0]
        p += 8
        pid = struct.unpack_from("<I", pairs, p)[0]
        val = pairs[p + 4 : p + plen]
        p += plen
        if pid not in (0x7109871A, 0xF05368C0, 0x1B93AD61):
            continue
        signers, _ = u32(val, 0)
        off = 0
        while off + 4 <= len(signers):
            signer, off = u32(signers, off)
            signed, _ = u32(signer, 0)
            _, d = u32(signed, 0)
            cert_list, _ = u32(signed, d)
            c = 0
            while c + 4 <= len(cert_list):
                cert, c = u32(cert_list, c)
                out.append(hashlib.sha256(cert).hexdigest())
    return out


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: verify-signature.py <apk>...")
    bad = 0
    for path in sys.argv[1:]:
        fps = certs(path)
        ok = TERMUX_DEBUG_CERT_SHA256 in fps
        print(f"{path}: {'OK' if ok else 'FAIL'} certs={fps}")
        if not ok:
            bad = 1
    raise SystemExit(bad)


if __name__ == "__main__":
    main()
