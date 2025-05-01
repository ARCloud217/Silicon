package easierMindustry.content.block;

import easierMindustry.world.blocks.EasierMindustryGeneratorPump;
import easierMindustry.world.blocks.EasierMindustryJunction;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

public class EasierMindustryBlocks {
    public static Block powerGenerationPump, dualPurposeJunction;

    public static void load() {
        powerGenerationPump = new EasierMindustryGeneratorPump("power-generation-pump") {{
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
        dualPurposeJunction = new EasierMindustryJunction("dual-purpose-junction") {{
            requirements(Category.liquid, BuildVisibility.shown,
                    ItemStack.with(Items.graphite, 2, Items.metaglass, 4, Items.copper, 1));
        }};
    }
}
