package silicon.world.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;

import static mindustry.Vars.content;

/**
 * DimensionAnchor - 维度锚点
 * A 3x3 item block. Clicking it opens a UI to pick a mode (send/receive) and a signal.
 * Only one receiving anchor is allowed per signal. In send mode it periodically tries to
 * send its whole inventory to the receiving anchor with the same signal, and aborts the
 * attempt (restarting the timer) if that is impossible.
 * The mode + signal are encoded into a single String config ("send:N3PO" / "receive:").
 */
public class DimensionAnchor extends Block{
    /** Interval between send attempts, in ticks (10 seconds). */
    public static final float sendInterval = 10f * 60f;
    /** Power consumed (per second, /60 convention) by a sending anchor. */
    public static final float sendPower = 1200f / 60f;
    /** Power consumed (per second, /60 convention) by a receiving anchor. */
    public static final float receivePower = 160f / 60f;

    /** Send status: still trying. */
    public static final int STATUS_TRYING = 0;
    /** Send status: last attempt failed. */
    public static final int STATUS_FAILED = 1;
    /** Send status: last attempt succeeded. */
    public static final int STATUS_SUCCESS = 2;

    public DimensionAnchor(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        breakable = true;
        hasItems = true;
        itemCapacity = 100;
        hasPower = true;
        // sending anchors draw lots of power to package+send, receiving anchors draw a little to receive
        consumePowerDynamic((Building entity) -> entity instanceof DimensionAnchorBuild b ? (b.sendMode ? sendPower : receivePower) : 0f);
        configurable = true;
        config(String.class, (building, value) -> {
            if(building instanceof DimensionAnchorBuild b){
                b.decode(value, true);
            }
        });
    }

    @Override
    public void setBars(){
        super.setBars();
        // send progress bar - shown only for send-mode anchors
        addBar("send", (DimensionAnchorBuild b) -> {
            if(!b.sendMode) return null;
            return new Bar(
                b::sendStatusText,
                () -> b.lastSendStatus == STATUS_FAILED ? Pal.remove : Pal.accent,
                () -> Mathf.clamp(b.sendTimer / sendInterval)
            );
        });
    }

    public class DimensionAnchorBuild extends Building{
        /** true = send mode, false = receive mode. */
        public boolean sendMode = true;
        /** The linked signal, or null if not configured. */
        public String signal;
        /** UI-only flag: whether the signal list is expanded. */
        public boolean expanded;
        /** Status of the last send attempt (see STATUS_*). */
        public int lastSendStatus = STATUS_TRYING;
        private float sendTimer = 0f;

        @Override
        public Object config(){
            return encode();
        }

        String encode(){
            return (sendMode ? "send:" : "receive:") + (signal == null ? "" : signal);
        }

        void decode(String str, boolean validate){
            if(str == null) return;
            int idx = str.indexOf(':');
            if(idx < 0) return;
            boolean newMode = str.startsWith("send:");
            String s = idx + 1 < str.length() ? str.substring(idx + 1) : "";
            if(s.isEmpty()) s = null;

            // only one receiving anchor may exist per signal
            if(validate && !newMode && s != null && hasOtherReceiver(s)) return;

            sendMode = newMode;
            signal = s;
        }

        /** @return whether another receiving anchor already uses this signal. */
        boolean hasOtherReceiver(String sig){
            for(Building b : Groups.build){
                if(b instanceof DimensionAnchorBuild other && other != this && !other.sendMode && sig.equals(other.signal)){
                    return true;
                }
            }
            return false;
        }

        String sendStatusText(){
            return Core.bundle.get(switch(lastSendStatus){
                case STATUS_FAILED -> "block.silicon-dimension-anchor.send.failed";
                case STATUS_SUCCESS -> "block.silicon-dimension-anchor.send.success";
                default -> "block.silicon-dimension-anchor.send.trying";
            });
        }

        @Override
        public void updateTile(){
            // only charge (and eventually send) in send mode with a configured signal
            if(sendMode && signal != null && enabled){
                // charging requires power - without it the anchor cannot charge
                if(power == null || power.status < 0.999f) return;

                sendTimer += Time.delta;
                if(sendTimer >= sendInterval){
                    sendTimer = 0f; // restart the charge cycle regardless of outcome
                    if(items != null && items.total() > 0){
                        lastSendStatus = trySend() ? STATUS_SUCCESS : STATUS_FAILED;
                    }
                }
            }
        }

