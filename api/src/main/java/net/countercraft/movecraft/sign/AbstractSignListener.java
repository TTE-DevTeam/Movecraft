package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.events.CraftDetectEvent;
import net.countercraft.movecraft.events.SignTranslateEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public abstract class AbstractSignListener implements Listener {

    public static AbstractSignListener INSTANCE;

    public AbstractSignListener() {
        INSTANCE = this;
    }

    public record SignWrapper(
            Sign block,
            Function<Integer, Component> getLine,
            List<Component> lines,
            BiConsumer<Integer, Component> setLine,
            BlockFace facing
    ) {
        public Component line(int index) {
            if (index >= lines.size() || index < 0) {
                throw new IndexOutOfBoundsException();
            }
            return getLine().apply(index);
        }

        public void line(int index, Component component) {
            setLine.accept(index, component);
        }

        public String getRaw(int index) {
            return PlainTextComponentSerializer.plainText().serialize(line(index));
        }

        public String[] rawLines() {
            String[] result = new String[this.lines.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = this.getRaw(i);
            }
            return result;
        }

        public boolean isEmpty() {
            for(String s : this.rawLines()) {
                if (s.trim().isEmpty() || s.trim().isBlank()) {
                    continue;
                }
                else {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            if (obj instanceof SignWrapper other) {
                return areSignsEqual(other);
            }
            return false;
        }

        public boolean areSignsEqual(SignWrapper other) {
            return areSignsEqual(this, other);
        }

        public static boolean areSignsEqual(SignWrapper a, SignWrapper b) {
            String[] aLines = a.rawLines();
            String[] bLines = b.rawLines();

            if (aLines.length != bLines.length) {
                return false;
            }

            for (int i = 0; i < aLines.length; i++) {
                String aLine = aLines[i].trim();
                String bLine = bLines[i].trim();

                if (!aLine.equalsIgnoreCase(bLine)) {
                    return false;
                }
            }
            return true;
        }

        public static boolean areSignsEqual(SignWrapper[] a, SignWrapper[] b) {
            if (a == null || b == null) {
                return false;
            }
            if (a.length != b.length) {
                return false;
            }

            for (int i = 0; i < a.length; i++) {
                SignWrapper aWrap = a[i];
                SignWrapper bWrap = b[i];
                if (!areSignsEqual(aWrap, bWrap)) {
                    return false;
                }
            }
            return true;
        }

    }

    public abstract SignWrapper[] getSignWrappers(Sign sign);
    public abstract SignWrapper[] getSignWrappers(Sign sign, SignTranslateEvent event);
    protected abstract SignWrapper getSignWrapper(Sign sign, SignChangeEvent signChangeEvent);
    protected abstract SignWrapper getSignWrapper(Sign sign, PlayerInteractEvent interactEvent);

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onCraftDetect(CraftDetectEvent event) {
        final World world = event.getCraft().getWorld();
        event.getCraft().getHitBox().forEach(
                (mloc) -> {
                    Block block = mloc.toBukkit(world).getBlock();
                    BlockState state = block.getState();
                    if (state instanceof Sign sign) {
                        for (SignWrapper wrapper : this.getSignWrappers(sign)) {
                            AbstractCraftSign.tryGetCraftSign(wrapper.line(0)).ifPresent(acs -> acs.onCraftDetect(event, wrapper));
                        }
                    }
                }
        );
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSignTranslate(SignTranslateEvent event) {
        AbstractCraftSign.tryGetCraftSign(event.line(0)).ifPresent(acs -> acs.onSignMovedByCraft(event));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        BlockState state = block.getState();
        if (state instanceof Sign sign) {
            SignWrapper wrapper = this.getSignWrapper(sign, event);
            AbstractMovecraftSign.tryGet(wrapper.line(0)).ifPresent(ams -> {

                boolean success = ams.processSignChange(event, wrapper);
                if (ams.shouldCancelEvent(success, null, event.getPlayer().isSneaking())) {
                    event.setCancelled(true);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSignClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        BlockState state = block.getState();
        if (state instanceof Sign sign) {
            SignWrapper wrapper = this.getSignWrapper(sign, event);
            AbstractMovecraftSign.tryGet(wrapper.line(0)).ifPresent(ams -> {
                boolean success = ams.processSignClick(event.getAction(), wrapper, event.getPlayer());
                if (ams.shouldCancelEvent(success, event.getAction(), event.getPlayer().isSneaking())) {
                    event.setCancelled(true);
                }
            });
        }
    }

}
