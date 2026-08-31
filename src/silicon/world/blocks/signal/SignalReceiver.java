package silicon.world.blocks.signal;

import arc.Core;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 信号接收器（1×1）：监听指定信道（0~4），计算所有同信道信号源到达本位置的强度
 * （距离衰减 + 实体障碍损耗），取最强信号；强度高于灵敏度时输出该信号的值，
 * 否则保持无信号（输出 0）。选中面板显示当前信号强度与输出值。
 */
public class SignalReceiver extends Block {
    public SignalReceiver(String name) {
        super(name);
        buildType = SignalReceiverBuild::new;
        size = 1;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
        // 信道（0~4）
        config(Integer.class, (SignalReceiverBuild b, Integer v) -> b.channel = Math.max(0, Math.min(SignalNet.CHANNEL_MAX, v)));
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerRange, "-90 dBm (sensitivity)");
    }

    public class SignalReceiverBuild extends Building {
        /** 监听信道（0~4） */
        public int channel = 0;
        /** 接收灵敏度（dBm） */
        public float sensitivity = SignalNet.DEFAULT_SENSITIVITY;
        /** 是否收到有效信号 */
        public boolean hasSignal = false;
        /** 输出值（最强信号源的值） */
        public float outputValue = 0f;
        /** 当前最强信号强度（dBm，显示用） */
        public float signalStrength = 0f;

        @Override
        public void updateTile() {
            hasSignal = false;
            outputValue = 0f;
            signalStrength = sensitivity;
            // 被干扰器压制（同信道/全信道）时收不到信号
            if (SignalJammer.jammed(team, channel, x, y)) return;
            for (Building b : SignalNet.allSources(team)) {
                if (SignalNet.channelOf(b) != channel) continue;
                float s = SignalNet.strengthAt(b.x, b.y, x, y, SignalNet.powerOf(b));
                if (s > signalStrength) {
                    signalStrength = s;
                    outputValue = SignalNet.valueOf(b);
                    hasSignal = true;
                }
            }
        }

        /** 配置面板：信道选择（0~4） */
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            table.add(Core.bundle.get("block.silicon-signal-receiver.channel")).pad(2f);
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

        /** 选中显示：信道、信号强度、输出值（动态刷新） */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-receiver.channel.current", channel)).pad(2f);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-receiver.strength",
                    hasSignal ? (int) signalStrength : Core.bundle.get("block.silicon-signal-receiver.nosignal"))).pad(2f);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-receiver.output",
                    hasSignal ? (int) outputValue : Core.bundle.get("block.silicon-signal-receiver.nosignal"))).pad(2f);
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
