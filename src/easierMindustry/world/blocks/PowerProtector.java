package easierMindustry.world.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Env;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * PowerProtector - A block that protects the power network when power drops to 0,
 * locks power >= 512, exits protection after 5 minutes or after 30 seconds of
 * continuous power growth, records spent power, and enters recovery mode after exiting.
 */
public class PowerProtector extends PowerGenerator {
    public float protectionTime = 5 * 60 * 60f; // 5 minutes in ticks (60 ticks per second)
    public float exitGrowthTime = 30 * 60f; // 30 seconds in ticks for continuous growth
    public float recoveryRate = 0.01f; // 1% per second for recovery
    public float minProtectedPower = 512f; // Minimum protected power level

    /**
     * Constructor for PowerProtector
     * Sets up basic properties for the block
     */
    public PowerProtector(String name) {
        super(name);
        // Basic properties setup
        update = true;           // Needs updating
        solid = true;            // Is solid
        hasPower = true;         // Requires power module
        outputsPower = true;    // Doesn't output power
        size = 2;                // Size of the block
        health = 600;            // Health points
        envEnabled = Env.any;    // Effective in any environment
        configurable = false;    // Not configurable
        saveConfig = false;      // Don't save configuration
        displayFlow = false;     // Don't display flow
        drawArrow = false;       // Don't draw arrow
        consumePowerDynamic((entity) -> ((PowerProtectorBuild) entity).getRecoveryPerTick());
    }

    /**
     * Sets up statistics for the block
     */
    @Override
    public void setStats() {
        super.setStats();

        stats.add(Stat.powerUse, "Protects power network when below 0");
        stats.add(Stat.repairTime, protectionTime / (60 * 60), StatUnit.minutes); // Protection time in minutes
    }

    /**
     * Sets up status bars for the block
     */
    @Override
    public void setBars() {
        super.setBars();

        // Add spent power bar
        addBar("spent-power", entity -> new Bar(
                () -> Core.bundle.format("bar.spent-power", Strings.fixed(((PowerProtectorBuild) entity).totalSpentPower, 2)),
                () -> Pal.powerBar,
                () -> ((PowerProtectorBuild) entity).totalSpentPower > 0 ?
                        Math.min(1f, ((PowerProtectorBuild) entity).totalSpentPower) : 0f
        ));

        // Add protection status bar
        addBar("protection", entity -> new Bar(
                () -> ((PowerProtectorBuild) entity).isInProtectionMode() ? Core.bundle.get("block.easier-mindustry-power-protector.protection") :
                        (((PowerProtectorBuild) entity).isInRecoveryMode() ? Core.bundle.get("block.easier-mindustry-power-protector.recovery") :
                                Core.bundle.get("block.easier-mindustry-power-protector.normal")),
                () -> ((PowerProtectorBuild) entity).isInProtectionMode() ? Color.red :
                        (((PowerProtectorBuild) entity).isInRecoveryMode() ? Color.orange : Color.white),
                () -> 1f)
        );
    }

    /**
     * Internal building class for PowerProtector
     */
    public class PowerProtectorBuild extends GeneratorBuild {
        private boolean inProtectionMode = false;
        private boolean inRecoveryMode = false;
        private float protectionTimer = 0f;
        private float recoveryTimer = 0f;
        /**
         * Timer for continuous power growth
         */
        private float powerGrowthTimer = 0f;
        /**
         * Total power that has been spent/deducted
         */
        private float totalSpentPower = 0f;
//        private float currentSpentPower = 0f;// Current amount of spent power during recovery
        /**
         * Recovery period for power growth
         */
        private float recoveryPeriod = 0f; // How long the recovery period should last
        /**
         * Current power production during recovery
         */
        private float currentPowerProduction = 0f;
        private float recoveryPerSecond = 0f;

        /**
         * Updates the tile every frame
         */
        @Override
        public void updateTile() {
            if (!enabled) return;

            float powerStored = power.graph.getBatteryStored();
//            float powerCapacity = power.graph.getBatteryCapacity();

            // Check if we should enter protection mode (when power is 0 or negative)
            if (!inProtectionMode && !inRecoveryMode && powerStored == 0) {
                enterProtectionMode();
            }

            // Handle protection mode
            if (inProtectionMode) {
                handleProtectionMode(powerStored);
            } else if (inRecoveryMode) {
                handleRecoveryMode();
            }

            // Track power changes for exit condition
//            if (inProtectionMode && powerStored >= power.graph.getLastPowerStored()) {
//                powerGrowthTimer += Time.delta;
//            }
//            } else if (inProtectionMode) {
//                powerGrowthTimer = 0f; // Reset if power isn't growing
//            }
        }

