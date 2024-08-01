package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.events.CraftDetectEvent;
import net.countercraft.movecraft.events.SignTranslateEvent;
import net.countercraft.movecraft.util.MathUtils;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;

import java.util.Optional;

public abstract class AbstractCraftSign extends AbstractMovecraftSign {

    public static Optional<AbstractCraftSign> tryGetCraftSign(final String ident) {
        Optional<AbstractMovecraftSign> tmp = AbstractCraftSign.tryGet(ident);
        if (tmp.isPresent() && tmp.get() instanceof AbstractCraftSign acs) {
            return Optional.of(acs);
        }
        return Optional.empty();
    }

    private final boolean ignoreCraftIsBusy;

    public AbstractCraftSign(boolean ignoreCraftIsBusy) {
        this(null, ignoreCraftIsBusy);
    }

    public AbstractCraftSign(final String permission, boolean ignoreCraftIsBusy) {
        super(permission);
        this.ignoreCraftIsBusy = ignoreCraftIsBusy;
    }

    // Return true to cancel the event
    @Override
    public boolean processSignClick(Action clickType, Sign sign, Player player) {
        if (!this.isSignValid(clickType, sign, player)) {
            return false;
        }
        if (!this.canPlayerUseSign(clickType, sign, player)) {
            return false;
        }
        Optional<Craft> craft = this.getCraft(sign);
        if (craft.isEmpty()) {
            this.onCraftNotFound(player, sign);
            return false;
        }

        if (craft.get() instanceof PlayerCraft pc) {
            if (!pc.isNotProcessing() && !this.ignoreCraftIsBusy) {
                this.onParentCraftBusy(player, craft.get());
                return false;
            }
        }

        return internalProcessSign(clickType, sign, player, craft);
    }

    protected abstract void onParentCraftBusy(Player player, Craft craft);

    protected boolean canPlayerUseSignOn(Player player, Craft craft) {
        if (craft instanceof PilotedCraft pc) {
            return pc.getPilot() == player;
        }
        return true;
    }

    protected abstract void onCraftNotFound(Player player, Sign sign);

    protected boolean canPlayerUseSign(ClickType clickType, Sign sign, Player player) {
        if (this.optPermission.isPresent()) {
            return player.hasPermission(this.optPermission.get());
        }
        return true;
    }

    protected abstract boolean internalProcessSign(ClickType clickType, Sign sign, Player player, Optional<Craft> craft);

    protected Optional<Craft> getCraft(Sign sign) {
        return Optional.ofNullable(MathUtils.getCraftByPersistentBlockData(sign.getLocation()));
    }

    public void onCraftDetect(CraftDetectEvent event) {
        // Do nothing by default
    }

    public void onSignMovedByCraft(SignTranslateEvent event) {
        // Do nothing by default
    }

    protected abstract boolean isSignValid(ClickType clickType, Sign sign, Player player);

}
