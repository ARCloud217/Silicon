package silicon.world.blocks.signal;

import arc.Core;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.Stat;

/**
 * 信号中继器（1×1）：监听指定信道（0~4），当同信道存在有效信号源（发射器或其他激活中继器）
 * 到达本位置强度高于灵敏度时激活，作为信号源以增益增强后转发（值继承最强来源）。
 * 级联传播：逐级激活可延伸信号覆盖。
 */
public class SignalRepeater extends Block {
    /** 中继器增益（dB） */
    public static final float GAIN = 10f;

    public SignalRepeater(String name) {
        super(name);
        buildType = SignalRepeaterBuild::new;
        size = 1;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
        // 信道（0~4）
        config(Integer.class, (SignalRepeaterBuild b, Integer v) -> b.channel = Math.max(0, Math.min(SignalNet.CHANNEL_MAX, v)));
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerRange, GAIN + " dB gain");
    }

    public class SignalRepeaterBuild extends Building implements SignalNet.SignalSourceI {
        /** 中继信道（0~4） */
        public int channel = 0;
        /** 是否已激活（在有效信号范围内，转发中） */
        public boolean active = false;
        /** 转发值（继承最强来源） */
        public float relayValue = 0f;
        private int timer = 0;

        @Override
        public void onProximityAdded() {
            super.onProximityAdded();
            SignalNet.markDirty();
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            SignalNet.markDirty();
        }

        @Override
        public void updateTile() {
            // 每 20 tick 更新一次激活状态（级联传播：逐级激活）
            if (++timer >= 20) {
                timer = 0;
                updateActive();
            }
        }

        void updateActive() {
            boolean newActive = false;
            float bestStrength = SignalNet.DEFAULT_SENSITIVITY;
            float bestValue = 0f;
            // 被干扰器压制（同信道/全信道）时无法转发
            if (SignalJammer.jammed(team, channel, x, y)) {
                if (active) {
                    active = false;
                    SignalNet.markDirty();
                }
                return;
            }
            // 遍历本队信号源（发射器 + 其他激活中继器）
            for (Building b : SignalNet.allSources(team)) {
                if (b == this) continue;
                if (SignalNet.channelOf(b) != channel) continue;
                float s = SignalNet.strengthAt(b.x, b.y, x, y, SignalNet.powerOf(b));
                if (s > bestStrength) {
                    bestStrength = s;
                    bestValue = SignalNet.valueOf(b);
                    newActive = true;
                }
            }
            if (newActive != active) {
                active = newActive;
                SignalNet.markDirty();
            }
            if (active) {
                relayValue = bestValue;
            }
        }

        @Override
        public int signalChannel() {
            return channel;
        }

        @Override
        public float signalValue() {
            return active ? relayValue : 0f;
        }

        @Override
        public float signalPower() {
            return SignalNet.P_LOW + GAIN; // 中继器以增强后功率转发
        }

        /** 配置面板：信道选择（0~4） */
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            table.add(Core.bundle.get("block.silicon-signal-repeater.channel")).pad(2f);
            table.row();
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            for (int i = 0; i <= SignalNet.CHANNEL_MAX; i++) {
                TextButton btn = new TextButton(String.valueOf(i), Styles.flatTogglet);
                btn.setChecked(channel == i);
                int ch = i;
                btn.clicked(() -> configure(ch));
                group.add(btn);
                table.add(btn).size(48f, 40f).pad(2f);
            }
        }

        /** 选中显示：信道、激活状态、转发值（动态刷新） */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-repeater.channel.current", channel)).pad(2f);
            table.row();
            Label stateLabel = new Label(() -> Core.bundle.get(active ? "block.silicon-signal-repeater.active" : "block.silicon-signal-repeater.inactive"));
            stateLabel.update(() -> stateLabel.setColor(active ? arc.graphics.Color.lime : arc.graphics.Color.lightGray));
            table.add(stateLabel).pad(2f);
            table.row();
            table.label(() -> active
                    ? Core.bundle.format("block.silicon-signal-repeater.value", (int) relayValue)
                    : "").pad(2f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(channel);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            channel = read.i();
        }
    }
}
