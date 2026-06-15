package silicon.util;

import arc.util.Log;
import silicon.Vars;

public class SiliconLog extends Log {
    private static final Object[] empty = {};

    public static void info(String text, Object... args) {
        log(LogLevel.info, "[" + Vars.name + "] " + text, args);
    }

    public static void info(Object object) {
        info(String.valueOf(object), empty);
    }
}
