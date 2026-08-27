package silicon.world.blocks.satellite;

import arc.Core;
import arc.func.Boolp;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;

import java.util.Locale;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import silicon.content.Statuses;
import silicon.util.SatelliteManager;

import static mindustry.type.ItemStack.with;

/**
 * 鍗槦鍙戝皠涓灑锛?脳3锛夛細閫夋嫨鍗槦绉嶇被骞剁敓浜у崼鏄燂紝鍚屾椂璐熻矗鍙戝皠鎵€闇€鐨勭噧鏂欎笌鐢靛姏鍌ㄥ銆?
 * - 鐢熶骇鏉愭枡锛堥€夋嫨绉嶇被鍚庡紑濮嬬敓浜ф椂涓€娆℃€ф秷鑰楋級锛氶摐 5000銆佺 5000銆佸閽?1250銆佸法娴悎閲?1250銆佸喎鍐绘恫 1000
 * - 鐢熶骇闃舵娑堣€?5000 鐢靛姏/绉掞紙鐢电綉锛夛紱姣忎腑鏋㈠悓鏃跺彧鑳界敓浜?1 棰楋紝瀹屾垚鍚庡仠姝㈣€楃數骞舵樉绀恒€屽彲鍙戝皠鍗槦銆嶆彁绀?
 * - 鍐呯疆 10000 鍙戝皠缂撳啿锛堢數缃戜緵鐢靛厖鐢碉級锛涘彂灏勭噧鏂欑煶娌癸紙1000锛変害鍌ㄥ瓨鍦ㄦ湰涓灑
 * - 鍗槦鐢卞崼鏄熸帶鍒跺彴鐐瑰嚮鍙戝皠
 */
public class SatelliteLauncher extends Block {
    /** 淇″彿鍗槦鐢熶骇鑰楁椂锛坱ick锛夛紝60 绉?*/
    public static final float PRODUCE_TIME_SIGNAL = 60f * 60f;
    /** 娴嬭瘯鍗槦鐢熶骇鑰楁椂锛坱ick锛夛紝1 绉?*/
    public static final float PRODUCE_TIME_TEST = 60f;
    /** 鐢熶骇闃舵鑰楃數锛?绉掞紝Mindustry 鎸?/60 tick 璁★級 */
    public static final float POWER_CONSUMPTION = 5000f / 60f;
    /** 鍙戝皠鎵€闇€缂撳啿鐢靛姏 */
    public static final float LAUNCH_POWER = 10000f;
    /** 缂撳啿鍏呯數閫熺巼锛?绉掞級锛氱數缃戜緵鐢垫椂鍚戠紦鍐插厖鐢?*/
    public static final float CHARGE_RATE = 2000f / 60f;
    /** 鍙戝皠鎵€闇€鐭虫补鐕冩枡 */
    public static final int FUEL_OIL = 1000;
    /** 鐢熶骇鎵€闇€鍐峰喕娑?*/
    public static final int COST_CRYOFLUID = 1000;
    /** 鐢熶骇鎵€闇€鐗╁搧鏉愭枡 */
    public static final ItemStack[] PRODUCTION_ITEMS = with(
            Items.copper, 5000,
            Items.silicon, 5000,
            Items.plastanium, 1250,
            Items.surgeAlloy, 1250
    );

    /** 鍗槦绉嶇被锛氫俊鍙峰崼鏄?*/
    public static final int TYPE_SIGNAL = 0;
    /** 鍗槦绉嶇被锛氭祴璇曞崼鏄燂紙鏉愭枡 1 閾滐紝鏃犲疄闄呮晥鏋滐紝浠呯敤浜庢祴璇曪級 */
    public static final int TYPE_TEST = 1;

    /** 娴嬭瘯鍗槦鐨勭敓浜ф潗鏂欙紙1 閾滐紝鏃犲喎鍐绘恫锛?*/
    public static final ItemStack[] TEST_PRODUCTION_ITEMS = with(Items.copper, 1);

