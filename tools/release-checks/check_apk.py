"""Release gates of AGENTS.md §4 on a built APK (CI): size budget and 16 KB page alignment.

Usage: python3 check_apk.py <apk> [--max-mb 60]
- the APK (without the model) must stay under --max-mb megabytes;
- every native library must have its LOAD segments aligned on at least 16 KB (Google Play requirement
  for Android 15+ devices with 16 KB pages).
"""

from __future__ import annotations

import argparse
import struct
import sys
import zipfile

PT_LOAD = 1
MIN_ALIGN = 16 * 1024


def load_alignments(elf: bytes) -> list[int]:
    """p_align of every PT_LOAD segment of a 64-bit little-endian ELF file."""
    if elf[:4] != b"\x7fELF" or elf[4] != 2 or elf[5] != 1:
        raise ValueError("not a 64-bit little-endian ELF file")
    phoff = struct.unpack_from("<Q", elf, 0x20)[0]
    phentsize, phnum = struct.unpack_from("<HH", elf, 0x36)
    alignments = []
    for i in range(phnum):
        offset = phoff + i * phentsize
        p_type = struct.unpack_from("<I", elf, offset)[0]
        if p_type == PT_LOAD:
            alignments.append(struct.unpack_from("<Q", elf, offset + 0x30)[0])
    return alignments


def check(apk: str, max_mb: float) -> list[str]:
    problems = []
    with open(apk, "rb") as handle:
        size = len(handle.read())
    print(f"{apk}: {size / 1e6:.1f} MB (budget {max_mb} MB)")
    if size > max_mb * 1e6:
        problems.append(f"APK is {size / 1e6:.1f} MB, over the {max_mb} MB budget")
    with zipfile.ZipFile(apk) as archive:
        libraries = [name for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")]
        for name in sorted(libraries):
            alignments = load_alignments(archive.read(name))
            low = [a for a in alignments if a < MIN_ALIGN]
            print(f"  {name}: LOAD alignment {sorted(set(alignments))}")
            if low:
                problems.append(f"{name}: LOAD segments aligned on {sorted(set(low))} bytes (< 16 KB)")
    if not any(name.startswith("lib/arm64-v8a/") for name in libraries):
        problems.append("no arm64-v8a native library found")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("--max-mb", type=float, default=60.0)
    args = parser.parse_args()
    problems = check(args.apk, args.max_mb)
    for problem in problems:
        print(f"::error::{problem}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
