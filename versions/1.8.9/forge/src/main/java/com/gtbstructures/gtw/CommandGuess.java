package com.gtbstructures.gtw;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.ClientCommandHandler;

import java.util.Collections;
import java.util.List;

/**
 * Client-side shortcut command "/guess <text>" that forwards to
 * "/gtbs guess ...". Free-text argument; no tab completion.
 */
public class CommandGuess extends CommandBase {

    public static void register() {
        ClientCommandHandler.instance.registerCommand(new CommandGuess());
    }

    @Override
    public String getCommandName() {
        return "guess";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.emptyList();
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/guess <text>";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return GtwGame.tabGuess(args);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        GtwGame.processGuess(sender, args);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
