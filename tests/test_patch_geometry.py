from pathlib import Path
from tempfile import TemporaryDirectory
import importlib.util
import subprocess

HERE = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('routeghost_patch', HERE / 'apply_patch.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

# A realistic excerpt of Organic Maps tag 2026.11.11-39-android,
# including the original JNI signature, densification, and next function.
ORIGINAL = '''JNIEXPORT jobjectArray Java_app_organicmaps_sdk_Framework_nativeGetRouteJunctionPoints(JNIEnv * env, jclass,
                                                                                        jdouble maxDistM)
{
  std::vector<geometry::PointWithAltitude> points;
  if (!frm()->GetRoutingManager().RoutingSession().GetRouteJunctionPoints(points))
  {
    LOG(LWARNING, ("Can't get the route junction points"));
    return nullptr;
  }
  std::vector<geometry::PointWithAltitude> result;
  result.reserve(points.size());
  result.push_back(points[0]);
  for (size_t i = 1; i < points.size(); ++i)
  {
    double const dist = ms::DistanceOnEarth(points[i - 1].ToLatLon(), points[i].ToLatLon());
    if (dist > maxDistM)
    {
      int const steps = static_cast<int>(dist / maxDistM) + 1;
      for (int s = 1; s < steps; ++s)
        result.push_back(points[i - 1].Interpolate(points[i], static_cast<double>(s) / steps));
    }
    result.push_back(points[i]);
  }
  return routing_jni::CreateJunctionInfoArray(env, result);
}
JNIEXPORT jobject Java_app_organicmaps_sdk_Framework_nativeGetRouteAltitudeData(JNIEnv * env, jclass)
{
  return nullptr;
}
'''
MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<application android:label="@string/app_name">
<activity-alias android:name="app.organicmaps.DownloadResourcesActivity" android:label="@string/app_name" />
</application></manifest>'''

for variation in (ORIGINAL,
                  ORIGINAL.replace('    LOG(LWARNING, ("Can\'t get the route junction points"));',
                                   '    LOG(LWARNING, ("Failed to get route vertices"));'),
                  ORIGINAL.replace('  if (!frm()->GetRoutingManager().RoutingSession().GetRouteJunctionPoints(points))',
                                   '  if  (!frm()->GetRoutingManager().RoutingSession().GetRouteJunctionPoints(points))')):
    patched = module.patch_native_route_geometry(variation)
    assert patched.count('routes.GetActive().ForEachPoint') == 1
    assert 'session.RouteCall' in patched
    assert 'result.reserve(points.size());' in patched
    assert 'return routing_jni::CreateJunctionInfoArray(env, result);' in patched
    assert 'nativeGetRouteAltitudeData' in patched
    assert module.patch_native_route_geometry(patched) == patched

try:
    module.patch_native_route_geometry('JNIEXPORT jint other_method() { return 0; }')
    raise AssertionError('Expected to reject missing JNI entry point')
except RuntimeError:
    pass

with TemporaryDirectory() as tmp:
    root = Path(tmp) / 'organicmaps'
    main = root / 'android/app/src/main'
    (main / 'java/app/organicmaps').mkdir(parents=True)
    (main / 'java/app/organicmaps/MwmActivity.java').write_text('    initViews(isLaunchByDeepLink);\n')
    (main / 'AndroidManifest.xml').write_text(MANIFEST)
    native = root / 'android/sdk/src/main/cpp/app/organicmaps/sdk/Framework.cpp'
    native.parent.mkdir(parents=True)
    native.write_text('header\n' + ORIGINAL)
    for _ in range(2):
        subprocess.check_call(['python3', str(HERE / 'apply_patch.py'), str(root)])
    output = native.read_text()
    assert output.count('routes.GetActive().ForEachPoint') == 1
    assert output.count('Selected route has no usable geometry') == 1
    assert output.count('Java_app_organicmaps_sdk_Framework_nativeGetRouteAltitudeData') == 1
    assert (main / 'AndroidManifest.xml').read_text().count('android:label="RouteGhost"') == 3
    assert (main / 'java/app/organicmaps/routeghost/RouteGhostUi.java').is_file()
    route_data = main / 'java/app/organicmaps/routeghost/RouteData.java'
    assert route_data.is_file() and 'class Plan' in route_data.read_text()

# Check a partial GitHub checkout with no overlay RouteData.java.
with TemporaryDirectory() as tmp:
    root = Path(tmp) / 'organicmaps'
    main = root / 'android/app/src/main'
    (main / 'java/app/organicmaps').mkdir(parents=True)
    (main / 'java/app/organicmaps/MwmActivity.java').write_text('    initViews(isLaunchByDeepLink);\n')
    (main / 'AndroidManifest.xml').write_text(MANIFEST)
    native = root / 'android/sdk/src/main/cpp/app/organicmaps/sdk/Framework.cpp'
    native.parent.mkdir(parents=True)
    native.write_text('header\n' + ORIGINAL)
    original_overlay = module.OVERLAY
    try:
        fake_overlay = Path(tmp) / 'partial_overlay'
        fake_overlay.mkdir()
        for item in original_overlay.glob('*.java'):
            if item.name != 'RouteData.java':
                (fake_overlay / item.name).write_bytes(item.read_bytes())
        module.OVERLAY = fake_overlay
        module.patch(root)
        assert 'public final class RouteData' in (main / 'java/app/organicmaps/routeghost/RouteData.java').read_text()
        assert 'class Plan' in (main / 'java/app/organicmaps/routeghost/RouteData.java').read_text()
    finally:
        module.OVERLAY = original_overlay
print('PASS: JNI-scoped patch applies twice, tolerates upstream formatting, retains neighbouring methods')
