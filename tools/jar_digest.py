#!/usr/bin/env python3
"""Prints a digest of a jar's CONTENT, so two builds of the same tree can be compared.

A jar's own sha256 changes with every uncached build of NeoForge and Forge 1.20.1 (zip entry timestamps), although
every entry is the same. This digest ignores timestamps and directory entries: it is the sha256 of the sorted lines
"<entry name> <sha256 of the entry's bytes>". Two jars with the same digest hold the same files.

    python3 tools/jar_digest.py neoforge/build/libs/proficiency-1.5.0.jar ...

Prints "<digest> <entry count> <path>" for each jar.
"""
import hashlib
import sys
import zipfile


def digest(path):
    with zipfile.ZipFile(path) as z:
        lines = []
        for info in sorted(z.infolist(), key=lambda i: i.filename):
            if not info.filename.endswith("/"):
                lines.append("%s %s\n" % (info.filename, hashlib.sha256(z.read(info.filename)).hexdigest()))
    return hashlib.sha256("".join(lines).encode("utf-8")).hexdigest(), len(lines)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for jar in sys.argv[1:]:
        d, n = digest(jar)
        print(d, n, jar)
