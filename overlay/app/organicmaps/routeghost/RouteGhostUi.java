package app.organicmaps.routeghost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.routing.JunctionInfo;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.routing.TransitRouteInfo;
import app.organicmaps.sdk.routing.TransitStepInfo;
import java.util.ArrayList;
import static app.organicmaps.routeghost.RouteData.*;

public final class RouteGhostUi {
    private RouteGhostUi() { }
    public static void attach(Activity activity) {
        FrameLayout frame=activity.findViewById(android.R.id.content);
        if(frame.findViewWithTag("routeghost-control")!=null)return;
        TextView launch=new TextView(activity);
        launch.setTag("routeghost-control");
        launch.setText("GPS  ◉");
        launch.setTextColor(Color.WHITE);
        launch.setTextSize(15);
        launch.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        launch.setGravity(Gravity.CENTER);
        launch.setElevation(dp(activity,9));
        launch.setPadding(dp(activity,18),dp(activity,13),dp(activity,18),dp(activity,13));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF168454);bg.setCornerRadius(dp(activity,26));launch.setBackground(bg);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.BOTTOM);
        params.rightMargin=dp(activity,15);params.bottomMargin=dp(activity,98);
        frame.addView(launch,params);
        launch.setOnClickListener(v->{
            boolean captured=false;
            boolean built=RoutingController.get().isBuilt();
            try{if(built)captured=captureSelectedRoute(activity);}
            catch(Exception ex){Toast.makeText(activity,"Маршрут недоступен: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
            activity.startActivity(new Intent(activity,RouteGhostPanel.class)
                .putExtra("captured",captured).putExtra("built",built));
        });
    }
    private static int dp(Activity a,int value){return (int)(value*a.getResources().getDisplayMetrics().density+0.5f);}
    private static boolean captureSelectedRoute(Activity activity)throws Exception {
        if(!Framework.nativeIsRouteBuilt())return false;
        JunctionInfo[] junctions=Framework.nativeGetRouteJunctionPoints(12.0);
        if(junctions==null||junctions.length<2)return false;
        ArrayList<Pt> points=new ArrayList<>();
        for(JunctionInfo j:junctions){
            Pt p=new Pt(j.mLat,j.mLon);
            if(points.isEmpty()||metres(points.get(points.size()-1),p)>0.15)points.add(p);
            if(points.size()>35000)throw new Exception("Слишком длинный маршрут");
        }
        if(points.size()<2||length(points)<5)return false;
        Router router=Router.get();
        String type=router==Router.Vehicle?CAR:router==Router.Pedestrian?WALK:router==Router.Bicycle?OTHER:BUS;
        int secs=(int)Math.max(1,length(points)/(type.equals(WALK)?1.35:10.0));
        if(router==Router.Transit){
            TransitRouteInfo info=RoutingController.get().getCachedTransitInfo();
            if(info!=null){
                secs=Math.max(1,info.getTotalTime());
                for(TransitStepInfo step:info.getTransitSteps()){
                    String t=step.getType().name();
                    if(t.equals("SUBWAY")){type=METRO;break;}
                    if(t.equals("TRAM")){type=TRAM;break;}
                    if(t.equals("TROLLEYBUS")){type=TROLLEY;break;}
                    if(t.equals("TRAIN")||t.equals("LIGHT_RAIL")){type=TRAIN;break;}
                    if(t.equals("BUS")){type=BUS;break;}
                }
            }
        }else{
            RoutingInfo info=RoutingController.get().getCachedRoutingInfo();
            if(info!=null && info.totalTimeInSeconds>0)secs=info.totalTimeInSeconds;
        }
        Plan plan=new Plan();plan.title="Маршрут Organic Maps";plan.source="Organic Maps";
        plan.note=router==Router.Transit?"Для смешанного транспорта режим движения приблизительный — тип отдельных участков не определяется по GPS-геометрии.":"";
        plan.legs.add(new Leg(type,"Маршрут",points,secs));
        RouteGhostStorage.save(activity,plan);
        return true;
    }
}
