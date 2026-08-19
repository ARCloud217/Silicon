package silicon.world.blocks.distribution;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BlockGroup;

import static mindustry.Vars.content;
import static mindustry.Vars.world;

public class ItemTransferHub extends Block {
    public float connectionRange = 20f;

    public ItemTransferHub(String name) {
        super(name);
        hasItems = false;
        hasPower = true;
        consumesPower = true;
        outputsPower = false;
        conductivePower = false;
        solid = true;
        update = true;
        size = 3;
        configurable = true;
        group = BlockGroup.transportation;
    }

    @Override
    public void setBars() {
        addBar("health", (ItemTransferHubBuild b) -> new Bar(
                () -> Core.bundle.format("stat.health"),
                () -> Pal.health,
                () -> b.healthf()
        ).blink(Color.white));
        addBar("silicon-hub-power", (ItemTransferHubBuild b) -> new Bar(
                () -> Core.bundle.format("bar.silicon-hub-power"),
                () -> Pal.powerBar,
                () -> b.power != null ? b.power.status : 0f
        ));
        addBar("silicon-hub-power-cost", (ItemTransferHubBuild b) -> new Bar(
                () -> Core.bundle.format("bar.silicon-hub-power-cost"),
                () -> Pal.accent,
                () -> Math.min(b.powerPerSecond / 100f, 1f)
        ));
    }

    @Override
    public void drawPlace(int tx, int ty, int rotation, boolean valid) {
        super.drawPlace(tx, ty, rotation, valid);

        float range = connectionRange * 8f;
        float cx = tx * 8f + offset;
        float cy = ty * 8f + offset;

        Drawf.dashCircle(cx, cy, range, Pal.accent);

        for (int ix = tx - (int) connectionRange; ix <= tx + (int) connectionRange; ix++) {
            for (int iy = ty - (int) connectionRange; iy <= ty + (int) connectionRange; iy++) {
                Building b = world.build(ix, iy);
                if (b != null && b.team == mindustry.Vars.player.team()) {
                    float dist = Mathf.dst(cx, cy, b.x, b.y);
                    if (dist <= range) {
                        Drawf.square(b.x, b.y, b.block.size * 8f / 2f + 2f, Pal.place);
                    }
                }
            }
        }

        Draw.reset();
    }

    public class ItemTransferHubBuild extends Building {
        public ItemTransferHubNetwork network = new ItemTransferHubNetwork();
        public ItemTransferHubNetwork.HubData data;
        public float powerConsumed = 0f;
        public float powerPerSecond = 0f;
        private float powerAccumulator = 0f;

        public ItemTransferHubBuild() {
            super();
            data = new ItemTransferHubNetwork.HubData(new Seq<>());
        }

        public void merge(ItemTransferHubBuild other) {
            network = network.merge(other.network);
            other.network = network;
        }

        public void addLink(ItemTransferHubBuild other) {
            merge(other);
        }

        public void addLink(Building other) {
            data.buildings.add(other);
        }

        public void addLinks(Building[] other) {
            for (Building b : other) {
                if (b instanceof ItemTransferHub.ItemTransferHubBuild hubBuild)
                    data.add(hubBuild);
                else
                    data.add(b);
            }
        }

        public void addLinks(Seq<Building> other) {
            addLinks(other.items);
        }

        public void removeLink(ItemTransferHubBuild other) {
            data.hubs.remove(other);
            other.data.hubs.remove(this);
            network.remove(other);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return false;
        }

        @Override
        public void onProximityUpdate() {
            super.onProximityUpdate();
            updateTopology();
        }

        @Override
        public void created() {
            super.created();
            updateTopology();
        }

