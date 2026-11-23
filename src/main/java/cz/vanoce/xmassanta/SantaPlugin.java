package cz.vanoce.xmassanta;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SantaPlugin extends JavaPlugin implements CommandExecutor {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private CoinStore store;
    private SantaTask task;
    private NamespacedKey coinKey;
    private NamespacedKey coinIdKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new CoinStore(this);
        coinKey = new NamespacedKey(this, "coin");
        coinIdKey = new NamespacedKey(this, "coin_id");

        getServer().getPluginManager().registerEvents(new CoinListener(this), this);
        PluginCommand command = getCommand("coins");
        if (command != null) {
            command.setExecutor(this);
        }

        // FancyNpcs načítá NPC až po startu serveru, proto chvíli počkáme
        getServer().getScheduler().runTaskLater(this, this::startTask, 200L);
        getServer().getScheduler().runTaskTimer(this, store::saveIfDirty, 600L, 600L);
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.stop();
        }
        if (store != null) {
            store.save();
        }
    }

    private void startTask() {
        if (task != null) {
            task.stop();
        }
        try {
            task = new SantaTask(this, store, FancyNpcsBridge.create());
            task.start();
            getLogger().info("Santa se rozešel na cestu.");
        } catch (RuntimeException e) {
            getLogger().severe("Nepodařilo se napojit na FancyNpcs (" + e + "). Santa se nehýbe. "
                    + "Pošli tuhle chybu autorovi pluginu.");
        }
    }

    public NamespacedKey coinKey() {
        return coinKey;
    }

    public NamespacedKey coinIdKey() {
        return coinIdKey;
    }

    public SantaTask task() {
        return task;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xmassanta.admin")) {
                sender.sendMessage(MM.deserialize("<red>Na to nemáš oprávnění."));
                return true;
            }
            reloadConfig();
            startTask();
            sender.sendMessage(MM.deserialize("<green>Konfigurace načtena."));
            return true;
        }
        if (sender instanceof Player player) {
            player.sendMessage(MM.deserialize("<gold>Tvoje vánoční coiny: <white><n>",
                    Placeholder.unparsed("n", String.valueOf(store.get(player.getUniqueId())))));
        } else {
            sender.sendMessage(MM.deserialize("<red>Tento příkaz může použít jen hráč."));
        }
        return true;
    }
}
