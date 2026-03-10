package me.twojanazwa;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DownedPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> downedPlayers = new HashMap<>();
    private final Map<UUID, BossBar> reviveBars = new HashMap<>();
    private final Map<UUID, BukkitTask> deathTasks = new HashMap<>();
    private final Map<UUID, Long> lastShiftClick = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin na powalenia zostal wlaczony!");
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (player.getHealth() - event.getFinalDamage() <= 0) {
            if (hasTotem(player)) return;

            if (!downedPlayers.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                enterDownedState(player);
            }
        }
    }

    private void enterDownedState(Player player) {
        UUID uuid = player.getUniqueId();
        player.setHealth(2.0);
        player.setSwimming(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1200, 1));
        
        downedPlayers.put(uuid, System.currentTimeMillis() + 60000);

        BukkitTask deathTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (downedPlayers.containsKey(uuid)) {
                    downedPlayers.remove(uuid);
                    player.setHealth(0);
                    player.sendMessage("§cWykrwawiles sie...");
                }
            }
        }.runTaskLater(this, 1200L);
        
        deathTasks.put(uuid, deathTask);
        player.sendMessage("§cZostales powalony! Masz 60s na ratunek.");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (downedPlayers.containsKey(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player helper = event.getPlayer();
        
        if (event.isSneaking()) {
            handleCarry(helper);
        }

        if (event.isSneaking()) {
            for (Entity entity : helper.getNearbyEntities(2, 2, 2)) {
                if (entity instanceof Player && downedPlayers.containsKey(entity.getUniqueId())) {
                    startReviveProcess(helper, (Player) entity);
                    break;
                }
            }
        }
    }

    private void startReviveProcess(Player helper, Player downed) {
        BossBar bar = Bukkit.createBossBar("§aWskrzeszanie: " + downed.getName(), BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(helper);
        reviveBars.put(helper.getUniqueId(), bar);

        new BukkitRunnable() {
            double progress = 0.0;

            @Override
            public void run() {
                if (!helper.isSneaking() || helper.getLocation().distance(downed.getLocation()) > 3 || !downedPlayers.containsKey(downed.getUniqueId())) {
                    bar.removeAll();
                    reviveBars.remove(helper.getUniqueId());
                    this.cancel();
                    return;
                }

                progress += 0.02; // 0.02 co 2 ticki = 10s (50 krokow)
                bar.setProgress(Math.min(progress, 1.0));

                if (progress >= 1.0) {
                    finishRevive(downed);
                    bar.removeAll();
                    reviveBars.remove(helper.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void finishRevive(Player downed) {
        UUID uuid = downed.getUniqueId();
        downedPlayers.remove(uuid);
        if (deathTasks.containsKey(uuid)) {
            deathTasks.get(uuid).cancel();
            deathTasks.remove(uuid);
        }
        downed.setSwimming(false);
        downed.setHealth(6.0);
        downed.sendMessage("§aZostales uratowany!");
    }

    private void handleCarry(Player helper) {
        long now = System.currentTimeMillis();
        long last = lastShiftClick.getOrDefault(helper.getUniqueId(), 0L);
        
        if (now - last < 400) {
            for (Entity entity : helper.getNearbyEntities(2, 2, 2)) {
                if (entity instanceof Player && downedPlayers.containsKey(entity.getUniqueId())) {
                    helper.addPassenger(entity);
                    helper.sendMessage("§6Niesiesz gracza " + entity.getName());
                    break;
                }
            }
        }
        lastShiftClick.put(helper.getUniqueId(), now);
    }

    private boolean hasTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING ||
               player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }
}
