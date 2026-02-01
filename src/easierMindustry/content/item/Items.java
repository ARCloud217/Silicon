package easierMindustry.content.item;

import arc.graphics.Color;
import easierMindustry.type.Item;

public class Items {

    public static Item thulium = new Item("thulium", Color.rgb(1, 1, 100)) {{
        buildable = true;
        explosiveness = 0.5f;
        alwaysUnlocked = true;
        iconName = "easier-mindustry-frog";
        }};
    public static void load(){}
}