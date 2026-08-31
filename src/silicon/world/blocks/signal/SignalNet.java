package silicon.world.blocks.signal;

import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.serialization.Json;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import silicon.Silicon;

/**
 * 信号网络（阶段 1~3）：信道化数据通信的传播模型、源缓存与外部 API。
 * - 功率档位（dBm）：低/中/高（可配置，默认 10/20/30）
 * - 传播：P_rx = P_tx - 20*log10(d格+1) - 每格实体障碍损耗
 * - 配置：mod 根 signal-config.json（可选）
 * - 外部 API：valueAt / hasSignalAt 供其他模组查询
 */
public class SignalNet {
    /** 功率档位（默认，可由 signal-config.json 覆盖） */
    public static int P_LOW = 10, P_MED = 20, P_HIGH = 30;
    /** 每格实体障碍损耗（dB） */
    public static float PER_SOLID_DB = 5f;
    /** 默认接收灵敏度（dBm） */
    public static float DEFAULT_SENSITIVITY = -90f;
    /** 干扰半径（格） */
    public static float JAM_RADIUS = 12f;
    /** 信道范围（0~4，共 5 个信道） */
    public static final int CHANNEL_MAX = 4;

    /** 配置结构（signal-config.json） */
    public static class Config {
        public int powerLow = 10, powerMed = 20, powerHigh = 30;
        public float perSolidDb = 5f;
        public float defaultSensitivity = -90f;
        public float jamRadius = 12f;
    }

    /** 从 mod 根 signal-config.json 加载配置（可选；文件缺失时用默认值） */
    public static void loadConfig() {
        try {
            Fi file = Vars.mods.getMod(Silicon.class).root.child("signal-config.json");
            if (!file.exists()) return;
            Config c = new Json().fromJson(Config.class, file.readString());
            P_LOW = c.powerLow;
            P_MED = c.powerMed;
            P_HIGH = c.powerHigh;
            PER_SOLID_DB = c.perSolidDb;
            DEFAULT_SENSITIVITY = c.defaultSensitivity;
            JAM_RADIUS = c.jamRadius;
            Log.info("SignalNet: config loaded (P=@/@/@ dBm, solid=@dB, sens=@dBm, jam=@t)",
                    P_LOW, P_MED, P_HIGH, PER_SOLID_DB, DEFAULT_SENSITIVITY, JAM_RADIUS);
        } catch (Exception e) {
            Log.err("SignalNet: config load failed: @", e);
        }
    }

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

    /** 障碍物损耗：发射器→接收器直线路径上实体格数 × 每格损耗（像素步进 4px 采样） */
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

    /** —— 外部查询 API（其他模组可调用） —— */

    /** 查询某位置某信道是否有有效信号（未受干扰且强度高于灵敏度） */
    public static boolean hasSignalAt(Team team, int channel, float wx, float wy) {
        if (SignalJammer.jammed(team, channel, wx, wy)) return false;
        return strengthAt(team, channel, wx, wy) > DEFAULT_SENSITIVITY;
    }

    /** 查询某位置某信道的最强信号值（无信号返回 0） */
    public static float valueAt(Team team, int channel, float wx, float wy) {
        float best = DEFAULT_SENSITIVITY;
        float val = 0f;
        for (Building b : allSources(team)) {
            if (channelOf(b) != channel) continue;
            float s = strengthAt(b.x, b.y, wx, wy, powerOf(b));
            if (s > best) {
                best = s;
                val = valueOf(b);
            }
        }
        return val;
    }

    /** 查询某位置某信道的最强信号强度（dBm；无信号返回灵敏度值） */
    public static float strengthAt(Team team, int channel, float wx, float wy) {
        float best = DEFAULT_SENSITIVITY;
        for (Building b : allSources(team)) {
            if (channelOf(b) != channel) continue;
            float s = strengthAt(b.x, b.y, wx, wy, powerOf(b));
            if (s > best) best = s;
        }
        return best;
    }
}
