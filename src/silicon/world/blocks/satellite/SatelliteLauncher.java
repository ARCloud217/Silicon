package silicon.world.blocks.satellite;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Liquids;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import silicon.content.Statuses;
import silicon.util.SatelliteManager;

/**
 * 卫星发射中枢（3×3）：通电后自动生产卫星，并负责发射所需的燃料与电力储备。
 * - 生产阶段消耗 5000 电力/秒（从电网）；每中枢同时只能生产 1 颗，完成后停止耗电并显示「可发射卫星」提示
 * - 内置电力缓冲 10000（consumePowerBuffered，从电网充电），发射时扣除
 * - 燃料储存：石油（容量 1000），发射时扣除
 * - 卫星由卫星控制台选择种类并发射
 */
public class SatelliteLauncher extends Block {
    /** 生产一颗卫星耗时（tick），60 秒 */
    public static final float PRODUCE_TIME = 60f * 60f;
    /** 生产阶段耗电（/秒，Mindustry 按 /60 tick 计） */
    public static final float POWER_CONSUMPTION = 5000f / 60f;
    /** 发射所需缓冲电力 */
    public static final float LAUNCH_POWER = 10000f;
    /** 缓冲充电速率（/秒）：电网供电时向缓冲充电 */
    public static final float CHARGE_RATE = 2000f / 60f;
    /** 发射所需石油燃料 */
    public static final int FUEL_OIL = 1000;

    public SatelliteLauncher(String name) {
        super(name);
        buildType = SatelliteLauncherBuild::new;
        size = 3;
        solid = true;
        destructible = true;
        update = true;
        // 生产阶段耗电（电网直耗）；发射用 10000 缓冲由本方块充电积累
        consumePower(POWER_CONSUMPTION);
        // 燃料储存（石油液体）
        hasLiquids = true;
        liquidCapacity = FUEL_OIL;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerCapacity, LAUNCH_POWER, StatUnit.powerSecond);
        stats.add(Stat.productionTime, PRODUCE_TIME / 60f, StatUnit.seconds);
    }

    public class SatelliteLauncherBuild extends Building {
        /** 生产进度（tick） */
        public float progress = 0f;
        /** 发射缓冲电量（0~10000，电网供电时充电积累，发射时一次性消耗） */
        public float battery = 0f;
        /** 本中枢是否已生产完成一颗（待发射） */
        public boolean produced = false;
        /** 是否已登记到待发射队列 */
        private boolean registered = false;

        @Override
        public void updateTile() {
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
            progress += delta();
            if (progress >= PRODUCE_TIME) {
                progress = PRODUCE_TIME;
                produced = true;
                register();
            }
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

        /** 生产完成：方块上方悬浮卫星图标提示「可发射卫星」 */
        @Override
        public void draw() {
            super.draw();
            if (produced) {
                Draw.rect(Statuses.satelliteBuff.uiIcon, x, y + 16f + Mathf.sin(Time.time / 24f, 3f));
            }
        }

        /** 状态条：生产进度（生产时），可发射（完成时绿色） */
        @Override
        public void drawStatus() {
            if (produced) {
                Draw.color(Pal.accent);
                Draw.rect(Core.atlas.find("status-bar-top"), x, y + size * 4f, 14f, 4f);
                Draw.reset();
            } else if (power != null && power.status > 0.001f) {
                Draw.color(Pal.ammo);
                Draw.rect(Core.atlas.find("status-bar-top"), x, y + size * 4f, 14f * progress / PRODUCE_TIME, 4f);
                Draw.reset();
            }
        }

        /** 选中时显示生产状态、缓冲电力与燃料 */
        @Override
        public void display(Table table) {
            super.display(table);
            if (produced) {
                table.row();
                table.add(Core.bundle.get("block.silicon-satellite-launcher.ready")).color(Pal.accent);
            } else if (power != null && power.status > 0.001f) {
                table.row();
                table.add(Core.bundle.format("block.silicon-satellite-launcher.progress", (int) (progress / PRODUCE_TIME * 100f)));
            }
            table.row();
            int powerPct = (int) (battery / LAUNCH_POWER * 100f);
            table.add(Core.bundle.format("block.silicon-satellite-launcher.power", powerPct)).color(arc.graphics.Color.lightGray);
            table.row();
            table.add(Core.bundle.format("block.silicon-satellite-launcher.fuel", (int) liquids.get(Liquids.oil), FUEL_OIL)).color(arc.graphics.Color.lightGray);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(progress);
            write.bool(produced);
            write.f(battery);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            progress = read.f();
            produced = read.bool();
            battery = read.f();
        }
    }
}
