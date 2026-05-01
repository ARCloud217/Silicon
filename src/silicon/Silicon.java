package silicon;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.input.Binding;
import mindustry.mod.Mod;
import silicon.content.block.Blocks;
import silicon.content.item.Items;
import silicon.world.blocks.production.MineConverter;

import static mindustry.Vars.*;
import static silicon.content.block.Blocks.mineConverter;


public class Silicon extends Mod {
    private static final Seq<Building> emptySeq = new Seq<>(0);
    public Silicon() {
//        Events.on(EventType.ClientLoadEvent.class, event -> {
//            Time.runTask(10f, () -> {
//                BaseDialog dialog = new BaseDialog("a title");
//                dialog.align(Align.center);
//                dialog.cont.add("hhhhhhh").row();
//                dialog.cont.button("what is this?",() -> {
//                    BaseDialog bd = new BaseDialog("what is GenshinImpact?");
//                    bd.top();
//                    bd.cont.add("????").pad(200).row();
//                    bd.cont.button("exit",bd::hide).size(100,50)

//                            ;
//                    bd.cont.button("bye~bye",dialog::hide);
//                    bd.show();
//                }).size(100,30)
//                        .style((Style) new Button.ButtonStyle().checked)

//                        .row();
//                dialog.cont.button("You can try",() -> {
//                    Table table = new Table();
//                    table.button("exit", ()->{}).left().size(100, 50);
//                    table.fill();
//                    table.row();
//                    BaseDialog fd = new FullTextDialog();
//                    fd.cont.add("text").row();
//                    fd.cont.button("exit", fd::hide).left().size(100, 50);
//                    fd.show();
//                }).size(100,100);
//                dialog.cont.button(new Button.ButtonStyle().checked,100,() -> {});
//                dialog.show();
//
//            });
//        });
//        Events.on(EventType.ClientLoadEvent.class, e -> {
//            //show dialog upon startup
//            Time.runTask(20f, () -> {
//                BaseDialog dialog = new BaseDialog("frog");
//                dialog.cont.add("behold").row();
//                //mod sprites are prefixed with the mod name (this mod is called 'example-java-mod' in its config)
//                dialog.cont.image(Core.atlas.find("easier-mindustry-frog")).pad(20f).row();
//                dialog.cont.button("I see", dialog::hide).size(100f, 50f);
//                dialog.show();
//            });
//
//        });
    }

    @Override
    public void loadContent() {
        Items.load();
        Blocks.load();
        Log.info("Loading some silicon content.");
    }

    @Override
    public void init() {
//        Core.input.getKeyboard().keyDown(Binding.pause.value.key);
        Events.run(EventType.Trigger.update, () -> {
            if (!state.isGame()) return;
            if (net.server()) {
                netServer.addPacketHandler("pause", (p, s) ->
                        state.set(state.isPaused() ? GameState.State.playing : GameState.State.paused));
            } else if (net.client() && Core.input.keyTap(Binding.pause)) {
                Call.serverPacketReliable("pause", null);
            }
        });
        Events.run(EventType.WorldLoadEvent.class, () -> ((MineConverter) mineConverter).countWorldCosts());
//        Events.run(EventType.Trigger.update, () -> {
//            Log.info(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "blockCount: " + Vars.blockCount);
//            Log.info(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "Groups.build.first(): " + Groups.build.first());
//
//            Vars.blockCount.clear();
////            world.tiles.eachTile(tile -> {
////                if (tile.build != null) {
////                    Vars.blockCount.get(tile.build.block, emptySeq).add(tile.build);
////                }
////            });
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