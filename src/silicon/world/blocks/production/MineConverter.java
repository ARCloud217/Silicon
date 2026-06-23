package silicon.world.blocks.production;

import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.EnumSet;
import arc.util.Log;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.blocks.production.Drill;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.Stat;
import mindustry.world.meta.Stats;
import silicon.util.SiliconLog;
import silicon.world.blocks.FrameBlock;
import silicon.world.meta.StatValues;

import java.util.Objects;
import java.util.TreeMap;

import static mindustry.Vars.*;
import static mindustry.content.Blocks.blastDrill;
import static silicon.Vars.costs;

public class MineConverter extends FrameBlock {
    public float craftTime = 60;
    public float consumeTime = 60;
    public float consumptionMultiples = 0.1f;
    TreeMap<Float, Item> scaled = new TreeMap<>((o1, o2) -> {
        if (Objects.equals(o1, o2)) return 0;
        return o1 > o2 ? 1 : -1;
    });

    public MineConverter(String name) {
        super(name);
        configurable = true;
        update = true;
        solid = true;
        hasItems = true;
        ambientSound = Sounds.loopMachine;
        sync = true;
        ambientSoundVolume = 0.03f;
        flags = EnumSet.of(BlockFlag.factory);
        drawArrow = false;
        saveConfig = true;


        config(Item.class, (MineConverterBuild b, Item item) -> {
            b.craft = item;
            SiliconLog.info(item.name);
        });
        configClear((MineConverterBuild b) -> b.craft = null);
    }

    @Override
    public void setBars() {
        addBar("consume", (MineConverterBuild b) -> new Bar(
                () -> Strings.fixed(b.consumeProgress / consumeTime, 2),
                () -> Pal.powerBar,
                () -> (b.consumeProgress / consumeTime) > Mathf.FLOAT_ROUNDING_ERROR ? b.consumeProgress / consumeTime : 1f)
        );
        addBar("craft1", (MineConverterBuild b) -> new Bar(
                () -> b.craft != null ? Strings.fixed(b.craftValue / (costs.get(b.craft, 0) * (1 + consumptionMultiples)), 2)
                        + "/" + Strings.fixed(b.mineValue / (costs.get(b.craft, 0) * (1 + consumptionMultiples)), 2)
                        : b.consume != null ? Strings.fixed(b.mineValue / (costs.get(b.consume, 0) * (1 + consumptionMultiples)), 2) : "-",
                () -> Pal.powerBar,
                () -> b.craft != null ? b.craftValue / (costs.get(b.craft, 0) * (1 + consumptionMultiples)) : 1f)
        );
    }

    public void setStats() {
        //reset stats
        stats = new Stats();
        super.setStats();
//        stats.add(Stat.basePowerGeneration, "动态变化，基于当前储存电量的1%/秒"); // Display special generation mechanism - actual value varies based on stored power
        stats.add(Stat.productionTime, "1s"); // Add special note
        stats.add(silicon.world.meta.Stat.itemsScaled, StatValues.itemsScaled(false, scaled));
//        {
//            Log.info(scaled);
//        }
//        stats.add(Stat.consumeTime, "1s");
    }

    public void countWorldCosts() {
        costs.clear();
        scaled.clear();
//            if (!costs.containsKey(world))costs.put(world, emptyMap);
        world.tiles.eachTile(tile -> {
            if (tile.drop() == null) return;
            costs.increment(tile.drop(), 0, 1);
        });
//            Log.info(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " Counting costs" + costs);
        costs.each((o) -> {
            costs.put(o.key, 1e4f / o.value * ((Drill) blastDrill).getDrillTime(o.key));
        });
        for (Item i : costs.keys()) {
            itemFilter[i.id] = true;
        }
//            Log.info(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " Counting costs" + costs);

        float max = 0;
        for (float i : costs.values().toSeq().toArray()) {
            if (i > max) max = i;
        }
        float finalMax = max;
        costs.each((i) -> scaled.put(finalMax / i.value, i.key));
        SiliconLog.info("Recount the number of minerals");
    }

