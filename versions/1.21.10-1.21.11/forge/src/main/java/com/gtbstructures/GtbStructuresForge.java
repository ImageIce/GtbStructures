package com.gtbstructures;

import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.commands.CommandGtbRuntime;
import com.gtbstructures.chat.ChatNameCompletion;
import com.gtbstructures.chat.GuessChatAnnotator;
import com.gtbstructures.diagnostics.RawPacketRecorder;
import com.gtbstructures.font.ClassicFontLanguageScreenButton;
import com.gtbstructures.font.ClassicFontResourcePack;
import com.gtbstructures.items.DisallowedItemHider;
import com.gtbstructures.items.HandheldItemOverlayRenderer;
import com.gtbstructures.chat.ModernChatCooldownCounter;
import com.gtbstructures.overlay.ModernHudOverlayRenderer;
import com.gtbstructures.overlay.WorldEnvironmentOverlayRenderer;
import com.gtbstructures.player.BuilderLeaderboardFallback;
import com.gtbstructures.player.BbRankDisplayRepair;
import com.gtbstructures.player.ModernPlayerListLogic;
import com.gtbstructures.chat.ScrambleGuess;
import com.gtbstructures.chat.TrollMode;
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
import com.gtbstructures.update.UpdateChecker;
import com.gtbstructures.util.MinecraftFormatting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.SystemMessageReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GtbStructuresForge.MOD_ID)
public final class GtbStructuresForge {
    public static final String MOD_ID = "gtbstructures";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String PLAY_GTB_COMMAND = "/play build_battle_guess_the_build";
    private static final int CLASSIC_FONT_REFRESH_INTERVAL_TICKS = 20;
    private static final int PLAYER_LIST_POLL_INTERVAL_TICKS = 20;
    private final ForgeServices services;
    private final Set<String> playerListRoster = new LinkedHashSet<String>();
    private final ChatNameCompletion.Session chatNameCompletion = new ChatNameCompletion.Session();
    private int scoreboardTickCounter;
    private boolean commandRefreshLogged;
    private boolean classicFontModeApplied;
    private int classicFontRefreshTicks;
    private Object lastInspectedThemeSelectionScreen;
    private Object pendingThemeSelectionScreen;
    private String pendingThemeSelectionTitle;
    private Object lastNonThemeSelectionScreen;
    private ClientPacketListener lastPlayerListConnection;
    private GameStats.TrackingState lastPlayerListState;
    private int ticksSincePlayerListPoll;
    private boolean playerListPollingActive;
    private ClientPacketListener updateNoticeConnection;
    private boolean updateNoticeRemoteJoinObserved;

