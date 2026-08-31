package silicon.world.blocks.signal;

import arc.Core;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Slider;
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
 * 信号发射器（1×1）：配置信道（0~4）、功率档位（低/中/高）与发送值（-999~999），
 * 以该信道广播数字信号（距离衰减 + 实体障碍损耗）。是信号网络的信号源。
 */
public class SignalTransmitter extends Block {
    public SignalTransmitter(String name) {
        super(name);
        buildType = SignalTransmitterBuild::new;
        size = 1;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
        // 信道（0~4）
        config(Integer.class, (SignalTransmitterBuild b, Integer v) -> b.channel = Math.max(0, Math.min(SignalNet.CHANNEL_MAX, v)));
        // 功率档位（"p0/p1/p2"）
        config(String.class, (SignalTransmitterBuild b, String v) -> {
            if (v != null && v.startsWith("p")) {
                int idx = Integer.parseInt(v.substring(1));
                b.power = idx <= 0 ? SignalNet.P_LOW : (idx >= 2 ? SignalNet.P_HIGH : SignalNet.P_MED);
            }
        });
        // 发送值
        config(Float.class, (SignalTransmitterBuild b, Float v) -> b.value = v);
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerRange, "10/20/30 dBm");
    }

    public class SignalTransmitterBuild extends Building implements SignalNet.SignalSourceI {
        /** 信道（0~4） */
        public int channel = 0;
        /** 功率档位（dBm：10/20/30） */
        public int power = SignalNet.P_MED;
        /** 发送值 */
        public float value = 0f;

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
        public int signalChannel() {
            return channel;
        }

        @Override
        public float signalValue() {
            return value;
        }

        @Override
        public float signalPower() {
            return power;
        }

        /** 配置面板：信道 + 功率 + 发送值 */
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            // 信道选择（0~4 共 5 个）
            table.add(Core.bundle.get("block.silicon-signal-transmitter.channel")).pad(2f);
            table.row();
            ButtonGroup<TextButton> chGroup = new ButtonGroup<>();
            for (int i = 0; i <= SignalNet.CHANNEL_MAX; i++) {
                TextButton btn = new TextButton(String.valueOf(i), Styles.flatTogglet);
                btn.setChecked(channel == i);
                int ch = i;
                btn.clicked(() -> configure(ch));
                chGroup.add(btn);
                table.add(btn).size(48f, 40f).pad(2f);
            }
            table.row();
            // 功率档位
            table.add(Core.bundle.get("block.silicon-signal-transmitter.power")).padTop(6f).pad(2f);
            table.row();
            ButtonGroup<TextButton> pGroup = new ButtonGroup<>();
            String[] powerNames = {Core.bundle.get("block.silicon-signal-transmitter.power.low"),
                    Core.bundle.get("block.silicon-signal-transmitter.power.med"),
                    Core.bundle.get("block.silicon-signal-transmitter.power.high")};
            for (int i = 0; i < 3; i++) {
                TextButton btn = new TextButton(powerNames[i], Styles.flatTogglet);
                btn.setChecked(power == (i == 0 ? SignalNet.P_LOW : (i == 2 ? SignalNet.P_HIGH : SignalNet.P_MED)));
                int idx = i;
                btn.clicked(() -> configure("p" + idx));
                pGroup.add(btn);
                table.add(btn).size(80f, 40f).pad(2f);
            }
            table.row();
            // 发送值（-999~999）
            table.add(Core.bundle.get("block.silicon-signal-transmitter.value")).padTop(6f).pad(2f);
            table.row();
            Slider slider = new Slider(-999f, 999f, 1f, false);
            slider.setValue(value);
            slider.changed(() -> configure(slider.getValue()));
            table.add(slider).width(240f).pad(2f);
            table.row();
            // 实时显示当前发送值（动态 label，slider 拖动即时刷新）
            table.label(() -> Core.bundle.format("block.silicon-signal-transmitter.value.current", (int) value))
                    .color(arc.graphics.Color.lightGray).pad(2f);
        }

        /** 选中显示：信道、功率、发送值（动态刷新） */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-transmitter.channel.current", channel)).pad(2f);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-transmitter.power.current", power)).pad(2f);
            table.row();
            table.label(() -> Core.bundle.format("block.silicon-signal-transmitter.value.current", (int) value)).pad(2f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(channel);
            write.i(power);
            write.f(value);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            channel = read.i();
            power = read.i();
            value = read.f();
        }
    }
}
