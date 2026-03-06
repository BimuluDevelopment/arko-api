package dk.arko.api.paper.menu.types;

import dk.arko.api.paper.menu.Menu;
import dk.arko.api.paper.menu.MenuItem;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnimatedMenuImpl extends Menu {
    private final List<AnimationFrame> frames = new ArrayList<>();
    private int currentFrame = 0;
    private long tickInterval = 10; // ticks between frames
    private boolean loop = true;

    public AnimatedMenuImpl(Component title, int rows) {
        super(title, rows);
    }

    public AnimatedMenuImpl addFrame(Map<Integer, MenuItem> slotItems) {
        frames.add(new AnimationFrame(slotItems));
        return this;
    }

    public AnimatedMenuImpl tickInterval(long ticks) { this.tickInterval = ticks; return this; }
    public AnimatedMenuImpl loop(boolean loop) { this.loop = loop; return this; }

    public void nextFrame() {
        if (frames.isEmpty()) return;
        currentFrame++;
        if (currentFrame >= frames.size()) {
            currentFrame = loop ? 0 : frames.size() - 1;
        }
    }

    public long getTickInterval() { return tickInterval; }
    public int getCurrentFrame() { return currentFrame; }
    public int getFrameCount() { return frames.size(); }

    @Override
    protected void render(Inventory inv) {
        inv.clear();
        // Base items
        items.forEach((slot, item) -> {
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item.getItemStack());
        });
        // Animation frame overlay
        if (!frames.isEmpty() && currentFrame < frames.size()) {
            AnimationFrame frame = frames.get(currentFrame);
            frame.items.forEach((slot, item) -> {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item.getItemStack());
            });
        }
    }

    private record AnimationFrame(Map<Integer, MenuItem> items) {}
}
