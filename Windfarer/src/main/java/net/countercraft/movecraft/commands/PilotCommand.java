package net.countercraft.movecraft.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.commands.argument.type.CraftTypeArgumentType;
import net.countercraft.movecraft.craft.*;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.functions.CraftSupplier;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.util.ChatUtils;
import net.countercraft.movecraft.util.MathUtils;
import net.countercraft.movecraft.util.Pair;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

public class PilotCommand implements IBrigadierCommandHelper {

    public static void register(final Commands commands) {
        final ArgumentBuilder<CommandSourceStack, ?> argName = Commands.argument("name", StringArgumentType.string());
        final ArgumentBuilder<CommandSourceStack, ?> argLifetime = Commands.argument("lifetime", LongArgumentType.longArg(0));
        final ArgumentBuilder<CommandSourceStack, ?> argAutoRelease = Commands.argument("shouldAutoRelease", BoolArgumentType.bool());
        final ArgumentBuilder<CommandSourceStack, ?> argPilot = Commands.argument("pilot", ArgumentTypes.entity());

        commands.register(
                Commands.literal("pilot")
                        .requires(source -> {
                            if (!(source.getExecutor() instanceof Entity)) {
                                source.getSender().sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Pilot - Must Be Entity")));
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.pilot");
                        })
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Pilot - No Craft Type")));
                            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("type", new CraftTypeArgumentType())
                                .executes(PilotCommand::processNormal)
                                // Pilot as non piloted craft
                                .then(Commands.literal("--null").requires(css -> css.getSender().hasPermission("movecraft.commands.pilot.null"))
                                        .executes(PilotCommand::processNullPiloted)
                                        .then(Commands.argument("lifetime", LongArgumentType.longArg(0))
                                                .executes(PilotCommand::processNullPiloted)
                                        )
                                        .then(Commands.argument("shouldAutoRelease", BoolArgumentType.bool())
                                                .executes(PilotCommand::processNullPiloted)
                                        )
                                )
                                // Pilot as sinking craft
                                .then(Commands.literal("--sinking").requires(css -> css.getSender().hasPermission("movecraft.commands.pilot.sinking"))
                                        .executes(PilotCommand::processSinking)
                                )
                                // Pilot as NPC craft
                                .then(Commands.literal("--npc").requires(css -> css.getSender().hasPermission("movecraft.commands.pilot.npc"))
                                        .executes(PilotCommand::processNPC)
                                        .then(
                                                argName
                                                        .then(
                                                                argLifetime
                                                                        .then(
                                                                                argAutoRelease
                                                                                        .then(
                                                                                                argPilot
                                                                                                        .executes(PilotCommand::processNPC)
                                                                                        )
                                                                        )
                                                        )
                                        )
                                )
                        )
                        .build(),
                "Command to change the cruise state of your craft",
                List.of()
        );
    }

    private static int processNormal(CommandContext<CommandSourceStack> commandContext) {
        final CraftSupplier supplier = (type, w, p, parents) -> {
            assert p != null; // Note: This only passes in a non-null player.
            if (parents.size() > 0)
                return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedComponent(
                        "Detection - Failed - Already commanding a craft")), null);

