/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.controller.directControl.HelmsManManager;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.events.CraftSinkEvent;
import net.countercraft.movecraft.events.TypesReloadedEvent;
import net.countercraft.movecraft.exception.NonCancellableReleaseException;
import net.countercraft.movecraft.features.status.StatusUpdateTask;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.processing.CachedMovecraftWorld;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.functions.CraftSupplier;
import net.countercraft.movecraft.processing.tasks.UpdateFuelBurnersTask;
import net.countercraft.movecraft.processing.tasks.detection.DetectionTask;
import net.countercraft.movecraft.util.TypeDependency;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CraftManager implements Iterable<Craft>{
    private static CraftManager instance;

    public static CraftManager getInstance() {
        return instance;
    }

    public static void initialize(boolean loadCraftTypes) {
        instance = new CraftManager(loadCraftTypes);
    }



    /**
     * Set of all crafts on the server, weakly ordered by their hashcode.
     * Note: Crafts are added in detection via the addCraft method, and removed in the removeCraft method.
     *   External plugins may still hold references to Crafts, but they are removed from this set when they are
     *   released.  It is therefore recommended best practice to only hold weak references to crafts to avoid memory
     *   leaks.
     */
    @NotNull private final Set<Craft> crafts = new ConcurrentSkipListSet<>(Comparator.comparingInt(Object::hashCode));
    /**
     * Map of players to their current craft.
     */
    @NotNull private final ConcurrentMap<UUID, PlayerCraft> playerCrafts = new ConcurrentHashMap<>();
    @NotNull private final ConcurrentMap<Craft, BukkitTask> releaseEvents = new ConcurrentHashMap<>();
    /**
     * Set of all craft types on the server.
     */
    private final ConcurrentMap<String, TypeSafeCraftType> craftTypeMap = new ConcurrentHashMap<>();


    private CraftManager(boolean loadCraftTypes) {
        if(loadCraftTypes) {
            try {
                loadCraftTypeSettings();
            } catch(IOException ioException) {
                ioException.printStackTrace();
                craftTypeMap.clear();
            }
        }
    }

    private void loadCraftTypeSettings() throws IOException {
        this.craftTypeMap.clear();
        File craftFileFolder = new File(Movecraft.getInstance().getDataFolder().getAbsolutePath() + "/types");

        // Install base types
        installBaseTypes(craftFileFolder);

        Set<Path> files = Files.find(
                craftFileFolder.toPath(),
                Integer.MAX_VALUE,
                (path, attribute) -> {
                    if (Files.isDirectory(path) || !Files.isReadable(path)) {
                        return false;
                    }
                    return path.getFileName().toString().endsWith(".crafttype");
                }
        ).collect(Collectors.toSet());

        // Before we load everything, we calculate our dependency tree
        // Dependency tree based loading
        Queue<File> queue = TypeDependency.buildLoadingQueue(files);
        Queue<TypeSafeCraftType> loadedTypes = new LinkedList<>();
        while (!queue.isEmpty()) {
            File file = queue.poll();
            // Replaces \ with / at the end
            final String name = file.getAbsolutePath().substring(craftFileFolder.getAbsolutePath().length() + 1, file.getAbsolutePath().lastIndexOf('.')).replace('\\', '/');
            Movecraft.getInstance().getLogger().info(String.format("Loading crafttype file <%s> (type name will be %s>)...", file.getName(), name));
            Optional<TypeSafeCraftType> optTypeSafeCraftType = TypeSafeCraftType.load(file, name, this::getCraftTypeByName, Movecraft.getInstance().getLogger());

            if (optTypeSafeCraftType.isEmpty()) {
                Movecraft.getInstance().getLogger().warning("FAILED to load crafttype file <" + file.getName() + ">! Skipping...");
            } else {
                final TypeSafeCraftType typeSafeCraftType = optTypeSafeCraftType.get();

                this.registerType(name, typeSafeCraftType, loadedTypes::remove);
                // Allow registering alias's for each type
                if (typeSafeCraftType.hasInSelfOrAnyParent(PropertyKeys.ALIAS, true, false)) {
                    this.registerType(typeSafeCraftType.get(PropertyKeys.ALIAS), typeSafeCraftType, (t) -> {});
                }

                loadedTypes.add(typeSafeCraftType);
            }
        }

        TypeSafeCraftType.runTransformers(new LinkedList<>(loadedTypes));
        Set<TypeSafeCraftType> validateList = new HashSet<>(loadedTypes);
        TypeSafeCraftType.runValidators(validateList);
        Set<String> toRemove = new HashSet<>();
        for (Map.Entry<String, TypeSafeCraftType> entry : this.craftTypeMap.entrySet()) {
            if (!loadedTypes.contains(entry.getValue())) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(this.craftTypeMap::remove);
    }

    private void registerType(final String id, final TypeSafeCraftType type, Consumer<TypeSafeCraftType> removeFromLoadListCallback) {
        final TypeSafeCraftType previous = this.craftTypeMap.put(id.toLowerCase(Locale.ROOT), type);

        if (previous != null) {
            Movecraft.getInstance().getLogger().warning("Overriding crafttype setting with name <" + id + ">! This means there are duplicates!");
            removeFromLoadListCallback.accept(previous);
        }
    }

    private static void installBaseTypes(File craftFileFolder) {
        // Check for directory
        if (craftFileFolder.isDirectory() && craftFileFolder.exists()) {
            return;
        }
        // Try to create directory
        if (!craftFileFolder.mkdirs()) {
            Movecraft.getInstance().getLogger().warning("FAILED to create directory<" + craftFileFolder.getPath() + ">! There WILL be errors!");
            return;
        }
        // Install base types
        final String[] BASE_TYPES = new String[]{
                "testtype.crafttype",
                "Airship.crafttype",
                "Airskiff.crafttype",
                "BigAirskiff.crafttype",
                "Elevator.crafttype",
                "LaunchBomb.crafttype",
                "LaunchTorpedo.crafttype",
                "MoveTool.crafttype",
                "Turret.crafttype",
                "Vehicle.crafttype"
        };
        Movecraft.getInstance().getLogger().info("Installing <" + BASE_TYPES.length + "> base types...");

        for (String filename : BASE_TYPES) {
            Movecraft.getInstance().getLogger().info("Installing base type <" + filename + ">...");
            Movecraft.getInstance().saveResource("types/" + filename, false);
        }

        Movecraft.getInstance().getLogger().info("Base type installation complete!");
    }

    public void reloadCraftTypes() throws IOException {
        loadCraftTypeSettings();
        Bukkit.getServer().getPluginManager().callEvent(new TypesReloadedEvent());
    }

    /**
     * Detect a craft and add it to the craft manager
     *
     * @param startPoint the starting point of the detection process
     * @param type the type of craft to detect
     * @param supplier the supplier run post-detection to create the craft.
     *   Note: This is where you can construct a custom Craft object if you want to, or tailor the detection process.
     * @param world the world to detect in
     * @param pilot the entity who is causing the detection
     *   Note: This is only used for logging and forwarded to the supplier.
     *   - It is highly encouraged to pass in a non-null value if a player is causing the detection.
     *   - If player is null, this will bypass protections like pilot signs and the like.
     * @param audience the audience to send detection messages to
     * @param postDetection the function run post-supplying to perform post-detection actions.
     *   Note: This is where you can perform any post-detection actions, such as starting a torpedo cruising.
     */
    public void detect(@NotNull MovecraftLocation startPoint,
                        @NotNull TypeSafeCraftType type, @NotNull CraftSupplier supplier,
                        @NotNull World world, @Nullable Entity pilot,
                        @NotNull Audience audience,
                        @NotNull Function<Craft, Effect> postDetection) {
        WorldManager.INSTANCE.submitAndRunNow(new DetectionTask(
                startPoint, CachedMovecraftWorld.of(world),
                type, supplier,
                world, pilot,
                audience,
                postDetection
        ));
    }

    public void detect(@NotNull MovecraftLocation startPoint,
                       @NotNull TypeSafeCraftType type, @NotNull CraftSupplier supplier,
                       @NotNull World world, @Nullable Entity pilot,
                       @NotNull Audience audience,
                       @NotNull Function<Craft, Effect> postDetection,
                       @Nullable Function<@Nullable Craft, Effect> alwaysRunAfter) {
        WorldManager.INSTANCE.submitAndRunNow(new DetectionTask(
                startPoint, CachedMovecraftWorld.of(world),
                type, supplier,
                world, pilot,
                audience,
                postDetection,
                alwaysRunAfter
        ));
    }

    public void add(@NotNull Craft c) {
        if (c instanceof PlayerCraft) {
            if (playerCrafts.containsKey(((PlayerCraft) c).getPilotUUID()))
                throw new IllegalStateException("Players may only have one PlayerCraft associated with them!");

            playerCrafts.put(((PlayerCraft) c).getPilotUUID(), (PlayerCraft) c);
        }

        crafts.add(c);

        // Immediately fire the Status update to make moveblocks apply and to have up-to-date values
        WorldManager.INSTANCE.submit(new StatusUpdateTask(c));
    }

    public void sink(@NotNull Craft craft) {
        sink(craft, CraftSinkEvent.SIMPLE_SINK_REASONS.UNKNOWN);
    }
    public void sink(@NotNull Craft craft, CraftSinkEvent.SinkReason sinkReason) {
        this.trySink(craft, sinkReason);
    }
    public boolean trySink(@NotNull Craft craft, CraftSinkEvent.SinkReason sinkReason) {
        CraftSinkEvent event = new CraftSinkEvent(craft, sinkReason);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        final MovecraftLocation craftPos = craft.getHitBox().isEmpty() ? MovecraftLocation.zero() : craft.getHitBox().getMidPoint();
        Movecraft.getInstance().getLogger().info(
                String.format(
                        "Sunk craft %s (%s) at [%d %d %d] in %s - reason: %s",
                        craft.getUUID().toString(),
                        PlainTextComponentSerializer.plainText().serialize(craft.getName()),
                        craftPos.getX(),
                        craftPos.getY(),
                        craftPos.getZ(),
                        craft.getWorld().getName(),
                        event.getReason().getName()
                )
        );
        this.removeCraft(craft);
        return crafts.add(new SinkingCraftImpl(craft));
    }

    public void removeCraft(final Craft craft) {
        crafts.remove(craft);
        CraftCache.removeCraft(craft);
        if (craft instanceof PlayerCraft)
            playerCrafts.remove(((PlayerCraft) craft).getPilotUUID());

        // TODO: Is this safe?
        HelmsManManager.activePilotToCraftUUID.values().remove(craft);

        craft.onReleased();
    }

    public void release(@NotNull Craft craft, @NotNull CraftReleaseEvent.Reason reason, boolean force) {
        boolean result = this.tryRelease(craft, reason, force);
    }

    public boolean tryRelease(@NotNull Craft craft, @NotNull CraftReleaseEvent.Reason reason, boolean force) {
        CraftReleaseEvent e = new CraftReleaseEvent(craft, reason);
        Bukkit.getServer().getPluginManager().callEvent(e);
        if (e.isCancelled()) {
            if (force)
                throw new NonCancellableReleaseException();
            else
                return false;
        }

        // Turn off furnaces
        // TODO: Not sure if this will work at all
        // TODO: FInd a prettier solution! THis is a pretty ugly workaround...
        Bukkit.getScheduler().runTaskLater(Movecraft.getInstance(), () -> {
            WorldManager.INSTANCE.submitAndRunNow(new UpdateFuelBurnersTask(craft, false));
        }, 10);

        if(craft.getHitBox().isEmpty())
            Movecraft.getInstance().getLogger().warning(I18nSupport.getInternationalisedString(
                    "Release - Empty Craft Release Console"));
        else {
            if (craft instanceof PlayerCraft)
                craft.getAudience().sendMessage(Component.text(I18nSupport.getInternationalisedString(
                        "Release - Craft has been released")));
            if (craft instanceof PilotedCraft)
                Movecraft.getInstance().getLogger().info(String.format(I18nSupport.getInternationalisedString(
                        "Release - Player has released a craft console"),
                        ((PilotedCraft) craft).getPilotEntity() == null ? ((PilotedCraft) craft).getPilotUUID().toString() : ((PilotedCraft) craft).getPilotEntity().getName(),
                        craft.getCraftProperties().getName(),
                        craft.getHitBox().size(),
                        craft.getHitBox().getMinX(),
                        craft.getHitBox().getMinY(),
                        craft.getHitBox().getMinZ(),
                        craft.getWorld().getName()
                ));
            else
                Movecraft.getInstance().getLogger().info(String.format(I18nSupport.getInternationalisedString(
                        "Release - Null Craft Release Console"),
                        craft.getCraftProperties().getName(),
                        craft.getHitBox().size(),
                        craft.getHitBox().getMinX(),
                        craft.getHitBox().getMinY(),
                        craft.getHitBox().getMinZ(),
                        craft.getWorld().getName()
                ));
        }
        Movecraft.getInstance().getWreckManager().queueWreck(craft);

        this.removeCraft(craft);

        return true;
    }

    //region Craft management
    @Nullable
    @Deprecated
    public Player getPlayerFromCraft(@NotNull Craft c) {
        for (var entry : playerCrafts.entrySet()) {
            if (entry.getValue() == c)
                return Bukkit.getPlayer(entry.getKey());
        }
        return null;
    }

    @Deprecated
    public final void addReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (p != null) {
            p.sendMessage(I18nSupport.getInternationalisedString("Release - Player has left craft"));
        }
        BukkitTask releaseTask = new BukkitRunnable() {
            @Override
            public void run() {
                release(c, CraftReleaseEvent.Reason.PLAYER, false);
                // I'm aware this is not ideal, but you shouldn't be using this anyways.
            }
        }.runTaskLater(Movecraft.getInstance(), (20 * 15));
        releaseEvents.put(c, releaseTask);

    }

    @Deprecated
    public final void removeReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (p != null) {
            if (releaseEvents.containsKey(c)) {
                if (releaseEvents.get(c) != null)
                    releaseEvents.get(c).cancel();
                releaseEvents.remove(c);
            }
        }
    }

    @Deprecated
    public boolean isReleasing(final Craft craft) {
        return releaseEvents.containsKey(craft);
    }
    //endregion

    //region Type management
    @NotNull
    @Deprecated(forRemoval = true)
    public Set<CraftType> getCraftTypes() {
        Set<CraftType> result = new HashSet(this.craftTypeMap.values().size());
        for (TypeSafeCraftType typeSafeCraftType : this.craftTypeMap.values()) {
            result.add(new CraftType(typeSafeCraftType));
        }
        return Collections.unmodifiableSet(result);
    }

    @Nullable
    @Deprecated(forRemoval = true)
    public CraftType getCraftTypeFromString(String s) {
        TypeSafeCraftType typeSafeCraftType = this.getCraftTypeByName(s);
        if (typeSafeCraftType == null) {
            return null;
        }
        return new CraftType(typeSafeCraftType);
    }
    //endregion

    public Set<TypeSafeCraftType> getTypesafeCraftTypes() {
        return this.craftTypeMap.values().stream().collect(Collectors.toUnmodifiableSet());
    }

    public TypeSafeCraftType getCraftTypeByName(String ident) {
        return craftTypeMap.getOrDefault(ident.toLowerCase(Locale.ROOT), null);
    }

    //region Craft set management
    @NotNull
    public Set<Craft> getCrafts() {
        return Collections.unmodifiableSet(crafts);
    }

    @NotNull
    public Collection<PlayerCraft> getPlayerCrafts() {
        return Collections.unmodifiableCollection(playerCrafts.values());
    }

    @NotNull
    public Set<Craft> getCraftsInWorld(@NotNull World w) {
        Set<Craft> crafts = new HashSet<>(this.crafts.size(), 1); // never has to resize
        for (Craft c : this.crafts) {
            if (c.getMovecraftWorld().getWorldUUID() == w.getUID())
                crafts.add(c);
        }
        return crafts;
    }

    @NotNull
    public Set<PlayerCraft> getPlayerCraftsInWorld(World w) {
        Set<PlayerCraft> crafts = new HashSet<>(this.crafts.size(), 1); // never has to resize
        for (PlayerCraft craft : playerCrafts.values()) {
            if (craft.getMovecraftWorld().getWorldUUID() == w.getUID())
                crafts.add(craft);
        }
        return crafts;
    }

    @Contract("null -> null")
    @Nullable
    public PlayerCraft getCraftByPlayer(@Nullable Player p) {
        if(p == null)
            return null;
        return playerCrafts.get(p.getUniqueId());
    }

    public PlayerCraft getCraftByHelmsMan(Player player) {
        if (player == null) {
            return null;
        }
        return HelmsManManager.activePilotToCraftUUID.getOrDefault(player.getUniqueId(), null);
    }

    @Contract("null -> null")
    @Nullable
    public Craft getCraftByEntity(@Nullable Entity entity) {
        if(entity == null || entity.getUniqueId() == null)
            return null;
        if (entity instanceof Player) {
            return getCraftByPlayer((Player) entity);
        }
        for (Craft craft : crafts) {
            if (craft instanceof PilotedCraft pilotedCraft) {
                final UUID pilotUUID = pilotedCraft.getPilotUUID();
                if (entity.getUniqueId().equals(pilotUUID)) {
                    return pilotedCraft;
                }
            }
        }
        return null;
    }

    public PlayerCraft getCraftByPlayerName(String name) {
        for (var entry : playerCrafts.entrySet()) {
            if (entry.getKey() != null && (Bukkit.getPlayer(entry.getKey()).getName().equals(name) || Bukkit.getOfflinePlayer(entry.getKey()).getName().equals(name)))
                return entry.getValue();
        }
        return null;
    }

    public boolean isEmpty() {
        return crafts.isEmpty();
    }

    @NotNull
    @Override
    public Iterator<Craft> iterator() {
        return Collections.unmodifiableSet(crafts).iterator();
    }
    //endregion

    //region Overboard management
    @NotNull private final WeakHashMap<Player, Long> overboards = new WeakHashMap<>();

    public void addOverboard(Player player) {
        overboards.put(player, System.currentTimeMillis());
    }

    public long getTimeFromOverboard(Player player) {
        return overboards.getOrDefault(player, 0L);
    }
    //endregion
}
