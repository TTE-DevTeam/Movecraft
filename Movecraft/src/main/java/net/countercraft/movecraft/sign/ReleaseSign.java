package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.jetbrains.annotations.Nullable;

public class ReleaseSign extends AbstractCraftSign {

    public ReleaseSign() {
        super(true);
    }

    @Override
    protected void onParentCraftBusy(Player player, Craft craft) {

    }

    @Override
    protected void onCraftNotFound(Player player, Sign sign) {

    }

    @Override
    public boolean shouldCancelEvent(boolean processingSuccessful, @Nullable Action type, boolean sneaking) {
        if (processingSuccessful) {
            return true;
        }
        return !sneaking;
    }

    @Override
    protected boolean isSignValid(Action clickType, Sign sign, Player player) {
        return true;
    }

    @Override
    public boolean processSignChange(SignChangeEvent event) {
        return false;
    }

    @Override
    protected boolean internalProcessSign(Action clickType, Sign sign, Player player, Craft craft) {
        CraftManager.getInstance().release(craft, CraftReleaseEvent.Reason.PLAYER, false);
        return true;
    }
}
