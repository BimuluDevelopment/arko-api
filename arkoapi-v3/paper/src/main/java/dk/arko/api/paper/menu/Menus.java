package dk.arko.api.paper.menu;

import dk.arko.api.common.text.TextUtil;
import dk.arko.api.paper.menu.types.*;
import net.kyori.adventure.text.Component;

import java.util.function.Consumer;

/**
 * Factory for creating all menu types.
 *
 * Usage:
 *   Menu menu = Menus.chest("My Menu", 3);
 *   Menu paginated = Menus.paginated("Shop", 6);
 *   Menu confirm = Menus.confirmation("Delete?", yes -> {}, no -> {});
 */
public final class Menus {

    private Menus() {}

    /**
     * Standard chest menu (1-6 rows).
     */
    public static Menu chest(String title, int rows) {
        return new ChestMenuImpl(TextUtil.parse(title), rows);
    }

    public static Menu chest(Component title, int rows) {
        return new ChestMenuImpl(title, rows);
    }

    /**
     * Paginated menu with auto-navigation.
     */
    public static PaginatedMenuImpl paginated(String title, int rows) {
        return new PaginatedMenuImpl(TextUtil.parse(title), rows);
    }

    public static PaginatedMenuImpl paginated(Component title, int rows) {
        return new PaginatedMenuImpl(title, rows);
    }

    /**
     * Confirmation (yes/no) dialog menu.
     */
    public static Menu confirmation(String title, Consumer<MenuClickContext> onConfirm, Consumer<MenuClickContext> onDeny) {
        return new ConfirmationMenuImpl(TextUtil.parse(title), onConfirm, onDeny);
    }

    public static Menu confirmation(Component title, Consumer<MenuClickContext> onConfirm, Consumer<MenuClickContext> onDeny) {
        return new ConfirmationMenuImpl(title, onConfirm, onDeny);
    }

    /**
     * Category/tabbed menu.
     */
    public static CategoryMenuImpl category(String title, int rows) {
        return new CategoryMenuImpl(TextUtil.parse(title), rows);
    }

    public static CategoryMenuImpl category(Component title, int rows) {
        return new CategoryMenuImpl(title, rows);
    }

    /**
     * Scrolling menu (vertical or horizontal).
     */
    public static ScrollingMenuImpl scrolling(String title, int rows) {
        return new ScrollingMenuImpl(TextUtil.parse(title), rows);
    }

    /**
     * Animated menu with frame-based animation.
     */
    public static AnimatedMenuImpl animated(String title, int rows) {
        return new AnimatedMenuImpl(TextUtil.parse(title), rows);
    }

    /**
     * Hopper menu (5 slots).
     */
    public static Menu hopper(String title) {
        return new HopperMenuImpl(TextUtil.parse(title));
    }

    /**
     * Dispenser/dropper menu (3x3).
     */
    public static Menu dispenser(String title) {
        return new DispenserMenuImpl(TextUtil.parse(title));
    }

    /**
     * Filterable menu with search/filter support.
     */
    public static FilterableMenuImpl filterable(String title, int rows) {
        return new FilterableMenuImpl(TextUtil.parse(title), rows);
    }
}
