package silicon.world.blocks.signal;

import arc.math.Mathf;
import mindustry.game.Team;
import mindustry.gen.Building;
import silicon.util.SatelliteManager;

/**
 * 信道信号统一计算（干扰模型 1~7、13）：
 * - 环境底噪/热噪声（含噪声系数）：固定底噪 N0，信号低于视为无信号
 * - 同信道干扰（CCI）：最强源为目标，其余同信道源强度之和为干扰
 * - 邻信道干扰（ACI）：其他信道源强度 × ACIR 泄漏系数
 * - 同信道/全信道干扰器：干扰强度（与信号同模型衰减）直接叠加
 * - 邻信道干扰器泄漏：干扰器对邻信道的泄漏（ACIR_jam）
 * 有效信号 = 最强信号 − 干扰总量；≤ 0 → 无信号。
 */
public class SignalChannel {
    /** 底噪（强度域 0~15，含噪声系数；低于此视为无信号） */
    public static final float NOISE_FLOOR = 0.5f;
    /** 邻信道泄漏系数 ACIR：Δch=0 → 1（本信道），1 → 0.25，2 → 0.08，≥3 → 0（忽略） */
    public static float acir(int dch) {
        int d = Math.abs(dch);
        if (d == 0) return 1f;
        if (d == 1) return 0.25f;
        if (d == 2) return 0.08f;
        return 0f;
    }

    /** 干扰器邻信道泄漏系数（比发射器略大）：Δch=1 → 0.4，2 → 0.12，≥3 → 0 */
    public static float acirJam(int dch) {
        int d = Math.abs(dch);
        if (d == 0) return 1f;
        if (d == 1) return 0.4f;
        if (d == 2) return 0.12f;
        return 0f;
    }

    /** 卫星归属信号所在信道（按归属信号编码找信号源；无归属或源不存在返回 -1） */
    public static int satelliteChannel(Team team) {
        String sig = SatelliteManager.satelliteSignal(team);
        if (sig == null) return -1;
        for (SignalSource.SignalSourceBuild sb : SignalSource.allSources(team)) {
            if (sb.signal != null && sig.equals(sb.signal.name)) return sb.channel;
        }
        return -1;
    }

    /**
     * (wx,wy) 处是否处于指定信号 name 的"信号范围"内。
     * 同一编码视为同一信号：卫星全图广播、信号源自身覆盖、同编码激活中继器的级联延伸，
     * 三者广播的有效范围取并集。
     * 供卫星控制台 ↔ 卫星发射中枢绑定判定（控制台与中枢必须同处该信号范围内）。
     */
    public static boolean inSignalRange(Team team, String name, float wx, float wy) {
        if (name == null || name.isEmpty()) return false;
        // 卫星：归属该编码的卫星信号为全图广播（发射过即仍在轨广播），覆盖任意位置
        if (name.equals(SatelliteManager.satelliteSignal(team))) {
            return true;
        }
        for (SignalSource.SignalSourceBuild sb : SignalSource.allSources(team)) {
            if (sb.signal != null && name.equals(sb.signal.name)
                    && SignalSource.strengthAt(sb.x, sb.y, wx, wy) > 0f) {
                return true;
            }
        }
        for (SignalRelay.SignalRelayBuild rb : SignalRelay.allRelays(team)) {
            if (rb.active && name.equals(rb.selectedSource) && rb.strengthAt(wx, wy) > 0f) {
                return true;
            }
        }
        return false;
    }

    /** 计算结果：有效强度 + 最强同信道源（用于显示颜色） */
    public static class Result {
        public float strength;
        public Building bestSource;
    }

    /** 计算结果（静态复用，避免每格分配；调用方立即读取字段） */
    private static final Result tmp = new Result();

