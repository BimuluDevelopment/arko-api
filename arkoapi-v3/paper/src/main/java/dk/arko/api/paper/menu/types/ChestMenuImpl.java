package dk.arko.api.paper.menu.types;

import dk.arko.api.paper.menu.Menu;
import net.kyori.adventure.text.Component; /**
 * Standard chest menu with 1-6 rows.
 * Usage:
 *   ChestMenu menu = new ChestMenu("My Menu", 3);
 *   menu.setItem(13, myItem, ctx -> ctx.player().sendMessage("Clicked!"));
 *   menu.open(player);
 */
public class ChestMenuImpl extends Menu {
    public ChestMenuImpl(Component title, int rows) {
        super(title, Math.max(1, Math.min(6, rows)));
    }
}
