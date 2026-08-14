package com.gtbstructures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.lang.reflect.Method;

/** 26.1 keeps local chat insertion private while Forge has no public equivalent. */
public final class ClientChatBridge {
    private static volatile Method addMessageMethod;

    private ClientChatBridge() {
    }

    public static void addLocalSystemMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || message == null) return;
        try {
            ChatComponent chat = mc.gui.getChat();
            Method method = addMessageMethod;
            if (method == null || !method.getDeclaringClass().isInstance(chat)) {
                method = ChatComponent.class.getDeclaredMethod("addMessage", Component.class,
                        MessageSignature.class, GuiMessageSource.class, GuiMessageTag.class);
                method.setAccessible(true);
                addMessageMethod = method;
            }
            method.invoke(chat, message, null, GuiMessageSource.SYSTEM_CLIENT, GuiMessageTag.system());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            if (mc.player != null) mc.player.sendSystemMessage(message);
        }
    }
}
