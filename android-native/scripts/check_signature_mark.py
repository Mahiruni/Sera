#!/usr/bin/env python3
"""Prevent the Android system splash, animated S and launcher icon from diverging."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/et/sera/nativeapp/SeraSignatureIntro.kt").read_text()
match = re.search(r'private const val SIGNATURE_S = "([^"]+)"', SOURCE)
assert match is not None, "Missing canonical signature S path"
outline = match.group(1)
assert outline.startswith("M") and outline.endswith("Z"), "Invalid signature path"
dot = "M86.44,8.84 L91.53,8.84 L91.53,13.93 L86.44,13.93 Z"
namespace = "{http://schemas.android.com/apk/res/android}"
for name in ("sera_signature_mark", "sera_signature_system", "sera_launcher_foreground"):
    file = ROOT / f"app/src/main/res/drawable/{name}.xml"
    root = ET.parse(file).getroot()
    shapes = list(root.iter("path"))
    assert len(shapes) == 2, f"{name}: expected S and small square"
    assert shapes[0].get(namespace + "pathData") == outline, f"{name}: S outline differs"
    assert shapes[0].get(namespace + "fillColor") == "#FFFFFF", f"{name}: wrong white"
    assert shapes[1].get(namespace + "pathData") == dot, f"{name}: corner square differs"
    assert shapes[1].get(namespace + "fillColor") == "#8CE9E1", f"{name}: corner square color differs"
assert "Offset(86.44f, 8.84f)" in SOURCE, "Animated square position differs"
assert "Size(5.09f, 5.09f)" in SOURCE, "Animated square dimensions differ"
assert "Color(0xFF8CE9E1)" in SOURCE, "Animated square tint differs"
print("SERA signature logo: identical in system splash, Compose animation and launcher icon.")
