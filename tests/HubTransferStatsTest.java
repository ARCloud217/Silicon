/**
 * 中枢单跳计费与吞吐统计行为测试（独立可编译，不参与模组构建）
 *
 * 用法：javac --release 17 -encoding UTF-8 HubTransferStatsTest.java && java HubTransferStatsTest
 *
 * 镜像 ItemTransferHubBuild 当前实现的关键算法（a0.11.8.x 口径）：<p>
 * 1. chargeOne：每个枢纽只记"通过自己"的一跳（10 电力/件），远端枢走 *Next 延迟一帧<br>
 * 2. 帧首无条件并入并清空 *Next（禁用/断电也不累积，防恢复尖峰）<br>
 * 3. 吞吐滑动窗口（600 tick）与耗电积分的 10:1 对应关系<br>
 * 4. 路径不可达兜底只按端点归属各计一跳<br>
 * 5. 禁用/断电帧瞬时请求清零
 *
 * 测试对象是镜像的纯逻辑模型，不依赖 Mindustry 运行时；修改中枢计费/统计代码后
 * 应同步核对本测试的镜像是否仍与实现一致。
 */
public class HubTransferStatsTest {
    static final float UNIT = 10f;
    static int total = 0, pass = 0;

    /** 极简滑动窗口：容量 cap 个 tick 桶，O(1) 均值。 */
    static class Window {
        final int[] buf; int head = 0, size = 0; long sum = 0;
        Window(int cap) { buf = new int[cap]; }
        void push(int v) {
            if (size == buf.length) { sum -= buf[head]; } else { size++; }
            buf[head] = v; sum += v;
            head = (head + 1) % buf.length;
        }
        float ratePerSecond() { return size == 0 ? 0f : sum / (size / 60f); }
    }

    /** 枢纽模型：逐行镜像 updateTile 帧首折叠/窗口/积分 与 chargeOne。 */
    static class Hub {
        final String name;
        float powerConsumed = 0f, powerConsumedNext = 0f;
        int transferCount = 0, transferCountNext = 0;
        float powerPerSecond = 0f, powerAccumulator = 0f, transferRate = 0f;
        boolean enabled = true;
        float status = 1f;
        final Window window = new Window(600);
        int tick = 0;

        Hub(String name) { this.name = name; }

        /** 镜像 updateTile：帧首折叠（电力赋值/计数累加，刻意不对称）→ 窗口 → 门控 → 积分。 */
        void frame(boolean scheduleFires) {
            // 帧首并入延迟计费/计数：电力【赋值】防膨胀；计数【+=】防覆盖同帧自有件数
            powerConsumed = powerConsumedNext; powerConsumedNext = 0f;
            transferCount += transferCountNext; transferCountNext = 0;
            // 窗口记录上一帧吞吐，随后清零
            window.push(transferCount);
            transferCount = 0;
            tick++;
            // 禁用/断电门控
            if (!enabled || status <= 0f) {
                powerConsumed = 0f; powerConsumedNext = 0f; powerAccumulator = 0f;
                if (tick % 60 == 0) { powerPerSecond = 0f; transferRate = 0f; }
                return;
            }
            // 调度（此处由驱动器直接调用 chargeBatch，节流节奏由外部控制）
            if (scheduleFires) { /* 驱动器已在调用前完成计费 */ }
            // 实际取电积分
            powerAccumulator += powerConsumed * Math.min(status, 1f);
            if (tick % 60 == 0) { powerPerSecond = powerAccumulator; powerAccumulator = 0f; }
            if (tick % 10 == 0) { transferRate = window.ratePerSecond(); }
        }

        /** 单跳计费/计数：本枢直接入账，远端枢写入延迟队列。 */
        void chargeOne(Hub h, int moved) {
            float share = UNIT * moved;
            if (h == this) { h.powerConsumed += share; h.transferCount += moved; }
            else { h.powerConsumedNext += share; h.transferCountNext += moved; }
        }

        /** 镜像 chargeBatch：同枢直转 / 路径不可达兜底（端点各一跳）/ 路径逐枢各一跳。 */
        void chargeBatch(Hub srcHub, Hub dstHub, int moved, Hub[] pathOrNull) {
            if (srcHub == dstHub || pathOrNull == null) {
                if (pathOrNull == null) { chargeOne(srcHub, moved); chargeOne(dstHub, moved); }
                else chargeOne(srcHub, moved);
                return;
            }
            java.util.LinkedHashSet<Hub> seen = new java.util.LinkedHashSet<>();
            for (Hub h : pathOrNull) { if (seen.add(h)) chargeOne(h, moved); }
        }
    }

    static void check(boolean cond, String msg) {
        total++;
        if (cond) { pass++; System.out.println("PASS " + msg); }
        else { System.out.println("FAIL " + msg); }
    }
    static boolean near(float a, float b, float eps) { return Math.abs(a - b) <= eps; }

