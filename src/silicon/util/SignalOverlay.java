package silicon.util;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.scene.ui.Label;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import silicon.world.blocks.signal.SignalRelay;
import silicon.world.blocks.signal.SignalRelay.SignalRelayBuild;
import silicon.world.blocks.signal.SignalSource;
import silicon.world.blocks.signal.SignalSource.SignalSourceBuild;
import silicon.util.SatelliteManager;

/**
 * 信号覆盖显示：H 键查看信号源覆盖。
 * 无论设置开关如何，按住 H 键始终显示信号强度；
 * 设置开启「切换」时，按一下 H 键可切换显示/隐藏（按住仍优先显示）。
 * 进入显示模式时屏幕下方中间显示一行提示小字。
 * 缩放视角较小时（视野 &gt; 阈值）逐格以数字显示信号强度；
 * 缩放视角较大时以绿色显示信号范围（强度随距离变淡），无信号显示为灰色。
 */
public class SignalOverlay {
    /** 信号显示颜色渐变：高强度=深蓝，低强度=浅蓝（不同强度颜色差异明显） */
    public static final Color DEEP_BLUE = Color.valueOf("1e4fb0");
    public static final Color LIGHT_BLUE = Color.valueOf("9dc3ff");
    /** 信号源选中/放置预览的范围圆颜色（深蓝） */
    public static final Color SIGNAL_COLOR = Color.valueOf("3a6fe0");
    /** 无信号颜色（灰色） */
    public static final Color NO_SIGNAL_COLOR = Color.valueOf("9a9a9a");
    /** 信号源区分色板（备用：无编码时兜底；有编码时按 HSV 色相生成，颜色数量不限） */
    public static final Color[] SOURCE_COLORS = {
            Color.valueOf("e05555"), // 红
            Color.valueOf("e08a3a"), // 橙
            Color.valueOf("e0c43a"), // 黄
            Color.valueOf("9ec42a"), // 黄绿
            Color.valueOf("5fb04c"), // 绿
            Color.valueOf("2fbf8f"), // 青绿
            Color.valueOf("3ac0c0"), // 青
            Color.valueOf("3aa8e0"), // 天蓝
            Color.valueOf("4a6fe0"), // 蓝
            Color.valueOf("6a4ae0"), // 紫蓝
            Color.valueOf("8a4ae0"), // 紫
            Color.valueOf("bf4ae0"), // 品红
            Color.valueOf("e04a9a"), // 粉
            Color.valueOf("d07a4a"), // 棕
            Color.valueOf("9a9a9a"), // 灰
            Color.valueOf("c0c0c0"), // 银
    };
    /** 信号专属颜色缓存（编码 → Color），避免每帧分配 */
    private static final ObjectMap<String, Color> colorCache = new ObjectMap<>();
    /** 已分配的色相（度），用于为新信号选择与已有颜色差异最大的色相（保证颜色明显不同） */
    private static final Seq<Float> usedHues = new Seq<>();
    /** 缩放阈值（相机视野宽度，像素）：视野宽于该值（缩小视角）显示蓝色范围，否则显示数字 */
    public static final float ZOOM_THRESHOLD_WIDTH = 600f;
    /** 预计算的强度数字字符串（0~15），避免每帧分配 */
    private static final String[] NUMBER_STRINGS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"};

    /** 信号专属颜色：色相动态分配——新编码选择与所有已用颜色色相距离最大的色相（贪心最远点，保证明显可辨），
     *  饱和度 0.85、亮度固定 0.75（鲜艳、非灰黑白，不受背景影响——颜色只由色相区分）；结果缓存复用 */
    public static Color signalColor(String code) {
        Color cached = colorCache.get(code);
        if (cached != null) return cached;
        // 贪心最远点：遍历候选色相（5° 步进），选与已用色相环距离最小者最大化的候选
        float hue;
        if (usedHues.isEmpty()) {
            int h0 = code.hashCode() & 0x7fffffff;
            hue = h0 % 360f;
        } else {
            float bestHue = 0f, bestMin = -1f;
            for (float cand = 0f; cand < 360f; cand += 5f) {
                float minDist = 360f;
                for (int i = 0; i < usedHues.size; i++) {
                    float d = Math.abs(cand - usedHues.get(i));
                    if (d > 180f) d = 360f - d; // 色相环最短距离
                    if (d < minDist) minDist = d;
                }
                if (minDist > bestMin) {
                    bestMin = minDist;
                    bestHue = cand;
                }
            }
            hue = bestHue;
        }
        usedHues.add(hue);
        // 固定亮度（0.75）与饱和度（0.85）：颜色差异完全由色相保证，不受地形背景影响
        Color c = Color.HSVtoRGB(hue, 0.85f, 0.75f);
        colorCache.put(code, c);
        return c;
    }

