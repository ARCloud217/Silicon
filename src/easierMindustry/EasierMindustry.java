package easierMindustry;

import arc.util.Log;
import easierMindustry.content.block.Blocks;
import easierMindustry.content.item.Items;
import mindustry.mod.Mod;


public class EasierMindustry extends Mod {
    public EasierMindustry() {
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
        Log.info("Loading some easierMindustry content.");
    }

    @Override
    public void init() {
//        MenuFragment.MenuButton menuButton =
//                new MenuFragment.MenuButton("1111",new BaseDrawable(),);
//        ui.menufrag.addButton(menuButton);
    }
}