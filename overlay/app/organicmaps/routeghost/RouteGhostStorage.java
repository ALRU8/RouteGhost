package app.organicmaps.routeghost;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static app.organicmaps.routeghost.RouteData.*;

public final class RouteGhostStorage {
    private RouteGhostStorage() {}
    private static File file(Context c){return new File(c.getFilesDir(),"routeghost-plan.json");}
    public static void save(Context c,Plan plan)throws Exception{
        byte[] bytes=plan.json().toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>10_000_000)throw new Exception("Маршрут превышает допустимый размер");
        try(FileOutputStream out=new FileOutputStream(file(c))){out.write(bytes);}
    }
    public static Plan load(Context c)throws Exception{
        File f=file(c);
        if(!f.isFile()||f.length()>10_000_000)throw new Exception("Сначала постройте или импортируйте маршрут");
        byte[] data=new byte[(int)f.length()];
        try(InputStream in=new FileInputStream(f)){
            int offset=0,n;while(offset<data.length&&(n=in.read(data,offset,data.length-offset))!=-1)offset+=n;
            if(offset!=data.length)throw new IOException("Неполный файл маршрута");
        }
        return Plan.read(new JSONObject(new String(data,StandardCharsets.UTF_8)));
    }
    public static void saveTimeline(Context c,Timeline timeline)throws Exception{
        byte[] data=timeline.json().toString().getBytes(StandardCharsets.UTF_8);
        if(data.length>20_000_000)throw new Exception("Сценарий слишком большой");
        try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"active-timeline.json"))){out.write(data);}
    }
}