    // —— 每信道批量计算（覆盖绘制用）：静态缓冲，一次遍历全部源按信道分摊 ——
    private static final float[] bestA = new float[SignalJammer.CHANNEL_MAX + 1];
    private static final Building[] bestSrcA = new Building[SignalJammer.CHANNEL_MAX + 1];
    private static final String[] bestIdA = new String[SignalJammer.CHANNEL_MAX + 1];
    private static final float[] otherA = new float[SignalJammer.CHANNEL_MAX + 1];
    private static final float[] aciA = new float[SignalJammer.CHANNEL_MAX + 1];
    private static final float[] jamA = new float[SignalJammer.CHANNEL_MAX + 1];

    /** 将某源信号按信道分摊：本信道按身份计入 best/other，邻信道计入 ACI */
    private static void addSource(int ch, float s, String id, Building src) {
        if (ch < 1 || ch > SignalJammer.CHANNEL_MAX) return;
        if (ch > 1) aciA[ch - 1] += s * acir(1);
        if (ch < SignalJammer.CHANNEL_MAX) aciA[ch + 1] += s * acir(1);
        if (ch > 2) aciA[ch - 2] += s * acir(2);
        if (ch < SignalJammer.CHANNEL_MAX - 1) aciA[ch + 2] += s * acir(2);
        // 本信道：同身份取最强不互扰，不同身份计 CCI
        if (id.equals(bestIdA[ch])) {
            if (s > bestA[ch]) {
                bestA[ch] = s;
                bestSrcA[ch] = src;
            }
        } else if (s > bestA[ch]) {
            otherA[ch] += bestA[ch];
            bestA[ch] = s;
            bestIdA[ch] = id;
            bestSrcA[ch] = src;
        } else {
            otherA[ch] += s;
        }
    }

    /**
     * 批量计算位置 (wx,wy) 所有信道（1~5）的有效信号强度。
     * 一次遍历全部信号源/中继器/干扰器，按信道分摊（含底噪/CCI/ACI/干扰器），
     * 结果写入 effOut[1..5] 与 srcOut[1..5]（最强同信道源，用于颜色）。
     * 比逐信道调用 effective 快约 5 倍（覆盖绘制用）。
     */
    public static void effectiveAll(Team team, float wx, float wy, float[] effOut, Building[] srcOut) {
        for (int ch = 1; ch <= SignalJammer.CHANNEL_MAX; ch++) {
            bestA[ch] = 0f;
            bestSrcA[ch] = null;
            bestIdA[ch] = null;
            otherA[ch] = 0f;
            aciA[ch] = 0f;
            jamA[ch] = 0f;
        }
        // 信号源
        for (SignalSource.SignalSourceBuild sb : SignalSource.allSources(team)) {
            float s = sb.strengthAt(wx, wy);
            if (s <= 0f) continue;
            addSource(sb.channel, s, "S" + sb.signal.name, sb);
        }
        // 激活中继器（级联源；发射信道与所选信号源一致）
        for (SignalRelay.SignalRelayBuild rb : SignalRelay.allRelays(team)) {
            if (!rb.active) continue;
            float s = rb.strengthAt(wx, wy);
            if (s <= 0f) continue;
            String id = (rb.selectedSource != null && !rb.selectedSource.isEmpty())
                    ? "S" + rb.selectedSource : "R" + ((int) rb.x * 7 + (int) rb.y * 13);
            addSource(rb.signalChannel(), s, id, rb);
        }
        // 干扰器（全局：敌方干扰器同样压制本信道；同信道 + 邻信道泄漏）
        for (SignalJammer.SignalJammerBuild jb : SignalJammer.allJammers()) {
            float j = SignalSource.strengthAt(jb.x, jb.y, wx, wy);
            if (jb.jamChannel == SignalJammer.ALL) {
                for (int ch = 1; ch <= SignalJammer.CHANNEL_MAX; ch++) jamA[ch] += j;
            } else {
                int c = jb.jamChannel;
                jamA[c] += j;
                if (c > 1) jamA[c - 1] += j * acirJam(1);
                if (c < SignalJammer.CHANNEL_MAX) jamA[c + 1] += j * acirJam(1);
                if (c > 2) jamA[c - 2] += j * acirJam(2);
                if (c < SignalJammer.CHANNEL_MAX - 1) jamA[c + 2] += j * acirJam(2);
            }
        }
        // 每信道有效信号 = 最强 − (底噪 + CCI + ACI + 干扰器)
        for (int ch = 1; ch <= SignalJammer.CHANNEL_MAX; ch++) {
            effOut[ch] = Math.max(0f, bestA[ch] - (NOISE_FLOOR + otherA[ch] + aciA[ch] + jamA[ch]));
            srcOut[ch] = bestSrcA[ch];
        }
    }

