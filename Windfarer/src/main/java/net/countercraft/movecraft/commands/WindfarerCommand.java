package net.countercraft.movecraft.commands;

import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.commands.argument.type.EnumArgumentType;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.sign.AbstractMovecraftSign;
import net.countercraft.movecraft.sign.MovecraftSignRegistry;
import net.countercraft.movecraft.util.ChatUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class WindfarerCommand {

    enum WINDFARER_SUB_COMMANDS {
        VERSION,
        RELOAD_TYPES,
        TYPE_LIST,
        ACTIVE_CRAFTS,
        REGISTERED_SIGNS
    }

    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("windfarer")
                        .requires(source -> {
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.movecraft");
                        })
                        .executes(
                                context -> {
                                    processNoArgs(context.getSource().getSender());
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                }
                        )
                        .then(Commands.argument("subcommand", EnumArgumentType.ofEnum(WINDFARER_SUB_COMMANDS.class))
                                .executes(context -> {
                                    WINDFARER_SUB_COMMANDS subcommand = context.getArgument("subcommand", WINDFARER_SUB_COMMANDS.class);
                                    if (subcommand == null) {
                                        processNoArgs(context.getSource().getSender());
                                    } else {
                                        processSubCommand(context.getSource().getSender(), subcommand);
                                    }
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                })
                        )
                        .build(),
                "Administrative command of Windfarer",
                List.of("movecraft")
        );
    }

    protected static void processSubCommand(CommandSender sender, WINDFARER_SUB_COMMANDS subcommand) {
        switch(subcommand) {
            case RELOAD_TYPES:
                processReloadTypes(sender);
                break;
            case ACTIVE_CRAFTS:
                processActiveCrafts(sender);
                break;
            case TYPE_LIST:
                processTypeList(sender);
                break;
            case REGISTERED_SIGNS:
                processRegisteredSigns(sender);
                break;
            default:
                processVersion(sender);
        }
    }

    private static void processRegisteredSigns(CommandSender sender) {
        sender.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Windfarer - Registered Signs")));

        for (Map.Entry<String, AbstractMovecraftSign> entry : MovecraftSignRegistry.INSTANCE.entries()) {
            sender.sendMessage(Component.text(" - " + entry.getKey()).hoverEvent(
                            HoverEvent.showText(
                                    Component.text("Class: " + entry.getValue().getClass().getName())
                            )
                    )
            );
        }
    }

    private static void processTypeList(CommandSender sender) {
        sender.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Windfarer - Craft Types")));

        for (TypeSafeCraftType tsct : CraftManager.getInstance().getTypesafeCraftTypes()) {
            sender.sendMessage(Component.text(" - " + tsct.getName()).hoverEvent(
                    HoverEvent.showText(
                            Component.text("Parent: " + tsct.getParent() == null ? "n/a" : tsct.getParent().getName())
                    )
                )
            );
        }
    }

    private static void processActiveCrafts(CommandSender sender) {
        final int totalCrafts = CraftManager.getInstance().getCrafts().size();
        final int playerCrafts = CraftManager.getInstance().getPlayerCrafts().size();
        sender.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Windfarer - Active Crafts")));
        sender.sendMessage(Component.text("Total Crafts: " + totalCrafts));
        sender.sendMessage(Component.text("Player crafts: " + playerCrafts));
    }

    private static void processVersion(CommandSender sender) {
        PluginDescriptionFile descriptionFile = Movecraft.getInstance().getDescription();
        sender.sendMessage(ChatUtils.commandPrefix().append(Component.text("Windfarer " + descriptionFile.getVersion() + " by " + descriptionFile.getAuthors())));
    }

    private static void processReloadTypes(CommandSender sender) {
        try {
            CraftManager.getInstance().reloadCraftTypes();
            sender.sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Movecraft - Reloaded Types")));
        } catch(IOException ioex) {
            ioex.printStackTrace();
            sender.sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("Movecraft - Failed Reload Types")));
        }
    }

    protected static void processNoArgs(CommandSender sender) {
        processSubCommand(sender, WINDFARER_SUB_COMMANDS.VERSION);
    }

}
