package cz.vanoce.xmassanta;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

public final class CoinListener implements Listener {

    private final SantaPlugin plugin;

    public CoinListener(SantaPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isCoin(SantaPlugin plugin, Item item) {
        var meta = item.getItemStack().getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(plugin.coinKey(), PersistentDataType.BYTE);
    }

    /** Coiny se nedají normálně sebrat (hráčem, mobem ani násypkou). */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isCoin(plugin, event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        if (isCoin(plugin, event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (isCoin(plugin, event.getEntity()) || isCoin(plugin, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** Po připojení znovu schováme coiny, které si hráč už "sebral" (po odpojení se skrytí ztrácí). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.task() != null) {
                plugin.task().hideCollectedFor(player);
            }
        }, 20L);
    }
}
