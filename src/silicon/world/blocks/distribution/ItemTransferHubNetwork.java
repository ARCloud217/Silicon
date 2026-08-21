package silicon.world.blocks.distribution;

import arc.struct.Seq;
import mindustry.gen.Building;

public class ItemTransferHubNetwork {
    private static int total = 1;
    public int id;
    public Seq<ItemTransferHub.ItemTransferHubBuild> hubs = new Seq<>();

    public boolean enableDemandPull = true;
    public boolean enableSurplusPush = true;

    public static void resetIdCounter() {
        total = 1;
    }

    public ItemTransferHubNetwork() {
        id = total++;
    }

    public ItemTransferHubNetwork(Seq<ItemTransferHub.ItemTransferHubBuild> hubs) {
        this();
        this.hubs.addAll(hubs);
    }

    public void clear() {
        hubs.clear();
    }

    public static class HubData {
        public final Seq<Building> buildings;
        public final Seq<ItemTransferHub.ItemTransferHubBuild> hubs = new Seq<>();

        public HubData(Seq<Building> buildings) {
            this.buildings = buildings;
        }

        public void add(Building building) {
            if (buildings.contains(building)) return;
            buildings.add(building);
        }

        public void add(ItemTransferHub.ItemTransferHubBuild hubBuild) {
            if (hubs.contains(hubBuild)) return;
            hubs.add(hubBuild);
        }

        public void remove(Building building) {
            buildings.remove(building);
        }

        public void remove(ItemTransferHub.ItemTransferHubBuild hubBuild) {
            hubs.remove(hubBuild);
        }

        public void clear() {
            buildings.clear();
            hubs.clear();
        }
    }
}
