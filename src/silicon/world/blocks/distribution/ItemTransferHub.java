package silicon.world.blocks.distribution;

import arc.struct.Seq;
import mindustry.gen.Building;
import mindustry.world.Block;

public class ItemTransferHub extends Block {
    public ItemTransferHub(String name) {
        super(name);
    }

    public class ItemTransferHubBuild extends Building {
        public ItemTransferHubNetwork network = new ItemTransferHubNetwork();
        public ItemTransferHubNetwork.HubData data;

        public ItemTransferHubBuild() {
            super();
            data = new ItemTransferHubNetwork.HubData(new Seq<>());
        }

        public void merge(ItemTransferHubBuild other) {
            network = network.merge(other.network);
            other.network = network;
        }

        public void addLink(ItemTransferHubBuild other) {
            merge(other);
        }

        public void addLink(Building other) {
            data.buildings.add(other);
        }

        public void addLinks(Building[] other) {
            for (Building b : other) {
                if (b instanceof ItemTransferHub.ItemTransferHubBuild hubBuild)
                    data.hubs.add(hubBuild);
                else
                    data.buildings.add(b);
            }
        }

        public void addLinks(Seq<Building> other) {
            addLinks(other.items);
        }

        public void removeLink(ItemTransferHubBuild other) {
            data.hubs.remove(other);
            other.data.hubs.remove(this);
            network.remove(other);
        }

        @Override
        public void updateTile() {
            super.updateTile();
            // TODO: Implement item transfer logic
        }
    }
}
