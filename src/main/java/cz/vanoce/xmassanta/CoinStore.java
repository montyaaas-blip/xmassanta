package cz.vanoce.xmassanta;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ukládá počet coinů každého hráče do coins.yml. */
public final class CoinStore {

    private final SantaPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> coins = new HashMap<>();
    private boolean dirty;

    public CoinStore(SantaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "coins.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("coins");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    coins.put(UUID.fromString(key), section.getInt(key));
                } catch (IllegalArgumentException ignored) {
                    // poškozený záznam přeskočíme
                }
            }
        }
    }

    public int get(UUID id) {
        return coins.getOrDefault(id, 0);
    }

    public int add(UUID id, int amount) {
        int total = get(id) + amount;
        coins.put(id, total);
        dirty = true;
        return total;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : coins.entrySet()) {
            yaml.set("coins." + entry.getKey(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Nepodařilo se uložit coins.yml: " + e.getMessage());
        }
    }
}
