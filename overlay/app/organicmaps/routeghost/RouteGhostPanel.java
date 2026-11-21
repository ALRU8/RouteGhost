package app.organicmaps.routeghost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import static app.organicmaps.routeghost.RouteData.*;

public final class RouteGhostPanel extends Activity {
    private static final int INK=0xFF18271E, GREEN=0xFF168454, MUTED=0xFF6B7B70, BORDER=0xFFE4EDE7;
    private static final int PICK_TRACK=301;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int mode=0;
    private LinearLayout root,main,tabStrip;
    private TextView statusText,summaryText;
    private EditText latitude,longitude,customText;
    private Spinner transport;
    private CheckBox noise,pauses,traffic,signal;
    private boolean routeLoaded=false, captureFailed=false;
    private final String[] transportNames={"Автоматически", "Пешком", "Автомобиль", "Автобус", "Метро", "Трамвай", "Троллейбус", "Электробус", "Электричка", "Монорельс"};
    private final String[] transportTypes={"",WALK,CAR,BUS,METRO,TRAM,TROLLEY,ELECTROBUS,TRAIN,MONORAIL};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        captureFailed=getIntent().getBooleanExtra("capture_attempted",false)&&!getIntent().getBooleanExtra("captured",false);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFFF7FAF8);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        main=new LinearLayout(this);main.setPadding(dp(20),dp(14),dp(20),dp(22));main.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(main);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        draw();
        handler.post(refreshStatus);
    }
    private int dp(int val){return (int)(val*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable box(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String message,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(message);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void put(LinearLayout parent,View view,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(view,lp);}
    private Button button(String message,boolean primary){Button b=new Button(this);b.setText(message);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:INK);b.setBackground(box(primary?GREEN:0xFFEDF3EF,17));b.setMinHeight(dp(48));b.setElevation(0);return b;}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setText(value);e.setSingleLine(true);e.setTextSize(15);e.setHint(hint);e.setPadding(dp(14),dp(9),dp(14),dp(9));e.setTextColor(INK);e.setBackground(box(Color.WHITE,12));return e;}
    private void draw(){
        main.removeAllViews();
        TextView title=text("RouteGhost",26,INK,true);put(main,title,4);
        put(main,text("GPS-симуляция на маршрутах Organic Maps",12,MUTED,false),2);
        tabStrip=new LinearLayout(this);tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels={"Маршрут","Одна точка","Свой"};
        for(int i=0;i<3;i++){
            final int selected=i;
            TextView chip=text(labels[i],13,mode==i?Color.WHITE:INK,mode==i);
            chip.setGravity(Gravity.CENTER);chip.setPadding(dp(8),dp(14),dp(8),dp(14));
            chip.setBackground(box(mode==i?GREEN:Color.WHITE,13));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(6);tabStrip.addView(chip,lp);
            chip.setOnClickListener(v->{mode=selected;draw();});
        }
        put(main,tabStrip,23);
        if(mode==0)drawRoute(); else if(mode==1)drawFixed();else drawCustom();
        drawControls();
    }
    private void drawRoute(){
        if(captureFailed)put(main,text("Не удалось получить координаты выбранного маршрута. Убедитесь, что Organic Maps завершил построение, и откройте GPS заново. Можно также импортировать GPX. Старый маршрут не запустится.",13,0xFFB34E27,true),18);
        LinearLayout card=vertical();card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(box(Color.WHITE,19));
        try{
            Plan p=RouteGhostStorage.load(this);routeLoaded=!captureFailed;
            put(card,text(p.title,18,INK,true),0);
            put(card,text(String.format(Locale.getDefault(),"%.1f км · %d мин · %d участок(ов)",p.length()/1000.0,Math.max(1,p.totalBaseSeconds()/60),p.legs.size()),14,MUTED,false),7);
            if(!p.note.isEmpty())put(card,text(p.note,12,MUTED,false),8);
        }catch(Exception ex){routeLoaded=false;put(card,text("Маршрут пока не выбран",18,INK,true),0);put(card,text("Вернитесь на карту, постройте маршрут средствами Organic Maps, выберите нужный вариант и нажмите GPS.",13,MUTED,false),10);}
        put(main,card,20);
        put(main,text("Тип движения",14,INK,true),17);
        transport=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,transportNames);
        transport.setAdapter(adapter);put(main,transport,8);
        if(routeLoaded){
            try{
                Plan p=RouteGhostStorage.load(this);
                if(!p.legs.isEmpty())for(int i=1;i<transportTypes.length;i++)if(Objects.equals(transportTypes[i],p.legs.get(0).type)){transport.setSelection(i);break;}
            }catch(Exception ignored){}
        }
        Button refresh=button("Обновить выбранный маршрут с карты",false);
        refresh.setOnClickListener(v->{Toast.makeText(this,"Вернитесь на карту, выберите маршрут и откройте GPS снова",Toast.LENGTH_LONG).show();finish();});
        put(main,refresh,12);
    }
    private void drawFixed(){
        put(main,text("Фиксированное местоположение",18,INK,true),22);
        put(main,text("Укажите широту и долготу в градусах. Точка сохраняет скорость 0 км/ч.",13,MUTED,false),8);
        latitude=input("Широта",getPreferences(0).getString("lat",""));longitude=input("Долгота",getPreferences(0).getString("lon",""));
        put(main,latitude,18);put(main,longitude,9);
        put(main,text("Для установки точки с карты: откройте Organic Maps, найдите место и скопируйте координаты.",12,MUTED,false),14);
    }
    private void drawCustom(){
        put(main,text("Собственный GPS-трек",18,INK,true),21);
        put(main,text("Импортируйте GPX/GeoJSON или задайте точки по строкам. Прямая между ручными точками не является построенной дорогой.",13,MUTED,false),8);
        Button importFile=button("Импорт GPX / GeoJSON",true);
        importFile.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_TRACK);});
        put(main,importFile,17);
        customText=new EditText(this);customText.setHint("Широта, долгота, тип участка\n51.5034, -0.1276, WALK\n51.5050, -0.1200, BUS\n51.5080, -0.1130, WALK");
        customText.setMinLines(5);customText.setGravity(Gravity.TOP);customText.setTextSize(14);customText.setPadding(dp(14),dp(14),dp(14),dp(14));
        customText.setBackground(box(Color.WHITE,15));put(main,customText,14);
        Button apply=button("Сохранить свои точки",false);
        apply.setOnClickListener(v->{try{Plan plan=parseCustom(customText.getText().toString());RouteGhostStorage.save(this,plan);captureFailed=false;toast("Свой маршрут сохранён");mode=0;draw();}catch(Exception ex){toast(ex.getMessage());}});
        put(main,apply,9);
    }
    private Plan parseCustom(String source)throws Exception{
        String[] lines=source.trim().split("\\r?\\n");
        if(lines.length<2||lines.length>10000)throw new Exception("Укажите от 2 до 10000 точек");
        Plan plan=new Plan();plan.title="Свой маршрут";plan.source="Ручной ввод";
        Pt last=null;
        for(String line:lines){
            if(line.trim().isEmpty())continue;
            String[] parts=line.split(",");if(parts.length<2)throw new Exception("Строка должна содержать широту, долготу");
            Pt p=new Pt(Double.parseDouble(parts[0].trim()),Double.parseDouble(parts[1].trim()));
            String type=parts.length>2?parts[2].trim().toUpperCase(Locale.ROOT):WALK;
            if(!validType(type))throw new Exception("Неизвестный тип движения: "+type);
            if(last!=null){
                ArrayList<Pt> path=new ArrayList<>();path.add(last);path.add(p);
                double metres=metres(last,p);
                if(metres>100_000)throw new Exception("Слишком длинный разрыв между точками");
                int seconds=Math.max(1,(int)(metres/typicalSpeed(type)));
                plan.legs.add(new Leg(type,display(type),path,seconds));
            }
            last=p;
        }
        if(plan.legs.isEmpty())throw new Exception("Нужно минимум 2 точки");
        return plan;
    }
    private boolean validType(String type){for(String t:transportTypes)if(type.equals(t))return true;return type.equals(MCC)||type.equals(MCD)||type.equals(FERRY)||type.equals(MINIBUS);}
    private double typicalSpeed(String t){return t.equals(WALK)?1.35:t.equals(CAR)?11.0:rail(t)?11.0:6.5;}
    private void drawControls(){
        put(main,text("Симуляция",17,INK,true),24);
        pauses=new CheckBox(this);pauses.setText("Ожидания и остановки");pauses.setChecked(true);put(main,pauses,8);
        traffic=new CheckBox(this);traffic.setText("Статистическая модель пробок");traffic.setChecked(true);put(main,traffic,0);
        noise=new CheckBox(this);noise.setText("Небольшая GPS-погрешность");noise.setChecked(true);put(main,noise,0);
        signal=new CheckBox(this);signal.setText("Потеря GPS в метро");signal.setChecked(false);put(main,signal,0);
        Button start=button("▶  Начать GPS",true);start.setOnClickListener(v->startSimulation());put(main,start,13);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button pause=button("Пауза",false);Button resume=button("Продолжить",false);Button stop=button("Стоп",false);
        Button[] buttons={pause,resume,stop};
        for(Button b:buttons){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(47),1);p.rightMargin=dp(5);row.addView(b,p);}
        pause.setOnClickListener(v->command(LocationService.PAUSE));resume.setOnClickListener(v->command(LocationService.RESUME));stop.setOnClickListener(v->command(LocationService.STOP));
        put(main,row,8);
        statusText=text("",13,MUTED,false);put(main,statusText,15);
        put(main,text("Android: в параметрах разработчика выберите приложение RouteGhost как источник фиктивных местоположений. Симуляция предназначена для тестирования.",12,MUTED,false),13);
        updateStatus();
    }
    private void startSimulation(){
        try{
            Intent intent=new Intent(this,LocationService.class).setAction(LocationService.START);
            if(mode==1){
                double lat=Double.parseDouble(latitude.getText().toString().trim().replace(',','.'));
                double lon=Double.parseDouble(longitude.getText().toString().trim().replace(',','.'));
                new Pt(lat,lon);
                getPreferences(0).edit().putString("lat",String.valueOf(lat)).putString("lon",String.valueOf(lon)).apply();
                intent.putExtra("fixed",true).putExtra("lat",lat).putExtra("lon",lon);
            }else{
                if(captureFailed)throw new Exception("Выбранный маршрут не содержит доступной GPS-геометрии");
                Plan p=RouteGhostStorage.load(this);
                if(mode==2)throw new Exception("Сначала сохраните собственный маршрут");
                if(transport!=null&&transport.getSelectedItemPosition()>0&&p.legs.size()==1){
                    p.legs.get(0).type=transportTypes[transport.getSelectedItemPosition()];
                }
                SimulationEngine.Config cfg=new SimulationEngine.Config();
                cfg.pauses=pauses.isChecked();cfg.trafficModel=traffic.isChecked();cfg.gpsNoise=noise.isChecked();
                Timeline timeline=SimulationEngine.generate(p,cfg);
                RouteGhostStorage.saveTimeline(this,timeline);
                intent.putExtra("fixed",false).putExtra("auto_stop",false).putExtra("subway_loss",signal.isChecked());
            }
            intent.putExtra("noise",noise.isChecked());
            if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
            toast("Запуск GPS. Проверьте настройки Mock Location.");
        }catch(Exception ex){toast("Не удалось запустить: "+ex.getMessage());}
    }
    private void command(String action){startService(new Intent(this,LocationService.class).setAction(action));handler.postDelayed(this::updateStatus,200);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void updateStatus(){if(statusText!=null){
        String status=LocationService.status;
        if(LocationService.running){status+=(LocationService.paused?" · пауза":String.format(Locale.getDefault()," · %.1f км/ч",LocationService.speed*3.6));}
        statusText.setText(status);
    }}
    private final Runnable refreshStatus=new Runnable(){@Override public void run(){updateStatus();handler.postDelayed(this,1000);}};
    @Override protected void onDestroy(){handler.removeCallbacks(refreshStatus);super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_TRACK||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new Exception("Файл недоступен");byte[] block=new byte[16*1024];int n;
            while((n=in.read(block))>0){out.write(block,0,n);if(out.size()>12_000_000)throw new Exception("Файл слишком большой");}
            String name="track.gpx";String path=data.getData().getLastPathSegment();
            if(path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".json"))name="track.json";
            else if(path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".geojson"))name="track.geojson";
            Plan plan=TrackIO.read(out.toByteArray(),name);RouteGhostStorage.save(this,plan);captureFailed=false;
            toast("Маршрут импортирован: "+plan.legs.size()+" участков");mode=0;draw();
        }catch(Exception ex){toast("Ошибка файла: "+ex.getMessage());}
    }
}
