package silicon.world.blocks.satellite;

import arc.Core;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import silicon.util.SatelliteManager;
import silicon.world.blocks.signal.SignalSource;

/**
 * 卫星控制台（3×3）：卫星的发射终端，仅提供发射操作。
 * 不存储燃料与电力——燃料（1000 石油）与缓冲电力（10000）均由卫星发射中枢提供；
 * 卫星种类由卫星发射中枢选择。点击方块打开全屏界面查看状态并发射。
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

        /** 不使用原版小配置面板：点击直接打开全屏界面 */
        @Override
        public boolean shouldShowConfigure(Player player) {
            return false;
        }

        /** 点击方块：打开可拖动窗口 */
        @Override
        public void tapped() {
            BaseDialog dialog = new BaseDialog(Core.bundle.get("block.silicon-satellite-console.title"));
            // 可拖动式窗口（原版对话框默认可拖标题栏移动）；不铺满全屏，保持固定大小
            dialog.setFillParent(false);
            dialog.setMovable(true);
            dialog.cont.pane(content -> rebuildFull(content, dialog)).width(600f).height(400f).pad(10f);
            dialog.buttons.button(Core.bundle.get("block.silicon-satellite-console.close"), Styles.defaultt, dialog::hide)
                    .size(120f, 40f).padTop(6f);
            dialog.show();
        }

        /** 窗口内容：状态 + 卫星所属信号选择（滚轮）+ 发射按钮 */
        void rebuildFull(Table table, BaseDialog dialog) {
            table.clearChildren();
            table.top();
            // 标题
            table.add(Core.bundle.get("block.silicon-satellite-console.name")).color(Pal.accent).pad(6f);
            table.row();
            // 状态（动态刷新）
            table.label(() -> Core.bundle.format("block.silicon-satellite-console.status.ready",
                    SatelliteManager.readyCount(team))).color(arc.graphics.Color.lightGray).pad(2f);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-satellite-console.status.orbit",
                    SatelliteManager.launchedCount(team))).color(arc.graphics.Color.lightGray).pad(2f);
            table.row();
            // 卫星所属信号选择（本队信号源编码；滚动区限高）
            table.add(Core.bundle.get("block.silicon-satellite-console.signal")).padTop(8f).padBottom(2f);
            table.row();
            Table srcTable = new Table();
            srcTable.center();
            Seq<SignalSource.SignalSourceBuild> srcs = SignalSource.allSources(team);
            if (srcs.isEmpty()) {
                srcTable.add(Core.bundle.get("block.silicon-satellite-console.nosignal")).color(arc.graphics.Color.lightGray).pad(2f);
            } else {
                ButtonGroup<TextButton> group = new ButtonGroup<>();
                int perRow = 4, count = 0; // 窗口 600f 宽：4 列
                for (SignalSource.SignalSourceBuild sb : srcs) {
                    String code = sb.signal == null ? "----" : sb.signal.name;
                    TextButton btn = new TextButton(code, Styles.flatTogglet);
                    btn.setChecked(code.equals(selectedSignal));
                    btn.clicked(() -> selectedSignal = code);
                    group.add(btn);
                    srcTable.add(btn).size(120f, 40f).pad(2f);
                    if (++count % perRow == 0) srcTable.row();
                }
            }
            ScrollPane pane = new ScrollPane(srcTable, Styles.noBarPane);
            pane.setScrollingDisabled(true, false); // 禁水平滚动，垂直滚轮翻页
            table.add(pane).height(200f).growX();
            table.row();
            // 发射按钮
            table.button(Core.bundle.get("block.silicon-satellite-console.launch"), Styles.defaultt, () -> {
                launch();
                // 发射后刷新状态（保留界面）
                rebuildFull(table, dialog);
            }).size(280f, 56f).padTop(10f);
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
