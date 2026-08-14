package com.gtbstructures.gtw;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.ClientCommandHandler;

import java.util.Collections;
import java.util.List;

/**
 * Client-side shortcut command "/gtw ..." that forwards to "/gtbs gtw ...".
 * Sub-arguments and tab completion are delegated to {@link GtwGame}.
 */
public class CommandGtw extends CommandBase {

    public static void register() {
        ClientCommandHandler.instance.registerCommand(new CommandGtw());
    }

    @Override
    public String getCommandName() {
        return "gtw";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.emptyList();
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtw <start|stop|showenglish> ...";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return GtwGame.tabGtw(args);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        GtwGame.processGtw(sender, args);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
