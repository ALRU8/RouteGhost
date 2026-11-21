#!/usr/bin/env python3
"""Install the RouteGhost bridge into an upstream Organic Maps source tree.

Usage: python apply_patch.py /path/to/organicmaps
"""
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

OVERLAY = Path(__file__).resolve().parent / "overlay" / "app" / "organicmaps" / "routeghost"


def once_replace(data: str, before: str, after: str, location: str) -> str:
    number = data.count(before)
    if number != 1:
        raise RuntimeError(f"Organic Maps version mismatch in {location}: expected 1 anchor, got {number}: {before[:90]}")
    return data.replace(before, after, 1)


def patch(root: Path):
    main = root / "android" / "app" / "src" / "main"
    mwm = main / "java" / "app" / "organicmaps" / "MwmActivity.java"
    manifest = main / "AndroidManifest.xml"
    if not mwm.is_file() or not manifest.is_file():
        raise FileNotFoundError("Expected original Organic Maps checkout (android/app/src/main/...)")
    text = mwm.read_text(encoding="utf-8")
    hook = "    initViews(isLaunchByDeepLink);"
    marker = "app.organicmaps.routeghost.RouteGhostUi.attach(this);"
    if marker not in text:
        text = once_replace(text, hook, hook + "\n    " + marker, "MwmActivity.java")
    mwm.write_text(text, encoding="utf-8")

    xml = manifest.read_text(encoding="utf-8")
    if 'android:name="app.organicmaps.routeghost.RouteGhostPanel"' not in xml:
        insertion = '''    <!-- RouteGhost: integrated test-location controls. -->
    <activity
        android:name="app.organicmaps.routeghost.RouteGhostPanel"
        android:label="RouteGhost"
        android:exported="false"
        android:theme="@android:style/Theme.Material.Light.NoActionBar" />
    <service
        android:name="app.organicmaps.routeghost.LocationService"
        android:exported="false"
        android:foregroundServiceType="location" />
'''
        xml = once_replace(xml, "</application>", insertion + "  </application>", "AndroidManifest.xml")
    if 'android:label="@string/app_name"' in xml:
        xml = once_replace(xml, 'android:label="@string/app_name"', 'android:label="RouteGhost"', "AndroidManifest.xml app label")
    # The upstream app already requests ACCESS_FINE_LOCATION, FOREGROUND_SERVICE and FOREGROUND_SERVICE_LOCATION.
    xmlroot = ET.fromstring(xml)
    ns = "{http://schemas.android.com/apk/res/android}"
    permission_names = {node.attrib.get(ns + "name") for node in xmlroot if node.tag == "uses-permission"}
    for name in ["android.permission.ACCESS_FINE_LOCATION", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_LOCATION"]:
        if name not in permission_names:
            raise RuntimeError(f"Organic Maps manifest is missing required permission {name}")
    manifest.write_text(xml, encoding="utf-8")

    destination = main / "java" / "app" / "organicmaps" / "routeghost"
    destination.mkdir(parents=True, exist_ok=True)
    for f in OVERLAY.glob("*.java"):
        shutil.copyfile(f, destination / f.name)
    print(f"RouteGhost integrated into {root}")
    print(f"Java classes: {len(list(destination.glob('*.java')))}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python apply_patch.py /path/to/organicmaps")
    patch(Path(sys.argv[1]).resolve())