    public GtbStructuresForge() {
        services = new ForgeServices();
        CommandGtbRuntime.configure(services);
        initializeSharedRuntime(services.gameDir(), services.configDir());
        GameStats.INSTANCE.setAccountPlayerUuid(services.accountPlayerUuid());
        RegisterClientCommandsEvent.BUS.addListener(this::registerClientCommands);
        ClientChatEvent.BUS.addListener(Priority.LOWEST,
                (java.util.function.Predicate<ClientChatEvent>) this::onClientChat);
        ClientChatReceivedEvent.BUS.addListener(this::onClientChatReceived);
        SystemMessageReceivedEvent.BUS.addListener(this::onSystemMessageReceived);
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::onClientTickPost);
        ScreenEvent.Init.Post.BUS.addListener(this::onScreenInitPost);
        ScreenEvent.KeyPressed.Pre.BUS.addListener(
                (java.util.function.Predicate<ScreenEvent.KeyPressed.Pre>) this::onScreenKeyPressed);
        InterfacePreview.setScreenController(new PreviewScreenController());
        PreviewEditorOverlayRenderer.registerBackdrop();
        // Forge inserts every addAbove(CHAT_OVERLAY) layer immediately beside the anchor.
        // Register the editor first so later preview samples are pushed below it at render time.
        PreviewEditorOverlayRenderer.register();
        CustomScoreboardOverlayRenderer.register();
        HandheldItemOverlayRenderer.register();
        ModernHudOverlayRenderer.register();
        WorldEnvironmentOverlayRenderer.register();
        DisallowedItemHider.registerForgeHooks();
        LOGGER.info("GtbStructures Forge initialized");
    }

    private static void initializeSharedRuntime(File gameDir, File configDir) {
        BuilderLeaderboardFallback.INSTANCE.init(configDir);
        GameStats.INSTANCE.setRuntimeGameDir(gameDir);
        GameStats.INSTANCE.setGtbHooks(GameTracker.INSTANCE);
        TranslationData.initAsync(gameDir);
        BuilderLeaderboardFallback.INSTANCE.refreshFromHypixelAsync(null);
    }

    private void registerClientCommands(RegisterClientCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String root : CommandGtbRuntime.rootCommands()) {
            dispatcher.register(commandRoot(root));
        }
    }

    static boolean ensureCommandsRegistered(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (dispatcher == null) return false;
        if (hasAllCommandRoots(dispatcher)) return false;
        registerCommands(dispatcher);
        return true;
    }

    static boolean hasAllCommandRoots(CommandDispatcher<?> dispatcher) {
        if (dispatcher == null) return false;
        for (String root : CommandGtbRuntime.rootCommands()) {
            if (dispatcher.getRoot().getChild(root) == null) return false;
        }
        return true;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandRoot(String root) {
        return Commands.literal(root)
                .executes(context -> execute(context, root, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggest(root, builder))
                        .executes(context -> execute(context, root, StringArgumentType.getString(context, "args"))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, String root, String rawArgs) {
        CommandGtbRuntime.Result result = CommandGtbRuntime.execute(root, rawArgs);
        for (String message : result.messages()) {
            context.getSource().sendSystemMessage(Component.literal(message));
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

    private boolean onClientChat(ClientChatEvent event) {
        GameStats.INSTANCE.onOutboundServerCommand(event.getMessage());
        boolean handled = dispatchLocalCommand(event.getMessage());
        if (!handled && GtbStructuresSettings.INSTANCE.modEnabled) {
            String outgoing = transformOutboundMessage(event.getMessage());
            event.setMessage(outgoing);
            RawPacketRecorder.INSTANCE.captureOutboundChat(outgoing);
            ModernChatCooldownCounter.onOutboundChat(GtbStructuresSettings.INSTANCE.chatCooldownCounter
                            && GameStats.INSTANCE.isTrackingEnabled(),
                    currentState(), outgoing, System.currentTimeMillis());
        }
        return handled;
    }

    static String transformOutboundMessage(String message) {
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return message;
        String processed = ScrambleGuess.transformOutbound(message);
        return TrollMode.transformOutbound(processed);
    }

    private boolean dispatchLocalCommand(String rawCommand) {
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
        if (!command.startsWith("/")) return null;
        command = command.substring(1);
        if (command.isEmpty()) return null;

        int firstSpace = command.indexOf(' ');
        String root = firstSpace < 0 ? command : command.substring(0, firstSpace);
        if (!CommandGtbRuntime.isRootCommand(root)) return null;
        String rawArgs = firstSpace < 0 ? "" : command.substring(firstSpace + 1);
        return new ParsedCommand(root, rawArgs);
    }

    private static void sendClientFeedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            // Local feedback must bypass Forge's inbound system-message event.
            // Feeding it through LocalPlayer would make GTBS parse its own
            // notices as Hypixel chat (and create bogus roster entries).
            StatsArchive.ThemeExportPrompt prompt =
                    StatsArchive.parseThemeExportPrompt(message);
            StatsArchive.LocalFileNotice localFileNotice =
                    StatsArchive.parseLocalFileNotice(message);
            mc.gui.getChat().addMessage(prompt != null
                    ? buildThemeExportPrompt(prompt)
                    : localFileNotice != null
                    ? buildLocalFileNotice(localFileNotice)
                    : Component.literal(message));
        }
    }

    static Component buildLocalFileNotice(StatsArchive.LocalFileNotice notice) {
        MutableComponent link = Component.literal(notice.link())
                .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE);
        link.setStyle(link.getStyle()
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(notice.hover())))
                .withClickEvent(new ClickEvent.RunCommand(notice.command())));
        return Component.literal(notice.prefix())
                .append(link)
                .append(Component.literal(notice.suffix()));
    }

    static Component buildThemeExportPrompt(
            StatsArchive.ThemeExportPrompt prompt) {
        MutableComponent link = Component.literal(prompt.link())
                .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE);
        link.setStyle(link.getStyle()
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal(prompt.hover())))
                .withClickEvent(new ClickEvent.RunCommand(prompt.command())));
        return Component.literal(prompt.prefix())
                .append(link)
                .append(Component.literal(prompt.suffix()));
    }

    static Component buildUpdateNotice(String version, String githubUrl, String modrinthUrl) {
        MutableComponent github = Component.literal("\u00a77GitHub");
        github.setStyle(github.getStyle().withClickEvent(
                new ClickEvent.OpenUrl(URI.create(githubUrl))));
        MutableComponent modrinth = Component.literal("\u00a7aModrinth");
        modrinth.setStyle(modrinth.getStyle().withClickEvent(
                new ClickEvent.OpenUrl(URI.create(modrinthUrl))));
        return Component.literal(CommandGtb.head + "New version available: \u00a7f"
                        + version + "\u00a77. \u00a78[")
                .append(github)
                .append(Component.literal("\u00a78]\u00a77 \u00a72["))
                .append(modrinth)
                .append(Component.literal("\u00a72]"));
    }

    private static void sendUpdateNotice(String version, String githubUrl, String modrinthUrl) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(buildUpdateNotice(version, githubUrl, modrinthUrl));
        }
    }

    private void onClientChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return;
        Component decorated = guessPeekDecoratedMessage(message);
        if (decorated != null) event.setMessage(decorated);
        ModernRuntimeBridge.onChat(formattedComponentString(message),
                message == null ? "" : message.getString());
    }

    private static Component guessPeekDecoratedMessage(Component original) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (!settings.modEnabled || original == null || !settings.guessPeek
                || !GameStats.INSTANCE.isTrackingEnabled()
                || !isGuessPeekAnnotationState(GameStats.INSTANCE.getState())
                || !TranslationData.isLoaded()) {
            return null;
        }
        GuessChatAnnotator.ParsedGuess parsed = GuessChatAnnotator.parseGuess(original.getString());
        if (parsed == null) return null;
        GuessChatAnnotator.Annotation annotation = GuessChatAnnotator.annotationForGuess(
                parsed.guess, settings.guessPeekShowLangCodes);
        if (annotation == null) return null;

        String display = GtbStructuresSettings.normalizeGuessPeekDisplay(settings.guessPeekDisplay);
        Component suffix = "suffix".equals(display)
                ? Component.literal(GuessChatAnnotator.suffixText(annotation.tooltip)) : null;
        HoverEvent hover = "suffix".equals(display)
                ? null : new HoverEvent.ShowText(Component.literal(annotation.tooltip));
        ClickEvent click = "suffix".equals(display)
                ? null : new ClickEvent.RunCommand(
                        GuessChatAnnotator.copyCommandForGuess(parsed.guess));
        return assembleGuessPeekMessage(original, suffix, hover, click, display,
                parsed.visibleGuessStart, parsed.visibleGuessEnd);
    }

    private static Component assembleGuessPeekMessage(Component original, Component suffix,
                                                       HoverEvent hover, ClickEvent click,
                                                       String display,
                                                       int visibleGuessStart, int visibleGuessEnd) {
        GuessPeekComponentParts parts = detachTrailingCopyButton(original);
        MutableComponent root = parts.base;
        if ("suffix".equals(display)) {
            if (suffix == null) return null;
            root.append(suffix);
        } else {
            root = rebuildGuessPeekStyleRange(root, hover, click,
                    visibleGuessStart, visibleGuessEnd);
            if (root == null) return null;
        }
        if (parts.trailing != null) root.append(parts.trailing);
        return root;
    }

    static Component assembleGuessPeekMessageForTest(Component original, Component suffix,
                                                     HoverEvent hover, ClickEvent click,
                                                     String display,
                                                     int visibleGuessStart, int visibleGuessEnd) {
        return assembleGuessPeekMessage(original, suffix, hover, click, display,
                visibleGuessStart, visibleGuessEnd);
    }

    private static MutableComponent rebuildGuessPeekStyleRange(Component text,
                                                               HoverEvent hover, ClickEvent click,
                                                               int visibleStart, int visibleEnd) {
        if (text == null || hover == null || visibleStart < 0 || visibleEnd <= visibleStart) {
            return null;
        }
        int[] cursor = new int[] {0};
        boolean[] applied = new boolean[] {false};
        MutableComponent rebuilt = rebuildGuessPeekComponent(text, hover, click,
                visibleStart, visibleEnd, cursor, applied);
        return applied[0] ? rebuilt : null;
    }

    private static MutableComponent rebuildGuessPeekComponent(Component text,
                                                               HoverEvent hover, ClickEvent click,
                                                               int visibleStart, int visibleEnd,
                                                               int[] cursor, boolean[] applied) {
        String fullText = text.getString();
        int componentStart = cursor[0];
        int componentEnd = componentStart + GuessChatAnnotator.visibleTextLength(fullText);
        if (visibleEnd <= componentStart || visibleStart >= componentEnd) {
            cursor[0] = componentEnd;
            return text.copy();
        }

        MutableComponent rebuilt = Component.literal("").setStyle(text.getStyle());
        List<Component> siblings = text.getSiblings();
        int siblingLength = 0;
        for (Component sibling : siblings) siblingLength += sibling.getString().length();
        int ownLength = Math.max(0, fullText.length() - siblingLength);
        String ownText = fullText.substring(0, Math.min(ownLength, fullText.length()));
        appendGuessPeekSegments(rebuilt, ownText, hover, click,
                visibleStart, visibleEnd, cursor, applied);
        for (Component sibling : siblings) {
            rebuilt.append(rebuildGuessPeekComponent(sibling, hover, click,
                    visibleStart, visibleEnd, cursor, applied));
        }
        return rebuilt;
    }

    private static void appendGuessPeekSegments(MutableComponent root, String text,
                                                HoverEvent hover, ClickEvent click,
                                                int visibleStart, int visibleEnd,
                                                int[] cursor, boolean[] applied) {
        int start = cursor[0];
        int end = start + GuessChatAnnotator.visibleTextLength(text);
        int activeStart = Math.max(start, visibleStart);
        int activeEnd = Math.min(end, visibleEnd);
        if (activeStart >= activeEnd) {
            if (!text.isEmpty()) root.append(Component.literal(text));
            cursor[0] = end;
            return;
        }

        GuessChatAnnotator.Split split = GuessChatAnnotator.splitFormattedGuess(
                text, activeStart - start, activeEnd - start);
        if (!split.prefix.isEmpty()) root.append(Component.literal(split.prefix));
        if (!split.guess.isEmpty()) {
            MutableComponent guess = Component.literal(split.guess);
            guess.setStyle(click == null
                    ? guess.getStyle().withHoverEvent(hover)
                    : guess.getStyle().withHoverEvent(hover).withClickEvent(click));
            root.append(guess);
            applied[0] = true;
        }
        if (!split.suffix.isEmpty()) root.append(Component.literal(split.suffix));
        cursor[0] = end;
    }

    private static boolean isGuessPeekAnnotationState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END;
    }

    private static GuessPeekComponentParts detachTrailingCopyButton(Component original) {
        MutableComponent base = original.copy();
        List<Component> siblings = base.getSiblings();
        if (siblings.isEmpty()) return new GuessPeekComponentParts(base, null);
        Component last = siblings.get(siblings.size() - 1);
        if (!"[C]".equals(last.getString().trim()) || last.getStyle().getClickEvent() == null) {
            return new GuessPeekComponentParts(base, null);
        }
        siblings.remove(siblings.size() - 1);
        return new GuessPeekComponentParts(base, last);
    }

    private static final class GuessPeekComponentParts {
        final MutableComponent base;
        final Component trailing;

        GuessPeekComponentParts(MutableComponent base, Component trailing) {
            this.base = base;
            this.trailing = trailing;
        }
    }

    private void onSystemMessageReceived(SystemMessageReceivedEvent event) {
        Component message = event.getMessage();
        String text = message == null ? "" : message.getString();
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return;
        if (event.isOverlay()) {
            ModernRuntimeBridge.onActionbar(text);
        } else {
            Component decorated = guessPeekDecoratedMessage(message);
            if (decorated != null) event.setMessage(decorated);
            ModernRuntimeBridge.onChat(formattedComponentString(message), text);
        }
    }

    private boolean onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (isEnterKey(event.getKeyCode())) {
            return tryDispatchChatLocalCommand(event.getScreen());
        }
        if (event.getKeyCode() != 258) return false;
        return tryCompleteChatName(event.getScreen());
    }

    private void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!GtbStructuresSettings.INSTANCE.modEnabled) return;
        try {
            ClassicFontLanguageScreenButton.install(event.getScreen(), Minecraft.getInstance(), services.gameDir());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to add GtbStructures Forge font settings option", e);
        }
    }

    private boolean tryDispatchChatLocalCommand(Object screen) {
        if (!(screen instanceof ChatScreen)) return false;
        try {
            Object input = fieldAny(screen, "input", "chatField", "field_2382");
            if (input == null) return false;
            String text = stringValue(invokeAny(input, "getValue", "getText", "method_1882"));
            if (!shouldInterceptLocalCommandInput(text)) return false;
            if (!dispatchLocalCommand(text)) return false;
            Minecraft mc = Minecraft.getInstance();
            mc.gui.getChat().addRecentChat(text);
            invokeStringAny(input, "", "setValue", "setText", "method_1852");
            if (shouldCloseInterceptedChatScreen(screen, mc.screen)) {
                mc.setScreen(null);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to dispatch GtbStructures local chat command", e);
            return false;
        }
    }

    static boolean shouldInterceptLocalCommandInput(String text) {
        return parseLocalCommand(text) != null;
    }

    static boolean isEnterKey(int keyCode) {
        return keyCode == 257 || keyCode == 335;
    }

    static boolean shouldCloseInterceptedChatScreen(Object interceptedScreen, Object currentScreen) {
        return interceptedScreen != null && interceptedScreen == currentScreen;
    }

    private boolean tryCompleteChatName(Object screen) {
        if (!(screen instanceof ChatScreen)) return false;
        if (!GtbStructuresSettings.INSTANCE.modEnabled) {
            chatNameCompletion.clear();
            return false;
        }
        if (!isCompletionState(GameStats.INSTANCE.getState())) {
            chatNameCompletion.clear();
            return false;
        }
        try {
            Object input = fieldAny(screen, "input", "chatField", "field_2382");
            if (input == null) return false;
            String text = stringValue(invokeAny(input, "getValue", "getText", "method_1882"));
            Integer cursor = intValue(invokeAny(input, "getCursorPosition", "getCursor", "method_1881"));
            int safeCursor = cursor == null ? text.length() : cursor.intValue();
            ChatNameCompletion.Completion completion = chatNameCompletion.complete(
                    screen, text, safeCursor, GameStats.INSTANCE.getPlayers(), true);
            if (completion == null) return false;
            if (!invokeStringAny(input, completion.apply(text), "setValue", "setText", "method_1852")) {
                return false;
            }
            invokeIntAny(input, completion.newCursor(), "setCursorPosition", "setCursor", "method_1883");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            chatNameCompletion.clear();
            LOGGER.warn("Unable to complete GTBS chat name", e);
            return false;
        }
    }

    private void onClientTickPost(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        applyClassicFontMode(mc);
        InterfacePreview.tick();
        CommandGtbRuntime.onLeaveRejoinClientTick();
        if (mc.level == null || mc.player == null) {
            CommandGtbRuntime.onClientConnectionBoundary(false);
            updateNoticeConnection = null;
            ModernRuntimeBridge.onClientUnavailable();
            ModernPlayerListLogic.applySnapshot(new ModernPlayerListLogic.Snapshot(), playerListRoster);
            playerListRoster.clear();
            resetPlayerListPolling();
            return;
        }

        handleUpdateNoticeJoin(mc);
        ensureClientCommandsRegistered(mc);
        if (!GtbStructuresSettings.INSTANCE.modEnabled) {
            resetPlayerListPolling();
            GameStats.INSTANCE.discardPendingAutonomousActions();
            CommandGtbRuntime.discardPendingAutonomousActions();
            ModernChatCooldownCounter.onTick(false, currentState(),
                    System.currentTimeMillis());
            return;
        }
        GameStats.INSTANCE.setAccountPlayerName(services.accountPlayerName());
        GameStats.INSTANCE.setLocalPlayerName(services.localPlayerName());
        captureThemeSelectionScreen(mc.screen);
        ModernRuntimeBridge.onClientTick();
        drainCommandRuntimeMessages();
        drainNotificationMessages();
        pollPlayerListIfDue(mc);
        pollPhysicalTimer(mc);
        DisallowedItemHider.onClientTick();
        ModernChatCooldownCounter.onTick(GtbStructuresSettings.INSTANCE.chatCooldownCounter
                        && GameStats.INSTANCE.isTrackingEnabled(),
                currentState(), System.currentTimeMillis());
        if (GameStats.INSTANCE.consumeAutoRequeueIfReady(System.currentTimeMillis())) {
            services.sendServerCommand(PLAY_GTB_COMMAND);
        }
        scoreboardTickCounter++;
        if (scoreboardTickCounter < 10) return;
        scoreboardTickCounter = 0;
        pollScoreboard(mc);
    }

    private void handleUpdateNoticeJoin(Minecraft mc) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || connection == updateNoticeConnection) return;
        updateNoticeConnection = connection;
        CommandGtbRuntime.onClientConnectionBoundary(true);
        if (updateNoticeRemoteJoinObserved || mc.hasSingleplayerServer()) return;
        updateNoticeRemoteJoinObserved = true;
        if (!GtbStructuresSettings.INSTANCE.updateNoticeEnabled) return;
        UpdateChecker.INSTANCE.checkOnceAsync(info -> mc.execute(() -> {
            if (!GtbStructuresSettings.INSTANCE.updateNoticeEnabled) return;
            sendUpdateNotice(info.version(), info.githubUrl(), info.modrinthUrl());
        }));
    }

    private void applyClassicFontMode(Minecraft mc) {
        String effectiveFontMode = GtbStructuresSettings.INSTANCE.modEnabled
                ? GtbStructuresSettings.INSTANCE.fontMode : "new";
        if (!classicFontModeApplied) {
            classicFontModeApplied = true;
            ClassicFontResourcePack.applyWithoutReload(services.gameDir(), mc, effectiveFontMode);
            return;
        }
        classicFontRefreshTicks++;
        if (classicFontRefreshTicks < CLASSIC_FONT_REFRESH_INTERVAL_TICKS) return;
        classicFontRefreshTicks = 0;
        ClassicFontResourcePack.refreshLoadedFontSet(mc, effectiveFontMode);
    }

    private static void drainNotificationMessages() {
        for (String message : GameStats.INSTANCE.drainNotificationMessages()) {
            sendClientFeedback(message);
        }
    }

    private static void drainCommandRuntimeMessages() {
        for (String message : CommandGtbRuntime.onClientTick().messages()) {
            sendClientFeedback(message);
        }
    }

    private void ensureClientCommandsRegistered(Minecraft mc) {
        try {
            boolean refreshed = false;
            CommandDispatcher<CommandSourceStack> clientDispatcher = ClientCommandHandler.getDispatcher();
            if (ensureCommandsRegistered(clientDispatcher)) {
                refreshed = true;
            }

            ClientPacketListener connection = mc.getConnection();
            if (connection != null && !hasAllCommandRoots(connection.getCommands())) {
                CommandBuildContext buildContext = CommandBuildContext.simple(
                        connection.registryAccess(), connection.enabledFeatures());
                connection.commands = ClientCommandHandler.mergeServerCommands(connection.getCommands(), buildContext);
                refreshed = true;
            }

            if (refreshed && !commandRefreshLogged) {
                commandRefreshLogged = true;
                LOGGER.info("Restored GtbStructures client command roots in the active dispatcher");
            }
        } catch (RuntimeException e) {
            if (!commandRefreshLogged) {
                commandRefreshLogged = true;
                LOGGER.warn("Unable to verify GtbStructures client command roots", e);
            }
        }
    }

    private void pollPlayerListIfDue(Minecraft mc) {
        if (ticksSincePlayerListPoll < PLAYER_LIST_POLL_INTERVAL_TICKS) {
            ticksSincePlayerListPoll++;
        }
        ClientPacketListener connection = mc.getConnection();
        GameStats.TrackingState state = GameStats.INSTANCE.getState();
        boolean connectionChanged = playerListPollingActive && connection != lastPlayerListConnection;
        if (!shouldPollPlayerList(playerListPollingActive, ticksSincePlayerListPoll,
                connectionChanged, lastPlayerListState, state)) return;

        pollPlayerList(mc, connection);
        playerListPollingActive = true;
        ticksSincePlayerListPoll = 0;
        lastPlayerListConnection = connection;
        lastPlayerListState = GameStats.INSTANCE.getState();
    }

    static boolean shouldPollPlayerList(boolean pollingActive, int ticksSinceLastPoll,
                                        boolean connectionChanged,
                                        GameStats.TrackingState previousState,
                                        GameStats.TrackingState currentState) {
        return !pollingActive || connectionChanged || previousState != currentState
                || ticksSinceLastPoll >= PLAYER_LIST_POLL_INTERVAL_TICKS;
    }

    /** Repairs a team immediately after the vanilla packet handler applies it. */
    public static void onPlayerTeamPacketApplied(ClientPacketListener listener,
                                                 ClientboundSetPlayerTeamPacket packet) {
        if (listener == null || packet == null) return;
        String teamName = packet.getName();
        if (teamName == null || teamName.isEmpty()) return;
        if (listener.getLevel() == null) {
            BbRankDisplayRepair.INSTANCE.restoreAll();
            return;
        }
        Scoreboard scoreboard = listener.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            BbRankDisplayRepair.INSTANCE.discardTeam(teamName);
            return;
        }
        if (!GtbStructuresSettings.INSTANCE.modEnabled) {
            BbRankDisplayRepair.INSTANCE.restoreAll();
            return;
        }
        BbRankDisplayRepair.INSTANCE.repairEncodedTeamColor(team);
    }

    private void resetPlayerListPolling() {
        playerListPollingActive = false;
        ticksSincePlayerListPoll = 0;
        lastPlayerListConnection = null;
        lastPlayerListState = null;
        lastInspectedThemeSelectionScreen = null;
        pendingThemeSelectionScreen = null;
        pendingThemeSelectionTitle = null;
        lastNonThemeSelectionScreen = null;
        BbRankDisplayRepair.INSTANCE.restoreAll();
    }

    private void pollPlayerList(Minecraft mc, ClientPacketListener connection) {
        pollRosterObjectiveTitle(mc);
        Set<String> listedPlayerNames = new LinkedHashSet<String>();
        if (connection == null) {
            ModernPlayerListLogic.applySnapshot(new ModernPlayerListLogic.Snapshot(), playerListRoster);
            BbRankDisplayRepair.INSTANCE.onPlayerListPoll(mc, listedPlayerNames);
            return;
        }

        ModernPlayerListLogic.Snapshot snapshot = new ModernPlayerListLogic.Snapshot();
        Set<String> observedTeams = new LinkedHashSet<String>();
        Collection<PlayerInfo> entries = connection.getListedOnlinePlayers();
        String accountUuid = services.accountPlayerUuid();
        for (PlayerInfo info : entries) {
            if (info == null || info.getProfile() == null) continue;
            String name = info.getProfile().name();
            String entryUuid = PlayerUuid.normalize(info.getProfile().id());
            if (accountUuid != null && accountUuid.equals(entryUuid)) {
                GameStats.INSTANCE.confirmServerLocalPlayerName(name);
            }
            listedPlayerNames.add(name);
            Component display = info.getTabListDisplayName();
            snapshot.addPlayer(name, formattedComponentString(display));
            PlayerTeam team = info.getTeam();
            if (team != null && observedTeams.add(team.getName())) {
                snapshot.addTeam(team.getName(),
                        formattedComponentString(team.getPlayerPrefix()),
                        formattedComponentString(team.getPlayerSuffix()),
                        team.getPlayers(), 0);
            }
        }
        addForgeScoreboardTeams(snapshot, observedTeams,
                mc.level == null ? null : mc.level.getScoreboard());
        ModernPlayerListLogic.applySnapshot(snapshot, playerListRoster);
        BbRankDisplayRepair.INSTANCE.onPlayerListPoll(mc, listedPlayerNames);
    }

    private static void addForgeScoreboardTeams(ModernPlayerListLogic.Snapshot snapshot,
                                                Set<String> observedTeams,
                                                Scoreboard scoreboard) {
        if (scoreboard == null) return;
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            if (team == null || !observedTeams.add(team.getName())) continue;
            snapshot.addTeam(team.getName(),
                    formattedComponentString(team.getPlayerPrefix()),
                    formattedComponentString(team.getPlayerSuffix()),
                    team.getPlayers(), 0);
        }
    }

    private static void pollRosterObjectiveTitle(Minecraft mc) {
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return;
        GameStats.INSTANCE.onRosterObjectiveTitle(ModernRuntimeBridge.cleanText(
                formattedComponentString(objective.getDisplayName())));
    }

    private static void pollPhysicalTimer(Minecraft mc) {
        ModernPlayerListLogic.observePhysicalTimer(mc.player.experienceLevel);
    }

    private void captureThemeSelectionScreen(Object screen) {
        if (screen == null) {
            lastInspectedThemeSelectionScreen = null;
            pendingThemeSelectionScreen = null;
            pendingThemeSelectionTitle = null;
            lastNonThemeSelectionScreen = null;
            return;
        }
        if (!shouldInspectThemeSelectionScreen(
                screen, lastInspectedThemeSelectionScreen, lastNonThemeSelectionScreen)) return;
        try {
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
            if (ModernRuntimeBridge.onThemeSelectionScreen(title, themes, services.localPlayerName())) {
                lastInspectedThemeSelectionScreen = screen;
                pendingThemeSelectionScreen = null;
                pendingThemeSelectionTitle = null;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Unable to inspect GtbStructures Forge theme selection screen", e);
        }
    }

    static boolean shouldInspectThemeSelectionScreen(Object screen, Object lastInspectedScreen,
                                                      Object lastNonThemeScreen) {
        return screen != null && screen != lastInspectedScreen && screen != lastNonThemeScreen;
    }

    private static String screenTitle(Object screen) throws ReflectiveOperationException {
        String title = componentString(invokeAny(screen,
                "getTitle", "getNarratedTitle", "method_25440"));
        if (!title.isEmpty()) return title;
        return componentString(fieldAnyDeep(screen, "title", "field_22785"));
    }

    private static String[] themeOptions(Object screen) throws ReflectiveOperationException {
        Object menu = invokeAny(screen, "getMenu", "getScreenHandler", "method_17577");
        if (menu == null) {
            menu = fieldAnyDeep(screen, "menu", "screenHandler", "handler", "field_2792");
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

        Object stack = invokeAny(slot, "getItem", "getStack", "method_7677");
        if (stack == null) stack = fieldAnyDeep(slot, "item", "stack", "field_7871");
        return itemStackName(stack);
    }

    private static String itemStackName(Object stack) throws ReflectiveOperationException {
        if (stack == null) return null;
        Object empty = invokeAny(stack, "isEmpty", "method_7960");
        if (empty instanceof Boolean && ((Boolean) empty).booleanValue()) return null;
        String name = componentString(invokeAny(stack,
                "getHoverName", "getName", "getDisplayName", "method_7964"));
        name = ModernRuntimeBridge.cleanText(name);
        return name.isEmpty() ? null : name;
    }

    private void pollScoreboard(Minecraft mc) {
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            ModernRuntimeBridge.onScoreboard(null, null);
            return;
        }

        List<PlayerScoreEntry> entries = new ArrayList<PlayerScoreEntry>(scoreboard.listPlayerScores(objective));
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value));
        List<String> lines = new ArrayList<String>(entries.size());
        for (PlayerScoreEntry entry : entries) {
            if (entry.isHidden()) continue;
            addScoreboardLine(lines, scoreboardLine(scoreboard, entry));
        }
        ModernRuntimeBridge.onScoreboard(formattedComponentString(objective.getDisplayName()), lines);
    }

    private static GtbGame.State currentState() {
        GtbGame game = GameTracker.INSTANCE.game;
        return game == null ? GtbGame.State.NONE : game.state;
    }

    private static boolean isCompletionState(GameStats.TrackingState state) {
        return state == GameStats.TrackingState.LOBBY
                || state == GameStats.TrackingState.ROUND_PRE
                || state == GameStats.TrackingState.ROUND_BUILD
                || state == GameStats.TrackingState.ROUND_END
                || state == GameStats.TrackingState.POST_GAME;
    }

    private static String scoreboardLine(Scoreboard scoreboard, PlayerScoreEntry entry) {
        String owner = entry.owner();
        PlayerTeam team = scoreboard.getPlayersTeam(owner);
        if (isHiddenScoreOwner(owner)) {
            Component teamLine = team == null ? null
                    : PlayerTeam.formatNameForTeam(team, Component.literal(""));
            return isVisiblyEmpty(teamLine) ? owner : formattedComponentString(teamLine);
        }
        Component display = entry.display();
        if (isVisiblyEmpty(display)) {
            display = PlayerTeam.formatNameForTeam(team, Component.literal(owner));
        }
        if (isVisiblyEmpty(display)) {
            display = entry.ownerName();
        }
        return formattedComponentString(display);
    }

    private static void addScoreboardLine(List<String> lines, String line) {
        if (line == null) return;
        lines.add(ModernRuntimeBridge.cleanText(line).isEmpty() ? "\u00a7r" : line);
    }

    static List<String> scoreboardLinesForTest(String... rawLines) {
        List<String> lines = new ArrayList<String>();
        if (rawLines != null) {
            for (String line : rawLines) addScoreboardLine(lines, line);
        }
        return lines;
    }

    private static boolean isHiddenScoreOwner(String owner) {
        return owner != null && !owner.isEmpty() && owner.charAt(0) == '\u00a7';
    }

    private static boolean isVisiblyEmpty(Component component) {
        return component == null || ModernRuntimeBridge.cleanText(component.getString()).isEmpty();
    }

    private static String componentString(Component component) {
        return component == null ? null : component.getString();
    }

    private static String formattedComponentString(Component component) {
        return component == null ? "" : MinecraftFormatting.legacyFormattedText(component);
    }

    private static String componentString(Object component) throws ReflectiveOperationException {
        if (component == null) return "";
        if (component instanceof Component) return ((Component) component).getString();
        if (component instanceof CharSequence) return component.toString();
        Object value = invokeAny(component, "getString", "method_10851");
        return value instanceof String ? (String) value : component.toString();
    }

    private static Object fieldAny(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return null;
        Class<?> type = target.getClass();
        for (String name : names) {
            try {
                Field field = type.getField(name);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignoredAgain) {
                    // Try next field name.
                }
            }
        }
        return null;
    }

    private static Object invokeAny(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // Try next method name.
            }
        }
        return null;
    }

    private static Object invokeAnyWithInt(Object target, int arg, String... names)
            throws ReflectiveOperationException {
        if (target == null) return null;
        for (String name : names) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter.equals(Integer.TYPE) || parameter.equals(Integer.class)) {
                    return method.invoke(target, Integer.valueOf(arg));
                }
            }
        }
        return null;
    }

    private static Object fieldAnyDeep(Object target, String... names)
            throws ReflectiveOperationException {
        if (target == null) return null;
        for (String name : names) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
        }
        return null;
    }

    private static boolean invokeStringAny(Object target, String value, String... names)
            throws ReflectiveOperationException {
        if (target == null) return false;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name, String.class);
                method.invoke(target, value);
                return true;
            } catch (NoSuchMethodException ignored) {
                // Try next method name.
            }
        }
        return false;
    }

    private static boolean invokeIntAny(Object target, int value, String... names)
            throws ReflectiveOperationException {
        if (target == null) return false;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name, Integer.TYPE);
                method.invoke(target, Integer.valueOf(value));
                return true;
            } catch (NoSuchMethodException ignored) {
                // Try next method name.
            }
        }
        return false;
    }

    private static Integer intValue(Object value) {
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static final class ParsedCommand {
        private final String root;
        private final String rawArgs;

        private ParsedCommand(String root, String rawArgs) {
            this.root = root;
            this.rawArgs = rawArgs;
        }
    }

    private final class ForgeServices implements CommandGtbRuntime.Services {
        @Override
        public File gameDir() {
            return Minecraft.getInstance().gameDirectory;
        }

        @Override
        public File configDir() {
            return FMLPaths.CONFIGDIR.get().toFile();
        }

        @Override
        public String localPlayerName() {
            return Minecraft.getInstance().player == null
                    ? null : Minecraft.getInstance().player.getGameProfile().name();
        }

        @Override
        public String accountPlayerName() {
            return Minecraft.getInstance().getUser().getName();
        }

        @Override
        public String accountPlayerUuid() {
            return PlayerUuid.normalize(Minecraft.getInstance().getUser().getProfileId());
        }

        @Override
        public boolean playerAvailable() {
            return Minecraft.getInstance().player != null;
        }

        @Override
        public void sendServerCommand(String command) {
            if (Minecraft.getInstance().getConnection() != null) {
                GameStats.INSTANCE.onOutboundServerCommand(command);
                RawPacketRecorder.INSTANCE.captureOutboundChat(withLeadingSlash(command));
                Minecraft.getInstance().getConnection().sendCommand(withoutLeadingSlash(command));
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
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            return true;
        }
    }
}
