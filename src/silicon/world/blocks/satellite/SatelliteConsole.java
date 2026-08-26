package silicon.world.blocks.satellite;

import arc.Core;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.ui.Styles;
import mindustry.world.Block;
import silicon.util.SatelliteManager;

/**
 * 卫星控制台（3×3）：卫星的发射终端，仅提供操作界面（选型 + 发射）。
 * 不存储燃料与电力——燃料（1000 石油）与缓冲电力（10000）均由卫星发射中枢提供。
 * 点击方块打开界面：选择卫星种类（目前仅「信号卫星」）、查看待发射/在轨状态、点击发射。
 */
public class SatelliteConsole extends Block {
    /** 卫星种类：信号卫星 */
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
        /** 当前选择的卫星种类（0=信号卫星） */
        public int selectedType = TYPE_SIGNAL;

        /** 发射卫星：由 SatelliteManager 检查/扣减中枢的燃料与缓冲电力 */
        public void launch() {
            int result = SatelliteManager.launch(team, selectedType);
            String key = switch (result) {
                case SatelliteManager.LAUNCH_OK -> "block.silicon-satellite-console.success";
                case SatelliteManager.LAUNCH_NO_READY -> "block.silicon-satellite-console.noready";
                case SatelliteManager.LAUNCH_NO_FUEL -> "block.silicon-satellite-console.nofuel";
                case SatelliteManager.LAUNCH_NO_POWER -> "block.silicon-satellite-console.nopower";
                default -> "block.silicon-satellite-console.fail";
            };
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
            // 卫星种类选择
            table.add(Core.bundle.get("block.silicon-satellite-console.type")).pad(4f);
            table.row();
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            TextButton signalBtn = new TextButton(Core.bundle.get("block.silicon-satellite-console.type.signal"), Styles.flatTogglet);
            signalBtn.setChecked(selectedType == TYPE_SIGNAL);
            signalBtn.clicked(() -> selectedType = TYPE_SIGNAL);
            group.add(signalBtn);
            table.add(signalBtn).size(180f, 44f).pad(3f);
            table.row();
            // 状态
            table.add(Core.bundle.format("block.silicon-satellite-console.status.ready", SatelliteManager.readyCount(team))).color(arc.graphics.Color.lightGray).pad(2f);
            table.row();
            table.add(Core.bundle.format("block.silicon-satellite-console.status.orbit", SatelliteManager.launchedCount(team))).color(arc.graphics.Color.lightGray).pad(2f);
            table.row();
            // 发射按钮
            table.button(Core.bundle.get("block.silicon-satellite-console.launch"), Styles.defaultt, () -> {
                launch();
                rebuildConfig(table);
            }).size(220f, 48f).padTop(6f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(selectedType);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            selectedType = read.i();
        }
    }
}
