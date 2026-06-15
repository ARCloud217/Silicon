package silicon;

import arc.assets.Loadable;
import arc.func.Floatf;
import arc.struct.ObjectFloatMap;
import mindustry.gen.Building;
import mindustry.type.Item;


public class Vars implements Loadable {
    public static final String name = "Silicon";
    public static final Floatf<Building> powerChanged = (entity) -> entity.power.graph.getLastScaledPowerIn() - entity.power.graph.getLastScaledPowerOut(); // Power balance change
    public static final Floatf<Building> powerStored = (entity) -> entity.power.graph.getBatteryStored();
    public static final Floatf<Building> powerCapacity = (entity) -> entity.power.graph.getTotalBatteryCapacity();
    public static final Floatf<Building> powerRemained = (entity) -> entity.power.graph.getBatteryCapacity();// Player -> Buildings
    public static final ObjectFloatMap<Item> costs = new ObjectFloatMap<>();
    public ObjectFloatMap<Item> emptyObjectFloatMap = new ObjectFloatMap<>();
//    public static final ObjectMap<Block, Seq<Building>> blockCount = new ObjectMap<>();


}
