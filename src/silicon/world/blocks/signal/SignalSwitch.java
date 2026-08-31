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

/**
 * 信号控制开关（1×1）：监听指定信道，同信道最强信号值 ≥ 阈值时启用（enabled=true），
 * 否则关闭。作为信号网络的消费端：后续模组方块可读取其 enabled 状态执行逻辑。
 * 选中面板实时显示当前信号值/阈值/开关状态。
 */
public class SignalSwitch extends Block {
    public SignalSwitch(String name) {
        super(name);
        buildType = SignalSwitchBuild::new;
        size = 1;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
        // 信道（0~4）
        config(Integer.class, (SignalSwitchBuild b, Integer v) -> b.channel = Math.max(0, Math.min(SignalNet.CHANNEL_MAX, v)));
        // 阈值（-999~999）
        config(Float.class, (SignalSwitchBuild b, Float v) -> b.threshold = v);
    }

    public class SignalSwitchBuild extends Building {
        /** 监听信道（0~4） */
        public int channel = 0;
        /** 触发阈值：同信道信号值 ≥ 阈值时启用 */
        public float threshold = 1f;
        /** 当前信号值 */
        public float currentValue = 0f;
        /** 是否有信号 */
        public boolean hasSignal = false;
        /** 开关状态（信号 ≥ 阈值） */
        public boolean enabled = false;

        @Override
        public void updateTile() {
            hasSignal = false;
            currentValue = 0f;
            float bestStrength = SignalNet.DEFAULT_SENSITIVITY;
            for (Building b : SignalNet.allSources(team)) {
                if (SignalNet.channelOf(b) != channel) continue;
                // 被干扰器压制则不算
                if (SignalJammer.jammed(team, channel, x, y)) continue;
                float s = SignalNet.strengthAt(b.x, b.y, x, y, SignalNet.powerOf(b));
                if (s > bestStrength) {
                    bestStrength = s;
                    currentValue = SignalNet.valueOf(b);
                    hasSignal = true;
                }
            }
            enabled = hasSignal && currentValue >= threshold;
        }

        /** 配置面板：信道 + 阈值 */
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            table.add(Core.bundle.get("block.silicon-signal-switch.channel")).pad(2f);
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
            table.row();
            table.add(Core.bundle.get("block.silicon-signal-switch.threshold")).padTop(6f).pad(2f);
            table.row();
            Slider slider = new Slider(-999f, 999f, 1f, false);
            slider.setValue(threshold);
            slider.changed(() -> configure(slider.getValue()));
            table.add(slider).width(240f).pad(2f);
            table.row();
            table.add(Core.bundle.format("block.silicon-signal-switch.threshold.current", (int) threshold)).color(arc.graphics.Color.lightGray).pad(2f);
        }

        /** 选中显示：信道、信号值、开关状态 */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            table.add(Core.bundle.format("block.silicon-signal-switch.channel.current", channel)).pad(2f);
            table.row();
            table.add(Core.bundle.format("block.silicon-signal-switch.value.current",
                    hasSignal ? (int) currentValue : Core.bundle.get("block.silicon-signal-switch.nosignal"))).pad(2f);
            table.row();
            table.add(Core.bundle.get(enabled ? "block.silicon-signal-switch.on" : "block.silicon-signal-switch.off"))
                    .color(enabled ? arc.graphics.Color.lime : arc.graphics.Color.lightGray).pad(2f);
        }

        /** 状态显示：启用时绿色状态条（可被其他模组方块引用） */
        @Override
        public void drawStatus() {
            super.drawStatus();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(channel);
            write.f(threshold);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            channel = read.i();
            threshold = read.f();
        }
    }
}
