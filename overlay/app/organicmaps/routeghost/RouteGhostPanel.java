package app.organicmaps.routeghost;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import static app.organicmaps.routeghost.RouteData.*;

public final class RouteGhostPanel extends Activity {
    private static final int BG=0xFF141226, PANEL=0xFF1F1C3A, PANEL_HI=0xFF2A2650, EDGE=0xFF3A3566;
    private static final int TEXT=0xFFF1EEFF, MUTED=0xFF9C97C4, GHOST=0xFFA88BFF, ON_GHOST=0xFF1A1033, WARN=0xFFFF8A65;
    private static final int PICK_TRACK=301;
    private static final String[] CORE_TYPES={WALK,CAR,BUS,TROLLEY,TRAM,METRO,TRAIN,MONORAIL,ELECTROBUS};
    private static final String[] TRANSPORT_NAMES={"Авто","Пешком","Автомобиль","Автобус","Метро","Трамвай","Троллейбус","Электробус","Электричка","Монорельс"};
    private static final String[] TRANSPORT_TYPES={"",WALK,CAR,BUS,METRO,TRAM,TROLLEY,ELECTROBUS,TRAIN,MONORAIL};

    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,EditText[]> speedFields=new LinkedHashMap<>();
    private int mode=0;
    private int selectedTransport=0;
    private LinearLayout main;
    private TextView statusText,speedText;
    private EditText latitude,longitude,customText;
    private CheckBox noise,pauses,traffic,signal;
    private RouteStrip strip;
    private Plan loaded;
    private boolean routeLoaded=false, captureFailed=false;

    private static int lineColor(String type){
        switch(type){
            case WALK:return 0xFFF1EEFF;
            case CAR:return 0xFFFFB020;
            case BUS:case ELECTROBUS:case MINIBUS:return 0xFF3FA7FF;
            case TROLLEY:return 0xFF38D6C0;
            case TRAM:return 0xFFFF8A3D;
            case METRO:case MCC:case MCD:return 0xFFFF5A7A;
            case TRAIN:case MONORAIL:return 0xFF7BD957;
            default:return 0xFFC0A8FF;
        }
    }

    private static final class RouteStrip extends View {
        private final ArrayList<Integer> colors=new ArrayList<>();
        private final ArrayList<Double> lengths=new ArrayList<>();
        private final ArrayList<Boolean> dashed=new ArrayList<>();
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float unit;
        private float progress=-1f;

        RouteStrip(Context context,float unit){
            super(context);
            this.unit=unit;
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        void add(int color,double length,boolean dash){colors.add(color);lengths.add(Math.max(1,length));dashed.add(dash);}
        void setProgress(float value){progress=value;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            int n=colors.size();
            if(n==0)return;
            float pad=16*unit;
            float width=getWidth()-2*pad;
            float y=getHeight()/2f;
            double sum=0;
            for(double l:lengths)sum+=l;
            double[] share=new double[n];
            double shareSum=0;
            for(int i=0;i<n;i++){share[i]=Math.max(lengths.get(i)/sum,0.08);shareSum+=share[i];}
            float[] edge=new float[n+1];
            edge[0]=pad;
            for(int i=0;i<n;i++)edge[i+1]=edge[i]+(float)(width*share[i]/shareSum);
            paint.setStyle(Paint.Style.STROKE);
            for(int i=0;i<n;i++){
                boolean dash=dashed.get(i);
                paint.setColor(colors.get(i));
                paint.setStrokeWidth(dash?3*unit:8*unit);
                paint.setPathEffect(dash?new DashPathEffect(new float[]{7*unit,8*unit},0):null);
                float inset=edge[i+1]-edge[i]>14*unit?4*unit:0;
                canvas.drawLine(edge[i]+inset,y,edge[i+1]-inset,y,paint);
            }
            paint.setPathEffect(null);
            for(int i=0;i<=n;i++){
                boolean ends=i==0||i==n;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(BG);
                canvas.drawCircle(edge[i],y,(ends?8:6)*unit,paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f*unit);
                paint.setColor(TEXT);
                canvas.drawCircle(edge[i],y,(ends?8:6)*unit,paint);
            }
            if(progress>=0){
                float cx=pad+width*Math.max(0f,Math.min(1f,progress));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(GHOST);
                paint.setAlpha(70);
                canvas.drawCircle(cx,y,17*unit,paint);
                paint.setAlpha(255);
                canvas.drawCircle(cx,y,9*unit,paint);
                paint.setColor(ON_GHOST);
                canvas.drawCircle(cx,y,3.5f*unit,paint);
            }
        }
    }

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        captureFailed=getIntent().getBooleanExtra("capture_attempted",false)&&!getIntent().getBooleanExtra("captured",false);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        main=new LinearLayout(this);
        main.setPadding(dp(18),dp(18),dp(18),dp(28));
        main.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(main);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        draw();
        handler.post(refreshStatus);
    }

