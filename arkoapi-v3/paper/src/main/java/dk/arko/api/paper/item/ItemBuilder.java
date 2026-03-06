package dk.arko.api.paper.item;

import dk.arko.api.common.text.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.util.*;

/**
 * Fluent builder for creating ItemStacks with full feature support.
 *
 * Usage:
 *   ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
 *       .name("<gradient:#FF0000:#00FF00>Flamme Sværd")
 *       .lore("<gray>Et legendarisk sværd", "<red>+50 Skade")
 *       .enchant(Enchantment.SHARPNESS, 5)
 *       .unbreakable()
 *       .glow()
 *       .persistentData(myKey, PersistentDataType.STRING, "legendary")
 *       .build();
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    private ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item);
    }

    // ─── Basic Properties ──────────────────────────────────────

    public ItemBuilder name(String miniMessage) {
        meta.displayName(TextUtil.parse(miniMessage));
        return this;
    }

    public ItemBuilder name(Component name) {
        meta.displayName(name);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        meta.lore(Arrays.stream(lines).map(TextUtil::parse).toList());
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        meta.lore(lines.stream().map(TextUtil::parse).toList());
        return this;
    }

    public ItemBuilder loreComponents(List<Component> lines) {
        meta.lore(lines);
        return this;
    }

    public ItemBuilder addLore(String... lines) {
        List<Component> existing = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String line : lines) existing.add(TextUtil.parse(line));
        meta.lore(existing);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        meta.setCustomModelData(data);
        return this;
    }

    // ─── Enchantments ──────────────────────────────────────────

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder enchant(Map<Enchantment, Integer> enchantments) {
        enchantments.forEach((e, l) -> meta.addEnchant(e, l, true));
        return this;
    }

    public ItemBuilder removeEnchant(Enchantment enchantment) {
        meta.removeEnchant(enchantment);
        return this;
    }

    public ItemBuilder glow() {
        meta.setEnchantmentGlintOverride(true);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        meta.setEnchantmentGlintOverride(glow);
        return this;
    }

    // ─── Flags ─────────────────────────────────────────────────

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return this;
    }

    public ItemBuilder maxStackSize(int maxStackSize) {
        meta.setMaxStackSize(maxStackSize);
        return this;
    }

    // ─── Persistent Data ───────────────────────────────────────

    public <T, Z> ItemBuilder persistentData(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    public ItemBuilder persistentString(NamespacedKey key, String value) {
        return persistentData(key, PersistentDataType.STRING, value);
    }

    public ItemBuilder persistentInt(NamespacedKey key, int value) {
        return persistentData(key, PersistentDataType.INTEGER, value);
    }

    // ─── Skull ─────────────────────────────────────────────────

    public ItemBuilder skullOwner(UUID uuid) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
        }
        return this;
    }

    public ItemBuilder skullTexture(String textureUrl) {
        if (meta instanceof SkullMeta skullMeta) {
            try {
                PlayerProfile profile = org.bukkit.Bukkit.createProfile(UUID.randomUUID());
                profile.getTextures().setSkin(new URL(textureUrl));
                skullMeta.setOwnerProfile(profile);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set skull texture", e);
            }
        }
        return this;
    }

    /**
     * Set skull from a base64-encoded texture value.
     */
    public ItemBuilder skullBase64(String base64) {
        return skullTexture("https://textures.minecraft.net/texture/" + base64);
    }

    // ─── Leather Armor Color ───────────────────────────────────

    public ItemBuilder color(Color color) {
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(color);
        }
        return this;
    }

    public ItemBuilder color(int r, int g, int b) {
        return color(Color.fromRGB(r, g, b));
    }

    public ItemBuilder hexColor(String hex) {
        hex = hex.replace("#", "");
        return color(Color.fromRGB(Integer.parseInt(hex, 16)));
    }

    // ─── Potion ────────────────────────────────────────────────

    public ItemBuilder potionColor(Color color) {
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setColor(color);
        }
        return this;
    }

    // ─── Firework ──────────────────────────────────────────────

    public ItemBuilder fireworkPower(int power) {
        if (meta instanceof FireworkMeta fireworkMeta) {
            fireworkMeta.setPower(power);
        }
        return this;
    }

    // ─── Build ─────────────────────────────────────────────────

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Build and return a clone.
     */
    public ItemStack buildClone() {
        item.setItemMeta(meta);
        return item.clone();
    }

    // ─── Static Helpers ────────────────────────────────────────

    /**
     * Create a glass pane filler item.
     */
    public static ItemStack filler(Material glassPaneMaterial) {
        return ItemBuilder.of(glassPaneMaterial).name(" ").build();
    }

    /**
     * Create a standard gray glass pane filler.
     */
    public static ItemStack filler() {
        return filler(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Create a player head item.
     */
    public static ItemStack playerHead(UUID player, String displayName) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .skullOwner(player)
                .name(displayName)
                .build();
    }

    /**
     * Create a player head from texture URL.
     */
    public static ItemStack customHead(String textureUrl, String displayName) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .skullTexture(textureUrl)
                .name(displayName)
                .build();
    }
}
