import app.organicmaps.routeghost.RouteData;
import app.organicmaps.routeghost.SimulationEngine;
import java.util.ArrayList;
import static app.organicmaps.routeghost.RouteData.*;

public class RunEngineTests {
  private static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);}
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
    System.out.println("PASS: geometry, timing, deterministic delays, gap guard, public transport");
  }
}
