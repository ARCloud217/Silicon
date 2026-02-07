package easierMindustry.content.block;

import easierMindustry.world.blocks.GeneratorPump;
import easierMindustry.world.blocks.Junction;
import easierMindustry.world.blocks.PowerProtector;
import easierMindustry.world.blocks.RollGenerator;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.sandbox.PowerSource;
import mindustry.world.meta.BuildVisibility;

import static mindustry.type.ItemStack.with;

public class Blocks {
    public static Block powerGenerationPump, dualPurposeJunction,
            rollGenerator, powerProtector, powerSource;

    public static void load() {
        powerGenerationPump = new GeneratorPump("power-generation-pump") {{
            hasItems = false;
            liquidPressure = 1f;
            pumpAmount = 0.22f;
            liquidCapacity = 90f;
            canPumpLiquids.add(Liquids.water);
            powerConsumption = 43f / 60;
            consumeLiquid(Liquids.water, 12.5f / 60).boost();
            powerProduction = 345f / 60;
            size = 3;
            destructible = true;
            requirements(Category.power, BuildVisibility.shown,
                    ItemStack.with(Items.copper, 60, Items.lead, 30, Items.metaglass, 15, Items.graphite, 40,
                            Items.titanium, 45, Items.thorium, 6, Items.silicon, 40));
        }};
        dualPurposeJunction = new Junction("dual-purpose-junction") {{
            requirements(Category.liquid, BuildVisibility.shown,
                    ItemStack.with(Items.graphite, 2, Items.metaglass, 4, Items.copper, 1));
        }};

        // Compound interest generator - generates power based on 1% of existing stored power
        rollGenerator = new RollGenerator("roll-generator") {{
            requirements(Category.power, BuildVisibility.shown,
                    ItemStack.with(Items.copper, 40, Items.lead, 24, Items.graphite, 20,
                            Items.silicon, 16, Items.thorium, 16, Items.plastanium, 10));
            size = 1;
//            health = 800;
            powerStoredProductionPercentage = 0.001f;
            powerChangedProductionPercentage = 0.01f;
        }};

        // Power protector - protects power network when below 0 and recovers spent power
        powerProtector = new PowerProtector("power-protector") {{
            requirements(Category.power, BuildVisibility.shown,
                    ItemStack.with(Items.copper, 150, Items.lead, 100, Items.graphite, 80,
                            Items.silicon, 70, Items.thorium, 50, Items.plastanium, 40, Items.phaseFabric, 20));
            size = 2;
            health = 600;
        }};
        powerSource = new PowerSource("power-source") {{
            requirements(Category.power, BuildVisibility.sandboxOnly, with());
            alwaysUnlocked = true;
            size = 1;
            health = 600;
            powerProduction = Float.MAX_VALUE;
        }};
    }
}