    public static void main(String[] args) {
        // ========== 场景1：同枢直转，发起枢记自己一跳 ==========
        Hub a = new Hub("A"), b = new Hub("B");
        a.chargeBatch(a, b, 10, new Hub[]{ a });
        check(near(a.powerConsumed, 100f, 0.001f), "场景1 同枢直转：发起枢耗电 = 10×10");
        check(a.transferCount == 10, "场景1 同枢直转：经手件数 = 10");
        check(b.powerConsumedNext == 0f && b.transferCountNext == 0, "场景1 未涉及其它枢纽");

        // ========== 场景2：跨枢 A→B，各自一跳，B 延迟一帧生效（计数入窗口） ==========
        a = new Hub("A"); b = new Hub("B");
        a.chargeBatch(a, b, 10, new Hub[]{ a, b });
        check(near(a.powerConsumed, 100f, 0.001f) && a.transferCount == 10, "场景2 发起枢只记自己一跳");
        check(near(b.powerConsumedNext, 100f, 0.001f) && b.transferCountNext == 10, "场景2 远端枢写入延迟队列");
        b.frame(false);
        check(near(b.powerConsumed, 100f, 0.001f), "场景2 B 下一帧并入计费");
        check(b.transferCount == 0 && b.window.sum == 10, "场景2 计数已入滑动窗口桶");

        // ========== 场景3：三枢链路各计一跳，无重复 ==========
        Hub c = new Hub("C");
        a = new Hub("A"); b = new Hub("B"); c = new Hub("C");
        a.chargeBatch(a, c, 5, new Hub[]{ a, b, c });
        check(near(a.powerConsumed, 50f, 0.001f) && a.transferCount == 5, "场景3 A 一跳 10×5");
        check(near(b.powerConsumedNext, 50f, 0.001f) && b.transferCountNext == 5, "场景3 B 一跳 10×5");
        check(near(c.powerConsumedNext, 50f, 0.001f) && c.transferCountNext == 5, "场景3 C 一跳 10×5");

        // ========== 场景4：路径不可达兜底——端点各一跳，第三方不买单 ==========
        Hub x = new Hub("X"); // 无关第三枢
        a = new Hub("A"); b = new Hub("B");
        a.chargeBatch(a, b, 4, null); // path=null 兜底
        check(near(a.powerConsumed, 40f, 0.001f) && near(b.powerConsumedNext, 40f, 0.001f), "场景4 端点各计一跳");
        check(x.powerConsumedNext == 0f && x.powerConsumed == 0f, "场景4 无关枢纽不被计费");

        // ========== 场景5：6Hz 突发的窗口速率与耗电 10:1 关系 ==========
        Hub relay = new Hub("R");
        Hub src = new Hub("S");
        // 模拟 600 帧：每 10 帧 S 发起一次 10 件、途经 R 的调度（先写延迟量再走帧）
        for (int f = 1; f <= 600; f++) {
            boolean fire = (f % 10 == 0);
            if (fire) {
                src.chargeOne(src, 10);           // 发起枢自己的一跳
                src.chargeOne(relay, 10);         // 中转枢的一跳（写 Next）
            }
            relay.frame(fire);
            src.frame(false);
        }
        check(near(relay.transferRate, 60f, 0.5f), "场景5 中转枢速率 = 60件/秒（600帧×每10帧10件）");
        check(near(relay.powerPerSecond, 600f, 3f), "场景5 中转枢秒耗电 = 600（=10×60）");
        check(near(relay.powerPerSecond, 10f * relay.transferRate, 5f), "场景5 耗电:速率 ≈ 10:1");

        // ========== 场景8：连续帧赋值折叠——无跨帧累加 ==========
        Hub g = new Hub("G");
        for (int f = 0; f < 50; f++) {
            g.powerConsumedNext += 100f;          // 每帧都有远端计费写入
            g.transferCountNext += 10;
            g.frame(true);
        }
        check(near(g.powerConsumed, 100f, 0.001f), "场景8 稳态帧耗电恒为单帧量（100），不随时间膨胀");
        check(g.transferCount == 0, "场景8 帧后计数已入窗口桶并清零");
        long windowSum = g.window.sum;
        check(windowSum == 50L * 10L, "场景8 窗口总量 = 50帧×10件");

        // ========== 场景6：禁用期间被路过——不累积、恢复无尖峰 ==========
        Hub d = new Hub("D");
        d.enabled = false;
        for (int f = 0; f < 120; f++) {
            d.powerConsumedNext += 100f;          // 其它枢持续写入延迟计费
            d.transferCountNext += 10;
            d.frame(false);
        }
        check(d.powerConsumed == 0f && d.powerAccumulator == 0f, "场景6 禁用期请求与积分为零");
        check(d.powerConsumedNext == 0f && d.transferCountNext == 0, "场景6 延迟队列每帧清空不累积");
        d.enabled = true;
        d.frame(true);
        check(d.powerConsumed == 0f, "场景6 恢复首帧无历史尖峰");

        // ========== 场景7：断电帧请求清零 ==========
        Hub e = new Hub("E");
        e.status = 0f;
        e.frame(false);
        check(e.powerConsumed == 0f && e.powerPerSecond == 0f, "场景7 断电帧请求与显示归零");

        // ========== 场景9：发起枢纽同时被路过——自有件数与中转件数都入窗口 ==========
        Hub mix = new Hub("M"), far = new Hub("F");
        for (int f = 1; f <= 600; f++) {
            boolean fire = (f % 10 == 0);
            if (fire) {
                mix.chargeOne(mix, 6);   // M 发起：自己一跳 6 件（仅调度帧）
                mix.chargeOne(far, 4);   // 途经远端 F 4 件
            }
            mix.powerConsumedNext += 40f; // 连续不断的其它枢流量路过 M
            mix.transferCountNext += 4;
            mix.frame(fire);
            far.frame(false);
        }
        check(mix.window.sum == 60L * 6L + 600L * 4L, "场景9 窗口总量 = 调度帧6件×60 + 每帧路过4件×600");
        check(near(mix.transferRate, 276f, 1f), "场景9 速率 = 276件/秒");

        System.out.println("== 结果: " + pass + "/" + total + " 通过 ==");
        if (pass != total) System.exit(1);
    }
}
