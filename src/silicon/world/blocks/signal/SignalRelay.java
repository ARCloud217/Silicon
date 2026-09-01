package silicon.world.blocks.signal;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;
import mindustry.ui.Styles;
import mindustry.world.Block;
import silicon.util.SignalOverlay;

/**
 * 信号中继器：位于信号覆盖范围（信号源或已激活中继器的 15 格内）时自动激活，
 * 激活后自身同样提供半径 15 格的信号，可级联延长信号覆盖。
 * 信号强度与信号源一致（正态分布衰减，0~15），绑定放置队伍。
 */
public class SignalRelay extends Block {
    /** 中继器信号半径（格） */
    public static final float RADIUS = SignalSource.RADIUS;

    public SignalRelay(String name) {
        super(name);
        // 手动指定建筑类
        buildType = SignalRelayBuild::new;
        size = 1;
        solid = true;
        destructible = true;
        // 需要更新以检测激活状态
        update = true;
        // 需要供电才能工作：50 电力/秒（Mindustry 功耗按 /60 tick 计）
        consumePower(50f / 60f);
        // 可配置：绑定信号源编号（空串=清除绑定）
        configurable = true;
        config(String.class, (SignalRelayBuild b, String value) ->
                b.selectedSource = (value == null || value.isEmpty()) ? null : value);
    }

    /**
     * 每队中继器缓存（建筑放置/拆除/加载时标记失效重建，避免每帧遍历 Groups.build）。
     */
    private static final ObjectMap<Team, Seq<SignalRelayBuild>> relayCache = new ObjectMap<>();
    private static boolean dirty = true;

    /** 标记缓存失效（建筑增删时调用） */
    public static void markDirty() {
        dirty = true;
    }

    static void rebuildCache() {
        if (!dirty) return;
        dirty = false;
        relayCache.clear();
        for (Building b : Groups.build) {
            if (b instanceof SignalRelayBuild rb) {
                relayCache.get(rb.team, Seq::new).add(rb);
            }
        }
    }

    /** 收集某队伍的所有中继器（走缓存） */
    public static Seq<SignalRelayBuild> allRelays(Team team) {
        rebuildCache();
        return relayCache.get(team, new Seq<>());
    }

