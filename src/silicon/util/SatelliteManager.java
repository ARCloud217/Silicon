package silicon.util;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.Vars;
import silicon.content.Statuses;
import silicon.world.blocks.satellite.SatelliteConsole;
import silicon.world.blocks.satellite.SatelliteLauncher;
import silicon.world.blocks.signal.SignalChannel;

/**
 * 卫星系统全局状态（按队伍）：
 * - 待发射卫星：由卫星发射中枢生产（每中枢同时 1 颗），生产完成后登记；燃料（石油）与缓冲电力（10000）均存储于中枢
 * - 在轨卫星：由卫星控制台发射，每颗信号/测试卫星提供全图信号强度 +1（可叠加，上限 15）
 * 状态为运行时内存态，世界加载时重置（重启后需重新发射）。
 */
public class SatelliteManager {
    /** 发射结果 */
    public static final int LAUNCH_OK = 0;
    /** 无待发射卫星 */
    public static final int LAUNCH_NO_READY = 1;
    /** 燃料不足（石油少于该轨道需求） */
    public static final int LAUNCH_NO_FUEL = 2;
    /** 缓冲电力不足（< 10000） */
    public static final int LAUNCH_NO_POWER = 3;
    /** 轨道与卫星种类不匹配（信号卫星不能发 SSO） */
    public static final int LAUNCH_ORBIT_FORBIDDEN = 4;
    /** 未绑定卫星发射中枢（无信号/信号范围内无中枢/控制台不在信号范围内） */
    public static final int LAUNCH_NO_HUB = 5;
    /** 所选信号范围内存在多个卫星发射中枢 */
    public static final int LAUNCH_MULTI_HUB = 6;
    /** 所选信号范围内存在多个卫星控制台 */
    public static final int LAUNCH_MULTI_CONSOLE = 7;

    /** 在轨卫星数量（按队伍、按种类）——联网时仅主机维护真实值，客机经 sat-state 广播镜像 */
    private static final ObjectMap<Team, ObjectIntMap<Integer>> launched = new ObjectMap<>();
    /** 已生产完成、待发射的中枢列表（按队伍）——仅主机使用（客机建筑不跑 updateTile） */
    private static final ObjectMap<Team, Seq<SatelliteLauncher.SatelliteLauncherBuild>> readyLaunchers = new ObjectMap<>();
    /** 卫星所属信号（按队伍）：控制台选择，发射后全图信号层用该信号的颜色显示（null=无归属，保持蓝色） */
    private static final ObjectMap<Team, String> satelliteSignal = new ObjectMap<>();
    /** 客机端镜像的待发射数（主机广播 sat-state 填充；主机端直接用 readyLaunchers） */
    private static final ObjectIntMap<Team> readyMirror = new ObjectIntMap<>();
    /** 客机端镜像：待发射第一颗的种类 / 制造中种类（-1=无；主机端实时计算） */
    private static final ObjectIntMap<Team> readyTypeMirror = new ObjectIntMap<>();
    private static final ObjectIntMap<Team> producingTypeMirror = new ObjectIntMap<>();
    /** 状态广播字段分隔符（编码：teamId|sigC|testC|signal|readyC|readyType|producingType） */
    static final String SEP = "|";

    /** 本端是否为状态权威端（dedicated 服务器或 host，或单机）：只有权威端执行发射/生产登记 */
    public static boolean isAuthority() {
        return Vars.net.server() || !Vars.net.active();
    }

