package silicon;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.TextureRegionDrawable;
import arc.util.Time;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.input.Binding;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
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
                st.checkPref("pauseRequest", true);
                st.sliderPref("pauseMode", 0, 0, 2, 1,
                        i -> Core.bundle.get("setting.pauseMode.value." + i, String.valueOf(i)),
                        i -> {
                            Vars.pauseMode = i;
                            if (net.client()) Call.serverPacketReliable("pause-setmode", String.valueOf(i));
                        });

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
}
