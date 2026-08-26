package silicon.world.blocks.defense;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Nullable;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.BlockGroup;
import silicon.util.SiliconTmp;

public class Switch extends Block {
    public Switch(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true; // 可配置：支持按钮式切换
        rotate = true;
        group = BlockGroup.logic;
        config(Boolean.class, (building, enabled) -> {
            Building front = building.front();
            // #28 只允许控制同队建筑
            if (front == null || front.team != building.team) return;
            front.enabled = !enabled;
        });
    }

    @Override
    public void placeEnded(Tile tile, @Nullable Unit builder, int rotation, @Nullable Object config) {
        if (tile.build instanceof SwitchBuild build && build.front() != null
            && build.front().team == build.team) { // #28 同队校验
            build.fE = build.front().enabled;
        }
    }

    public class SwitchBuild extends Building {
        boolean fE;
        @Override
        public void drawSelect() {
            super.drawSelect();
            if (front() == null || (front() instanceof SwitchBuild)) return;
            Drawf.selected(front(), SiliconTmp.c1.set(front().enabled ? Color.green : Color.red).a(Mathf.absin(4f, 1f)));

        }

        @Override
        public void updateTile() {
            super.updateTile();
            // #28 同队校验：不控制其它队伍建筑
            if (front() != null && front().team == team && front().enabled != fE) front().enabled = fE;
        }

        @Override
        public void tapped() {
            // #28 同队校验
            if (front() != null && front().team == team && !(front() instanceof SwitchBuild)) fE = !fE;
        }

        /**
         * 切换式按钮配置界面：按一次切换 front 建筑启用状态并持续保持。
         * 按钮尺寸 80×40（与原版开关按钮一致）。
         */
        @Override
        public void buildConfiguration(Table table) {
            table.button(Core.bundle.get("block.silicon-switch.name"), Styles.flatTogglet, () -> {
                Building front = front();
                if (front != null && front.team == team && !(front instanceof SwitchBuild)) {
                    fE = !fE;
                    configure(fE);
                }
            }).checked(fE).size(80f, 40f).pad(4f);
        }

        /**
         * Writes building data to save a file
         *
         * @param write The writer object
         */
        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(fE);
        }

        /**
         * Reads building data from a save file
         *
         * @param read     The reader object
         * @param revision The save revision
         */
        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            fE = read.bool();
        }

        @Override
        public Boolean config() {
            return front() != null && front().enabled;
        }
    }
}
