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

package net.countercraft.movecraft.listener;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.type.CraftType;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.MathUtils;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class PlayerListener implements Listener {
    private final Map<Craft, Long> timeToReleaseAfter = new WeakHashMap<>();
    private final Map<UUID, Integer> logoutReleaseTaskRelation = new HashMap<>();

    private Set<Location> checkCraftBorders(Craft craft) {
        Set<Location> mergePoints = new HashSet<>();
        final EnumSet<Material> ALLOWED_BLOCKS = craft.getType().getMaterialSetProperty(CraftType.ALLOWED_BLOCKS);
        final EnumSet<Material> FORBIDDEN_BLOCKS = craft.getType().getMaterialSetProperty(CraftType.FORBIDDEN_BLOCKS);
        final MovecraftLocation[] SHIFTS = {
                //x
                new MovecraftLocation(-1, 0, 0),
                new MovecraftLocation(-1, -1, 0),
                new MovecraftLocation(-1,1,0),
                new MovecraftLocation(1, -1, 0),
                new MovecraftLocation(1, 1, 0),
                new MovecraftLocation(1, 0, 0),
                //z
                new MovecraftLocation(0, 1, 1),
                new MovecraftLocation(0, 0, 1),
                new MovecraftLocation(0, -1, 1),
                new MovecraftLocation(0, 1, -1),
                new MovecraftLocation(0, 0, -1),
                new MovecraftLocation(0, -1, -1),
                //y
                new MovecraftLocation(0, 1, 0),
                new MovecraftLocation(0, -1, 0)};
        //Check each location in the hitbox
        for (MovecraftLocation ml : craft.getHitBox()){
            //Check the surroundings of each location
            for (MovecraftLocation shift : SHIFTS){
                MovecraftLocation test = ml.add(shift);
                //Ignore locations contained in the craft's hitbox
                if (craft.getHitBox().contains(test)){
                    continue;
                }
                Block testBlock = test.toBukkit(craft.getWorld()).getBlock();
                Material testMaterial = testBlock.getType();
                //Break the loop if an allowed block is found adjacent to the craft's hitbox
                if (ALLOWED_BLOCKS.contains(testMaterial)){
                    mergePoints.add(testBlock.getLocation());
                }
                //Do the same if a forbidden block is found
                else if (FORBIDDEN_BLOCKS.contains(testMaterial)){
                    mergePoints.add(testBlock.getLocation());
                }
            }
        }
        //Return the string representation of the merging point and alert the pilot
        return mergePoints;
    }

    @EventHandler
    public void onPlayerLogout(PlayerQuitEvent e) {
        final PlayerCraft craft = CraftManager.getInstance().getCraftByPlayer(e.getPlayer());
        if (craft == null)
            return;

        if (!Settings.ReleaseCraftOnLogout) {
            // Remove audience as that is a player reference
            craft.setAudience(Audience.empty());
            if (Settings.ReleaseCraftTimeOutAfterLogOut > 0) {
                int taskID = Bukkit.getScheduler().scheduleSyncDelayedTask(Movecraft.getInstance(), () -> {
                    // TODO: Do not instantly release, release based on a timer
                    CraftManager.getInstance().release(craft, CraftReleaseEvent.Reason.DISCONNECT, false);
                    logoutReleaseTaskRelation.remove(craft.getPilotUUID());
                }, Settings.ReleaseCraftTimeOutAfterLogOut);
                logoutReleaseTaskRelation.put(craft.getPilotUUID(), taskID);
            }

            return;
        }

        CraftManager.getInstance().release(craft, CraftReleaseEvent.Reason.DISCONNECT, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!Settings.ReleaseCraftOnLogout) {
            if (logoutReleaseTaskRelation.containsKey(event.getPlayer().getUniqueId())) {
                int taskID = logoutReleaseTaskRelation.get(event.getPlayer().getUniqueId());
                Bukkit.getScheduler().cancelTask(taskID);
                logoutReleaseTaskRelation.remove(event.getPlayer().getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        // when you shoot a craft and kill the pilot, it sinks
        if (!Settings.ReleaseOnDeath)
            return;

        Player p = e.getPlayer();
        Craft craft = CraftManager.getInstance().getCraftByPlayer(p);
        if (craft == null)
            return;

        CraftManager.getInstance().sink(craft);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        final Craft c = CraftManager.getInstance().getCraftByPlayer(p);

        if (c == null) {
            return;
        }

        if(MathUtils.locationNearHitBox(c.getHitBox(), p.getLocation(), 2)){
            timeToReleaseAfter.remove(c);
            return;
        }

        if(timeToReleaseAfter.containsKey(c) && timeToReleaseAfter.get(c) < System.currentTimeMillis()){
            CraftManager.getInstance().release(c, CraftReleaseEvent.Reason.PLAYER, false);
            timeToReleaseAfter.remove(c);
            return;
        }

        if (c.isNotProcessing() && c.getType().getBoolProperty(CraftType.MOVE_ENTITIES)
                && !timeToReleaseAfter.containsKey(c)) {
            if (Settings.ManOverboardTimeout != 0) {
                c.getAudience().sendActionBar(I18nSupport.getInternationalisedComponent("Manoverboard - Player has left craft"));
                CraftManager.getInstance().addOverboard(p);
            }
            else {
                p.sendMessage(I18nSupport.getInternationalisedString("Release - Player has left craft"));
            }
            var mergePoints = checkCraftBorders(c);
            if (!mergePoints.isEmpty())
                p.sendMessage(I18nSupport.getInternationalisedString("Manoverboard - Craft May Merge"));
            timeToReleaseAfter.put(c, System.currentTimeMillis() + c.getType().getIntProperty(CraftType.RELEASE_TIMEOUT) * 1000L);
        }
    }
}
