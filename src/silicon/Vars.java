package silicon;

import arc.assets.Loadable;
import arc.func.Floatf;
import arc.struct.Queue;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.gen.Player;

import java.util.HashMap;


public class Vars implements Loadable {
    public static final Floatf<Building> powerChanged = (entity) -> entity.power.graph.getLastScaledPowerIn() - entity.power.graph.getLastScaledPowerOut(); // Power balance change
    public static final Floatf<Building> powerStored = (entity) -> entity.power.graph.getBatteryStored();
    public static final Floatf<Building> powerCapacity = (entity) -> entity.power.graph.getTotalBatteryCapacity();
    public static final Floatf<Building> powerRemained = (entity) -> entity.power.graph.getBatteryCapacity();
    public static HashMap<Player, Queue<BuildPlan>> allPlans = new HashMap<>(); // Player -> Buildings


}
