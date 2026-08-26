package silicon.world.blocks.satellite;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import silicon.content.Statuses;
import silicon.util.SatelliteManager;

import static mindustry.type.ItemStack.with;

/**
 * 卫星发射中枢（3×3）：选择卫星种类并生产卫星，同时负责发射所需的燃料与电力储备。
 * - 生产材料（选择种类后开始生产时一次性消耗）：铜 5000、硅 5000、塑钢 1250、巨浪合金 1250、冷冻液 1000
 * - 生产阶段消耗 5000 电力/秒（电网）；每中枢同时只能生产 1 颗，完成后停止耗电并显示「可发射卫星」提示
 * - 内置 10000 发射缓冲（电网供电充电）；发射燃料石油（1000）亦储存在本中枢
 * - 卫星由卫星控制台点击发射
 */
public class SatelliteLauncher extends Block {
    /** 信号卫星生产耗时（tick），60 秒 */
    public static final float PRODUCE_TIME_SIGNAL = 60f * 60f;
    /** 测试卫星生产耗时（tick），1 秒 */
    public static final float PRODUCE_TIME_TEST = 60f;
    /** 生产阶段耗电（/秒，Mindustry 按 /60 tick 计） */
    public static final float POWER_CONSUMPTION = 5000f / 60f;
    /** 发射所需缓冲电力 */
    public static final float LAUNCH_POWER = 10000f;
    /** 缓冲充电速率（/秒）：电网供电时向缓冲充电 */
    public static final float CHARGE_RATE = 2000f / 60f;
    /** 发射所需石油燃料 */
    public static final int FUEL_OIL = 1000;
    /** 生产所需冷冻液 */
    public static final int COST_CRYOFLUID = 1000;
    /** 生产所需物品材料 */
    public static final ItemStack[] PRODUCTION_ITEMS = with(
            Items.copper, 5000,
            Items.silicon, 5000,
            Items.plastanium, 1250,
            Items.surgeAlloy, 1250
    );

    /** 卫星种类：信号卫星 */
    public static final int TYPE_SIGNAL = 0;
    /** 卫星种类：测试卫星（材料 1 铜，无实际效果，仅用于测试） */
    public static final int TYPE_TEST = 1;

    /** 测试卫星的生产材料（1 铜，无冷冻液） */
    public static final ItemStack[] TEST_PRODUCTION_ITEMS = with(Items.copper, 1);

    /** 按种类返回生产所需物品材料 */
    public static ItemStack[] productionItems(int type) {
        return type == TYPE_TEST ? TEST_PRODUCTION_ITEMS : PRODUCTION_ITEMS;
    }

    /** 按种类返回生产所需冷冻液 */
    public static int productionCryofluid(int type) {
        return type == TYPE_TEST ? 0 : COST_CRYOFLUID;
    }

    /** 按种类返回生产耗时（测试卫星 1 秒，信号卫星 60 秒） */
    public static float produceTime(int type) {
        return type == TYPE_TEST ? PRODUCE_TIME_TEST : PRODUCE_TIME_SIGNAL;
    }

