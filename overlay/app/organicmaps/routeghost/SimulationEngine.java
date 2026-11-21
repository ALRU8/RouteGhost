package app.organicmaps.routeghost;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static app.organicmaps.routeghost.RouteData.*;

/** Generates one reproducible journey. Never adds phantom geometry or invents real-world facts. */
public final class SimulationEngine {
    private SimulationEngine() {}
    public static final class Config {
        public boolean pauses=true, gpsNoise=true, trafficModel=true, subwaySignalLoss=false;
        public double pace=1.0;
        public int density=2; // 0 low, 1 standard, 2 realistic
    }
    private static int between(Random r,int lo,int hi){return lo+r.nextInt(Math.max(1,hi-lo+1));}
    private static boolean chance(Random r,double prob){return r.nextDouble()<prob;}
    private static int weighted(Random r,int[] values,int[] weights){int total=0;for(int w:weights)total+=w;int n=r.nextInt(total);for(int i=0;i<values.length;i++){n-=weights[i];if(n<0)return values[i];}return values[values.length-1];}
    private static long add(Timeline t,long at,Pt pos,String label,String type){
        if(!t.frames.isEmpty() && at<=t.frames.get(t.frames.size()-1).atMillis)at=t.frames.get(t.frames.size()-1).atMillis+100;
        t.frames.add(new Keyframe(at,pos,label,type));return at;
    }
    public static Timeline generate(Plan p,Config c){
        if(p.legs.isEmpty())throw new IllegalArgumentException("Нет участков маршрута");
        Timeline t=new Timeline();Random r=new Random(p.seed);
        long now=0;Pt last=null;
        for(int j=0;j<p.legs.size();j++){
            Leg l=p.legs.get(j);if(l.path.isEmpty())continue;
            Pt start=l.path.get(0),finish=l.path.get(l.path.size()-1);
            if(last!=null && metres(last,start)>35){throw new IllegalArgumentException("Разрыв маршрута между участками "+j+" и "+(j+1)+": добавьте соединяющий участок");}
            if(t.frames.isEmpty())now=add(t,0,start,"Отправление",l.type);
            boolean transit=publicTransport(l.type);
            if(transit && c.pauses){
                String waitName=rail(l.type)?"Ожидание поезда":"Ожидание транспорта";
                int wait=rail(l.type)?weighted(r,new int[]{30,60,90,120,180,240},new int[]{12,25,29,19,11,4}):weighted(r,new int[]{45,90,150,240,360,540},new int[]{12,24,29,20,11,4});
                if(j>0 && !WALK.equals(p.legs.get(j-1).type)){
                    int transfer=rail(l.type)?between(r,90,330):between(r,40,180);
                    now=add(t,now+1000L*transfer,start,"Пересадка",WAIT);t.events.add("Пересадка: "+transfer+" с");
                }
                now=add(t,now+wait*1000L,start,waitName,WAIT);t.events.add(waitName+": "+wait+" с");
            }
            double distance=length(l.path);
            int duration=l.baseSeconds;
            if(distance>5){
                double typical= WALK.equals(l.type)?between(r,37,55)/10.0/3.6 : CAR.equals(l.type)?between(r,24,52)/3.6 : rail(l.type)?between(r,24,51)/3.6 : between(r,13,33)/3.6;
                // Use base duration from router for roads and public transport; cap stochastic variation.
                double factor=WALK.equals(l.type)?0.84+r.nextDouble()*0.32 : (CAR.equals(l.type)?0.80+r.nextDouble()*0.4:0.94+r.nextDouble()*0.12);
                duration=Math.max(1,(int)Math.round(duration*factor/Math.max(0.5,Math.min(2,c.pace))));
                if(l.inferredGeometry && !CAR.equals(l.type))duration=Math.max(duration,(int)(distance/typical));
                if(c.trafficModel && (CAR.equals(l.type)||BUS.equals(l.type)||ELECTROBUS.equals(l.type)||MINIBUS.equals(l.type)||TROLLEY.equals(l.type)||TRAM.equals(l.type))){
                    // A statistical time-of-day model, not realtime congestion measurements.
                    java.time.ZoneId zone=java.time.ZoneId.systemDefault();
                    int hour=java.time.LocalTime.now(zone).getHour();
                    double scale=(hour>=7&&hour<10)||(hour>=17&&hour<20) ? 1.22+r.nextDouble()*0.53
                        :(hour>=22||hour<6 ? 0.95+r.nextDouble()*0.13:1.04+r.nextDouble()*0.29);
                    duration=(int)Math.max(1,Math.round(duration*scale));
                }
            }
            List<Pt> path=l.path;
            if(path.size()==1){now=add(t,now+duration*1000L,finish,l.name,l.type);last=finish;continue;}
            double[] cum=new double[path.size()];for(int i=1;i<path.size();i++)cum[i]=cum[i-1]+metres(path.get(i-1),path.get(i));
            ArrayList<Double> breakAt=new ArrayList<>();
            if(c.pauses && c.density>0 && distance>100){
                if(!rail(l.type)){
                    double step=WALK.equals(l.type)?260:(CAR.equals(l.type)?410:700);
                    for(double d=step;d<distance-step/2;d+=step){
                        if(chance(r,c.density==2?0.50:0.27))breakAt.add(d+between(r,-45,45));
                    }
                }
            }
            // Transit stop coordinates, when supplied by route provider, have priority over synthetic events.
            if(transit&&!l.stops.isEmpty()){
                breakAt.clear();for(Pt stop:l.stops){
                    double nearest=0,err=Double.MAX_VALUE;
                    for(int i=0;i<path.size();i++){double e=metres(path.get(i),stop);if(e<err){err=e;nearest=cum[i];}}
                    if(nearest>15&&nearest<distance-15&&err<200)breakAt.add(nearest);
                }
            }
            java.util.Collections.sort(breakAt);
            ArrayList<Double> markers=new ArrayList<>();markers.add(0.0);
            for(double d:breakAt){if(d>markers.get(markers.size()-1)+20 && d<distance-15) markers.add(d);}
            markers.add(distance);
            // Route in short geometric hops. Acceleration/easing around stops but no jumps.
            for(int part=1;part<markers.size();part++){
                double from=markers.get(part-1),to=markers.get(part);
                int n=Math.max(1,(int)Math.ceil((to-from)/25));
                for(int k=1;k<=n;k++){
                    double d=from+(to-from)*k/n;
                    int idx=1;while(idx<cum.length-1&&cum[idx]<d)idx++;
                    double span=Math.max(0.001,cum[idx]-cum[idx-1]);Pt point=mix(path.get(idx-1),path.get(idx),(d-cum[idx-1])/span);
                    double fraction=(to-from)/Math.max(0.001,distance)/n;
                    // Easing adjusts each hop duration by a small factor, preserving route geometry.
                    double phase=(double)k/n;
                    double easing=(k==1||k==n)?1.35:(phase<0.18||phase>0.82?1.14:0.91);
                    long hop=Math.max(250,Math.round(duration*1000*fraction*easing));
                    now=add(t,now+hop,point,l.name,l.type);
                }
                if(part<markers.size()-1 && c.pauses){
                    boolean stop=transit&&!l.stops.isEmpty();
                    String text=stop?"Остановка транспорта":WALK.equals(l.type)?"Пешеходный переход":CAR.equals(l.type)?"Светофор / затор":"Дорожная остановка";
                    int delay=stop?between(r,14,42):WALK.equals(l.type)?weighted(r,new int[]{10,20,35,50,70},new int[]{15,29,30,20,6}):CAR.equals(l.type)?weighted(r,new int[]{10,25,40,60,85},new int[]{15,25,28,24,8}):between(r,10,35);
                    Pt point=t.frames.get(t.frames.size()-1).pos;
                    now=add(t,now+delay*1000L,point,text,WAIT);t.events.add(text+": "+delay+" с");
                }
            }
            last=finish;
        }
        if(t.frames.size()<2)throw new IllegalArgumentException("Слишком короткий маршрут");
        return t;
    }
    public static Sample sample(Timeline t,long timeMillis,boolean noise,long seed){
        if(t.frames.isEmpty())throw new IllegalArgumentException("Пустой сценарий");
        int low=0,high=t.frames.size()-1;
        while(low<high){int m=(low+high+1)/2;if(t.frames.get(m).atMillis<=timeMillis)low=m;else high=m-1;}
        Keyframe a=t.frames.get(low),b=t.frames.get(Math.min(low+1,t.frames.size()-1));
        double fraction=b.atMillis<=a.atMillis?0:Math.max(0,Math.min(1,(timeMillis-a.atMillis)/(double)(b.atMillis-a.atMillis)));
        Pt p=mix(a.pos,b.pos,fraction);
        double secs=(b.atMillis-a.atMillis)/1000.0;
        double speed=secs<=0?0:Math.min(45,metres(a.pos,b.pos)/secs);
        if(timeMillis>=t.duration())speed=0;
        double bearing=metres(a.pos,b.pos)>0.5?bearing(a.pos,b.pos):0;
        String label=(metres(a.pos,b.pos)<0.2&&b.atMillis>a.atMillis)?b.label:a.label;
        String type=(metres(a.pos,b.pos)<0.2&&b.atMillis>a.atMillis)?WAIT:a.type;
        // Stable deterministic low-frequency GPS drift; does not imply actual movement.
        if(noise){double ts=timeMillis/1000.0;double north=Math.sin(ts*0.29+seed*.001)*1.7+Math.sin(ts*.071)*.7;
            double east=Math.cos(ts*.21+seed*.001)*1.5;
            p=new Pt(p.lat+north/111111.0,p.lon+east/(111111.0*Math.max(0.1,Math.cos(Math.toRadians(p.lat)))));
        }
        return new Sample(p,speed,bearing,label,type);
    }
}