        private void updateTopology() {
            boolean hubChanged = false;

            for (Building other : proximity) {
                if (other == null || !other.isValid()) continue;
                if (other instanceof ItemTransferHubBuild hub) {
                    if (!data.hubs.contains(hub)) {
                        data.add(hub);
                        hub.data.add(this);
                        hubChanged = true;
                    }
                } else {
                    if (!data.buildings.contains(other)) {
                        data.add(other);
                    }
                }
            }

            data.buildings.removeAll(b -> !b.isValid() || !proximity.contains(b));
            data.hubs.removeAll(h -> !h.isValid() || !proximity.contains(h));

            if (hubChanged) {
                network.version++;
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();
            if (power == null || power.status <= 0) return;
            if (!enabled) return;

            powerConsumed = 0f;

            if (timer(1, 30)) updateTopology();
            if (timer(2, 60)) {
                data.updateBefore();
                data.update();
            }

            if (timer(3, 60)) {
                powerPerSecond = powerAccumulator;
                powerAccumulator = 0f;
            }

            if (network.enableDemandPull) pullOnDemand();
            if (network.enableSurplusPush) pushSurplusToCore();

            powerAccumulator += powerConsumed;
        }

        private void pullOnDemand() {
            for (Building consumer : data.buildings) {
                if (consumer.items == null || !consumer.isValid()) continue;

                for (int i = 0; i < content.items().size; i++) {
                    Item item = content.item(i);
                    if (item == null) continue;
                    if (!consumer.acceptItem(this, item)) continue;
                    if (consumer.items.get(item) >= consumer.block.itemCapacity) continue;

                    Building supplier = findNearestSupplier(consumer, item);
                    if (supplier != null) {
                        directTransfer(supplier, consumer, item);
                    }
                }
            }
        }

        private void pushSurplusToCore() {
            for (Building producer : data.buildings) {
                if (producer.items == null || producer.items.empty() || !producer.isValid()) continue;
                if (producer instanceof CoreBlock.CoreBuild) continue;

                for (int i = 0; i < producer.items.length(); i++) {
                    Item item = content.item(i);
                    if (item == null || producer.items.get(item) == 0) continue;
                    if (producer.items.get(item) < producer.block.itemCapacity * 0.9f) continue;

                    CoreBlock.CoreBuild core = findNearestCore(producer, item);
                    if (core != null) {
                        directTransfer(producer, core, item);
                    }
                }
            }
        }

        private Building findNearestSupplier(Building consumer, Item item) {
            for (Building b : data.buildings) {
                if (b == consumer || !b.isValid()) continue;
                if (b.items != null && b.items.get(item) > 0) {
                    if (consumer.acceptItem(b, item)) return b;
                }
            }

            Seq<ItemTransferHubBuild> queue = new Seq<>();
            IntSeq dists = new IntSeq();
            Seq<ItemTransferHubBuild> visited = new Seq<>();

            for (ItemTransferHubBuild hub : data.hubs) {
                if (!visited.contains(hub)) {
                    visited.add(hub);
                    queue.add(hub);
                    dists.add(1);
                }
            }

            for (int idx = 0; idx < queue.size; idx++) {
                ItemTransferHubBuild hub = queue.get(idx);
                int dist = dists.get(idx);

                for (Building b : hub.data.buildings) {
                    if (b == consumer || !b.isValid()) continue;
                    if (b.items != null && b.items.get(item) > 0 && consumer.acceptItem(b, item)) {
                        return b;
                    }
                }

                for (ItemTransferHubBuild neighbor : hub.data.hubs) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                        dists.add(dist + 1);
                    }
                }
            }

            return null;
        }

        private CoreBlock.CoreBuild findNearestCore(Building producer, Item item) {
            for (Building b : data.buildings) {
                if (b instanceof CoreBlock.CoreBuild core && b.isValid()) {
                    if (core.acceptItem(producer, item)) return core;
                }
            }

            Seq<ItemTransferHubBuild> queue = new Seq<>();
            IntSeq dists = new IntSeq();
            Seq<ItemTransferHubBuild> visited = new Seq<>();

            for (ItemTransferHubBuild hub : data.hubs) {
                if (!visited.contains(hub)) {
                    visited.add(hub);
                    queue.add(hub);
                    dists.add(1);
                }
            }

            for (int idx = 0; idx < queue.size; idx++) {
                ItemTransferHubBuild hub = queue.get(idx);
                int dist = dists.get(idx);

                for (Building b : hub.data.buildings) {
                    if (b instanceof CoreBlock.CoreBuild core && b.isValid()) {
                        if (core.acceptItem(producer, item)) return core;
                    }
                }

                for (ItemTransferHubBuild neighbor : hub.data.hubs) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                        dists.add(dist + 1);
                    }
                }
            }

            return null;
        }

        private boolean directTransfer(Building supplier, Building consumer, Item item) {
            if (power == null || power.status <= 0) return false;
            if (!consumer.acceptItem(supplier, item)) return false;

            consumer.handleItem(supplier, item);
            supplier.items.remove(item, 1);
            powerConsumed += 10f;
            return true;
        }

        @Override
        public void drawSelect() {
            super.drawSelect();

            Lines.stroke(2f);
            for (ItemTransferHubBuild hub : data.hubs) {
                Draw.color(Color.blue);
                Lines.line(x, y, hub.x, hub.y);
            }

            Draw.color(Color.blue, 0.5f);
            Lines.stroke(1f);
            for (Building b : data.buildings) {
                Drawf.dashLine(Color.blue, x, y, b.x, b.y, 8);
            }

            Draw.reset();
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            table.button("Pull", Styles.clearTogglet, () -> configure(new Object[]{0}))
                    .checked(b -> network.enableDemandPull);
            table.button("Push", Styles.clearTogglet, () -> configure(new Object[]{1}))
                    .checked(b -> network.enableSurplusPush);
        }

        @Override
        public void configured(Unit unit, Object value) {
            if (value instanceof Object[] arr && arr.length > 0) {
                if (arr[0] instanceof Integer i) {
                    if (i == 0) network.enableDemandPull = !network.enableDemandPull;
                    else if (i == 1) network.enableSurplusPush = !network.enableSurplusPush;
                }
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(network.id);
            write.i(network.version);
            write.bool(network.enableDemandPull);
            write.bool(network.enableSurplusPush);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int netId = read.i();
            int ver = read.i();
            network.enableDemandPull = read.bool();
            network.enableSurplusPush = read.bool();
        }
    }
}