    /** 鎸夌绫昏繑鍥炵敓浜ф墍闇€鐗╁搧鏉愭枡 */
    public static ItemStack[] productionItems(int type) {
        return type == TYPE_TEST ? TEST_PRODUCTION_ITEMS : PRODUCTION_ITEMS;
    }

    /** 鎸夌绫昏繑鍥炵敓浜ф墍闇€鍐峰喕娑?*/
    public static int productionCryofluid(int type) {
        return type == TYPE_TEST ? 0 : COST_CRYOFLUID;
    }

    /** 鎸夌绫昏繑鍥炵敓浜ц€楁椂锛堟祴璇曞崼鏄?1 绉掞紝淇″彿鍗槦 60 绉掞級 */
    public static float produceTime(int type) {
        return type == TYPE_TEST ? PRODUCE_TIME_TEST : PRODUCE_TIME_SIGNAL;
    }

    /** 鏁伴噺鏍煎紡鍖栵紙鍘熺増椋庢牸锛夛細>=1000 鏄剧ず涓?x.xk锛?000鈫?.0k銆?250鈫?.3k銆?000鈫?.0k锛宬 鍚庣紑鐏拌壊锛夛紝灏忎簬 1000 鍘熸牱鏄剧ず */
    static String formatCount(int amount) {
        return amount >= 1000
                ? String.format(Locale.ROOT, "%.1f[gray]k[]", amount / 1000f)
                : String.valueOf(amount);
    }

    /** 鐗╁搧涓嶈冻鎸囩ず锛堝師鐗堢己澶辨牱寮忥級锛氬綋鏉′欢鎴愮珛鏃讹紝鍦ㄧ墿鍝佸浘鏍囦笂缁樺埗涓€鏉″乏涓婂埌鍙充笅鐨勭孩鑹叉枩绾?*/
    static class InsufficientLine extends Element {
        /** 涓嶈冻鍒ゆ柇鏉′欢锛堟瘡甯ф眰鍊硷級 */
        final Boolp condition;

        InsufficientLine(Boolp condition) {
            this.condition = condition;
        }

        @Override
        public void draw() {
            if (!condition.get()) return;
            Draw.color(Pal.remove);
            Draw.rect(Core.atlas.find("white"), x + width / 2f, y + height / 2f, Math.max(2f, width * 0.07f), height * 1.35f, 45f);
            Draw.color();
        }
    }

