package silicon.world.blocks.satellite;

import arc.Core;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Call;
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
    /** 耗电（/秒，Mindustry 按 /60 tick 计）：100 电力/秒 */
    public static final float POWER_CONSUMPTION = 100f / 60f;

    public SatelliteConsole(String name) {
        super(name);
        buildType = SatelliteConsoleBuild::new;
        size = 3;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
        // 需要供电：100 电力/秒（选中面板显示原版电力条）
        consumePower(POWER_CONSUMPTION);
        // 卫星所属信号走原版 configure 机制同步（服务器 tileConfig 权威下发，各端 selectedSignal 一致）
        config(String.class, (SatelliteConsoleBuild b, String value) ->
                b.selectedSignal = (value == null || value.isEmpty()) ? null : value);
    }

    public class SatelliteConsoleBuild extends Building {
        /** 卫星所属信号编码（4 位；null=无归属，全图信号保持蓝色） */
        public String selectedSignal = null;
        /** 上次渲染的信号源列表签名（窗口实时刷新用） */
        private String lastSrcSignature = "";

        /** 发射卫星：本队可点发射。权威端（主机/单机）直接执行；纯客机发请求由主机执行并广播/反馈结果 */
        public void launch() {
            // 关闭（enabled=false，逻辑门/开关控制）：不能发射
            if (!enabled) {
                Vars.ui.showInfoToast(Core.bundle.get("block.silicon-satellite-console.disabled"), 3f);
                return;
            }
            // 纯客机（联网但非主机）：发射请求交给主机（sat-launch），主机校验后执行并广播状态、反馈失败原因
            if (Vars.net.active() && !SatelliteManager.isAuthority()) {
                Call.serverPacketReliable("sat-launch", tileX() + "," + tileY() + "|"
                        + (selectedSignal == null ? "" : selectedSignal));
                return;
            }
            // 权威端：本地执行（建筑逻辑与卫星状态均在主机/单机计算）
            doLaunch(selectedSignal);
        }

        /** 权威端发射执行 + 结果提示（launch 的本地路径，或主机的 sat-launch 处理器直接调用） */
        public void doLaunch(String signalName) {
            int result = SatelliteManager.launch(team, signalName);
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

        /** 选中时的小面板：仅一个"打开界面"按钮，点击后打开可拖动窗口 */
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            table.button(Core.bundle.get("block.silicon-satellite-console.open"), Styles.defaultt, () -> {
                // 隐藏原版小面板（此时 showConfig 已完成，hide 动画正常生效），再打开可拖动窗口
                if (Vars.control != null && Vars.control.input != null) {
                    Vars.control.input.config.hideConfig();
                }
                openDialog();
            }).size(160f, 48f).pad(4f);
        }

        /** 打开可拖动窗口 */
        void openDialog() {
            BaseDialog dialog = new BaseDialog(Core.bundle.get("block.silicon-satellite-console.title"));
            // 可拖动式窗口（原版对话框默认可拖标题栏移动）；不铺满全屏
            dialog.setFillParent(false);
            dialog.setMovable(true);
            // 尺寸按屏幕比例动态计算（大屏封顶 620×500，小屏按比例缩小）
            float w = Math.min(620f, Core.graphics.getWidth() * 0.6f);
            float h = Math.min(500f, Core.graphics.getHeight() * 0.72f);
            dialog.cont.pane(content -> rebuildFull(content, dialog)).width(w).height(h).pad(10f);
            dialog.buttons.button(Core.bundle.get("block.silicon-satellite-console.close"), Styles.defaultt, dialog::hide)
                    .size(120f, 40f).padTop(6f);
            dialog.show();
        }

        /** 窗口内容：状态 + 当前信号 + 信号选择（搜索/滚轮，参考信号中继器）+ 发射按钮 */
        void rebuildFull(Table table, BaseDialog dialog) {
            table.clearChildren();
            table.top();
            // 状态（动态刷新）
            table.label(() -> Core.bundle.format("block.silicon-satellite-console.status.ready",
                    SatelliteManager.readyCount(team))).color(arc.graphics.Color.lightGray).pad(2f);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-satellite-console.status.orbit",
                    SatelliteManager.launchedCount(team))).color(arc.graphics.Color.lightGray).pad(2f);
            table.row();
            // 当前卫星所属信号（顶部居中，与中继器"当前编号"风格一致）
            table.label(() -> Core.bundle.format("block.silicon-satellite-console.signal.current",
                    selectedSignal == null || selectedSignal.isEmpty()
                            ? Core.bundle.get("block.silicon-satellite-console.nobind") : selectedSignal))
                    .pad(2f);
            table.row();
            // 信号选择区（参考信号中继器：搜索框模糊过滤 + 滚轮按钮网格 + 清除）
            Table srcTable = new Table();
            arc.scene.ui.TextField search = table.field("", text -> rebuildSourceButtons(srcTable, text.trim()))
                    .width(280f).padTop(2f).get();
            search.setMessageText(Core.bundle.get("block.silicon-satellite-console.signal.search"));
            search.setMaxLength(4);
            table.row();
            ScrollPane pane = new ScrollPane(srcTable, Styles.noBarPane);
            pane.setScrollingDisabled(true, false); // 禁水平滚动，垂直滚轮翻页
            table.add(pane).height(160f).growX().padTop(2f);
            table.row();
            // 清除按钮
            table.button(Core.bundle.get("block.silicon-satellite-console.signal.clear"), Styles.defaultt, () -> {
                selectedSignal = null;
                configure("");
                rebuildSourceButtons(srcTable, search.getText().trim());
            }).size(88f, 40f).padTop(2f);
            table.row();
            // 发射按钮（状态为动态 label，发射后自动刷新，无需重建窗口）
            table.button(Core.bundle.get("block.silicon-satellite-console.launch"), Styles.defaultt, this::launch)
                    .size(280f, 56f).padTop(10f);
            // 实时刷新：信号源列表变化（增删/编号变更）时重建按钮区（保持搜索过滤）
            lastSrcSignature = "";
            pane.update(() -> {
                String sig = sourceSignature();
                if (!sig.equals(lastSrcSignature)) {
                    lastSrcSignature = sig;
                    rebuildSourceButtons(srcTable, search.getText().trim());
                }
            });
            // 初始填充全部信号源
            rebuildSourceButtons(srcTable, "");
        }

        /** 模糊匹配：query 的字符按顺序出现在 code 中（子序列匹配，忽略大小写）；空 query 匹配一切（与中继器一致） */
        static boolean fuzzyMatch(String code, String query) {
            int qi = 0;
            for (int i = 0; i < code.length() && qi < query.length(); i++) {
                if (Character.toUpperCase(code.charAt(i)) == Character.toUpperCase(query.charAt(qi))) qi++;
            }
            return qi == query.length();
        }

        /** 重建源按钮区（按搜索模糊过滤；无匹配显示提示） */
        void rebuildSourceButtons(Table srcTable, String filter) {
            srcTable.clearChildren();
            srcTable.center();
            Seq<SignalSource.SignalSourceBuild> srcs = SignalSource.allSources(team);
            boolean any = false;
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            int perRow = 5, count = 0;
            for (SignalSource.SignalSourceBuild sb : srcs) {
                String code = sb.signal == null ? "----" : sb.signal.name;
                if (!filter.isEmpty() && !fuzzyMatch(code, filter)) continue;
                any = true;
                TextButton btn = new TextButton(code, Styles.flatTogglet);
                btn.setChecked(code.equals(selectedSignal));
                // configure 走网络同步（服务器权威下发，各端一致）；乐观先设本地并刷新按钮选中态
                btn.clicked(() -> {
                    selectedSignal = code;
                    configure(code);
                    rebuildSourceButtons(srcTable, filter);
                });
                group.add(btn);
                srcTable.add(btn).size(88f, 40f).pad(1f);
                if (++count % perRow == 0) srcTable.row();
            }
            if (!any) {
                srcTable.add(Core.bundle.get("block.silicon-satellite-console.signal.none"))
                        .color(arc.graphics.Color.lightGray).pad(2f);
            }
        }

        /** 信号源列表签名（数量 + 编号集合），用于检测列表变化 */
        String sourceSignature() {
            StringBuilder sb = new StringBuilder();
            Seq<SignalSource.SignalSourceBuild> srcs = SignalSource.allSources(team);
            sb.append(srcs.size).append(':');
            for (SignalSource.SignalSourceBuild s : srcs) {
                sb.append(s.signal == null ? "----" : s.signal.name).append(',');
            }
            return sb.toString();
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
