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
