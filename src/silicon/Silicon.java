package silicon;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.input.Binding;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import silicon.content.block.Blocks;
import silicon.content.item.Items;
import silicon.util.SiliconLog;

import static mindustry.Vars.*;


public class Silicon extends Mod {
    private static final Seq<Building> emptySeq = new Seq<>(0);
    public static Mods.LoadedMod MOD;

    public Silicon() {
        Events.on(EventType.ClientLoadEvent.class, e -> {
            MOD = mods.getMod(Silicon.class);
            MOD.meta.subtitle = MOD.meta.version;
        });
    }

    @Override
    public void loadContent() {
        Items.load();
        Blocks.load();
        SiliconLog.info("Loading contents.");
    }

    @Override
    public void init() {
        Events.on(EventType.ClientLoadEvent.class, e -> {
            ui.settings.addCategory("@settings.silicon.meta.category.name",
                    new TextureRegionDrawable(new TextureRegion(Silicon.MOD.iconTexture)), st -> {
                st.checkPref("pause", false);
                        SiliconLog.info("Loading settings.");
                    });
        });

        Events.on(EventType.ClientLoadEvent.class, e -> {
            netServer.addPacketHandler("pause", (p, time) -> {
                if (!Core.settings.getBool("pause")) return;
                if (p.admin || p.name.equals(state.map.author())) {
                    state.set(state.isPaused() ? GameState.State.playing : GameState.State.paused);
                    Call.clientPacketReliable(p.con, "paused", time);
                    SiliconLog.info(p.name + " pause");
                }
            });
            netClient.addPacketHandler("paused", (s) -> {
                Vars.pause.complete = true;
            });
        });
//        Core.input.getKeyboard().keyDown(Binding.pause.value.key);
        Events.run(EventType.Trigger.update, () -> {
            if (!state.isGame()) return;
            if (net.client() && (Core.input.keyTap(Binding.pause) || !Vars.pause.complete)) {
                String time = String.valueOf(Time.time);
                Call.serverPacketReliable("pause", time);
                Vars.pause = new Vars.Pause(time);
                SiliconLog.info("pause");
            }
        });
//        Events.run(EventType.WorldLoadEvent.class, () -> ((MineConverter) mineConverter).countWorldCosts());
//        Events.run(EventType.Trigger.update, () -> {
//            Log.info(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "blockCount: " + Vars.blockCount);
//            Log.info(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "Groups.build.first(): " + Groups.build.first());
//
//            Vars.blockCount.clear();
//            world.tiles.eachTile(tile -> {
//                if (tile.build != null) {
//                    Vars.blockCount.get(tile.build.block, emptySeq).add(tile.build);
//                }
//            });
//            Groups.build.each(building -> {
//                if (building.block != null) {
//                    Vars.blockCount.get(building.block, emptySeq).add(building);
//                }
//            });
//        });

//        MenuFragment.MenuButton menuButton =
//                new MenuFragment.MenuButton("1111",new BaseDrawable(),);
//        ui.menufrag.addButton(menuButton);
    }

}