package silicon.world.blocks.distribution;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
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
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.BlockGroup;

import static mindustry.Vars.content;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class ItemTransferHub extends Block {
    public float connectionRange = 20f;
    public int maxConnections = 50;

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

        config(Integer.class, (ItemTransferHubBuild entity, Integer pos) -> {
            Building other = world.build(pos);
            if (other == null || !other.isValid() || other == entity) return;
            if (!linkValid(entity, other)) return;

            if (entity.links.contains(pos)) {
                entity.links.removeValue(pos);
                if (other instanceof ItemTransferHubBuild otherHub) {
                    otherHub.links.removeValue(entity.pos());
                }
                rebuildData(entity);
            } else {
                if (entity.links.size >= maxConnections) return;
                if (other instanceof ItemTransferHubBuild && isInSameNetwork(entity, other)) return;
                entity.links.addUnique(pos);
                if (other instanceof ItemTransferHubBuild otherHub) {
                    if (!otherHub.links.contains(entity.pos()) && otherHub.links.size < maxConnections) {
                        otherHub.links.addUnique(entity.pos());
                    }
                }
                rebuildData(entity);
            }
        });
    }

    /** Only connect to buildings that consume items or store items as primary function. */
    private static boolean shouldConnect(Building other) {
        if (other == null) return false;
        Block b = other.block;
        if (b instanceof CoreBlock) return true;
        if (b instanceof StorageBlock) return true;
        if (b instanceof GenericCrafter) return true;
        if (b instanceof Drill) return true;
        if (b instanceof ItemTurret) return true;
        return false;
    }

    public static boolean linkValid(Building tile, Building link) {
        if (tile == link || link == null) return false;
        if (tile.team != link.team) return false;
        if (!shouldConnect(link)) return false;
        float range = ((ItemTransferHub) tile.block).connectionRange * tilesize;
        float dist = Mathf.dst(tile.x, tile.y, link.x, link.y);
        return dist <= range;
    }

    /** BFS check whether two hubs are already in the same network. */
    private static boolean isInSameNetwork(ItemTransferHubBuild a, Building b) {
        if (!(b instanceof ItemTransferHubBuild hubB)) return false;
        Seq<ItemTransferHubBuild> visited = new Seq<>();
        Seq<ItemTransferHubBuild> queue = new Seq<>();
        queue.add(a);
        visited.add(a);
        for (int i = 0; i < queue.size; i++) {
            ItemTransferHubBuild current = queue.get(i);
            for (int j = 0; j < current.data.hubs.size; j++) {
                ItemTransferHubBuild neighbor = current.data.hubs.get(j);
                if (neighbor == hubB) return true;
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return false;
    }

    private static void rebuildData(ItemTransferHubBuild hub) {
        hub.data.clear();
        float range = ((ItemTransferHub) hub.block).connectionRange * tilesize;
        int rangeTiles = (int) ((ItemTransferHub) hub.block).connectionRange;

        for (int ix = hub.tile.x - rangeTiles; ix <= hub.tile.x + rangeTiles; ix++) {
            for (int iy = hub.tile.y - rangeTiles; iy <= hub.tile.y + rangeTiles; iy++) {
                Building b = world.build(ix, iy);
                if (b == null || !b.isValid() || b == hub) continue;
                if (b.team != hub.team) continue;
                float dist = Mathf.dst(hub.x, hub.y, b.x, b.y);
                if (dist > range) continue;

                if (b instanceof ItemTransferHubBuild otherHub) {
                    hub.data.add(otherHub);
                } else if (shouldConnect(b)) {
                    hub.data.add(b);
                }
            }
        }

        hub.links.each(pos -> {
            Building b = world.build(pos);
            if (b == null || !b.isValid() || b == hub) return;
            if (b instanceof ItemTransferHubBuild otherHub) {
                if (!hub.data.hubs.contains(otherHub)) hub.data.add(otherHub);
            } else if (shouldConnect(b)) {
                if (!hub.data.buildings.contains(b)) hub.data.add(b);
            }
        });
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

        float range = connectionRange * tilesize;
        float cx = tx * tilesize + offset;
        float cy = ty * tilesize + offset;

        Drawf.dashCircle(cx, cy, range, Pal.accent);

        for (int ix = tx - (int) connectionRange; ix <= tx + (int) connectionRange; ix++) {
            for (int iy = ty - (int) connectionRange; iy <= ty + (int) connectionRange; iy++) {
                Building b = world.build(ix, iy);
                if (b != null && b.team == mindustry.Vars.player.team()) {
                    if (b instanceof ItemTransferHubBuild || shouldConnect(b)) {
                        float dist = Mathf.dst(cx, cy, b.x, b.y);
                        if (dist <= range) {
                            Drawf.square(b.x, b.y, b.block.size * tilesize / 2f + 2f, Pal.place);
                        }
                    }
                }
            }
        }

        Draw.reset();
    }

    public class ItemTransferHubBuild extends Building {
        public ItemTransferHubNetwork network = new ItemTransferHubNetwork();
        public ItemTransferHubNetwork.HubData data;
        public IntSeq links = new IntSeq();
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
            float range = connectionRange * tilesize;
            int rangeTiles = (int) connectionRange;

            for (int ix = tile.x - rangeTiles; ix <= tile.x + rangeTiles; ix++) {
                for (int iy = tile.y - rangeTiles; iy <= tile.y + rangeTiles; iy++) {
                    Building b = world.build(ix, iy);
                    if (b == null || !b.isValid() || b == this) continue;
                    if (b.team != team) continue;
                    float dist = Mathf.dst(x, y, b.x, b.y);
                    if (dist > range) continue;

                    if (b instanceof ItemTransferHubBuild hub) {
                        if (!data.hubs.contains(hub)) {
                            data.add(hub);
                            hub.data.add(this);
                        }
                    } else if (shouldConnect(b)) {
                        if (!data.buildings.contains(b)) {
                            data.add(b);
                        }
                    }
                }
            }

            links.each(pos -> {
                Building b = world.build(pos);
                if (b == null || !b.isValid() || b == this) return;

                if (b instanceof ItemTransferHubBuild hub) {
                    if (!data.hubs.contains(hub)) {
                        data.add(hub);
                        hub.data.add(this);
                    }
                } else if (shouldConnect(b)) {
                    if (!data.buildings.contains(b)) {
                        data.add(b);
                    }
                }
            });

            data.buildings.removeAll(b -> !b.isValid());
            data.hubs.removeAll(h -> !h.isValid());
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
        public void draw() {
            super.draw();

            Lines.stroke(2f);
            links.each(pos -> {
                Building other = world.build(pos);
                if (other == null || !other.isValid()) return;

                if (other instanceof ItemTransferHubBuild) {
                    Draw.color(Color.blue);
                    Lines.line(x, y, other.x, other.y, false);
                } else {
                    Drawf.dashLine(Color.blue, x, y, other.x, other.y, 8);
                }
            });
            Draw.reset();
        }

        @Override
        public void drawSelect() {
            super.drawSelect();

            Drawf.dashCircle(x, y, connectionRange * tilesize, Pal.accent);

            for (Building b : data.buildings) {
                if (links.contains(b.pos())) continue;
                Drawf.square(b.x, b.y, b.block.size * tilesize / 2f + 1f, Pal.place);
            }

            for (int i = 0; i < links.size; i++) {
                Building b = world.build(links.get(i));
                if (b == null || !b.isValid()) continue;
                Drawf.square(b.x, b.y, b.block.size * tilesize / 2f + 2f, Pal.accent);
            }

            for (ItemTransferHubBuild hub : data.hubs) {
                Drawf.square(hub.x, hub.y, hub.block.size * tilesize / 2f + 2f, Color.blue);
            }

            Draw.reset();
        }

        @Override
        public void drawConfigure() {
            super.drawConfigure();

            Drawf.circles(x, y, block.size * tilesize / 2f + 1f + Mathf.absin(Time.time, 4f, 1f));

            Drawf.circles(x, y, connectionRange * tilesize);

            int rangeTiles = (int) connectionRange;
            for (int ix = tile.x - rangeTiles - 2; ix <= tile.x + rangeTiles + 2; ix++) {
                for (int iy = tile.y - rangeTiles - 2; iy <= tile.y + rangeTiles + 2; iy++) {
                    Building link = world.build(ix, iy);
                    if (link == this || link == null) continue;
                    boolean linked = links.contains(link.pos());
                    if (linked && linkValid(this, link)) {
                        Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.place);
                    } else if (!linked && linkValid(this, link)) {
                        Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.accent);
                    }
                }
            }

            Draw.reset();
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (linkValid(this, other)) {
                configure(other.pos());
                return false;
            }

            if (this == other) {
                if (links.size > 0) {
                    links.each(pos -> {
                        Building b = world.build(pos);
                        if (b instanceof ItemTransferHubBuild hub) {
                            hub.links.removeValue(pos);
                        }
                    });
                    links.clear();
                    rebuildData(this);
                } else {
                    int rangeTiles = (int) connectionRange;
                    for (int ix = tile.x - rangeTiles; ix <= tile.x + rangeTiles; ix++) {
                        for (int iy = tile.y - rangeTiles; iy <= tile.y + rangeTiles; iy++) {
                            Building b = world.build(ix, iy);
                            if (b != null && b != this && linkValid(this, b)
                                    && !links.contains(b.pos()) && links.size < maxConnections) {
                                configure(b.pos());
                            }
                        }
                    }
                }
                deselect();
                return false;
            }

            return true;
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            table.defaults().size(120f, 40f);
            table.button(Core.bundle.get("hubPull"), Styles.clearTogglet, () -> {
                network.enableDemandPull = !network.enableDemandPull;
                configure(new Object[]{0, network.enableDemandPull});
            }).checked(b -> network.enableDemandPull);
            table.row();
            table.button(Core.bundle.get("hubPush"), Styles.clearTogglet, () -> {
                network.enableSurplusPush = !network.enableSurplusPush;
                configure(new Object[]{1, network.enableSurplusPush});
            }).checked(b -> network.enableSurplusPush);
        }

        @Override
        public void configured(Unit unit, Object value) {
            if (value instanceof Object[] arr && arr.length >= 2) {
                if (arr[0] instanceof Integer mode && arr[1] instanceof Boolean state) {
                    if (mode == 0) network.enableDemandPull = state;
                    else if (mode == 1) network.enableSurplusPush = state;
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
            write.s(links.size);
            for (int i = 0; i < links.size; i++) {
                write.i(links.get(i));
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int netId = read.i();
            int ver = read.i();
            network.enableDemandPull = read.bool();
            network.enableSurplusPush = read.bool();
            short linkCount = read.s();
            links.clear();
            for (int i = 0; i < linkCount; i++) {
                int pos = read.i();
                links.add(pos);
            }
            rebuildData(this);
        }
    }
}
