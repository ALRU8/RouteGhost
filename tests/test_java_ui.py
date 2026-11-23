from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
ROOT=Path(__file__).resolve().parent.parent
S={
'android/app/Activity.java':'''package android.app; import android.content.Intent; import android.content.res.Resources; import android.view.View; public class Activity { public <T extends View>T findViewById(int id){return null;} public Resources getResources(){return new Resources();} public void startActivity(Intent i){} public boolean isFinishing(){return false;} public boolean isDestroyed(){return false;} }''',
'android/content/Intent.java':'''package android.content; public class Intent { public Intent(Object a,Class<?> b){} public Intent putExtra(String k,boolean v){return this;} }''',
'android/content/res/Resources.java':'''package android.content.res; public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}''',
'android/util/DisplayMetrics.java':'''package android.util; public class DisplayMetrics {public float density=1;}''',
'android/graphics/Color.java':'''package android.graphics;public class Color {public static final int WHITE=0;}''',
'android/graphics/Typeface.java':'''package android.graphics;public class Typeface {public static final Typeface DEFAULT=new Typeface();public static final int BOLD=1;}''',
'android/graphics/drawable/GradientDrawable.java':'''package android.graphics.drawable;public class GradientDrawable {public void setColor(int c){}public void setCornerRadius(int r){}public void setStroke(int w,int c){}}''',
'android/os/Build.java':'''package android.os;public class Build {public static class VERSION {public static int SDK_INT=30;}}''',
'android/os/Handler.java':'''package android.os;public class Handler {public Handler(Looper l){}public boolean postDelayed(Runnable r,long d){return true;}}''',
'android/os/Looper.java':'''package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}''',
'android/view/View.java':'''package android.view;public class View {public interface OnClickListener{void onClick(View v);}public void setTag(Object o){}public void setOnClickListener(OnClickListener l){}public void setEnabled(boolean b){} public boolean isEnabled(){return true;}public void setElevation(float f){} public void setPadding(int a,int b,int c,int d){} public static final int NO_ID=-1;}''',
'android/view/Gravity.java':'''package android.view;public class Gravity {public static final int CENTER=1,END=2,BOTTOM=4;}''',
'android/widget/FrameLayout.java':'''package android.widget;import android.view.View;public class FrameLayout extends View {public View findViewWithTag(String s){return null;}public void addView(View v,LayoutParams p){}public static class LayoutParams{public LayoutParams(int a,int b,int c){}public int rightMargin,bottomMargin;}}''',
'android/widget/TextView.java':'''package android.widget;import android.view.View;public class TextView extends View {public TextView(Object c){}public void setText(String s){}public void setTextColor(int i){}public void setTextSize(int i){}public void setTypeface(android.graphics.Typeface f,int i){}public void setGravity(int i){}public void setBackground(android.graphics.drawable.GradientDrawable d){}}''',
'android/widget/Toast.java':'''package android.widget;public class Toast {public static int LENGTH_LONG=1;public static Toast makeText(Object c,String s,int i){return new Toast();}public void show(){}}''',
'android/R.java':'''package android;public class R{public static class id{public static int content=1;}}''',
'app/organicmaps/sdk/Framework.java':'''package app.organicmaps.sdk;public class Framework {public static boolean nativeIsRouteBuilt(){return true;} public static boolean nativeIsRouteBuilding(){return false;} public static app.organicmaps.sdk.routing.JunctionInfo[] nativeGetRouteJunctionPoints(double x){return null;}}''',
'app/organicmaps/sdk/Router.java':'''package app.organicmaps.sdk;public enum Router {Vehicle,Pedestrian,Bicycle,Transit;public static Router get(){return Vehicle;}}''',
'app/organicmaps/sdk/routing/JunctionInfo.java':'''package app.organicmaps.sdk.routing;public class JunctionInfo {public double mLat,mLon;}''',
'app/organicmaps/sdk/routing/RoutingController.java':'''package app.organicmaps.sdk.routing;public class RoutingController {public static RoutingController get(){return new RoutingController();}public boolean isBuilt(){return true;}public boolean isBuilding(){return false;}public TransitRouteInfo getCachedTransitInfo(){return null;}public RoutingInfo getCachedRoutingInfo(){return null;}}''',
'app/organicmaps/sdk/routing/RoutingInfo.java':'''package app.organicmaps.sdk.routing;public class RoutingInfo {public int totalTimeInSeconds;}''',
'app/organicmaps/sdk/routing/TransitRouteInfo.java':'''package app.organicmaps.sdk.routing;import java.util.List;import java.util.ArrayList;public class TransitRouteInfo {public int getTotalTime(){return 1;}public List<TransitStepInfo> getTransitSteps(){return new ArrayList<>();}}''',
'app/organicmaps/sdk/routing/TransitStepInfo.java':'''package app.organicmaps.sdk.routing;public class TransitStepInfo {public enum Type {SUBWAY,TRAM,TROLLEYBUS,TRAIN,LIGHT_RAIL,BUS}public Type getType(){return Type.SUBWAY;}public int getTimeInSec(){return 1;}}''',
'app/organicmaps/routeghost/RouteGhostStorage.java':'''package app.organicmaps.routeghost; public class RouteGhostStorage {public static void save(Object c,RouteData.Plan p)throws Exception{}}''',
'app/organicmaps/routeghost/RouteGhostPanel.java':'''package app.organicmaps.routeghost;public class RouteGhostPanel extends android.app.Activity {}''',
}
with TemporaryDirectory() as folder:
    base=Path(folder)
    for name,code in S.items():
        out=base/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_text(code)
    files=[str(x) for x in base.rglob('*.java')]+[str(x) for x in (ROOT/'tests/stubs/org/json').glob('*.java')]+[str(ROOT/'overlay/app/organicmaps/routeghost/RouteData.java'),str(ROOT/'overlay/app/organicmaps/routeghost/RouteGhostUi.java')]
    subprocess.run(['javac','-d',str(base/'build'),*files],check=True)
print('PASS: RouteGhostUi.java compiles against Android/Organic Maps API stubs')
