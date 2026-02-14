package easierMindustry.world.blocks.sandbox;

import mindustry.gen.Building;
import mindustry.world.blocks.sandbox.PowerVoid;

public class PowerSource extends mindustry.world.blocks.sandbox.PowerSource {
    public PowerSource(String name) {
        super(name);
    }

    public class PowerSourceBuild extends mindustry.world.blocks.sandbox.PowerSource.PowerSourceBuild {

        @Override
        public float getPowerProduction() {
            for (Building e : power.graph.consumers.items)
                if (e != null && e.block instanceof PowerVoid) return 0f;
            return enabled ? powerProduction : 0f;
        }
    }
}
