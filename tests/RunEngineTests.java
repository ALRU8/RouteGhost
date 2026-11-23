import app.organicmaps.routeghost.RouteData;
import app.organicmaps.routeghost.SimulationEngine;
import java.util.ArrayList;
import static app.organicmaps.routeghost.RouteData.*;

public class RunEngineTests {
  private static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);}
  private static long timeAt(Timeline t,Pt p){for(Keyframe f:t.frames)if(metres(f.pos,p)<0.5)return f.atMillis;throw new AssertionError("point not on timeline");}
  public static void main(String[] args){
    Plan walking=new Plan();walking.seed=54321;
    ArrayList<Pt> coords=new ArrayList<>();
    for(int i=0;i<50;i++)coords.add(new Pt(52.52+i*0.0001,13.40+i*0.00005));
    walking.legs.add(new Leg(WALK,"Walking",coords,800));
    SimulationEngine.Config cfg=new SimulationEngine.Config();
    Timeline timeline=SimulationEngine.generate(walking,cfg);
    check(timeline.frames.size()>20,"too few samples");
    check(timeline.duration()>0,"duration");
    Pt first=timeline.frames.get(0).pos,last=timeline.frames.get(timeline.frames.size()-1).pos;
    check(metres(first,coords.get(0))<1,"start drift");
    check(metres(last,coords.get(coords.size()-1))<1,"finish drift");
    long previous=-1;
    for(Keyframe f:timeline.frames){check(f.atMillis>previous,"nonmonotonic time");previous=f.atMillis;}
    check(metres(SimulationEngine.sample(timeline,timeline.duration()+1000,false,12).pos,last)<0.1,"did not stop");
    Timeline again=SimulationEngine.generate(walking,cfg);
    check(timeline.duration()==again.duration(),"not deterministic");
    Plan broken=new Plan();broken.legs.add(new Leg(WALK,"1",coords.subList(0,5),20));
    broken.legs.add(new Leg(WALK,"2",coords.subList(20,30),30));
    boolean rejected=false;
    try{SimulationEngine.generate(broken,cfg);}catch(IllegalArgumentException e){rejected=true;}
    check(rejected,"teleport jump allowed");
    Plan bus=new Plan();bus.seed=4;bus.legs.add(new Leg(BUS,"Bus",coords,180));
    Timeline busTimeline=SimulationEngine.generate(bus,cfg);
    check(busTimeline.duration()>0&&busTimeline.frames.size()>3,"transit failed");
    Plan mixed=new Plan();mixed.seed=9;
    ArrayList<Pt> part1=new ArrayList<>(),part2=new ArrayList<>(),part3=new ArrayList<>();
    for(int i=0;i<=40;i++)part1.add(new Pt(52.52+i*0.0003,13.40));
    for(int i=0;i<=40;i++)part2.add(new Pt(52.52+(40+i)*0.0003,13.40));
    for(int i=0;i<=40;i++)part3.add(new Pt(52.52+(80+i)*0.0003,13.40));
    mixed.legs.add(new Leg(BUS,"Bus",part1,1));
    mixed.legs.add(new Leg(WALK,"Walk",part2,1));
    mixed.legs.add(new Leg(BUS,"Bus",part3,1));
    SimulationEngine.Config plain=new SimulationEngine.Config();
    plain.pauses=false;plain.trafficModel=false;
    Timeline mixedTimeline=SimulationEngine.generate(mixed,plain);
    double busSpeed=metres(part1.get(0),part1.get(40))/ (timeAt(mixedTimeline,part1.get(40))/1000.0);
    double walkSpeed=metres(part2.get(0),part2.get(40))/ ((timeAt(mixedTimeline,part2.get(40))-timeAt(mixedTimeline,part2.get(0)))/1000.0);
    check(busSpeed*3.6>=14&&busSpeed*3.6<=31,"bus speed out of range: "+busSpeed*3.6);
    check(walkSpeed*3.6>=3.9&&walkSpeed*3.6<=6.0,"walk speed out of range: "+walkSpeed*3.6);
    SimulationEngine.Config fast=new SimulationEngine.Config();
    fast.pauses=false;fast.trafficModel=false;
    fast.speeds.put(WALK,new double[]{9,9});
    Timeline fastTimeline=SimulationEngine.generate(mixed,fast);
    double fastWalk=metres(part2.get(0),part2.get(40))/((timeAt(fastTimeline,part2.get(40))-timeAt(fastTimeline,part2.get(0)))/1000.0);
    check(Math.abs(fastWalk*3.6-9)<0.5,"custom walk speed ignored: "+fastWalk*3.6);
    System.out.println("PASS: geometry, timing, deterministic delays, gap guard, public transport, per-mode speed ranges");
  }
}
