package net.countercraft.movecraft.features.contacts;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.sign.AbstractInformationSign;
import net.countercraft.movecraft.sign.SignListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ContactsSign extends AbstractInformationSign {

    protected final int MAX_DISTANCE_COLOR_RED = 64 * 64;
    protected final int MAX_DISTANCE_COLOR_YELLOW = 128 * 128;

    protected @NotNull Component contactsLine(@NotNull Craft base, @NotNull Craft target) {
        MovecraftLocation baseCenter = base.getHitBox().getMidPoint();
        MovecraftLocation targetCenter = target.getHitBox().getMidPoint();
        int distanceSquared = baseCenter.distanceSquared(targetCenter);

        String craftTypeName = target.getType().getStringProperty(CraftType.NAME);
        if (craftTypeName.length() > 9)
            craftTypeName = craftTypeName.substring(0, 7);

        Style style = STYLE_COLOR_GREEN;
        if (distanceSquared <= MAX_DISTANCE_COLOR_RED) {
            style = STYLE_COLOR_RED;
        }
        else if (distanceSquared <= MAX_DISTANCE_COLOR_YELLOW) {
            style = STYLE_COLOR_YELLOW;
        }

        Component result = Component.text(craftTypeName + " ").style(style);

        int diffX = baseCenter.getX() - targetCenter.getX();
        int diffZ = baseCenter.getZ() - targetCenter.getZ();
        String directionStr = "" + (int) Math.sqrt(distanceSquared);
        if (Math.abs(diffX) > Math.abs(diffZ)) {
            if (diffX<0) {
                directionStr +=" E";
            } else {
                directionStr +=" W";
            }
        } else {
            if (diffZ<0) {
                directionStr +=" S";
            } else {
                directionStr +=" N";
            }
        }
        result = result.append(Component.text(directionStr).style(STYLE_COLOR_WHITE));
        return result;
    }

    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, Craft craft, Player player) {
        player.performCommand("contacts");

        return true;
    }

    @Override
    protected @Nullable Component getUpdateString(int lineIndex, Component oldData, Craft craft) {
        Craft contact = null;
        List<Craft> contacts = craft.getDataTag(Craft.CONTACTS);
        if (contacts.isEmpty() || contacts.size() <= lineIndex) {
            return EMPTY;
        }
        contact = contacts.get(lineIndex);

        return contactsLine(craft, contact);
    }

    @Override
    protected @Nullable Component getDefaultString(int lineIndex, Component oldComponent) {
        return EMPTY;
    }

    @Override
    protected void performUpdate(Component[] newComponents, SignListener.SignWrapper sign, REFRESH_CAUSE refreshCause) {
        for (int i = 0; i < newComponents.length; i++) {
            Component newComp = newComponents[i];
            if (newComp != null) {
                sign.line(i, newComp);
            }
        }
        if (refreshCause != REFRESH_CAUSE.SIGN_MOVED_BY_CRAFT && sign.block() != null) {
            sign.block().update();
        }
    }

    @Override
    protected void onCraftIsBusy(Player player, Craft craft) {

    }
}
