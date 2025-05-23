package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.events.CraftDetectEvent;
import net.countercraft.movecraft.events.SignTranslateEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractInformationSign extends AbstractCraftSign {

    public static final Component EMPTY = Component.text("");

    protected static final Style STYLE_COLOR_GREEN = Style.style(TextColor.color(0, 255, 0));
    protected static final Style STYLE_COLOR_YELLOW = Style.style(TextColor.color(255, 255, 0));
    protected static final Style STYLE_COLOR_RED = Style.style(TextColor.color(255, 0, 0));
    protected static final Style STYLE_COLOR_WHITE = Style.style(TextColor.color(255, 255, 255));

    public enum REFRESH_CAUSE {
        SIGN_CREATION,
        CRAFT_DETECT,
        SIGN_MOVED_BY_CRAFT,
        SIGN_CLICK
    }

    public AbstractInformationSign() {
        // Info signs only display things, that should not require permissions, also it doesn't matter if the craft is busy or not
        super(null, true);
    }

    @Override
    protected boolean canPlayerUseSignOn(Player player, Craft craft) {
        // Permcheck related, no perms required, return true
        return true;
    }

    @Override
    public void onCraftDetect(CraftDetectEvent event, SignListener.SignWrapper sign) {
        // TODO: Check if the craft supports this sign? If no, cancel
        super.onCraftDetect(event, sign);
        this.refreshSign(event.getCraft(), sign, true, REFRESH_CAUSE.CRAFT_DETECT);
        sign.block().update();
        this.sendUpdatePacket(event.getCraft(), sign, REFRESH_CAUSE.CRAFT_DETECT);
    }

    @Override
    public boolean processSignTranslation(Craft translatingCraft, SignListener.SignWrapper movingData, @Nullable List<MovecraftLocation> signLocations) {
        //SignListener.SignWrapper wrapperTmp = new SignListener.SignWrapper(null, movingData::line, movingData.lines(), movingData::line, BlockFace.SELF);
        if (this.refreshSign(translatingCraft, movingData, false, REFRESH_CAUSE.SIGN_MOVED_BY_CRAFT)) {
            // All cool, we dont need to call the sendPacket method as that will be called by the listener itself
        }
        // We looped over the lines, so we HAVE to return true here
        return true;
    }

    @Override
    protected void onCraftNotFound(Player player, SignListener.SignWrapper sign) {
        // Nothing to do
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Player player) {
        return true;
    }

    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, Craft craft, Player player) {
        if (this.refreshSign(craft, sign, false, REFRESH_CAUSE.SIGN_CLICK)) {
            this.sendUpdatePacket(craft, sign, REFRESH_CAUSE.SIGN_CLICK);
        }
        return true;
    }

    // Called whenever the info needs to be refreshed
    // That happens on CraftDetect, sign right click (new), Sign Translate
    // The new and old values are gathered here and compared
    // If nothing has changed, no update happens
    // If something has changed, performUpdate() and sendUpdatePacket() are called
    // Returns wether or not something has changed
    protected boolean refreshSign(@Nullable Craft craft, SignListener.SignWrapper sign, boolean fillDefault, REFRESH_CAUSE refreshCause) {
        boolean changedSome = false;
        Component[] updatePayload = new Component[sign.lines().size()];
        for(int i = 1; i < sign.lines().size(); i++) {
            Component oldComponent = sign.line(i);
            Component potentiallyNew;
            if (craft == null || fillDefault) {
                potentiallyNew = this.getDefaultString(i, oldComponent);
            } else {
                 potentiallyNew = this.getUpdateString(i, oldComponent, craft);
            }
            if (potentiallyNew != null && !potentiallyNew.equals(oldComponent)) {
                String oldValue = PlainTextComponentSerializer.plainText().serialize(oldComponent);
                String newValue = PlainTextComponentSerializer.plainText().serialize(potentiallyNew);
                if (!oldValue.equals(newValue)) {
                    changedSome = true;
                    updatePayload[i] = potentiallyNew;
                }
            }
        }
        if (changedSome) {
            this.performUpdate(updatePayload, sign, refreshCause);
        }
        return changedSome;
    }

    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        if (this.refreshSign(null, sign, true, REFRESH_CAUSE.SIGN_CREATION)) {
            this.sendUpdatePacket(null, sign, REFRESH_CAUSE.SIGN_CREATION);
        }
        return true;
    }

    /*
    Data to set on the sign. Return null if no update should happen!
    Attention: A update will only be performed, if the new and old component are different!
     */
    @Nullable
    protected abstract Component getUpdateString(int lineIndex, Component oldData, Craft craft);

    // Returns the default value for this info sign per line
    // Used on CraftDetect and on sign change
    @Nullable
    protected abstract Component getDefaultString(int lineIndex, Component oldComponent);

    /*
     * @param newComponents: Array of nullable values. The index represents the index on the sign. Only contains the updated components
     *
     * Only gets called if at least one line has changed
     */
    protected abstract void performUpdate(Component[] newComponents, SignListener.SignWrapper sign, REFRESH_CAUSE refreshCause);

    @Override
    public void onCraftStatusUpdate(Craft craft, final SignListener.SignWrapper sign) {
        if (this.refreshSign(craft, sign, false, REFRESH_CAUSE.SIGN_CLICK)) {
            this.sendUpdatePacket(craft, sign, REFRESH_CAUSE.SIGN_CLICK);
        }
    }
}
