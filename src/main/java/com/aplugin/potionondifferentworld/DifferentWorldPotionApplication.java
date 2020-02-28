package com.aplugin.potionondifferentworld;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                List<PotionEffect> desiredEffects = getPotionEffectsByWorld(p.getWorld().getName());
                List<PotionEffectType> desiredEffectTypes = new ArrayList<>();
                for (PotionEffect effect : desiredEffects) {
                    desiredEffectTypes.add(effect.getType());
                }
                for (PotionEffectType type : desiredEffectTypes) {
                    if (p.hasPotionEffect(type)) {
                        p.removePotionEffect(type);
                    }
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
        List<PotionEffect> oldEffects = getPotionEffectsByWorld(e.getFrom().getName());
        List<PotionEffectType> oldEffectTypes = new ArrayList<>();
        for (PotionEffect effect : oldEffects) {
            oldEffectTypes.add(effect.getType());
        }
        for (PotionEffectType type : oldEffectTypes) {
            if (p.hasPotionEffect(type)) {
                p.removePotionEffect(type);
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
                List<PotionEffect> desiredEffects = getPotionEffectsByWorld(p.getWorld().getName());
                List<PotionEffectType> desiredEffectTypes = new ArrayList<>();
                for (PotionEffect effect : desiredEffects) {
                    desiredEffectTypes.add(effect.getType());
                }
                if (e.getOldEffect() != null) {
                    if (/*e.getOldEffect().getType() == desiredEffect*/desiredEffectTypes.contains(e.getOldEffect().getType())) {
                        e.setCancelled(true);
                        applyPotionEffect(p, true);
                    }
                }
                break;
            default:
                if (getConfig().contains("worldList")) {
                    if (getConfig().getConfigurationSection("worldList").contains(p.getWorld().getName())) {
                        applyPotionEffect(p);
                    }
                }
        }
    }

    private void applyPotionEffect(Player p) {
        applyPotionEffect(p, false);
    }

    private void applyPotionEffect(Player p, boolean force) {
        List<PotionEffect> potionEffects = getPotionEffectsByWorld(p.getWorld().getName());
        if (potionEffects.isEmpty()) {
            return;
        }
        for (PotionEffect effect : potionEffects) {
            p.addPotionEffect(effect, force);
        }
    }

    private List<PotionEffect> getPotionEffectsByWorld(String world) {
        List<PotionEffect> effectList = new ArrayList<>();
        if (!getConfig().contains("worldList." + world)) {
            debug("配置worldList中不含指定世界" + world + "!");
            return effectList;
        }
        if (!getConfig().getConfigurationSection("worldList").getConfigurationSection(world).contains("effects"))
            return effectList;
        Map<String, ?> map = getConfig().getConfigurationSection("worldList").getConfigurationSection(world).getConfigurationSection("effects").getValues(false);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) continue;
            PotionEffectType type = getTypeByIdOrName(entry.getKey().toString());
            if (type == null) continue;
            ConfigurationSection potionEffects = (ConfigurationSection) entry.getValue();
            int duration = potionEffects.getInt("duration");
            int amplifier = potionEffects.getInt("amplifier");
            boolean ambient = potionEffects.getBoolean("ambient");
            boolean particles = potionEffects.getBoolean("particles");
            PotionEffect potionEffect = new PotionEffect(type, duration * 20, amplifier, ambient, particles);
            effectList.add(potionEffect);
        }
        return effectList;
    }

    private PotionEffectType getTypeByIdOrName(String nameOrId) {
        PotionEffectType potionEffectType = PotionEffectType.getByName(nameOrId);
        if (potionEffectType == null) {
            int potionEffectTypeId;
            try {
                potionEffectTypeId = Integer.parseInt(nameOrId);
            } catch (NumberFormatException exception) {
                return null;
            }
            potionEffectType = PotionEffectType.getById(potionEffectTypeId);
        }
        return potionEffectType;
    }
}