    public SatelliteLauncher(String name) {
        super(name);
        buildType = SatelliteLauncherBuild::new;
        size = 3;
        solid = true;
        destructible = true;
        update = true;
        configurable = true;
        // 鐢熶骇闃舵鑰楃數锛堢數缃戠洿鑰楋級锛涘彂灏勭敤 10000 缂撳啿鐢辨湰鏂瑰潡鍏呯數绉疮
        consumePower(POWER_CONSUMPTION);
        // 鏉愭枡鍌ㄥ瓨锛堢墿鍝?+ 娑蹭綋锛氱煶娌?鍐峰喕娑诧級
        hasItems = true;
        acceptsItems = true;
        itemCapacity = 5000 + 5000 + 1250 + 1250;
        hasLiquids = true;
        liquidCapacity = FUEL_OIL + COST_CRYOFLUID;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerCapacity, LAUNCH_POWER, StatUnit.powerSecond);
        stats.add(Stat.productionTime, produceTime(TYPE_SIGNAL) / 60f, StatUnit.seconds);
        for (ItemStack stack : PRODUCTION_ITEMS) {
            stats.add(Stat.input, stack);
        }
    }

    public class SatelliteLauncherBuild extends Building {
        /** 褰撳墠閫夋嫨鐨勫崼鏄熺绫伙紙0=淇″彿鍗槦锛?*/
        public int selectedType = TYPE_SIGNAL;
        /** 鐢熶骇杩涘害锛坱ick锛?*/
        public float progress = 0f;
        /** 鍙戝皠缂撳啿鐢甸噺锛?~10000锛岀數缃戜緵鐢垫椂鍏呯數绉疮锛屽彂灏勬椂涓€娆℃€ф秷鑰楋級 */
        public float battery = 0f;
        /** 鏈腑鏋㈡槸鍚﹀凡鐢熶骇瀹屾垚涓€棰楋紙寰呭彂灏勶級 */
        public boolean produced = false;
        /** 鏄惁宸茬櫥璁板埌寰呭彂灏勯槦鍒?*/
        private boolean registered = false;
        /** 閫変腑闈㈡澘闇€姹傛潗鏂欒锛堝垏鎹㈢绫绘椂閲嶅缓锛?*/
        private final Table materialTable = new Table();
        /** 涓婃鏄剧ず鐨勭绫伙紙鐢ㄤ簬妫€娴嬪垏鎹㈠苟閲嶅缓鏉愭枡琛岋級 */
        private int lastShownType = -1;

        @Override
        public void updateTile() {
            // 鏉愭枡琛岄殢绉嶇被瀹炴椂鏇存柊锛堝垏鎹㈢绫诲嵆鏃堕噸寤猴級
            if (selectedType != lastShownType) {
                lastShownType = selectedType;
                rebuildMaterialTable();
            }
            // 鐢电綉鏈夌數鏃跺悜鍙戝皠缂撳啿鍏呯數锛堝彂灏勫偍澶囷級
            if (power != null && power.status > 0.001f && battery < LAUNCH_POWER) {
                battery = Math.min(LAUNCH_POWER, battery + CHARGE_RATE * delta());
            }
            if (produced) {
                // 淇濇寔鐧昏锛堝彂灏勫悗鐢?SatelliteManager 閲嶇疆锛?
                register();
                return;
            }
            // 鏂數涓嶇敓浜э紙杩涘害淇濈暀锛?
            if (power == null || power.status <= 0.001f) return;
            // 鐢熶骇寮€濮嬶細妫€鏌ュ苟涓€娆℃€ф墸闄ゆ潗鏂欙紙杩涘害 > 0 琛ㄧず宸叉墸锛?
            if (progress <= 0f) {
                if (!hasProductionMaterials()) return;
                consumeProductionMaterials();
            }
            progress += delta();
            if (progress >= produceTime(selectedType)) {
                progress = produceTime(selectedType);
                produced = true;
                register();
            }
        }

        /** 鐢熶骇鏉愭枡鏄惁鍏呰冻锛堟寜褰撳墠鎵€閫夌绫伙細鐗╁搧 + 鍐峰喕娑诧級 */
        public boolean hasProductionMaterials() {
            for (ItemStack stack : productionItems(selectedType)) {
                if (items.get(stack.item) < stack.amount) return false;
            }
            return liquids.get(Liquids.cryofluid) >= productionCryofluid(selectedType);
        }

        /** 鎵ｉ櫎鐢熶骇鏉愭枡锛堜竴娆℃€э紝鎸夊綋鍓嶆墍閫夌绫伙級 */
        public void consumeProductionMaterials() {
            for (ItemStack stack : productionItems(selectedType)) {
                items.remove(stack.item, stack.amount);
            }
            liquids.remove(Liquids.cryofluid, productionCryofluid(selectedType));
        }

        void register() {
            if (!registered) {
                SatelliteManager.addReady(this);
                registered = true;
            }
        }

        void unregister() {
            if (registered) {
                SatelliteManager.removeReady(this);
                registered = false;
            }
        }

        /** 鐗╁搧杈撳叆锛氫粎鎺ュ彈鐢熶骇鎵€闇€鏉愭枡锛堥摐/纭?濉戦挗/宸ㄦ氮鍚堥噾锛夛紝涓旀湭婊″簱瀛橈紙override 榛樿鐨?consumesItem 妫€鏌ワ級 */
        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items.get(item) >= itemCapacity) return false;
            for (ItemStack stack : PRODUCTION_ITEMS) {
                if (stack.item == item) return true;
            }
            return false;
        }

        /** 娑蹭綋杈撳叆锛氫粎鎺ュ彈鐭虫补锛堢噧鏂欙級涓庡喎鍐绘恫锛堢敓浜ф潗鏂欙級 */
        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            if (liquids.get(liquid) >= liquidCapacity) return false;
            return liquid == Liquids.oil || liquid == Liquids.cryofluid;
        }

        /** 鍙戝皠鍓嶈祫婧愭鏌ワ細杩斿洖 LAUNCH_OK 鎴栫己澶卞師鍥?*/
        public int checkLaunchResources() {
            if (liquids.get(Liquids.oil) < FUEL_OIL) return SatelliteManager.LAUNCH_NO_FUEL;
            if (battery < LAUNCH_POWER) return SatelliteManager.LAUNCH_NO_POWER;
            return SatelliteManager.LAUNCH_OK;
        }

        /** 鍙戝皠锛氭墸闄ょ噧鏂欎笌缂撳啿鐢靛姏锛岄噸缃湰涓灑浣垮叾鍙啀鐢熶骇锛堢敱 SatelliteManager 璋冪敤锛?*/
        public void consumeLaunchResources() {
            liquids.remove(Liquids.oil, FUEL_OIL);
            battery = Math.max(0f, battery - LAUNCH_POWER);
            // 鍚屾浠庣數缃戠數姹犳墸闄わ紙妯℃嫙鐪熷疄娑堣€楋紝鐢电綉鏃犵數姹犲垯浠呮竻绌烘湰缂撳啿锛?
            if (power != null) power.graph.useBatteries(LAUNCH_POWER);
            resetForLaunch();
        }

        /** 鍗槦鍙戝皠鍚庨噸缃紝浣挎湰涓灑鍙啀鐢熶骇 */
        public void resetForLaunch() {
            produced = false;
            progress = 0f;
            registered = false;
        }

        @Override
        public void onProximityAdded() {
            super.onProximityAdded();
            // 璇绘。鎭㈠锛氬凡鐢熶骇瀹屾垚鐨勪腑鏋㈤噸鏂扮櫥璁?
            if (produced) register();
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            unregister();
        }

        /** 缁樺埗锛氱敓浜у畬鎴愭椂鏂瑰潡涓婃柟鎮诞銆屽彲鍙戝皠銆嶆彁绀猴紙涓嶇粯鍒跺父椹诲崼鏄熷浘鏍囷紝鎸夊師鐗堢畝娲佹樉绀猴級 */
        @Override
        public void draw() {
            super.draw();
            if (produced) {
                Draw.z(35f);
                Draw.rect(Statuses.satelliteBuff.uiIcon, x, y + 16f + Mathf.sin(Time.time / 24f, 3f), 16f, 16f);
                Draw.reset();
            }
        }

        /** 鐘舵€佹樉绀猴細鍘熺増鐘舵€佹潯锛堢己鏉愭枡/鏂數鑷姩鐫€鑹诧級+ 鍘熺増椋庢牸鍒堕€犺繘搴︽潯 + 鐭虫补涓嶈冻鍥炬爣 */
        @Override
        public void drawStatus() {
            // 鍘熺増鐘舵€佹潯锛氬簳閮ㄧ伆鑹叉柟鍧?+ 鐘舵€佽壊锛堢己鏉愭枡=绾€佷緵鐢垫甯?缁匡級锛岀己澶辩墿鍝佺敱姝ゆ樉绀?
            super.drawStatus();
            if (produced) {
                Draw.reset();
                return;
            }
            // 鍒堕€犺繘搴︽潯锛堝師鐗堥鏍硷細鐏板簳 + 寮鸿皟鑹插～鍏咃紝鏂瑰潡椤堕儴锛?
            if (power != null && power.status > 0.001f) {
                float barW = size * 8f - 8f;
                float barH = 2.5f;
                float barY = y + size * 4f + 2f;
                Draw.color(Pal.gray, 0.7f);
                Fill.rect(x, barY, barW, barH);
                float t = Math.min(1f, progress / produceTime(selectedType));
                Draw.color(Pal.accent);
                Fill.rect(x - barW / 2f + barW * t / 2f, barY, barW * t, barH);
            }
            // 鐭虫补涓嶈冻锛氭柟鍧楀乏涓嬭鏄剧ず鐭虫补灏忓浘鏍囷紙鍘熺増缂烘恫浣撻鏍硷級
            if (liquids.get(Liquids.oil) < FUEL_OIL) {
                Draw.rect(Liquids.oil.uiIcon, x - size * 4f + 6f, y - size * 4f + 6f, 8f, 8f);
            }
            Draw.reset();
        }

        /** 閰嶇疆闈㈡澘锛氶€夋嫨鍗槦绉嶇被锛堢敓浜ф墍闇€绉嶇被锛?*/
        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();
            table.top();
            table.add(Core.bundle.get("block.silicon-satellite-launcher.type")).pad(4f);
            table.row();
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            TextButton signalBtn = new TextButton(Core.bundle.get("block.silicon-satellite-launcher.type.signal"), Styles.flatTogglet);
            signalBtn.setChecked(selectedType == TYPE_SIGNAL);
            signalBtn.clicked(() -> selectedType = TYPE_SIGNAL);
            group.add(signalBtn);
            table.add(signalBtn).size(200f, 44f).pad(3f);
            table.row();
            TextButton testBtn = new TextButton(Core.bundle.get("block.silicon-satellite-launcher.type.test"), Styles.flatTogglet);
            testBtn.setChecked(selectedType == TYPE_TEST);
            testBtn.clicked(() -> selectedType = TYPE_TEST);
            group.add(testBtn);
            table.add(testBtn).size(200f, 44f).pad(3f);
        }

        /** 褰撳墠绉嶇被鏄剧ず鍚嶏紙bundle 閿級 */
        String typeNameKey() {
            return selectedType == TYPE_TEST
                    ? "block.silicon-satellite-launcher.type.test" : "block.silicon-satellite-launcher.type.signal";
        }

        /** 閫変腑闈㈡澘锛堟寜鍘熺増绌哄啗宸ュ巶鏍峰紡锛夛細闇€姹傛潗鏂?鐭虫补锛堝浘鏍?鏁伴噺瑙掓爣涓嬭竟缂樺眳涓級銆佽繘搴︽潯銆佺煶娌规潯銆佺數鍔涙潯锛堥暱搴︿笌鍘熺増 bar 涓€鑷达級 */
        @Override
        public void display(Table table) {
            super.display(table);
            table.row();
            // info 琛ㄦ拺婊￠潰鏉垮搴︼紝浣垮悇 bar 闀垮害涓庡師鐗堬紙鐢熷懡鍊肩瓑锛塨ar 涓€鑷达紝鑰岄潪闅忕墿鍝佽瀹藉害鍙樺寲
            table.table(info -> {
                info.left();
                // 闇€姹傛潗鏂?+ 鐭虫补锛堝浘鏍囨í鎺掞紝闇€姹傛暟閲忚鏍囪鐩栧湪鐗╁搧涓嬭竟缂樺眳涓紱鍒囨崲绉嶇被鍗虫椂閲嶅缓锛?
                info.add(materialTable);
                info.row();
                // 鍗槦鍒堕€犺繘搴︽潯
                float total = produceTime(selectedType);
                info.add(new Bar(
                        () -> produced ? Core.bundle.get("block.silicon-satellite-launcher.ready")
                                : Core.bundle.format("block.silicon-satellite-launcher.progress", (int) (Math.min(1f, progress / total) * 100f)),
                        () -> produced ? Pal.accent : Pal.ammo,
                        () -> produced ? 1f : Math.min(1f, progress / total)))
                        .height(18f).growX();
                info.row();
                // 鐭虫补鏉★紙涓庡叾浠?bar 闀垮害缁熶竴锛屽甫璇存槑鏂囧瓧锛氱煶娌圭噧鏂?x/1000锛?
                info.add(new Bar(
                        () -> Core.bundle.format("block.silicon-satellite-launcher.fuel", (int) liquids.get(Liquids.oil), FUEL_OIL),
                        () -> Pal.ammo,
                        () -> Math.min(1f, liquids.get(Liquids.oil) / FUEL_OIL)))
                        .height(14f).growX();
                info.row();
                // 鐢靛姏鏉★紙鍗曠嫭鏄剧ず锛氬彂灏勭紦鍐?Bar锛屼笌鍏朵粬 bar 闀垮害缁熶竴锛屽甫璇存槑鏂囧瓧锛氬彂灏勭紦鍐?xx%锛?
                info.add(new Bar(
                        () -> Core.bundle.format("block.silicon-satellite-launcher.power", (int) (battery / LAUNCH_POWER * 100f)),
                        () -> Pal.power,
                        () -> battery / LAUNCH_POWER))
                        .height(14f).growX();
            }).growX().left();
        }

        /** 閲嶅缓闇€姹傛潗鏂欒锛堟寜鍘熺増锛氬浘鏍?+ 闇€姹傛暟閲忚鏍囷紙宸︿笅瑙掞紝鍗冧綅 k 鏍煎紡锛夛紝涓嶈冻鏃剁孩鑹叉枩绾匡紱鍒囨崲绉嶇被鍗虫椂閲嶅缓锛?*/
        void rebuildMaterialTable() {
            materialTable.clearChildren();
            materialTable.left();
            for (ItemStack stack : productionItems(selectedType)) {
                materialTable.table(r -> {
                    r.left();
                    r.stack(
                            new Image(stack.item.uiIcon),
                            new InsufficientLine(() -> items.get(stack.item) < stack.amount),
                            new Table(t -> t.add(new Label(formatCount(stack.amount)) {{
                                setFontScale(0.8f);
                            }}).expand().bottom().left().padBottom(2f).padLeft(2f))
                    ).size(40f);
                }).padRight(4f);
            }
            if (productionCryofluid(selectedType) > 0) {
                materialTable.table(r -> {
                    r.left();
                    r.stack(
                            new Image(Liquids.cryofluid.uiIcon),
                            new InsufficientLine(() -> liquids.get(Liquids.cryofluid) < COST_CRYOFLUID),
                            new Table(t -> t.add(new Label(formatCount(COST_CRYOFLUID)) {{
                                setFontScale(0.8f);
                            }}).expand().bottom().left().padBottom(2f).padLeft(2f))
                    ).size(40f);
                }).padRight(4f);
            }
            // 鐭虫补锛堝彂灏勭噧鏂欙級锛氬悓鏍峰紡锛岄渶姹傛暟閲?1000 瑙掓爣宸︿笅瑙掞紝涓嶈冻绾㈡枩绾?
            materialTable.table(r -> {
                r.left();
                r.stack(
                        new Image(Liquids.oil.uiIcon),
                        new InsufficientLine(() -> liquids.get(Liquids.oil) < FUEL_OIL),
                        new Table(t -> t.add(new Label(formatCount(FUEL_OIL)) {{
                            setFontScale(0.8f);
                        }}).expand().bottom().left().padBottom(2f).padLeft(2f))
                ).size(40f);
            }).padRight(4f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(selectedType);
            write.f(progress);
            write.bool(produced);
            write.f(battery);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            selectedType = read.i();
            progress = read.f();
            produced = read.bool();
            battery = read.f();
        }
    }
}

