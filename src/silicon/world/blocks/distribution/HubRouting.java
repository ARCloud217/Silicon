package silicon.world.blocks.distribution;

import arc.math.geom.Intersector;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.type.Item;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import silicon.world.blocks.production.MineConverter;
import mindustry.world.blocks.defense.turrets.ItemTurret;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * 中枢网络路由工具（纯静态，无状态）。
 * 职责：连接白名单 / 链接有效性 / 整体占位判定 / BFS 最近供源与核心 / 同网判定。
 */
public class HubRouting {

    private HubRouting() {}

    // ── 连接白名单 ────────────────────────────────────────────────

    /** 可被中枢连接的建筑类型；核心旁已合并的容器排除。 */
    public static boolean shouldConnect(Building other) {
        if (other == null) return false;
        Block b = other.block;
        if (b instanceof CoreBlock) return true;
        if (b instanceof StorageBlock) {
            // 核心旁已与核心合并的容器本身就是核心的一部分，不参与中枢连接
            for (int x = other.tile.x - 1; x <= other.tile.x + other.block.size; x++) {
                for (int y = other.tile.y - 1; y <= other.tile.y + other.block.size; y++) {
                    Building nb = world.build(x, y);
                    if (nb != null && nb != other && nb.block instanceof CoreBlock) return false;
                }
            }
            return true;
        }
        if (b instanceof GenericCrafter) return true;
        if (b instanceof MineConverter) return true;
        if (b instanceof Drill) return true;
        if (b instanceof ItemTurret) return true;
        if (b instanceof ItemTransferHub) return true;
        return false;
    }

    /** 整体占位检测：中枢范围圆与目标建筑矩形相交即有效（非中心点距离）。 */
    public static boolean linkValid(Building tile, Building link) {
        if (tile == link || link == null) return false;
        if (!(tile.block instanceof ItemTransferHub)) return false;
        if (tile.team != link.team) return false;
        if (!shouldConnect(link)) return false;
        float range = ((ItemTransferHub) tile.block).connectionRange * tilesize;
        return Intersector.overlaps(Tmp.cr1.set(tile.x, tile.y, range),
            Tmp.r1.setCentered(link.x, link.y, link.block.size * tilesize, link.block.size * tilesize));
    }

    /** 圆 vs 建筑整体矩形。 */
    public static boolean overlaps(float srcx, float srcy, Building other, float range) {
        return Intersector.overlaps(Tmp.cr1.set(srcx, srcy, range),
            Tmp.r1.setCentered(other.x, other.y, other.block.size * tilesize, other.block.size * tilesize));
    }

    // ── 角色判定 ────────────────────────────────────────────────

    /** 需喂料的消费者：炮台(0) > 工厂(1)；仓储为 2。 */
    public static int consumerPriority(Building b) {
        if (b instanceof ItemTurret.ItemTurretBuild) return 0;
        if (isFactory(b)) return 1;
        return 2;
    }

    public static boolean isFactory(Building b) {
        return b instanceof GenericCrafter.GenericCrafterBuild
            || b instanceof MineConverter.MineConverterBuild
            || b instanceof Drill.DrillBuild
            || b instanceof ItemTurret.ItemTurretBuild;
    }

    /** 可被拉取的产出源：矿机 + 工厂。 */
    public static boolean isProducer(Building b) {
        return b instanceof Drill.DrillBuild
            || b instanceof GenericCrafter.GenericCrafterBuild
            || b instanceof MineConverter.MineConverterBuild;
    }

    /** 工厂仍在接收该物品 → 属于其自身输入库存，不应被同类工厂拉走。 */
    public static boolean isInputStockOfFactory(Building supplier, Item item) {
        return isFactory(supplier)
            && supplier.acceptItem(supplier, item)
            && supplier.items.get(item) < supplier.getMaximumAccepted(item);
    }
}