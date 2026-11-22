package cz.vanoce.xmassanta;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Ovládání NPC z FancyNpcs přes reflexi, aby plugin nebyl vázaný na konkrétní verzi FancyNpcs.
 * Používá: FancyNpcsPlugin.get().getNpcManager().getNpc(jméno), Npc.getData().setLocation(...), Npc.moveForAll().
 */
public final class FancyNpcsBridge {

    private final Object manager;
    private final Method getNpc;
    private final Method getData;
    private final Method setLocation;
    private final Method move;
    private final boolean moveTakesBoolean;

    private FancyNpcsBridge(Object manager, Method getNpc, Method getData, Method setLocation,
                            Method move, boolean moveTakesBoolean) {
        this.manager = manager;
        this.getNpc = getNpc;
        this.getData = getData;
        this.setLocation = setLocation;
        this.move = move;
        this.moveTakesBoolean = moveTakesBoolean;
    }

    public static FancyNpcsBridge create() throws ReflectiveOperationException {
        Plugin fancy = Bukkit.getPluginManager().getPlugin("FancyNpcs");
        if (fancy == null) {
            throw new IllegalStateException("Plugin FancyNpcs není na serveru načtený.");
        }
        ClassLoader loader = fancy.getClass().getClassLoader();

        Class<?> mainClass = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin", true, loader);
        Object instance = mainClass.getMethod("get").invoke(null);
        Object manager = mainClass.getMethod("getNpcManager").invoke(instance);

        Class<?> managerClass = Class.forName("de.oliver.fancynpcs.api.NpcManager", true, loader);
        Class<?> npcClass = Class.forName("de.oliver.fancynpcs.api.Npc", true, loader);
        Class<?> dataClass = Class.forName("de.oliver.fancynpcs.api.NpcData", true, loader);

        Method getNpc = managerClass.getMethod("getNpc", String.class);
        Method getData = npcClass.getMethod("getData");
        Method setLocation = dataClass.getMethod("setLocation", Location.class);

        Method move;
        boolean takesBoolean = false;
        try {
            move = npcClass.getMethod("moveForAll");
        } catch (NoSuchMethodException e) {
            try {
                move = npcClass.getMethod("moveForAll", boolean.class);
                takesBoolean = true;
            } catch (NoSuchMethodException e2) {
                move = npcClass.getMethod("updateForAll");
            }
        }
        return new FancyNpcsBridge(manager, getNpc, getData, setLocation, move, takesBoolean);
    }

    /** @return false, pokud NPC s tímhle jménem neexistuje */
    public boolean move(String npcName, Location location) throws ReflectiveOperationException {
        Object npc = getNpc.invoke(manager, npcName);
        if (npc == null) {
            return false;
        }
        Object data = getData.invoke(npc);
        setLocation.invoke(data, location);
        if (moveTakesBoolean) {
            move.invoke(npc, false);
        } else {
            move.invoke(npc);
        }
        return true;
    }
}
