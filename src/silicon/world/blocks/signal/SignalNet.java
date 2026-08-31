package silicon.world.blocks.signal;

import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;

/**
 * 信号网络（阶段 1）：信道化数据通信的传播模型与源缓存。
 * - 功率档位（dBm）：低 10 / 中 20 / 高 30
 * - 传播：P_rx = P_tx - 20*log10(d格+1) - 5dB × 实体障碍格数
 * - 源缓存：每队信号源（发射器 + 激活中继器）列表，建筑增删时脏标记重建
 */
public class SignalNet {
    /** 功率档位 */
    public static final int P_LOW = 10, P_MED = 20, P_HIGH = 30;
    /** 每格实体障碍损耗（dB） */
    public static final float PER_SOLID_DB = 5f;
    /** 默认接收灵敏度（dBm） */
    public static final float DEFAULT_SENSITIVITY = -90f;
    /** 信道范围（0~4，共 5 个信道） */
    public static final int CHANNEL_MAX = 4;

    /** 信号源接口：发射器与激活中继器共用 */
    public interface SignalSourceI {
        int signalChannel();
        float signalValue();
        float signalPower();
    }

    /** 源缓存（每队）：发射器 + 激活中继器 */
    private static final ObjectMap<Team, Seq<Building>> sourceCache = new ObjectMap<>();
    private static boolean dirty = true;

    /** 标记缓存失效（建筑增删时调用） */
    public static void markDirty() {
        dirty = true;
    }

    static void rebuildCache() {
        if (!dirty) return;
        dirty = false;
        sourceCache.clear();
        for (Building b : Groups.build) {
            if (b instanceof SignalSourceI && (b instanceof SignalTransmitter.SignalTransmitterBuild
                    || (b instanceof SignalRepeater.SignalRepeaterBuild rb && rb.active))) {
                sourceCache.get(b.team, Seq::new).add(b);
            }
        }
    }

    /** 某队伍的信号源列表（发射器 + 激活中继器；走缓存） */
    public static Seq<Building> allSources(Team team) {
        rebuildCache();
        return sourceCache.get(team, new Seq<>());
    }

    /** 某源的信道 */
    public static int channelOf(Building b) {
        return ((SignalSourceI) b).signalChannel();
    }

    /** 某源发送的值 */
    public static float valueOf(Building b) {
        return ((SignalSourceI) b).signalValue();
    }

    /** 某源功率（dBm） */
    public static float powerOf(Building b) {
        return ((SignalSourceI) b).signalPower();
    }

    /**
     * 接收信号强度（dBm）：P_tx - 20*log10(d格+1) - 障碍损耗。
     * 坐标均为像素（1 格 = 8px）。
     */
    public static float strengthAt(float tx, float ty, float rx, float ry, float powerDbm) {
        float dist = Mathf.dst(tx, ty, rx, ry) / 8f; // 格
        float lfs = 20f * (float) Math.log10(dist + 1f);
        return powerDbm - lfs - obstacleLoss(tx, ty, rx, ry);
    }

    /** 障碍物损耗：发射器→接收器直线路径上实体格数 × 每格 5dB（像素步进 4px 采样） */
    static float obstacleLoss(float x1, float y1, float x2, float y2) {
        float dist = Mathf.dst(x1, y1, x2, y2);
        int steps = Math.max(1, (int) (dist / 4f));
        int solidCount = 0;
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            float wx = Mathf.lerp(x1, x2, t);
            float wy = Mathf.lerp(y1, y2, t);
            if (Vars.world.solid((int) (wx / 8f), (int) (wy / 8f))) solidCount++;
        }
        return solidCount * PER_SOLID_DB;
    }
}
