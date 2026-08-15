package com.gtbstructures;

import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.commands.CommandGtbRuntime;
import com.gtbstructures.player.BbRankDisplayRepair;
import com.gtbstructures.player.BuilderLeaderboardFallback;
import com.gtbstructures.chat.ChatNameCompletion;
import com.gtbstructures.font.ClassicFontLanguageScreenButton;
import com.gtbstructures.font.ClassicFontResourcePack;
import com.gtbstructures.items.DisallowedItemHider;
import com.gtbstructures.chat.GuessChatAnnotator;
import com.gtbstructures.items.HandheldItemOverlayRenderer;
import com.gtbstructures.chat.ModernChatCooldownCounter;
import com.gtbstructures.overlay.ModernHudOverlayRenderer;
import com.gtbstructures.player.ModernPlayerListLogic;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.chat.ScrambleGuess;
import com.gtbstructures.chat.TrollMode;
import com.gtbstructures.overlay.WorldEnvironmentOverlayRenderer;
import com.gtbstructures.scoreboard.CustomScoreboardOverlayRenderer;
import com.gtbstructures.scoreboard.GameTracker;
import com.gtbstructures.scoreboard.GtbGame;
import com.gtbstructures.scoreboard.InterfacePreview;
import com.gtbstructures.scoreboard.PreviewEditorOverlayRenderer;
import com.gtbstructures.scoreboard.PreviewScreenController;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.stats.PlayerUuid;
import com.gtbstructures.stats.StatsArchive;
import com.gtbstructures.translation.TranslationData;
import com.gtbstructures.util.MinecraftClientUiBridge;
import com.gtbstructures.util.MinecraftFormatting;
import com.gtbstructures.util.RuntimeReflectionCache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GtbStructuresFabric implements ClientModInitializer {
    private static final String LEGACY_NOTICE_PREFIX = "\u00a76[\u00a7eGtbStructures\u00a76] \u00a77";
    public static final String MOD_ID = "gtbstructures";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String PLAY_GTB_COMMAND = "/play build_battle_guess_the_build";
    private static final long LOBBY_REJOIN_MIN_WAIT_MS = 750L;
    private static final long LOBBY_REJOIN_MAX_WAIT_MS = 3500L;
    private static final int PLAYER_LIST_POLL_INTERVAL_TICKS = 20;
    private static final int CLASSIC_FONT_REFRESH_INTERVAL_TICKS = 20;
    private static final String[] TEAM_COLOR_NAMES = new String[] {
            "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA",
            "DARK_RED", "DARK_PURPLE", "GOLD", "GRAY",
            "DARK_GRAY", "BLUE", "GREEN", "AQUA",
            "RED", "LIGHT_PURPLE", "YELLOW", "WHITE"
    };
    private static FabricServices runtimeServices;
    private static final Set<String> playerListRoster = new LinkedHashSet<String>();
    private static final ChatNameCompletion.Session chatNameCompletion = new ChatNameCompletion.Session();
    private static final BbRankDisplayRepair bbRankDisplayRepair = BbRankDisplayRepair.INSTANCE;
    private static int scoreboardTickCounter;
    private static int playerListTickCounter;
    private static GameStats.TrackingState lastPlayerListPollState;
    private static boolean fabricBbRankRepairDisabled;
    private static String pendingLobbyRejoinCommand;
    private static long pendingLobbyRejoinAtMs;
    private static boolean commandRefreshLogged;
    private static boolean fabricCommandCompletionsRefreshed;
    private static boolean classicFontModeApplied;
    private static int classicFontRefreshTicks;
    private static Object lastThemeSelectionScreen;
    private static Object pendingThemeSelectionScreen;
    private static String pendingThemeSelectionTitle;
    private static Object lastNonThemeSelectionScreen;
    private static Object lastClassicFontSettingsPollScreen;
    private static Object lastClassicFontSettingsFailureScreen;

    @Override
    public void onInitializeClient() {
        FabricServices services = new FabricServices();
        runtimeServices = services;
        CommandGtbRuntime.configure(services);
        initializeSharedRuntime(services.gameDir(), services.configDir());
        GameStats.INSTANCE.setAccountPlayerUuid(services.accountPlayerUuid());
        registerClientCommands();
        DisallowedItemHider.registerFabricHooks();
        registerClientConnectionEvents();
        registerClientTickEvents();
        registerClientMessageEvents();
        registerClientScreenEvents();
        InterfacePreview.setScreenController(new PreviewScreenController());
        PreviewEditorOverlayRenderer.registerBackdrop();
        CustomScoreboardOverlayRenderer.register();
        HandheldItemOverlayRenderer.register();
        ModernHudOverlayRenderer.register();
        WorldEnvironmentOverlayRenderer.register();
        PreviewEditorOverlayRenderer.register();
        LOGGER.info("GtbStructures Fabric initialized");
    }

    private static void initializeSharedRuntime(File gameDir, File configDir) {
        BuilderLeaderboardFallback.INSTANCE.init(configDir);
        GameStats.INSTANCE.setRuntimeGameDir(gameDir);
        GameStats.INSTANCE.setGtbHooks(GameTracker.INSTANCE);
        TranslationData.initAsync(gameDir);
        BuilderLeaderboardFallback.INSTANCE.refreshFromHypixelAsync(null);
    }

    private static void registerClientCommands() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback");
            Object event = callbackClass.getField("EVENT").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("register") && args != null && args.length >= 1) {
                            registerCommands(args[0]);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures command registration callback";
                        return null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric client commands", e);
        }
    }

    private static void registerClientTickEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents")
                    .getField("END_CLIENT_TICK").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onEndTick") && args != null && args.length >= 1) {
                            ensureFabricClientCommandsRegistered();
                            applyClassicFontMode(args[0]);
                            InterfacePreview.tick();
                            if (!GtbStructuresSettings.INSTANCE.modEnabled) {
                                bbRankDisplayRepair.restoreAll();
                                GameStats.INSTANCE.discardPendingAutonomousActions();
                                CommandGtbRuntime.discardPendingAutonomousActions();
                                ModernChatCooldownCounter.onTick(false, currentState(),
                                        System.currentTimeMillis());
                                resetFabricPlayerListPoll();
                                sendQueuedLobbyRejoinIfReady();
                                return null;
                            }
                            updateLocalPlayerName();
                            pollFabricThemeSelectionScreen(args[0]);
                            pollFabricFontSettingsScreen(args[0]);
                            ModernRuntimeBridge.onClientTick();
                            drainCommandRuntimeMessages();
                            drainNotificationMessages();
                            if (shouldPollFabricPlayerList(GameStats.INSTANCE.getState())) {
                                pollFabricPlayerList(args[0]);
                            }
                            pollFabricPhysicalTimer(args[0]);
                            DisallowedItemHider.onClientTick();
                            ModernChatCooldownCounter.onTick(GtbStructuresSettings.INSTANCE.chatCooldownCounter
                                            && GameStats.INSTANCE.isTrackingEnabled(),
                                    currentState(), System.currentTimeMillis());
                            sendAutoRequeueIfReady();
                            scoreboardTickCounter++;
                            if (scoreboardTickCounter >= 10) {
                                scoreboardTickCounter = 0;
                                pollFabricScoreboard(args[0]);
                            }
                            bbRankDisplayRepair.onClientTick(GameStats.INSTANCE.shouldRepairBbRankDisplay());
                            sendQueuedLobbyRejoinIfReady();
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures tick callback";
                        return null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric tick events", e);
        }
    }

    private static void registerClientConnectionEvents() {
        try {
            Class<?> eventsClass = Class.forName(
                    "net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents");
            registerClientConnectionEvent(eventsClass, "JOIN",
                    "net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents$Join",
                    "onPlayReady");
            registerClientConnectionEvent(eventsClass, "DISCONNECT",
                    "net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents$Disconnect",
                    "onPlayDisconnect");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to register GtbStructures Fabric connection reset hooks", e);
        }
    }

    private static void registerClientConnectionEvent(Class<?> eventsClass, String fieldName,
                                                      String callbackClassName, String callbackMethod)
            throws ReflectiveOperationException {
        Class<?> callbackClass = Class.forName(callbackClassName);
        Object event = eventsClass.getField(fieldName).get(null);
        Object callback = Proxy.newProxyInstance(
                GtbStructuresFabric.class.getClassLoader(),
                new Class<?>[] {callbackClass},
                (proxy, method, args) -> {
                    if (method.getName().equals(callbackMethod)) {
                        handleClientConnectionBoundary(fieldName.toLowerCase(java.util.Locale.ROOT));
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "GtbStructures Fabric connection " + fieldName + " hook";
                    }
                    return null;
                });
        registerEvent(event, callback);
    }

    private static void handleClientConnectionBoundary(String reason) {
        bbRankDisplayRepair.restoreAll();
        InterfacePreview.INSTANCE.disableSilently();
        ModernRuntimeBridge.onClientUnavailable();
        ModernPlayerListLogic.applySnapshot(new ModernPlayerListLogic.Snapshot(), playerListRoster);
        playerListRoster.clear();
        resetFabricPlayerListPoll();
        RawPacketRecorder.INSTANCE.captureOverlayDecision("fabric_connection", "reset", reason);
    }

    private static boolean shouldPollFabricPlayerList(GameStats.TrackingState state) {
        playerListTickCounter++;
        if (lastPlayerListPollState == state
                && playerListTickCounter < PLAYER_LIST_POLL_INTERVAL_TICKS) {
            return false;
        }
        playerListTickCounter = 0;
        lastPlayerListPollState = state;
        return true;
    }

    private static void resetFabricPlayerListPoll() {
        playerListTickCounter = 0;
        lastPlayerListPollState = null;
    }

    private static void updateLocalPlayerName() {
        if (runtimeServices != null) {
            GameStats.INSTANCE.setAccountPlayerName(runtimeServices.accountPlayerName());
            GameStats.INSTANCE.setLocalPlayerName(runtimeServices.localPlayerName());
        }
    }

    private static void drainNotificationMessages() {
        for (String message : GameStats.INSTANCE.drainNotificationMessages()) {
            sendClientFeedback(notificationWithCurrentHeader(message));
        }
    }

    static String notificationWithCurrentHeader(String message) {
        String body = message == null ? "" : message;
        if (body.startsWith(LEGACY_NOTICE_PREFIX)) {
            body = body.substring(LEGACY_NOTICE_PREFIX.length());
        }
        return CommandGtb.head + body;
    }

    private static void drainCommandRuntimeMessages() {
        for (String message : CommandGtbRuntime.onClientTick().messages()) {
            sendClientFeedback(message);
        }
    }

    private static void sendAutoRequeueIfReady() {
        if (runtimeServices != null
                && GameStats.INSTANCE.consumeAutoRequeueIfReady(System.currentTimeMillis())) {
            runtimeServices.sendServerCommand(PLAY_GTB_COMMAND);
        }
    }

    private static void queueLobbyRejoin(String command) {
        pendingLobbyRejoinCommand = command;
        pendingLobbyRejoinAtMs = System.currentTimeMillis();
    }

    private static void sendQueuedLobbyRejoinIfReady() {
        if (runtimeServices == null || pendingLobbyRejoinCommand == null) return;
        long now = System.currentTimeMillis();
        long elapsed = now - pendingLobbyRejoinAtMs;
        GameStats.TrackingState state = GameStats.INSTANCE.getState();
        if (!shouldSendQueuedLobbyRejoin(state, elapsed)) return;
        String command = pendingLobbyRejoinCommand;
        pendingLobbyRejoinCommand = null;
        pendingLobbyRejoinAtMs = 0L;
        runtimeServices.sendServerCommand(command);
    }

    static boolean shouldSendQueuedLobbyRejoin(GameStats.TrackingState state, long elapsedMs) {
        if (elapsedMs < LOBBY_REJOIN_MIN_WAIT_MS) return false;
        return state == GameStats.TrackingState.LOBBY || elapsedMs >= LOBBY_REJOIN_MAX_WAIT_MS;
    }

    private static GtbGame.State currentState() {
        GtbGame game = GameTracker.INSTANCE.game;
        return game == null ? GtbGame.State.NONE : game.state;
    }

    private static void registerClientMessageEvents() {
        registerFabricSendCommandEvents();
        registerFabricModifyChatEvents();
        registerFabricSendChatEvents();
        registerFabricChatMessageEvents();
        registerFabricModifyGameMessageEvents();
        registerFabricGameMessageEvents();
    }

    private static void registerFabricModifyChatEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents$ModifyChat");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents")
                    .getField("MODIFY_CHAT").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("modifySendChatMessage")
                                && args != null && args.length >= 1) {
                            String message = args[0] instanceof String ? (String) args[0] : null;
                            return transformOutboundMessage(message);
                        }
                        if (method.getName().equals("toString")) return "GtbStructures outbound guess transform hook";
                        return args != null && args.length >= 1 ? args[0] : null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric outbound guess transform hook", e);
        }
    }

    static String transformOutboundMessage(String message) {
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return message;
        String processed = ScrambleGuess.transformOutbound(message);
        return TrollMode.transformOutbound(processed);
    }

    private static void registerClientScreenEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterInit");
            Object event = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents")
                    .getField("AFTER_INIT").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("afterInit") && args != null && args.length >= 2) {
                            registerChatKeyCompletion(args[1]);
                            if (GtbStructuresSettings.INSTANCE.modEnabled) {
                                try {
                                    captureThemeSelectionScreen(args[1]);
                                } catch (ReflectiveOperationException | RuntimeException e) {
                                    LOGGER.warn("Unable to inspect GtbStructures Fabric initialized screen", e);
                                }
                                try {
                                    ClassicFontLanguageScreenButton.install(args[1], args[0],
                                            runtimeServices == null ? null : runtimeServices.gameDir());
                                } catch (ReflectiveOperationException | RuntimeException e) {
                                    LOGGER.warn("Unable to add GtbStructures Fabric font settings option", e);
                                }
                            }
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures chat key init callback";
                        return null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric screen events", e);
        }
    }

    private static void applyClassicFontMode(Object minecraft) {
        String effectiveFontMode = GtbStructuresSettings.INSTANCE.modEnabled
                ? GtbStructuresSettings.INSTANCE.fontMode : "new";
        if (!classicFontModeApplied) {
            classicFontModeApplied = true;
            ClassicFontResourcePack.applyWithoutReload(runtimeServices == null ? null : runtimeServices.gameDir(),
                    minecraft, effectiveFontMode);
            return;
        }
        classicFontRefreshTicks++;
        if (classicFontRefreshTicks < CLASSIC_FONT_REFRESH_INTERVAL_TICKS) return;
        classicFontRefreshTicks = 0;
        ClassicFontResourcePack.refreshLoadedFontSet(minecraft, effectiveFontMode);
    }

    private static void registerChatKeyCompletion(Object screen) {
        if (!isChatScreen(screen)) return;
        try {
            Class<?> eventsClass = Class.forName(
                    "net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents");
            Object event = invokeStaticEventWithArg(eventsClass, "allowKeyPress", screen);
            if (event == null) return;
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents$AllowKeyPress");
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("allowKeyPress") && args != null && args.length >= 2) {
                            int keyCode = keyCode(args[1]);
                            if (isEnterKey(keyCode) && tryDispatchChatLocalCommand(args[0])) {
                                return Boolean.FALSE;
                            }
                            if (keyCode == 258 && tryCompleteChatName(args[0])) {
                                return Boolean.FALSE;
                            }
                            return Boolean.TRUE;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures chat tab completion";
                        return Boolean.TRUE;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to register GtbStructures Fabric chat tab completion", e);
        }
    }

    private static void registerFabricSendCommandEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents$AllowCommand");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents")
                    .getField("ALLOW_COMMAND").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("allowSendCommandMessage") && args != null && args.length >= 1) {
                            String command = args[0] instanceof String ? (String) args[0] : null;
                            GameStats.INSTANCE.onOutboundServerCommand(command);
                            boolean handled = dispatchLocalCommand(command);
                            return Boolean.valueOf(!handled);
                        }
                        if (method.getName().equals("toString")) return "GtbStructures command send fallback";
                        return Boolean.TRUE;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric command send fallback", e);
        }
        registerFabricSentCommandEvents();
    }

    private static void registerFabricSentCommandEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents$Command");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents")
                    .getField("COMMAND").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onSendCommandMessage") && args != null && args.length >= 1) {
                            if (!GtbStructuresSettings.INSTANCE.modEnabled) return null;
                            String command = args[0] instanceof String ? (String) args[0] : null;
                            if (command != null) RawPacketRecorder.INSTANCE.captureOutboundChat("/" + command);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures sent command capture hook";
                        return null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric sent command capture hook", e);
        }
    }

    private static void registerFabricSendChatEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents$Chat");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents")
                    .getField("CHAT").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onSendChatMessage") && args != null && args.length >= 1) {
                            if (!GtbStructuresSettings.INSTANCE.modEnabled) return null;
                            String message = args[0] instanceof String ? (String) args[0] : null;
                            RawPacketRecorder.INSTANCE.captureOutboundChat(message);
                            ModernChatCooldownCounter.onOutboundChat(
                                    GtbStructuresSettings.INSTANCE.chatCooldownCounter
                                            && GameStats.INSTANCE.isTrackingEnabled(),
                                    currentState(), message, System.currentTimeMillis());
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures chat send cooldown hook";
                        return null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric chat send cooldown hook", e);
        }
    }

    private static void registerFabricChatMessageEvents() {
        registerFabricAllowChatMessageEvents();
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents$Chat");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents")
                    .getField("CHAT").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onReceiveChatMessage") && args != null && args.length >= 1) {
                            if (!GtbStructuresSettings.INSTANCE.modEnabled) return null;
                            ModernRuntimeBridge.onChat(formattedComponentString(args[0]),
                                    componentString(args[0]));
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures chat callback";
                        return null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric chat events", e);
        }
    }

    private static void registerFabricAllowChatMessageEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents$AllowChat");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents")
                    .getField("ALLOW_CHAT").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("allowReceiveChatMessage")
                                && args != null && args.length >= 1) {
                            if (!GtbStructuresSettings.INSTANCE.modEnabled) return Boolean.TRUE;
                            Object decorated = guessPeekDecoratedMessage(args[0]);
                            if (decorated != null && addChatText(decorated)) {
                                try {
                                    ModernRuntimeBridge.onChat(formattedComponentString(args[0]),
                                            componentString(args[0]));
                                } catch (ReflectiveOperationException | RuntimeException e) {
                                    LOGGER.warn("Unable to process decorated GtbStructures Fabric chat", e);
                                }
                                return Boolean.FALSE;
                            }
                            return Boolean.TRUE;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures guess-peek chat hook";
                        return Boolean.TRUE;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric guess-peek chat hook", e);
        }
    }

    private static Object guessPeekDecoratedMessage(Object component) {
        try {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if (!settings.modEnabled) return null;
            if (component == null) return null;
            if (!settings.guessPeek) return null;
            if (!GameStats.INSTANCE.isTrackingEnabled()) return null;
            if (!isGuessPeekAnnotationState(GameStats.INSTANCE.getState())) return null;
            if (!TranslationData.isLoaded()) return null;

            String plain = componentString(component);
            GuessChatAnnotator.ParsedGuess parsed = GuessChatAnnotator.parseGuess(plain);
            if (parsed == null) return null;
            GuessChatAnnotator.Annotation annotation = GuessChatAnnotator.annotationForGuess(
                    parsed.guess, settings.guessPeekShowLangCodes);
            if (annotation == null) return null;
            return buildGuessPeekMessage(component, parsed, annotation,
                    GtbStructuresSettings.normalizeGuessPeekDisplay(settings.guessPeekDisplay));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to decorate GtbStructures Fabric guess-peek chat", e);
            return null;
        }
    }

    private static boolean isGuessPeekAnnotationState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END;
    }

    private static Object buildGuessPeekMessage(Object original, GuessChatAnnotator.ParsedGuess parsed,
                                                GuessChatAnnotator.Annotation annotation,
                                                String display)
            throws ReflectiveOperationException {
        if (parsed == null || annotation == null) return null;
        Class<?> textClass = classAny("net.minecraft.text.Text",
                "net.minecraft.network.chat.Component", "net.minecraft.class_2561");
        if ("suffix".equals(display)) {
            Object suffix = literalText(textClass,
                    GuessChatAnnotator.suffixText(annotation.tooltip));
            return assembleGuessPeekMessage(original, suffix, null, null, display, textClass);
        }
        Object hover = createHoverEvent(annotation.tooltip);
        if (hover == null) return null;
        String command = GuessChatAnnotator.copyCommandForGuess(annotation.copyGuess);
        Object click = createClickEvent(command);
        return assembleGuessPeekMessage(original, null, hover, click, display, textClass);
    }

    private static Object assembleGuessPeekMessage(Object original, Object suffix,
                                                   Object hover, Object click,
                                                   String display, Class<?> textClass)
            throws ReflectiveOperationException {
        GuessPeekComponentParts parts = detachTrailingCopyButton(original, textClass);
        Object root = parts.base;
        if ("suffix".equals(display)) {
            if (suffix == null || !appendText(root, suffix, textClass)) return null;
        } else if (!applyGuessPeekStyle(root, hover, click)) {
            return null;
        }
        if (parts.trailing != null) appendText(root, parts.trailing, textClass);
        return root;
    }

    static Object assembleGuessPeekMessageForTest(Object original, Object suffix,
                                                  Object hover, Object click,
                                                  String display, Class<?> textClass)
            throws ReflectiveOperationException {
        return assembleGuessPeekMessage(original, suffix, hover, click, display, textClass);
    }

    private static GuessPeekComponentParts detachTrailingCopyButton(Object original, Class<?> textClass) {
        Object base = copyText(original, textClass);
        if (base == null) base = original;
        try {
            Object rawSiblings = invokeAnyOrNull(base, "getSiblings", "method_10855");
            if (!(rawSiblings instanceof List)) return new GuessPeekComponentParts(base, null);
            @SuppressWarnings("unchecked")
            List<Object> siblings = (List<Object>) rawSiblings;
            if (siblings.isEmpty()) return new GuessPeekComponentParts(base, null);
            Object last = siblings.get(siblings.size() - 1);
            String visible = componentString(last).trim();
            if (!"[C]".equals(visible)) return new GuessPeekComponentParts(base, null);
            siblings.remove(siblings.size() - 1);
            return new GuessPeekComponentParts(base, last);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return new GuessPeekComponentParts(base, null);
        }
    }

    private static final class GuessPeekComponentParts {
        final Object base;
        final Object trailing;

        GuessPeekComponentParts(Object base, Object trailing) {
            this.base = base;
            this.trailing = trailing;
        }
    }

    private static Object copyText(Object original, Class<?> textClass) {
        if (original == null) return null;
        for (String name : new String[] {"copy", "plainCopy", "method_27661", "method_54160"}) {
            try {
                Method method = original.getClass().getMethod(name);
                Object copy = method.invoke(original);
                if (copy != null && textClass.isInstance(copy)) return copy;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next copy method.
            }
        }
        return null;
    }

    private static boolean appendText(Object root, Object child, Class<?> textClass)
            throws ReflectiveOperationException {
        if (root == null || child == null) return false;
        for (String name : new String[] {"append", "method_10852"}) {
            for (Method method : root.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (!parameter.isInstance(child) && !parameter.isAssignableFrom(textClass)) continue;
                method.invoke(root, child);
                return true;
            }
        }
        return false;
    }

    static boolean appendTextForTest(Object root, Object child, Class<?> textClass)
            throws ReflectiveOperationException {
        return appendText(root, child, textClass);
    }

    private static boolean applyGuessPeekStyle(Object text, Object hover, Object click) {
        try {
            Object style = invokeAnyOrNull(text, "getStyle", "method_10866");
            if (style == null) return false;
            Object styled = style;
            if (hover == null) return false;
            Object hovered = withGuessPeekHoverEvent(styled, hover);
            if (hovered == null || hovered == styled) return false;
            styled = hovered;
            if (click != null) {
                Object updated = withGuessPeekClickEvent(styled, click);
                if (updated != null) styled = updated;
            }
            if (styled == style) return false;
            return invokeAnyWithArg(text, styled, "setStyle", "method_10862") != null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Object withGuessPeekHoverEvent(Object style, Object hover)
            throws ReflectiveOperationException {
        return invokeAnyWithArg(style, hover, "withHoverEvent", "method_10949");
    }

    private static Object withGuessPeekClickEvent(Object style, Object click)
            throws ReflectiveOperationException {
        return invokeAnyWithArg(style, click, "withClickEvent", "method_10958");
    }

    static Object withGuessPeekHoverEventForTest(Object style, Object hover)
            throws ReflectiveOperationException {
        return withGuessPeekHoverEvent(style, hover);
    }

    static Object withGuessPeekClickEventForTest(Object style, Object click)
            throws ReflectiveOperationException {
        return withGuessPeekClickEvent(style, click);
    }

    private static Object createHoverEvent(String tooltip) {
        try {
            Class<?> textClass = classAny("net.minecraft.text.Text",
                    "net.minecraft.network.chat.Component", "net.minecraft.class_2561");
            Object tooltipText = literalText(textClass, tooltip);
            Class<?> hoverClass = classAny("net.minecraft.text.HoverEvent",
                    "net.minecraft.network.chat.HoverEvent", "net.minecraft.class_2568");
            Object modern = constructNestedTextEvent(
                    hoverClass, tooltipText, "ShowText", "class_10613");
            if (modern != null) return modern;
            return constructActionEvent(hoverClass, tooltipText, "SHOW_TEXT", "show_text");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object createClickEvent(String command) {
        try {
            Class<?> clickClass = classAny("net.minecraft.text.ClickEvent",
                    "net.minecraft.network.chat.ClickEvent", "net.minecraft.class_2558");
            Object modern = constructNestedStringEvent(
                    clickClass, command, "RunCommand", "class_10609");
            if (modern != null) return modern;
            return constructActionEvent(clickClass, command, "RUN_COMMAND", "run_command");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object constructNestedTextEvent(Class<?> owner, Object text, String... simpleNames)
            throws ReflectiveOperationException {
        for (Class<?> nested : owner.getDeclaredClasses()) {
            if (!matchesSimpleName(nested, simpleNames)) continue;
            for (java.lang.reflect.Constructor<?> constructor : nested.getDeclaredConstructors()) {
                if (constructor.getParameterCount() != 1
                        || !constructor.getParameterTypes()[0].isInstance(text)) continue;
                constructor.setAccessible(true);
                return constructor.newInstance(text);
            }
        }
        return null;
    }

    private static Object constructNestedStringEvent(Class<?> owner, String value, String... simpleNames)
            throws ReflectiveOperationException {
        for (Class<?> nested : owner.getDeclaredClasses()) {
            if (!matchesSimpleName(nested, simpleNames)) continue;
            for (java.lang.reflect.Constructor<?> constructor : nested.getDeclaredConstructors()) {
                if (constructor.getParameterCount() != 1
                        || !constructor.getParameterTypes()[0].equals(String.class)) continue;
                constructor.setAccessible(true);
                return constructor.newInstance(value);
            }
        }
        return null;
    }

    private static boolean matchesSimpleName(Class<?> type, String... names) {
        if (type == null || names == null) return false;
        String simpleName = type.getSimpleName();
        for (String name : names) {
            if (simpleName.equals(name)) return true;
        }
        return false;
    }

    static Object constructGuessPeekHoverForTest(Class<?> owner, Object text)
            throws ReflectiveOperationException {
        return constructNestedTextEvent(owner, text, "ShowText", "class_10613");
    }

    static Object constructGuessPeekClickForTest(Class<?> owner, String command)
            throws ReflectiveOperationException {
        return constructNestedStringEvent(owner, command, "RunCommand", "class_10609");
    }

    private static Object constructActionEvent(Class<?> eventClass, Object value,
                                               String enumName, String serializedName)
            throws ReflectiveOperationException {
        for (java.lang.reflect.Constructor<?> constructor : eventClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != 2) continue;
            Class<?> actionClass = constructor.getParameterTypes()[0];
            Object action = actionConstant(actionClass, enumName, serializedName);
            if (action == null) continue;
            Class<?> valueClass = constructor.getParameterTypes()[1];
            if (value != null && !valueClass.isInstance(value)
                    && !valueClass.isAssignableFrom(value.getClass())) continue;
            constructor.setAccessible(true);
            return constructor.newInstance(action, value);
        }
        return null;
    }

    private static Object actionConstant(Class<?> actionClass, String enumName, String serializedName)
            throws ReflectiveOperationException {
        for (Field field : actionClass.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            Object value = field.get(null);
            if (value == null) continue;
            String name = field.getName();
            String text = String.valueOf(value);
            if (enumName.equals(name) || serializedName.equalsIgnoreCase(text)
                    || text.toLowerCase(java.util.Locale.ROOT).contains(serializedName)) {
                return value;
            }
        }
        if (actionClass.isEnum()) {
            for (Object constant : actionClass.getEnumConstants()) {
                if (constant == null) continue;
                String text = String.valueOf(constant);
                if (enumName.equals(text) || serializedName.equalsIgnoreCase(text)
                        || text.toLowerCase(java.util.Locale.ROOT).contains(serializedName)) {
                    return constant;
                }
            }
        }
        return null;
    }

    private static boolean addChatText(Object text) {
        try {
            Object client = FabricServices.minecraftClient();
            Object chatHud = MinecraftClientUiBridge.chatHud(client);
            if (chatHud == null) return false;
            for (Method method : chatHud.getClass().getMethods()) {
                if ((!method.getName().equals("addMessage")
                        && !method.getName().equals("addClientSystemMessage")
                        && !method.getName().equals("method_1812"))
                        || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0].isInstance(text)) {
                    continue;
                }
                method.invoke(chatHud, text);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to display GtbStructures Fabric guess-peek chat", e);
        }
        return false;
    }

    private static void registerFabricGameMessageEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents$Game");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents")
                    .getField("GAME").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onReceiveGameMessage") && args != null && args.length >= 2) {
                            if (!GtbStructuresSettings.INSTANCE.modEnabled) return null;
                            String text = componentString(args[0]);
                            boolean overlay = args[1] instanceof Boolean && ((Boolean) args[1]).booleanValue();
                            if (overlay) ModernRuntimeBridge.onActionbar(text);
                            else ModernRuntimeBridge.onChat(formattedComponentString(args[0]), text);
                            return null;
                        }
                        if (method.getName().equals("toString")) return "GtbStructures game-message callback";
                        return null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric game-message events", e);
        }
    }

    private static void registerFabricModifyGameMessageEvents() {
        try {
            Class<?> callbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents$ModifyGame");
            Object event = Class.forName(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents")
                    .getField("MODIFY_GAME").get(null);
            Object callback = Proxy.newProxyInstance(
                    GtbStructuresFabric.class.getClassLoader(),
                    new Class<?>[] {callbackClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("modifyReceivedGameMessage")
                                && args != null && args.length >= 2) {
                            boolean overlay = args[1] instanceof Boolean && ((Boolean) args[1]).booleanValue();
                            if (overlay) return args[0];
                            Object decorated = guessPeekDecoratedMessage(args[0]);
                            return decorated == null ? args[0] : decorated;
                        }
                        if (method.getName().equals("toString")) {
                            return "GtbStructures game-message guess-peek modifier";
                        }
                        return args != null && args.length >= 1 ? args[0] : null;
                    });
            registerEvent(event, callback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Unable to register GtbStructures Fabric game-message guess-peek modifier", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerCommands(Object rawDispatcher) {
        CommandDispatcher dispatcher = (CommandDispatcher) rawDispatcher;
        for (String root : CommandGtbRuntime.rootCommands()) {
            dispatcher.register(commandRoot(root));
        }
    }

    static boolean ensureCommandsRegistered(Object rawDispatcher) {
        if (rawDispatcher == null) return false;
        CommandDispatcher<?> dispatcher = (CommandDispatcher<?>) rawDispatcher;
        if (hasAllCommandRoots(dispatcher)) return false;
        registerCommands(rawDispatcher);
        return true;
    }

    static boolean hasAllCommandRoots(CommandDispatcher<?> dispatcher) {
        if (dispatcher == null) return false;
        for (String root : CommandGtbRuntime.rootCommands()) {
            if (dispatcher.getRoot().getChild(root) == null) return false;
        }
        return true;
    }

    private static LiteralArgumentBuilder<Object> commandRoot(String root) {
        return LiteralArgumentBuilder.<Object>literal(root)
                .executes(context -> execute(context, root, ""))
                .then(RequiredArgumentBuilder.<Object, String>argument("args", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggest(root, builder))
                        .executes(context -> execute(context, root, StringArgumentType.getString(context, "args"))));
    }

    private static int execute(CommandContext<Object> context, String root, String rawArgs) {
        CommandGtbRuntime.Result result = CommandGtbRuntime.execute(root, rawArgs);
        for (String message : result.messages()) {
            sendFeedback(context.getSource(), message);
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> suggest(String root, SuggestionsBuilder builder) {
        String rawArgs = rawArgs(builder.getInput());
        SuggestionsBuilder argumentBuilder = builder.createOffset(builder.getInput().length() - rawArgs.length());
        for (String suggestion : CommandGtbRuntime.complete(root, rawArgs)) {
            argumentBuilder.suggest(suggestion);
        }
        return argumentBuilder.buildFuture();
    }

    private static String rawArgs(String input) {
        int firstSpace = input.indexOf(' ');
        return firstSpace < 0 ? "" : input.substring(firstSpace + 1);
    }

    private static boolean dispatchLocalCommand(String rawCommand) {
        ParsedCommand parsed = parseLocalCommand(rawCommand);
        if (parsed == null) return false;

        CommandGtbRuntime.Result result = CommandGtbRuntime.execute(parsed.root, parsed.rawArgs);
        for (String message : result.messages()) {
            sendClientFeedback(message);
        }
        return true;
    }

    private static ParsedCommand parseLocalCommand(String rawCommand) {
        if (rawCommand == null) return null;
        String command = rawCommand.trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return null;

        int firstSpace = command.indexOf(' ');
        String root = firstSpace < 0 ? command : command.substring(0, firstSpace);
        if (!CommandGtbRuntime.isRootCommand(root)) return null;
        String rawArgs = firstSpace < 0 ? "" : command.substring(firstSpace + 1);
        return new ParsedCommand(root, rawArgs);
    }

    private static boolean tryCompleteChatName(Object screen) {
        if (!GtbStructuresSettings.INSTANCE.modEnabled) {
            chatNameCompletion.clear();
            return false;
        }
        if (!isCompletionState(GameStats.INSTANCE.getState())) {
            chatNameCompletion.clear();
            return false;
        }
        try {
            Object input = FabricServices.fieldAny(screen, "input", "chatField", "field_2382");
            if (input == null) return false;
            String text = stringValue(FabricServices.invokeAny(input,
                    "getValue", "getText", "method_1882"));
            Integer cursor = intValue(FabricServices.invokeAny(input,
                    "getCursorPosition", "getCursor", "method_1881"));
            int safeCursor = cursor == null ? text.length() : cursor.intValue();
            ChatNameCompletion.Completion completion = chatNameCompletion.complete(
                    screen, text, safeCursor, GameStats.INSTANCE.getPlayers(), true);
            if (completion == null) return false;
            if (!FabricServices.invokeStringAny(input, completion.apply(text),
                    "setValue", "setText", "method_1852")) {
                return false;
            }
            invokeIntAny(input, completion.newCursor(),
                    "setCursorPosition", "setCursor", "method_1883");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            chatNameCompletion.clear();
            LOGGER.warn("Unable to complete GTBS chat name", e);
            return false;
        }
    }

    private static boolean tryDispatchChatLocalCommand(Object screen) {
        if (!isChatScreen(screen)) return false;
        try {
            Object input = FabricServices.fieldAny(screen, "input", "chatField", "field_2382");
            if (input == null) return false;
            String text = stringValue(FabricServices.invokeAny(input, "getValue", "getText", "method_1882"));
            if (!shouldInterceptLocalCommandInput(text)) return false;
            if (!dispatchLocalCommand(text)) return false;
            FabricServices.invokeStringAny(input, "", "setValue", "setText", "method_1852");
            closeChatScreen();
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to dispatch GtbStructures Fabric local chat command", e);
            return false;
        }
    }

    static boolean shouldInterceptLocalCommandInput(String text) {
        String command = text == null ? "" : text.trim();
        return command.startsWith("/") && parseLocalCommand(command) != null;
    }

    static boolean isEnterKey(int keyCode) {
        return keyCode == 257 || keyCode == 335;
    }

    private static void closeChatScreen() {
        try {
            if (!MinecraftClientUiBridge.setScreen(FabricServices.minecraftClient(), null)) {
                LOGGER.warn("Unable to close GtbStructures Fabric chat command screen: screen setter was not found");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to close GtbStructures Fabric chat command screen", e);
        }
    }

    private static boolean isChatScreen(Object screen) {
        if (screen == null) return false;
        String name = screen.getClass().getName();
        return name.endsWith(".ChatScreen")
                || name.equals("net.minecraft.class_408")
                || name.endsWith(".class_408");
    }

    private static boolean isCompletionState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.LOBBY
                || state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END
                || state == GameStats.TrackingState.POST_GAME;
    }

    private static void sendClientFeedback(String message) {
        StatsArchive.ThemeExportPrompt prompt =
                StatsArchive.parseThemeExportPrompt(message);
        try {
            Object client = FabricServices.minecraftClient();
            Object chatHud = MinecraftClientUiBridge.chatHud(client);
            if (prompt != null) {
                Object component = buildThemeExportPrompt(prompt);
                if (component != null
                        && invokeTextObjectAny(chatHud, component,
                        "addMessage", "addClientSystemMessage", "method_1812")) {
                    return;
                }
                message = prompt.visibleText();
            }
            invokeTextAny(chatHud, message, "addMessage", "addClientSystemMessage", "method_1812");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to send GtbStructures Fabric local command feedback", e);
        }
    }

    private static Object buildThemeExportPrompt(
            StatsArchive.ThemeExportPrompt prompt)
            throws ReflectiveOperationException {
        Class<?> textClass = classAny("net.minecraft.text.Text",
                "net.minecraft.network.chat.Component", "net.minecraft.class_2561");
        Object root = legacyFormattedText(textClass, prompt.prefix());
        Object link = legacyFormattedText(textClass,
                "\u00a7f\u00a7n" + prompt.link());
        Object hover = createHoverEvent(prompt.hover());
        Object click = createClickEvent(prompt.command());
        if (hover == null || click == null
                || !applyGuessPeekStyle(link, hover, click)
                || !appendText(root, link, textClass)
                || !appendText(root,
                legacyFormattedText(textClass, prompt.suffix()), textClass)) {
            return null;
        }
        return root;
    }

    static void registerEventForTest(Object event, Object callback) throws ReflectiveOperationException {
        registerEvent(event, callback);
    }

    private static void registerEvent(Object event, Object callback) throws ReflectiveOperationException {
        Method register = event.getClass().getMethod("register", Object.class);
        register.setAccessible(true);
        register.invoke(event, callback);
    }

    private static void ensureFabricClientCommandsRegistered() {
        try {
            Class<?> manager = classAny(
                    "net.fabricmc.fabric.api.client.command.v2.ClientCommandManager",
                    "net.fabricmc.fabric.api.client.command.v2.ClientCommands");
            Object dispatcher = manager.getMethod("getActiveDispatcher").invoke(null);
            boolean changed = ensureFabricClientCommandsRegistered(dispatcher,
                    () -> refreshFabricCommandCompletions(manager));
            if (changed && !commandRefreshLogged) {
                commandRefreshLogged = true;
                LOGGER.info("Restored GtbStructures Fabric client command roots in the active dispatcher");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!commandRefreshLogged) {
                commandRefreshLogged = true;
                LOGGER.warn("Unable to verify GtbStructures Fabric client command roots", e);
            }
        }
    }

    static boolean ensureFabricClientCommandsRegistered(Object dispatcher, CommandCompletionRefresh refresh)
            throws ReflectiveOperationException {
        if (dispatcher == null) return false;
        boolean registered = ensureCommandsRegistered(dispatcher);
        if (!registered && fabricCommandCompletionsRefreshed) return false;
        if (refresh != null && !refresh.refresh()) return registered;
        fabricCommandCompletionsRefreshed = true;
        return true;
    }

    static void resetCommandRefreshForTest() {
        commandRefreshLogged = false;
        fabricCommandCompletionsRefreshed = false;
    }

    private static boolean refreshFabricCommandCompletions(Class<?> manager)
            throws ReflectiveOperationException {
        try {
            manager.getMethod("refreshCommandCompletions").invoke(null);
            return true;
        } catch (NoSuchMethodException ignored) {
            // Older Fabric command API builds do not expose a manual refresh hook.
            return true;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException) {
                return false;
            }
            throw e;
        }
    }

    interface CommandCompletionRefresh {
        boolean refresh() throws ReflectiveOperationException;
    }

    private static Object invokeStaticEventWithArg(Class<?> type, String name, Object arg)
            throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            Class<?> parameter = method.getParameterTypes()[0];
            if (arg == null || parameter.isInstance(arg)) {
                return method.invoke(null, arg);
            }
        }
        return null;
    }

    private static void pollFabricScoreboard(Object client) {
        try {
            Object level = FabricServices.fieldAny(client, "level", "world", "field_1687");
            if (level == null) {
                ModernRuntimeBridge.onScoreboard(null, null);
                return;
            }
            Object scoreboard = FabricServices.invokeAny(level, "getScoreboard", "method_8428");
            if (scoreboard == null) return;

            Object objective = sidebarObjective(scoreboard);
            if (objective == null) {
                ModernRuntimeBridge.onScoreboard(null, null);
                return;
            }

            String title = formattedComponentString(invokeAnyOrNull(objective,
                    "getDisplayName", "getFormattedDisplayName", "method_1114", "method_1111"));
            ModernRuntimeBridge.onScoreboard(title, scoreboardLines(scoreboard, objective));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to poll GtbStructures Fabric scoreboard", e);
        }
    }

    private static void pollFabricThemeSelectionScreen(Object client) {
        try {
            Object screen = fieldAnyDeep(client, "screen", "currentScreen", "field_1755");
            if (screen == null) {
                lastThemeSelectionScreen = null;
                pendingThemeSelectionScreen = null;
                pendingThemeSelectionTitle = null;
                lastNonThemeSelectionScreen = null;
                return;
            }
            captureThemeSelectionScreen(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to inspect GtbStructures Fabric theme selection screen", e);
        }
    }

    private static void pollFabricFontSettingsScreen(Object client) {
        try {
            Object screen = fieldAnyDeep(client, "screen", "currentScreen", "field_1755");
            if (screen == null) {
                lastClassicFontSettingsPollScreen = null;
                lastClassicFontSettingsFailureScreen = null;
                return;
            }
            if (screen == lastClassicFontSettingsPollScreen) return;
            lastClassicFontSettingsPollScreen = screen;
            ClassicFontLanguageScreenButton.install(screen, client,
                    runtimeServices == null ? null : runtimeServices.gameDir());
            lastClassicFontSettingsFailureScreen = null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (lastClassicFontSettingsFailureScreen != lastClassicFontSettingsPollScreen) {
                lastClassicFontSettingsFailureScreen = lastClassicFontSettingsPollScreen;
                LOGGER.warn("Unable to poll GtbStructures Fabric font settings screen", e);
            }
        }
    }

    private static void captureThemeSelectionScreen(Object screen)
            throws ReflectiveOperationException {
        if (screen == null || screen == lastThemeSelectionScreen
                || screen == lastNonThemeSelectionScreen) return;
        if (pendingThemeSelectionScreen != screen) {
            pendingThemeSelectionScreen = null;
            pendingThemeSelectionTitle = null;
        }
        String title = pendingThemeSelectionScreen == screen
                ? pendingThemeSelectionTitle : null;
        if (title == null) {
            title = screenTitle(screen);
            if (!GameStats.INSTANCE.isThemeSelectionGui(ModernRuntimeBridge.cleanText(title))) {
                lastNonThemeSelectionScreen = screen;
                return;
            }
            pendingThemeSelectionScreen = screen;
            pendingThemeSelectionTitle = title;
        }
        String[] themes = themeOptions(screen);
        String localName = runtimeServices == null ? null : runtimeServices.localPlayerName();
        if (ModernRuntimeBridge.onThemeSelectionScreen(title, themes, localName)) {
            lastThemeSelectionScreen = screen;
            pendingThemeSelectionScreen = null;
            pendingThemeSelectionTitle = null;
        }
    }

    static void resetThemeScreenInspectionForTest() {
        lastThemeSelectionScreen = null;
        pendingThemeSelectionScreen = null;
        pendingThemeSelectionTitle = null;
        lastNonThemeSelectionScreen = null;
    }

    static void captureThemeSelectionScreenForTest(Object screen)
            throws ReflectiveOperationException {
        captureThemeSelectionScreen(screen);
    }

    private static String screenTitle(Object screen) throws ReflectiveOperationException {
        String title = componentString(invokeAnyOrNull(screen,
                "getTitle", "getNarratedTitle", "method_25440"));
        if (!title.isEmpty()) return title;
        return componentString(fieldAnyDeep(screen, "title", "field_22785"));
    }

    private static String[] themeOptions(Object screen) throws ReflectiveOperationException {
        Object menu = invokeAnyOrNull(screen, "getScreenHandler", "getMenu", "method_17577");
        if (menu == null) {
            menu = fieldAnyDeep(screen, "screenHandler", "handler", "menu", "field_2792");
        }
        String[] themes = new String[3];
        int[] slots = {11, 13, 15};
        for (int i = 0; i < slots.length; i++) {
            themes[i] = slotItemName(menu, slots[i]);
        }
        return themes;
    }

    private static String slotItemName(Object menu, int index) throws ReflectiveOperationException {
        if (menu == null) return null;
        Object slot = invokeAnyWithInt(menu, index, "getSlot", "method_7611");
        if (slot == null) {
            Object rawSlots = fieldAnyDeep(menu, "slots", "field_7761");
            if (rawSlots instanceof List && index >= 0 && index < ((List<?>) rawSlots).size()) {
                slot = ((List<?>) rawSlots).get(index);
            }
        }
        if (slot == null) return null;

        Object stack = invokeAnyOrNull(slot, "getItem", "getStack", "method_7677");
        if (stack == null) stack = fieldAnyDeep(slot, "item", "stack", "field_7871");
        return itemStackName(stack);
    }

    private static String itemStackName(Object stack) throws ReflectiveOperationException {
        if (stack == null) return null;
        Object empty = invokeAnyOrNull(stack, "isEmpty", "method_7960");
        if (empty instanceof Boolean && ((Boolean) empty).booleanValue()) return null;
        String name = componentString(invokeAnyOrNull(stack,
                "getHoverName", "getName", "getDisplayName", "method_7964"));
        name = ModernRuntimeBridge.cleanText(name);
        return name.isEmpty() ? null : name;
    }

    private static void pollFabricPlayerList(Object client) {
        try {
            Object player = FabricServices.fieldAny(client, "player", "field_1724");
            Object level = FabricServices.fieldAny(client, "level", "world", "field_1687");
            if (player == null || level == null) {
                RawPacketRecorder.INSTANCE.captureOverlayDecision(
                        "fabric_player_list_poll", "missing_client", "player_or_level_null");
                return;
            }

            Object networkHandler = playerListConnection(client, player);
            if (networkHandler == null) return;

            Collection<?> entries = playerListEntries(networkHandler);
            if (entries == null) return;

            Object scoreboard = FabricServices.invokeAny(level, "getScoreboard", "method_8428");
            applyFabricRosterObjectiveGate(scoreboard);
            ModernPlayerListLogic.Snapshot snapshot = new ModernPlayerListLogic.Snapshot();
            Set<String> observedTeams = new LinkedHashSet<String>();
            boolean repairBbRanks = GameStats.INSTANCE.shouldRepairBbRankDisplay()
                    && !fabricBbRankRepairDisabled;
            for (Object entry : entries) {
                String name = playerEntryName(entry);
                if (name == null || name.isEmpty()) continue;
                snapshot.addPlayer(name, playerEntryDisplay(entry));
                Object team = playerEntryTeam(entry);
                if (team == null && scoreboard != null) team = teamForPlayer(scoreboard, name);
                addFabricTeam(snapshot, observedTeams, team, name);
                if (repairBbRanks) {
                    try {
                        repairFabricBbRankTeam(team, name);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        repairBbRanks = false;
                        fabricBbRankRepairDisabled = true;
                        LOGGER.warn("Disabling Fabric BB-rank display repair after a runtime mapping failure", e);
                    }
                }
            }
            addFabricScoreboardTeams(snapshot, observedTeams, scoreboard);
            ModernPlayerListLogic.applySnapshot(snapshot, playerListRoster);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to poll GtbStructures Fabric player list", e);
        }
    }

    private static Object playerListConnection(Object client, Object player)
            throws ReflectiveOperationException {
        Object connection = FabricServices.fieldAny(player,
                "networkHandler", "connection", "field_3944");
        if (connection == null) {
            connection = invokeAnyOrNull(player,
                    "getNetworkHandler", "getConnection", "method_1562");
        }
        if (connection == null) {
            connection = FabricServices.invokeAny(client,
                    "getNetworkHandler", "getConnection", "method_1562");
        }
        return connection;
    }

    private static void applyFabricRosterObjectiveGate(Object scoreboard)
            throws ReflectiveOperationException {
        if (scoreboard == null) return;
        Object objective = sidebarObjective(scoreboard);
        if (objective == null) return;
        String title = formattedComponentString(invokeAnyOrNull(objective,
                "getDisplayName", "getFormattedDisplayName", "method_1114", "method_1111"));
        GameStats.INSTANCE.onRosterObjectiveTitle(title);
    }

    private static Collection<?> playerListEntries(Object networkHandler)
            throws ReflectiveOperationException {
        Object direct = invokeAnyOrNull(networkHandler,
                "getListedPlayerListEntries", "method_45732",
                "getPlayerList", "method_2880",
                "getListedOnlinePlayers", "getOnlinePlayers");
        if (direct instanceof Collection) return (Collection<?>) direct;

        for (Method method : networkHandler.getClass().getMethods()) {
            if (method.getParameterCount() != 0
                    || !Collection.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String name = method.getName().toLowerCase();
            if (!name.contains("player") && !name.contains("listed")) continue;
            Object value = method.invoke(networkHandler);
            if (value instanceof Collection) return (Collection<?>) value;
        }
        return null;
    }

    private static String playerEntryName(Object entry) throws ReflectiveOperationException {
        Object profile = invokeAnyOrNull(entry, "getProfile", "profile", "method_2966");
        if (profile == null) profile = FabricServices.fieldAny(entry, "profile", "field_3701");
        String name = profileName(profile);
        if (name != null && !name.isEmpty()) return name;
        Object direct = invokeAnyOrNull(entry, "getName", "name");
        return direct instanceof String ? (String) direct : null;
    }

    private static String profileName(Object profile) throws ReflectiveOperationException {
        if (profile == null) return null;
        Object name = FabricServices.invokeAny(profile, "name", "getName");
        return name instanceof String ? (String) name : null;
    }

    private static String playerEntryDisplay(Object entry) throws ReflectiveOperationException {
        String text = formattedComponentString(invokeAnyOrNull(entry,
                "getDisplayName", "getTabListDisplayName", "method_2971", "method_2967"));
        return text.isEmpty() ? null : text;
    }

    private static Object playerEntryTeam(Object entry) throws ReflectiveOperationException {
        return invokeAnyOrNull(entry, "getScoreboardTeam", "getTeam", "method_2955");
    }

    private static Object teamForPlayer(Object scoreboard, String name)
            throws ReflectiveOperationException {
        Object direct = invokeAnyWithArg(scoreboard, name,
                "getPlayersTeam", "getPlayerTeam", "getScoreHolderTeam", "method_1164", "method_1182");
        return direct != null ? direct : scanScoreboardTeamsForPlayer(scoreboard, name);
    }

    private static Object scanScoreboardTeamsForPlayer(Object scoreboard, String name)
            throws ReflectiveOperationException {
        if (scoreboard == null || name == null || name.isEmpty()) return null;
        for (Method method : scoreboard.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType().equals(Void.TYPE)) continue;
            Class<?> returnType = method.getReturnType();
            if (!Collection.class.isAssignableFrom(returnType)
                    && !Map.class.isAssignableFrom(returnType)
                    && !Iterable.class.isAssignableFrom(returnType)
                    && !Optional.class.isAssignableFrom(returnType)
                    && !returnType.isArray()) {
                continue;
            }
            try {
                Object team = teamContainerMember(method.invoke(scoreboard), name);
                if (team != null) return team;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next scoreboard team accessor.
            }
        }
        return null;
    }

    private static Object teamContainerMember(Object raw, String name)
            throws ReflectiveOperationException {
        if (raw == null) return null;
        if (raw instanceof Optional) {
            Optional<?> optional = (Optional<?>) raw;
            return optional.isPresent() ? teamContainerMember(optional.get(), name) : null;
        }
        if (raw instanceof Map) {
            for (Object value : ((Map<?, ?>) raw).values()) {
                Object team = teamContainerMember(value, name);
                if (team != null) return team;
            }
            return null;
        }
        if (raw instanceof Iterable) {
            for (Object value : (Iterable<?>) raw) {
                Object team = teamContainerMember(value, name);
                if (team != null) return team;
            }
            return null;
        }
        Class<?> type = raw.getClass();
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object team = teamContainerMember(java.lang.reflect.Array.get(raw, i), name);
                if (team != null) return team;
            }
            return null;
        }
        return teamContainsMember(raw, name) ? raw : null;
    }

    private static boolean teamContainsMember(Object team, String name) {
        try {
            for (String member : teamMembers(team)) {
                if (member != null && member.equals(name)) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static void addFabricTeam(ModernPlayerListLogic.Snapshot snapshot,
                                      Set<String> observedTeams,
                                      Object team,
                                      String knownMember) throws ReflectiveOperationException {
        if (team == null) return;
        String teamName = stringValue(invokeAnyOrNull(team, "getName", "method_1197"));
        if (teamName.isEmpty()) teamName = stringValue(fieldAnyDeep(team, "name", "field_1421"));
        if (teamName.isEmpty()) return;
        List<String> members = new ArrayList<String>(teamMembers(team));
        if (members.isEmpty() && isValidMemberName(knownMember)) {
            members.add(knownMember);
        }
        if (!observedTeams.add(teamName)) {
            if (!members.isEmpty()) {
                snapshot.addTeam(teamName, "", "", members, 3);
            }
            return;
        }
        snapshot.addTeam(teamName,
                formattedComponentString(invokeAnyOrNull(team, "getPlayerPrefix", "getPrefix", "method_1144")),
                formattedComponentString(invokeAnyOrNull(team, "getPlayerSuffix", "getSuffix", "method_1136", "method_1142")),
                members, 0);
    }

    private static void addFabricScoreboardTeams(ModernPlayerListLogic.Snapshot snapshot,
                                                 Set<String> observedTeams,
                                                 Object scoreboard)
            throws ReflectiveOperationException {
        if (scoreboard == null) return;
        Object raw = invokeAnyOrNull(scoreboard,
                "getPlayerTeams", "getTeams", "method_1159");
        if (raw instanceof Map) raw = ((Map<?, ?>) raw).values();
        if (raw instanceof Optional) {
            Optional<?> optional = (Optional<?>) raw;
            raw = optional.isPresent() ? optional.get() : null;
        }
        if (raw instanceof Iterable) {
            for (Object team : (Iterable<?>) raw) {
                addFabricTeam(snapshot, observedTeams, team, null);
            }
            return;
        }
        if (raw != null && raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                addFabricTeam(snapshot, observedTeams,
                        java.lang.reflect.Array.get(raw, i), null);
            }
        }
    }

    private static void repairFabricBbRankTeam(Object team, String ign)
            throws ReflectiveOperationException {
        if (team == null) return;
        String teamName = stringValue(invokeAnyOrNull(team, "getName", "method_1197"));
        if (teamName.isEmpty()) teamName = stringValue(fieldAnyDeep(team, "name", "field_1421"));
        if (teamName.isEmpty()) return;
        bbRankDisplayRepair.repairExistingTeam(new FabricTeamPrefixTarget(team, teamName), ign,
                GameStats.INSTANCE.getCurrentLangCode());
    }

    /** Repairs a team immediately after the vanilla packet handler applies it. */
    public static void onFabricPlayerTeamPacketApplied(Object listener, Object packet) {
        if (listener == null || packet == null) return;
        try {
            String teamName = stringValue(invokeAnyOrNull(packet, "getName", "method_34177"));
            if (teamName.isEmpty()) return;
            Object level = invokeAnyOrNull(listener, "getLevel", "method_2890");
            if (level == null) return;
            Object scoreboard = invokeAnyOrNull(level, "getScoreboard", "method_8428");
            if (scoreboard == null) return;
            Object team = invokeAnyWithArg(scoreboard, teamName,
                    "getPlayerTeam", "method_1153");
            if (team == null) {
                bbRankDisplayRepair.discardTeam(teamName);
                return;
            }
            if (!GtbStructuresSettings.INSTANCE.modEnabled) {
                bbRankDisplayRepair.restoreAll();
                return;
            }
            if (fabricBbRankRepairDisabled) return;
            bbRankDisplayRepair.repairEncodedTeamColor(
                    new FabricTeamPrefixTarget(team, teamName));
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!fabricBbRankRepairDisabled) {
                LOGGER.warn("Disabling immediate Fabric BB-rank team repair after a runtime mapping failure", e);
            }
            fabricBbRankRepairDisabled = true;
        }
    }

    private static Collection<String> teamMembers(Object team) throws ReflectiveOperationException {
        Object raw = invokeAnyOrNull(team, "getPlayers", "getPlayerList", "getPlayerNames", "method_1125");
        if (raw == null) raw = fieldAnyDeep(team, "playerList", "field_1415");
        List<String> members = new ArrayList<String>();
        if (raw instanceof Collection) {
            for (Object value : (Collection<?>) raw) {
                if (value != null) members.add(value.toString());
            }
        }
        return members;
    }

    private static boolean isValidMemberName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static void pollFabricPhysicalTimer(Object client) {
        try {
            Object player = FabricServices.fieldAny(client, "player", "field_1724");
            Integer level = intFieldAny(player, "experienceLevel", "field_7495");
            if (level != null) ModernPlayerListLogic.observePhysicalTimer(level.intValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to poll GtbStructures Fabric physical timer", e);
        }
    }

    private static Integer intFieldAny(Object target, String... names)
            throws ReflectiveOperationException {
        Object value = FabricServices.fieldAny(target, names);
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    private static int keyCode(Object keyEvent) throws ReflectiveOperationException {
        Object value = FabricServices.invokeAny(keyEvent, "getKeyCode", "keyCode", "f_412394_");
        Integer key = intValue(value);
        return key == null ? -1 : key.intValue();
    }

    private static boolean invokeIntAny(Object target, int value, String... names)
            throws ReflectiveOperationException {
        if (target == null) return false;
        for (String name : names) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && parameters[0].equals(Integer.TYPE)) {
                    method.invoke(target, Integer.valueOf(value));
                    return true;
                }
                if (parameters.length == 2
                        && parameters[0].equals(Integer.TYPE)
                        && parameters[1].equals(Boolean.TYPE)) {
                    method.invoke(target, Integer.valueOf(value), Boolean.FALSE);
                    return true;
                }
            }
        }
        return false;
    }

    private static Object sidebarObjective(Object scoreboard) throws ReflectiveOperationException {
        for (Method method : scoreboard.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || method.getReturnType().equals(Void.TYPE)) continue;
            Object slot = sidebarSlot(method.getParameterTypes()[0]);
            if (slot == null) continue;
            Object objective = method.invoke(scoreboard, slot);
            if (objective != null) return objective;
        }
        return null;
    }

    private static Object sidebarSlot(Class<?> type) throws ReflectiveOperationException {
        if (!type.isEnum()) return null;
        Object[] constants = type.getEnumConstants();
        if (constants == null) return null;
        for (Object constant : constants) {
            String name = constant instanceof Enum ? ((Enum<?>) constant).name() : String.valueOf(constant);
            if ("SIDEBAR".equalsIgnoreCase(name)) return constant;
            Object serialized = invokeAnyOrNull(constant,
                    "asString", "getName", "getSerializedName", "method_15434");
            if (serialized != null && serialized.toString().toLowerCase().contains("sidebar")) {
                return constant;
            }
        }
        return null;
    }

    private static List<String> scoreboardLines(Object scoreboard, Object objective)
            throws ReflectiveOperationException {
        List<String> lines = new ArrayList<String>();
        Object rawEntries = invokeAnyWithArg(scoreboard, objective,
                "getScoreboardEntries", "method_1184",
                "listPlayerScores", "getAllPlayerScores", "method_55499");
        collectScoreboardLines(lines, rawEntries, scoreboard);
        if (!lines.isEmpty()) return lines;

        for (Method method : scoreboard.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || method.getReturnType().equals(Void.TYPE)) continue;
            Class<?> parameter = method.getParameterTypes()[0];
            if (objective != null && !parameter.isInstance(objective)) continue;
            try {
                List<String> candidate = new ArrayList<String>();
                collectScoreboardLines(candidate, method.invoke(scoreboard, objective), scoreboard);
                if (!candidate.isEmpty()) return candidate;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next sidebar-entry accessor.
            }
        }
        return lines;
    }

    private static void collectScoreboardLines(List<String> lines, Object raw, Object scoreboard)
            throws ReflectiveOperationException {
        if (lines == null || raw == null) return;
        List<ScoreboardEntryRef> entries = new ArrayList<ScoreboardEntryRef>();
        collectScoreboardEntries(entries, raw);
        boolean hasOrder = false;
        for (ScoreboardEntryRef entry : entries) {
            if (entry.orderValue != null) {
                hasOrder = true;
                break;
            }
        }
        if (hasOrder) {
            Collections.sort(entries, new Comparator<ScoreboardEntryRef>() {
                @Override
                public int compare(ScoreboardEntryRef a, ScoreboardEntryRef b) {
                    if (a.orderValue != null && b.orderValue != null) {
                        int byScore = a.orderValue.compareTo(b.orderValue);
                        if (byScore != 0) return byScore;
                    }
                    return Integer.compare(a.index, b.index);
                }
            });
        }
        for (ScoreboardEntryRef entry : entries) {
            addScoreboardLine(lines, scoreboardLineFromEntry(entry.entry, scoreboard));
        }
    }

    private static void collectScoreboardEntries(List<ScoreboardEntryRef> entries, Object raw)
            throws ReflectiveOperationException {
        if (entries == null || raw == null) return;
        if (raw instanceof Optional) {
            Optional<?> optional = (Optional<?>) raw;
            if (!optional.isPresent()) return;
            raw = optional.get();
        }
        if (raw instanceof Map) {
            for (Object entry : ((Map<?, ?>) raw).entrySet()) {
                entries.add(scoreboardEntryRef(entry, entries.size()));
            }
            return;
        }
        if (raw instanceof Iterable) {
            for (Object entry : (Iterable<?>) raw) {
                entries.add(scoreboardEntryRef(entry, entries.size()));
            }
            return;
        }
        if (raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                entries.add(scoreboardEntryRef(java.lang.reflect.Array.get(raw, i), entries.size()));
            }
            return;
        }
        entries.add(scoreboardEntryRef(raw, entries.size()));
    }

    private static ScoreboardEntryRef scoreboardEntryRef(Object entry, int index)
            throws ReflectiveOperationException {
        return new ScoreboardEntryRef(entry, index, scoreboardEntryOrderValue(entry));
    }

    private static Integer scoreboardEntryOrderValue(Object entry) throws ReflectiveOperationException {
        if (entry == null) return null;
        if (entry instanceof Map.Entry) {
            Map.Entry<?, ?> mapEntry = (Map.Entry<?, ?>) entry;
            Integer valueOrder = scoreboardEntryOrderValue(mapEntry.getValue());
            return valueOrder != null ? valueOrder : scoreboardEntryOrderValue(mapEntry.getKey());
        }
        if (entry instanceof Number) return Integer.valueOf(((Number) entry).intValue());
        Integer methodValue = invokeNumberAnyOrNull(entry,
                "value", "getValue", "score", "getScore",
                "comp_2128", "d", "method_55465", "b");
        if (methodValue != null) return methodValue;
        return intValue(fieldAnyDeep(entry, "value", "score", "comp_2128", "b"));
    }

    static Integer scoreboardEntryOrderValueForTest(Object entry) throws ReflectiveOperationException {
        return scoreboardEntryOrderValue(entry);
    }

    private static Integer invokeNumberAnyOrNull(Object target, String... names)
            throws ReflectiveOperationException {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                Object value = method.invoke(target);
                if (value instanceof Number) {
                    return Integer.valueOf(((Number) value).intValue());
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static final class ScoreboardEntryRef {
        final Object entry;
        final int index;
        final Integer orderValue;

        ScoreboardEntryRef(Object entry, int index, Integer orderValue) {
            this.entry = entry;
            this.index = index;
            this.orderValue = orderValue;
        }
    }

    private static String scoreboardLineFromEntry(Object entry, Object scoreboard)
            throws ReflectiveOperationException {
        if (entry == null) return "";
        if (entry instanceof Map.Entry) {
            Map.Entry<?, ?> mapEntry = (Map.Entry<?, ?>) entry;
            String line = scoreboardLineFromEntry(mapEntry.getValue(), scoreboard);
            if (line == null) return null;
            return isVisiblyEmpty(line) ? scoreboardLineFromEntry(mapEntry.getKey(), scoreboard) : line;
        }
        Object hidden = invokeAnyOrNull(entry, "hidden", "isHidden", "method_55385", "method_55462");
        if (hidden instanceof Boolean && ((Boolean) hidden).booleanValue()) return null;

        String owner = scoreboardOwnerName(entry);
        String line = scoreboardLineFromOwner(scoreboard, owner);
        if (!isVisiblyEmpty(line)) return line;

        line = formattedComponentString(invokeAnyOrNull(entry,
                "display", "getDisplay", "displayName", "getDisplayName",
                "comp_2129", "method_55464"));
        if (!isVisiblyEmpty(line)) return line;

        line = componentString(invokeAnyOrNull(entry,
                "ownerName", "getOwnerName", "method_55463"));
        if (!isVisiblyEmpty(line)) return line;

        return line.isEmpty() ? scoreboardText(entry) : line;
    }

    private static String scoreboardOwnerName(Object entry) throws ReflectiveOperationException {
        String owner = scoreboardText(invokeAnyOrNull(entry,
                "owner", "getOwner", "scoreHolder", "getScoreHolder",
                "holder", "comp_2127", "method_55461"));
        if (!owner.isEmpty()) return owner;
        owner = componentString(invokeAnyOrNull(entry,
                "name", "getName", "method_55387"));
        if (!owner.isEmpty()) return owner;
        return componentString(invokeAnyOrNull(entry,
                "ownerName", "getOwnerName", "method_55463"));
    }

    private static String scoreboardLineFromOwner(Object scoreboard, String owner)
            throws ReflectiveOperationException {
        if (owner == null || owner.isEmpty()) return "";
        Object team = teamForPlayer(scoreboard, owner);
        if (team == null) return isHiddenScoreOwner(owner) ? "" : owner;
        return scoreboardLineFromParts(
                formattedComponentString(invokeAnyOrNull(team, "getPlayerPrefix", "getPrefix", "method_1144")),
                owner,
                formattedComponentString(invokeAnyOrNull(team, "getPlayerSuffix", "getSuffix", "method_1136", "method_1142")));
    }

    static String scoreboardLineFromPartsForTest(String prefix, String owner, String suffix) {
        return scoreboardLineFromParts(prefix, owner, suffix);
    }

    private static String scoreboardLineFromParts(String prefix, String owner, String suffix) {
        String visibleOwner = isHiddenScoreOwner(owner) ? "" : owner;
        return (prefix == null ? "" : prefix)
                + (visibleOwner == null ? "" : visibleOwner)
                + (suffix == null ? "" : suffix);
    }

    private static boolean isHiddenScoreOwner(String owner) {
        return owner != null && !owner.isEmpty() && owner.charAt(0) == '\u00a7';
    }

    private static String scoreboardText(Object value) throws ReflectiveOperationException {
        if (value == null) return "";
        if (value instanceof Optional) {
            Optional<?> optional = (Optional<?>) value;
            if (!optional.isPresent()) return "";
            value = optional.get();
        }
        if (value instanceof CharSequence) return value.toString();
        Object direct = invokeAnyOrNull(value,
                "getNameForScoreboard", "getScoreboardName", "ownerName", "getOwnerName",
                "name", "getName", "asString", "getString", "getLiteralString",
                "method_55460", "method_10851");
        String text = componentString(direct);
        if (!text.isEmpty()) return text;
        return componentString(invokeAnyOrNull(value,
                "display", "getDisplay", "displayName", "getDisplayName", "getFormattedDisplayName",
                "method_55464", "method_1114", "method_1111"));
    }

    private static void addScoreboardLine(List<String> lines, String line) {
        if (line == null) return;
        lines.add(isVisiblyEmpty(line) ? "\u00a7r" : line);
    }

    static List<String> scoreboardLinesForTest(String... rawLines) {
        List<String> lines = new ArrayList<String>();
        if (rawLines != null) {
            for (String line : rawLines) addScoreboardLine(lines, line);
        }
        return lines;
    }

    private static boolean isVisiblyEmpty(String line) {
        return line == null || ModernRuntimeBridge.cleanText(line).isEmpty();
    }

    private static Object invokeAnyOrNull(Object target, String... names) throws ReflectiveOperationException {
        return FabricServices.invokeAny(target, names);
    }

    private static Object invokeAnyWithArg(Object target, Object arg, String... names)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyWithArg(target, arg, names);
    }

    private static Object invokeAnyWithInt(Object target, int arg, String... names)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.invokeAnyWithArg(target, Integer.valueOf(arg), names);
    }

    private static Class<?> classAny(String... names) throws ClassNotFoundException {
        return RuntimeReflectionCache.classAny(names);
    }

    private static Object fieldAnyDeep(Object target, String... names)
            throws ReflectiveOperationException {
        return RuntimeReflectionCache.fieldAnyDeep(target, names);
    }

    private static String componentString(Object component) throws ReflectiveOperationException {
        if (component == null) return "";
        if (component instanceof Optional) {
            Optional<?> optional = (Optional<?>) component;
            if (!optional.isPresent()) return "";
            component = optional.get();
        }
        if (component instanceof CharSequence) return component.toString();
        Object value = FabricServices.invokeAny(component, "getString", "method_10851");
        return value instanceof String ? (String) value : component.toString();
    }

    private static String formattedComponentString(Object component) throws ReflectiveOperationException {
        if (component == null) return "";
        if (component instanceof Optional) {
            Optional<?> optional = (Optional<?>) component;
            if (!optional.isPresent()) return "";
            component = optional.get();
        }
        if (component instanceof CharSequence) return component.toString();
        return MinecraftFormatting.legacyFormattedText(component);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Integer intValue(Object value) {
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    private static void sendFeedback(Object source, String message) {
        try {
            Method feedback = findFeedbackMethod(source.getClass());
            Object text = literalText(feedback.getParameterTypes()[0], message);
            feedback.invoke(source, text);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to send GtbStructures command feedback", e);
        }
    }

    private static Method findFeedbackMethod(Class<?> sourceClass) throws NoSuchMethodException {
        for (Method method : sourceClass.getMethods()) {
            if (method.getName().equals("sendFeedback") && method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new NoSuchMethodException("sendFeedback(Text)");
    }

    private static boolean invokeTextAny(Object target, String message, String... names) throws ReflectiveOperationException {
        if (target == null) return false;
        for (String name : names) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                Object text = literalText(method.getParameterTypes()[0], message);
                method.invoke(target, text);
                return true;
            }
        }
        return false;
    }

    private static boolean invokeTextObjectAny(Object target, Object text,
                                               String... names)
            throws ReflectiveOperationException {
        if (target == null || text == null) return false;
        for (String name : names) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name)
                        || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0].isInstance(text)) {
                    continue;
                }
                method.invoke(target, text);
                return true;
            }
        }
        return false;
    }

    private static Object literalText(Class<?> textClass, String message) throws ReflectiveOperationException {
        Method method = findTextFactory(textClass, "literal");
        if (method == null) method = findTextFactory(textClass, "method_43470");
        if (method == null) method = findAnyTextFactory(textClass);
        if (method == null) throw new NoSuchMethodException("Text literal factory");
        try {
            return method.invoke(null, message);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }

    private static Method findTextFactory(Class<?> textClass, String name) {
        try {
            Method method = textClass.getMethod(name, String.class);
            return textClass.isAssignableFrom(method.getReturnType()) ? method : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findAnyTextFactory(Class<?> textClass) {
        for (Method method : textClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].equals(String.class)
                    && textClass.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private static final class FabricTeamPrefixTarget implements BbRankDisplayRepair.TeamPrefixTarget {
        private final Object team;
        private final String teamName;

        FabricTeamPrefixTarget(Object team, String teamName) {
            this.team = team;
            this.teamName = teamName;
        }

        @Override
        public Object identity() {
            return team;
        }

        @Override
        public String teamName() {
            return teamName;
        }

        @Override
        public String prefix() {
            try {
                return formattedComponentString(invokeAnyOrNull(team,
                        "getPlayerPrefix", "getPrefix", "method_1144"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to read Fabric scoreboard team prefix", e);
            }
        }

        @Override
        public void setPrefix(String prefix) {
            try {
                Method method = fabricTeamPrefixSetter(team.getClass());
                method.invoke(team, legacyFormattedText(method.getParameterTypes()[0], prefix));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to set Fabric scoreboard team prefix", e);
            }
        }

        @Override
        public Integer colorIndex() {
            try {
                return fabricTeamColorIndex(invokeAnyOrNull(team, "getColor", "method_1202"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to read Fabric scoreboard team color", e);
            }
        }

        @Override
        public void setColorIndex(Integer colorIndex) {
            try {
                Object currentColor = invokeAnyOrNull(team, "getColor", "method_1202");
                Method method = fabricTeamColorSetter(team.getClass());
                Object value = fabricTeamColorValue(
                        method.getParameterTypes()[0], currentColor, colorIndex);
                method.invoke(team, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to set Fabric scoreboard team color", e);
            }
        }
    }

    private static Method fabricTeamPrefixSetter(Class<?> teamClass) throws NoSuchMethodException {
        Method method = RuntimeReflectionCache.findMethodByArity(teamClass, false,
                new String[] {"setPlayerPrefix", "setPrefix", "method_1138", "method_1130"}, 1);
        if (method != null) return method;
        throw new NoSuchMethodException("PlayerTeam.setPlayerPrefix(Text)");
    }

    static Method fabricTeamPrefixSetterForTest(Class<?> teamClass) throws NoSuchMethodException {
        return fabricTeamPrefixSetter(teamClass);
    }

    private static Method fabricTeamColorSetter(Class<?> teamClass) throws NoSuchMethodException {
        Method method = RuntimeReflectionCache.findMethodByArity(teamClass, false,
                new String[] {"setColor", "method_1141"}, 1);
        if (method != null) return method;
        throw new NoSuchMethodException("PlayerTeam.setColor(Formatting/Optional<TeamColor>)");
    }

    private static Integer fabricTeamColorIndex(Object rawColor) {
        Object color = rawColor;
        if (color instanceof Optional) {
            Optional<?> optional = (Optional<?>) color;
            color = optional.isPresent() ? optional.get() : null;
        }
        if (!(color instanceof Enum)) return null;
        String name = ((Enum<?>) color).name();
        for (int i = 0; i < TEAM_COLOR_NAMES.length; i++) {
            if (TEAM_COLOR_NAMES[i].equals(name)) return Integer.valueOf(i);
        }
        return null;
    }

    private static Object fabricTeamColorValue(Class<?> parameterType, Object currentColor,
                                               Integer colorIndex)
            throws ReflectiveOperationException {
        if (colorIndex != null
                && (colorIndex.intValue() < 0 || colorIndex.intValue() >= TEAM_COLOR_NAMES.length)) {
            throw new IllegalArgumentException("Invalid team color index: " + colorIndex);
        }
        boolean optional = Optional.class.isAssignableFrom(parameterType);
        Class<?> colorClass = optional ? containedEnumClass(currentColor) : parameterType;
        if (colorClass == null) {
            colorClass = classAny("net.minecraft.world.scores.TeamColor");
        }
        Object color = colorIndex == null ? null
                : enumConstant(colorClass, TEAM_COLOR_NAMES[colorIndex.intValue()]);
        if (optional) return Optional.ofNullable(color);
        return color == null ? enumConstant(colorClass, "RESET") : color;
    }

    private static Class<?> containedEnumClass(Object value) {
        Object color = value;
        if (color instanceof Optional) {
            Optional<?> optional = (Optional<?>) color;
            color = optional.isPresent() ? optional.get() : null;
        }
        return color instanceof Enum ? color.getClass() : null;
    }

    private static Object enumConstant(Class<?> enumClass, String name) throws NoSuchFieldException {
        if (enumClass != null && enumClass.isEnum()) {
            Object[] constants = enumClass.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    if (name.equals(((Enum<?>) constant).name())) return constant;
                }
            }
        }
        throw new NoSuchFieldException(String.valueOf(enumClass) + "." + name);
    }

    static Integer fabricTeamColorIndexForTest(Object team) {
        return new FabricTeamPrefixTarget(team, "test").colorIndex();
    }

    static void setFabricTeamColorIndexForTest(Object team, Integer colorIndex) {
        new FabricTeamPrefixTarget(team, "test").setColorIndex(colorIndex);
    }

    private static Object legacyFormattedText(Class<?> textClass, String legacy)
            throws ReflectiveOperationException {
        Object root = literalText(textClass, "");
        List<Character> formats = new ArrayList<Character>();
        int segmentStart = 0;
        for (int i = 0; i < legacy.length(); i++) {
            if (legacy.charAt(i) != '\u00a7' || i + 1 >= legacy.length()) continue;
            appendLegacyTextSegment(root, textClass, legacy.substring(segmentStart, i), formats);
            applyLegacyFormatCode(formats, legacy.charAt(i + 1));
            segmentStart = i + 2;
            i++;
        }
        appendLegacyTextSegment(root, textClass, legacy.substring(segmentStart), formats);
        return root;
    }

    private static void appendLegacyTextSegment(Object root, Class<?> textClass, String value,
                                                List<Character> formats)
            throws ReflectiveOperationException {
        if (value.isEmpty()) return;
        Object segment = literalText(textClass, value);
        if (!formats.isEmpty()) segment = applyTextFormats(segment, formats);
        if (appendText(root, segment, textClass)) return;
        throw new NoSuchMethodException("MutableText.append(Text)");
    }

    private static void applyLegacyFormatCode(List<Character> formats, char rawCode) {
        char code = Character.toLowerCase(rawCode);
        if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
            formats.clear();
        }
        if (code != 'r' && "0123456789abcdefklmno".indexOf(code) >= 0
                && !formats.contains(Character.valueOf(code))) {
            formats.add(Character.valueOf(code));
        }
    }

    private static Object applyTextFormats(Object text, List<Character> formats)
            throws ReflectiveOperationException {
        Class<?> formattingClass = classAny("net.minecraft.util.Formatting",
                "net.minecraft.ChatFormatting", "net.minecraft.class_124");
        Method byCode = null;
        for (Method method : formattingClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].equals(Character.TYPE)
                    && formattingClass.isAssignableFrom(method.getReturnType())) {
                byCode = method;
                break;
            }
        }
        if (byCode == null) throw new NoSuchMethodException("Formatting.byCode(char)");
        Object values = java.lang.reflect.Array.newInstance(formattingClass, formats.size());
        for (int i = 0; i < formats.size(); i++) {
            Object value = byCode.invoke(null, Character.valueOf(formats.get(i).charValue()));
            java.lang.reflect.Array.set(values, i, value);
        }
        Method formattingMethod = textFormattingArrayMethod(text.getClass(), formattingClass);
        if (formattingMethod != null) {
            Object result = formattingMethod.invoke(text, values);
            return result == null ? text : result;
        }
        throw new NoSuchMethodException("MutableText.formatted(Formatting[])");
    }

    private static Method textFormattingArrayMethod(Class<?> textClass, Class<?> formattingClass) {
        Object emptyFormattingArray = java.lang.reflect.Array.newInstance(formattingClass, 0);
        return RuntimeReflectionCache.findCompatibleMethod(textClass, false,
                new String[] {"formatted", "withStyle", "method_27695"}, emptyFormattingArray);
    }

    static Method textFormattingArrayMethodForTest(Class<?> textClass, Class<?> formattingClass) {
        return textFormattingArrayMethod(textClass, formattingClass);
    }

    private static final class ParsedCommand {
        private final String root;
        private final String rawArgs;

        private ParsedCommand(String root, String rawArgs) {
            this.root = root;
            this.rawArgs = rawArgs;
        }
    }

    private static final class FabricServices implements CommandGtbRuntime.Services {
        @Override
        public File gameDir() {
            return FabricLoader.getInstance().getGameDir().toFile();
        }

        @Override
        public File configDir() {
            return FabricLoader.getInstance().getConfigDir().toFile();
        }

        @Override
        public String localPlayerName() {
            try {
                Object player = player();
                if (player != null) {
                    Object profile = invokeAny(player, "getGameProfile", "method_7334");
                    Object name = invokeAny(profile, "name", "getName");
                    if (name instanceof String && !((String) name).isEmpty()) return (String) name;
                }
                return null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        public String accountPlayerName() {
            try {
                Object client = minecraftClient();
                Object session = invokeAny(client, "getSession", "getUser", "method_1548");
                if (session == null) session = fieldAny(client, "session", "user", "field_1690");
                Object name = invokeAny(session, "getUsername", "getName", "method_1676");
                return name instanceof String ? (String) name : null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        public String accountPlayerUuid() {
            try {
                Object client = minecraftClient();
                Object session = invokeAny(client, "getSession", "getUser", "method_1548");
                if (session == null) session = fieldAny(client, "session", "user", "field_1690");
                Object uuid = invokeAny(session, "getProfileId", "getUuidOrNull", "getUuid",
                        "method_44717");
                return PlayerUuid.normalize(uuid);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        @Override
        public void sendServerCommandAfterLobby(String command) {
            queueLobbyRejoin(command);
        }

        @Override
        public void sendServerCommand(String command) {
            try {
                Object player = player();
                if (player == null) return;
                Object networkHandler = fieldAny(player, "networkHandler", "connection", "field_3944");
                if (networkHandler == null) return;
                GameStats.INSTANCE.onOutboundServerCommand(command);
                String networkCommand = withoutLeadingSlash(command);
                boolean sent = invokeStringAny(networkHandler, networkCommand,
                        "sendCommand", "sendChatCommand", "method_45730", "d")
                        || invokeStringAny(networkHandler, withLeadingSlash(command),
                        "sendChatMessage", "method_45729");
                if (!sent) {
                    LOGGER.warn("Unable to send GtbStructures Fabric server command {}", command);
                } else {
                    RawPacketRecorder.INSTANCE.captureOutboundChat(withLeadingSlash(command));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Unable to send GtbStructures Fabric server command {}", command, e);
            }
        }

        private static String withoutLeadingSlash(String command) {
            if (command == null) return "";
            return command.startsWith("/") ? command.substring(1) : command;
        }

        private static String withLeadingSlash(String command) {
            if (command == null || command.isEmpty()) return "/";
            return command.startsWith("/") ? command : "/" + command;
        }

        @Override
        public boolean copyToClipboard(String text) {
            try {
                Object client = minecraftClient();
                Object keyboard = fieldAny(client, "keyboard", "keyboardHandler", "field_1774");
                if (keyboard != null && invokeStringAny(keyboard, text, "setClipboard", "method_1455")) {
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to the platform-neutral clipboard implementation.
            }
            return false;
        }

        private static Object player() throws ReflectiveOperationException {
            Object client = minecraftClient();
            return fieldAny(client, "player", "field_1724");
        }

        private static Object minecraftClient() throws ReflectiveOperationException {
            Class<?> clientClass = RuntimeReflectionCache.classAny(
                    "net.minecraft.client.MinecraftClient",
                    "net.minecraft.client.Minecraft",
                    "net.minecraft.class_310");
            Method method = methodAny(clientClass, "getInstance", "method_1551");
            return method.invoke(null);
        }

        private static Object fieldAny(Object target, String... names) throws ReflectiveOperationException {
            return RuntimeReflectionCache.fieldAny(target, names);
        }

        private static Object invokeAny(Object target, String... names) throws ReflectiveOperationException {
            return RuntimeReflectionCache.invokeAny(target, names);
        }

        private static Method methodAny(Class<?> type, String... names) throws ReflectiveOperationException {
            return RuntimeReflectionCache.methodAny(type, names);
        }

        private static boolean invokeStringAny(Object target, String value, String... names)
                throws ReflectiveOperationException {
            if (target == null) return false;
            Method method = RuntimeReflectionCache.findCompatibleMethod(target.getClass(), false,
                    names, value);
            if (method == null) return false;
            method.invoke(target, value);
            return true;
        }
    }
}
