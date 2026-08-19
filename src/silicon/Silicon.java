package silicon;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.input.Binding;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import silicon.content.block.Blocks;
import silicon.content.item.Items;
import silicon.util.SiliconLog;

import static mindustry.Vars.*;


public class Silicon extends Mod {
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
                st.checkPref("pauseRequest", true);

                st.button("@setting.pause-manage.name", Styles.flatt, () -> {
                    showPauseManageDialog();
                }).width(280f).height(50f).padTop(7f).fillX().left().row();

                SiliconLog.info("Loading settings.");
            });
        });

        Events.on(EventType.ClientLoadEvent.class, e -> {
            netServer.addPacketHandler("pause", (p, time) -> {
                if (p.admin || p.name.equals(state.map.author())) {
                    state.set(state.isPaused() ? GameState.State.playing : GameState.State.paused);
                    Call.clientPacketReliable(p.con, "paused", time);
                    SiliconLog.info(p.name + " pause");
                    return;
                }

                if (Vars.pauseMode == 0) return;

                if (Vars.pauseMode == 1) {
                    state.set(state.isPaused() ? GameState.State.playing : GameState.State.paused);
                    Call.clientPacketReliable(p.con, "paused", time);
                    SiliconLog.info(p.name + " pause");
                    return;
                }

                if (Vars.pauseMode == 2 && Vars.pauseWhitelist.contains(p.name)) {
                    state.set(state.isPaused() ? GameState.State.playing : GameState.State.paused);
                    Call.clientPacketReliable(p.con, "paused", time);
                    SiliconLog.info(p.name + " pause");
                }
            });

            netServer.addPacketHandler("pause-setmode", (p, data) -> {
                if (!p.admin && !p.name.equals(state.map.author())) return;
                try {
                    Vars.pauseMode = Integer.parseInt(data.trim());
                    if (Vars.pauseMode < 0 || Vars.pauseMode > 2) Vars.pauseMode = 0;
                } catch (NumberFormatException ignored) {}
            });

            netServer.addPacketHandler("pause-grant", (p, data) -> {
                if (!p.admin && !p.name.equals(state.map.author())) return;
                String target = data.trim();
                if (target.isEmpty()) return;
                if (!Vars.pauseWhitelist.contains(target)) {
                    Vars.pauseWhitelist.add(target);
                }
            });

            netServer.addPacketHandler("pause-revoke", (p, data) -> {
                if (!p.admin && !p.name.equals(state.map.author())) return;
                String target = data.trim();
                Vars.pauseWhitelist.remove(target);
            });

            netClient.addPacketHandler("paused", (s) -> {
                Vars.pause.complete = true;
            });
        });

        Events.run(EventType.Trigger.update, () -> {
            if (!state.isGame()) return;
            if (net.client() && Core.settings.getBool("pauseRequest", true)) {
                if (Core.input.keyTap(Binding.pause)) {
                    String time = String.valueOf((long) Time.time);
                    Call.serverPacketReliable("pause", time);
                    Vars.pause = new Vars.Pause(time);
                } else if (!Vars.pause.complete && Time.time - Float.parseFloat(Vars.pause.time) > 60f) {
                    String time = String.valueOf((long) Time.time);
                    Call.serverPacketReliable("pause", time);
                    Vars.pause = new Vars.Pause(time);
                }
            }
        });

        Events.on(EventType.PlayerChatEvent.class, e -> {
            String msg = e.message;
            if (msg == null || !msg.startsWith("!pause")) return;
            handlePauseCommand(e.player, msg);
        });
    }

    private void handlePauseCommand(Player p, String msg) {
        String[] parts = msg.split(" ");
        if (parts.length < 2) return;

        boolean isHost = p.admin || p.name.equals(state.map.author());

        switch (parts[1]) {
            case "on":
                if (!isHost) return;
                Vars.pauseMode = 1;
                Call.infoMessage(p.con, "[accent]Pause mode: Admins only");
                break;
            case "off":
                if (!isHost) return;
                Vars.pauseMode = 0;
                Call.infoMessage(p.con, "[accent]Pause mode: Off");
                break;
            case "custom":
                if (!isHost) return;
                Vars.pauseMode = 2;
                Call.infoMessage(p.con, "[accent]Pause mode: Custom whitelist");
                break;
            case "grant":
                if (!isHost || parts.length < 3) return;
                String grantTarget = parts[2];
                if (!Vars.pauseWhitelist.contains(grantTarget)) {
                    Vars.pauseWhitelist.add(grantTarget);
                }
                Call.infoMessage(p.con, "[accent]Granted pause to: " + grantTarget);
                break;
            case "revoke":
                if (!isHost || parts.length < 3) return;
                String revokeTarget = parts[2];
                Vars.pauseWhitelist.remove(revokeTarget);
                Call.infoMessage(p.con, "[accent]Revoked pause from: " + revokeTarget);
                break;
            case "list":
                if (!isHost) return;
                String list = Vars.pauseWhitelist.isEmpty() ? "(empty)" : Vars.pauseWhitelist.toString(", ");
                Call.infoMessage(p.con, "[accent]Whitelist: " + list);
                break;
        }
    }

    private void showPauseManageDialog() {
        BaseDialog dialog = new BaseDialog("@setting.pause-manage.name");
        dialog.addCloseButton();

        dialog.cont.pane(pane -> {
            pane.background(Tex.button);
            pane.defaults().size(300f, 50f).left().pad(4f);

            pane.button("[  OFF  ]", Styles.flatt, () -> {
                Vars.pauseMode = 0;
                if (net.client()) Call.serverPacketReliable("pause-setmode", "0");
            }).marginLeft(8).padTop(4);
            pane.button("[ ADMINS ]", Styles.flatt, () -> {
                Vars.pauseMode = 1;
                if (net.client()) Call.serverPacketReliable("pause-setmode", "1");
            }).marginLeft(8).padTop(4);
            pane.button("[ CUSTOM ]", Styles.flatt, () -> {
                Vars.pauseMode = 2;
                if (net.client()) Call.serverPacketReliable("pause-setmode", "2");
            }).marginLeft(8).padTop(4);
            pane.row();

            pane.image().width(280f).color(Color.darkGray).padTop(8f).padBottom(8f).row();

            pane.label(() -> "[accent]Whitelist:[] " + (Vars.pauseWhitelist.isEmpty() ? "(empty)" : Vars.pauseWhitelist.toString(", "))).left().padLeft(8f).row();

            pane.image().width(280f).color(Color.darkGray).padTop(8f).padBottom(8f).row();

            if (net.server()) {
                Groups.player.each(p -> {
                    if (p.con == null) return;
                    boolean hasPerm = Vars.pauseWhitelist.contains(p.name);
                    pane.button(p.name + (hasPerm ? " [green][V]" : " [red][X]"), Styles.flatt, () -> {
                        if (hasPerm) {
                            Vars.pauseWhitelist.remove(p.name);
                        } else {
                            Vars.pauseWhitelist.add(p.name);
                        }
                    }).marginLeft(8);
                    pane.row();
                });
            }
        });

        dialog.show();
    }
}
