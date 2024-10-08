package net.countercraft.movecraft.mapUpdater.update;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.WorldHandler;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.sign.SignListener;
import net.countercraft.movecraft.util.MathUtils;
import net.countercraft.movecraft.util.Tags;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import net.countercraft.movecraft.util.hitboxes.SetHitBox;
import net.countercraft.movecraft.util.hitboxes.SolidHitBox;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.data.Waterlogged;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Logger;

public class CraftTranslateCommand extends UpdateCommand {
    @NotNull private final Craft craft;
    @NotNull private final MovecraftLocation displacement;
    @NotNull private final World world;

    public CraftTranslateCommand(@NotNull Craft craft, @NotNull MovecraftLocation displacement) {
        this.craft = craft;
        this.displacement = displacement;
        this.world = craft.getWorld();
    }
    
    public CraftTranslateCommand(@NotNull Craft craft, @NotNull MovecraftLocation displacement, @NotNull World world) {
        this.craft = craft;
        this.displacement = displacement;
        this.world = world;
    }




    @Override
    public void doUpdate() {
        final Logger logger = Movecraft.getInstance().getLogger();
        if(craft.getHitBox().isEmpty()){
            logger.warning("Attempted to move craft with empty HashHitBox!");
            CraftManager.getInstance().release(craft, CraftReleaseEvent.Reason.EMPTY, false);
            return;
        }
        long time = System.nanoTime();
        World oldWorld = craft.getWorld();
        final Set<Material> passthroughBlocks = new HashSet<>(
                craft.getType().getMaterialSetProperty(CraftType.PASSTHROUGH_BLOCKS));
        if (craft instanceof SinkingCraft) {
            passthroughBlocks.addAll(Tags.FLUID);
            passthroughBlocks.addAll(Tag.LEAVES.getValues());
            passthroughBlocks.addAll(Tags.SINKING_PASSTHROUGH);
        }
        if (passthroughBlocks.isEmpty()){
            //translate the craft
            Movecraft.getInstance().getWorldHandler().translateCraft(craft,displacement,world);
            craft.setWorld(world);
            //trigger sign events
            sendSignEvents();
        }
        else {
            SetHitBox originalLocations = new SetHitBox();
            for (MovecraftLocation movecraftLocation : craft.getHitBox()) {
                originalLocations.add(movecraftLocation.subtract(displacement));
            }
            final Set<MovecraftLocation> to = Sets.difference(craft.getHitBox().asSet(), originalLocations.asSet());
            //place phased blocks
            for (MovecraftLocation location : to) {
                var data = location.toBukkit(world).getBlock().getBlockData();
                if (passthroughBlocks.contains(data.getMaterial())) {
                    craft.getPhaseBlocks().put(location.toBukkit(world), data);
                }
            }
            //The subtraction of the set of coordinates in the HitBox cube and the HitBox itself
            final var invertedHitBox = Sets.difference(
                    craft.getHitBox().boundingHitBox().asSet(), craft.getHitBox().asSet());

            //place phased blocks
            final Set<Location> overlap = new HashSet<>(craft.getPhaseBlocks().keySet());
            overlap.removeIf((location -> !craft.getHitBox().contains(MathUtils.bukkit2MovecraftLoc(location))));
            final int minX = craft.getHitBox().getMinX();
            final int maxX = craft.getHitBox().getMaxX();
            final int minY = craft.getHitBox().getMinY();
            final int maxY = overlap.isEmpty() ? craft.getHitBox().getMaxY() : Collections.max(overlap,
                    Comparator.comparingInt(Location::getBlockY)).getBlockY();
            final int minZ = craft.getHitBox().getMinZ();
            final int maxZ = craft.getHitBox().getMaxZ();
            final HitBox[] surfaces = {
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(minX, maxY, maxZ)),
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(maxX, maxY, minZ)),
                    new SolidHitBox(new MovecraftLocation(maxX, minY, maxZ), new MovecraftLocation(minX, maxY, maxZ)),
                    new SolidHitBox(new MovecraftLocation(maxX, minY, maxZ), new MovecraftLocation(maxX, maxY, minZ)),
                    new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(maxX, minY, maxZ))
            };
            final SetHitBox validExterior = new SetHitBox();
            for (HitBox hitBox : surfaces) {
                validExterior.addAll(Sets.difference(hitBox.asSet(),craft.getHitBox().asSet()));
            }

            //Check to see which locations in the from set are actually outside of the craft
            final Set<MovecraftLocation> confirmed = craft instanceof SinkingCraft
                    ? invertedHitBox.copyInto(new LinkedHashSet<>())
                    : verifyExterior(invertedHitBox, validExterior);

            //A set of locations that are confirmed to be "exterior" locations
            final Set<MovecraftLocation> failed = Sets.difference(invertedHitBox,
                    confirmed).copyInto(new LinkedHashSet<>());

            final WorldHandler handler = Movecraft.getInstance().getWorldHandler();
            for (MovecraftLocation location : failed) {
                var data = location.toBukkit(world).getBlock().getBlockData();
                if (!passthroughBlocks.contains(data.getMaterial()))
                    continue;

                craft.getPhaseBlocks().put(location.toBukkit(world), data);
            }
            //translate the craft
            handler.translateCraft(craft, displacement,world);
            craft.setWorld(world);
            //trigger sign events
            sendSignEvents();

            for (MovecraftLocation l : failed){
                MovecraftLocation orig = l.subtract(displacement);
                if (craft.getHitBox().contains(orig) || failed.contains(orig)){
                    continue;
                }
                confirmed.add(orig);

            }

            //place confirmed blocks if they have been un-phased
            for (MovecraftLocation location : confirmed) {
                Location bukkit = location.toBukkit(craft.getWorld());
                if (!craft.getPhaseBlocks().containsKey(bukkit))
                    continue;

                //Do not place if it is at a collapsed HitBox location
                if (!craft.getCollapsedHitBox().isEmpty() && craft.getCollapsedHitBox().contains(location))
                    continue;
                var phaseBlock = craft.getPhaseBlocks().remove(bukkit);
                handler.setBlockFast(bukkit, phaseBlock);
                craft.getPhaseBlocks().remove(bukkit);
            }

            for (MovecraftLocation location : originalLocations) {
                Location bukkit = location.toBukkit(oldWorld);
                if(!craft.getHitBox().contains(location) && craft.getPhaseBlocks().containsKey(bukkit)){
                    var phaseBlock = craft.getPhaseBlocks().remove(bukkit);
                    handler.setBlockFast(bukkit, phaseBlock);
                }
            }

            for (MovecraftLocation location : failed) {
                Location bukkit = location.toBukkit(oldWorld);
                var data = bukkit.getBlock().getBlockData();
                if (passthroughBlocks.contains(data.getMaterial())) {
                    craft.getPhaseBlocks().put(bukkit, data);
                    handler.setBlockFast(bukkit, Material.AIR.createBlockData());

                }
            }
