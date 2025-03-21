package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.events.CraftScuttleEvent;
import net.countercraft.movecraft.events.CraftStopCruiseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;

import static net.countercraft.movecraft.util.ChatUtils.MOVECRAFT_COMMAND_PREFIX;

public class ScuttleSign extends AbstractCraftSign {

    public ScuttleSign() {
        super("movecraft.commands.scuttle.others", true);
    }

    @Override
    protected void onCraftIsBusy(Player player, Craft craft) {

    }

    @Override
    protected void onCraftNotFound(Player player, SignListener.SignWrapper sign) {
        player.sendMessage(MOVECRAFT_COMMAND_PREFIX
                + I18nSupport.getInternationalisedString("You must be piloting a craft"));
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Player player) {
        return true;
    }

    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        return false;
    }

    @Override
    protected boolean canPlayerUseSignOn(Player player, Craft craft) {
        if(craft instanceof SinkingCraft) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX
                    + I18nSupport.getInternationalisedString("Scuttle - Craft Already Sinking"));
            return false;
        }
        if(!player.hasPermission("movecraft." + craft.getType().getStringProperty(CraftType.NAME)
                + ".scuttle")) {
            player.sendMessage(MOVECRAFT_COMMAND_PREFIX
                    + I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return false;
        }
        // Checks if the given player is the owner/pilot of the given craft
        // If it isnt a piloted craft, it returns true
        if (super.canPlayerUseSignOn(player, craft) && (craft instanceof PilotedCraft)) {            
            return true;            
        }
        // Check for "can scuttle others" permission
        if (this.permissionString != null || !this.permissionString.isBlank()) {
            if (!player.hasPermission(this.permissionString)) {
                player.sendMessage(MOVECRAFT_COMMAND_PREFIX
                        + I18nSupport.getInternationalisedString("You must be piloting a craft"));
            }
        }
        return true;
    }

    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, Craft craft, Player player) {
        CraftScuttleEvent e = new CraftScuttleEvent(craft, player);
        Bukkit.getServer().getPluginManager().callEvent(e);
        if(e.isCancelled())
            return false;

        craft.setCruising(false, CraftStopCruiseEvent.Reason.CRAFT_SUNK);
        CraftManager.getInstance().sink(craft);
        player.sendMessage(MOVECRAFT_COMMAND_PREFIX
                + I18nSupport.getInternationalisedString("Scuttle - Scuttle Activated"));
        return true;
    }
}
