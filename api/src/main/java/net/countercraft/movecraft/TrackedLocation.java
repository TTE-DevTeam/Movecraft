package net.countercraft.movecraft;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.util.MathUtils;
import org.jetbrains.annotations.NotNull;

public class TrackedLocation {
    private MovecraftLocation offSet;
    private Craft craft;

    /**
     * Creates a new TrackedLocation instance which tracks a location about a craft's midpoint.
     * @param craft The craft that's that tied to the location.
     * @param location The absolute position to track. This location will be stored as a relative
     *                 location to the craft's central hitbox.
     */
    public TrackedLocation(@NotNull Craft craft, @NotNull MovecraftLocation location) {
        this.reset(craft, location);
    }

    /**
     * Rotates the stored location.
     * @param rotation A clockwise or counter-clockwise direction to rotate.
     */
    public void rotate(MovecraftRotation rotation, MovecraftLocation origin) {
        // TODO: SOMEHOW "absolute" and "origin" can end up to be the same thing?! WTF?!
        MovecraftLocation absolute = this.getAbsoluteLocation();
        System.out.println("Absolute: " + absolute.toString());
        MovecraftLocation vector = MathUtils.rotateVec(rotation, absolute.subtract(origin));

        MovecraftLocation newAbsolute = origin.add(vector);
        System.out.println("New absolute: " + newAbsolute.toString());
        // Ugly hack, but necessary
        MovecraftLocation craftMidPoint = this.craft.getHitBox().getMidPoint();
        this.offSet = newAbsolute.subtract(craftMidPoint);
        System.out.println("New absolute after recalculating: " + this.getAbsoluteLocation().toString());
    }

    /**
     * Gets the stored absolute location.
     * @return Returns the absolute location instead of a vector.
     */
    public MovecraftLocation getAbsoluteLocation() {
        MovecraftLocation midPoint = craft.getHitBox().getMidPoint();
        return offSet.add(midPoint);
    }

    /**
     * Gets the stored location as a position vector relative to the midpoint.
     * @return Returns the absolute location instead of a vector.
     */
    public MovecraftLocation getOffSet() {
        return offSet;
    }

    /**
     * NEVER USE THIS UNLESS ABSOLUTELY NECESSARY
     * @param craft
     * @param location
     */
    public void reset(@NotNull Craft craft, @NotNull MovecraftLocation location) {
        if (craft == this.craft) {
            return;
        }
        if (this.craft != null) {
            if (!(
                    // From parent to subcraft
                    (craft instanceof SubCraft subCraft && subCraft.getParent() == this.craft)
                    // From subcraft back to parent
                    || (this.craft instanceof SubCraft subCraft2 && subCraft2.getParent() == craft)
                )) {
                throw new IllegalStateException("Only ever call this when transferring from or to subcraft!");
            }
        }
        this.craft = craft;
        MovecraftLocation midPoint = craft.getHitBox().getMidPoint();
        System.out.println("Location: " + location.toString());
        System.out.println("Midpoint: " + midPoint.toString());
        offSet = location.subtract(midPoint);
    }

    public void reset(@NotNull MovecraftLocation location) {
        this.reset(this.craft, location);
    }

}