    private int dp(int val){return (int)(val*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable box(int color,int radius){return box(color,radius,0,0);}
    private GradientDrawable box(int color,int radius,int strokeColor,int strokeDp){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if(strokeDp>0)d.setStroke(dp(strokeDp),strokeColor);
        return d;
    }
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout card(){
        LinearLayout card=vertical();
        card.setPadding(dp(16),dp(16),dp(16),dp(16));
        card.setBackground(box(PANEL,20,EDGE,1));
        return card;
    }
    private TextView text(String message,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(message);
        t.setTextColor(color);
        t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView display(String message,int size,int color){
        TextView t=text(message,size,color,true);
        t.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        return t;
    }
    private void put(LinearLayout parent,View view,int top){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(top);
        parent.addView(view,lp);
    }
    private Button button(String message,boolean primary){
        Button b=new Button(this);
        b.setText(message);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(primary?ON_GHOST:TEXT);
        b.setBackground(primary?box(GHOST,16):box(PANEL_HI,16,EDGE,1));
        b.setMinHeight(dp(50));
        b.setStateListAnimator(null);
        return b;
    }
    private EditText input(String hint,String value,boolean numeric){
        EditText e=new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setPadding(dp(14),dp(10),dp(14),dp(10));
        e.setBackground(box(PANEL_HI,12,EDGE,1));
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
    private CheckBox check(String label,boolean on){
        CheckBox c=new CheckBox(this);
        c.setText(label);
        c.setTextColor(TEXT);
        c.setTextSize(14);
        c.setChecked(on);
        c.setButtonTintList(ColorStateList.valueOf(GHOST));
        return c;
    }
    private TextView chip(String label,boolean selected){
        TextView chip=text(label,13,selected?ON_GHOST:TEXT,selected);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14),dp(9),dp(14),dp(9));
        chip.setBackground(selected?box(GHOST,18):box(PANEL_HI,18,EDGE,1));
        return chip;
    }
    private View dot(int color,int size){
        View v=new View(this);
        v.setBackground(box(color,size));
        return v;
    }

    private static String fmt(double v){return v==Math.rint(v)?String.valueOf((int)v):String.valueOf(v);}

    private void draw(){
        rememberSpeeds();
        main.removeAllViews();
        speedFields.clear();
        strip=null;
        loaded=null;
        put(main,display("RouteGhost",32,TEXT),2);
        put(main,text("GPS-симуляция на маршрутах Organic Maps",13,MUTED,false),2);
        LinearLayout tabs=horizontal();
        tabs.setPadding(dp(4),dp(4),dp(4),dp(4));
        tabs.setBackground(box(PANEL,22,EDGE,1));
        String[] labels={"Маршрут","Одна точка","Свой трек"};
        for(int i=0;i<3;i++){
            final int selected=i;
            TextView tab=text(labels[i],14,mode==i?ON_GHOST:MUTED,mode==i);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(6),dp(12),dp(6),dp(12));
            if(mode==i)tab.setBackground(box(GHOST,18));
            tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
            tab.setOnClickListener(v->{mode=selected;draw();});
        }
        put(main,tabs,20);
        if(mode==0)drawRoute();else if(mode==1)drawFixed();else drawCustom();
        if(mode!=1)drawSpeeds();
        drawControls();
    }

    private double midSpeedMps(String type){
        double[] range=currentRange(type);
        return Math.max(0.3,(range[0]+range[1])/2/3.6);
    }
    private double[] currentRange(String type){
        SharedPreferences prefs=getPreferences(0);
        double[] fallback=SimulationEngine.defaultRange(type);
        try{
            double lo=Double.parseDouble(prefs.getString("min_"+type,"").trim().replace(',','.'));
            double hi=Double.parseDouble(prefs.getString("max_"+type,"").trim().replace(',','.'));
            if(lo>0&&hi>=lo)return new double[]{lo,hi};
        }catch(Exception ignored){}
        return fallback;
    }

    private void drawRoute(){
        if(captureFailed){
            LinearLayout warn=card();
            warn.setBackground(box(0xFF3A2224,20,WARN,1));
            warn.addView(text("Маршрут не прочитан",16,WARN,true));
            put(warn,text("Дождитесь, пока Organic Maps закончит построение, и откройте GPS снова. Можно импортировать GPX. Старый маршрут не запустится.",13,TEXT,false),6);
            put(main,warn,18);
        }
        try{loaded=RouteGhostStorage.load(this);}catch(Exception ex){loaded=null;}
        routeLoaded=loaded!=null&&!captureFailed;
        LinearLayout card=card();
        if(routeLoaded){
            double metres=loaded.length();
            double seconds=0;
            for(Leg l:loaded.legs){
                String type=effectiveType(l);
                seconds+=length(l.path)/midSpeedMps(type);
            }
            card.addView(text(loaded.title,17,TEXT,true));
            LinearLayout stats=horizontal();
            stats.addView(stat(String.format(Locale.getDefault(),"%.1f",metres/1000.0),"км"),new LinearLayout.LayoutParams(0,-2,1));
            stats.addView(stat("≈"+Math.max(1,(int)Math.round(seconds/60)),"мин"),new LinearLayout.LayoutParams(0,-2,1));
            stats.addView(stat(String.valueOf(loaded.legs.size()),loaded.legs.size()==1?"участок":"участков"),new LinearLayout.LayoutParams(0,-2,1));
            put(card,stats,12);
            strip=new RouteStrip(this,getResources().getDisplayMetrics().density);
            for(Leg l:loaded.legs){
                String type=effectiveType(l);
                strip.add(lineColor(type),length(l.path),WALK.equals(type));
            }
            LinearLayout.LayoutParams stripParams=new LinearLayout.LayoutParams(-1,dp(44));
            stripParams.topMargin=dp(10);
            card.addView(strip,stripParams);
            for(Leg l:loaded.legs){
                String type=effectiveType(l);
                LinearLayout row=horizontal();
                row.addView(dot(lineColor(type),5),new LinearLayout.LayoutParams(dp(10),dp(10)));
                TextView name=text(RouteData.display(type),14,TEXT,false);
                name.setPadding(dp(10),0,0,0);
                row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                row.addView(text(String.format(Locale.getDefault(),"%.1f км",length(l.path)/1000.0),13,MUTED,false));
                put(card,row,8);
            }
            if(!loaded.note.isEmpty())put(card,text(loaded.note,12,MUTED,false),12);
        }else{
            card.addView(display("Маршрут не выбран",20,TEXT));
            put(card,text("Постройте маршрут в Organic Maps, выберите вариант и нажмите кнопку GPS на карте.",13,MUTED,false),8);
        }
        put(main,card,18);
        if(routeLoaded&&loaded.legs.size()==1){
            put(main,text("Тип движения",14,TEXT,true),18);
            HorizontalScrollView scroller=new HorizontalScrollView(this);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout chips=horizontal();
            for(int i=0;i<TRANSPORT_NAMES.length;i++){
                final int index=i;
                TextView c=chip(TRANSPORT_NAMES[i],selectedTransport==i);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
                lp.rightMargin=dp(8);
                chips.addView(c,lp);
                c.setOnClickListener(v->{selectedTransport=index;draw();});
            }
            scroller.addView(chips);
            put(main,scroller,8);
        }else if(routeLoaded){
            put(main,text("Составной маршрут: скорость каждого участка берётся по его типу движения.",12,MUTED,false),14);
        }
        Button refresh=button("Обновить маршрут с карты",false);
        refresh.setOnClickListener(v->{Toast.makeText(this,"Вернитесь на карту, выберите маршрут и откройте GPS снова",Toast.LENGTH_LONG).show();finish();});
        put(main,refresh,14);
    }

    private String effectiveType(Leg l){
        if(loaded!=null&&loaded.legs.size()==1&&selectedTransport>0)return TRANSPORT_TYPES[selectedTransport];
        return l.type;
    }

    private View stat(String value,String label){
        LinearLayout box=vertical();
        box.addView(display(value,28,GHOST));
        box.addView(text(label,12,MUTED,false));
        return box;
    }

    private void drawSpeeds(){
        LinearLayout card=card();
        card.addView(display("Скорость, км/ч",18,TEXT));
        put(card,text("Диапазон для каждого типа движения. На каждом участке скорость выбирается случайно между «от» и «до».",12,MUTED,false),4);
        LinkedHashSet<String> types=new LinkedHashSet<>();
        for(String t:CORE_TYPES)types.add(t);
        if(loaded!=null)for(Leg l:loaded.legs)types.add(effectiveType(l));
        LinkedHashSet<String> used=new LinkedHashSet<>();
        if(loaded!=null)for(Leg l:loaded.legs)used.add(effectiveType(l));
        SharedPreferences prefs=getPreferences(0);
        for(String type:types){
            double[] def=SimulationEngine.defaultRange(type);
            LinearLayout row=horizontal();
            row.addView(dot(lineColor(type),5),new LinearLayout.LayoutParams(dp(10),dp(10)));
            TextView name=text(RouteData.display(type),14,TEXT,used.contains(type));
            if(!used.contains(type))name.setTextColor(MUTED);
            name.setPadding(dp(10),0,0,0);
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            EditText lo=input("от",prefs.getString("min_"+type,fmt(def[0])),true);
            EditText hi=input("до",prefs.getString("max_"+type,fmt(def[1])),true);
            lo.setGravity(Gravity.CENTER);
            hi.setGravity(Gravity.CENTER);
            row.addView(lo,new LinearLayout.LayoutParams(dp(64),dp(44)));
            TextView dash=text("–",16,MUTED,false);
            dash.setGravity(Gravity.CENTER);
            row.addView(dash,new LinearLayout.LayoutParams(dp(20),-2));
            row.addView(hi,new LinearLayout.LayoutParams(dp(64),dp(44)));
            put(card,row,10);
            speedFields.put(type,new EditText[]{lo,hi});
        }
        Button reset=button("Сбросить скорости",false);
        reset.setOnClickListener(v->{
            SharedPreferences.Editor editor=getPreferences(0).edit();
            for(String type:speedFields.keySet())editor.remove("min_"+type).remove("max_"+type);
            editor.apply();
            speedFields.clear();
            draw();
        });
        put(card,reset,14);
        put(main,card,18);
    }

    private void rememberSpeeds(){
        if(speedFields.isEmpty())return;
        SharedPreferences.Editor editor=getPreferences(0).edit();
        for(String type:speedFields.keySet()){
            EditText[] f=speedFields.get(type);
            editor.putString("min_"+type,f[0].getText().toString());
            editor.putString("max_"+type,f[1].getText().toString());
        }
        editor.apply();
    }

    private void readSpeeds(SimulationEngine.Config cfg)throws Exception{
        for(String type:speedFields.keySet()){
            EditText[] f=speedFields.get(type);
            double lo,hi;
            try{
                lo=Double.parseDouble(f[0].getText().toString().trim().replace(',','.'));
                hi=Double.parseDouble(f[1].getText().toString().trim().replace(',','.'));
            }catch(NumberFormatException ex){
                throw new Exception("Скорость «"+RouteData.display(type)+"»: введите числа в полях «от» и «до»");
            }
            if(!(lo>=1&&hi>=lo&&hi<=200))throw new Exception("Скорость «"+RouteData.display(type)+"»: «от» не меньше 1, «до» не меньше «от» и не больше 200 км/ч");
            cfg.speeds.put(type,new double[]{lo,hi});
        }
    }

    private void drawFixed(){
        LinearLayout card=card();
        card.addView(display("Одна точка",20,TEXT));
        put(card,text("Телефон сообщает выбранные координаты и остаётся на месте со скоростью 0 км/ч.",13,MUTED,false),6);
        latitude=input("Широта",getPreferences(0).getString("lat",""),true);
        longitude=input("Долгота",getPreferences(0).getString("lon",""),true);
        put(card,latitude,16);
        put(card,longitude,10);
        put(card,text("Координаты места можно скопировать из карточки места в Organic Maps.",12,MUTED,false),12);
        put(main,card,18);
    }

    private void drawCustom(){
        LinearLayout card=card();
        card.addView(display("Свой трек",20,TEXT));
        put(card,text("Импортируйте GPX или GeoJSON либо задайте точки по строкам. Между ручными точками движение идёт по прямой, а не по дорогам.",13,MUTED,false),6);
        Button importFile=button("Импорт GPX / GeoJSON",true);
        importFile.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_TRACK);});
        put(card,importFile,16);
        customText=new EditText(this);
        customText.setHint("Широта, долгота, тип участка\n51.5034, -0.1276, WALK\n51.5050, -0.1200, BUS\n51.5080, -0.1130, WALK");
        customText.setHintTextColor(MUTED);
        customText.setTextColor(TEXT);
        customText.setMinLines(5);
        customText.setGravity(Gravity.TOP);
        customText.setTextSize(14);
        customText.setPadding(dp(14),dp(14),dp(14),dp(14));
        customText.setBackground(box(PANEL_HI,14,EDGE,1));
        put(card,customText,12);
        Button apply=button("Сохранить точки",false);
        apply.setOnClickListener(v->{
            try{
                Plan plan=parseCustom(customText.getText().toString());
                RouteGhostStorage.save(this,plan);
                captureFailed=false;
                toast("Маршрут сохранён");
                mode=0;
                draw();
            }catch(Exception ex){toast(ex.getMessage());}
        });
        put(card,apply,10);
        put(main,card,18);
    }

    private Plan parseCustom(String source)throws Exception{
        String[] lines=source.trim().split("\\r?\\n");
        if(lines.length<2||lines.length>10000)throw new Exception("Укажите от 2 до 10000 точек");
        Plan plan=new Plan();
        plan.title="Свой маршрут";
        plan.source="Ручной ввод";
        Pt last=null;
        for(String line:lines){
            if(line.trim().isEmpty())continue;
            String[] parts=line.split(",");
            if(parts.length<2)throw new Exception("Строка должна содержать широту и долготу");
            Pt p=new Pt(Double.parseDouble(parts[0].trim()),Double.parseDouble(parts[1].trim()));
            String type=parts.length>2?parts[2].trim().toUpperCase(Locale.ROOT):WALK;
            if(!validType(type))throw new Exception("Неизвестный тип движения: "+type);
            if(last!=null){
                ArrayList<Pt> path=new ArrayList<>();
                path.add(last);
                path.add(p);
                double metres=metres(last,p);
                if(metres>100_000)throw new Exception("Слишком длинный разрыв между точками");
                int seconds=Math.max(1,(int)(metres/midSpeedMps(type)));
                plan.legs.add(new Leg(type,RouteData.display(type),path,seconds));
            }
            last=p;
        }
        if(plan.legs.isEmpty())throw new Exception("Нужно минимум 2 точки");
        return plan;
    }

    private boolean validType(String type){
        for(String t:TRANSPORT_TYPES)if(type.equals(t)&&!t.isEmpty())return true;
        return type.equals(MCC)||type.equals(MCD)||type.equals(FERRY)||type.equals(MINIBUS)||type.equals(OTHER);
    }

    private void drawControls(){
        LinearLayout card=card();
        card.addView(display("Симуляция",18,TEXT));
        pauses=check("Остановки и ожидания",true);
        traffic=check("Пробки по времени суток",true);
        noise=check("Небольшая GPS-погрешность",true);
        signal=check("Потеря GPS в метро",false);
        put(card,pauses,8);
        put(card,traffic,0);
        put(card,noise,0);
        put(card,signal,0);
        put(main,card,18);

        LinearLayout live=card();
        live.setBackground(box(PANEL,20,GHOST,1));
        LinearLayout speedRow=horizontal();
        speedText=display("0.0",44,GHOST);
        speedRow.addView(speedText);
        TextView unit=text("км/ч",14,MUTED,false);
        unit.setPadding(dp(8),dp(14),0,0);
        speedRow.addView(unit);
        live.addView(speedRow);
        statusText=text("",13,TEXT,false);
        put(live,statusText,2);
        put(main,live,14);

        Button start=button("Начать GPS",true);
        start.setOnClickListener(v->startSimulation());
        put(main,start,14);
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button pause=button("Пауза",false);
        Button resume=button("Продолжить",false);
        Button stop=button("Стоп",false);
        Button[] buttons={pause,resume,stop};
        for(Button b:buttons){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);
            p.rightMargin=dp(6);
            row.addView(b,p);
        }
        pause.setOnClickListener(v->command(LocationService.PAUSE));
        resume.setOnClickListener(v->command(LocationService.RESUME));
        stop.setOnClickListener(v->command(LocationService.STOP));
        put(main,row,8);
        put(main,text("В параметрах разработчика Android выберите RouteGhost в разделе «Приложение для фиктивных местоположений». Симуляция предназначена для тестирования.",12,MUTED,false),16);
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
                if(p.legs.size()==1&&selectedTransport>0){
                    p.legs.get(0).type=TRANSPORT_TYPES[selectedTransport];
                }
                SimulationEngine.Config cfg=new SimulationEngine.Config();
                cfg.pauses=pauses.isChecked();
                cfg.trafficModel=traffic.isChecked();
                cfg.gpsNoise=noise.isChecked();
                readSpeeds(cfg);
                rememberSpeeds();
                Timeline timeline=SimulationEngine.generate(p,cfg);
                RouteGhostStorage.saveTimeline(this,timeline);
                intent.putExtra("fixed",false).putExtra("auto_stop",false).putExtra("subway_loss",signal.isChecked());
            }
            intent.putExtra("noise",noise.isChecked());
            if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
            toast("Запуск GPS. Проверьте настройки Mock Location.");
        }catch(Exception ex){toast("Не удалось запустить: "+ex.getMessage());}
    }

    private void command(String action){
        startService(new Intent(this,LocationService.class).setAction(action));
        handler.postDelayed(this::updateStatus,200);
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void updateStatus(){
        if(statusText==null)return;
        statusText.setText(LocationService.status+(LocationService.running&&LocationService.paused?" · пауза":""));
        if(speedText!=null)speedText.setText(String.format(Locale.US,"%.1f",LocationService.running&&!LocationService.paused?LocationService.speed*3.6:0.0));
        if(strip!=null){
            long total=LocationService.total;
            strip.setProgress(LocationService.running&&!LocationService.fixed&&total>0?Math.min(1f,LocationService.elapsed/(float)total):-1f);
        }
    }
    private final Runnable refreshStatus=new Runnable(){@Override public void run(){updateStatus();handler.postDelayed(this,1000);}};
    @Override protected void onDestroy(){handler.removeCallbacks(refreshStatus);super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_TRACK||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new Exception("Файл недоступен");
            byte[] block=new byte[16*1024];
            int n;
            while((n=in.read(block))>0){out.write(block,0,n);if(out.size()>12_000_000)throw new Exception("Файл слишком большой");}
            String name="track.gpx";
            String path=data.getData().getLastPathSegment();
            if(path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".json"))name="track.json";
            else if(path!=null&&path.toLowerCase(Locale.ROOT).endsWith(".geojson"))name="track.geojson";
            Plan plan=TrackIO.read(out.toByteArray(),name);
            RouteGhostStorage.save(this,plan);
            captureFailed=false;
            toast("Маршрут импортирован: "+plan.legs.size()+" участков");
            mode=0;
            draw();
        }catch(Exception ex){toast("Ошибка файла: "+ex.getMessage());}
    }
}
