package com.winthier.manual;

import com.winthier.custom.item.CustomItem;
import com.winthier.custom.item.UncraftableItem;
import com.winthier.custom.item.UpdatableItem;
import com.winthier.custom.util.Dirty;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public final class ManualItem implements CustomItem, UpdatableItem, UncraftableItem {
    private final ManualPlugin plugin;
    public static final String CUSTOM_ID = "manual:manual";

    @Override
    public String getCustomId() {
        return CUSTOM_ID;
    }

    @Override
    public ItemStack spawnItemStack(int amount) {
        return new ItemStack(Material.WRITTEN_BOOK, amount);
    }

    @Override
    public void setItemData(ItemStack item, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry: data.entrySet()) {
            Object value = entry.getValue();
            switch (entry.getKey()) {
            case "name":
                if (value instanceof String) {
                    String name = (String)value;
                    ManualPlugin.Manual manual = plugin.getManual(name);
                    if (manual != null) {
                        setManual(item, manual);
                    }
                }
                break;
            default:
                break;
            }
        }
    }

    @Override
    public int getUpdateVersion(ItemStack item) {
        Dirty.TagWrapper conf = Dirty.TagWrapper.getItemConfigOf(item);
        String bookName = conf.getString("name");
        if (bookName == null || bookName.isEmpty()) return 0;
        ManualPlugin.Manual manual = plugin.getManual(bookName);
        if (manual == null) return 0;
        return manual.getVersion();
    }

    @Override
    public ItemStack updateItem(ItemStack item) {
        Dirty.TagWrapper conf = Dirty.TagWrapper.getItemConfigOf(item);
        String bookName = conf.getString("name");
        if (bookName == null || bookName.isEmpty()) return null;
        ManualPlugin.Manual manual = plugin.getManual(bookName);
        if (manual == null) return null;
        return Dirty.applyMap(item, manual.getItemTag());
    }

    static String getName(ItemStack item) {
        Dirty.TagWrapper conf = Dirty.TagWrapper.getItemConfigOf(item);
        return conf.getString("name");
    }

    static void setManual(ItemStack item, ManualPlugin.Manual manual) {
        Dirty.TagWrapper conf = Dirty.TagWrapper.getItemConfigOf(item);
        conf.setString("name", manual.getName());
        conf.setInt("UpdateVersion", manual.getVersion());
        conf = Dirty.TagWrapper.getItemTagOf(item);
        conf.applyMap(manual.getItemTag());
    }

    @Override
    public void handleMessage(ItemStack item, CommandSender sender, String[] args) {
        if (args.length == 0) return;
        switch (args[0]) {
        case "name":
            if (args.length == 1) {
                Dirty.TagWrapper conf = Dirty.TagWrapper.getItemConfigOf(item);
                sender.sendMessage("Name of manual is '" + conf.getString("name") + "'");
            } else if (args.length >= 2) {
                StringBuilder sb = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i += 1) sb.append(" ").append(args[i]);
                String name = sb.toString();
                ManualPlugin.Manual manual = plugin.getManual(name);
                if (manual == null) {
                    sender.sendMessage("Manual not found: " + name);
                    return;
                }
                setManual(item, manual);
                sender.sendMessage("Manual updated to " + manual.getName() + " version " + manual.getVersion() + ".");
            }
            break;
        case "version":
            Dirty.TagWrapper conf = Dirty.TagWrapper.getItemConfigOf(item);
            sender.sendMessage("Update version: " + conf.getInt("UpdateVersion"));
            break;
        default: break;
        }
    }
}