//            waterlog();
        }
        if (!craft.isNotProcessing())
            craft.setProcessing(false);
        time = System.nanoTime() - time;
        if(Settings.Debug)
            logger.info("Total time: " + (time / 1e6) + " milliseconds. Moving with cooldown of " + craft.getTickCooldown() + ". Speed of: " + String.format("%.2f", craft.getSpeed()) + ". Displacement of: " + displacement);

        // Only add cruise time if cruising
        if(craft.getCruising() && displacement.getY() == 0 && (displacement.getX() == 0 || displacement.getZ() == 0))
            craft.addCruiseTime(time / 1e9f);
    }

    @NotNull
    private Set<MovecraftLocation> verifyExterior(Set<MovecraftLocation> invertedHitBox, SetHitBox validExterior) {
        var shifts = new MovecraftLocation[]{new MovecraftLocation(0,-1,0),
                new MovecraftLocation(1,0,0),
                new MovecraftLocation(-1,0,0),
                new MovecraftLocation(0,0,1),
                new MovecraftLocation(0,0,-1)};
        Set<MovecraftLocation> visited = new LinkedHashSet<>(validExterior.asSet());
        Queue<MovecraftLocation> queue = new ArrayDeque<>();
        for(var node : validExterior){
            //If the node is already a valid member of the exterior of the HitBox, continued search is unitary.
            for(var shift : shifts){
                var shifted = node.add(shift);
                if(invertedHitBox.contains(shifted) && visited.add(shifted)){
                    queue.add(shifted);
                }
            }
        }
        while (!queue.isEmpty()) {
            var node = queue.poll();
            //If the node is already a valid member of the exterior of the HitBox, continued search is unitary.
            for(var shift : shifts){
                var shifted = node.add(shift);
                if(invertedHitBox.contains(shifted) && visited.add(shifted)){
                    queue.add(shifted);
                }
            }
        }
        return visited;
    }

    @Deprecated(forRemoval = true)
    private void waterlog(){
        final int minX = craft.getHitBox().getMinX();
        final int maxX = craft.getHitBox().getMaxX();
        final int minY = craft.getHitBox().getMinY();
        final int maxY = craft.getHitBox().getMaxY();
        final int minZ = craft.getHitBox().getMinZ();
        final int maxZ = craft.getHitBox().getMaxZ();
        final HitBox[] surfaces = {
                new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(minX, maxY, maxZ)),
                new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(maxX, maxY, minZ)),
                new SolidHitBox(new MovecraftLocation(maxX, minY, maxZ), new MovecraftLocation(minX, maxY, maxZ)),
                new SolidHitBox(new MovecraftLocation(maxX, minY, maxZ), new MovecraftLocation(maxX, maxY, minZ)),
                new SolidHitBox(new MovecraftLocation(minX, minY, minZ), new MovecraftLocation(maxX, minY, maxZ))};
        final SetHitBox validExterior = new SetHitBox();
        for (HitBox surface : surfaces) {
            for(var location : surface){
                if(!craft.getHitBox().contains(location)){
                    validExterior.add(location);
                }
            }
        }
        var hull = hullSearch(validExterior);
        for(var location : hull){
            var block = location.toBukkit(world).getBlock();
            var data = block.getBlockData();
            if(!(data instanceof Waterlogged)){
                continue;
            }
            var shouldFlood = craft.getPhaseBlocks().containsKey(location.toBukkit(world)) && craft.getPhaseBlocks().get(location.toBukkit(world)).getMaterial().equals(Material.WATER);
            if(shouldFlood == ((Waterlogged) data).isWaterlogged()){
                continue;
            }
            ((Waterlogged) data).setWaterlogged(shouldFlood);
            block.setBlockData(data);
        }
    }

    @NotNull
    private LinkedList<MovecraftLocation> hullSearch(SetHitBox validExterior) {
        var shifts = new MovecraftLocation[]{new MovecraftLocation(0,-1,0),
                new MovecraftLocation(1,0,0),
                new MovecraftLocation(-1,0,0),
                new MovecraftLocation(0,0,1),
                new MovecraftLocation(0,0,-1)};
        var hull = new LinkedList<MovecraftLocation>();
        var craftBox = craft.getHitBox();
        Queue<MovecraftLocation> queue = Lists.newLinkedList(validExterior);
        var visited = new SetHitBox(validExterior);
        while (!queue.isEmpty()){
            var top = queue.poll();
            if(craftBox.contains(top)){
                hull.add(top);
            }
            for(var shift : shifts){
                var shifted = top.add(shift);
                if(craftBox.inBounds(shifted) && visited.add(shifted)){
                    queue.add(shifted);
                }

            }
        }
        return hull;
    }

    private void sendSignEvents(){
        SignListener.INSTANCE.processSignTranslation(craft, false);
    }

    @NotNull
    public Craft getCraft(){
        return craft;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof CraftTranslateCommand)){
            return false;
        }
        CraftTranslateCommand other = (CraftTranslateCommand) obj;
        return other.craft.equals(this.craft) &&
                other.displacement.equals(this.displacement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(craft, displacement);
    }
}
