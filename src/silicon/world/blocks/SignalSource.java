package silicon.world.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.util.Nullable;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.Tile;

import static mindustry.Vars.tilesize;

/**
 * SignalSource - 信号源
 * Generates a unique random 4-character signal (uppercase A-Z and digits 0-9) when placed.
 * The signal is generated once on the server (placeEnded only runs on the server), synced to
 * all clients via the config mechanism, and persisted to saves.
 * The signal is removed again when the block is removed.
 */
public class SignalSource extends Block{
    /** Allowed characters: uppercase letters and digits only, to avoid encoding issues. */
    public static final String SIGNAL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** Length of a signal. */
    public static final int SIGNAL_LENGTH = 4;
    /** Max attempts before giving up on finding an unused signal. */
    private static final int MAX_ATTEMPTS = 1000;

    /** All signals currently in use, so every placed block gets a unique one. */
    public static final ObjectSet<String> usedSignals = new ObjectSet<>();

    public SignalSource(String name){
        super(name);
        solid = true;
        destructible = true;
        breakable = true;
        // A signal source needs power to emit its signal.
        hasPower = true;
        consumePower(60f / 60f);
        config(String.class, (building, value) -> {
            if(building instanceof SignalSourceBuild b){
                b.signal = value;
                if(value != null) usedSignals.add(value);
            }
        });
    }

    @Override
    public void placeEnded(Tile tile, @Nullable Unit builder, int rotation, @Nullable Object config){
        super.placeEnded(tile, builder, rotation, config);
        if(tile.build instanceof SignalSourceBuild b){
            // placeEnded only runs on the server, so generate here and sync to all clients.
            b.configureAny(generateUniqueSignal());
        }
    }

    /** @return a random 4-character signal (A-Z, 0-9) that is not already in use. */
    public static String generateUniqueSignal(){
        StringBuilder sb = new StringBuilder(SIGNAL_LENGTH);
        String candidate;
        int attempts = 0;
        do{
            sb.setLength(0);
            for(int i = 0; i < SIGNAL_LENGTH; i++){
                sb.append(SIGNAL_CHARS.charAt(Mathf.random(SIGNAL_CHARS.length() - 1)));
            }
            candidate = sb.toString();
        }while(usedSignals.contains(candidate) && ++attempts < MAX_ATTEMPTS);

        usedSignals.add(candidate);
        return candidate;
    }

    public class SignalSourceBuild extends Building{
        public String signal;

        @Override
        public Object config(){
            return signal;
        }

        /** Custom HUD: signal text on top, then only health, power and a white strength bar. */
        @Override
        public void display(Table table){
            // signal text, above the bars: "信号：[字符串]"
            table.table(t -> {
                t.left();
                t.label(() -> signal == null
                    ? "[lightgray]" + Core.bundle.get("block.silicon-signal-source.nosignal")
                    : "[accent]" + Core.bundle.format("block.silicon-signal-source.signal", signal)
                ).left();
            }).left();
            table.row();

            // health bar
            table.table(t -> {
                t.left();
                t.add(new Bar("stat.health", Pal.health, this::healthf).blink(Color.white)).height(18f).width(220f);
            }).left();
            table.row();

            // power bar
            table.table(t -> {
                t.left();
                t.add(new Bar(() -> Core.bundle.get("bar.power"), () -> Pal.powerBar, () -> power == null ? 0f : power.status)).height(18f).width(220f);
            }).left();
            table.row();

            // white signal strength bar
            table.table(t -> {
                t.left();
                t.add(new Bar(() -> Core.bundle.get("block.silicon-signal-source.strength"), () -> Color.white, () -> signal == null ? 0f : 1f)).height(18f).width(220f);
            }).left();
        }

        @Override
        public void draw(){
            super.draw();
            if(signal != null){
                Drawf.text(Core.bundle.format("block.silicon-signal-source.signal", signal), x, y + size * tilesize / 2f - 4f, Pal.accent, 1f);
            }
        }

        /** Releases the signal when this block is removed, so it can be reused later. */
        @Override
        public void onRemoved(){
            super.onRemoved();
            if(signal != null){
                usedSignals.remove(signal);
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.str(signal);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            signal = read.str();
            if(signal != null) usedSignals.add(signal);
        }
    }
}
