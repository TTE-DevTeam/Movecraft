package net.countercraft.movecraft.commands.argument.type;

import com.google.common.base.Predicates;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import org.bukkit.permissions.Permissible;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class CraftTypeArgumentType implements CustomArgumentType.Converted<TypeSafeCraftType, String> {

    @Override
    public TypeSafeCraftType convert(String value) throws CommandSyntaxException {
        if (value != null) {
            TypeSafeCraftType type = CraftManager.getInstance().getCraftTypeByName(value.replace('"', ' ').trim());
            return type;
        }
        return null;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.string();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        S s = context.getSource();
        Predicate<TypeSafeCraftType> checkFunction;
        if (s instanceof Permissible permissible) {
            checkFunction = (craftType) -> !craftType.get(PropertyKeys.REQUIRE_PERM_FOR_ASSEMBLY, "") || permissible.hasPermission("movecraft." + craftType.getName().toLowerCase() + ".pilot");
        } else {
            checkFunction = Predicates.alwaysTrue();
        }
        CraftManager.getInstance().getTypesafeCraftTypes().forEach(ct -> {
            boolean hasPerm = checkFunction.test(ct);
            String ctNameLowerCase = ct.getName().toLowerCase();
            if (hasPerm && ctNameLowerCase.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(StringArgumentType.escapeIfRequired(ct.getName()));
            }
        });
        return builder.buildFuture();
    }

}