    /** 信号源颜色：基于信号编码生成（同一信号源颜色稳定） */
    public static Color sourceColor(SignalSourceBuild sb) {
        if (sb.signal == null) return SOURCE_COLORS[0];
        return signalColor(sb.signal.name);
    }

    /** 中继器颜色：基于世界坐标生成（标识不同转发来源，稳定） */
    public static Color relayColor(SignalRelayBuild rb) {
        return signalColor("R" + ((int) rb.x * 7 + (int) rb.y * 13));
    }

    /** 覆盖中某建筑的信号颜色（信号源/中继器用各自颜色，其余用浅蓝） */
    static Color buildingColor(Building b) {
        if (b instanceof SignalSourceBuild sb) return sourceColor(sb);
        if (b instanceof SignalRelayBuild rb) return relayColor(rb);
        return LIGHT_BLUE;
    }

    private static boolean visible = false;
    private static boolean toggleVisible = false;
    private static boolean prevDown = false;
    /** 淡入淡出透明度（0~1，每帧向目标过渡） */
    private static float displayAlpha = 0f;
    /** 当前显示模式（true=范围，false=数字），用于切换时淡入淡出 */
    private static boolean lastRangeMode = false;
    /** 底部提示标签 */
    private static Label hintLabel;

    public static void init() {
        // 无头服务器跳过（无渲染循环/无 UI），避免访问 Vars.ui.hudGroup 崩溃
        if (Vars.headless) return;
        // 渲染循环方块层绘制后触发（每帧）
        Events.run(EventType.Trigger.draw, SignalOverlay::update);
        // 客户端加载完成后创建底部提示标签
        Events.on(EventType.ClientLoadEvent.class, e -> {
            // 模组重载等场景重复触发时先移除旧标签，避免泄漏
            if (hintLabel != null) hintLabel.remove();
            hintLabel = new Label(Core.bundle.get("signal.overlay.hint"), Styles.outlineLabel);
            hintLabel.setFontScale(0.7f);
            hintLabel.visible = false;
            Vars.ui.hudGroup.addChild(hintLabel);
        });
    }

    static void update() {
        // 先取局部引用再判空，避免 null 检查与 team() 调用之间玩家断线导致的空指针
        Player player = Vars.player;
        if (player == null) return;
        Team team = player.team();
        boolean toggleMode = Core.settings.getBool("signal.hkey.toggle", true);
        boolean hold = Core.input.keyDown(KeyCode.h);
        // 无论设置开关如何，按住 H 始终显示信号强度
        if (toggleMode) {
            // 切换模式：按一下 H 翻转切换状态（按住优先显示）
            if (hold && !prevDown) toggleVisible = !toggleVisible;
            prevDown = hold;
            visible = hold || toggleVisible;
        } else {
            // 按住模式：按住显示，松开隐藏
            visible = hold;
        }
        // 淡入淡出：透明度每帧向目标过渡（约 6 帧完成）
        displayAlpha = Mathf.lerp(displayAlpha, visible ? 1f : 0f, 0.15f);
        if (displayAlpha > 0.01f) {
            drawOverlay(team, displayAlpha);
        }
        if (visible) {
            showHint();
        } else if (displayAlpha < 0.01f) {
            hideHint();
        }
    }

    /** 显示底部提示小字（屏幕下方中间） */
    static void showHint() {
        if (hintLabel == null) return;
        hintLabel.setPosition(Core.graphics.getWidth() / 2f - hintLabel.getPrefWidth() / 2f, 40f);
        hintLabel.visible = true;
    }