    /** 卫星发射特效：巨大光柱尾焰 + 向上喷射粒子 + 烟柱（配合方块自绘动画，1.5 秒） */
    public static final Effect launchFx = new Effect(90f, e -> {
        // 特效层（盖过方块，确保可见）
        Draw.z(Layer.effect);
        // 底部光柱（大而明显）
        Draw.color(Pal.lightOrange, Pal.ammo, e.fin());
        Fill.circle(e.x, e.y, 5f + e.finpow() * 12f);
        // 向上喷射粒子（大半径）
        for (int i = 0; i < 12; i++) {
            float ang = 90f + Mathf.range(25f);
            float len = e.fin() * 55f;
            Draw.color(Pal.ammo, Pal.lightOrange, e.fin());
            Fill.circle(e.x + Angles.trnsx(ang, len) * 0.7f, e.y + Angles.trnsy(ang, len) * 0.8f, e.fout() * 6f);
        }
        // 烟柱：向上漂散
        Draw.color(Color.gray, Color.lightGray, e.fin());
        Fill.circle(e.x + Mathf.range(2f), e.y + e.fin() * 45f, e.fout() * 8f);
    });

    /** 世界加载时重置（卫星不跨存档） */
    public static void reset() {
        launched.clear();
        readyLaunchers.clear();
        satelliteSignal.clear();
        readyMirror.clear();
        readyTypeMirror.clear();
        producingTypeMirror.clear();
    }

    /** 某队伍在轨卫星总数 */
    public static int launchedCount(Team team) {
        ObjectIntMap<Integer> map = launched.get(team);
        if (map == null) return 0;
        return map.get(SatelliteLauncher.TYPE_SIGNAL, 0) + map.get(SatelliteLauncher.TYPE_TEST, 0);
    }

    /** 某队伍指定种类在轨卫星数 */
    public static int launchedCount(Team team, int type) {
        ObjectIntMap<Integer> map = launched.get(team);
        return map == null ? 0 : map.get(type, 0);
    }

    /** 某队伍是否有可发射的卫星 */
    public static boolean hasReady(Team team) {
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(team);
        return list != null && !list.isEmpty();
    }

    /** 某队伍待发射卫星数（客机读广播镜像，权威端读登记列表） */
    public static int readyCount(Team team) {
        if (!isAuthority()) return readyMirror.get(team, 0);
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(team);
        return list == null ? 0 : list.size;
    }

    /** 中枢生产完成时登记（权威端调用）；登记变化向同队客机广播 */
    public static void addReady(SatelliteLauncher.SatelliteLauncherBuild launcher) {
        readyLaunchers.get(launcher.team, Seq::new).add(launcher);
        broadcastState(launcher.team);
    }

    /** 中枢拆除/重置时移除登记（权威端调用）；登记变化向同队客机广播 */
    public static void removeReady(SatelliteLauncher.SatelliteLauncherBuild launcher) {
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(launcher.team);
        if (list != null) list.remove(launcher);
        broadcastState(launcher.team);
    }

    /** 某队伍待发射第一颗卫星种类（信号/测试；-1=无）——客机读镜像，权威端读登记列表 */
    public static int readyType(Team team) {
        if (!isAuthority()) return readyTypeMirror.get(team, -1);
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(team);
        return list == null || list.isEmpty() ? -1 : list.get(0).selectedType;
    }

    /** 某队伍正在制造的卫星种类（-1=无）——客机读镜像，权威端扫描中枢生产状态 */
    public static int producingType(Team team) {
        if (!isAuthority()) return producingTypeMirror.get(team, -1);
        for (Building b : Groups.build) {
            if (b instanceof SatelliteLauncher.SatelliteLauncherBuild lb
                    && lb.team == team && !lb.produced && lb.progress > 0f) {
                return lb.selectedType;
            }
        }
        return -1;
    }

    /** 编码某队状态为广播串（teamId|sigC|testC|signal|readyC|readyType|producingType） */
    static String encodeState(Team team) {
        return team.id + SEP + launchedCount(team, SatelliteLauncher.TYPE_SIGNAL) + SEP
                + launchedCount(team, SatelliteLauncher.TYPE_TEST) + SEP
                + (satelliteSignal.get(team) == null ? "" : satelliteSignal.get(team)) + SEP
                + readyCount(team) + SEP + readyType(team) + SEP + producingType(team);
    }

