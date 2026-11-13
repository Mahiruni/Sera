#!/usr/bin/env python3
"""Fail release if the lightweight APK exceeds 10 MiB or contains WebRTC."""
import pathlib
import sys
import zipfile

apk = pathlib.Path(sys.argv[1])
size = apk.stat().st_size
limit = 10 * 1024 * 1024
with zipfile.ZipFile(apk) as archive:
    rtc = [name for name in archive.namelist()
           if name.startswith("lib/") and
           any(token in name.lower() for token in ("webrtc", "jingle", "peerconnection"))]
print(f"Lightweight APK: {size:,} bytes ({size / 1024**2:.2f} MiB); limit {limit / 1024**2:.0f} MiB")
if size > limit or rtc:
    raise SystemExit(f"Lightweight release budget exceeded or RTC libraries present: {rtc}")
