package silicon;

import arc.assets.Loadable;
import arc.func.Floatf;
import arc.struct.ObjectFloatMap;
import arc.struct.Seq;
import mindustry.gen.Building;
import mindustry.type.Item;


public class Vars implements Loadable {
    public static final String name = "Silicon";
    public static final Floatf<Building> powerChanged = (entity) -> entity.power.graph.getLastScaledPowerIn() - entity.power.graph.getLastScaledPowerOut(); // Power balance change
    public static final Floatf<Building> powerStored = (entity) -> entity.power.graph.getBatteryStored();
    public static final Floatf<Building> powerCapacity = (entity) -> entity.power.graph.getTotalBatteryCapacity();
    public static final Floatf<Building> powerRemained = (entity) -> entity.power.graph.getBatteryCapacity();// Player -> Buildings
    public static final ObjectFloatMap<Item> costs = new ObjectFloatMap<>();
    public static volatile Pause pause = new Pause("", true);
    public ObjectFloatMap<Item> emptyObjectFloatMap = new ObjectFloatMap<>();

    public static int pauseMode = 0;
    public static Seq<String> pauseWhitelist = new Seq<>();

    public static class Pause {
        String time;
        boolean complete;

        Pause(String time, boolean complete) {
            this.time = time;
            this.complete = complete;
        }

        Pause(String time) {
            this(time, false);
        }
    }

}
