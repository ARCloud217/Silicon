package silicon.world.blocks.power;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Interval;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.blocks.sandbox.PowerVoid;
import mindustry.world.meta.Env;
import mindustry.world.meta.Stat;

import static silicon.Vars.powerChanged;
import static silicon.Vars.powerStored;

/**
 * RollGenerator - A dynamic power generator that produces power based on
 * current stored power and power change rates in the network
 * Power generation scales with network conditions and has adaptive limits
 */
public class RollGenerator extends PowerGenerator {
    /**
     * Percentage of stored power used for base power generation per second
     */
    public float powerStoredProductionPercentage = 0.01f;
    /**
     * Percentage of power change used for additional power generation
     */
    public float powerChangedProductionPercentage = 0.05f;
    /**
     * Speed of warmup animation transition
     */
    public float warmupSpeed = 0.1f;


    /**
     * Constructor for RollGenerator block
     * @param name The name identifier for this block
     */
    public RollGenerator(String name) {
        super(name);
        // Basic properties setup
        update = true;           // Needs updating
        solid = true;            // Is solid
        hasPower = true;         // Requires power module
        outputsPower = true;     // Outputs power
        size = 3;                // Size of the block
        health = 800;            // Health points
        envEnabled = Env.any;    // Effective in any environment
        configurable = false;    // Not configurable
        saveConfig = false;      // Don't save configuration
        displayFlow = false;     // Don't display flow
        drawArrow = false;       // Don't draw arrow
        consumePowerDynamic((entity) -> ((RollGeneratorBuild) entity).getPowerConsumptionPerTick()).optional(false, false);

    }

    /**
     * Sets up statistics for the block
     */
    @Override
    public void setStats() {
        super.setStats();
//        stats.add(Stat.basePowerGeneration, "动态变化，基于当前储存电量的1%/秒"); // Display special generation mechanism - actual value varies based on stored power
        stats.add(Stat.productionTime, "1s"); // Add special note
    }

    /**
     * Sets up status bars for the block
     */
    @Override
    public void setBars() {
        super.setBars();

        // Add power status bar
        addBar("power", (RollGeneratorBuild entity) -> new Bar(() ->
                Core.bundle.format("bar.power1", Strings.fixed((entity.currentPowerProduction * 60 * entity.timeScale()), 1)),
                () -> Pal.powerBar,
                () -> entity.currentPowerProduction / entity.maxPowerGeneration));
//        addBar("4",
//                (RollGeneratorBuild entity) ->
//                        new Bar(() -> String.valueOf(entity.efficiency), () -> Color.white, () -> 1f));
//        addBar("5",
//                (RollGeneratorBuild entity) ->
//                        new Bar(() -> String.valueOf(entity.shouldConsume()), () -> Color.white, () -> 1f));
        addBar("6",
                (RollGeneratorBuild entity) ->
                        new Bar(() -> String.valueOf(entity.currentPowerProduction), () -> Color.green, () -> 1f));
        addBar("7",
                (RollGeneratorBuild entity) ->
                        new Bar(() -> String.valueOf(entity.maxPowerGeneration), () -> Color.white, () -> 1f));
        addBar("8",
                (RollGeneratorBuild entity) ->
                        new Bar(() -> String.valueOf(powerStored.get(entity) * powerStoredProductionPercentage / 60 +
                                powerChanged.get(entity) * powerChangedProductionPercentage / 60), () -> Color.white, () -> 1f));
        addBar("9",
                (RollGeneratorBuild entity) ->
                        new Bar(() -> String.valueOf(powerChanged.get(entity)), () -> Color.white, () -> 1f));
//        addBar("10",
//                (RollGeneratorBuild entity) ->
//                        new Bar(() -> String.valueOf(nonOptionalConsumers[0].efficiency(entity)), () -> Color.white, () -> 1f));

    }

    /**
     * Building class for RollGenerator
     * Manages dynamic power generation based on network conditions
     */
    public class RollGeneratorBuild extends GeneratorBuild {
        /**
         * Interval timer for periodic updates
         */
        private final Interval interval = new Interval();
        /** Current power production rate */
        private float currentPowerProduction = 0f;
        /** Maximum allowed power generation */
        private float maxPowerGeneration = 0;
        /** Previous power production value for smooth transitions */
        private float lastCurrentPowerProduction = 0f;
//        private float timer = 0f;

