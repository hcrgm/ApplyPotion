package com.aplugin.potionondifferentworld;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

public final class DifferentWorldPotionApplication extends JavaPlugin implements Listener {

    private boolean debug;
    private String messagePrefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug");
        messagePrefix = ChatColor.GRAY + "[" + ChatColor.GOLD + getName() + ChatColor.GRAY + "]" + ChatColor.RESET + " ";
        getCommand("applypotion").setExecutor(this);
        applyToAllPlayers();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        messagePrefix = null;
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
        if (!sender.isOp()) {
            sender.sendMessage(messagePrefix + ChatColor.RED + "您没有权限执行此命令");
            return true;
        }
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                debug = getConfig().getBoolean("debug");
                applyToAllPlayers();
                sender.sendMessage(messagePrefix + ChatColor.GREEN + "已重载!");
                return true;
            }
        }
        sender.sendMessage(messagePrefix + ChatColor.GOLD + "重载配置:/" + label + " reload");
        return true;
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        PotionEffectType oldEffect = getPotionEffectTypeByWorld(e.getFrom().getName());
        if (oldEffect != null) {
            if (p.hasPotionEffect(oldEffect)) {
                p.removePotionEffect(oldEffect);
            }
        }
        applyPotionEffect(e.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        applyPotionEffect(e.getPlayer());
    }

    @EventHandler
    public void onChangePotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (e.getCause() == EntityPotionEffectEvent.Cause.COMMAND || e.getCause() == EntityPotionEffectEvent.Cause.PLUGIN)
            return;
        switch (e.getCause()) {
            case MILK:
                Bukkit.getScheduler().runTaskLater(this, () -> applyPotionEffect(p), 1L); // 延时设置, 等待效果清除
                break;
            case EXPIRATION:
                if (!getConfig().getBoolean("reapply")) break;
                PotionEffectType desiredEffect = getPotionEffectTypeByWorld(p.getWorld().getName());
                if (desiredEffect == null) break;
                if (e.getOldEffect() != null) {
                    if (e.getOldEffect().getType() == desiredEffect) {
                        e.setCancelled(true);
                        applyPotionEffect(p, true);
                    }
                }
                break;
            default:
                if (getConfig().getConfigurationSection("worldList").contains(p.getWorld().getName())) {
                    applyPotionEffect(p);
                }
        }
    }

    private void applyPotionEffect(Player p) {
        applyPotionEffect(p, false);
    }

    private void applyPotionEffect(Player p, boolean force) {
        PotionEffectType potionEffectType = getPotionEffectTypeByWorld(p.getWorld().getName());
        if (potionEffectType == null) {
            return;
        }
        int duration = getConfig().getConfigurationSection("worldList").getConfigurationSection(p.getWorld().getName()).getInt("duration") * 20; // 转化为tick
        int amplifier = getConfig().getConfigurationSection("worldList").getConfigurationSection(p.getWorld().getName()).getInt("amplifier");
        p.addPotionEffect(potionEffectType.createEffect(duration, amplifier), force);
    }

    private PotionEffectType getPotionEffectTypeByWorld(String world) {
        if (!getConfig().contains("worldList." + world)) {
            debug("配置worldList中不含指定世界" + world + "!");
            return null;
        }
        if (!getConfig().getConfigurationSection("worldList").getConfigurationSection(world).contains("effect"))
            return null;
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
