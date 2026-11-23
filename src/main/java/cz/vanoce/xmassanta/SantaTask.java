package cz.vanoce.xmassanta;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hlavní smyčka: každé 2 ticky posune Santu (a soby za ním), občas upustí coin
 * a zkontroluje, jestli některý hráč nestojí u coinu.
 */
public final class SantaTask implements Runnable {

    private static final int PERIOD_TICKS = 2;

    /** Pozice na trase: odsazení do strany, z a natočení. */
    private record Pose(double dx, double z, float yaw) {}

    private static final class Coin {
        final Item item;
        final long expiresAt;
        final Set<UUID> collectedBy = new HashSet<>();

        Coin(Item item, long expiresAt) {
            this.item = item;
            this.expiresAt = expiresAt;
        }
    }

    private final SantaPlugin plugin;
    private final CoinStore store;
    private final FancyNpcsBridge bridge;
    private final List<Pose> history = new ArrayList<>();
    private final List<Coin> coins = new ArrayList<>();
    private BukkitTask task;

    // Nastavení načtené z configu
    private String worldName;
    private double pathX;
    private double pathY;
    private double startZ;
    private double endZ;
    private double laneOffset;
    private String santaName;
    private List<String> reindeer;
    private int spacingSteps;
    private double stepLength;
    private double activeRadius;
    private int dropEverySteps;
    private long lifetimeMillis;
    private double pickupRadius;
    private int npcUpdateEverySteps;

    // Stav pohybu
    private double distance;
    private int direction = 1;
    private double length;
    private double sign;
    private int stepsSinceDrop;
    private int stepsSinceNpcUpdate;

    public SantaTask(SantaPlugin plugin, CoinStore store, FancyNpcsBridge bridge) {
        this.plugin = plugin;
        this.store = store;
        this.bridge = bridge;
        readConfig();
    }

    private void readConfig() {
        FileConfiguration c = plugin.getConfig();
        worldName = c.getString("world", "Christmas");
        pathX = c.getDouble("path.x", -377);
        pathY = c.getDouble("path.y", 81.0);
        startZ = c.getDouble("path.start-z", 10);
        endZ = c.getDouble("path.end-z", -70);
        laneOffset = c.getDouble("path.return-lane-offset", 1.5);
        santaName = c.getString("npcs.santa", "santa");
        reindeer = c.getStringList("npcs.reindeer");
        spacingSteps = Math.max(1, c.getInt("npcs.reindeer-spacing-steps", 14));
        stepLength = c.getDouble("walk-speed", 2.5) * PERIOD_TICKS / 20.0;
        activeRadius = c.getDouble("active-radius", 120);
        dropEverySteps = Math.max(1, c.getInt("coins.drop-every-steps", 15));
        lifetimeMillis = c.getLong("coins.lifetime-seconds", 45) * 1000L;
        pickupRadius = c.getDouble("coins.pickup-radius", 1.2);
        // FancyNpcs umí NPC jen teleportovat (viz /npc move_to), ne nechat ho jít.
        // Kdybychom teleportovali při každém kroku (10x/s), vypadalo by to jako blikání/škubání -
        // proto se poloha NPC posílá jen tak často, jak je tu nastaveno.
        double npcUpdateSeconds = c.getDouble("npc-update-seconds", 1.0);
        npcUpdateEverySteps = Math.max(1, (int) Math.round(npcUpdateSeconds * 20.0 / PERIOD_TICKS));

        length = Math.abs(endZ - startZ);
        sign = Math.signum(endZ - startZ);
    }

    public void start() {
        sweepOldCoins();
        // Na začátku všichni stojí na startu trasy
        Pose startPose = poseFor(0, 1);
        int size = spacingSteps * (reindeer.size() + 1) + 2;
        for (int i = 0; i < size; i++) {
            history.add(startPose);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Coin coin : coins) {
            coin.item.remove();
        }
        coins.clear();
    }

    @Override
    public void run() {
        World world = Bukkit.getWorld(worldName);
        if (world == null || !playerNearby(world)) {
            return;
        }
        step(world);
        tickCoins(world);
    }

    // ------------------------------------------------------------------
    // Pohyb
    // ------------------------------------------------------------------

    private void step(World world) {
        distance += direction * stepLength;
        if (distance >= length) {
            distance = length;
            direction = -1;
        } else if (distance <= 0) {
            distance = 0;
            direction = 1;
        }

        Pose pose = poseFor(distance, direction);
        history.add(0, pose);
        while (history.size() > spacingSteps * (reindeer.size() + 1) + 2) {
            history.remove(history.size() - 1);
        }

        // FancyNpcs umí NPC jen teleportovat (žádná plynulá chůze), takže teleport
        // posíláme jen občas (viz npc-update-seconds), ne při každém dílčím kroku -
        // jinak by to blikalo/škubalo se, přesně jak to vypadalo předtím.
        stepsSinceNpcUpdate++;
        if (stepsSinceNpcUpdate >= npcUpdateEverySteps) {
            stepsSinceNpcUpdate = 0;
            moveNpc(world, santaName, pose);
            for (int i = 0; i < reindeer.size(); i++) {
                int index = Math.min(history.size() - 1, spacingSteps * (i + 1));
                moveNpc(world, reindeer.get(i), history.get(index));
            }
        }

        stepsSinceDrop++;
        if (stepsSinceDrop >= dropEverySteps) {
            stepsSinceDrop = 0;
            dropCoin(world, pose);
        }
    }

