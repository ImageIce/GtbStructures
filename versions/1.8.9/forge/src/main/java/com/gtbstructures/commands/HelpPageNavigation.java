package com.gtbstructures.commands;

import java.util.function.IntFunction;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/** Inline header navigation; disabled directions have no interactive events. */
final class HelpPageNavigation {
    private HelpPageNavigation() {
    }

    static IChatComponent build(String prefix, int page, int pages,
            IntFunction<String> pageCommand, String suffix) {
        if (pages <= 1) return null;
        if (page < 1 || page > pages) throw new IllegalArgumentException("Page outside result bounds");
        ChatComponentText navigation = new ChatComponentText("");
        navigation.appendSibling(chevron("\u00ab", "Previous page",
                page > 1 ? pageCommand.apply(page - 1) : null));
        ChatComponentText position = new ChatComponentText(" " + page + "/" + pages + " ");
        position.getChatStyle().setColor(EnumChatFormatting.GRAY);
        navigation.appendSibling(position);
        navigation.appendSibling(chevron("\u00bb", "Next page",
                page < pages ? pageCommand.apply(page + 1) : null));
        ChatComponentText header = new ChatComponentText(prefix);
        header.appendSibling(navigation);
        header.appendText(suffix);
        return header;
    }

    private static IChatComponent chevron(String text, String label, String command) {
        ChatComponentText button = new ChatComponentText(text);
        button.getChatStyle().setColor(command == null
                ? EnumChatFormatting.DARK_GRAY : EnumChatFormatting.GOLD);
        if (command != null) {
            button.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
            button.getChatStyle().setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText("\u00a7e" + label + "\n\u00a77" + command)));
        }
        return button;
    }
}
