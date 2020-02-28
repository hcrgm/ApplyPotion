# GlobalPotionEffect
A Minecraft server (Spigot Server) plugin written to meet my friend's demand.  
应群友一需求写的一个示例 Bukkit 插件.  
Plugin description in English: Apply potion effects globally. Multi worlds and multi potion effects are supported.  
This plugin only supports Spigot version 1.13 or above.  
This plugin uses default configuration file called `config.yml`, which is stored under the plugin data folder.  
The only command `/applypotion reload` can be used to reload the configuration from the disk.  
插件一句话介绍:全局药水效果, 可在多个世界为玩家设置多种全局药水效果  
该群友的需求:  
1.玩家在不同世界获得不同的药水效果  
2.喝牛奶以及死亡不会清除设置的药水效果  
3.切换世界时相应地切换药水效果  
4.喝牛奶不会影响其它效果的清除. 同时插件也不影响为玩家设置的其它效果 (即尽量不影响游戏特性)  

本插件支持通过配置文件进行配置, 可通过命令`/applypotion reload`重载(仅OP可用)  
群友要求的服务器版本是1.14.4, 所以基于1.14.4的API进行开发, 理论上支持1.13及以上版本, 不支持1.12.2及以下版本  
Notice: 本插件原理是通过监听一系列事件来实现的, 优点是实时性高. 也可以通过重复任务的方法来实现, 但实时性略低(感知不强?)  
# License
This project is distributed under [GPL v3](https://www.gnu.org/licenses/gpl-3.0.html) license.  
本项目使用[GPL v3](https://www.gnu.org/licenses/gpl-3.0.html)协议