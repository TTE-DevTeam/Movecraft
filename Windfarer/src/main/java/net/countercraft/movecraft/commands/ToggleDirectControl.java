package net.countercraft.movecraft.commands;

import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.commands.argument.type.EnumArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.controller.directControl.HelmsManManager;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class ToggleDirectControl {

    enum STATE {
        ON,
        OFF,
        TOGGLE
    }

    // TODO: Fail messages
    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("directcontrol")
                        .requires(source -> {
                            if (!(source.getExecutor() instanceof Entity)) {
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.directcontrol");
                        })
                        .executes(
                                context -> {
                                    process(context.getSource().getExecutor(), context.getSource().getSender(), STATE.TOGGLE);
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                }
                        )
                        .then(Commands.argument("toggle", EnumArgumentType.ofEnum(ToggleDirectControl.STATE.class))
                                .executes(context -> {
                                    ToggleDirectControl.STATE state = context.getArgument("toggle", ToggleDirectControl.STATE.class);
                                    if (state == null) {
                                        process(context.getSource().getExecutor(), context.getSource().getSender(), STATE.TOGGLE);
                                    } else {
                                        process(context.getSource().getExecutor(), context.getSource().getSender(), state);
                                    }
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                })
                        )
                        .build(),
                "Command to enter or leave direct control of the craft you are the helmsman of",
                List.of()
        );
    }

    static void process(Entity executor, CommandSender commandSender, ToggleDirectControl.STATE state) {
        if (executor == null || !(executor instanceof Player)) {
            return;
        }
        final Player player = (Player) executor;
        final Optional<Craft> optCraft = Optional.ofNullable(CraftManager.getInstance().getCraftByHelmsMan(player));
        if (optCraft.isPresent() && optCraft.get() instanceof PlayerCraft craft) {
            if (HelmsManManager.getHelmsMan(craft) == player) {
                if (!craft.getCraftProperties().get(PropertyKeys.CAN_DIRECT_CONTROL)) {
                    commandSender.sendMessage(I18nSupport.getInternationalisedString("Direct Control - Not allowed on craft"));
                    return;
                }
                switch (state) {
                    case ON:
                        if (!craft.getPilotLocked()) {
                            HelmsManManager.enterDirectControl(player, craft);
                        }
                        commandSender.sendMessage(I18nSupport.getInternationalisedString("Direct Control - Entering"));
                    case OFF:
                        if (craft.getPilotLocked()) {
                            HelmsManManager.resetDirectControl(craft);
                        }
                        commandSender.sendMessage(I18nSupport.getInternationalisedString("Direct Control - Leaving"));
                    default:
                        if (craft.getPilotLocked()) {
                            HelmsManManager.resetDirectControl(craft);
                            commandSender.sendMessage(I18nSupport.getInternationalisedString("Direct Control - Leaving"));
                        } else {
                            HelmsManManager.enterDirectControl(player, craft);
                            commandSender.sendMessage(I18nSupport.getInternationalisedString("Direct Control - Entering"));
                        }
                }
            } else {
                commandSender.sendMessage(I18nSupport.getInternationalisedString("Direct Control - Only for helmsman"));
            }
        } else {
            executor.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("You must be piloting a craft"));
        }
    }

}