    /** 放置预览显示信号范围（同信号源） */
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Draw.color(SignalOverlay.SIGNAL_COLOR, 0.5f);
        Drawf.circles(x * 8 + 4f, y * 8 + 4f, RADIUS * 8f);
        Draw.reset();
    }

    public class SignalRelayBuild extends Building {
        /** 是否已激活（在所选信号源覆盖范围内） */
        public boolean active = false;
        /** 绑定的信号源编号（4 位；null/空=未绑定，不发射） */
        public String selectedSource = null;
        /** 中继信道（兼容字段：未绑定时用；绑定后信道跟随所选信号源） */
        public int channel = 1;
        private int timer = 0;

        @Override
        public void onProximityAdded() {
            super.onProximityAdded();
            SignalRelay.markDirty();
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            SignalRelay.markDirty();
        }

        @Override
        public void updateTile() {
            // 每 20 tick 检测一次激活状态（级联传播：逐级激活）
            if (++timer >= 20) {
                timer = 0;
                updateActive();
            }
        }

        /** 供电是否充足（power.status：0=无电，1=满电） */
        private boolean hasPower() {
            return power != null && power.status > 0.001f;
        }

        /** 查找绑定的信号源（按编号） */
        public SignalSource.SignalSourceBuild findSource() {
            if (selectedSource == null || selectedSource.isEmpty()) return null;
            for (SignalSource.SignalSourceBuild sb : SignalSource.allSources(team)) {
                if (sb.signal != null && selectedSource.equals(sb.signal.name)) return sb;
            }
            return null;
        }

        /** 发射信道：绑定信号源后与其保持一致；绑定卫星归属信号时用其信道；未绑定用自身 channel */
        public int signalChannel() {
            SignalSource.SignalSourceBuild src = findSource();
            if (src != null) return src.channel;
            // 卫星中继：绑定编号 == 卫星归属编号 → 卫星归属信道
            String sat = silicon.util.SatelliteManager.satelliteSignal(team);
            if (selectedSource != null && selectedSource.equals(sat)) {
                int sch = SignalChannel.satelliteChannel(team);
                if (sch >= 0) return sch;
            }
            return channel;
        }

        void updateActive() {
            boolean newActive = false;
            // 被禁用（如开关控制）或断电时不激活
            if (enabled && hasPower()) {
                // 必须绑定信号源，且该源存在并供电
                SignalSource.SignalSourceBuild src = findSource();
                if (src != null && src.power != null && src.power.status > 0.001f) {
                    // 在所选信号源覆盖范围内（或其同源级联转发范围内）才能发射
                    if (Mathf.dst(x, y, src.x, src.y) <= RADIUS * 8f) {
                        newActive = true;
                    } else {
                        // 级联：其他绑定同一信号源且已激活的中继器
                        for (SignalRelayBuild rb : SignalRelay.allRelays(team)) {
                            if (rb == this || !rb.active) continue;
                            if (selectedSource != null && selectedSource.equals(rb.selectedSource)
                                    && Mathf.dst(x, y, rb.x, rb.y) <= RADIUS * 8f) {
                                newActive = true;
                                break;
                            }
                        }
                    }
                }
                // 卫星中继：绑定编号 == 卫星归属编号，且卫星信号有效（归属信道未被完全压制）
                if (!newActive) {
                    String sat = silicon.util.SatelliteManager.satelliteSignal(team);
                    if (selectedSource != null && selectedSource.equals(sat)) {
                        int sch = SignalChannel.satelliteChannel(team);
                        float satJam = sch >= 0 ? SignalJammer.strengthAt(team, sch, x, y) : 0f;
                        float satEff = Math.max(0f, silicon.util.SatelliteManager.signalStrength(team) - satJam);
                        if (satEff > 0.5f) newActive = true;
                    }
                }
            }
            if (newActive != active) {
                active = newActive;
                SignalRelay.markDirty();
            }
        }

        /** 配置面板（与信号源面板风格一致：顶部当前编号 + 居中黄色标题 + 按钮行；灰底面板） */
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            table.table(Styles.grayPanel, t -> {
                t.top();
                // 顶部：当前转发编号（跨满整行居中，与信号源编号显示风格一致）
                t.label(() -> Core.bundle.format("block.silicon-signal-relay.source.current",
                        selectedSource == null || selectedSource.isEmpty() ? Core.bundle.get("block.silicon-signal-relay.nobind") : selectedSource))
                        .colspan(SignalJammer.CHANNEL_MAX).center().pad(2f);
                t.row();
                // 标题居中，原版黄色（跨满整行，避免挤占首列导致按钮间距不均）
                t.add(Core.bundle.get("block.silicon-signal-relay.source")).colspan(SignalJammer.CHANNEL_MAX).center()
                        .color(mindustry.graphics.Pal.accent).pad(2f);
                t.row();
                Seq<SignalSource.SignalSourceBuild> srcs = SignalSource.allSources(team);
                if (srcs.isEmpty()) {
                    t.add(Core.bundle.get("block.silicon-signal-relay.nosource")).color(arc.graphics.Color.lightGray).pad(2f);
                } else {
                    arc.scene.ui.ButtonGroup<arc.scene.ui.TextButton> group = new arc.scene.ui.ButtonGroup<>();
                    for (SignalSource.SignalSourceBuild sb : srcs) {
                        String code = sb.signal == null ? "----" : sb.signal.name;
                        arc.scene.ui.TextButton btn = new arc.scene.ui.TextButton(code, Styles.flatTogglet);
                        btn.setChecked(code.equals(selectedSource));
                        btn.clicked(() -> configure(code));
                        group.add(btn);
                        t.add(btn).size(88f, 40f).pad(1f);
                    }
                }
                t.row();
                // 清除按钮（与按钮行同高，风格统一）
                t.button(Core.bundle.get("block.silicon-signal-relay.source.clear"), Styles.defaultt,
                        () -> configure("")).size(88f, 40f).padTop(2f);
            }).pad(4f);
        }

        /** 本中继器在指定世界坐标处的原始信号强度（0~15，激活时；干扰由 SignalChannel 统一计算） */
        public float strengthAt(float wx, float wy) {
            if (!active) return 0f;
            return SignalSource.strengthAt(x, y, wx, wy);
        }

        /** 选中时显示信号范围（激活=深蓝，未激活=灰色） */
        @Override
        public void drawSelect() {
            super.drawSelect();
            Draw.color(active ? SignalOverlay.SIGNAL_COLOR : SignalOverlay.NO_SIGNAL_COLOR, active ? 0.6f : 0.3f);
            Lines.stroke(2f);
            Lines.circle(x, y, RADIUS * 8f);
            Draw.reset();
        }

        /** 选中显示：仅保留原版 bar（生命/电力）+ 信号唯一编号（绑定源编号） */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-relay.source.current",
                    selectedSource == null || selectedSource.isEmpty() ? Core.bundle.get("block.silicon-signal-relay.nobind") : selectedSource)).pad(2f);
        }

        /** 存档/网络同步 active 字段（host 上由 updateActive 重算，保证一致性） */
        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(active);
            write.i(channel);
            write.str(selectedSource == null ? "" : selectedSource);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            active = read.bool();
            if (revision >= 1) {
                channel = read.i();
            }
            if (revision >= 2) {
                String s = read.str();
                selectedSource = s.isEmpty() ? null : s;
            }
        }
    }
}
