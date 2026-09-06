package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/*
 * Base implementation for all craft pilot signs, does nothing but has the relevant CraftType instance backed
 */
public abstract class AbstractCraftPilotSign extends AbstractMovecraftSign {

    protected final TypeSafeCraftType craftType;

    public AbstractCraftPilotSign(final TypeSafeCraftType craftType) {
        super();
        this.craftType = craftType;
    }

    public TypeSafeCraftType getCraftType() {
        return this.craftType;
    }

    @Override
    public TextColor highlightColor() {
        return NamedTextColor.YELLOW;
    }
}
