#!/usr/bin/env python3
"""Validate Android localization resources before compiling the APK."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
import sys

root = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
languages = ("am", "om", "ti", "ar", "aa", "so")
errors = []


def strings(directory):
    path = root / directory / "strings.xml"
    if not path.is_file():
        errors.append(f"Missing {path}")
        return {}
    try:
        parsed = ET.parse(path)
    except ET.ParseError as exc:
        errors.append(f"Invalid XML in {path}: {exc}")
        return {}
    result = {}
    for element in parsed.getroot().findall("string"):
        key = element.attrib.get("name")
        if not key or key in result:
            errors.append(f"Duplicate or missing string key in {path}: {key}")
        result[key] = element.text or ""
    return result


base = strings("values")
for language in languages:
    localized = strings(f"values-{language}")
    extra = set(localized) - set(base)
    if extra:
        errors.append(f"{language}: unknown resource keys {sorted(extra)}")
    count = len(set(localized) & set(base))
    print(f"{language}: {count}/{len(base)} translated resource entries; remaining use English fallback")

config = root / "xml" / "locales_config.xml"
try:
    locale_root = ET.parse(config).getroot()
    declared = {node.attrib.get("{http://schemas.android.com/apk/res/android}name") for node in locale_root}
    missing = set(languages) | {"en"}
    if not missing.issubset(declared):
        errors.append(f"Missing locale declarations: {sorted(missing - declared)}")
except (ET.ParseError, FileNotFoundError) as exc:
    errors.append(f"Invalid locale config: {exc}")

for error in errors:
    print(f"ERROR: {error}", file=sys.stderr)
sys.exit(1 if errors else 0)