        /**
         * Attempts to send the whole inventory to the single receiving anchor with the same signal.
         * Cancels (and reports failure) if there are multiple/no receivers, the receiver has no
         * power, or the receiver cannot fit the whole inventory.
         */
        boolean trySend(){
            if(items == null || items.total() <= 0) return false;

            DimensionAnchorBuild target = null;
            int receivers = 0;
            for(Building b : Groups.build){
                if(b instanceof DimensionAnchorBuild other && other != this && !other.sendMode
                    && other.signal != null && other.signal.equals(signal)){
                    receivers++;
                    target = other;
                }
            }
            // must be exactly one receiving anchor
            if(receivers != 1 || target == null) return false;

            // receiving anchor has no power to receive
            if(target.power == null || target.power.status < 0.999f) return false;

            // receiving anchor cannot fit the whole inventory
            if(target.items == null || target.block.itemCapacity - target.items.total() < items.total()) return false;

            // transfer everything
            for(Item item : content.items()){
                int amount = items.get(item);
                if(amount <= 0) continue;
                target.items.add(item, amount);
                items.remove(item, amount);
            }
            return true;
        }

        @Override
        public void buildConfiguration(Table table){
            rebuild(table);
        }

        void rebuild(Table table){
            table.clearChildren();
            table.top();

            // mode buttons: send / receive
            table.table(mt -> {
                mt.left();
                mt.button(Core.bundle.get("block.silicon-dimension-anchor.send"), Styles.flatTogglet, () -> {
                    sendMode = true;
                    expanded = true;
                    configure(encode());
                    rebuild(table);
                }).checked(sendMode).size(110f, 44f).pad(3f);
                mt.button(Core.bundle.get("block.silicon-dimension-anchor.receive"), Styles.flatTogglet, () -> {
                    if(signal != null && hasOtherReceiver(signal)){
                        Vars.ui.showInfoToast(Core.bundle.get("block.silicon-dimension-anchor.hasreceiver"), 3f);
                    }else{
                        sendMode = false;
                        expanded = true;
                        configure(encode());
                    }
                    rebuild(table);
                }).checked(!sendMode).size(110f, 44f).pad(3f);
            }).left();
            table.row();

            // signal list, shown after picking a mode (or if a signal is already configured)
            if(expanded || signal != null){
                if(SignalSource.usedSignals.isEmpty()){
                    table.label(() -> Core.bundle.get("block.silicon-dimension-anchor.nosignals"))
                        .color(Color.gray).padTop(10f);
                }else{
                    Seq<String> signals = new Seq<>();
                    for(String s : SignalSource.usedSignals){
                        signals.add(s);
                    }
                    signals.sort();

                    Table list = new Table();
                    list.top().left();
                    for(String s : signals){
                        list.button(b -> b.label(() -> s).left(), Styles.flatBordert, () -> {
                            if(!sendMode && hasOtherReceiver(s)){
                                Vars.ui.showInfoToast(Core.bundle.get("block.silicon-dimension-anchor.hasreceiver"), 3f);
                            }else{
                                signal = s;
                                configure(encode());
                            }
                            rebuild(table);
                        }).checked(s.equals(signal)).size(230f, 34f).pad(2f).left();
                        list.row();
                    }

                    ScrollPane pane = new ScrollPane(list);
                    pane.setScrollingDisabled(true, false);
                    table.add(pane).height(220f).width(240f).padTop(6f);
                }
            }

            // inventory display + deposit button, so items can be put into the anchor from the UI
            table.table(it -> {
                it.left();
                it.label(() -> Core.bundle.get("block.silicon-dimension-anchor.inventory")).left().padRight(6f);
                it.row();
                boolean any = false;
                for(Item item : content.items()){
                    if(items != null && items.get(item) > 0){
                        any = true;
                        it.image(item.fullIcon).size(24f).pad(1f);
                        it.label(() -> String.valueOf(items.get(item))).padRight(6f);
                    }
                }
                if(!any){
                    it.label(() -> "0").color(Color.gray).left();
                }
            }).left();
            table.row();

            if(items != null && Vars.player.unit() != null && Vars.player.unit().stack.amount > 0){
                Unit unit = Vars.player.unit();
                table.button(Core.bundle.format("block.silicon-dimension-anchor.deposit", unit.item().localizedName), Styles.flatBordert, () -> {
                    if(unit.stack.amount > 0 && acceptStack(unit.item(), unit.stack.amount, unit) > 0){
                        Call.transferInventory(Vars.player, this);
                        rebuild(table);
                    }
                }).size(230f, 40f).padTop(6f);
                table.row();
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.str(encode());
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            // trust the save when restoring; uniqueness is only enforced on new configs
            decode(read.str(), false);
        }
    }
}
