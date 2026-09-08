package net.countercraft.movecraft.commands;

import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.events.ManOverboardEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import net.countercraft.movecraft.util.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class ManOverboardCommand {

    static final NamespacedKey MANOVERBOARD_LAST_TIME = new NamespacedKey("windfarer", "manoverboard_last_timestamp");

    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("manoverboard")
                        .requires(source -> {
                            if (!(source.getExecutor() instanceof Entity)) {
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.manoverboard")) {
                                return false;
                            }
                            return true;
                        })
                        .executes(
                                context -> {
                                    process(context.getSource().getExecutor(), context.getSource().getSender());
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                }
                        )

                        .build(),
                "If enabled, returns you to a craft you have fallen out of",
                List.of()
        );
    }

    static void process(Entity executor, CommandSender commandSender) {
        if (executor == null || !(executor instanceof Player)) {
            return;
        }
        final Player player = (Player) executor;
        final Optional<Craft> optCraft = Optional.ofNullable(CraftManager.getInstance().getCraftByHelmsMan(player));
        if (optCraft.isPresent() && optCraft.get() instanceof PlayerCraft craft) {
            if (craft == null) {
                player.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("ManOverboard - No Craft Found")));
                return;
            }

            Location telPoint = getCraftTeleportPoint(craft);
            if (craft.getWorld() != player.getWorld()) {
                player.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("ManOverboard - Other World")));
                return;
            }

            if ((System.currentTimeMillis() -
                    CraftManager.getInstance().getTimeFromOverboard(player)) / 1_000 > Settings.ManOverboardTimeout
                    && !MathUtils.locIsNearCraftFast(craft, MathUtils.bukkit2MovecraftLoc(player.getLocation()))) {
                player.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("ManOverboard - Timed Out")));
                return;
            }

            if (telPoint.distanceSquared(player.getLocation()) > Settings.ManOverboardDistSquared) {
                player.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("ManOverboard - Distance Too Far")));
                return;
            }

            if (craft.getDisabled() || craft instanceof SinkingCraft) {
                player.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("ManOverboard - Disabled")));
                return;
            }

            // Last manoverboard time for player
            if (Settings.ManOverboardCooldown > 0) {
                Long lastManoverboard = player.getPersistentDataContainer().get(MANOVERBOARD_LAST_TIME, PersistentDataType.LONG);
                if (lastManoverboard != null) {
                    // SECONDS!!
                    int minCooldown = Settings.ManOverboardCooldown * 1000;
                    long now = System.currentTimeMillis();
                    if ((now - lastManoverboard) < minCooldown) {
                        player.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("ManOverboard - Cooldown")));
                        return;
                    }
                    player.getPersistentDataContainer().remove(MANOVERBOARD_LAST_TIME);
                }
            }

            ManOverboardEvent event = new ManOverboardEvent(craft, telPoint);
            Bukkit.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                player.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("ManOverboard - Cancelled")));
                return;
            }

            player.getPersistentDataContainer().set(MANOVERBOARD_LAST_TIME, PersistentDataType.LONG, Long.valueOf(System.currentTimeMillis()));

            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
            Movecraft.getInstance().getSmoothTeleport().teleport(player, telPoint.getWorld(), telPoint.getX(), telPoint.getY(), telPoint.getZ(), 0, 0);
            return;
        } else {
            executor.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("ManOverboard - No Craft Found")));
        }
    }

    private static @NotNull Location getCraftTeleportPoint(@NotNull Craft craft) {
        double telX = ((craft.getHitBox().getMinX() + craft.getHitBox().getMaxX()) / 2D) + 0.5D;
        double telZ = ((craft.getHitBox().getMinZ() + craft.getHitBox().getMaxZ()) / 2D) + 0.5D;
        double telY = craft.getHitBox().getMaxY() + 1;
        return new Location(craft.getWorld(), telX, telY, telZ);
    }
}
