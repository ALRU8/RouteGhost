package app.organicmaps.routeghost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static app.organicmaps.routeghost.RouteData.*;

public final class LocationService extends Service {
    public static final String START="app.organicmaps.routeghost.START", STOP="app.organicmaps.routeghost.STOP", PAUSE="app.organicmaps.routeghost.PAUSE", RESUME="app.organicmaps.routeghost.RESUME", UPDATE="app.organicmaps.routeghost.UPDATE";
    public static volatile boolean running=false, paused=false, fixed=false;
    public static volatile String status="Эмуляция выключена";
    public static volatile Pt position=null;
    public static volatile double speed=0;
    public static volatile long elapsed=0,total=0;
    private static final String CHANNEL="routeghost_simulation";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<String> providers=new ArrayList<>();
    private LocationManager manager;
    private PowerManager.WakeLock wake;
    private Timeline timeline;
    private Pt fixedPoint;
    private boolean noisy,subwayLoss,loop,autoStop;
    private long startedAt, pausedAt, pausedDuration, seed;
    private float multiplier=1f;
    private long lastNotification=0;

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i==null)return START_NOT_STICKY;
        String action=i.getAction();
        if(STOP.equals(action)){halt("Эмуляция остановлена");return START_NOT_STICKY;}
        if(PAUSE.equals(action)){if(running && !paused){paused=true;pausedAt=SystemClock.elapsedRealtime();status="Пауза";notifyStatus();}return START_NOT_STICKY;}
        if(RESUME.equals(action)){if(running && paused){pausedDuration+=SystemClock.elapsedRealtime()-pausedAt;paused=false;status="Продолжается";notifyStatus();}return START_NOT_STICKY;}
        if(!START.equals(action))return START_NOT_STICKY;
        if(running){
            handler.removeCallbacks(tick);
            if(manager!=null)for(String provider:providers){try{manager.removeTestProvider(provider);}catch(Exception ignored){}}
            providers.clear();
            if(wake!=null&&wake.isHeld())wake.release();wake=null;
            running=false;paused=false;
        }
        try{
            makeForeground("Запуск GPS-тестирования");
            fixed=i.getBooleanExtra("fixed",false);
            noisy=i.getBooleanExtra("noise",false);subwayLoss=i.getBooleanExtra("subway_loss",false);
            loop=i.getBooleanExtra("loop",false);autoStop=i.getBooleanExtra("auto_stop",false);
            seed=i.getLongExtra("seed",System.currentTimeMillis());
            multiplier=Math.max(0.25f,Math.min(10f,i.getFloatExtra("multiplier",1)));
            if(fixed){fixedPoint=new Pt(i.getDoubleExtra("lat",55.7512),i.getDoubleExtra("lon",37.6184));total=0;timeline=null;}
            else{
                File input=new File(getFilesDir(),"active-timeline.json");
                if(!input.exists()||input.length()>20_000_000)throw new Exception("Сценарий маршрута не найден или слишком большой");
                byte[] bytes=new byte[(int)input.length()];try(FileInputStream in=new FileInputStream(input)){int off=0;while(off<bytes.length){int n=in.read(bytes,off,bytes.length-off);if(n==-1)break;off+=n;}}
                timeline=Timeline.read(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));
                if(timeline.frames.isEmpty())throw new Exception("Маршрут пустой");
                total=timeline.duration();
            }
            manager=getSystemService(LocationManager.class);
            for(String p:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
                try {
                    manager.addTestProvider(p,false,false,false,false,true,true,true,1,1);
                    manager.setTestProviderEnabled(p,true);providers.add(p);
                }catch(SecurityException ex){if(providers.isEmpty())throw ex;}
                catch(IllegalArgumentException ex){if(providers.isEmpty())throw ex; }
            }
            if(providers.isEmpty())throw new Exception("Не удалось подключить тестовые GPS-провайдеры");
            PowerManager powers=getSystemService(PowerManager.class);
            wake=powers.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RouteGhost:GPSReplay");wake.acquire();
            startedAt=SystemClock.elapsedRealtime();pausedDuration=0;paused=false;running=true;status=fixed?"Точка закреплена":"Движение по маршруту";
            tick.run();
        }catch(SecurityException e){halt("Выберите RouteGhost в настройках разработчика → фиктивные местоположения");}
        catch(Exception e){halt("Ошибка запуска: "+e.getMessage());}
        return START_NOT_STICKY;
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!running)return;
        try{
            long now=SystemClock.elapsedRealtime();
            long ms=paused?(pausedAt-startedAt-pausedDuration):(now-startedAt-pausedDuration);
            long current=Math.max(0,(long)(ms*multiplier));
            elapsed=fixed?0:loop&&total>0?current%total:Math.min(current,total);
            Sample sample;
            if(fixed){Pt p=fixedPoint;
                if(noisy){double phase=now/1000.0;double dy=1.1*Math.sin(phase*0.21+seed*.001);double dx=1.0*Math.cos(phase*.17+seed*.001);
                    p=new Pt(p.lat+dy/111111.0,p.lon+dx/(111111.0*Math.max(0.1,Math.cos(Math.toRadians(p.lat)))));
                }
                sample=new Sample(p,0,0,"Фиксированная точка",WAIT);
            }else{sample=SimulationEngine.sample(timeline,elapsed,noisy,seed);}
            position=sample.pos;speed=paused?0:sample.speedMps*(fixed?0:multiplier);
            status=paused?"Пауза":fixed?"Фиксированная точка":elapsed>=total?"Маршрут завершён, точка удерживается":sample.label;

            if(!(subwayLoss && METRO.equals(sample.type) && !fixed))for(String provider:providers){
                Location loc=new Location(provider);
                loc.setLatitude(sample.pos.lat);loc.setLongitude(sample.pos.lon);
                loc.setAccuracy(noisy?5f:3f);loc.setSpeed((float)speed);loc.setBearing((float)sample.bearing);
                loc.setTime(System.currentTimeMillis());loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                manager.setTestProviderLocation(provider,loc);
            }
            if(now-lastNotification>8000){notifyManager();lastNotification=now;}
            sendBroadcast(new Intent(UPDATE).setPackage(getPackageName()));
            if(!fixed&&!loop&&autoStop&&current>=total){handler.postDelayed(()->halt("Маршрут завершён"),1000);}
            else handler.postDelayed(this,1000);
        }catch(Exception e){halt("Ошибка GPS: "+e.getMessage());}
    }};
    private void makeForeground(String desc){
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Эмуляция GPS",NotificationManager.IMPORTANCE_LOW));
        startForeground(1701,notification(desc));
    }
    private Notification notification(String desc){
        int flags=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,RouteGhostPanel.class),flags);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,LocationService.class).setAction(STOP),flags);
        PendingIntent pause=PendingIntent.getService(this,2,new Intent(this,LocationService.class).setAction(paused?RESUME:PAUSE),flags);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("RouteGhost · тестовый GPS")
            .setContentText(desc).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,paused?"Продолжить":"Пауза",pause).build())
            .addAction(new Notification.Action.Builder(null,"Стоп",stop).build()).build();
    }
    private void notifyManager(){getSystemService(NotificationManager.class).notify(1701,notification(status));}
    private void notifyStatus(){notifyManager();sendBroadcast(new Intent(UPDATE).setPackage(getPackageName()));}
    private void halt(String desc){
        handler.removeCallbacks(tick);
        running=false;paused=false;position=null;speed=0;elapsed=0;total=0;
        if(manager!=null)for(String provider:providers){try{manager.removeTestProvider(provider);}catch(Exception ignored){}}
        providers.clear();
        if(wake!=null&&wake.isHeld())wake.release();wake=null;
        status=desc;
        sendBroadcast(new Intent(UPDATE).setPackage(getPackageName()));
        try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Exception ignored){}
        stopSelf();
    }
    @Override public void onDestroy(){if(running)halt("Эмуляция выключена");super.onDestroy();}
}