    public class MineConverterBuild extends FrameBuild {
        public float mineValue = 0;
        public float consumeProgress = 0;
        public float craftValue = 0;
        public float warmup;
        public Item craft = null, consume = null;
        public float lastChange;

        @Override
        public void updateTile() {
            if (!enabled) return;

            if (lastChange != world.tileChanges) {
                lastChange = world.tileChanges;
                countWorldCosts();
            }
//            test();
//            Log.info(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " MineConverterBuild update");
            if (costs.size == 0) countWorldCosts();
            block.setStats();
            {
                if ((consumeProgress >= consumeTime || consume == null)) {
                    consumeProgress = 0;

                    if (consume == null || items.get(consume) == 0) {
                        consume = null;
                        for (int i = 0; i < items.length(); i++) {
                            if ((consume == null || items.get(i) > items.get(consume)) && content.item(i) != null && content.item(i) != craft && items.get(i) != 0)
                                consume = content.item(i);
                        }
//                        items.each((i, a) -> {
//                            if ((consume == null || a > items.get(consume)) && i != craft && i != consume && items.get(i) != 0)
//                                consume = i;
//                        });
                    }
                    if (consume != null && items.get(consume) > 0) {
                        items.remove(consume, 1);
//                        Log.info(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " MineConverterBuild consume");
                    }
                }
                if (consume != null) {
                    consumeProgress += edelta();
                    float change = costs.get(consume, 0) * edelta() / consumeTime;
                    mineValue += change;
                }
            }
            {
                if (craft == null) return;
                float c = costs.get(craft, 0) * (1 + consumptionMultiples);
                if (craftValue >= c && items.get(craft) < itemCapacity) {
                    craftValue -= c;
                    items.add(craft, 1);
                } else if (items.get(craft) == itemCapacity) {
                    dump(craft);
                    return;
                }
                float del = Math.min(mineValue, c / craftTime * edelta());
                mineValue -= del;
                craftValue += del;
            }
            dump(craft);
        }

//        @Override
//        public void onProximityUpdate() {
//            countCosts();
//            super.onProximityUpdate();
//        }

        @Override
        public void buildConfiguration(Table table) {
            ItemSelection.buildTable(MineConverter.this, table, costs.keys().toSeq(),
                    () -> craft, this::configure, selectionRows, selectionColumns);
//            ItemSelection.buildTable(MineConverter.this, table, content.items(), () -> craft, this::configure, selectionRows, selectionColumns);
//            Log.info(costs);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return craft != item && !(source instanceof MineConverterBuild && source != self()) && super.acceptItem(source, item);
        }


        public boolean shouldConsume() {
            return consume != null || craft != null;
        }

        @Override
        public Item config() {
            return craft;
        }

        @Override
        public byte version() {
            return 2;
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            drawItemSelection(craft);
        }


        /**
         * Writes building data to save a file
         *
         * @param write The writer object
         */
        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(mineValue);
            write.f(craftValue);
            write.f(consumeProgress);
            write.f(warmup);
            write.s(craft == null ? -1 : craft.id);
            write.s(consume == null ? -1 : consume.id);
        }

        /**
         * Reads building data from a save file
         *
         * @param read     The reader object
         * @param revision The save revision
         */
        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            mineValue = read.f();
            craftValue = read.f();
            consumeProgress = read.f();
            warmup = read.f();
            craft = read.s() >= 0 ? content.items().get(read.s()) : null;
            consume = read.s() >= 0 ? content.items().get(read.s()) : null;
        }

        private void test() {
            Log.info(acceptStack(Items.lead, 100, player.unit()));
            Log.info(player.unit().team() == this.team);
            Log.info(getMaximumAccepted(Items.lead));
            Log.info(getMaximumAccepted(Items.graphite));

        }
    }
}
