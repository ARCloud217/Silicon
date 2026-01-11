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
    public float powerProductionPercentage = 0.01f;

    public float maxPowerGeneration = 1000f;

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
        addBar("power", entity -> new Bar(
                () -> Core.bundle.format("bar.poweroutput", Strings.fixed(entity.getPowerProduction() * 60 * entity.timeScale(), 1)),
                () -> Pal.powerBar,
                () -> entity.power != null ? entity.power.status : 0f
        ));
    }

    public class CompoundInterestGeneratorBuild extends GeneratorBuild {
        private float currentPowerProduction = 0f;
        private float timer = 0f;

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

            // Calculate new power generation: 1% per second = 1% / 60 per tick
            // Limit minimum power generation to avoid stopping
            currentPowerProduction = Math.min(Math.max(powerStored * powerProductionPercentage / 60f, 0.01f / 60f), maxPowerGeneration / 60f);

            // Update efficiency
            power.status = currentPowerProduction > 0 ? currentPowerProduction / (maxPowerGeneration / 60f) : 0f;
            //}
        }

        @Override
        public float getPowerProduction() {
            // Return current power generation
            return currentPowerProduction;
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
