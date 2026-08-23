package silicon.world.blocks.distribution;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.game.Team;
import mindustry.core.Renderer;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import silicon.world.blocks.production.MineConverter;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.BlockGroup;

import mindustry.world.Tile;
import arc.math.geom.Intersector;
import arc.util.Tmp;

import static mindustry.Vars.content;
import static mindustry.Vars.player;
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
        conductivePower = true;
        consumePowerDynamic(entity -> ((ItemTransferHubBuild) entity).powerConsumed);
        solid = true;
        update = true;
        size = 3;
        timers = 4;
        configurable = true;
        group = BlockGroup.transportation;

        config(Integer.class, (ItemTransferHubBuild entity, Integer pos) -> {
            Building other = world.build(pos);
            // 存在性 + 自身 + 范围 二次校验
            if (other == null || !other.isValid() || other == entity) return;
            if (!linkValid(entity, other)) return;

            if (entity.links.contains(pos)) {
                entity.links.removeValue(pos);
                if (other instanceof ItemTransferHubBuild otherHub) {
                    otherHub.links.removeValue(entity.pos());
                    rebuildData(otherHub);
                }
                rebuildData(entity);
            } else {
                if (entity.links.size >= maxConnections) return;
                if (!linkValid(entity, other)) return;
                entity.links.addUnique(pos);
                if (other instanceof ItemTransferHubBuild otherHub) {
                    if (!otherHub.links.contains(entity.pos()) && otherHub.links.size < maxConnections) {
                        otherHub.links.addUnique(entity.pos());
                    }
                    rebuildData(otherHub);
                }
                rebuildData(entity);
            }
        });
    }

    private static boolean shouldConnect(Building other) {
        if (other == null) return false;
        Block b = other.block;
        if (b instanceof CoreBlock) return true;
        if (b instanceof StorageBlock) return true;
        if (b instanceof GenericCrafter) return true;
        if (b instanceof MineConverter) return true;
        if (b instanceof Drill) return true;
        if (b instanceof ItemTurret) return true;
        if (b instanceof ItemTransferHub) return true;
        return false;
    }

    public static boolean linkValid(Building tile, Building link) {
        if (tile == link || link == null) return false;
        if (!(tile.block instanceof ItemTransferHub)) return false;
        if (tile.team != link.team) return false;
        if (!shouldConnect(link)) return false;
        float range = ((ItemTransferHub) tile.block).connectionRange * tilesize;
        float dist = Mathf.dst(tile.x, tile.y, link.x, link.y);
        return dist <= range;
    }

    private static void rebuildData(ItemTransferHubBuild hub) {
        hub.data.clear();
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
                () -> Core.bundle.format("bar.silicon-hub-power-cost", Strings.fixed(b.powerPerSecond, 1)),
                () -> Pal.accent,
                () -> Math.min(b.powerPerSecond / 100f, 1f)
        ));
        addBar("silicon-hub-connections", (ItemTransferHubBuild b) -> new Bar(
                () -> Core.bundle.format("bar.silicon-hub-connections", b.links.size, maxConnections),
                () -> Pal.items,
                () -> (float) b.links.size / maxConnections
        ));
        addBar("silicon-hub-transfer-rate", (ItemTransferHubBuild b) -> new Bar(
                () -> Core.bundle.format("bar.silicon-hub-transfer-rate", b.transferCountPerSecond),
                () -> Pal.accent,
                () -> Math.min(b.transferCountPerSecond / 50f, 1f)
        ));
    }

    /**
     * 查找范围内可连接的建筑（电力节点式）。
     */
    protected void getPotentialLinks(Tile tile, Team team, arc.func.Cons<Building> cons) {

        float range = connectionRange * tilesize;
        float wx = tile.worldx() + offset;
        float wy = tile.worldy() + offset;

        var tree = team.data().buildingTree;
        if (tree == null) {
            return;
        }

        tree.intersect(wx - range, wy - range, range * 2, range * 2, b -> {

            if (b == null || b.tile == tile) {
                return;
            }

            if (b.team != team) {
                return;
            }

            if (!overlaps(wx, wy, b.tile, b.block, range)) {
                return;
            }

            if (!shouldConnect(b)) {
                return;
            }

            if (b instanceof ItemTransferHubBuild hub && hub.links.size >= maxConnections) {
                return;
            }

            cons.get(b);
        });
    }

    protected boolean overlaps(float srcx, float srcy, Tile other, Block otherBlock, float range) {
        return Intersector.overlaps(Tmp.cr1.set(srcx, srcy, range),
            Tmp.r1.setCentered(other.worldx() + otherBlock.offset, other.worldy() + otherBlock.offset,
                otherBlock.size * tilesize, otherBlock.size * tilesize));
    }

    @Override
    public void drawPlace(int tx, int ty, int rotation, boolean valid) {
        Tile tile = world.tile(tx, ty);
        if (tile == null) return;
        super.drawPlace(tx, ty, rotation, valid);

        float range = connectionRange * tilesize;
        float cx = tx * tilesize + offset;
        float cy = ty * tilesize + offset;

        Lines.stroke(1f);
        Draw.color(Pal.placing);
        Drawf.circles(cx, cy, range);

        getPotentialLinks(tile, player.team(), other -> {
            // 放置预览：与正常连接线一致——淡绿色细实线（不使用激光拉伸）
            float angle = Angles.angle(cx, cy, other.x, other.y);
            float len1 = size * tilesize / 2f;
            float len2 = other.block.size * tilesize / 2f;
            float x1 = cx + Mathf.cosDeg(angle) * len1;
            float y1 = cy + Mathf.sinDeg(angle) * len1;
            float x2 = other.x - Mathf.cosDeg(angle) * len2;
            float y2 = other.y - Mathf.sinDeg(angle) * len2;
            Draw.color(Pal.powerLight, Renderer.laserOpacity);
            Lines.stroke(1f);
            Lines.line(x1, y1, x2, y2, false);
            Drawf.square(other.x, other.y, other.block.size * tilesize / 2f + 2f, Pal.place);
        });

        Draw.reset();
    }

    @Override
    public void changePlacementPath(arc.struct.Seq<arc.math.geom.Point2> points, int rotation) {
        // chain planning like PowerNode: auto-connect via Placement.calculateNodes
        mindustry.input.Placement.calculateNodes(points, this, rotation,
            (point, other) -> {
                Tile a = world.tile(point.x, point.y);
                Tile b = world.tile(other.x, other.y);
                if (a == null || b == null) return false;
                float range = connectionRange * tilesize;
                return Intersector.overlaps(Tmp.cr1.set(a.worldx() + offset, a.worldy() + offset, range),
                    Tmp.r1.setCentered(b.worldx() + offset, b.worldy() + offset, size * tilesize, size * tilesize));
            });
    }

    public class ItemTransferHubBuild extends Building {
        public ItemTransferHubNetwork network = new ItemTransferHubNetwork();
        public ItemTransferHubNetwork.HubData data;
        public IntSeq links = new IntSeq();
        public float powerConsumed = 0f;
        // 延迟计费：跨枢 charge 分摊到下一帧，避免同帧执行顺序导致的清零覆盖
        public float powerConsumedNext = 0f;
        public float powerPerSecond = 0f;
        private float powerAccumulator = 0f;
        private int transferCount = 0;
        private int transferCountPerSecond = 0;

        private final Seq<ItemTransferHubBuild> bfsQueue = new Seq<>();
        private final IntSeq bfsDists = new IntSeq();
        private final IntSet bfsVisited = new IntSet();

        public ItemTransferHubBuild() {
            super();
            data = new ItemTransferHubNetwork.HubData(new Seq<>());
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

        @Override
        public void placed() {
            if (mindustry.Vars.net.client() || links.size > 0) {
                super.placed();
                return;
            }
            // PowerNode-style: scan via buildingTree + overlap test, then configure via network
            ItemTransferHub hubBlock = (ItemTransferHub) block;
            hubBlock.getPotentialLinks(tile, team, other -> {
                if (other == null || !other.isValid()) return;
                if (!links.contains(other.pos()) && links.size < hubBlock.maxConnections && linkValid(this, other)) {
                    configure(other.pos());
                }
            });
            super.placed();
        }

        // ── 建筑拓扑（Building Topology）──────────────────────
        // 职责：本中枢的 links → data.hubs/buildings 本地视图重建与陈旧链剔除。
        private void updateTopology() {
            // 变量路由：每帧重建并即时剔除失效链
            IntSeq stale = new IntSeq();
            links.each(pos -> {
                Building b = world.build(pos);
                if (b == null || !b.isValid() || b == this || !linkValid(this, b)) {
                    stale.add(pos);
                }
            });
            stale.each(pos -> {
                links.removeValue(pos);
                // also clean reverse link
                Building other = world.build(pos);
                if (other instanceof ItemTransferHubBuild hub) {
                    hub.links.removeValue(this.pos());
                    rebuildData(hub);
                }
            });
            data.clear();
            links.each(pos -> {
                Building b = world.build(pos);
                if (b == null || !b.isValid() || b == this) return;
                if (b instanceof ItemTransferHubBuild hub) {
                    if (!data.hubs.contains(hub)) data.add(hub);
                } else if (shouldConnect(b)) {
                    if (!data.buildings.contains(b)) data.add(b);
                }
            });
        }

        // ── 建筑级更新（Building Update）──────────────────────
        // 职责：本中枢直连的工厂拉取 / 仓储溢出推送 + 本枢 power/transfer 统计。
        // 网络级（ItemTransferHubNetwork）只提供 enableDemandPull/SurplusPush 总开关与寻址辅助。
        // 电力统计：consumePowerDynamic 拉 powerConsumed 瞬时值，秒级取 powerAccumulator 积分求均。
        @Override
        public void updateTile() {

            super.updateTile();

            if (!enabled) {
                return;
            }

            // 本帧发起的转移数清零；powerConsumed 为本帧瞬时耗电（由 chargePath 累加）
            powerConsumed = 0f;
            transferCount = 0;

            // 无电力或禁用：不调度，秒级统计归零（避免 powerPerSecond 滞留）
            if (power == null || power.status <= 0) {

                powerAccumulator = 0f;

                if (timer(3, 60)) {
                    powerPerSecond = 0f;
                    powerAccumulator = 0f;
                    transferCountPerSecond = 0;
                }

                return;
            }

            // 调度：拉取优先于推送（同产品链路）
            boolean pulled = false;

            if (network.enableDemandPull) {
                pulled = pullOnDemand();
            }

            boolean hasDemand = hasPendingDemand();

            if (network.enableSurplusPush && !pulled && !hasDemand) {
                pushSurplusToCore();
            }

            // 积分：powerConsumed 是本帧瞬时功耗（10 × 经过本枢的件数/倍率），
            // 按帧累加后每秒取均作为 powerPerSecond 供 bar 显示。
            // 注意：chargePath 可能对远端 hub 的 powerConsumed 累加，需归入发起枢的 accumulator 统一口径
            // （发起枢的 powerConsumed 已含直连/路径首跳，远端 hub 的由其自身 updateTile 累加）
            powerAccumulator += powerConsumed * Time.delta;

            if (timer(3, 60)) {
                powerPerSecond = powerAccumulator;
                powerAccumulator = 0f;
                transferCountPerSecond = transferCount;
            }
        }

        /**
         * 工厂判定：需喂料的消费者（合成/转化/炮台/矿机）。
         * 矿机虽产出为主，但补润滑剂/水等时也需 acceptItem，故归入工厂。
         */
        private boolean isFactory(Building b) {
            return b instanceof GenericCrafter.GenericCrafterBuild
                || b instanceof MineConverter.MineConverterBuild
                || b instanceof Drill.DrillBuild
                || b instanceof ItemTurret.ItemTurretBuild;
        }

        /**
         * 产出源判定：可被拉取的供源 —— 矿机产出 + 工厂产出。
         */
        private boolean isProducer(Building b) {
            return b instanceof Drill.DrillBuild
                || b instanceof GenericCrafter.GenericCrafterBuild
                || b instanceof MineConverter.MineConverterBuild;
        }

        /**
         * 推送源判定：矿机/工厂溢出优先推核心（其次仓储由拉取补货，不主动推）。
         */
        private boolean isPushProducer(Building b) {
            return isProducer(b);
        }

        /**
         * 拉取：工厂按需补料 + 仓储按需补货（均通过最近供源）。
         * 同类型多工厂均衡：按缺口比例排序，最缺的先补；每物品每帧仅尝试一次，避免单厂吸干。
         */
        private boolean pullOnDemand() {

            boolean any = false;

            // 收集待补消费者，按缺口比例降序（最缺的先补）
            arc.struct.Seq<Building> consumers = new arc.struct.Seq<>();
            for (Building b : data.buildings) {
                if (b.items == null || !b.isValid()) {
                    continue;
                }
                boolean isStorage = b instanceof StorageBlock.StorageBuild
                    && !(b instanceof CoreBlock.CoreBuild);
                boolean isFactoryC = isFactory(b);
                if (!isFactoryC && !isStorage) {
                    continue;
                }
                // 至少有一种物品待补才入队
                boolean needs = false;
                for (int i = 0; i < content.items().size; i++) {
                    Item it = content.item(i);
                    if (it == null || it.id >= b.items.length()) {
                        continue;
                    }
                    if (isFactoryC) {
                        if (b.items.get(it) < b.getMaximumAccepted(it)) {
                            needs = true;
                            break;
                        }
                    } else if (isStorage) {
                        if (b.items.get(it) < b.block.itemCapacity * 0.9f && b.acceptItem(null, it)) {
                            needs = true;
                            break;
                        }
                    }
                }
                if (needs) {
                    consumers.add(b);
                }
            }

            // 缺口比 = 1 - 当前/上限，越大越缺；仓储按 0.9*capacity 归一
            consumers.sort((a, b) -> {
                float ra = deficitRatio(a);
                float rb = deficitRatio(b);
                return Float.compare(rb, ra);
            });

            for (Building consumer : consumers) {

                if (consumer.items == null || !consumer.isValid()) {
                    continue;
                }

                boolean isStorageConsumer = consumer instanceof StorageBlock.StorageBuild
                    && !(consumer instanceof CoreBlock.CoreBuild);
                boolean isFactoryConsumer = isFactory(consumer);

                // 炮台：按弹药伤害从高到低优选
                arc.struct.Seq<Item> candidates;
                boolean isTurret = consumer instanceof ItemTurret.ItemTurretBuild;
                if (isTurret) {
                    candidates = new arc.struct.Seq<>();
                    ItemTurret turret = (ItemTurret) consumer.block;
                    turret.ammoTypes.each((it, bt) -> {
                        if (it != null && bt != null && it.id < consumer.items.length()
                            && consumer.items.get(it) < consumer.getMaximumAccepted(it)) {
                            candidates.add(it);
                        }
                    });
                    candidates.sort((a, b) -> Float.compare(
                        ((ItemTurret) consumer.block).ammoTypes.get(b).damage,
                        ((ItemTurret) consumer.block).ammoTypes.get(a).damage));
                } else {
                    candidates = null;
                }

        // 候选物品序列：炮台按伤害降序；工厂/仓储按各自缺口比例降序（多源料均衡的关键）
        arc.struct.Seq<Item> ordered;
        if (isTurret && candidates != null) {
            ordered = candidates;
        } else {
            ordered = new arc.struct.Seq<>();
            for (int i = 0; i < content.items().size; i++) {
                Item it = content.item(i);
                if (it == null || it.id >= consumer.items.length()) {
                    continue;
                }
                int cap = isStorageConsumer
                    ? (int) (consumer.block.itemCapacity * 0.9f)
                    : consumer.getMaximumAccepted(it);
                if (cap <= 0) {
                    continue;
                }
                if (consumer.items.get(it) < cap) {
                    ordered.add(it);
                }
            }
            final Building fc = consumer;
            final boolean fStorage = isStorageConsumer;
            ordered.sort((a, b) -> Float.compare(
                itemDeficitRatio(fc, b, fStorage),
                itemDeficitRatio(fc, a, fStorage)));
        }

        // 多源料工厂：同一帧可连续补多种输入（不提前 break），保证双料同时到位
        for (int ii = 0; ii < ordered.size; ii++) {

            Item item = ordered.get(ii);
            if (item == null) {
                continue;
            }

            if (item.id >= consumer.items.length()) {
                continue;
            }

            if (isFactoryConsumer) {
                if (consumer.items.get(item) >= consumer.getMaximumAccepted(item)) {
                    continue;
                }
            } else if (isStorageConsumer) {
                if (consumer.items.get(item) >= consumer.block.itemCapacity * 0.9f) {
                    continue;
                }
                if (!consumer.acceptItem(null, item)) {
                    continue;
                }
            }

            Building supplier = findNearestSupplier(consumer, item);

            if (supplier == null) {
                continue;
            }

            if (power == null || power.status <= 0) {
                return any;
            }

            if (directTransfer(supplier, consumer, item)) {
                any = true;
            }
            }
        }

        return any;
    }

    /** 单物品缺口比例：1 - 当前/上限，越大越缺。 */
    private float itemDeficitRatio(Building b, Item it, boolean storage) {
        int cap = storage
            ? (int) (b.block.itemCapacity * 0.9f)
            : b.getMaximumAccepted(it);
        if (cap <= 0) {
            return 0f;
        }
        return 1f - (float) b.items.get(it) / cap;
    }

        private float deficitRatio(Building b) {
            float maxDef = 0f;
            for (int i = 0; i < content.items().size; i++) {
                Item it = content.item(i);
                if (it == null || it.id >= b.items.length()) {
                    continue;
                }
                int cap;
                if (b instanceof StorageBlock.StorageBuild && !(b instanceof CoreBlock.CoreBuild)) {
                    cap = (int) (b.block.itemCapacity * 0.9f);
                } else {
                    cap = b.getMaximumAccepted(it);
                }
                if (cap <= 0) {
                    continue;
                }
                float ratio = 1f - (float) b.items.get(it) / cap;
                if (ratio > maxDef) {
                    maxDef = ratio;
                }
            }
            return maxDef;
        }

        /**
         * 是否有待补需求（工厂缺料 或 仓储 <90%）。
         * 有待补需求时，即使本帧未拉到，也不应把货推回核心。
         */
        private boolean hasPendingDemand() {

            for (Building consumer : data.buildings) {

                if (consumer.items == null || !consumer.isValid()) {
                    continue;
                }

                boolean isStorageConsumer = consumer instanceof StorageBlock.StorageBuild
                    && !(consumer instanceof CoreBlock.CoreBuild);
                boolean isFactoryConsumer = isFactory(consumer);

                if (!isFactoryConsumer && !isStorageConsumer) {
                    continue;
                }

                for (int i = 0; i < content.items().size; i++) {

                    Item item = content.item(i);
                    if (item == null) {
                        continue;
                    }

                    if (item.id >= consumer.items.length()) {
                        continue;
                    }

                    if (isFactoryConsumer) {
                        if (consumer.items.get(item) >= consumer.getMaximumAccepted(item)) {
                            continue;
                        }
                    } else if (isStorageConsumer) {
                        if (consumer.items.get(item) >= consumer.block.itemCapacity * 0.9f) {
                            continue;
                        }
                        if (!consumer.acceptItem(null, item)) {
                            continue;
                        }
                    }

                    return true;
                }
            }

            return false;
        }

        /**
         * 推送：仓储/矿机/工厂 溢出 -> 核心；核心满时不推。
         * - 仓储：>=90% 容量算溢出
         * - 矿机/工厂：任一输出满即堵线需要排空
         */
        private void pushSurplusToCore() {

            for (Building producer : data.buildings) {

                if (producer.items == null || producer.items.empty() || !producer.isValid()) {
                    continue;
                }

                boolean isStorage = producer instanceof StorageBlock.StorageBuild
                    && !(producer instanceof CoreBlock.CoreBuild);
                boolean isProducer = isPushProducer(producer);

                if (!isStorage && !isProducer) {
                    continue;
                }

                // 工厂/矿机：仅当任一输出满时才视为堵线需要排空；仓储用 90% 阈值在下方按物判断
                if (isProducer) {
                    boolean blocked = false;
                    for (int k = 0; k < producer.items.length(); k++) {
                        Item ck = content.item(k);
                        if (ck == null) {
                            continue;
                        }
                        if (producer.items.get(ck) >= producer.block.itemCapacity) {
                            blocked = true;
                            break;
                        }
                    }
                    if (!blocked) {
                        continue;
                    }
                }

                for (int i = 0; i < producer.items.length(); i++) {

                    Item item = content.item(i);

                    if (item == null || producer.items.get(item) == 0) {
                        continue;
                    }

                    if (isStorage) {
                        if (producer.items.get(item) < producer.block.itemCapacity * 0.9f) {
                            continue;
                        }
                    }

                    if (power == null || power.status <= 0) {
                        return;
                    }

                    CoreBlock.CoreBuild core = findNearestCore(producer, item);
                    if (core == null) {
                        continue;
                    }
                    // 核心满时不推：按物判断已满则跳过
                    if (core.items != null && item.id < core.items.length()
                        && core.items.get(item) >= core.block.itemCapacity) {
                        continue;
                    }
                    if (!core.acceptItem(producer, item)) {
                        continue;
                    }

                    directTransfer(producer, core, item);
                }
            }
        }

        private void bfsInit() {

            bfsQueue.clear();
            bfsDists.clear();
            bfsVisited.clear();
            bfsVisited.add(id);
        }

        private Building findNearestSupplier(Building consumer, Item item) {
            // Route-variable: dynamically evaluate distance each call; prefer local, then BFS nearest
            Building best = null;
            int bestDist = Integer.MAX_VALUE;

            for (Building b : data.buildings) {
                if (b == consumer || !b.isValid() || b.items == null || b.items.get(item) <= 0) continue;
                if (!consumer.acceptItem(b, item)) continue;
                // local buildings are distance 1 (direct hub scope) — immediate candidate
                if (bestDist > 1) {
                    best = b;
                    bestDist = 1;
                }
            }
            if (bestDist == 1) return best;

            bfsInit();
            for (ItemTransferHubBuild hub : data.hubs) {
                if (bfsVisited.add(hub.id)) {
                    bfsQueue.add(hub);
                    bfsDists.add(1);
                }
            }

            for (int idx = 0; idx < bfsQueue.size; idx++) {
                ItemTransferHubBuild hub = bfsQueue.get(idx);
                int d = bfsDists.get(idx) + 1; // d = hub distance + 1 for its buildings
                for (Building b : hub.data.buildings) {
                    if (b == consumer || !b.isValid()) continue;
                    if (b.items == null || b.items.get(item) <= 0) continue;
                    if (!consumer.acceptItem(b, item)) continue;
                    if (d < bestDist) {
                        best = b;
                        bestDist = d;
                    }
                }
                if (best != null && bestDist <= d) {
                    // already found nearer in this layer — still need to finish layer for tie-break, but early exit ok
                }
                for (ItemTransferHubBuild neighbor : hub.data.hubs) {
                    if (bfsVisited.add(neighbor.id)) {
                        bfsQueue.add(neighbor);
                        bfsDists.add(bfsDists.get(idx) + 1);
                    }
                }
            }
            // variable route: if nearest supplier became invalid/empty during scan, caller will retry next tick
            return best;
        }

        private CoreBlock.CoreBuild findNearestCore(Building producer, Item item) {
            // Route-variable: same BFS nearest logic for cores
            CoreBlock.CoreBuild best = null;
            int bestDist = Integer.MAX_VALUE;

            for (Building b : data.buildings) {
                if (b instanceof CoreBlock.CoreBuild core && b.isValid() && core.acceptItem(producer, item)) {
                    best = core;
                    bestDist = 1;
                    break;
                }
            }
            if (bestDist == 1) return best;

            bfsInit();
            for (ItemTransferHubBuild hub : data.hubs) {
                if (bfsVisited.add(hub.id)) {
                    bfsQueue.add(hub);
                    bfsDists.add(1);
                }
            }

            for (int idx = 0; idx < bfsQueue.size; idx++) {
                ItemTransferHubBuild hub = bfsQueue.get(idx);
                int d = bfsDists.get(idx) + 1;
                for (Building b : hub.data.buildings) {
                    if (b instanceof CoreBlock.CoreBuild core && b.isValid() && core.acceptItem(producer, item)) {
                        if (d < bestDist) {
                            best = core;
                            bestDist = d;
                        }
                    }
                }
                for (ItemTransferHubBuild neighbor : hub.data.hubs) {
                    if (bfsVisited.add(neighbor.id)) {
                        bfsQueue.add(neighbor);
                        bfsDists.add(bfsDists.get(idx) + 1);
                    }
                }
            }
            return best;
        }

        private boolean directTransfer(Building supplier, Building consumer, Item item) {

            if (power == null || power.status <= 0) {
                return false;
            }

            if (supplier.items == null || supplier.items.get(item) <= 0) {
                return false;
            }

            if (!consumer.acceptItem(supplier, item)) {
                return false;
            }

            // 动态拓扑：中途供源可能已被拆除
            if (!supplier.isValid()) {
                return false;
            }

            consumer.handleItem(supplier, item);

            if (supplier.items != null) {
                supplier.items.remove(item, 1);
            }

            // 经由计费：按 BFS 最短路上的每个中枢各收 10
            chargePath(supplier, consumer);

            transferCount++;

            return true;
        }

        /**
         * 经由计费：每经一枢 +10 瞬时，秒级由 updateTile 的 powerAccumulator 求和。
         * 远端枢改为写入 powerConsumedNext（延迟一帧），避免被其帧首清零覆盖。
         */
        private void chargePath(Building supplier, Building consumer) {
            ItemTransferHubBuild srcHub = findOwnerHub(supplier);
            ItemTransferHubBuild dstHub = findOwnerHub(consumer);
            // 直连同枢：仅本枢
            if (srcHub == null || dstHub == null || srcHub == dstHub) {
                powerConsumed += 10f;
                return;
            }
            Seq<ItemTransferHubBuild> path = bfsPath(srcHub, dstHub);
            if (path == null || path.size == 0) {
                powerConsumed += 10f;
                return;
            }
            IntSet charged = new IntSet();
            for (ItemTransferHubBuild h : path) {
                if (!charged.add(h.id)) continue;
                if (h == this) {
                    h.powerConsumed += 10f;
                } else {
                    h.powerConsumedNext += 10f;
                }
            }
            if (charged.add(this.id)) {
                powerConsumed += 10f;
            }
        }

        private ItemTransferHubBuild findOwnerHub(Building b) {
            if (b instanceof ItemTransferHubBuild) return (ItemTransferHubBuild) b;
            if (data.buildings.contains(b)) return this;
            for (ItemTransferHubBuild hub : data.hubs) {
                if (hub.data.buildings.contains(b)) return hub;
            }
            // variable route: building may be on a hub beyond direct neighbors — BFS search whole network
            bfsInit();
            for (ItemTransferHubBuild h : data.hubs) {
                if (bfsVisited.add(h.id)) {
                    bfsQueue.add(h);
                    bfsDists.add(1);
                }
            }
            for (int i = 0; i < bfsQueue.size; i++) {
                ItemTransferHubBuild cur = bfsQueue.get(i);
                if (cur.data.buildings.contains(b)) return cur;
                for (ItemTransferHubBuild nb : cur.data.hubs) {
                    if (bfsVisited.add(nb.id)) {
                        bfsQueue.add(nb);
                        bfsDists.add(bfsDists.get(i) + 1);
                    }
                }
            }
            return null;
        }

        private Seq<ItemTransferHubBuild> bfsPath(ItemTransferHubBuild src, ItemTransferHubBuild dst) {
            if (src == dst) {
                Seq<ItemTransferHubBuild> s = new Seq<>();
                s.add(src);
                return s;
            }
            bfsInit();
            Seq<ItemTransferHubBuild> parentHub = new Seq<>();
            IntSeq parentIdx = new IntSeq();
            // reuse bfsQueue/bfsVisited for hubs, track parent index
            bfsQueue.clear();
            bfsVisited.clear();
            bfsVisited.add(src.id);
            bfsQueue.add(src);
            parentHub.add((ItemTransferHubBuild) null);
            parentIdx.add(-1);
            for (int i = 0; i < bfsQueue.size; i++) {
                ItemTransferHubBuild cur = bfsQueue.get(i);
                if (cur == dst) {
                    Seq<ItemTransferHubBuild> path = new Seq<>();
                    int at = i;
                    while (at >= 0) {
                        path.add(bfsQueue.get(at));
                        at = parentIdx.get(at);
                    }
                    path.reverse();
                    return path;
                }
                for (ItemTransferHubBuild nb : cur.data.hubs) {
                    if (bfsVisited.add(nb.id)) {
                        bfsQueue.add(nb);
                        parentHub.add(cur);
                        parentIdx.add(i);
                    }
                }
            }
            return null;
        }

        @Override
        public void draw() {
            super.draw();

            if (Mathf.zero(Renderer.laserOpacity) || isPayload() || team == Team.derelict) return;

            Draw.z(Layer.power);

            Lines.stroke(2f);
            links.each(pos -> {
                Building other = world.build(pos);
                if (other == null || !other.isValid()) return;
                if (!linkValid(this, other)) return;

                if (other instanceof ItemTransferHubBuild && other.id >= id) return;

                float angle = Angles.angle(x, y, other.x, other.y);
                float cos = Mathf.cosDeg(angle);
                float sin = Mathf.sinDeg(angle);

                float len1 = block.size * tilesize / 2f;
                float len2 = other.block.size * tilesize / 2f;

                float x1 = x + cos * len1;
                float y1 = y + sin * len1;
                float x2 = other.x - cos * len2;
                float y2 = other.y - sin * len2;

                // 模仿电力线风格：淡绿色细实线（不拉伸虚线，改为填充）
                Draw.color(Pal.powerLight, Renderer.laserOpacity);
                Lines.stroke(1f);

                if (other instanceof ItemTransferHubBuild) {
                    Lines.line(x1, y1, x2, y2, false);
                } else {
                    Lines.line(x1, y1, x2, y2, false);
                }
            });
            Draw.reset();
        }

        @Override
        public void drawSelect() {
            super.drawSelect();

            Drawf.dashCircle(x, y, connectionRange * tilesize, Pal.accent);

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
            // PowerNode-style: single tap on valid target toggles link
            if (linkValid(this, other)) {
                configure(other.pos());
                return false;
            }
            // double-tap self (PowerNode: linkValid branch above already handles single tap, this == other is double)
            if (this == other) {
                ItemTransferHub hubBlock = (ItemTransferHub) block;
                if (links.size > 0) {
                    // clear all — use configure(new Point2[0]) pattern via clearing links
                    links.each(pos -> {
                        Building b = world.build(pos);
                        if (b instanceof ItemTransferHubBuild hub) {
                            hub.links.removeValue(this.pos());
                            rebuildData(hub);
                        }
                    });
                    links.clear();
                    rebuildData(this);
                } else {
                    // auto-connect all potential links like PowerNode.getPotentialLinks
                    hubBlock.getPotentialLinks(tile, team, b -> {
                        if (b == null || !b.isValid()) return;
                        if (!links.contains(b.pos()) && links.size < hubBlock.maxConnections && linkValid(this, b)) {
                            configure(b.pos());
                        }
                    });
                }
                deselect();
                return false;
            }
            // tap on invalid target: exit config like PowerNode (return true -> deselect)
            return true;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(network.id);
            write.s(links.size);
            for (int i = 0; i < links.size; i++) {
                write.i(links.get(i));
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            network.id = read.i();
            // 兼容旧存档（<v1）：旧格式在 id 之后还有一个 network.version 字段，需跳过，
            // 否则后续 linkCount 会错位读到 version 值，导致链接数据损坏
            if (revision < 1) {
                read.i();
            }
            short linkCount = read.s();
            links.clear();
            for (int i = 0; i < linkCount; i++) {
                int pos = read.i();
                links.add(pos);
            }
            rebuildData(this);
        }

        @Override
        public byte version() {
            return 1;
        }
    }
}