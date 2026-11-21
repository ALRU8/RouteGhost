package app.organicmaps.routeghost;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static app.organicmaps.routeghost.RouteData.*;

/** Imported GPX is treated as user-supplied geometry, not a validated transit route. */
public final class TrackIO {
    private TrackIO(){}
    public static Plan read(byte[] bytes,String filename)throws Exception{
        String text=new String(bytes,StandardCharsets.UTF_8);
        if(filename.toLowerCase(Locale.ROOT).endsWith(".gpx")||text.trim().startsWith("<?xml"))return gpx(bytes);
        JSONObject json=new JSONObject(text);
        if(json.has("legs"))return Plan.read(json);
        JSONArray coords=null;
        if("FeatureCollection".equals(json.optString("type"))){JSONArray feats=json.getJSONArray("features");for(int j=0;j<feats.length();j++){JSONObject g=feats.getJSONObject(j).optJSONObject("geometry");if(g!=null&&"LineString".equals(g.optString("type"))){coords=g.optJSONArray("coordinates");break;}}}
        else if("Feature".equals(json.optString("type")))coords=json.getJSONObject("geometry").optJSONArray("coordinates");
        else if("LineString".equals(json.optString("type")))coords=json.optJSONArray("coordinates");
        if(coords==null)throw new Exception("Импортируются RouteGhost JSON, GeoJSON LineString или GPX");
        ArrayList<Pt> points=new ArrayList<>();for(int i=0;i<coords.length();i++){JSONArray p=coords.getJSONArray(i);points.add(new Pt(p.getDouble(1),p.getDouble(0)));}
        return make(points,"Импорт GeoJSON");
    }
    private static Plan gpx(byte[] data)throws Exception{
        XmlPullParser xml=XmlPullParserFactory.newInstance().newPullParser();xml.setInput(new ByteArrayInputStream(data),"UTF-8");
        ArrayList<Pt> points=new ArrayList<>();int event;
        while((event=xml.next())!=XmlPullParser.END_DOCUMENT){if(event==XmlPullParser.START_TAG && ("trkpt".equals(xml.getName())||"rtept".equals(xml.getName()))){
            points.add(new Pt(Double.parseDouble(xml.getAttributeValue(null,"lat")),Double.parseDouble(xml.getAttributeValue(null,"lon"))));
            if(points.size()>300_000)throw new Exception("GPX слишком большой");
        }}
        return make(points,"Импорт GPX");
    }
    private static Plan make(List<Pt> list,String name)throws Exception{
        if(list.size()<2)throw new Exception("В файле менее двух точек");
        Plan plan=new Plan();plan.title=name;plan.source="Пользовательский файл";
        plan.legs.add(new Leg(WALK,"Загруженный трек",list,Math.max(1,(int)Math.round(length(list)/1.35))));return plan;
    }
    public static String gpx(Plan p){
        StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"RouteGhost\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>RouteGhost</name><trkseg>");
        for(Leg l:p.legs)for(Pt pt:l.path)s.append(String.format(Locale.US,"<trkpt lat=\"%.7f\" lon=\"%.7f\"/>",pt.lat,pt.lon));
        return s.append("</trkseg></trk></gpx>").toString();
    }
}