    /**
     * 权威端广播某队卫星状态给同队所有已连接客户端（客机以 applyState 应用）。
     */
    public static void broadcastState(Team team) {
        if (!Vars.net.server()) return; // 仅服务器（host/dedicated）广播；单机无客户端
        String data = encodeState(team);
        for (Player p : Groups.player) {
            if (p.team() == team && p.con != null) {
                Call.clientPacketReliable(p.con, "sat-state", data);
            }
        }
    }

    /** 权威端周期广播所有在场队伍的卫星状态（控制台名称行保鲜；调用方按约 30 tick 周期） */
    public static void periodicBroadcastAll() {
        if (!Vars.net.server()) return;
        Seq<Team> seen = new Seq<>();
        for (Player p : Groups.player) {
            if (p.con == null || seen.contains(p.team())) continue;
            seen.add(p.team());
            broadcastState(p.team());
        }
    }

    /** 客户端应用主机广播的某队卫星状态（镜像；不修改权威端数据） */
    public static void applyState(String data) {
        String[] parts = data.split("\\" + SEP, -1);
        if (parts.length != 7) return;
        try {
            Team team = Team.get(Integer.parseInt(parts[0]));
            ObjectIntMap<Integer> map = launched.get(team, ObjectIntMap::new);
            map.clear();
            map.put(SatelliteLauncher.TYPE_SIGNAL, Integer.parseInt(parts[1]));
            map.put(SatelliteLauncher.TYPE_TEST, Integer.parseInt(parts[2]));
            satelliteSignal.put(team, parts[3].isEmpty() ? null : parts[3]);
            readyMirror.put(team, Integer.parseInt(parts[4]));
            readyTypeMirror.put(team, Integer.parseInt(parts[5]));
            producingTypeMirror.put(team, Integer.parseInt(parts[6]));
        } catch (NumberFormatException ignored) {
        }
    }

    /** 指定信号范围内的本队卫星发射中枢（中枢位置处于该信号有效范围内） */
    public static Seq<SatelliteLauncher.SatelliteLauncherBuild> hubsInSignal(Team team, String signal) {
        Seq<SatelliteLauncher.SatelliteLauncherBuild> out = new Seq<>();
        if (signal == null || signal.isEmpty()) return out;
        for (Building b : Groups.build) {
            if (b instanceof SatelliteLauncher.SatelliteLauncherBuild lb && lb.team == team
                    && SignalChannel.inSignalRange(team, signal, lb.x, lb.y)) {
                out.add(lb);
            }
        }
        return out;
    }

