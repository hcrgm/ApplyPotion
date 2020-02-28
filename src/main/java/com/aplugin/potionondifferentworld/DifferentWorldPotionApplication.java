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

    private boolean debug; // 调试模式开关
    private String messagePrefix; // 提示信息前缀

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
            // 插件停用时清除本插件对玩家的影响
            for (Player p : Bukkit.getOnlinePlayers()) {
                // 获取配置中的药水效果列表
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

    // 玩家改变世界的事件, 用以设置不同世界对应的不同药水效果
    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        // 获取玩家在原世界获得的药水效果
        List<PotionEffect> oldEffects = getPotionEffectsByWorld(e.getFrom().getName());
        List<PotionEffectType> oldEffectTypes = new ArrayList<>();
        for (PotionEffect effect : oldEffects) {
            oldEffectTypes.add(effect.getType());
        }
        // 移除之前的药水效果, 稍后应用新世界的
        for (PotionEffectType type : oldEffectTypes) {
            if (p.hasPotionEffect(type)) {
                p.removePotionEffect(type);
            }
        }
        applyPotionEffect(e.getPlayer());
    }

    // 玩家加入时应用
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        applyPotionEffect(e.getPlayer());
    }

    @EventHandler
    public void onChangePotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        // 若药水效果的改变是命令或其它插件引起的, 则跳出本方法
        if (e.getCause() == EntityPotionEffectEvent.Cause.COMMAND || e.getCause() == EntityPotionEffectEvent.Cause.PLUGIN)
            return;
        switch (e.getCause()) {
            case MILK:
                Bukkit.getScheduler().runTaskLater(this, () -> applyPotionEffect(p), 1L); // 延时设置, 等待效果清除
                break;
            // 药水时间已过
            case EXPIRATION:
                // 是否重新应用
                if (!getConfig().getBoolean("reapply")) break;
                // 获取玩家应获得的药水效果
                List<PotionEffect> desiredEffects = getPotionEffectsByWorld(p.getWorld().getName());
                List<PotionEffectType> desiredEffectTypes = new ArrayList<>();
                for (PotionEffect effect : desiredEffects) {
                    desiredEffectTypes.add(effect.getType());
                }
                if (e.getOldEffect() != null) {
                    // 判断此过期的药水效果是否在配置中指定的药水效果内
                    if (/*e.getOldEffect().getType() == desiredEffect*/desiredEffectTypes.contains(e.getOldEffect().getType())) {
                        e.setCancelled(true); // 取消本事件, 并重新设置药水效果
                        applyPotionEffect(p, true);
                    }
                }
                break;
            default:
                // 其它原因导致的指定药水效果的改变, 根据需求的意思可以不做进一步判断
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

    // 对两种获取药水效果类型的简单包装
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
