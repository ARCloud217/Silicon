package easierMindustry;

import arc.func.Floatf;
import mindustry.gen.Building;

public class Vars {
    public static final Floatf<Building> powerChanged = (entity) -> entity.power.graph.getPowerBalance(); // Power balance change
    public static final Floatf<Building> powerStored = (entity) -> entity.power.graph.getBatteryStored();
    public static final Floatf<Building> powerCapacity = (entity) -> entity.power.graph.getTotalBatteryCapacity();


}