    static void hideHint() {
        if (hintLabel != null) hintLabel.visible = false;
    }

    /** 信号源与激活中继器列表（静态复用，避免每帧分配） */
    private static final Seq<Building> sources = new Seq<>();

    static void drawOverlay(Team team, float alpha) {
        // 视野宽（缩小视角）显示蓝色范围；视野窄（放大视角）显示数字
        boolean rangeMode = Core.camera.width >= ZOOM_THRESHOLD_WIDTH;
        // 模式切换时重新淡入（数字 ↔ 范围淡入淡出）
        if (rangeMode != lastRangeMode) {
            lastRangeMode = rangeMode;
            displayAlpha = 0f;
        }
        // 收集所有信号源与已激活中继器（同队；静态列表复用，不产生分配）
        sources.clear();
        sources.addAll(SignalSource.allSources(team));
        for (SignalRelayBuild rb : SignalRelay.allRelays(team)) {
            if (rb.active) sources.add(rb);
        }
        // 卫星全图信号强度（信号卫星每颗 +1，上限 15）
        int satStrength = SatelliteManager.signalStrength(team);
        if (rangeMode) {
            // 范围模式：先画卫星全图基础层（按卫星所属信号着色），再逐格合成各源覆盖
            if (satStrength > 0) {
                drawSatelliteRange(team, satStrength, alpha);
            }
            drawRangeComposite(team, alpha);
        } else {
            // 数字模式：逐格取 max（卫星基础强度 + 各源强度），每格只绘制一次，避免重复/叠加
            drawNumbersOverlay(team, satStrength, alpha);
        }
        Draw.reset();
    }

    /** 卫星全图信号层（范围模式）：可见区域内每格按卫星信号强度填充色块；颜色取卫星所属信号编码的专属色（无归属时蓝色渐变） */
    static void drawSatelliteRange(Team team, int satStrength, float alpha) {
        Rect view = Core.camera.bounds(Tmp.r1);
        int x0 = (int) (view.x / 8f) - 1, x1 = (int) ((view.x + view.width) / 8f) + 1;
        int y0 = (int) (view.y / 8f) - 1, y1 = (int) ((view.y + view.height) / 8f) + 1;
        float t = satStrength / SignalSource.MAX_STRENGTH;
        float rangeAlpha = Core.settings.getInt("signal.rangeAlpha", 45) / 100f;
        // 颜色：卫星所属信号的专属色（按编码生成，不限数量，固定亮度）；无归属时浅蓝→深蓝渐变
        String sig = SatelliteManager.satelliteSignal(team);
        if (sig != null) {
            Tmp.c1.set(signalColor(sig));
        } else {
            Tmp.c1.set(LIGHT_BLUE).lerp(DEEP_BLUE, t);
        }
        Draw.color(Tmp.c1, (0.1f + 0.25f * t) * rangeAlpha * alpha);
        for (int gx = x0; gx <= x1; gx++) {
            for (int gy = y0; gy <= y1; gy++) {
                Fill.rect(gx * 8f, gy * 8f, 8f, 8f);
            }
        }
    }