    /**
     * 计算位置 (wx,wy) 在信道 ch 的有效信号强度（0~15）。
     * 遍历本队信号源与激活中继器：
     * - 同信道：最强为目标，其余之和为 CCI
     * - 邻信道：强度 × ACIR 为泄漏干扰
     * - 干扰器：同信道强度 + 邻信道泄漏，直接叠加
     */
    public static Result effective(Team team, int ch, float wx, float wy) {
        float best = 0f;
        Building bestSrc = null;
        String bestId = null; // 最强信号的来源身份（同身份不互扰：信号源与绑定它的中继器视为同一信号）
        float otherSum = 0f; // 同信道其他源（CCI，仅不同身份之间）
        float aciSum = 0f;   // 邻信道泄漏
        // 信号源
        for (SignalSource.SignalSourceBuild sb : SignalSource.allSources(team)) {
            float s = sb.strengthAt(wx, wy); // 原始信号强度（含断电检查）
            if (s <= 0f) continue;
            int dch = Math.abs(sb.channel - ch);
            if (dch == 0) {
                String id = "S" + sb.signal.name; // 信号源身份 = 自身编码
                if (id.equals(bestId)) {
                    // 同一信号身份（如本源的级联中继器）：取最强，不互相干扰
                    if (s > best) {
                        best = s;
                        bestSrc = sb;
                    }
                } else if (s > best) {
                    otherSum += best;
                    best = s;
                    bestId = id;
                    bestSrc = sb;
                } else {
                    otherSum += s;
                }
            } else {
                aciSum += s * acir(dch);
            }
        }
        // 激活中继器（级联源；发射信道与所选信号源一致）
        for (SignalRelay.SignalRelayBuild rb : SignalRelay.allRelays(team)) {
            if (!rb.active) continue;
            float s = rb.strengthAt(wx, wy);
            if (s <= 0f) continue;
            int dch = Math.abs(rb.signalChannel() - ch);
            if (dch == 0) {
                // 中继器身份 = 所选信号源编码（绑定）；未绑定用自身坐标
                String id = (rb.selectedSource != null && !rb.selectedSource.isEmpty()) ? "S" + rb.selectedSource : "R" + ((int) rb.x * 7 + (int) rb.y * 13);
                if (id.equals(bestId)) {
                    // 同身份（信号源或其级联中继器）：取最强，不互相干扰
                    if (s > best) {
                        best = s;
                        bestSrc = rb;
                    }
                } else if (s > best) {
                    otherSum += best;
                    best = s;
                    bestId = id;
                    bestSrc = rb;
                } else {
                    otherSum += s;
                }
            } else {
                aciSum += s * acir(dch);
            }
        }
        // 干扰器（全局：不分队伍）：同信道 + 邻信道泄漏
        float jamSum = SignalJammer.strengthAt(ch, wx, wy); // 同信道/全信道
        for (SignalJammer.SignalJammerBuild jb : SignalJammer.allJammers()) {
            if (jb.jamChannel == SignalJammer.ALL || jb.jamChannel == ch) continue; // 同信道已在 jamSum
            float s = SignalSource.strengthAt(jb.x, jb.y, wx, wy);
            jamSum += s * acirJam(jb.jamChannel - ch);
        }
        // 干扰总量 = 底噪 + CCI + ACI + 干扰器
        float interference = NOISE_FLOOR + otherSum + aciSum + jamSum;
        tmp.strength = Math.max(0f, best - interference);
        tmp.bestSource = bestSrc;
        return tmp;
    }
}
