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
    /** 矿机/工厂产出达到该容量比例即视为“快满”，触发向核心/仓库推送。 */
    public float surplusPushAt = 0.75f;

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

        // 长按拖线放置：InputHandler 将拖过的相对坐标以 Point2[] 传入
        config(arc.math.geom.Point2[].class, (ItemTransferHubBuild entity, arc.math.geom.Point2[] dragLinks) -> {
            entity.links.clear();
            for (arc.math.geom.Point2 link : dragLinks) {
                Building other = world.build(entity.tile.x + link.x, entity.tile.y + link.y);
                if (other == null || !other.isValid() || other == entity) continue;
                if (!linkValid(entity, other)) continue;
                if (entity.links.size >= maxConnections) break;
                entity.links.addUnique(other.pos());
                if (other instanceof ItemTransferHubBuild otherHub
                    && !otherHub.links.contains(entity.pos()) && otherHub.links.size < maxConnections) {
                    otherHub.links.addUnique(entity.pos());
                }
            }
            rebuildData(entity);
        });
    }

    private static boolean shouldConnect(Building other) {
        return HubRouting.shouldConnect(other);
    }

    public static boolean linkValid(Building tile, Building link) {
        return HubRouting.linkValid(tile, link);
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
                () -> Core.bundle.format("bar.silicon-hub-transfer-rate", b.transferRate),
                () -> Pal.accent,
                () -> Math.min(b.transferRate / 50f, 1f)
        ));
    }

    @Override
    public void placeEnded(Tile tile, mindustry.gen.Unit builder, int rotation, Object config) {
        if (!(config instanceof arc.math.geom.Point2[] links)) return;

        Building hubB = tile.build;
        if (!(hubB instanceof ItemTransferHubBuild hub)) return;

        for (arc.math.geom.Point2 link : links) {
            Tile other = world.tile(tile.x + link.x, tile.y + link.y);
            if (other == null || other.build == null || other.build == hub) continue;
            if (hub.links.contains(other.build.pos())) continue;
            if (hub.links.size >= maxConnections) break;
            hub.configure(other.build.pos());
        }
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

            if (!overlaps(wx, wy, b, range)) {
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

    /** Building 版：建筑中心已含 offset，直接以占位矩形判定。 */
    protected boolean overlaps(float srcx, float srcy, Building other, float range) {
        return Intersector.overlaps(Tmp.cr1.set(srcx, srcy, range),
            Tmp.r1.setCentered(other.x, other.y, other.block.size * tilesize, other.block.size * tilesize));
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
                // 放置预览：原版风格淡蓝灰，透明度随激光设置
                Draw.color(Pal.lightishGray, Renderer.laserOpacity);
                Lines.stroke(1f);
        });

        Draw.reset();
    }

    @Override
    public void drawPlanConfigTop(mindustry.entities.units.BuildPlan plan, arc.util.Eachable<mindustry.entities.units.BuildPlan> list) {
        if (!(plan.config instanceof arc.math.geom.Point2[] ps)) return;

        mindustry.world.Block self = this;
        int cx = plan.x, cy = plan.y;

        for (arc.math.geom.Point2 p : ps) {
            final int fx = cx + p.x, fy = cy + p.y;
            mindustry.entities.units.BuildPlan otherReq = findPlan(list, fx, fy, other ->
                other != plan && other.block != null && other.block.size > 0);

            // 与已放置建筑连线
            Tile placedTile = world.tile(fx, fy);
            if (placedTile != null && placedTile.build != null && shouldConnect(placedTile.build)) {
                Draw.color(Pal.lightishGray, Renderer.laserOpacity);
                Lines.stroke(1f);
                Drawf.dashLine(Pal.lightishGray,
                    plan.drawx(), plan.drawy(),
                    placedTile.build.x, placedTile.build.y);
                Drawf.square(placedTile.build.x, placedTile.build.y,
                    placedTile.build.block.size * tilesize / 2f + 2f, Pal.place);
            }

            // 与同批规划的其他中枢连线
            if (otherReq != null && otherReq.block == self) {
                Draw.color(Pal.lightishGray, Renderer.laserOpacity);
                Lines.stroke(1f);
                Drawf.dashLine(Pal.lightishGray, plan.drawx(), plan.drawy(),
                    otherReq.drawx(), otherReq.drawy());
                Drawf.square(otherReq.drawx(), otherReq.drawy(),
                    otherReq.block.size * tilesize / 2f + 2f, Pal.place);
            }
        }
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
        /** 传输速率：10 秒滑动窗口平均（件/秒） */
        public float transferRate = 0f;
        private static final int RATE_WINDOW_TICKS = 600; // 10s * 60fps
        private final arc.struct.Seq<Integer> rateWindowCounts = new arc.struct.Seq<>();
        private int rateWindowSum = 0;
        private int rateTickCounter = 0;

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
                // 非中枢建筑若已在相同网络内（被其它中枢服务），不重复连接
                if (!(other instanceof ItemTransferHubBuild) && inSameNetwork(other)) return;
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

            // 先并入上一帧跨枢分摊的延迟计费（chargePath 写入 powerConsumedNext），
            // 否则远端枢的耗电会被此处清零丢弃 → 电力统计 < 传输速率。
            powerConsumed = powerConsumedNext;
            powerConsumedNext = 0f;

            // 将上一帧转移数写入 10s 滑动窗口（每 tick 一个桶）
            if (rateTickCounter > 0 || rateWindowCounts.size == 0) {
                rateWindowCounts.add(transferCount);
                rateWindowSum += transferCount;
                if (rateWindowCounts.size > RATE_WINDOW_TICKS / 6) {
                    rateWindowSum -= rateWindowCounts.remove(0);
                }
            }
            rateTickCounter++;

            // 本帧发起的转移数清零；powerConsumed 现含延迟计费 + 本帧新计费（由 chargePath 继续累加）
            transferCount = 0;

            // 无电力或禁用：不调度，秒级统计归零（避免 powerPerSecond 滞留）
            if (power == null || power.status <= 0) {

                powerAccumulator = 0f;

                if (timer(3, 60)) {
                    powerPerSecond = 0f;
                    powerAccumulator = 0f;
                    transferRate = 0f;
                }

                return;
            }

            // 调度优先级（每帧顺序执行）：
            // ① 拉取：先满足工厂 / 炮台的原料需求（最高优先）
            // ② 推送：矿机 / 工厂溢出 → 核心（全网 BFS 找可收核心，无视距离）
            // ③ 兜底：核心满 / 无核时才落入仓库

            if (network.enableDemandPull) {
                pullOnDemand();
            }

            if (network.enableSurplusPush) {
                pushSurplusToCore();
            }

            // 积分：powerConsumed 是本帧瞬时功耗（10 × 经过本枢的件数/倍率），
            // 按帧累加后每秒取均作为 powerPerSecond 供 bar 显示。
            // 注意：chargePath 可能对远端 hub 的 powerConsumed 累加，需归入发起枢的 accumulator 统一口径
            // （发起枢的 powerConsumed 已含直连/路径首跳，远端 hub 的由其自身 updateTile 累加）
            powerAccumulator += powerConsumed;

            if (timer(3, 60)) {
                powerPerSecond = powerAccumulator;
                powerAccumulator = 0f;
            }

            // 10 秒平均运输速率：滑动窗口各 tick 件数之和 ÷ 窗口覆盖的秒数
            if (rateTickCounter % 10 == 0) {
                long sum = 0;
                for (int i = 0; i < rateWindowCounts.size; i++) {
                    sum += rateWindowCounts.get(i);
                }
                float seconds = Math.max(rateWindowCounts.size, 1) / 60f;
                transferRate = sum / seconds;
            }
        }

        private boolean isFactory(Building b) {
            return HubRouting.isFactory(b);
        }

        /**
         * 产出源判定：可被拉取的供源 —— 矿机产出 + 工厂产出。
         */
        /**
         * 消费者优先级：炮台(0) > 工厂(1) > 仓储(2)。数值越小越优先。
         */
        private int consumerPriority(Building b) {
            return HubRouting.consumerPriority(b);
        }

        private boolean isProducer(Building b) {
            return HubRouting.isProducer(b);
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
                // 只有工厂参与主动拉取；仓储不拉（仅被动接收核心满时的溢出推送）
                if (!isFactory(b)) {
                    continue;
                }
                boolean needs = false;
                for (int i = 0; i < content.items().size; i++) {
                    Item it = content.item(i);
                    if (it == null || it.id >= b.items.length()) {
                        continue;
                    }
                    if (b.items.get(it) < b.getMaximumAccepted(it)) {
                        needs = true;
                        break;
                    }
                }
                if (needs) {
                    consumers.add(b);
                }
            }

            /**
             * 消费者优先级（三级）：
             * ① 炮台 —— 断弹即失去防御能力，供弹最优先
             * ② 工厂 —— 保证生产线不断
             * ③ 仓储 —— 最后补存
             * 同级之间按缺口比例降序（最饿的先吃）
             */
            consumers.sort((a, b) -> {
                int ta = consumerPriority(a);
                int tb = consumerPriority(b);
                if (ta != tb) {
                    return Integer.compare(ta, tb);
                }
                return Float.compare(deficitRatio(b), deficitRatio(a));
            });

            for (Building consumer : consumers) {

                if (consumer.items == null || !consumer.isValid()) {
                    continue;
                }

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
                int cap = consumer.getMaximumAccepted(it);
                if (cap <= 0) {
                    continue;
                }
                if (consumer.items.get(it) < cap) {
                    ordered.add(it);
                }
            }
            final Building fc = consumer;
            ordered.sort((a, b) -> Float.compare(
                itemDeficitRatio(fc, b, false),
                itemDeficitRatio(fc, a, false)));
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

            }

            Building supplier = findNearestSupplier(consumer, item);

            if (supplier == null) {
                continue;
            }

            if (power == null || power.status <= 0) {
                return any;
            }

            if (directTransfer(supplier, consumer, item, 10)) {
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
                        if (producer.items.get(ck) >= producer.block.itemCapacity * surplusPushAt) {
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

                    // 仓储内容尽可能回核心（不再设 90% 保留阈值）

                    if (power == null || power.status <= 0) {
                        return;
                    }

                    CoreBlock.CoreBuild core = findNearestCore(producer, item);

                    Building target = core;

                    if (core == null || !core.acceptItem(producer, item)
                        || (core.items != null && item.id < core.items.length()
                            && core.items.get(item) >= core.block.itemCapacity)) {
                        // 矿机/工厂：核心满 → 回退最近仓库
                        // 仓储自身：核心满则停止（避免仓库间乒乓倒手）
                        target = isStorage ? null : findNearestStorage(producer, item);
                    }

                    if (target == null) {
                        continue;
                    }

                    directTransfer(producer, target, item, 10);
                }
            }
        }

        private void bfsInit() {

            bfsQueue.clear();
            bfsDists.clear();
            bfsVisited.clear();
            bfsVisited.add(id);
        }

        /**
         * 同类工厂互斥（精确版）：仅当该工厂【仍在接收】此物品（acceptItem 为真，
         * 即其配方输入料）且未满时，才视为输入料库存而不作为供源——
         * 避免同类工厂互相吸输入料形成乒乓。
         *
         * 工厂的【产出物】其 itemFilter 通常不含自身（acceptItem=false），
         * 因此产出物即使未满仓也会被判定为可拉取——修复了中间链
         * （A 厂产物喂 B 厂原料）中 A 被整体跳过的问题。
         */
        /**
         * 判断建筑是否已在本中枢网络内（经由任意中枢链路可达的直连建筑）。
         * 用于放置/双击自动连接时排除同网建筑——它们已被现有中枢服务。
         */
        private boolean inSameNetwork(Building b) {
            if (b == null) return false;
            if (data.buildings.contains(b)) return true;
            bfsInit();
            for (ItemTransferHubBuild h : data.hubs) {
                if (bfsVisited.add(h.id)) {
                    bfsQueue.add(h);
                    bfsDists.add(1);
                }
            }
            for (int i = 0; i < bfsQueue.size; i++) {
                ItemTransferHubBuild cur = bfsQueue.get(i);
                if (cur.data.buildings.contains(b)) return true;
                for (ItemTransferHubBuild nb : cur.data.hubs) {
                    if (bfsVisited.add(nb.id)) {
                        bfsQueue.add(nb);
                        bfsDists.add(bfsDists.get(i) + 1);
                    }
                }
            }
            return false;
        }

        private boolean isInputStockOfFactory(Building supplier, Item item) {
            return isFactory(supplier)
                && supplier.acceptItem(supplier, item)
                && supplier.items.get(item) < supplier.getMaximumAccepted(item);
        }

        /**
         * 供源三级优先级（满足工厂/炮台拉取时的取货顺序）：
         * ① 仓库（StorageBlock，非核心）—— 首选
         * ② 核心
         * ③ 矿机 / 工厂产出物
         * 每级内部取 BFS 最近；全部落空后，兜底允许同类工厂输入库存（防饿死）。
         */
        private Building findNearestSupplier(Building consumer, Item item) {

            for (int pass = 0; pass < 4; pass++) {

                Building best = null;
                int bestDist = Integer.MAX_VALUE;

                // 直连建筑：距离 1
                for (Building b : data.buildings) {
                    if (!supplierMatchesPass(b, item, pass)) continue;
                    if (b == consumer || !b.isValid() || b.items == null || b.items.get(item) <= 0) continue;
                    if (!consumer.acceptItem(b, item)) continue;
                    if (bestDist > 1) {
                        best = b;
                        bestDist = 1;
                    }
                }
                if (bestDist == 1) return best;

                // BFS 全网层序
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
                        if (!supplierMatchesPass(b, item, pass)) continue;
                        if (b == consumer || !b.isValid()) continue;
                        if (b.items == null || b.items.get(item) <= 0) continue;
                        if (!consumer.acceptItem(b, item)) continue;
                        if (d < bestDist) {
                            best = b;
                            bestDist = d;
                        }
                    }
                    for (ItemTransferHubBuild neighbor : hub.data.hubs) {
                        if (bfsVisited.add(neighbor.id)) {
                            bfsQueue.add(neighbor);
                            bfsDists.add(bfsDists.get(idx) + 1);
                        }
                    }
                }

                if (best != null) return best;
            }

            return null;
        }

        private boolean supplierMatchesPass(Building b, Item item, int pass) {
            switch (pass) {
                case 0: return b instanceof StorageBlock.StorageBuild && !(b instanceof CoreBlock.CoreBuild);
                case 1: return b instanceof CoreBlock.CoreBuild;
                case 2: return isProducer(b) && !isInputStockOfFactory(b, item);
                default: return true;
            }
        }

        /**
         * 最近可收货仓储（非核心）。用于核心满或无核时的次级落点。
         */
        private StorageBlock.StorageBuild findNearestStorage(Building producer, Item item) {
            StorageBlock.StorageBuild best = null;
            int bestDist = Integer.MAX_VALUE;

            for (Building b : data.buildings) {
                if (!(b instanceof StorageBlock.StorageBuild st)) continue;
                if (b instanceof CoreBlock.CoreBuild) continue;
                if (!b.isValid() || b.items == null || item.id >= b.items.length()) continue;
                if (b.items.get(item) >= b.block.itemCapacity) continue;
                if (!b.acceptItem(producer, item)) continue;
                int d = Math.abs(b.tile.x - producer.tile.x) + Math.abs(b.tile.y - producer.tile.y);
                if (d < bestDist) {
                    best = st;
                    bestDist = d;
                }
            }
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
            return directTransfer(supplier, consumer, item, 1);
        }

        /**
         * 批量直转：单次最多搬 maxAmount 件（受供源存量 / 收方余位约束），
         * 大幅提升矿机/工厂产物的吞吐速率。计费仍为每件经一枢 +10。
         */
        private boolean directTransfer(Building supplier, Building consumer, Item item, int maxAmount) {

            if (maxAmount <= 1) {
                return directTransfer(supplier, consumer, item);
            }

            if (power == null || power.status <= 0) {
                return false;
            }

            if (supplier.items == null || supplier.isValid() == false || supplier.items.get(item) <= 0) {
                return false;
            }

            if (!consumer.acceptItem(supplier, item)) {
                return false;
            }

            int supplierStock = supplier.items.get(item);
            int consumerSpace = consumer.getMaximumAccepted(item) - consumer.items.get(item);
            int moved = Math.min(Math.min(maxAmount, supplierStock), Math.max(consumerSpace, 0));

            if (moved <= 0) {
                return false;
            }

            // 零缓冲代理：直接操作双方库存（等价于 moved 次 handleItem）
            consumer.items.add(item, moved);
            supplier.items.remove(item, moved);

            // 经由计费：路径与费用整批只计算一次，避免逐件重跑 BFS
            chargeBatch(supplier, consumer, moved);

            transferCount += moved;

            return true;
        }

        /**
         * 计费口径：物品每经过一个中枢，该中枢消耗 10/moved 件——单价 10。
         * 均摊到路径上每个中枢；本枢直接入账，远端枢写入延迟队列（下一帧生效）。
         */
        private void chargeBatch(Building supplier, Building consumer, int moved) {
            ItemTransferHubBuild srcHub = findOwnerHub(supplier);
            ItemTransferHubBuild dstHub = findOwnerHub(consumer);

            // 直连同枢 / 无法归属：仅本枢按件收费
            if (srcHub == null || dstHub == null || srcHub == dstHub) {
                powerConsumed += 10f * moved;
                return;
            }

            Seq<ItemTransferHubBuild> path = bfsPath(srcHub, dstHub);
            if (path == null || path.size == 0) {
                powerConsumed += 10f * moved;
                return;
            }

            // 单价 10：物品每经过一个中枢，该中枢即消耗 10（不均摊）
            float share = 10f * moved;
            IntSet charged = new IntSet();
            for (ItemTransferHubBuild h : path) {
                if (!charged.add(h.id)) continue;
                if (h == this) {
                    powerConsumed += share;
                } else {
                    h.powerConsumedNext += share;
                }
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

                // 物流连线：原版风格淡蓝灰，透明度随激光设置
                Draw.color(Pal.lightishGray, Renderer.laserOpacity);
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
                        // 非中枢建筑已在相同网络内则跳过
                        if (!(b instanceof ItemTransferHubBuild) && inSameNetwork(b)) return;
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