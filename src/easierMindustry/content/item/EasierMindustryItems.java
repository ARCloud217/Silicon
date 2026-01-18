package easierMindustry.content.item;

import arc.graphics.Color;
import easierMindustry.type.EasierMindustryItem;

public class EasierMindustryItems {

    public static EasierMindustryItem thulium = new EasierMindustryItem("thulium", Color.rgb(1, 1, 100)) {{
        buildable = true;
        explosiveness = 0.5f;
        alwaysUnlocked = true;
        iconName = "easier-mindustry-frog";
        }};
    public static void load(){}
}