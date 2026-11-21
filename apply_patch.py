#!/usr/bin/env python3
"""Install the RouteGhost bridge into an upstream Organic Maps source tree.

Usage: python apply_patch.py /path/to/organicmaps
"""
from pathlib import Path
import shutil
import re
import sys
import xml.etree.ElementTree as ET

OVERLAY = Path(__file__).resolve().parent / "overlay" / "app" / "organicmaps" / "routeghost"


def once_replace(data: str, before: str, after: str, location: str) -> str:
    number = data.count(before)
    if number != 1:
        raise RuntimeError(f"Organic Maps version mismatch in {location}: expected 1 anchor, got {number}: {before[:90]}")
    return data.replace(before, after, 1)


def set_routeghost_label(xml: str, tag_name: str, identity: str = "") -> str:
    """Rename only a specific manifest element, preserving the original formatting.

    Organic Maps defines the app name on both <application> and its launcher
    <activity-alias>. Replacing a global string or expecting one match breaks.
    """
    matches = [match for match in re.finditer(r"<" + re.escape(tag_name) + r"\b[^>]*>", xml, re.DOTALL)
               if not identity or identity in match.group(0)]
    if len(matches) != 1:
        raise RuntimeError(f"Expected exactly one <{tag_name}> element containing {identity!r}; got {len(matches)}")
    match = matches[0]
    tag = match.group(0)
    if 'android:label="RouteGhost"' in tag:
        return xml
    before = 'android:label="@string/app_name"'
    if tag.count(before) != 1:
        raise RuntimeError(f"Unexpected label in <{tag_name}> with {identity!r}")
    updated = tag.replace(before, 'android:label="RouteGhost"', 1)
    return xml[:match.start()] + updated + xml[match.end():]


def patch_native_route_geometry(native_text: str) -> str:
    # Patch just one JNI function. Tolerate upstream whitespace and logging changes.
    name = "Java_app_organicmaps_sdk_Framework_nativeGetRouteJunctionPoints"
    matches = list(re.finditer(r"\b" + name + r"\s*\(", native_text))
    if len(matches) != 1:
        raise RuntimeError(f"Expected exactly one {name} in Framework.cpp; got {len(matches)}")
    opening = native_text.find("{", matches[0].end())
    if opening == -1:
        raise RuntimeError("No opening brace in nativeGetRouteJunctionPoints")

    depth = 0
    closing = -1
    for pos in range(opening, len(native_text)):
        if native_text[pos] == "{":
            depth += 1
        elif native_text[pos] == "}":
            depth -= 1
            if depth == 0:
                closing = pos
                break
    if closing < 0:
        raise RuntimeError("Unbalanced JNI method braces")

    body = native_text[opening + 1:closing]
    if "Selected route has no usable geometry" in body and "session.RouteCall" in body:
        return native_text

    start = re.search(r"std::vector\s*<\s*geometry::PointWithAltitude\s*>\s+points\s*;", body)
    finish = re.search(r"std::vector\s*<\s*geometry::PointWithAltitude\s*>\s+result\s*;", body)
    if not start or not finish or finish.start() <= start.end():
        raise RuntimeError("Unexpected JNI geometry function: missing points/result declarations")
    original = body[start.end():finish.start()]
    if "GetRouteJunctionPoints(points)" not in original:
        raise RuntimeError("Unexpected JNI geometry method: routing source differs")
    if "result.push_back(points[0])" not in body[finish.end():]:
        raise RuntimeError("Unexpected interpolation code; aborting")

    new_part = """std::vector<geometry::PointWithAltitude> points;
  auto const & session = frm()->GetRoutingManager().RoutingSession();
  if (!session.GetRouteJunctionPoints(points) || points.size() < 2)
  {
    points.clear();
    session.RouteCall([&points](routing::RoutesResult const & routes)
    {
      if (!routes.IsValid())
        return;
      routes.GetActive().ForEachPoint([&points](geometry::PointWithAltitude const & point)
      {
        points.push_back(point);
      });
    });
  }
  if (points.size() < 2)
  {
    LOG(LWARNING, ("Selected route has no usable geometry"));
    return nullptr;
  }
  std::vector<geometry::PointWithAltitude> result;"""
    body = body[:start.start()] + new_part + body[finish.end():]
    return native_text[:opening + 1] + body + native_text[closing:]


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
    xml = set_routeghost_label(xml, "application")
    xml = set_routeghost_label(xml, "activity-alias",
                                'android:name="app.organicmaps.DownloadResourcesActivity"')
    # The upstream app already requests ACCESS_FINE_LOCATION, FOREGROUND_SERVICE and FOREGROUND_SERVICE_LOCATION.
    xmlroot = ET.fromstring(xml)
    ns = "{http://schemas.android.com/apk/res/android}"
    permission_names = {node.attrib.get(ns + "name") for node in xmlroot if node.tag == "uses-permission"}
    for name in ["android.permission.ACCESS_FINE_LOCATION", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_LOCATION"]:
        if name not in permission_names:
            raise RuntimeError(f"Organic Maps manifest is missing required permission {name}")
    manifest.write_text(xml, encoding="utf-8")

    # When the route is previewed or the selected alternative has just switched,
    # the native following-route geometry may not be ready yet. The routing core
    # retains the selected route in RoutesResult, including its actual vertices.
    # Read these vertices as a fallback instead of inventing a straight line.
    native_routing = (root / "android" / "sdk" / "src" / "main" / "cpp" / "app" /
                      "organicmaps" / "sdk" / "Framework.cpp")
    if not native_routing.is_file():
        raise FileNotFoundError("Missing upstream SDK JNI Framework.cpp")
    native_text = native_routing.read_text(encoding="utf-8")
    patched_text = patch_native_route_geometry(native_text)
    if patched_text != native_text:
        native_routing.write_text(patched_text, encoding="utf-8")

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
