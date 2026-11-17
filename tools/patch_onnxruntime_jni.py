#!/usr/bin/env python3
"""Rebinds onnxruntime-android's Java bridge to the ONNX Runtime library inside the sherpa-onnx AAR.

Why: Audiops ships sherpa-onnx, whose AAR bundles its own libonnxruntime.so (1.28.2, exported symbols
tagged VERS_1.28.2). onnxruntime-android 1.28.0 (the closest release on Maven Central) ships a bridge,
libonnxruntime4j_jni.so, that asks for VERS_1.28.0, so Android's linker refuses it ("cannot locate
symbol OrtGetApiBase"). Both releases have the same C API version (28), so the bridge works against
sherpa's library once it asks for the tag that library exports. This script changes that one version
string in the bridge's .gnu.version_r table (same length) and fixes its hash. Nothing else changes.

Usage: tools/patch_onnxruntime_jni.py <onnxruntime-android-1.28.0.aar> <app/src/main/jniLibs>
Re-run it (and commit the four small outputs) when the onnxruntime-android or sherpa-onnx version changes.
"""
import struct
import sys
import zipfile
from pathlib import Path

OLD, NEW = "VERS_1.28.0", "VERS_1.28.2"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
SHT_GNU_VERNEED = 0x6FFFFFFE


def elf_hash(name: str) -> int:
    h = 0
    for c in name.encode():
        h = ((h << 4) + c) & 0xFFFFFFFF
        g = h & 0xF0000000
        if g:
            h ^= g >> 24
        h &= ~g & 0xFFFFFFFF
    return h


def patch(data: bytearray) -> int:
    assert data[:4] == b"\x7fELF"
    is64 = data[4] == 2
    if is64:
        shoff = struct.unpack_from("<Q", data, 0x28)[0]
        shentsize, shnum = struct.unpack_from("<HH", data, 0x3A)
    else:
        shoff = struct.unpack_from("<I", data, 0x20)[0]
        shentsize, shnum = struct.unpack_from("<HH", data, 0x2E)

    def section(i):
        base = shoff + i * shentsize
        if is64:
            typ = struct.unpack_from("<I", data, base + 4)[0]
            off, size = struct.unpack_from("<QQ", data, base + 0x18)
            link = struct.unpack_from("<I", data, base + 0x28)[0]
        else:
            typ = struct.unpack_from("<I", data, base + 4)[0]
            off, size = struct.unpack_from("<II", data, base + 0x10)
            link = struct.unpack_from("<I", data, base + 0x18)[0]
        return typ, off, size, link

    patched = 0
    for i in range(shnum):
        typ, off, size, link = section(i)
        if typ != SHT_GNU_VERNEED:
            continue
        _, str_off, _, _ = section(link)
        pos = off
        while True:
            _vn_version, vn_cnt, _vn_file, vn_aux, vn_next = struct.unpack_from("<HHIII", data, pos)
            aux = pos + vn_aux
            for _ in range(vn_cnt):
                _hash, _flags, _other, name_off, vna_next = struct.unpack_from("<IHHII", data, aux)
                start = str_off + name_off
                if bytes(data[start : start + len(OLD)]) == OLD.encode() and data[start + len(OLD)] == 0:
                    data[start : start + len(NEW)] = NEW.encode()
                    struct.pack_into("<I", data, aux, elf_hash(NEW))
                    patched += 1
                aux += vna_next
            if vn_next == 0:
                break
            pos += vn_next
    return patched


def main() -> None:
    aar, out = Path(sys.argv[1]), Path(sys.argv[2])
    with zipfile.ZipFile(aar) as z:
        for abi in ABIS:
            data = bytearray(z.read(f"jni/{abi}/libonnxruntime4j_jni.so"))
            n = patch(data)
            assert n >= 1, f"{abi}: {OLD} not found"
            target = out / abi / "libonnxruntime4j_jni.so"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            print(f"{abi}: patched {n} version requirement(s) -> {target} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
