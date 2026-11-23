package cz.vanoce.xmassanta;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Locale;

/**
 * Ovládá NPC z FancyNpcs přes jeho vlastní, zdokumentované příkazy (/npc move_to, /npc rotate),
 * ne přes reflexi na nezdokumentované vnitřní API.
 *
 * Dřívější verze hádala přes reflexi, jak NPC posunout, protože FancyNpcs svoje API pro pohyb
 * nikde nepopisuje. To se ukázalo jako nespolehlivé - NPC se po čase rozcházelo s tím, kde ho
 * FancyNpcs doopravdy zobrazuje hráčům (typicky se to projeví jako "teleport" po desítkách minut
 * až hodinách běhu). Příkazy /npc move_to a /npc rotate jsou oficiálně zdokumentované a dělají
 * přesně to, co dělá admin ručně ve hře, takže by měly fungovat spolehlivě i po dlouhé době.
 */
public final class FancyNpcsBridge {

    private FancyNpcsBridge() {
    }

    public static FancyNpcsBridge create() {
        if (Bukkit.getPluginManager().getPlugin("FancyNpcs") == null) {
            throw new IllegalStateException("Plugin FancyNpcs není na serveru načtený.");
        }
        return new FancyNpcsBridge();
    }

    /**
     * Přesune a natočí NPC. Pokud NPC s tímhle jménem neexistuje, FancyNpcs o tom sám
     * napíše do konzole - zvlášť to tady nekontrolujeme.
     */
    public void move(String npcName, Location location, float yaw) {
        String world = location.getWorld().getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(Locale.ROOT,
                "npc move_to %s %.3f %.3f %.3f %s", npcName, location.getX(), location.getY(), location.getZ(), world));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(Locale.ROOT,
                "npc rotate %s %.1f 0", npcName, yaw));
    }
}