    public SatelliteLauncher(String name) {
        super(name);
        buildType = SatelliteLauncherBuild::new;
        size = 3;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
        // 生产阶段耗电（电网直耗）；发射用 10000 缓冲由本方块充电积累
        consumePower(POWER_CONSUMPTION);
        // 材料储存（物品 + 液体：石油/冷冻液）
        hasItems = true;
        acceptsItems = true;
        itemCapacity = 5000 + 5000 + 1250 + 1250;
        hasLiquids = true;
        liquidCapacity = FUEL_OIL + COST_CRYOFLUID;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerCapacity, LAUNCH_POWER, StatUnit.powerSecond);
        stats.add(Stat.productionTime, produceTime(TYPE_SIGNAL) / 60f, StatUnit.seconds);
        for (ItemStack stack : PRODUCTION_ITEMS) {
            stats.add(Stat.input, stack);
        }
    }

    public class SatelliteLauncherBuild extends Building {
        /** 当前选择的卫星种类（0=信号卫星） */
        public int selectedType = TYPE_SIGNAL;
        /** 生产进度（tick） */
        public float progress = 0f;
        /** 发射缓冲电量（0~10000，电网供电时充电积累，发射时一次性消耗） */
        public float battery = 0f;
        /** 本中枢是否已生产完成一颗（待发射） */
        public boolean produced = false;
        /** 是否已登记到待发射队列 */
        private boolean registered = false;
        /** 选中面板需求材料行（切换种类时重建） */
        private final Table materialTable = new Table();
        /** 上次显示的种类（用于检测切换并重建材料行） */
        private int lastShownType = -1;

        @Override
        public void updateTile() {
            // 材料行随种类实时更新（切换种类即时重建）
            if (selectedType != lastShownType) {
                lastShownType = selectedType;
                rebuildMaterialTable();
            }
            // 电网有电时向发射缓冲充电（发射储备）
            if (power != null && power.status > 0.001f && battery < LAUNCH_POWER) {
                battery = Math.min(LAUNCH_POWER, battery + CHARGE_RATE * delta());
            }
            if (produced) {
                // 保持登记（发射后由 SatelliteManager 重置）
                register();
                return;
            }
            // 断电不生产（进度保留）
            if (power == null || power.status <= 0.001f) return;
            // 生产开始：检查并一次性扣除材料（进度 > 0 表示已扣）
            if (progress <= 0f) {
                if (!hasProductionMaterials()) return;
                consumeProductionMaterials();
            }
            progress += delta();
            if (progress >= produceTime(selectedType)) {
                progress = produceTime(selectedType);
                produced = true;
                register();
            }
        }

        /** 生产材料是否充足（按当前所选种类：物品 + 冷冻液） */
        public boolean hasProductionMaterials() {
            for (ItemStack stack : productionItems(selectedType)) {
                if (items.get(stack.item) < stack.amount) return false;
            }
            return liquids.get(Liquids.cryofluid) >= productionCryofluid(selectedType);
        }

        /** 扣除生产材料（一次性，按当前所选种类） */
        public void consumeProductionMaterials() {
            for (ItemStack stack : productionItems(selectedType)) {
                items.remove(stack.item, stack.amount);
            }
            liquids.remove(Liquids.cryofluid, productionCryofluid(selectedType));
        }

        void register() {
            if (!registered) {
                SatelliteManager.addReady(this);
                registered = true;
            }
        }

        void unregister() {
            if (registered) {
                SatelliteManager.removeReady(this);
                registered = false;
            }
        }

        /** 物品输入：仅接受生产所需材料（铜/硅/塑钢/巨浪合金），且未满库存（override 默认的 consumesItem 检查） */
        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.get(item) >= itemCapacity) return false;
            for (ItemStack stack : PRODUCTION_ITEMS) {
                if (stack.item == item) return true;
            }
            return false;
        }

        /** 液体输入：仅接受石油（燃料）与冷冻液（生产材料） */
        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            if (liquids.get(liquid) >= liquidCapacity) return false;
            return liquid == Liquids.oil || liquid == Liquids.cryofluid;
        }

        /** 发射前资源检查：返回 LAUNCH_OK 或缺失原因 */
        public int checkLaunchResources() {
            if (liquids.get(Liquids.oil) < FUEL_OIL) return SatelliteManager.LAUNCH_NO_FUEL;
            if (battery < LAUNCH_POWER) return SatelliteManager.LAUNCH_NO_POWER;
            return SatelliteManager.LAUNCH_OK;
        }

        /** 发射：扣除燃料与缓冲电力，重置本中枢使其可再生产（由 SatelliteManager 调用） */
        public void consumeLaunchResources() {
            liquids.remove(Liquids.oil, FUEL_OIL);
            battery = Math.max(0f, battery - LAUNCH_POWER);
            // 同步从电网电池扣除（模拟真实消耗，电网无电池则仅清空本缓冲）
            if (power != null) power.graph.useBatteries(LAUNCH_POWER);
            resetForLaunch();
        }

        /** 卫星发射后重置，使本中枢可再生产 */
        public void resetForLaunch() {
            produced = false;
            progress = 0f;
            registered = false;
        }

        @Override
        public void onProximityAdded() {
            super.onProximityAdded();
            // 读档恢复：已生产完成的中枢重新登记
            if (produced) register();
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            unregister();
        }

        /** 绘制：生产完成时方块上方悬浮「可发射」提示（不绘制常驻卫星图标，按原版简洁显示） */
        @Override
        public void draw() {
            super.draw();
            if (produced) {
                Draw.z(35f);
                Draw.rect(Statuses.satelliteBuff.uiIcon, x, y + 16f + Mathf.sin(Time.time / 24f, 3f), 16f, 16f);
                Draw.reset();
            }
        }

        /** 状态显示：原版状态条（缺材料/断电自动着色）+ 原版风格制造进度条 + 石油不足图标 */
        @Override
        public void drawStatus() {
            // 原版状态条：底部灰色方块 + 状态色（缺材料=红、供电正常=绿），缺失物品由此显示
            super.drawStatus();
            if (produced) {
                Draw.reset();
                return;
            }
            // 制造进度条（原版风格：灰底 + 强调色填充，方块顶部）
            if (power != null && power.status > 0.001f) {
                float barW = size * 8f - 8f;
                float barH = 2.5f;
                float barY = y + size * 4f + 2f;
                Draw.color(Pal.gray, 0.7f);
                Fill.rect(x, barY, barW, barH);
                float t = Math.min(1f, progress / produceTime(selectedType));
                Draw.color(Pal.accent);
                Fill.rect(x - barW / 2f + barW * t / 2f, barY, barW * t, barH);
            }
            // 石油不足：方块左下角显示石油小图标（原版缺液体风格）
            if (liquids.get(Liquids.oil) < FUEL_OIL) {
                Draw.rect(Liquids.oil.uiIcon, x - size * 4f + 6f, y - size * 4f + 6f, 8f, 8f);
            }
            Draw.reset();
        }

        /** 配置面板：选择卫星种类（生产所需种类） */
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            table.add(Core.bundle.get("block.silicon-satellite-launcher.type")).pad(4f);
            table.row();
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            TextButton signalBtn = new TextButton(Core.bundle.get("block.silicon-satellite-launcher.type.signal"), Styles.flatTogglet);
            signalBtn.setChecked(selectedType == TYPE_SIGNAL);
            signalBtn.clicked(() -> selectedType = TYPE_SIGNAL);
            group.add(signalBtn);
            table.add(signalBtn).size(200f, 44f).pad(3f);
            table.row();
            TextButton testBtn = new TextButton(Core.bundle.get("block.silicon-satellite-launcher.type.test"), Styles.flatTogglet);
            testBtn.setChecked(selectedType == TYPE_TEST);
            testBtn.clicked(() -> selectedType = TYPE_TEST);
            group.add(testBtn);
            table.add(testBtn).size(200f, 44f).pad(3f);
        }

        /** 当前种类显示名（bundle 键） */
        String typeNameKey() {
            return selectedType == TYPE_TEST
                    ? "block.silicon-satellite-launcher.type.test" : "block.silicon-satellite-launcher.type.signal";
        }

        /** 选中面板（按原版空军工厂样式）：需求材料+石油（图标+数量角标右下角）、进度条、电力条 */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.table(info -> {
                info.left();
                // 需求材料 + 石油（图标横排，需求数量角标覆盖在右下角；切换种类即时重建）
                info.add(materialTable).growX();
                info.row();
                // 卫星制造进度条
                float total = produceTime(selectedType);
                info.add(new Bar(
                        () -> produced ? Core.bundle.get("block.silicon-satellite-launcher.ready")
                                : Core.bundle.format("block.silicon-satellite-launcher.progress", (int) (Math.min(1f, progress / total) * 100f)),
                        () -> produced ? Pal.accent : Pal.ammo,
                        () -> produced ? 1f : Math.min(1f, progress / total)))
                        .height(18f).growX();
                info.row();
                // 电力条（单独显示：发射缓冲 Bar）
                info.add(new Bar(
                        () -> (int) (battery / LAUNCH_POWER * 100f) + "%",
                        () -> Pal.power,
                        () -> battery / LAUNCH_POWER))
                        .height(14f).growX();
            }).left();
        }

        /** 重建需求材料行（按原版：图标 + 需求数量角标覆盖在右下角；石油同样式加入；切换种类即时重建） */
        void rebuildMaterialTable() {
            materialTable.clearChildren();
            materialTable.left();
            for (ItemStack stack : productionItems(selectedType)) {
                materialTable.table(r -> {
                    r.left();
                    r.stack(
                            new Image(stack.item.uiIcon),
                            new Table(t -> t.add(new Label(String.valueOf(stack.amount)) {{
                                setFontScale(0.35f);
                            }}).bottom().right().pad(0f))
                    ).size(36f);
                }).padRight(4f);
            }
            if (productionCryofluid(selectedType) > 0) {
                materialTable.table(r -> {
                    r.left();
                    r.stack(
                            new Image(Liquids.cryofluid.uiIcon),
                            new Table(t -> t.add(new Label(String.valueOf(COST_CRYOFLUID)) {{
                                setFontScale(0.35f);
                            }}).bottom().right().pad(0f))
                    ).size(36f);
                }).padRight(4f);
            }
            // 石油（发射燃料）：同样式，需求数量 1000 角标右下角
            materialTable.table(r -> {
                r.left();
                r.stack(
                        new Image(Liquids.oil.uiIcon),
                        new Table(t -> t.add(new Label(String.valueOf(FUEL_OIL)) {{
                            setFontScale(0.35f);
                        }}).bottom().right().pad(0f))
                ).size(36f);
            }).padRight(4f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(selectedType);
            write.f(progress);
            write.bool(produced);
            write.f(battery);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            selectedType = read.i();
            progress = read.f();
            produced = read.bool();
            battery = read.f();
        }
    }
}