        /**
         * Enters protection mode
         */
        private void enterProtectionMode() {
            inProtectionMode = true;
            protectionTimer = 0f;
            powerGrowthTimer = 0f;
            // Record current spent power as recovery baseline
//            currentSpentPower = totalSpentPower;
            Log.info("Power Protector entered protection mode.");
        }

        /**
         * Exits protection mode and enters recovery mode
         */
        private void exitProtectionMode() {
            inProtectionMode = false;
            protectionTimer = 0f;
            powerGrowthTimer = 0f;
            currentPowerProduction = 0f;
            // Enter recovery mode for the same duration as protection time
//            inRecoveryMode = true;
//            recoveryTimer = 0f;
//            recoveryPeriod = protectionTimer; // Same duration as a protection period

            enterRecoveryMode();

            Log.info("Power Protector exited protection mode, entering recovery mode.");
        }

        /**
         * Enters recovery mode
         */
        private void enterRecoveryMode() {
            inRecoveryMode = true;
            recoveryTimer = 0f;
            // The Recovery period is the same as the protection period
            recoveryPeriod = protectionTimer;
            recoveryPerSecond = totalSpentPower / (recoveryPeriod / 60f); // Convert ticks to seconds
            Log.info("Power Protector entered recovery mode for " + recoveryPeriod / (60 * 60) + " seconds.");
        }

        /**
         * Exits recovery mode
         */
        private void exitRecoveryMode() {
            inRecoveryMode = false;
            recoveryTimer = 0f;
            totalSpentPower = 0f;

            Log.info("Power Protector exited recovery mode.");
        }

        /**
         * Handles protection mode logic
         */
        private void handleProtectionMode(float powerStored) {
            protectionTimer += Time.delta;
            if (inProtectionMode && powerStored >= power.graph.getLastPowerStored()) {
                powerGrowthTimer += Time.delta;
            }
            if (powerStored < Math.min(minProtectedPower, power.graph.getBatteryCapacity())) {
                currentPowerProduction = minProtectedPower - powerStored;
                totalSpentPower += currentPowerProduction;
            } else {
                currentPowerProduction = 0;
            }
            // Exit conditions for protection mode:
            // 1. After 5 minutes have passed
            // 2. After 30 seconds of continuous power growth
            if (protectionTimer >= protectionTime || powerGrowthTimer >= exitGrowthTime) {
                exitProtectionMode();
            }
        }

        /**
         * Handles recovery mode logic
         */
        private void handleRecoveryMode() {
            recoveryTimer += Time.delta;

            // In recovery mode, consume spent power using equal principal method at 1% per second
            if (totalSpentPower > 0 && recoveryPeriod > 0) {
                // Calculate equal principal amount to consume per second
                float totalRecoveryPerTick = getRecoveryPerTick();

                // Reduce the current spent power by the recovery amount
                totalSpentPower -= totalRecoveryPerTick;

                // Ensure currentSpentPower doesn't go below 0
                if (totalSpentPower <= 0) {
                    totalSpentPower = 0;
                    exitRecoveryMode();
                }
            }

            // Exit recovery mode when time is up or all spent power is consumed
            if (recoveryTimer >= recoveryPeriod || totalSpentPower <= 0) {
                exitRecoveryMode();
            }
        }

        /**
         * Calculates the amount of power to recover per tick
         */
        public float getRecoveryPerTick() {
            float recoveryPerTick = recoveryPerSecond / 60; // Convert to per tick

            // Also add interest at 1% per second of remaining spent power
            float interestPerSecond = totalSpentPower * recoveryRate; // 1% per second
            float interestPerTick = interestPerSecond / 60;

            return recoveryPerTick + interestPerTick;
        }

        /**
         * Checks if the protector is in protection mode
         */
        public boolean isInProtectionMode() {
            return inProtectionMode;
        }

        /**
         * Checks if the protector is in recovery mode
         */
        public boolean isInRecoveryMode() {
            return inRecoveryMode;
        }

        @Override
        public float getPowerProduction() {
            // Return current power generation
            return currentPowerProduction;
        }

        /**
         * Provides sensor access to power network data
         */
        @Override
        public double sense(LAccess sensor) {
            if (sensor == LAccess.powerNetStored) return power.graph.getBatteryStored();
            if (sensor == LAccess.powerNetCapacity) return power.graph.getBatteryCapacity();
            return super.sense(sensor);
        }
    }
}