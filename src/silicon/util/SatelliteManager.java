package silicon.util;

import arc.Core;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import silicon.content.Statuses;
import silicon.world.blocks.satellite.SatelliteConsole;
import silicon.world.blocks.satellite.SatelliteLauncher;

/**
 * 卫星系统全局状态（按队伍）：
 * - 待发射卫星：由卫星发射中枢生产（每中枢同时 1 颗），生产完成后登记；燃料（石油）与缓冲电力（10000）均存储于中枢
 * - 在轨卫星：由卫星控制台发射，每颗信号卫星提供全图信号强度 +1（可叠加，上限 15）
 * 状态为运行时内存态，世界加载时重置（重启后需重新发射）。
 */
public class SatelliteManager {
    /** 发射结果 */
    public static final int LAUNCH_OK = 0;
    /** 无待发射卫星 */
    public static final int LAUNCH_NO_READY = 1;
    /** 燃料不足（石油 < 1000） */
    public static final int LAUNCH_NO_FUEL = 2;
    /** 缓冲电力不足（< 10000） */
    public static final int LAUNCH_NO_POWER = 3;

    /** 在轨卫星数量（按队伍） */
    private static final ObjectMap<Team, Integer> launched = new ObjectMap<>();
    /** 已生产完成、待发射的中枢列表（按队伍） */
    private static final ObjectMap<Team, Seq<SatelliteLauncher.SatelliteLauncherBuild>> readyLaunchers = new ObjectMap<>();

    /** 世界加载时重置（卫星不跨存档） */
    public static void reset() {
        launched.clear();
        readyLaunchers.clear();
    }

    /** 某队伍在轨卫星数 */
    public static int launchedCount(Team team) {
        return launched.get(team, 0);
    }

    /** 某队伍是否有可发射的卫星 */
    public static boolean hasReady(Team team) {
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(team);
        return list != null && !list.isEmpty();
    }

    /** 某队伍待发射卫星数 */
    public static int readyCount(Team team) {
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(team);
        return list == null ? 0 : list.size;
    }

    /** 中枢生产完成时登记 */
    public static void addReady(SatelliteLauncher.SatelliteLauncherBuild launcher) {
        readyLaunchers.get(launcher.team, Seq::new).add(launcher);
    }

    /** 中枢拆除/重置时移除登记 */
    public static void removeReady(SatelliteLauncher.SatelliteLauncherBuild launcher) {
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(launcher.team);
        if (list != null) list.remove(launcher);
    }

    /**
     * 发射一颗卫星：取出第一颗待发射卫星，由其中枢扣除燃料（1000 石油）与缓冲电力（10000），
     * 在轨 +1，给发射队伍的全图玩家应用「卫星在轨」buff，并向全图播报。
     * @param type 卫星种类（SatelliteConsole.TYPE_*）
     * @return 发射结果（LAUNCH_*）
     */
    public static int launch(Team team, int type) {
        Seq<SatelliteLauncher.SatelliteLauncherBuild> list = readyLaunchers.get(team);
        if (list == null || list.isEmpty()) return LAUNCH_NO_READY;
        SatelliteLauncher.SatelliteLauncherBuild launcher = list.get(0);
        int reason = launcher.checkLaunchResources();
        if (reason != LAUNCH_OK) return reason;
        // 扣燃料与缓冲电力，重置该中枢使其可再生产
        launcher.consumeLaunchResources();
        list.remove(0);
        launched.put(team, launchedCount(team) + 1);
        // 给发射队伍的全图玩家应用卫星 buff（显示用，无属性；其他队伍的玩家不显示）
        for (Player p : Groups.player) {
            if (p.team() == team && p.unit() != null) {
                p.unit().apply(Statuses.satelliteBuff, 999999f);
            }
        }
        // 全图播报：xx队发射了一颗xx卫星
        String teamName = Core.bundle.get("team." + team.name + ".name", team.name);
        String typeKey = type == SatelliteConsole.TYPE_SIGNAL
                ? "block.silicon-satellite-console.type.signal" : "block.silicon-satellite-console.type.signal";
        Call.sendMessage(Core.bundle.format("satellite.launch.message", teamName, Core.bundle.get(typeKey)));
        return LAUNCH_OK;
    }

    /** 信号卫星提供的全图信号强度（每颗 +1，上限 15） */
    public static int signalStrength(Team team) {
        return Math.min(15, launchedCount(team));
    }
}
