package com.azuriom.azlink.forge.legacy.command;

import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.command.AzLinkCommand;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /azlink} for Forge 1.7.10 ({@link CommandBase} / {@code ICommand}).
 */
public final class AzLinkLegacyCommand extends CommandBase {

    private final AzLinkCommand delegate;

    public AzLinkLegacyCommand(AzLinkPlugin plugin) {
        this.delegate = new AzLinkCommand(plugin);
    }

    @Override
    public String getCommandName() {
        return "azlink";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/azlink [status|setup|fetch|coins|port]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        this.delegate.execute(new ForgeLegacyCommandSender(sender), args == null ? new String[0] : args);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> completions = this.delegate.tabComplete(new ForgeLegacyCommandSender(sender),
                args == null ? new String[0] : args);
        return completions;
    }

    @Override
    public List getCommandAliases() {
        return Arrays.asList("azuriom", "azuriomlink");
    }
}
