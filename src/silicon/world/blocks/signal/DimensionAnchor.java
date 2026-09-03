package silicon.world.blocks.signal;

import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * 已废弃的维度锚点（兼容存根）。
 * 旧版信号系统（信号源 + 维度锚点）已移除，但直接删除该方块会破坏包含它的旧存档
 * （Mindustry 存档按方块 ID 存储，删除会使其后所有方块 ID 前移、内容错位）。
 * 因此保留本存根以维持原注册位置与方块 ID：
 * 无任何功能、不出现在建造菜单（BuildVisibility.hidden），仅可拆除以清理旧存档残留。
 *
 * 旧版 DimensionAnchorBuild.write() 会在 base 数据后写入一个 String（"send:xxx" / "receive:xxx"）。
 * 如果存根不读取该 String，残留字节会导致同一 chunk 中后续建筑的反序列化错位（建筑消失/错乱）。
 * 因此必须定义 stubBuild 来消费旧格式数据。
 */
public class DimensionAnchor extends Block {
    public DimensionAnchor(String name) {
        super(name);
        // 无功能：不更新、不可配置
        update = false;
        solid = true;
        destructible = true;
        breakable = true;
        // 隐藏于建造菜单（旧存档加载仍正常）
        buildVisibility = BuildVisibility.hidden;
        // 指定存根建筑类，确保旧存档的自定义序列化数据被正确消费
        buildType = StubBuild::new;
    }

    /**
     * 存根建筑：仅用于读取旧版写入的 String 配置数据并丢弃，
     * 防止未消费的字节导致同一存档块中后续建筑反序列化错位。
     */
    public static class StubBuild extends Building {
        /** 旧版 write() 格式：super.write + write.str(encodedConfig) */
        @Override
        public void write(Writes write) {
            super.write(write);
            // 不写入任何额外数据——旧版 String 已无意义
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            // 消费旧版写入的 String（"send:xxx" / "receive:xxx"），防止残留字节污染后续建筑
            read.str();
        }
    }
}
