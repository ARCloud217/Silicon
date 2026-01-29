package easierMindustry.world.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Strings;
import arc.util.Time;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Env;
import mindustry.world.meta.Stat;

public class RollGenerator extends PowerGenerator {
    public float powerStoredProductionPercentage = 0.01f;
    public float powerChangedProductionPercentage = 0.05f;
    public float maxPowerGeneration = 1f;

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

    @Override
    public void setStats() {
        super.setStats();
//        stats.add(Stat.basePowerGeneration, "动态变化，基于当前储存电量的1%/秒"); // Display special generation mechanism - actual value varies based on stored power
        stats.add(Stat.productionTime, "1s"); // Add special note
    }

    @Override
    public void setBars() {
        super.setBars();

        // Add power status bar
        addBar("power", (RollGeneratorBuild entity) -> new Bar(() ->
                Core.bundle.format("bar.power1", Strings.fixed((entity.currentPowerProduction * 60 * entity.timeScale()), 1)),
                () -> Pal.powerBar,
                () -> entity.productionEfficiency));
    }

    public class RollGeneratorBuild extends GeneratorBuild {
        private float currentPowerProduction = 0f;

        @Override
        public void updateTile() {
            if (!enabled) return;


            // Calculate power generation every second (based on 1% of current stored power)
            //timer += edelta(); // Use edelta() instead of Time.delta
            // Update interval in game ticks (1 second)
            //float UPDATE_INTERVAL = 60f;
            //if (timer >= UPDATE_INTERVAL) { // Trigger once per second
            //    timer = 0f;

            // Get current stored power in the power network
            float powerStored = power.graph.getBatteryStored();
            float powerChanged = power.graph.getLastScaledPowerIn() - power.graph.getLastScaledPowerOut();
            if (powerStored < power.graph.getBatteryCapacity()) {
                maxPowerGeneration += edelta();
            } else if (powerStored == power.graph.getBatteryCapacity() && maxPowerGeneration > 1f) {
                maxPowerGeneration /= 2f;
            }

            // Calculate new power generation: 1% per second = 1% / 60 per tick
            // Limit minimum power generation to avoid stopping
            currentPowerProduction = Math.min(Math.max(powerStored * powerStoredProductionPercentage / 60f +
                    powerChanged * powerChangedProductionPercentage / 60f, 0.01f / 60f), maxPowerGeneration / 60f);

            // Update efficiency
            power.status = currentPowerProduction > 0 ? currentPowerProduction / (maxPowerGeneration / 60f) : 0f;
            //}
        }

        @Override
        public float getPowerProduction() {
            // Return current power generation
            return (currentPowerProduction > 0) ? currentPowerProduction : 0f;
        }

        public float getPowerConsumptionPerTick() {
            return (currentPowerProduction < 0) ? currentPowerProduction : 0f;
        }

        @Override
        public byte version() {
            return super.version();
        }

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

        @Override
        public double sense(LAccess sensor) {
            if (sensor == LAccess.powerNetStored) return power.graph.getBatteryStored();
            if (sensor == LAccess.powerNetCapacity) return power.graph.getBatteryCapacity();
            return super.sense(sensor);
        }
    }
}