    private Pose poseFor(double dist, int dir) {
        double z = startZ + sign * dist;
        // Směr chůze v ose z: kladný = jih (yaw 0), záporný = sever (yaw 180)
        float yaw = (dir * sign) >= 0 ? 0f : 180f;
        double dx = dir < 0 ? laneOffset : 0;
        return new Pose(dx, z, yaw);
    }

    private void moveNpc(World world, String name, Pose pose) {
        Location location = new Location(world, pathX + pose.dx(), pathY, pose.z());
        bridge.move(name, location, pose.yaw());
    }

    private boolean playerNearby(World world) {
        // Vzdálenost od středu trasy; dosah = active-radius + půl délky trasy
        double reach = activeRadius + length / 2.0;
        double centerZ = (startZ + endZ) / 2.0;
        for (Player player : world.getPlayers()) {
            Location l = player.getLocation();
            double dx = l.getX() - pathX;
            double dz = l.getZ() - centerZ;
            if (dx * dx + dz * dz <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Coiny
    // ------------------------------------------------------------------

    private void dropCoin(World world, Pose pose) {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET);
        stack.editMeta(meta -> {
            meta.displayName(SantaPlugin.MM.deserialize(plugin.getConfig().getString("coins.name", "<gold>Coin")));
            meta.getPersistentDataContainer().set(plugin.coinKey(), PersistentDataType.BYTE, (byte) 1);
            // Unikátní ID, aby se coiny nesloučily do jednoho stacku
            meta.getPersistentDataContainer().set(plugin.coinIdKey(), PersistentDataType.STRING,
                    UUID.randomUUID().toString());
        });

        Location where = new Location(world, pathX + pose.dx(), pathY + 0.3, pose.z());
        Item item = world.dropItem(where, stack, dropped -> {
            dropped.setPickupDelay(32767);
            dropped.setUnlimitedLifetime(true);
            dropped.setVelocity(new Vector(0, 0.2, 0));
            dropped.customName(SantaPlugin.MM.deserialize(
                    plugin.getConfig().getString("coins.name", "<gold>Coin")));
            dropped.setCustomNameVisible(true);
            dropped.setGlowing(true);
        });
        coins.add(new Coin(item, System.currentTimeMillis() + lifetimeMillis));
    }

    /** Falešný pickup: hráč, který přijde ke coinu, ho dostane a pro něj coin zmizí (ostatním zůstává). */
    private void tickCoins(World world) {
        long now = System.currentTimeMillis();
        Iterator<Coin> iterator = coins.iterator();
        while (iterator.hasNext()) {
            Coin coin = iterator.next();
            if (!coin.item.isValid() || now >= coin.expiresAt) {
                coin.item.remove();
                iterator.remove();
                continue;
            }
            Vector position = coin.item.getLocation().toVector();
            for (Player player : world.getPlayers()) {
                if (coin.collectedBy.contains(player.getUniqueId())
                        || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }
                if (player.getBoundingBox().clone().expand(pickupRadius).contains(position)) {
                    collect(player, coin);
                }
            }
        }
    }

    private void collect(Player player, Coin coin) {
        coin.collectedBy.add(player.getUniqueId());
        player.hideEntity(plugin, coin.item);

        int total = store.add(player.getUniqueId(), 1);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.6f);
        player.spawnParticle(Particle.END_ROD, coin.item.getLocation(), 6, 0.2, 0.2, 0.2, 0.02);
        player.sendActionBar(SantaPlugin.MM.deserialize(
                plugin.getConfig().getString("coins.message", "<gold>+1 coin"),
                Placeholder.unparsed("total", String.valueOf(total))));

        for (String raw : plugin.getConfig().getStringList("coins.commands")) {
            String command = raw.replace("%player%", player.getName()).trim();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (!command.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
    }

    public void hideCollectedFor(Player player) {
        for (Coin coin : coins) {
            if (coin.collectedBy.contains(player.getUniqueId()) && coin.item.isValid()) {
                player.hideEntity(plugin, coin.item);
            }
        }
    }

    /** Smaže coiny, které po pádu nebo restartu serveru zůstaly ve světě. */
    private void sweepOldCoins() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (CoinListener.isCoin(plugin, item)) {
                item.remove();
            }
        }
    }
}
