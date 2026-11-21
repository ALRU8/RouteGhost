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

# Self-contained fallback: a partial GitHub upload may omit RouteData.java.
# Keep this source in sync with overlay/app/organicmaps/routeghost/RouteData.java.
ROUTE_DATA_SOURCE = r'''
package app.organicmaps.routeghost;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RouteData {
    private RouteData() {}
    public static final String WALK="WALK", CAR="CAR", BUS="BUS", ELECTROBUS="ELECTROBUS", MINIBUS="MINIBUS", TROLLEY="TROLLEY", TRAM="TRAM",
      METRO="METRO", MCC="MCC", MCD="MCD", TRAIN="TRAIN", MONORAIL="MONORAIL", FERRY="FERRY", OTHER="OTHER", WAIT="WAIT";

    public static final class Pt {
        public final double lat, lon;
        public Pt(double lat, double lon) { if (!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180) throw new IllegalArgumentException("Неверные координаты"); this.lat=lat;this.lon=lon; }
        public JSONArray json() throws JSONException { JSONArray a=new JSONArray(); a.put(lat);a.put(lon);return a; }
        public static Pt read(JSONArray a) throws JSONException {return new Pt(a.getDouble(0),a.getDouble(1));}
        @Override public String toString(){return String.format(Locale.US,"%.6f, %.6f",lat,lon);}
    }
    public static class Leg {
        public String type, name;
        public final ArrayList<Pt> path = new ArrayList<>();
        public final ArrayList<Pt> stops = new ArrayList<>();
        public int baseSeconds;
        public boolean inferredGeometry=false;
        public Leg(String type, String name, List<Pt> path, int seconds){this.type=type;this.name=name;this.path.addAll(path);baseSeconds=Math.max(1,seconds);}
        public JSONObject json() throws JSONException {
            JSONObject o=new JSONObject();o.put("type",type);o.put("name",name);o.put("seconds",baseSeconds);o.put("inferred",inferredGeometry);
            JSONArray arr=new JSONArray();for(Pt p:path)arr.put(p.json());o.put("path",arr);
            JSONArray ss=new JSONArray();for(Pt p:stops)ss.put(p.json());o.put("stops",ss);return o;
        }
        public static Leg read(JSONObject o) throws JSONException {
            ArrayList<Pt> path=new ArrayList<>(); JSONArray a=o.getJSONArray("path");for(int i=0;i<a.length();i++)path.add(Pt.read(a.getJSONArray(i)));
            Leg l=new Leg(o.getString("type"),o.optString("name",""),path,o.optInt("seconds",1));
            l.inferredGeometry=o.optBoolean("inferred",false);
            JSONArray ss=o.optJSONArray("stops");if(ss!=null) for(int i=0;i<ss.length();i++)l.stops.add(Pt.read(ss.getJSONArray(i)));
            return l;
        }
    }
    public static final class Plan {
        public String title="Маршрут", source="Локальный", note="";
        public final ArrayList<Leg> legs = new ArrayList<>();
        public long seed=System.currentTimeMillis();
        public JSONObject json() throws JSONException {
            JSONObject o=new JSONObject();o.put("title",title);o.put("source",source);o.put("note",note);o.put("seed",seed);
            JSONArray a=new JSONArray();for(Leg leg:legs)a.put(leg.json());o.put("legs",a);return o;
        }
        public static Plan read(JSONObject o) throws JSONException {
            Plan p=new Plan();p.title=o.optString("title","Маршрут");p.source=o.optString("source","");p.note=o.optString("note","");p.seed=o.optLong("seed",System.currentTimeMillis());
            JSONArray a=o.getJSONArray("legs");for(int i=0;i<a.length();i++)p.legs.add(Leg.read(a.getJSONObject(i)));return p;
        }
        public int totalBaseSeconds(){int n=0;for(Leg l:legs)n+=l.baseSeconds;return n;}
        public double length(){double d=0;for(Leg l:legs)d+=RouteData.length(l.path);return d;}
    }
    public static final class Keyframe {
        public final long atMillis;
        public final Pt pos;
        public final String label, type;
        public Keyframe(long atMillis, Pt pos, String label, String type){this.atMillis=atMillis;this.pos=pos;this.label=label;this.type=type;}
        public JSONObject json() throws JSONException {JSONObject o=new JSONObject();o.put("at",atMillis);o.put("pos",pos.json());o.put("label",label);o.put("type",type);return o;}
        public static Keyframe read(JSONObject o)throws JSONException{return new Keyframe(o.getLong("at"),Pt.read(o.getJSONArray("pos")),o.getString("label"),o.getString("type"));}
    }
    public static final class Timeline {
        public final ArrayList<Keyframe> frames=new ArrayList<>();
        public final ArrayList<String> events=new ArrayList<>();
        public long duration(){return frames.isEmpty()?0:frames.get(frames.size()-1).atMillis;}
        public JSONObject json()throws JSONException{JSONArray a=new JSONArray();for(Keyframe f:frames)a.put(f.json());JSONObject o=new JSONObject();o.put("frames",a);return o;}
        public static Timeline read(JSONObject o)throws JSONException {Timeline t=new Timeline();JSONArray a=o.getJSONArray("frames");for(int i=0;i<a.length();i++)t.frames.add(Keyframe.read(a.getJSONObject(i)));return t;}
    }
    public static final class Sample {
        public final Pt pos;
        public final double speedMps, bearing;
        public final String label,type;
        public Sample(Pt p,double s,double b,String label,String type){pos=p;speedMps=s;bearing=b;this.label=label;this.type=type;}
    }
    public static double metres(Pt a,Pt b){double r=6371000.0;double x=Math.toRadians(b.lon-a.lon)*Math.cos(Math.toRadians((a.lat+b.lat)/2));double y=Math.toRadians(b.lat-a.lat);return Math.sqrt(x*x+y*y)*r;}
    public static double length(List<Pt> path){double d=0;for(int i=1;i<path.size();i++)d+=metres(path.get(i-1),path.get(i));return d;}
    public static Pt mix(Pt a,Pt b,double q){q=Math.max(0,Math.min(1,q));return new Pt(a.lat+(b.lat-a.lat)*q,a.lon+(b.lon-a.lon)*q);}
    public static double bearing(Pt a,Pt b){double phi1=Math.toRadians(a.lat),phi2=Math.toRadians(b.lat),lambda=Math.toRadians(b.lon-a.lon);return (360+Math.toDegrees(Math.atan2(Math.sin(lambda)*Math.cos(phi2),Math.cos(phi1)*Math.sin(phi2)-Math.sin(phi1)*Math.cos(phi2)*Math.cos(lambda))))%360;}
    public static String display(String type){switch(type){case WALK:return "Пешком";case CAR:return "Автомобиль";case BUS:return "Автобус";case ELECTROBUS:return "Электробус";case MINIBUS:return "Маршрутка";case TROLLEY:return "Троллейбус";case TRAM:return "Трамвай";case METRO:return "Метро";case MCC:return "МЦК";case MCD:return "МЦД";case TRAIN:return "Электричка";case MONORAIL:return "Монорельс";case FERRY:return "Водный транспорт";case WAIT:return "Ожидание";default:return "Транспорт";}}
    public static boolean rail(String mode){return mode.equals(METRO)||mode.equals(MCC)||mode.equals(MCD)||mode.equals(TRAIN)||mode.equals(MONORAIL);}
    public static boolean publicTransport(String type){return !(WALK.equals(type)||CAR.equals(type)||WAIT.equals(type));}
}
'''



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
    required = (
        "LocationService.java", "RouteData.java", "RouteGhostPanel.java",
        "RouteGhostStorage.java", "RouteGhostUi.java", "SimulationEngine.java",
        "TrackIO.java",
    )
    for name in required:
        source = OVERLAY / name
        target = destination / name
        if source.is_file():
            shutil.copyfile(source, target)
        elif name == "RouteData.java":
            target.write_text(ROUTE_DATA_SOURCE, encoding="utf-8")
            print("Restored missing RouteData.java from embedded source")
        else:
            raise FileNotFoundError(f"RouteGhost source file is missing from the GitHub checkout: {source}")
        if not target.is_file() or target.stat().st_size == 0:
            raise RuntimeError(f"RouteGhost installation failed: {target}")
    if "public final class RouteData" not in (destination / "RouteData.java").read_text(encoding="utf-8"):
        raise RuntimeError("RouteData.java was copied but has unexpected contents")
    print(f"RouteGhost integrated into {root}")
    print(f"Java classes installed: {len(required)}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python apply_patch.py /path/to/organicmaps")
    patch(Path(sys.argv[1]).resolve())
