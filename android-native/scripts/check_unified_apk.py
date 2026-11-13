#!/usr/bin/env python3
"""Verify the universal compatibility APK and smaller ABI-specific APKs."""
import pathlib,sys,zipfile
root=pathlib.Path(sys.argv[1])
abis={"arm64-v8a","armeabi-v7a","x86","x86_64"}
for abi in sorted(abis|{"universal"}):
    path=root/f"app-{abi}-release.apk"
    with zipfile.ZipFile(path) as z:
        rtc=[n for n in z.namelist() if n.startswith("lib/") and any(t in n.lower() for t in ("webrtc","jingle","peerconnection"))]
        actual={n.split("/")[1] for n in rtc}
        expected=abis if abi=="universal" else {abi}
        if actual!=expected: raise SystemExit(f"{path}: RTC ABIs {actual}, expected {expected}")
        if any(z.getinfo(n).compress_type==zipfile.ZIP_STORED for n in rtc): raise SystemExit(f"{path}: uncompressed RTC")
    size=path.stat().st_size
    cap=(32 if abi=="universal" else 14)*1024**2
    print(f"{path.name}: {size:,} bytes ({size/1024**2:.2f} MiB), limit {cap/1024**2:.0f} MiB")
    if size>cap: raise SystemExit(f"{path}: size budget exceeded")
