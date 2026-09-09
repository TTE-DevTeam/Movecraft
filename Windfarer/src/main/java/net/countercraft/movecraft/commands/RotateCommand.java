package net.countercraft.movecraft.commands;

import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.MovecraftRotation;
import net.countercraft.movecraft.commands.argument.type.EnumArgumentType;
import net.countercraft.movecraft.craft.*;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

import java.util.List;
import java.util.Optional;

public class RotateCommand {

    enum ROTATE_OPTIONS {
        LEFT(MovecraftRotation.ANTICLOCKWISE),
        RIGHT(MovecraftRotation.CLOCKWISE),
        ;

        private MovecraftRotation movecraftRotation;

        ROTATE_OPTIONS(MovecraftRotation movecraftRotation) {
            this.movecraftRotation = movecraftRotation;
        }

        public MovecraftRotation movecraftRotation() {
            return this.movecraftRotation;
        }
    }

    // TODO: Fail messages
    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("rotate")
                        .requires(source -> {
                            if (!(source.getExecutor() instanceof Entity)) {
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.rotate");
                        })
                        .executes(
                                context -> {
                                    process(context.getSource().getExecutor(), context.getSource().getSender());
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                }
                        )
                        .then(Commands.argument("rotation", EnumArgumentType.ofEnum(ROTATE_OPTIONS.class))
                                .executes(context -> {
                                    ROTATE_OPTIONS rotation = context.getArgument("rotation", ROTATE_OPTIONS.class);
                                    if (rotation == null) {
                                        process(context.getSource().getExecutor(), context.getSource().getSender());
                                    } else {
                                        process(context.getSource().getExecutor(), context.getSource().getSender(), rotation);
                                    }
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                })
                        )
                        .build(),
                "Command to rotate your craft",
                List.of()
        );
    }

    static void process (Entity executor, CommandSender commandSender, ROTATE_OPTIONS rotation) {
        if (executor == null) {
            return;
        }
        final Optional<Craft> optCraft = CraftManager.getInstance().getCraftsInWorld(executor.getWorld())
                .stream()
                .filter(craftTmp -> {
                    if (executor instanceof Player) {
                        return craftTmp instanceof PlayerCraft pc && pc.getPilotUUID() != null && pc.getPilotUUID().equals(executor.getUniqueId());
                    } else {
                        return craftTmp instanceof PilotedCraft pc && pc.getPilotUUID() != null && pc.getPilotUUID().equals(executor.getUniqueId());
                    }
                })
                .findFirst();
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();
            /*if (!craft.getCraftProperties().get(PropertyKeys.CAN_CRUISE)) {
                sender.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Cruise - Craft Cannot Rotate"));
            }
            else*/ if (!commandSender.hasPermission("movecraft." + craft.getCraftProperties().getName().toLowerCase() + ".rotate")) {
                executor.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Insufficient Permissions"));
            }
            else if (rotation == null) {
                rotation = ROTATE_OPTIONS.RIGHT;
            }
            craft.rotate(rotation.movecraftRotation(), craft.getHitBox().getMidPoint());
        } else {
            executor.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("You must be piloting a craft"));
        }
    }

    static void process(Entity executor, CommandSender commandSender) {
        process(executor, commandSender, ROTATE_OPTIONS.RIGHT);
    }

}
