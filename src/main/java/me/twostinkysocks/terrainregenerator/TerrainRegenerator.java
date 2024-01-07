package me.twostinkysocks.terrainregenerator;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class TerrainRegenerator extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private ArrayList<BukkitTask> tasks;

    public void onEnable() {
        tasks = new ArrayList<>();
        this.getCommand("terrainregenerator").setExecutor(this);
        this.getCommand("terrainregenerator").setTabCompleter(this);
        this.getCommand("regenschematic").setExecutor(this);
        this.getCommand("regenschematic").setTabCompleter(this);
        this.getServer().getPluginManager().registerEvents(this, this);
        this.load();
    }


    public int load() {
        for(BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if(!this.getDataFolder().exists()) {
            this.getDataFolder().mkdir();
        }
        File schematicsFolder = new File(this.getDataFolder(), "schematics");
        if(!schematicsFolder.exists()) {
            schematicsFolder.mkdir();
        }
        File config = new File(this.getDataFolder(), "config.yml");
        if(!config.exists()) {
            saveDefaultConfig();
        }
        reloadConfig();
        if(getConfig().getBoolean("disabled", false)) {
            getLogger().warning("TerrainRegenerator is disabled in the config!");
            return -1;
        }
        // schematics
        for(Object schem : getConfig().getConfigurationSection("schematics").getKeys(false).toArray()) {
            int timerseconds = getConfig().getInt("schematics." + schem + ".timer-seconds");
            int firstWarningDelaySeconds = getConfig().getInt("schematics." + schem + ".first-warning-delay-seconds");
            int secondWarningDelaySeconds = getConfig().getInt("schematics." + schem + ".second-warning-delay-seconds");
            File schemFile = new File(this.getDataFolder(), "schematics/" + schem + ".schem");
            // starts
            tasks.add(Bukkit.getScheduler()
                    .runTaskTimer(
                            this,
                            () -> this.regenerateNew(schemFile),
                            (timerseconds * 20)+(firstWarningDelaySeconds*20),
                            timerseconds * 20
                    ));
            tasks.add(Bukkit.getScheduler()
                    .runTaskTimer(
                            this,
                            () -> this.firstWarning((String) schem),
                            (timerseconds * 20),
                            timerseconds * 20
                    )
            );
            tasks.add(Bukkit.getScheduler()
                    .runTaskTimer(
                            this,
                            () -> this.secondWarning((String) schem),
                            (timerseconds * 20)+((firstWarningDelaySeconds*20)-(secondWarningDelaySeconds*20)),
                            timerseconds * 20
                    )
            );
            this.getLogger().info("Started timer for schematic " + schem);
        }

        return getConfig().getConfigurationSection("schematics").getKeys(false).toArray().length;
    }

    private void firstWarning(String name) {
        if(getConfig().getString("schematics." + name + ".first-respawn-warning-broadcast") != null) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("schematics." + name + ".first-respawn-warning-broadcast")));
        }
    }

    private void secondWarning(String name) {
        if(getConfig().getString("schematics." + name + ".second-respawn-warning-broadcast") != null) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("schematics." + name + ".second-respawn-warning-broadcast")));
        }
    }

    private void regenerateNew(File schematic) {
        if(schematic.exists()) {
            String filename = schematic.getName().replace(".schem", "");
            Clipboard clipboard;
            ClipboardFormat format = ClipboardFormats.findByFile(schematic);
            try(ClipboardReader reader = format.getReader(new FileInputStream(schematic))) {
                clipboard = reader.read();
                EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(Bukkit.getWorld(getConfig().getString("schematics." + filename + ".world"))));
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(
                                getConfig().getInt("schematics." + filename + ".x"),
                                getConfig().getInt("schematics." + filename + ".y"),
                                getConfig().getInt("schematics." + filename + ".z")
                        ))
                        .copyEntities(getConfig().getBoolean("schematics." + filename + ".includeentities"))
                        .ignoreAirBlocks(getConfig().getBoolean("schematics." + filename + ".ignoreairblocks"))
                        .build();
                if(getConfig().getBoolean("schematics." + filename + ".removecrystals", false)) {
                    for(Entity e : editSession.getEntities()) {
                        if(e.getState() != null && BukkitAdapter.adapt(e.getState().getType()) == EntityType.ENDER_CRYSTAL) {
                            e.remove();
                        }
                    }
                }
                Operations.complete(operation);
                editSession.flushSession();
                if(getConfig().getString("schematics." + filename + ".respawn-broadcast") != null) {
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("schematics." + filename + ".respawn-broadcast")));
                }
                if(getConfig().getString("schematics." + filename + ".command") != null) {
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), getConfig().getString("schematics." + filename + ".command"));
                }
            } catch (IOException | WorldEditException e) {
                e.printStackTrace();
            }
        } else {
            this.getLogger().severe("Schematic: " + schematic + " was not found");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            CommandSender p = sender;
            if(label.equals("terrainregenerator")) {
                if(!p.hasPermission("terrainregenerator.reload")) {
                    p.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                if(args.length == 0 || !args[0].equals("reload")) {
                    p.sendMessage(ChatColor.GRAY + "Use /terrainregenerator reload to reload config files");
                } else {
                    int num = load();
                    if(num == -1) {
                        p.sendMessage("Reloaded 0 schematics! (Plugin is disabled in the config)");
                        return true;
                    }
                    p.sendMessage("Reloaded " + num + " schematics!");
                }
                return true;
            } else if(label.equals("regenschematic")) {
                if(!p.hasPermission("terrainregenerator.regenschematic")) {
                    p.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                if(args.length == 0 || !getConfig().contains("schematics." + args[0])) {
                    p.sendMessage(ChatColor.RED + "Invalid schematic name");
                    return true;
                }
                File schemFile = new File(this.getDataFolder(), "schematics/" + args[0] + ".schem");
                p.sendMessage(ChatColor.AQUA + "Manually regenerating schematic...");
                this.regenerateNew(schemFile);
            }
        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if(alias.equals("terrainregenerator")) {
            return List.of("reload");
        } else if(alias.equals("regenschematic")) {
            if(args.length == 1) {
                StringUtil.copyPartialMatches(args[0], getConfig().getConfigurationSection("schematics").getKeys(false), completions);
                return completions;
            }
        }
        return List.of();
    }
}