            if (p instanceof Player player) {
                return new Pair<>(Result.succeed(),
                        new PlayerCraftImpl(type, w, player));
            } else {
                return new Pair<>(Result.succeed(), new PilotedCraftImpl(type, w, p));
            }
        };
        final Function<Craft, Effect> postDetection = craft -> () -> {
            Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(craft, CraftPilotEvent.Reason.PLAYER));
            if (commandContext.getSource().getExecutor() instanceof Player player) {
                // Release old craft if it exists
                Craft oldCraft = CraftManager.getInstance().getCraftByPlayer(player);
                if(oldCraft != null)
                    CraftManager.getInstance().release(oldCraft, CraftReleaseEvent.Reason.PLAYER, false);
            }
        };
        final @Nullable Entity executor = commandContext.getSource().getExecutor();
        return process(commandContext, supplier, executor, executor != null ? executor : commandContext.getSource().getSender(), postDetection);
    }

    private static int processNPC(CommandContext<CommandSourceStack> commandContext) {
        final long timeOut = Math.abs(IBrigadierCommandHelper.tryGetArgument("lifetime", Long.class, commandContext, 5000L));
        final boolean autoRelease = IBrigadierCommandHelper.tryGetArgument("shouldAutoRelease", Boolean.class, commandContext, false);
        final Entity pilot = IBrigadierCommandHelper.tryGetArgument("pilot", Entity.class, commandContext, commandContext.getSource().getExecutor());
        final String name = IBrigadierCommandHelper.tryGetArgument("name", String.class, commandContext, "");
        Component nameComponent;
        if (name.length() > 0) {
            nameComponent = MiniMessage.miniMessage().deserialize(name);
        } else {
            nameComponent = null;
        }
        final CraftSupplier supplier = (type, w, p, parents) -> {
            if (parents.size() > 0)
                return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedComponent(
                        "Detection - Failed - Already commanding a craft")), null);

            return new Pair<>(Result.succeed(), new NPCCraft(type, w, autoRelease, timeOut, nameComponent, pilot));
        };
        final Function<Craft, Effect> postDetection = craft -> () -> {
            Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(craft, CraftPilotEvent.Reason.COMMAND));
            if (pilot != null) {
                // Release old craft if it exists
                Craft oldCraft = CraftManager.getInstance().getCraftByEntity(pilot);
                if(oldCraft != null)
                    CraftManager.getInstance().release(oldCraft, CraftReleaseEvent.Reason.PLAYER, false);
            }
        };
        return process(commandContext, supplier, pilot, pilot != null ? pilot : Bukkit.getConsoleSender(), postDetection);
    }

    private static int processSinking(CommandContext<CommandSourceStack> commandContext) {
        final CraftSupplier supplier = (type, w, p, parents) -> {
            if (parents.size() > 0)
                return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedComponent(
                        "Detection - Failed - Already commanding a craft")), null);

            return new Pair<>(Result.succeed(), new SinkingCraftImpl(new PilotedCraftImpl(type, w, p)));
        };
        final Function<Craft, Effect> postDetection = craft -> () -> {
            // Do nothing
        };
        return process(commandContext, supplier, null, Audience.empty(), postDetection);
    }

    private static int processNullPiloted(CommandContext<CommandSourceStack> commandContext) {
        final long timeOut = Math.abs(IBrigadierCommandHelper.tryGetArgument("lifetime", Long.class, commandContext, 5000L));
        final boolean autoRelease = IBrigadierCommandHelper.tryGetArgument("shouldAutoRelease", Boolean.class, commandContext, false);
        final CraftSupplier supplier = (type, w, p, parents) -> {
            if (parents.size() > 0)
                return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedComponent(
                        "Detection - Failed - Already commanding a craft")), null);

            return new Pair<>(Result.succeed(), new NullCraft(type, w, autoRelease, timeOut));
        };
        final Function<Craft, Effect> postDetection = craft -> () -> {
            Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(craft, CraftPilotEvent.Reason.COMMAND));
            if (commandContext.getSource().getExecutor() instanceof Entity pilot) {
                // Release old craft if it exists
                Craft oldCraft = CraftManager.getInstance().getCraftByEntity(pilot);
                if(oldCraft != null)
                    CraftManager.getInstance().release(oldCraft, CraftReleaseEvent.Reason.PLAYER, false);
            }
        };
        return process(commandContext, supplier, null, Bukkit.getConsoleSender(), postDetection);
    }

    static int process(final CommandContext<CommandSourceStack> commandContext, final CraftSupplier craftSupplier, final @Nullable Entity pilot, final Audience audience, final Function<Craft, Effect> postDetectAction) {
        final Entity executor = commandContext.getSource().getExecutor() == null ? commandContext.getSource().getSender() instanceof Entity ? (Entity) commandContext.getSource().getSender() : null : commandContext.getSource().getExecutor();
        if (executor == null) {
            return -1;
        } else {
            // TODO: Reinforce logic and check against the permissiosn here aswell!
            final TypeSafeCraftType craftType = IBrigadierCommandHelper.tryGetArgument("type", TypeSafeCraftType.class, commandContext, null);
            if (craftType == null) {
                commandContext.getSource().getSender().sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Pilot - No Craft Type")));
                return -1;
            }
            final World world = pilot == null ? executor.getWorld() : pilot.getWorld();
            final MovecraftLocation startPoint = MathUtils.bukkit2MovecraftLoc(executor.getLocation());

            CraftManager.getInstance().detect(
                    startPoint,
                    craftType,
                    craftSupplier,
                    world,
                    // Pilot
                    pilot,
                    // Audience
                    audience,
                    postDetectAction
            );
        }
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

}
