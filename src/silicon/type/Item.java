package silicon.type;

import arc.Core;
import arc.graphics.Color;

public class Item extends mindustry.type.Item {
    public String iconName;

    public Item(String name) {
        super(name);
    }

    public Item(String name, Color color) {
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
