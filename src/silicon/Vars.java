package silicon;

import arc.assets.Loadable;
import arc.func.Floatf;
import mindustry.gen.Building;


public class Vars implements Loadable {
    public static final Floatf<Building> powerChanged = (entity) -> entity.power.graph.getLastScaledPowerIn() - entity.power.graph.getLastScaledPowerOut(); // Power balance change
    public static final Floatf<Building> powerStored = (entity) -> entity.power.graph.getBatteryStored();
    public static final Floatf<Building> powerCapacity = (entity) -> entity.power.graph.getTotalBatteryCapacity();
    public static final Floatf<Building> powerRemained = (entity) -> entity.power.graph.getBatteryCapacity();// Player -> Buildings


}
