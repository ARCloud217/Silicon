package silicon.world.blocks.signal;

import arc.math.Mathf;
import mindustry.game.Team;
import mindustry.gen.Building;

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

    /** 计算结果：有效强度 + 最强同信道源（用于显示颜色） */
    public static class Result {
        public float strength;
        public Building bestSource;
    }

    /** 计算结果（静态复用，避免每格分配；调用方立即读取字段） */
    private static final Result tmp = new Result();

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
        // 干扰器：同信道 + 邻信道泄漏
        float jamSum = SignalJammer.strengthAt(team, ch, wx, wy); // 同信道/全信道
        for (SignalJammer.SignalJammerBuild jb : SignalJammer.allJammers(team)) {
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