        /**
         * Updates the tile each frame
         * Calculates dynamic power generation based on network conditions
         */
        @Override
        public void updateTile() {
            if (!enabled) return;
            int i = 0;
            if (power.graph.all.size > 0) {
                for (Building e : power.graph.all.items) {
                    if (e != null && e.block instanceof PowerVoid) {
                        return;
                    }
                    if (e != null && e.block instanceof RollGenerator) {
                        i++;
                    }
                }
            }
            if (Float.isNaN(currentPowerProduction)) {
                lastCurrentPowerProduction = 0f;
            } else {
                lastCurrentPowerProduction = currentPowerProduction;
            }
            if (Float.isNaN(maxPowerGeneration)) {
                maxPowerGeneration = 0f;
            }
            currentPowerProduction = 0f;

            float roll = powerStored.get(self()) * powerStoredProductionPercentage / 60 +
                    powerChanged.get(self()) * powerChangedProductionPercentage / 60;

            // Calculate power generation every second (based on 1% of current stored power)
            //timer += edelta(); // Use edelta() instead of Time.delta
            // Update interval in game ticks (1 second)
            //float UPDATE_INTERVAL = 60f;
            //if (timer >= UPDATE_INTERVAL) { // Trigger once per second
            //    timer = 0f;

            // Get current stored power in the power network

            if (powerChanged.get(self()) <= 0 & maxPowerGeneration <= roll) {
                maxPowerGeneration += Time.delta / 60f;
                interval.clear();
            } else if (powerChanged.get(self()) >= 0.01f * roll & powerChanged.get(self()) >= 0) {
                if (interval.get(60f)) {
                    if (maxPowerGeneration > 0) {
                        maxPowerGeneration -= (powerChanged.get(self()) - 0.01f * roll) / i * 0.5f;
                    } else if (maxPowerGeneration < 0) {
                        maxPowerGeneration = 0;
                    }
                    interval.clear();

                }
            }

            // Calculate new power generation: 1% per second = 1% / 60 per tick
            // Limit minimum power generation to avoid stopping
            currentPowerProduction = Mathf.lerp(lastCurrentPowerProduction, Math.min(roll, maxPowerGeneration), warmup());

            // Update efficiency
//            power.status = currentPowerProduction > 0 ? currentPowerProduction / (maxPowerGeneration / 60f) : 0f;
            //}
        }

        /**
         * Gets the current power production amount
         * @return Power production in power units per tick
         */
        @Override
        public float getPowerProduction() {
            // Return current power generation
            return (currentPowerProduction > 0) ? currentPowerProduction : 0f;
        }

        /**
         * Gets the power consumption per tick
         * @return Negative power consumption when producing power
         */
        public float getPowerConsumptionPerTick() {
            return (currentPowerProduction < 0) ? currentPowerProduction : 0f;
        }

        /**
         * Gets the save version for this building
         * @return The version number
         */
        @Override
        public byte version() {
            return 7;
        }

        /**
         * Gets the warmup progress for animations
         * @return Warmup progress from 0 to 1
         */
        @Override
        public float warmup() {
            return warmupSpeed;
        }

        /**
         * Draws the building and visual effects
         */
        @Override
        public void draw() {
            super.draw();

            // Draw generation effect
            if (enabled && currentPowerProduction > 0) {
                Draw.color(Color.valueOf("f8c266"));
                Lines.stroke(0.8f);
                Lines.circle(x, y, 3f + Mathf.absin(Time.time, 10f, 1f));
                Draw.reset();
            }
        }

        /**
         * Called when proximity changes
         */
        @Override
        public void onProximityUpdate() {
            power.status = 1;
            super.onProximityUpdate();
        }

        /**
         * Provides sensor access to power network data
         * @param sensor The sensor type to query
         * @return The requested sensor value
         */
        @Override
        public double sense(LAccess sensor) {
            if (sensor == LAccess.powerNetStored) return power.graph.getBatteryStored();
            if (sensor == LAccess.powerNetCapacity) return power.graph.getBatteryCapacity();
            return super.sense(sensor);
        }

        /**
         * Writes building data to save file
         * @param write The writer object
         */
        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(currentPowerProduction);
            write.f(maxPowerGeneration);
            write.f(lastCurrentPowerProduction);
        }

        /**
         * Reads building data from save file
         * @param read The reader object
         * @param revision The save revision
         */
        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            currentPowerProduction = read.f();
            maxPowerGeneration = read.f();
            lastCurrentPowerProduction = read.f();
        }
    }
}
