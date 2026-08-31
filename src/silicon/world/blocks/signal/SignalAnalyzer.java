package silicon.world.blocks.signal;

import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.Stat;

/**
 * 频谱分析仪（1×1）：扫描范围内各信道（0~4）的信号场强与值，选中面板以频谱条实时显示。
 * 用于调试信号网络与定位干扰。
 */
public class SignalAnalyzer extends Block {
    /** 扫描范围（格） */
    public static final int RANGE = 20;

    public SignalAnalyzer(String name) {
        super(name);
        buildType = SignalAnalyzerBuild::new;
        size = 1;
        solid = true;
        destructible = true;
        update = true;
        configurable = false;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerRange, RANGE + " tiles");
    }

    public class SignalAnalyzerBuild extends Building {
        /** 各信道最强信号强度（dBm，显示用） */
        public float[] strengths = new float[SignalNet.CHANNEL_MAX + 1];
        /** 各信道最强信号值 */
        public float[] values = new float[SignalNet.CHANNEL_MAX + 1];

        @Override
        public void updateTile() {
            for (int ch = 0; ch <= SignalNet.CHANNEL_MAX; ch++) {
                strengths[ch] = SignalNet.DEFAULT_SENSITIVITY;
                values[ch] = 0f;
                for (Building b : SignalNet.allSources(team)) {
                    if (SignalNet.channelOf(b) != ch) continue;
                    // 只统计扫描范围内
                    if (Mathf.dst(b.x, b.y, x, y) > RANGE * 8f) continue;
                    float s = SignalNet.strengthAt(b.x, b.y, x, y, SignalNet.powerOf(b));
                    if (s > strengths[ch]) {
                        strengths[ch] = s;
                        values[ch] = SignalNet.valueOf(b);
                    }
                }
            }
        }

        /** 选中面板：各信道频谱条（强度 + 值；被干扰标记红色） */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            for (int ch = 0; ch <= SignalNet.CHANNEL_MAX; ch++) {
                final int c = ch;
                table.add(new Bar(
                        () -> "CH" + c + ": " + (strengths[c] > SignalNet.DEFAULT_SENSITIVITY ? (int) values[c] : Core.bundle.get("block.silicon-signal-analyzer.nosignal")),
                        () -> SignalJammer.jammed(team, c, x, y) ? Pal.remove
                                : (strengths[c] > SignalNet.DEFAULT_SENSITIVITY ? Pal.accent : Pal.gray),
                        () -> SignalJammer.jammed(team, c, x, y) ? 1f
                                : (strengths[c] > SignalNet.DEFAULT_SENSITIVITY ? Mathf.clamp((strengths[c] + 100f) / 110f) : 0f)))
                        .height(16f).growX();
                table.row();
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
        }
    }
}