    /** 数字模式：可见区域内逐格计算强度 = max(卫星基础强度, 各源强度)，每格只绘制一次（字号覆盖一格 8px）；颜色取最强来源的专属色 */
    static void drawNumbersOverlay(Team team, int satStrength, float alpha) {
        Rect view = Core.camera.bounds(Tmp.r1);
        int x0 = (int) (view.x / 8f) - 1, x1 = (int) ((view.x + view.width) / 8f) + 1;
        int y0 = (int) (view.y / 8f) - 1, y1 = (int) ((view.y + view.height) / 8f) + 1;
        float digitAlpha = Core.settings.getInt("signal.digitAlpha", 80) / 100f;
        // 保存字体原始颜色与比例，绘制后恢复（try-finally 保证异常时也恢复）
        Color oldFontColor = Fonts.def.getColor();
        float oldScale = Fonts.def.getData().scaleX;
        // 字号 0.2（约 3.2px），远小于一格（8px）
        Fonts.def.getData().setScale(0.2f);
        try {
            for (int gx = x0; gx <= x1; gx++) {
                for (int gy = y0; gy <= y1; gy++) {
                    float wx = gx * 8f, wy = gy * 8f; // 格子中心（像素）
                    float s = satStrength; // 卫星全图基础强度
                    int best = -1; // 提供最强信号的来源索引（-1=仅卫星）
                    for (int i = 0; i < sources.size; i++) {
                        Building b = sources.get(i);
                        float bs = sourceStrength(b, wx, wy);
                        if (bs > s) {
                            s = bs;
                            best = i;
                        }
                    }
                    if (s <= 0f) continue;
                    int val = Mathf.round(s);
                    float t = s / SignalSource.MAX_STRENGTH;
                    // 颜色：最强来源的专属色（信号源/中继器不同色）；仅卫星信号时为蓝色渐变
                    if (best >= 0) {
                        Tmp.c1.set(buildingColor(sources.get(best)));
                    } else {
                        Tmp.c1.set(LIGHT_BLUE).lerp(DEEP_BLUE, t);
                    }
                    Tmp.c1.a((0.6f + 0.4f * t) * digitAlpha * alpha);
                    // 复用预计算字符串避免分配；字号 0.2（约 3.2px）时单字符居中偏移
                    Fonts.def.setColor(Tmp.c1);
                    Fonts.def.draw(NUMBER_STRINGS[val < 0 ? 0 : (val > 15 ? 15 : val)], wx - 1f, wy - 1.6f);
                }
            }
        } finally {
            // 恢复默认颜色与字号，避免影响其他字体渲染
            Fonts.def.setColor(oldFontColor);
            Fonts.def.getData().setScale(oldScale);
        }
    }

    /** 该源/中继器在 (wx, wy) 的信号强度（信号源无信号、中继器未激活时为 0） */
    static float sourceStrength(Building b, float wx, float wy) {
        if (b instanceof SignalSourceBuild sb) {
            return sb.signal == null ? 0f : SignalSource.strengthAt(b.x, b.y, wx, wy);
        }
        if (b instanceof SignalRelayBuild rb) {
            return rb.active ? SignalSource.strengthAt(b.x, b.y, wx, wy) : 0f;
        }
        return 0f;
    }

    /** 范围模式（逐格合成）：每格取最强信号来源，用其专属颜色绘制（重叠区域显示最强源，不做半透明混合） */
    static void drawRangeComposite(Team team, float alpha) {
        if (sources.isEmpty()) return;
        Rect view = Core.camera.bounds(Tmp.r1);
        float rpx = SignalSource.RADIUS * 8f;
        float rpxSq = rpx * rpx;
        // 格子范围：视口外扩一个覆盖半径（源在视口外但覆盖进入视口）
        int x0 = (int) ((view.x - rpx) / 8f) - 1, x1 = (int) ((view.x + view.width + rpx) / 8f) + 1;
        int y0 = (int) ((view.y - rpx) / 8f) - 1, y1 = (int) ((view.y + view.height + rpx) / 8f) + 1;
        // 范围模式透明度（0~100，设置项）
        float rangeAlpha = Core.settings.getInt("signal.rangeAlpha", 45) / 100f;
        for (int gx = x0; gx <= x1; gx++) {
            for (int gy = y0; gy <= y1; gy++) {
                float wx = gx * 8f, wy = gy * 8f; // 格子中心（像素）
                float best = 0f;
                int bestIdx = -1;
                for (int i = 0; i < sources.size; i++) {
                    Building b = sources.get(i);
                    // 平方距离快速跳过（覆盖半径外无信号）
                    float dx = wx - b.x, dy = wy - b.y;
                    if (dx * dx + dy * dy > rpxSq) continue;
                    float bs = sourceStrength(b, wx, wy);
                    if (bs > best) {
                        best = bs;
                        bestIdx = i;
                    }
                }
                if (bestIdx < 0) continue;
                float t = best / SignalSource.MAX_STRENGTH;
                // 最强来源的专属颜色，透明度随强度（越近越不透明）
                Draw.color(buildingColor(sources.get(bestIdx)), (0.1f + 0.25f * t) * rangeAlpha * alpha);
                Fill.rect(wx, wy, 8f, 8f);
            }
        }
    }
}
