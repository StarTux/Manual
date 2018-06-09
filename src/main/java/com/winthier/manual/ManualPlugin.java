package com.winthier.manual;

import com.winthier.custom.CustomPlugin;
import com.winthier.custom.event.CustomRegisterEvent;
import com.winthier.custom.util.Dirty;
import com.winthier.custom.util.Items;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONValue;

public final class ManualPlugin extends JavaPlugin implements Listener {
    private final Map<String, Manual> manuals = new HashMap<>();

    @Value
    static class Manual {
        private String name;
        private int version;
        private Map<String, Object> itemTag;
        private ItemStack item;
    }

    @Override
    public void onEnable() {
        new File(getDataFolder(), "books").mkdirs();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        manuals.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        final Player player = sender instanceof Player ? (Player)sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "give":
            if (args.length >= 2) {
                Manual manual = getManual(args[1]);
                if (manual == null) {
                    sender.sendMessage("Manual not found: " + args[1]);
                    return true;
                }
                final Player target;
                if (args.length >= 3) {
                    target = getServer().getPlayerExact(args[2]);
                    if (target == null) {
                        sender.sendMessage("Player not found: " + args[2]);
                        return true;
                    }
                } else if (player == null) {
                    sender.sendMessage("Player required");
                    return true;
                } else {
                    target = player;
                }
                ItemStack item = CustomPlugin.getInstance().getItemManager().spawnItemStack(ManualItem.CUSTOM_ID, 1);
                ManualItem.setManual(item, manual);
                for (ItemStack drop: target.getInventory().addItem(item).values()) {
                    Items.give(drop, target);
                }
                sender.sendMessage("Manual " + manual.getName() + " given to " + target.getName());
            }
            break;
        case "reload":
            manuals.clear();
            sender.sendMessage("Manuals reloaded.");
            break;
        case "info":
        case "name":
            if (player != null) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getAmount() == 0) {
                    player.sendMessage("Hand is empty!");
                    return true;
                }
                String customId = CustomPlugin.getInstance().getItemManager().getCustomId(item);
                if (customId == null || !customId.equals(ManualItem.CUSTOM_ID)) {
                    player.sendMessage("No manual in hand!");
                    return true;
                }
                player.sendMessage("Manual in hand: " + ManualItem.getName(item));
            }
            break;
        case "reloadhand":
            if (player != null) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getAmount() == 0) {
                    player.sendMessage("Hand is empty!");
                    return true;
                }
                String customId = CustomPlugin.getInstance().getItemManager().getCustomId(item);
                if (customId == null || !customId.equals(ManualItem.CUSTOM_ID)) {
                    player.sendMessage("No manual in hand!");
                    return true;
                }
                String name = ManualItem.getName(item);
                Manual manual = createManual(name);
                if (manual == null) {
                    player.sendMessage("Manual not found: " + name);
                    return true;
                }
                ManualItem.setManual(item, manual);
                player.sendMessage("Updated item in hand with " + manual.getName() + " Version " + manual.getVersion());
            }
            break;
        case "list":
            sender.sendMessage("All loaded manuals:");
            for (Manual manual2: manuals.values()) {
                sender.sendMessage("- " + manual2.getName() + " Version " + manual2.getVersion());
            }
            break;
        default:
            return false;
        }
        return true;
    }

    @EventHandler
    public void onCustomRegister(CustomRegisterEvent event) {
        event.addItem(new ManualItem(this));
    }

    static String format(String str) {
        if (str == null) return "";
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    Manual getManual(String name) {
        if (manuals.containsKey(name)) {
            return manuals.get(name); // May yield null
        } else {
            Manual result = createManual(name);
            manuals.put(name, result); // May store null
            return result;
        }
    }

    ItemStack spawnBook(String name) {
        Manual manual = getManual(name);
        if (manual == null) return null;
        return manual.getItem().clone();
    }

    Manual createManual(String name) {
        File file = new File(new File(getDataFolder(), "books"), name + ".yml");
        if (!file.isFile() || !file.canRead()) return null;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<List<Object>> pages = new ArrayList<>();
        Map<String, Integer> anchors = new HashMap<>();
        Map<String, Integer> chapters = new LinkedHashMap<>();
        List<Map<String, Object>> references = new ArrayList<>();
        List<Object> pageList = (List<Object>)config.getList("pages");
        if (pageList == null || pageList.isEmpty()) return null;
        List<Object> page = new ArrayList<>();
        page.add("");
        for (Object o: pageList) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                final ConfigurationSection section = config.createSection("tmp", (Map<?, ?>)o);
                Map map = new HashMap<>();
                if (section.isSet("chapter")) {
                    String chapterName = section.getString("chapter");
                    chapters.put(chapterName, pages.size());
                    anchors.put(chapterName, pages.size());
                }
                if (section.isSet("anchor")) {
                    anchors.put(section.getString("anchor"), pages.size());
                }
                if (section.isSet("text")) {
                    map.put("text", format(section.getString("text")));
                }
                final String[] ims = {"color", "strikethrough", "underlined", "bold", "italic", "obfuscated", "insertion"};
                for (String key: ims) {
                    if (section.isSet(key)) {
                        map.put(key, section.get(key));
                    }
                }
                if (section.isSet("tooltip")) {
                    Map<String, Object> tooltip = new HashMap<>();
                    tooltip.put("action", "show_text");
                    tooltip.put("value", format(section.getString("tooltip")));
                    map.put("hoverEvent", tooltip);
                }
                if (section.isSet("command")) {
                    Map<String, Object> command = new HashMap<>();
                    command.put("action", "run_command");
                    command.put("value", section.getString("command"));
                    map.put("clickEvent", command);
                }
                if (section.isSet("url")) {
                    Map<String, Object> url = new HashMap<>();
                    url.put("action", "open_url");
                    url.put("value", section.getString("url"));
                    map.put("clickEvent", url);
                }
                if (section.isSet("page")) {
                    Map<String, Object> pageLink = new HashMap<>();
                    pageLink.put("action", "change_page");
                    pageLink.put("value", section.getInt("page"));
                    map.put("clickEvent", pageLink);
                }
                if (section.isSet("reference")) {
                    Map<String, Object> reference = new HashMap<>();
                    reference.put("action", "change_page");
                    reference.put("value", section.getString("reference"));
                    map.put("clickEvent", reference);
                    references.add(reference);
                }
                if (!map.isEmpty()) page.add(map);
            } else if (o instanceof String) {
                String par = (String)o;
                try {
                    Pattern pattern = Pattern.compile("\\{[^}]+\\}");
                    Matcher matcher = pattern.matcher(par);
                    int prevEnd = 0;
                    while (matcher.find()) {
                        String group = matcher.group();
                        group = group.substring(1, group.length() - 1);
                        String[] toks = group.split("\\|");
                        Map<String, Object> tag = new HashMap<>();
                        switch (toks[0].toLowerCase()) {
                        case "command":
                            if (toks.length >= 3 && toks.length <= 4) {
                                tag.put("text", format(toks[1]) + ChatColor.RESET);
                                Map<String, Object> event = new HashMap<>();
                                event.put("action", "run_command");
                                event.put("value", toks[2]);
                                tag.put("clickEvent", event);
                            }
                            if (toks.length >= 4) {
                                Map<String, Object> event = new HashMap<>();
                                event.put("action", "show_text");
                                event.put("value", format(toks[3]));
                                tag.put("hoverEvent", event);
                            }
                            break;
                        case "url":
                            if (toks.length >= 3 && toks.length <= 4) {
                                tag.put("text", format(toks[1]) + ChatColor.RESET);
                                Map<String, Object> event = new HashMap<>();
                                event.put("action", "open_url");
                                event.put("value", toks[2]);
                                tag.put("clickEvent", event);
                            }
                            if (toks.length >= 4) {
                                Map<String, Object> event = new HashMap<>();
                                event.put("action", "show_text");
                                event.put("value", format(toks[3]));
                                tag.put("hoverEvent", event);
                            }
                            break;
                        case "tooltip":
                            if (toks.length == 3) {
                                tag.put("text", format(toks[1]) + ChatColor.RESET);
                                Map<String, Object> event = new HashMap<>();
                                event.put("action", "show_text");
                                event.put("value", format(toks[2]));
                                tag.put("hoverEvent", event);
                            }
                            break;
                        case "page":
                            if (toks.length >= 3 && toks.length <= 4) {
                                tag.put("text", format(toks[1]) + ChatColor.RESET);
                                Map<String, Object> event = new HashMap<>();
                                event.put("action", "change_page");
                                try {
                                    event.put("value", Integer.parseInt(toks[2]));
                                } catch (NumberFormatException nfe) { }
                                tag.put("clickEvent", event);
                            }
                            if (toks.length >= 4) {
                                Map<String, Object> event = new HashMap<>();
                                event.put("action", "show_text");
                                event.put("value", format(toks[3]));
                                tag.put("hoverEvent", event);
                            }
                        case "chapter":
                            if (toks.length == 2) {
                                chapters.put(toks[1], pages.size());
                                anchors.put(toks[1], pages.size());
                            }
                        case "anchor":
                            if (toks.length == 2) {
                                anchors.put(toks[1], pages.size());
                            }
                            break;
                        default:
                            getLogger().info("Unknown tag: " + toks[0]);
                        }
                        page.add(format(par.substring(prevEnd, matcher.start())));
                        prevEnd = matcher.end();
                        if (!tag.isEmpty()) page.add(tag);
                    }
                    page.add(format(par.substring(prevEnd, par.length())));
                    pages.add(page);
                    page = new ArrayList<>();
                    page.add("");
                } catch (RuntimeException re) {
                    re.printStackTrace();
                    return null;
                }
            }
        }
        boolean tableOfContents = config.getBoolean("TableOfContents");
        if (tableOfContents) {
            page = new ArrayList<>();
            page.add(format("&lTable of Contents&r\n"));
            int entries = 0;
            int chapterNo = 0;
            for (String chapter: chapters.keySet()) {
                chapterNo += 1;
                Integer pageNo = chapters.get(chapter);
                pageNo += 2;
                Map<String, Object> tocEntry = new HashMap<>();
                tocEntry.put("text", chapterNo + ". " + ChatColor.BLUE + chapter);
                Map<String, Object> clickEvent = new HashMap<>();
                tocEntry.put("clickEvent", clickEvent);
                clickEvent.put("action", "change_page");
                clickEvent.put("value", pageNo);
                Map<String, Object> hoverEvent = new HashMap<>();
                tocEntry.put("hoverEvent", hoverEvent);
                hoverEvent.put("action", "show_text");
                hoverEvent.put("value", "Jump to page " + pageNo);
                page.add("\n");
                page.add(tocEntry);
                entries += 1;
            }
            pages.add(0, page);
        }
        for (Map<String, Object> reference: references) {
            String refname = (String)reference.get("value");
            Integer pageNo = anchors.get(refname);
            if (pageNo == null) {
                getLogger().warning("Reference not found in manual " + name + ": " + refname);
                pageNo = 0;
            }
            if (tableOfContents) {
                reference.put("value", pageNo + 2);
            } else {
                reference.put("value", pageNo + 1);
            }
        }
        List<String> formattedPages = new ArrayList<>();
        for (List<Object> oldPage: pages) {
            if (oldPage.size() == 1) {
                formattedPages.add(JSONValue.toJSONString(oldPage.get(0)));
            } else {
                formattedPages.add(JSONValue.toJSONString(oldPage));
            }
        }
        Map<String, Object> itemTag = new HashMap<>();
        itemTag.put("generation", config.getInt("generation", 0));
        itemTag.put("author", format(config.getString("author", "author")));
        itemTag.put("title", format(config.getString("title", "title")));
        itemTag.put("pages", formattedPages);
        itemTag.put("resolved", true);
        int version = config.getInt("Version");
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        item = Dirty.applyMap(item, itemTag);
        return new Manual(name, version, itemTag, item);
    }
}