    /** 指定信号范围内的本队卫星控制台数量（含自身；>1 即"存在多个控制台"） */
    public static int consolesInSignal(Team team, String signal) {
        int n = 0;
        if (signal == null || signal.isEmpty()) return 0;
        for (Building b : Groups.build) {
            if (b instanceof SatelliteConsole.SatelliteConsoleBuild cb && cb.team == team
                    && SignalChannel.inSignalRange(team, signal, cb.x, cb.y)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 发射一颗卫星（权威端调用）：控制台与卫星发射中枢必须绑定——
     * 控制台须处于所选信号范围内，且该信号范围内恰好一台中枢与一台控制台（1:1 配对，否则拒绝并提示），
     * 由绑定的中枢扣除该轨道所需石油与缓冲电力（10000），在轨 +1，
     * 记录卫星所属信号（控制台选择，全图信号层用其颜色显示），
     * 给发射队伍的全图玩家应用「卫星在轨」buff，并向全图播报。
     * @param signalName 卫星所属信号编码（4 位；null=未绑定）
     * @param orbit 发射轨道（SatelliteConsole.ORBIT_*），决定燃油需求
     * @param consoleX/consoleY 控制台世界坐标（像素）——判定控制台自身是否在该信号范围内
     * @return 发射结果（LAUNCH_*）
     */
    public static int launch(Team team, String signalName, int orbit, float consoleX, float consoleY) {
        // —— 绑定校验 ——
        if (signalName == null || signalName.isEmpty()) return LAUNCH_NO_HUB;
        if (!SignalChannel.inSignalRange(team, signalName, consoleX, consoleY)) return LAUNCH_NO_HUB;
        Seq<SatelliteLauncher.SatelliteLauncherBuild> hubs = hubsInSignal(team, signalName);
        if (hubs.isEmpty()) return LAUNCH_NO_HUB;
        if (hubs.size > 1) return LAUNCH_MULTI_HUB;
        if (consolesInSignal(team, signalName) > 1) return LAUNCH_MULTI_CONSOLE;
        SatelliteLauncher.SatelliteLauncherBuild launcher = hubs.first();
        int type = launcher.selectedType;
        if (!SatelliteConsole.orbitAllowed(type, orbit)) return LAUNCH_ORBIT_FORBIDDEN;
        if (!launcher.produced) return LAUNCH_NO_READY;
        int fuel = SatelliteConsole.fuelFor(orbit);
        int reason = launcher.checkLaunchResources(fuel);
        if (reason != LAUNCH_OK) return reason;
        // 启动方块自绘发射动画（绕开 Effect 渲染管线，方块可见即特效可见）
        launcher.launchAnim = 0f;
        // 扣该轨道所需石油与缓冲电力，重置该中枢使其可再生产
        launcher.consumeLaunchResources(fuel);
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(team);
        if (list != null) list.remove(launcher);
        launched.get(team, ObjectIntMap::new).increment(type, 1);
        // 记录卫星所属信号（全图信号层按此着色）
        if (signalName != null && !signalName.isEmpty()) {
            satelliteSignal.put(team, signalName);
        }
        // 发射特效（在发射中枢位置，全图广播）：原版火箭发射喷发 + 自定义尾焰 + 冲击波/烟雾
        Call.effect(Fx.padlaunch, launcher.x, launcher.y, 0f, null);
        Call.effect(launchFx, launcher.x, launcher.y + 10f, 0f, null);
        Call.effect(Fx.shockwave, launcher.x, launcher.y, 0f, null);
        Call.effect(Fx.explosion, launcher.x, launcher.y, 0f, null);
        Call.effect(Fx.smokeCloud, launcher.x, launcher.y, 0f, null);
        Call.effect(Fx.bigShockwave, launcher.x, launcher.y, 0f, null);
        // 发射音效（核心发射音，全图可听）
        Call.soundAt(Sounds.coreLaunch, launcher.x, launcher.y, 1f, 1f);
        // 给发射队伍的全图玩家应用卫星 buff（显示用，无属性；其他队伍的玩家不显示）
        for (Player p : Groups.player) {
            if (p.team() == team && p.unit() != null) {
                p.unit().apply(Statuses.satelliteBuff, 999999f);
            }
        }
        // 全图播报：xx队发射了一颗xx卫星到xx轨道
        String teamName = Core.bundle.get("team." + team.name + ".name", team.name);
        String typeKey;
        switch (type) {
            case SatelliteLauncher.TYPE_TEST: typeKey = "block.silicon-satellite-console.type.test"; break;
            default: typeKey = "block.silicon-satellite-console.type.signal"; break;
        }
        Call.sendMessage(Core.bundle.format("satellite.launch.message", teamName,
                Core.bundle.get(typeKey), SatelliteConsole.orbitName(orbit)));
        // 在轨/待发射变化 → 广播同队客户端（launch 仅在权威端被调用）
        broadcastState(team);
        return LAUNCH_OK;
    }

    /** 卫星提供的全图信号强度（信号/测试卫星均提供，每颗 +1，上限 15） */
    public static int signalStrength(Team team) {
        return Math.min(15,
                launchedCount(team, SatelliteConsole.TYPE_SIGNAL) + launchedCount(team, SatelliteLauncher.TYPE_TEST));
    }

    /** 卫星所属信号编码（null=无归属，全图信号层保持蓝色） */
    public static String satelliteSignal(Team team) {
        return satelliteSignal.get(team);
    }
}
