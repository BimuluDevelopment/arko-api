package dk.arko.api.paper.menu.types;

import dk.arko.api.common.text.TextUtil;
import dk.arko.api.paper.menu.Menu;
import dk.arko.api.paper.menu.MenuClickContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer; /**
 * Simple confirmation dialog menu.
 *
 * Usage:
 *   ConfirmationMenu.create("Slet dette?", ctx -> { delete(); }, ctx -> { cancel(); }).open(player);
 */
public class ConfirmationMenuImpl extends Menu {
    public ConfirmationMenuImpl(Component title, Consumer<MenuClickContext> onConfirm,
                                Consumer<MenuClickContext> onDeny) {
        super(title, 3);

        // Confirm button (green wool)
        ItemStack confirmItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.displayName(TextUtil.parse("<green><bold>Bekræft"));
        confirmItem.setItemMeta(confirmMeta);

        // Deny button (red wool)
        ItemStack denyItem = new ItemStack(Material.RED_WOOL);
        ItemMeta denyMeta = denyItem.getItemMeta();
        denyMeta.displayName(TextUtil.parse("<red><bold>Annuller"));
        denyItem.setItemMeta(denyMeta);

        // Glass fillers
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);

        fillEmpty(filler);
        setItem(11, confirmItem, onConfirm);
        setItem(12, confirmItem, onConfirm);
        setItem(14, denyItem, onDeny);
        setItem(15, denyItem, onDeny);
    }
}
