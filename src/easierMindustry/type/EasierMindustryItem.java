package easierMindustry.type;

import arc.Core;
import arc.graphics.Color;
import mindustry.type.Item;

public class EasierMindustryItem extends Item {
    public String iconName;

    public EasierMindustryItem(String name) {
        super(name);
    }

    public EasierMindustryItem(String name, Color color) {
        super(name, color);
    }
    public void loadIcon(String fullIconName){
        fullIcon = Core.atlas.find(fullIconName);
        uiIcon = fullIcon;
    }

    @Override
    public void loadIcon(){
        super.loadIcon();
        if (iconName != null){
            fullIcon = Core.atlas.find(iconName);
            uiIcon = Core.atlas.find(iconName);
        }
    }
}
