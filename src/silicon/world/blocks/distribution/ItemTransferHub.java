package silicon.world.blocks.distribution;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
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
import silicon.util.SiliconLog;
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
    public int maxConnections = 20;
    /** 矿机/工厂产出达到该容量比例即视为“快满”，触发向核心/仓库推送。 */
    public float surplusPushAt = 0.75f;
    /** 调试日志开关（Silicon 设置页控制）。 */
    public static boolean debugFlows = false;

    /** 物流连线颜色：与「连接数」状态栏一致（Pal.items）。 */
    public static final Color linkColor = Pal.items;
    /** 电力节点风格激光贴图。 */
    public TextureRegion laserRegion, laserEndRegion;

    /** 连线透明度：读取 Silicon 设置「中枢连线透明度」（0-100，默认 100）。 */
    public static float linkOpacity() {
        return Core.settings.getInt("hubLinkOpacity", 100) / 100f;
    }

    @Override
    public void load() {
        super.load();
        laserRegion = Core.atlas.find("laser");
        laserEndRegion = Core.atlas.find("laser-end");
    }

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
                () -> Core.bundle.format("bar.silicon-hub-transfer-rate", Strings.fixed(b.transferRate, 1)),
                () -> Pal.accent,
                () -> Math.min(b.transferRate / 50f, 1f)
        ));
    }

    /**
     * 自动连接目标统一判定（预览 / 放置 / 双击 三处共用）。
     * - 范围内的所有中枢：一律可连
     * - 非中枢：不在【即将连接的中枢自身】网络系统内即可连
     *   （self 为空 = 放置预览/新中枢，网络为空，全部可连）
     */
    private static boolean autoConnectTargetValid(Building self, Building target){
        if (target == null || !target.isValid()) return false;
        if (target instanceof ItemTransferHubBuild) return true;

        if (self instanceof ItemTransferHubBuild h) {
            return !h.inSameNetwork(target);
        }
        return true;
    }

    /** 收集 root 枢纽所在网络的全部直连建筑（含自身，跨枢 BFS）。 */
    private static void collectNetworkBuildings(ItemTransferHubBuild root, arc.struct.ObjectSet<Building> out) {
        arc.struct.ObjectSet<ItemTransferHubBuild> seen = new arc.struct.ObjectSet<>();
        java.util.ArrayDeque<ItemTransferHubBuild> queue = new java.util.ArrayDeque<>();
        seen.add(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            ItemTransferHubBuild cur = queue.poll();
            out.addAll(cur.data.buildings);
            for (ItemTransferHubBuild nb : cur.data.hubs) {
                if (seen.add(nb)) queue.add(nb);
            }
        }
    }

    @Override
    public void placeEnded(Tile tile, mindustry.gen.Unit builder, int rotation, Object config) {
        if (!(config instanceof arc.math.geom.Point2[] links)) return;

        Building hubB = tile.build;
        if (!(hubB instanceof ItemTransferHubBuild hub)) return;

        for (arc.math.geom.Point2 link : links) {
            Tile other = world.tile(tile.x + link.x, tile.y + link.y);
            if (other == null || other.build == null || other.build == hub) continue;
            // 白名单 + 整体检测统一走 linkValid（排除核心旁已合并容器等）
            if (!linkValid(hub, other.build)) continue;
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

        Seq<Building> cands = new Seq<>();
        getPotentialLinks(tile, player.team(), cands::add);

        // 预览与实际建链口径一致：候选中枢网络内的建筑不显示连线
        // （它们将由其所在中枢继续服务，新枢只连中枢本身）
        arc.struct.ObjectSet<Building> servedByCandidateHubs = new arc.struct.ObjectSet<>();
        for (Building cand : cands) {
            if (cand instanceof ItemTransferHubBuild h) collectNetworkBuildings(h, servedByCandidateHubs);
        }

        for (Building other : cands) {
            if (!(other instanceof ItemTransferHubBuild) && servedByCandidateHubs.contains(other)) continue;
            float angle = Angles.angle(cx, cy, other.x, other.y);
            float len1 = size * tilesize / 2f;
            float len2 = other.block.size * tilesize / 2f;
            float ca = Mathf.cosDeg(angle), sa = Mathf.sinDeg(angle);
            float x1 = cx + ca * len1;
            float y1 = cy + sa * len1;
            float x2 = other.x - ca * len2;
            float y2 = other.y - sa * len2;
            float pulse = Mathf.absin(Time.time, 4f, 0.6f);
            Tmp.c1.set(linkColor).lerp(Color.white, pulse);
            Draw.color(Tmp.c1, linkOpacity());
            Drawf.laser(laserRegion, laserEndRegion, laserEndRegion,
                x1 + ca * 1.5f, y1 + sa * 1.5f,
                x2 - ca * 1.5f, y2 - sa * 1.5f, 0.25f);
            Drawf.square(other.x, other.y, other.block.size * tilesize / 2f + 2f, Pal.place);
        }

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
                Draw.color(linkColor, Renderer.laserOpacity);
                Lines.stroke(1.5f);
                Drawf.dashLine(linkColor,
                    plan.drawx(), plan.drawy(),
                    placedTile.build.x, placedTile.build.y);
                Drawf.square(placedTile.build.x, placedTile.build.y,
                    placedTile.build.block.size * tilesize / 2f + 2f, Pal.place);
            }

            // 与同批规划的其他中枢连线
            if (otherReq != null && otherReq.block == self) {
                Draw.color(linkColor, Renderer.laserOpacity);
                Lines.stroke(1.5f);
                Drawf.dashLine(linkColor, plan.drawx(), plan.drawy(),
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
        /** 本帧经手件数（含上一帧跨枢延迟并入的 transferCountNext）。 */
        private int transferCount = 0;
        /** 跨枢延迟计数：物品途经本枢（由其它枢纽发起调度）时写入，下一帧并入吞吐统计。 */
        public int transferCountNext = 0;
        /** 传输速率：10 秒滑动窗口平均（件/秒），含路过本枢的所有件数 */
        public float transferRate = 0f;
        private static final int RATE_WINDOW_TICKS = 600; // 10s * 60fps，真实 10 秒滑动窗口
        private final IntSeq rateWindowCounts = new IntSeq();
        private long rateWindowSum = 0;
        private int rateTickCounter = 0;

        private final Seq<ItemTransferHubBuild> bfsQueue = new Seq<>();
        private final IntSeq bfsDists = new IntSeq();
        private final IntSet bfsVisited = new IntSet();
        /**
         * 每非炮台消费者【上次供料后】的物品存量快照：
         * 本轮可补量 = 快照 − 当前库存（即真实消耗量）+ 少量缓冲，
         * 从数学上保证搬运量 ≈ 消耗量，杜绝批量补满带来的吞吐放大。
         */
        private final arc.struct.ObjectMap<Building, int[]> feedSnapshot = new arc.struct.ObjectMap<>();
        /** 调试：流量聚合计数（"标签" -> 件数），由设置开关控制输出。 */
        private final arc.struct.ObjectMap<String, Integer> debugFlow = new arc.struct.ObjectMap<>();
        private int debugTicks = 0;

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
            autoConnectNearby((ItemTransferHub) block);
            super.placed();
        }

        /**
         * 两阶段自动连接：候选按【中枢优先】排序——先建好全部枢-枢链路，
         * 再对非中枢目标做同网排除（inSameNetwork 此时已含刚连入的中枢），
         * 避免“既连中枢、又抢走该中枢网络内建筑”的重复服务。
         */
        private void autoConnectNearby(ItemTransferHub hubBlock) {
            Seq<Building> cands = new Seq<>();
            hubBlock.getPotentialLinks(tile, team, cands::add);
            cands.sort((x, y) -> Boolean.compare(y instanceof ItemTransferHubBuild, x instanceof ItemTransferHubBuild));
            for (Building other : cands) {
                if (links.size >= hubBlock.maxConnections) break;
                if (links.contains(other.pos())) continue;
                if (!autoConnectTargetValid(this, other)) continue;
                configure(other.pos());
            }
        }

        // ── 建筑拓扑（Building Topology）──────────────────────
        // 职责：本中枢的 links → data.hubs/buildings 本地视图重建与陈旧链剔除。
        private void updateTopology() {
            // 存档/地图加载期间邻居建筑可能尚未创建（Tile.changed() 会逐个触发
            // onProximityUpdate），此时不能因“目标不存在”误删已保存的链接；
            // 加载完成后由周期刷新兜底清理真正失效的链接。
            boolean loading = world.isGenerating();
            IntSeq stale = new IntSeq();
            links.each(pos -> {
                Building b = world.build(pos);
                if (b == null) {
                    if (!loading) stale.add(pos);
                    return;
                }
                if (!b.isValid() || b == this || !linkValid(this, b)) {
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
            // 清理不再直连的消费者存量快照（防 Building 引用滞留）
            Seq<Building> staleKeys = new Seq<>();
            for (Building k : feedSnapshot.keys()) {
                if (!data.buildings.contains(k)) staleKeys.add(k);
            }
            for (Building k : staleKeys) feedSnapshot.remove(k);
        }

        // ── 建筑级更新（Building Update）──────────────────────
        // 职责：本中枢直连的工厂拉取 / 仓储溢出推送 + 本枢 power/transfer 统计。
        // 网络级（ItemTransferHubNetwork）只提供 enableDemandPull/SurplusPush 总开关与寻址辅助。
        // 电力统计：consumePowerDynamic 拉 powerConsumed 瞬时值，秒级取 powerAccumulator 积分求均。
        @Override
        public void updateTile() {

            super.updateTile();

            // 周期性拓扑刷新：链路目标被拆除时不会触发本枢邻近事件，
            // 定时剔除失效路径并回收连接数（timers=4 中使用 id=2）。
            if (timer(2, 120)) {
                updateTopology();
            }

            // 帧首并入跨枢延迟计费/计数——两者语义【刻意不对称】：
            // powerConsumed 用【赋值】：其值跨帧有意义（电网读取最新请求），且供电路径
            //   无其它清零点，+= 会随时间无限膨胀（a0.11.9.0 前的耗电虚高根因）；
            // transferCount 用【+=】：入口恒为上帧清零后的 0，若用赋值会把
            //   【本帧刚调度产生的自有件数】连同延迟量一起覆盖丢失 → 速率恒 0 而耗电正常。
            powerConsumed = powerConsumedNext;
            powerConsumedNext = 0f;
            transferCount += transferCountNext;
            transferCountNext = 0;

            // 将上一帧转移数写入 10s 滑动窗口（每 tick 一个桶），随后本帧计数清零
            rateWindowCounts.add(transferCount);
            rateWindowSum += transferCount;
            if (rateWindowCounts.size > RATE_WINDOW_TICKS) {
                rateWindowSum -= rateWindowCounts.removeIndex(0);
            }
            transferCount = 0;
            rateTickCounter++;

            // 禁用 / 断电：不调度；瞬时请求清零——
            // 禁用时原版电网本就跳过本枢（shouldConsumePower=false），残留值只会冻结显示；
            // 断电时清零可避免“幽灵需求”挤占电池，且显示与实际消耗保持一致。
            if (!enabled || power == null || power.status <= 0) {
                powerConsumed = 0f;
                powerConsumedNext = 0f;
                powerAccumulator = 0f;

                if (timer(3, 60)) {
                    powerPerSecond = 0f;
                    transferRate = 0f;
                }

                return;
            }

            // 调度优先级（每帧顺序执行）：
            // ① 拉取：先满足工厂 / 炮台的原料需求（最高优先）
            // ② 推送：矿机 / 工厂溢出 → 核心（全网 BFS 找可收核心，无视距离）
            // ③ 兜底：核心满 / 无核时才落入仓库

            // 调度节流：每 10 tick（6Hz）执行一轮拉取/推送。
            // 零缓冲批量直转单轮即可搬 10 件，逐帧调度会产生远超产线需求的吞吐，
            // 导致耗电与速率统计虚高；节流后数值回归合理量级，调度行为本身不变。
            if (timer(0, 10)) {
                if (network.enableDemandPull) {
                    pullOnDemand();
                }

                if (network.enableSurplusPush) {
                    pushSurplusToCore();
                }
            }

            // 积分口径 = 实际取电量：电网欠载（status<1）时按满足率折算，
            // 与电网实际供给一致；满电时 status=1 即全额请求（10 × 经手件数）。
            powerAccumulator += powerConsumed * Math.min(power.status, 1f);

            if (timer(3, 60)) {
                powerPerSecond = powerAccumulator;
                powerAccumulator = 0f;
            }

            // 10 秒平均运输速率：滑动窗口各 tick 件数之和 ÷ 窗口覆盖的秒数
            if (rateTickCounter % 10 == 0) {
                float seconds = Math.max(rateWindowCounts.size, 1) / 60f;
                transferRate = rateWindowSum / seconds;
            }

            // 调试流量聚合输出（设置页开关控制，每 2 秒一次）
            if (debugFlows && ++debugTicks >= 120 && !debugFlow.isEmpty()) {
                StringBuilder sb = new StringBuilder("[中枢流量 @").append(tile.x).append(",").append(tile.y).append("]");
                for (arc.struct.ObjectMap.Entry<String, Integer> e : debugFlow) {
                    sb.append(' ').append(e.key).append('=').append(e.value);
                }
                sb.append(" | 速率=").append(transferRate).append(" 耗电=").append(powerPerSecond);
                SiliconLog.info(sb.toString());
                debugFlow.clear();
                debugTicks = 0;
            }
        }

        private boolean isFactory(Building b) {
            return HubRouting.isFactory(b);
        }

        /** 消费者优先级：炮台(0) > 工厂(1) > 仓储(2)。数值越小越优先。 */
        private int consumerPriority(Building b) {
            return HubRouting.consumerPriority(b);
        }

        private boolean isProducer(Building b) {
            return HubRouting.isProducer(b);
        }

        /** 推送源判定：矿机/工厂溢出优先推核心（其次仓储由拉取补货，不主动推）。 */
        private boolean isPushProducer(Building b) {
            return isProducer(b);
        }

        /**
         * 拉取调度：
         * 消费者三级优先：炮台(0) > 工厂(1)；仓储不拉取。
         * 同级按缺口比例降序；工厂内多输入物品同帧连补。
         * 供源四级：仓库 → 核心 → 矿机/工厂产出 → 兜底同类输入料。
         */
        private boolean pullOnDemand() {

            boolean any = false;

            // 收集待补消费者
            arc.struct.Seq<Building> consumers = new arc.struct.Seq<>();
            for (Building b : data.buildings) {
                if (b.items == null || !b.isValid()) continue;
                if (!isFactory(b)) continue;
                for (int i = 0; i < content.items().size; i++) {
                    Item it = content.item(i);
                    if (it == null || it.id >= b.items.length()) continue;
                    if (b.items.get(it) < b.getMaximumAccepted(it)) {
                        consumers.add(b);
                        break;
                    }
                }
            }

            // 排序：炮台 > 工厂；同级缺口比降序
            consumers.sort((a, b) -> {
                int ta = consumerPriority(a);
                int tb = consumerPriority(b);
                if (ta != tb) return Integer.compare(ta, tb);
                return Float.compare(deficitRatio(b), deficitRatio(a));
            });

            for (Building consumer : consumers) {

                if (consumer.items == null || !consumer.isValid()) continue;

                boolean turret = consumer instanceof ItemTurret.ItemTurretBuild;
                // 消耗匹配补货：非炮台消费者按「快照以来真实消耗量 + 缓冲」供给；
                // 炮台作战耗弹快，保持按缺口即时足量供弹
                int[] snap = turret ? null : feedSnapshot.get(consumer);

                // 候选物品：炮台按伤害降序；其余按缺口比降序
                Seq<Item> ordered = new Seq<>();
                if (consumer instanceof ItemTurret.ItemTurretBuild) {
                    ItemTurret tur = (ItemTurret) consumer.block;
                    tur.ammoTypes.each((it, bt) -> {
                        if (it != null && bt != null && it.id < consumer.items.length()
                            && consumer.items.get(it) < consumer.getMaximumAccepted(it)) {
                            ordered.add(it);
                        }
                    });
                    ordered.sort((a, b) -> Float.compare(
                        ((ItemTurret) consumer.block).ammoTypes.get(b).damage,
                        ((ItemTurret) consumer.block).ammoTypes.get(a).damage));
                } else {
                    for (int i = 0; i < content.items().size; i++) {
                        Item it = content.item(i);
                        if (it == null || it.id >= consumer.items.length()) continue;
                        int cap = consumer.getMaximumAccepted(it);
                        if (cap <= 0) continue;
                        if (consumer.items.get(it) < cap) ordered.add(it);
                    }
                    final Building fc = consumer;
                    ordered.sort((a, b) -> Float.compare(
                        itemDeficitRatio(fc, b, false),
                        itemDeficitRatio(fc, a, false)));
                }

                // 多源料工厂：同一帧连续补多种输入，不提前 break
                boolean fed = false;
                for (Item item : ordered) {
                    if (item.id >= consumer.items.length()) continue;
                    if (consumer.items.get(item) >= consumer.getMaximumAccepted(item)) continue;

                    Building supplier = findNearestSupplier(consumer, item);
                    if (supplier == null || !consumer.acceptItem(supplier, item)) continue;

                    if (power == null || power.status <= 0) return any;

                    // 预算：非炮台 = 真实消耗量 + 2 缓冲（首轮回看快照缺省 2）
                    int budget = 10;
                    if (!turret) {
                        int consumedSince = snap == null || item.id >= snap.length
                            ? 0 : Math.max(0, snap[item.id] - consumer.items.get(item));
                        budget = Math.min(10, consumedSince + 2);
                        if (budget <= 0) continue;
                    }

                    if (directTransfer(supplier, consumer, item, budget)) {
                        any = true;
                        fed = true;
                        addFlow("拉:" + consumer.block.name, moved);
                    }
                }
                // 刷新快照为当前存量（含本轮供料），供下轮计算真实消耗
                if (!turret && consumer.items != null) {
                    int[] ns = new int[consumer.items.length()];
                    for (int i = 0; i < ns.length; i++) ns[i] = consumer.items.get(i);
                    feedSnapshot.put(consumer, ns);
                }
            }

            return any;
        }

    /** 单物品缺口比例：1 - 当前/上限，越大越缺。 */
    private float itemDeficitRatio(Building b, Item it, boolean storage) {
        if (it.id >= b.items.length()) {
            return 0f;
        }
        int cap = storage
            ? (int) (b.block.itemCapacity * 0.9f)
            : b.getMaximumAccepted(it);
        if (cap <= 0 || it.id >= b.items.length()) {
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
         * 推送优先级：
         * ① 工厂 / 炮台（需要该原料且未满的消费者）
         * ② 核心
         * ③ 仓库（兜底）
         */
        private void pushSurplusToCore() {

            for (Building producer : data.buildings) {

                if (producer.items == null || producer.items.empty() || !producer.isValid()) {
                    continue;
                }

                boolean isStorage = producer instanceof StorageBlock.StorageBuild
                    && !(producer instanceof CoreBlock.CoreBuild);
                boolean isProducerB = isPushProducer(producer);

                if (!isStorage && !isProducerB) {
                    continue;
                }

                // 矿机/工厂：任一输出达到快满阈值才排空；仓储用 90% 按物判断
                if (isProducerB) {
                    boolean blocked = false;
                    for (int k = 0; k < producer.items.length(); k++) {
                        Item ck = content.item(k);
                        if (ck == null) continue;
                        if (producer.items.get(ck) >= producer.block.itemCapacity * surplusPushAt) {
                            blocked = true;
                            break;
                        }
                    }
                    if (!blocked) continue;
                }

                for (int i = 0; i < producer.items.length(); i++) {

                    Item item = content.item(i);
                    if (item == null || producer.items.get(item) == 0) continue;

                    // 输入料保护：该物品仍是本生产建筑愿意接收的原料（未满）时不外运，
                    // 否则推送出去会被拉取逻辑再抽回来，形成乒乓倒手、虚增吞吐与耗电。
                    // 输出物必然不被自身接收（acceptItem=false），不受影响；仓库分支除外。
                    if (!isStorage && producer.acceptItem(producer, item)) continue;

                    if (isStorage) {
                        float stock = producer.items.get(item);
                        boolean surplus = stock >= producer.block.itemCapacity * 0.9f;
                        if (!surplus) {
                            // 核心该物品低于 75% 阈值时，仓库存量即可回收，
                            // 不受仓库自身 90% 盈余阈值限制（与产出推送阈值对齐）
                            CoreBlock.CoreBuild probe = findNearestCore(producer, item);
                            if (probe == null
                                || probe.items.get(item) >= probe.block.itemCapacity * surplusPushAt) continue;
                        }
                    }

                    if (power == null || power.status <= 0) return;

                    // ① 优先：找需要该原料的工厂/炮台
                    Building factoryTarget = findNearestConsumer(producer, item);
                    if (factoryTarget != null) {
                        directTransfer(producer, factoryTarget, item, 10);
                        addFlow("分:" + factoryTarget.block.name, moved);
                        continue;
                    }

                    // ② 其次：推核心（该物品存量低于 75% 阈值时）
                    CoreBlock.CoreBuild core = findNearestCore(producer, item);
                    boolean coreHasRoom = false;
                    if (core != null && core.acceptItem(producer, item)) {
                        int cap = Math.max(core.block.itemCapacity, 1);
                        int cur = (core.items != null && item.id < core.items.length()) ? core.items.get(item) : 0;
                        // 与推送阈值统一：核心该物品存量低于 75% 即可接收
                        coreHasRoom = cur < cap * surplusPushAt;
                    }
                    // ③ 核心≥75%或拒收：产物回流仓库
                    if (!coreHasRoom || core == null) {
                        StorageBlock.StorageBuild storage = findNearestStorage(producer, item);
                        if (storage != null) {
                            forceTransferToStorage(producer, storage, item, 10);
                            continue;
                        }
                    }
                    if (coreHasRoom && core != null) {
                        directTransfer(producer, core, item, 10);
                        addFlow("推:核心", moved);
                    }
                }
            }
        }

        /**
         * 找最近的需要该物品的工厂/炮台消费者。
         */
        private Building findNearestConsumer(Building producer, Item item) {
            Building best = null;
            int bestDist = Integer.MAX_VALUE;

            for (Building b : data.buildings) {
                if (b == producer || !b.isValid()) continue;
                if (!isFactory(b)) continue;
                if (b.items == null || b.items.get(item) >= b.getMaximumAccepted(item)) continue;
                if (!b.acceptItem(producer, item)) continue;
                int d = Math.abs(b.tile.x - producer.tile.x) + Math.abs(b.tile.y - producer.tile.y);
                if (d < bestDist) {
                    best = b;
                    bestDist = d;
                }
            }
            return best;
        }
        /**
         * 强制入库：供源 → 仓库。跳过收方 acceptItem（规避原版仓库-核心容量联动），
         * 仅以仓库自身剩余容量为约束。
         */
        private boolean forceTransferToStorage(Building supplier, StorageBlock.StorageBuild storage, Item item, int maxAmount){
            if (supplier.items == null || !supplier.isValid() || item.id >= supplier.items.length()) return false;
            int stock = supplier.items.get(item);
            if (stock <= 0) return false;
            if (item.id >= storage.items.length()) return false;
            int space = storage.block.itemCapacity - storage.items.get(item);
            int moved = Math.min(Math.min(maxAmount, stock), Math.max(space, 0));
            if (moved <= 0) return false;

            // 与 directTransfer 同契约：逐件经 handleItem 交付（仓库默认实现即 items.add）
            for (int i = 0; i < moved; i++) {
                storage.handleItem(supplier, item);
            }
            supplier.items.remove(item, moved);
            addFlow("推:仓库", moved);

            // 计费与统计口径与 directTransfer 一致（统一在 chargeBatch 内完成）
            addFlow("推:->仓库", moved);
            chargeBatch(supplier, storage, moved);
            return true;
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
                    if (item.id >= b.items.length()) continue;
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
                        if (item.id >= b.items.length()) continue;
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
         * 直连与跨中枢 BFS 双层查找：仓库常连在其它中枢上，
         * 仅扫直连会导致“核心满却推不进仓库”。
         */
        private StorageBlock.StorageBuild findNearestStorage(Building producer, Item item) {
            StorageBlock.StorageBuild best = null;
            int bestDist = Integer.MAX_VALUE;

            // 第一层：直连建筑
            for (Building b : data.buildings) {
                if (!(b instanceof StorageBlock.StorageBuild st)) continue;
                if (b instanceof CoreBlock.CoreBuild) continue;
                if (!b.isValid() || b.items == null || item.id >= b.items.length()) continue;
                // 不检查 acceptItem：原版仓库与核心容量联动，核心满会连带拒收；
                // 以仓库自身容量为准即可
                if (b.items.get(item) >= b.block.itemCapacity) continue;
                int d = Math.abs(b.tile.x - producer.tile.x) + Math.abs(b.tile.y - producer.tile.y);
                if (d < bestDist) {
                    best = st;
                    bestDist = d;
                }
            }
            if (best != null) return best;

            // 第二层：BFS 全网层序，寻找其它中枢直连的仓库
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
                    if (!(b instanceof StorageBlock.StorageBuild st)) continue;
                    if (b instanceof CoreBlock.CoreBuild) continue;
                    if (!b.isValid() || b.items == null || item.id >= b.items.length()) continue;
                    if (b.items.get(item) >= b.block.itemCapacity) continue;
                    if (d < bestDist) {
                        best = st;
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
            return best;
        }

        private CoreBlock.CoreBuild findNearestCore(Building producer, Item item) {
            // Route-variable: same BFS nearest logic for cores
            CoreBlock.CoreBuild best = null;
            int bestDist = Integer.MAX_VALUE;

            for (Building b : data.buildings) {
                if (b instanceof CoreBlock.CoreBuild core && b.isValid() && item.id < core.items.length() && core.acceptItem(producer, item)) {
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

            if (power == null || power.status <= 0) {
                return false;
            }

            if (supplier.items == null || supplier.isValid() == false || supplier.items.get(item) <= 0 || item.id >= supplier.items.length()) {
                return false;
            }

            if (!consumer.acceptItem(supplier, item)) {
                return false;
            }

            int supplierStock = supplier.items.get(item);
            int consumerSpace = consumer.getMaximumAccepted(item) - consumer.items.get(item);

            // 距离过近保护：供源与消费者贴面时，原版邻接卸货已在工作；
            // 中枢再抽会造成同帧供需倒手。跳过贴面供源，改由其它供源层级满足。
            int half = (supplier.block.size + consumer.block.size) / 2 + 1;
            if (Math.abs(supplier.tile.x - consumer.tile.x) <= half
                && Math.abs(supplier.tile.y - consumer.tile.y) <= half) {
                return false;
            }

            // 供源保留配额：最多抽走存量的一半（向下取整），防止源头被瞬间抽干后
            // 看起来“拉不到原料”。矿机产量低时尤其明显。
            int reserve = supplierStock / 2;
            int available = supplierStock - reserve;

            int moved = Math.min(Math.min(maxAmount, available), Math.max(consumerSpace, 0));

            if (moved <= 0) {
                return false;
            }

            // 零缓冲代理：供方扣减 + 收方经 handleItem 逐件交付。
            // 默认 handleItem 为 items.add(1)；炮台等重写实现会把物品转换为弹药，
            // 直接 items.add 会绕过转换造成"死库存"（既非弹药也无法取出）。
            for (int i = 0; i < moved; i++) {
                consumer.handleItem(supplier, item);
            }
            supplier.items.remove(item, moved);

            // 经由计费：路径与费用整批只计算一次，避免逐件重跑 BFS；
            // 计费与吞吐计数统一在 chargeBatch 内完成
            chargeBatch(supplier, consumer, moved);

            return true;
        }

        /**
         * 计费与吞吐统计口径（统一入口）：
         * 物品每经过一个中枢：该中枢自身消耗 10 电力、经手件数 +moved。
         * 每个枢纽只统计"通过自己"的那一跳——发起枢不为其它枢的经手买单，
         * 路径不可达时也只按端点归属各记一跳，绝不把全程记到单个枢头上。
         */
        private void chargeBatch(Building supplier, Building consumer, int moved) {
            ItemTransferHubBuild srcHub = findOwnerHub(supplier);
            ItemTransferHubBuild dstHub = findOwnerHub(consumer);

            // 端点无法归属：仅当该端点直连本枢时才计入本枢
            if (srcHub == null || dstHub == null) {
                if ((srcHub == null && data.buildings.contains(supplier))
                    || (dstHub == null && data.buildings.contains(consumer))) {
                    powerConsumed += 10f * moved;
                    transferCount += moved;
                }
                return;
            }

            // 同枢直转：费用与吞吐归该枢本身（本枢直接入账，远端枢延迟一帧）
            if (srcHub == dstHub) {
                chargeOne(srcHub, moved);
                return;
            }

            Seq<ItemTransferHubBuild> path = bfsPath(srcHub, dstHub);
            if (path == null || path.size == 0) {
                // 路径不可达（拓扑竞态兜底）：两端点归属枢各记自己的一跳
                chargeOne(srcHub, moved);
                chargeOne(dstHub, moved);
                return;
            }

            // 路径上每个经手中枢各计自己的一跳；本枢直接入账，远端枢下一帧生效
            IntSet charged = new IntSet();
            for (ItemTransferHubBuild h : path) {
                if (!charged.add(h.id)) continue;
                chargeOne(h, moved);
            }
        }

        /** 调试计数（仅 debugFlows 开启时累计）。 */
        void addFlow(String tag, int moved) {
            if (debugFlows) debugFlow.merge(tag, moved, Integer::sum);
        }

        /** 单跳计费/计数：本枢直接入账，远端枢写入延迟队列（下一帧并入）。 */
        private void chargeOne(ItemTransferHubBuild h, int moved) {
            float share = 10f * moved;
            if (h == this) {
                powerConsumed += share;
                transferCount += moved;
            } else {
                h.powerConsumedNext += share;
                h.transferCountNext += moved;
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

                // 物流连线：电力节点激光样式（颜色随时间轻微脉动，同原版电力线）
                float pulse = Mathf.absin(Time.time, 4f, 0.6f);
                Tmp.c1.set(linkColor).lerp(Color.white, pulse);
                Draw.color(Tmp.c1, linkOpacity());
                // 原版大小：PowerNode 默认 laserScale=0.25，且端点向内收缩 1.5px
                Drawf.laser(laserRegion, laserEndRegion, laserEndRegion,
                    x1 + cos * 1.5f, y1 + sin * 1.5f,
                    x2 - cos * 1.5f, y2 - sin * 1.5f, 0.25f);
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
                        // 已直连：蓝白色
                        Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.place);
                    } else if (linkValid(this, link)) {
                        if (inSameNetwork(link)) {
                            // 同网络但未直连：紫色（区别于蓝=直连、绿=可新建）
                            Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.reactorPurple);
                        } else {
                            // 可新建直连：绿色提示
                            Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.heal);
                        }
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
                    // 双击已连中枢：清空全部链接
                    while (links.size > 0) {
                        int pos = links.first();
                        Building ob = world.build(pos);
                    if (ob instanceof ItemTransferHubBuild oh) {
                        oh.links.removeValue(this.pos());
                        rebuildData(oh);
                    }
                    links.removeValue(pos);
                }
                rebuildData(this);
                } else {
                    // 双击自动连接：与放置同一套【中枢优先两阶段】逻辑
                    autoConnectNearby(hubBlock);
                }
                deselect();
                return false;
            }
            return true;
        }

        // ── 存档序列化（Save / Load）──────────────────────
        // v1 格式：network.id(int) + 链接数(short) + 每个链接 pos(int)。
        // version() 必须与格式配套：未序列化链接的旧构建写入 revision=0 且无自定义数据，
        // 读取时按 revision<1 直接跳过，避免错位解析损坏存档流。

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
            if (revision < 1) {
                // 未写过自定义数据的存档：保持空链接，加载后由放置预览/周期刷新重建
                return;
            }
            network.id = read.i();
            short linkCount = read.s();
            links.clear();
            for (int i = 0; i < linkCount; i++) {
                links.add(read.i());
            }
            // 仅按已存在的建筑重建本地视图；加载中缺失的邻居由
            // onProximityUpdate / 周期 updateTopology 补齐，不在此剔除链接
            rebuildData(this);
        }

        @Override
        public byte version() {
            return 1;
        }
    }
}
