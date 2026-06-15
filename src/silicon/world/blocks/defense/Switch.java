package silicon.world.blocks.defense;

import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;
import silicon.util.SiliconTmp;

public class Switch extends Block {
    public Switch(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = false;
        rotate = true;
        group = BlockGroup.projectors;
        config(Boolean.class, (building, enabled) -> building.front().enabled = !enabled);
    }

    public class SwitchBuild extends Building {
        @Override
        public void drawSelect() {
            super.drawSelect();
//            Draw.color(front().enabled ? Color.green : Color.red, 255);
            if (front() == null || (front() instanceof SwitchBuild)) return;
            Drawf.selected(front(), SiliconTmp.c1.set(front().enabled ? Color.green : Color.red).a(Mathf.absin(4f, 1f)));

        }

        @Override
        public void tapped() {
            if (front() != null && !(front() instanceof SwitchBuild)) front().enabled = !front().enabled;
        }

//        @Override
//        public Boolean config() {
//            return front().enabled;
//        }
    }
}
