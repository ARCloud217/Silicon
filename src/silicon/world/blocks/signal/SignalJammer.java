package silicon.world.blocks.signal;

import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.Stat;

/**
 * 信号干扰器（1×1）：在指定信道（1~5，或全信道 ALL）发射压制噪声。
 * 干扰半径内同信道信号源/中继器的信号被压制（H 覆盖中该区域无信号）。
 */
public class SignalJammer extends Block {
    /** 干扰半径（格） */
    public static final float JAM_RADIUS = 12f;
    /** 全信道模式值 */
    public static final int ALL = -1;
    /** 信道范围（1~5，共 5 个信道） */
    public static final int CHANNEL_MAX = 5;

    public SignalJammer(String name) {
        super(name);
        buildType = SignalJammerBuild::new;
        size = 1;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
        config(Integer.class, (SignalJammerBuild b, Integer v) -> b.jamChannel = Math.max(-1, Math.min(CHANNEL_MAX, v)));
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerRange, JAM_RADIUS + " tiles");
    }

    /** 干扰器缓存（每队） */
    private static final ObjectMap<Team, Seq<SignalJammerBuild>> jammerCache = new ObjectMap<>();
    private static boolean dirty = true;

    public static void markDirty() {
        dirty = true;
    }

    static void rebuildCache() {
        if (!dirty) return;
        dirty = false;
        jammerCache.clear();
        for (Building b : Groups.build) {
            if (b instanceof SignalJammerBuild jb) {
                jammerCache.get(jb.team, Seq::new).add(jb);
            }
        }
    }

    /** 某队伍的干扰器列表（走缓存） */
    public static Seq<SignalJammerBuild> allJammers(Team team) {
        rebuildCache();
        return jammerCache.get(team, new Seq<>());
    }

    /** 判断位置 (wx,wy) 是否被同信道（或全信道）干扰器压制 */
    public static boolean jammed(Team team, int channel, float wx, float wy) {
        float rangePx = JAM_RADIUS * 8f;
        for (SignalJammerBuild jb : allJammers(team)) {
            if (jb.jamChannel != ALL && jb.jamChannel != channel) continue;
            if (Mathf.dst(jb.x, jb.y, wx, wy) <= rangePx) return true;
        }
        return false;
    }

    public class SignalJammerBuild extends Building {
        /** 干扰信道（1~5，-1=全信道） */
        public int jamChannel = ALL;

        @Override
        public void onProximityAdded() {
            super.onProximityAdded();
            SignalJammer.markDirty();
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            SignalJammer.markDirty();
        }

        /** 配置面板：信道选择（1~5 + 全信道） */
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            table.add(Core.bundle.get("block.silicon-signal-jammer.channel")).pad(2f);
            table.row();
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            TextButton allBtn = new TextButton(Core.bundle.get("block.silicon-signal-jammer.all"), Styles.flatTogglet);
            allBtn.setChecked(jamChannel == ALL);
            allBtn.clicked(() -> configure(ALL));
            group.add(allBtn);
            table.add(allBtn).size(68f, 40f).pad(2f);
            for (int i = 1; i <= CHANNEL_MAX; i++) {
                TextButton btn = new TextButton(String.valueOf(i), Styles.flatTogglet);
                btn.setChecked(jamChannel == i);
                int ch = i;
                btn.clicked(() -> configure(ch));
                group.add(btn);
                table.add(btn).size(44f, 40f).pad(2f);
            }
        }

        /** 选中显示：干扰信道 */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-jammer.channel.current",
                    jamChannel == ALL ? Core.bundle.get("block.silicon-signal-jammer.all") : String.valueOf(jamChannel))).pad(2f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(jamChannel);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            jamChannel = read.i();
        }
    }
}
