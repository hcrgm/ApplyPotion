package com.aplugin.potionondifferentworld;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

public final class DifferentWorldPotionApplication extends JavaPlugin implements Listener {

    private boolean debug;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug");
        getCommand("applypotion").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (getConfig().getBoolean("clear")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PotionEffectType desiredEffect = getPotionEffectTypeByWorld(p.getWorld().getName());
                if (desiredEffect == null) continue;
                if (p.hasPotionEffect(desiredEffect)) {
                    p.removePotionEffect(desiredEffect);
                }
            }
        }
    }

    private void applyToAllPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyPotionEffect(p);
        }
    }

    private void debug(String message) {
        if (debug) {
            getLogger().warning(message);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) return true;
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                debug = getConfig().getBoolean("debug");
                applyToAllPlayers();
                sender.sendMessage(ChatColor.GREEN + "已重载!");
            }
        }
        return true;
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        applyPotionEffect(e.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        applyPotionEffect(e.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        applyPotionEffect(e.getPlayer());
    }

    @EventHandler
    public void onDrinkMilk(PlayerBucketEmptyEvent e) {
        if (e.getBucket() == Material.MILK_BUCKET) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onChangePotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        switch (e.getCause()) {
            case MILK:
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    applyPotionEffect(p); // 等待效果清除, 延时设置
                }, 2L);
                break;
            case EXPIRATION:
                if (!getConfig().getBoolean("reapply")) break;
                PotionEffectType desiredEffect = getPotionEffectTypeByWorld(p.getWorld().getName());
                if (desiredEffect == null) break;
                if (e.getOldEffect().getType() == desiredEffect) {
                    applyPotionEffect(p);
                }
                break;
        }
    }

    /*
     此代码可能产生bug, 舍弃
     Player p = (Player) e.getEntity();
            PotionEffectType desiredEffect = getPotionEffectTypeByWorld(p.getWorld().getName());
            if (desiredEffect == null) return;
            switch (e.getAction()) {
                case CHANGED:
                    if (!p.hasPotionEffect(desiredEffect)) {
                        applyPotionEffect(p);;
                    }
                    break;
                case CLEARED:
                    applyPotionEffect(p);
                    break;
                case REMOVED:
                    if (e.getOldEffect().getType() == desiredEffect) {
                        applyPotionEffect(p);
                    }
                    break;
            }
     */

    private void applyPotionEffect(Player p) {
        PotionEffectType potionEffectType = getPotionEffectTypeByWorld(p.getWorld().getName());
        if (potionEffectType == null) {
            return;
        }
        int duration = getConfig().getConfigurationSection("worldList").getConfigurationSection(p.getWorld().getName()).getInt("duration");
        int amplifier = getConfig().getConfigurationSection("worldList").getConfigurationSection(p.getWorld().getName()).getInt("amplifier");
        Collection<PotionEffect> playerEffects = p.getActivePotionEffects();
        for (PotionEffect effect : playerEffects) {
            p.removePotionEffect(effect.getType());
        }
        p.addPotionEffect(potionEffectType.createEffect(duration, amplifier));
    }

    private PotionEffectType getPotionEffectTypeByWorld(String world) {
        if (!getConfig().contains("worldList." + world)) {
            debug("配置worldList中不含指定世界" + world + "!");
            return null;
        }
        String potionEffectName = getConfig().getConfigurationSection("worldList").getConfigurationSection(world).getString("effect");
        PotionEffectType potionEffectType = PotionEffectType.getByName(potionEffectName);
        if (potionEffectType == null) {
            int potionEffectTypeId;
            try {
                potionEffectTypeId = Integer.parseInt(potionEffectName);
            } catch (NumberFormatException exception) {
                debug("无法解析配置中" + world + "的药水效果名或ID - 不是一个有效的数字");
                return null;
            }
            potionEffectType = PotionEffectType.getById(potionEffectTypeId);
            if (potionEffectType == null) {
                debug("无法解析配置中" + world + "的药水效果名或ID - 无法根据ID:" + potionEffectTypeId + "找到对应的药水效果");
                return null;
            }
        }
        return potionEffectType;
    }
}
