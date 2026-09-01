package silicon.world.blocks.satellite;

import arc.Core;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.ui.Styles;
import mindustry.world.Block;
import silicon.util.SatelliteManager;
import silicon.world.blocks.signal.SignalSource;
import silicon.world.meta.Signal;

/**
 * 卫星控制台（3×3）：卫星的发射终端，仅提供发射操作。
 * 不存储燃料与电力——燃料（1000 石油）与缓冲电力（10000）均由卫星发射中枢提供；
 * 卫星种类由卫星发射中枢选择。点击方块打开界面查看状态并发射。
 */
public class SatelliteConsole extends Block {
    /** 卫星种类：信号卫星（与发射中枢保持一致） */
    public static final int TYPE_SIGNAL = 0;

    public SatelliteConsole(String name) {
        super(name);
        buildType = SatelliteConsoleBuild::new;
        size = 3;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
    }

    public class SatelliteConsoleBuild extends Building {
        /** 卫星所属信号编码（4 位；null=无归属，全图信号保持蓝色） */
        public String selectedSignal = null;

        /** 发射卫星：由 SatelliteManager 检查/扣减中枢的燃料与缓冲电力，并记录卫星所属信号 */
        public void launch() {
            // 关闭（enabled=false，逻辑门/开关控制）：不能发射
            if (!enabled) {
                Vars.ui.showInfoToast(Core.bundle.get("block.silicon-satellite-console.disabled"), 3f);
                return;
            }
            int result = SatelliteManager.launch(team, selectedSignal);
            String key;
            switch (result) {
                case SatelliteManager.LAUNCH_OK: key = "block.silicon-satellite-console.success"; break;
                case SatelliteManager.LAUNCH_NO_READY: key = "block.silicon-satellite-console.noready"; break;
                case SatelliteManager.LAUNCH_NO_FUEL: key = "block.silicon-satellite-console.nofuel"; break;
                case SatelliteManager.LAUNCH_NO_POWER: key = "block.silicon-satellite-console.nopower"; break;
                default: key = "block.silicon-satellite-console.fail"; break;
            }
            if (result == SatelliteManager.LAUNCH_OK) {
                Vars.ui.showInfoToast(Core.bundle.format(key, SatelliteManager.launchedCount(team)), 3f);
            } else {
                Vars.ui.showInfoToast(Core.bundle.get(key), 3f);
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            rebuildConfig(table);
        }

        private void rebuildConfig(Table table) {
            table.clearChildren();
            table.top();
            // 状态
            table.add(Core.bundle.format("block.silicon-satellite-console.status.ready", SatelliteManager.readyCount(team))).color(arc.graphics.Color.lightGray).pad(2f);
            table.row();
            table.add(Core.bundle.format("block.silicon-satellite-console.status.orbit", SatelliteManager.launchedCount(team))).color(arc.graphics.Color.lightGray).pad(2f);
            table.row();
            // 卫星所属信号选择（本队信号源编码）
            table.add(Core.bundle.get("block.silicon-satellite-console.signal")).padTop(8f).padBottom(2f);
            table.row();
            Seq<SignalSource.SignalSourceBuild> srcs = SignalSource.allSources(team);
            if (srcs.isEmpty()) {
                table.add(Core.bundle.get("block.silicon-satellite-console.nosignal")).color(arc.graphics.Color.lightGray).pad(2f);
                table.row();
            } else {
                ButtonGroup<TextButton> group = new ButtonGroup<>();
                for (SignalSource.SignalSourceBuild sb : srcs) {
                    String code = sb.signal == null ? "----" : sb.signal.name;
                    TextButton btn = new TextButton(code, Styles.flatTogglet);
                    btn.setChecked(code.equals(selectedSignal));
                    btn.clicked(() -> selectedSignal = code);
                    group.add(btn);
                    table.add(btn).size(120f, 40f).pad(2f);
                    table.row();
                }
            }
            // 发射按钮
            table.button(Core.bundle.get("block.silicon-satellite-console.launch"), Styles.defaultt, () -> {
                launch();
                rebuildConfig(table);
            }).size(220f, 48f).padTop(6f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.str(selectedSignal == null ? "" : selectedSignal);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            String s = read.str();
            selectedSignal = s.isEmpty() ? null : s;
        }
    }
}
