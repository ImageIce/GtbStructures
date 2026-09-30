package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructuresSettings;
import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.commands.CommandGtbRuntime;
import com.gtbstructures.overlay.HudPanelScale;
import com.gtbstructures.overlay.HudPosition;
import com.gtbstructures.stats.GameStats;
import com.gtbstructures.sound.GuessSounds;
import com.gtbstructures.sound.MinecraftClientSoundBridge;
import com.gtbstructures.translation.ShortcutLanguageGroups;
import com.gtbstructures.translation.ShortcutReminder;
import com.gtbstructures.util.MinecraftFormatting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PreviewLayoutEditor {
    public static final PreviewLayoutEditor INSTANCE = new PreviewLayoutEditor();

    public static final Target ACTIONBAR = new Target("actionbar", MoveMode.FIXED, false, false);
    public static final Target HINT_STRUCTURE = new Target("hint", MoveMode.TOP_LEFT, true, true);
    public static final Target COOLDOWN = new Target("cooldown", MoveMode.TOP_LEFT, true, true);
    public static final Target HANDHELD = new Target("handheld", MoveMode.BOTTOM_LEFT, true, true);
    public static final Target ENVIRONMENT = new Target("environment", MoveMode.BOTTOM_LEFT, true, true);
    public static final Target ENVIRONMENT_WEATHER = new Target("environment_weather", MoveMode.BOTTOM_LEFT, true, true);
    public static final Target ENVIRONMENT_BIOME = new Target("environment_biome", MoveMode.BOTTOM_LEFT, true, true);
    public static final Target ENVIRONMENT_TIME = new Target("environment_time", MoveMode.BOTTOM_LEFT, true, true);
    public static final Target SCOREBOARD = new Target("scoreboard", MoveMode.SCOREBOARD_Y, false, true);

    private static final int MARGIN = 2;
    private static final int ARROW_REPEAT_FRAMES = 8;
    private static final int ARROW_ACCELERATED_REPEAT_FRAMES = 3;
    private static final int ARROW_ACCELERATION_START_FRAMES = 24;
    private static final int ARROW_LEFT = 1;
    private static final int ARROW_RIGHT = 2;
    private static final int ARROW_UP = 4;
    private static final int ARROW_DOWN = 8;
    private static final float HOVER_STEP_IN = 0.50F;
    private static final float HOVER_STEP_OUT = 0.42F;
    private static final int MODAL_SHADE = 0xD8181818;
    private static final int PANEL_BACKGROUND = 0xE0101217;
    private static final int PANEL_BORDER = 0x553A4654;
    private static final int PANEL_TEXT = 0xFFFFFFFF;
    private static final int PANEL_MUTED = 0xFF9EA8B5;
    private static final int PANEL_CLOSE_BG = 0x70302028;
    private static final int PANEL_CLOSE_BORDER = 0x88AA4252;
    private static final int DONE_BUTTON_WIDTH = 58;
    private static final int DONE_BUTTON_HEIGHT = 20;
    private static final int LABELS_PANEL_WIDTH = 268;
    private static final int LABELS_BUTTON_SIZE = 20;
    private static final int LABELS_SCROLLBAR_WIDTH = 4;
    private static final int LABELS_SCROLLBAR_MIN_THUMB = 16;
    private static final int PANEL_PAD = 6;
    private static final int TITLE_HEIGHT = 11;
    private static final int ROW_HEIGHT = 12;
    private static final int HEADER_ROW_HEIGHT = 20;
    private static final int AUTO_REQUEUE_DELAY_EDITOR_WIDTH = 24;
    private static final String AUTO_REQUEUE_DELAY_LABEL = "Requeue Delay";
    private static final int LABELS_SCROLL_STEP = ROW_HEIGHT * 3;
    private static final int CHECK_SIZE = 10;
    private static final int LANGUAGE_GROUP_BUTTON_HEIGHT = 20;
    private static final int CLOSE_SIZE = 11;
    private static final int BACKGROUND_COLUMN_OFFSET = 64;
    private static final int CHILD_ROW_INDENT = CHECK_SIZE + 5;
    private static final int SECTION_MARGIN_Y = 3;
    private static final int SECTION_TEXT_HEIGHT = 9;
    private static final int STEPPER_BUTTON_SIZE = 13;
    private static final int STEPPER_VALUE_CENTER = 26;
    private static final int STEPPER_PLUS_X = 39;
    private static final int SLIDER_ROW_HEIGHT = 16;
    private static final int SLIDER_WIDTH = 74;
    private static final int SLIDER_KNOB_WIDTH = 5;
    private static final int SLIDER_VALUE_EDITOR_WIDTH = 28;
    private static final int SLIDER_VALUE_EDITOR_GAP = 4;
    private static final int HISTORY_LIMIT = 40;
    private static final int RESIZE_HANDLE_VISUAL_RADIUS = 3;
    private static final int RESIZE_HANDLE_HIT_RADIUS = 8;
    private static final int RESIZE_DRAG_MULTIPLIER = 2;
    private static final int RESIZE_LEFT = 1;
    private static final int RESIZE_RIGHT = 2;
    private static final int RESIZE_TOP = 4;
    private static final int RESIZE_BOTTOM = 8;
    private static final int LABEL_BG_PAD_X = 3;
    private static final int LABEL_BG_PAD_Y = 2;
    private static final int SETTINGS_PANEL_GAP = 6;
    private static final String[] COLOR_CODES = {
            "0", "1", "2", "3", "4", "5", "6", "7",
            "8", "9", "a", "b", "c", "d", "e", "f"
    };

    private final List<Label> labels = new ArrayList<Label>();
    private final Map<String, Float> hoverAmounts = new HashMap<String, Float>();
    private final Map<String, Float> controlHoverAmounts = new HashMap<String, Float>();
    private String hoveredId = "";
    private String selectedId = "";
    private String activePanelId = "";
    private String pinnedPanelFamily = "";
    private int pinnedPanelX;
    private int pinnedPanelY;
    private int pinnedPanelAnchorX;
    private int pinnedPanelAnchorSide;
    private boolean pinnedPanelAnchorValid;
    private String hintColorTarget = "number";
    private String cooldownColorTarget = "timer";
    private final List<ConfigSnapshot> undoStack = new ArrayList<ConfigSnapshot>();
    private final List<ConfigSnapshot> redoStack = new ArrayList<ConfigSnapshot>();
    private final ShortcutLanguageGroupPreviewManager shortcutGroupManager =
            new ShortcutLanguageGroupPreviewManager();
    private final GuessSoundsPreviewPanel guessSoundPanel = new GuessSoundsPreviewPanel();
    private boolean labelsPanelOpen;
    private int labelsScrollOffset;
    private boolean labelsScrollbarDragging;
    private int labelsScrollbarDragOffset;
    private boolean guessSoundScrollbarDragging;
    private int guessSoundScrollbarDragOffset;
    private boolean shortcutScrollbarDragging;
    private int shortcutScrollbarDragOffset;
    private boolean previousLeftDown;
    private boolean previousRightDown;
    private boolean previousDeleteDown;
    private boolean previousEscapeDown;
    private boolean previousZDown;
    private int previousArrowMask;
    private int arrowHoldFrames;
    private int arrowRepeatFrames;
    private ConfigSnapshot dragUndoSnapshot;
    private ConfigSnapshot arrowUndoSnapshot;
    private boolean dragArmed;
    private boolean dragDirty;
    private boolean toggleSelectedOnRelease;
    private int dragDistance;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private float dragStartX;
    private float dragStartY;
    private int dragStartScoreboardOffset;
    private int dragStartLabelX;
    private int dragStartLabelY;
    private int dragStartPanelX;
    private int dragStartPanelY;
    private int dragPanelLabelX;
    private int dragPanelLabelY;
    private boolean dragPanelFollows;
    private int resizeEdges;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private ConfigSnapshot resizeUndoSnapshot;
    private String activeSlider = "";
    private int activeSliderX;
    private ConfigSnapshot sliderUndoSnapshot;
    private boolean sliderDirty;
    private boolean headerEditing;
    private boolean autoRequeueDelayEditing;
    private boolean sliderValueEditing;
    private String sliderValueEditingId = "";
    private String sliderValueInitialText = "";
    private ConfigSnapshot externalScaleUndoSnapshot;
    private ScaleChangeAnchor activeDiscreteScaleChange;
    private ScaleChangeAnchor pendingDiscreteScaleChange;
    private String frozenScaleDragFamily = "";
    private boolean scaleDragMenuRebasePending;
    private int drawMouseX;
    private int drawMouseY;
    private boolean drawLeftDown;

    private PreviewLayoutEditor() {
    }

    public void beginExternalScaleChange(String panel) {
        externalScaleUndoSnapshot = InterfacePreview.isActive() ? ConfigSnapshot.capture() : null;
        beginDiscreteScaleChange(panel);
    }

    public void endExternalScaleChange(String panel) {
        endDiscreteScaleChange(panel);
        if (externalScaleUndoSnapshot != null && !externalScaleUndoSnapshot.matchesCurrent()) {
            recordUndo(externalScaleUndoSnapshot);
        }
        externalScaleUndoSnapshot = null;
    }

    public static void register(String id, int x, int y, int width, int height, Target target) {
        if (!InterfacePreview.isActive()) return;
        INSTANCE.registerInternal(id, x, y, width, height, target);
    }

    public static void registerWithBackground(String id, int x, int y, int width, int height,
                                              Target target, BackgroundRenderer renderer) {
        if (!InterfacePreview.isActive()) return;
        Label label = INSTANCE.registerInternal(id, x, y, width, height, target);
        if (label != null && renderer != null) {
            INSTANCE.drawBackground(renderer, label);
        }
    }

    public static void render(EditorRenderer renderer, InputState input, int screenWidth, int screenHeight) {
        if (!InterfacePreview.isActive()) {
            INSTANCE.reset();
            return;
        }
        INSTANCE.handleInput(renderer, input, screenWidth, screenHeight);
        INSTANCE.draw(renderer, input, screenWidth, screenHeight);
    }

    public static void renderBackdrop(EditorRenderer renderer, int screenWidth, int screenHeight) {
        if (!InterfacePreview.isActive()) {
            INSTANCE.reset();
            return;
        }
        INSTANCE.labels.clear();
        if (renderer != null) {
            renderer.fill(0, 0, screenWidth, screenHeight, MODAL_SHADE);
        }
    }

    public void reset() {
        if (headerEditing) finishHeaderEditing();
        labels.clear();
        hoverAmounts.clear();
        controlHoverAmounts.clear();
        hoveredId = "";
        selectedId = "";
        activePanelId = "";
        pinnedPanelFamily = "";
        pinnedPanelX = 0;
        pinnedPanelY = 0;
        pinnedPanelAnchorX = 0;
        pinnedPanelAnchorSide = 0;
        pinnedPanelAnchorValid = false;
        hintColorTarget = "number";
        cooldownColorTarget = "timer";
        undoStack.clear();
        redoStack.clear();
        labelsPanelOpen = false;
        shortcutGroupManager.reset();
        guessSoundPanel.close();
        labelsScrollOffset = 0;
        labelsScrollbarDragging = false;
        labelsScrollbarDragOffset = 0;
        guessSoundScrollbarDragging = false;
        guessSoundScrollbarDragOffset = 0;
        shortcutScrollbarDragging = false;
        shortcutScrollbarDragOffset = 0;
        previousLeftDown = false;
        previousRightDown = false;
        previousDeleteDown = false;
        previousEscapeDown = false;
        previousZDown = false;
        resetArrowRepeat();
        dragUndoSnapshot = null;
        arrowUndoSnapshot = null;
        dragArmed = false;
        dragDirty = false;
        toggleSelectedOnRelease = false;
        dragDistance = 0;
        dragPanelLabelX = 0;
        dragPanelLabelY = 0;
        dragPanelFollows = false;
        resizeEdges = 0;
        resizeStartWidth = 0;
        resizeStartHeight = 0;
        resizeUndoSnapshot = null;
        activeSlider = "";
        sliderUndoSnapshot = null;
        sliderDirty = false;
        headerEditing = false;
        autoRequeueDelayEditing = false;
        sliderValueEditing = false;
        sliderValueEditingId = "";
        sliderValueInitialText = "";
        externalScaleUndoSnapshot = null;
        activeDiscreteScaleChange = null;
        pendingDiscreteScaleChange = null;
        frozenScaleDragFamily = "";
        scaleDragMenuRebasePending = false;
        PreviewScreenController.cancelHeaderEditing();
        drawMouseX = 0;
        drawMouseY = 0;
        drawLeftDown = false;
    }

    private Label registerInternal(String id, int x, int y, int width, int height, Target target) {
        if (id == null || id.trim().isEmpty() || target == null || width <= 0 || height <= 0) return null;
        if (!targetVisibleForMaster(GtbStructuresSettings.INSTANCE.modEnabled, target.key)) return null;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        Label label = new Label(normalized, x, y, x + width, y + height, target);
        for (int i = labels.size() - 1; i >= 0; i--) {
            if (normalized.equals(labels.get(i).id)) {
                labels.remove(i);
                break;
            }
        }
        labels.add(label);
        return label;
    }

    private void handleInput(EditorRenderer renderer, InputState input, int screenWidth, int screenHeight) {
        if (renderer == null || input == null) return;
        applyPendingDiscreteScaleChange(screenWidth, screenHeight);
        rebaseScaleDragMenu(screenWidth, screenHeight);
        boolean modEnabled = GtbStructuresSettings.INSTANCE.modEnabled;
        if (!modEnabled) guessSoundPanel.close();
        Label selected = labelById(selectedId);
        if (!modEnabled && selected != null) {
            deselect();
            selected = null;
        }
        if (selected == null && !selectedId.isEmpty()) {
            selectedId = "";
            selected = null;
        }
        boolean overEditorPanel = isPointInEditorPanel(renderer, screenWidth, screenHeight, input.mouseX, input.mouseY);
        int selectedResizeEdges = overEditorPanel
                ? 0 : resizeEdgesAt(selected, input.mouseX, input.mouseY);
        Label hovered = !modEnabled ? null : overEditorPanel
                ? null : selectedResizeEdges != 0
                ? selected : topLabelAt(input.mouseX, input.mouseY);
        hoveredId = hovered == null ? "" : hovered.id;

        boolean leftPressed = input.leftDown && !previousLeftDown;
        boolean leftReleased = !input.leftDown && previousLeftDown;
        boolean rightPressed = input.rightDown && !previousRightDown;
        boolean escapePressed = input.escapeDown && !previousEscapeDown;
        boolean deletePressed = input.deleteDown && !previousDeleteDown;
        boolean zPressed = input.zDown && !previousZDown;

        if (input.scrollDelta != 0.0D && headerEditing) finishHeaderEditing();
        if (input.scrollDelta != 0.0D && guessSoundPanel.isOpen()) {
            guessSoundPanel.scrollAt(input.mouseX, input.mouseY, input.scrollDelta,
                    guessSoundsLayout(labelsPanelRect(screenWidth, screenHeight)));
        } else if (input.scrollDelta != 0.0D && shortcutGroupManager.inSubview()) {
            shortcutGroupManager.finishInlineField();
            shortcutGroupManager.scrollBy(input.scrollDelta > 0.0D ? -LABELS_SCROLL_STEP : LABELS_SCROLL_STEP,
                    shortcutLanguageGroupViewportHeight(labelsPanelRect(screenWidth, screenHeight)), ROW_HEIGHT);
        } else if (input.scrollDelta != 0.0D && labelsPanelOpen
                && labelsPanelRect(screenWidth, screenHeight).contains(input.mouseX, input.mouseY)) {
            scrollLabelsBy(input.scrollDelta > 0.0D ? -LABELS_SCROLL_STEP : LABELS_SCROLL_STEP,
                    screenWidth, screenHeight);
        }

        if (escapePressed && guessSoundPanel.isOpen()) { guessSoundPanel.close(); return; }
        if (escapePressed && shortcutGroupManager.inSubview()) {
            shortcutGroupManager.handleKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            return;
        }
        if (escapePressed) {
            if (headerEditing) finishHeaderEditing();
            InterfacePreview.INSTANCE.disableSilently();
            return;
        }

        if (!headerEditing && input.controlDown && zPressed) {
            if (input.shiftDown) redoConfigChange();
            else undoConfigChange();
            previousLeftDown = input.leftDown;
            previousRightDown = input.rightDown;
            previousDeleteDown = input.deleteDown;
            previousEscapeDown = input.escapeDown;
            previousZDown = input.zDown;
            return;
        }

        boolean consumedLeftPress = false;
        boolean consumedRightPress = false;

        Label inlineEditorPanelLabel = sliderValueEditing ? activePanelLabel() : null;
        Rect inlineEditorPanel = inlineEditorPanelLabel == null
                ? labelsPanelRect(screenWidth, screenHeight)
                : settingsPanelRect(inlineEditorPanelLabel, screenWidth, screenHeight);
        if (leftPressed && headerEditing
                && !headerInputRect(renderer, inlineEditorPanel)
                .contains(input.mouseX, input.mouseY)) {
            finishHeaderEditing();
        }

        if (leftPressed) {
            if (handleSettingsPanelClick(renderer, screenWidth, screenHeight, input.mouseX, input.mouseY)) {
                consumedLeftPress = true;
            } else if (handleLabelsPanelClick(renderer, screenWidth, screenHeight, input.mouseX, input.mouseY)) {
                consumedLeftPress = true;
            } else if (labelsButtonRect(screenWidth).contains(input.mouseX, input.mouseY)) {
                guessSoundPanel.close();
                labelsPanelOpen = !labelsPanelOpen;
                labelsScrollbarDragging = false;
                guessSoundScrollbarDragging = false;
                shortcutScrollbarDragging = false;
                if (labelsPanelOpen) labelsScrollOffset = 0;
                consumedLeftPress = true;
            } else if (doneButtonRect(screenWidth, screenHeight).contains(input.mouseX, input.mouseY)) {
                if (headerEditing) finishHeaderEditing();
                InterfacePreview.INSTANCE.disableSilently();
                return;
            }
        }

        if (rightPressed) {
            if (hovered != null) {
                select(hovered.id);
                activePanelId = hovered.id;
                pinPanel(hovered, screenWidth, screenHeight);
                selected = hovered;
                consumedRightPress = true;
            } else if (!overEditorPanel) {
                clearActivePanel();
            }
        }

        if (leftPressed && !consumedLeftPress) {
            dragArmed = false;
            dragDirty = false;
            toggleSelectedOnRelease = false;
            dragDistance = 0;
            if (hovered == null) {
                deselect();
            } else if ("handheld".equals(hovered.target.key)
                    && resizeEdgesAt(hovered, input.mouseX, input.mouseY) != 0) {
                select(hovered.id);
                selected = hovered;
                resizeEdges = resizeEdgesAt(hovered, input.mouseX, input.mouseY);
                resizeStartWidth = hovered.width();
                resizeStartHeight = hovered.height();
                dragStartMouseX = input.mouseX;
                dragStartMouseY = input.mouseY;
                dragStartLabelX = hovered.x1;
                dragStartLabelY = hovered.y1;
                resizeUndoSnapshot = ConfigSnapshot.capture();
            } else if (input.modifierDown) {
                selectOnPress(hovered, true);
            } else {
                boolean same = hovered.id.equals(selectedId);
                select(hovered.id);
                selected = hovered;
                dragArmed = true;
                toggleSelectedOnRelease = same;
                dragStartMouseX = input.mouseX;
                dragStartMouseY = input.mouseY;
                dragStartX = selected.target.x();
                dragStartY = selected.target.y();
                dragStartScoreboardOffset = GtbStructuresSettings.INSTANCE.sbConfig.heightOffset;
                dragStartLabelX = selected.x1;
                dragStartLabelY = selected.y1;
                armPanelFollow(selected, screenWidth, screenHeight);
                dragUndoSnapshot = ConfigSnapshot.capture();
            }
        }

        if (rightPressed && !consumedRightPress
                && selected != null && (hovered == null || !hovered.id.equals(selected.id))) {
            deselect();
            selected = null;
        }

        if (input.leftDown && !activeSlider.isEmpty()) {
            updateActiveScoreboardSlider(input.mouseX);
        }
        if (input.leftDown && labelsScrollbarDragging) {
            updateLabelsScrollbarDrag(screenWidth, screenHeight, input.mouseY);
        }
        if (input.leftDown && guessSoundScrollbarDragging) {
            updateGuessSoundScrollbarDrag(labelsPanelRect(screenWidth, screenHeight), input.mouseY);
        }
        if (input.leftDown && shortcutScrollbarDragging) {
            updateShortcutScrollbarDrag(labelsPanelRect(screenWidth, screenHeight), input.mouseY);
        }

        if (input.leftDown && resizeEdges != 0) {
            resizeHandheldPanel(input.mouseX - dragStartMouseX, input.mouseY - dragStartMouseY,
                    screenWidth, screenHeight);
        } else if (input.leftDown && dragArmed && selected != null) {
            int dx = input.mouseX - dragStartMouseX;
            int dy = input.mouseY - dragStartMouseY;
            if (dx != 0 || dy != 0) {
                selected.target.moveFromAnchor(dx, dy, selected.width(), selected.height(),
                        screenWidth, screenHeight, dragStartX, dragStartY, dragStartScoreboardOffset);
                movePinnedPanelWithDrag(selected, screenWidth, screenHeight);
                dragDirty = true;
                dragDistance = Math.abs(dx) + Math.abs(dy);
            }
        }

        if (leftReleased) {
            labelsScrollbarDragging = false;
            guessSoundScrollbarDragging = false;
            shortcutScrollbarDragging = false;
            if (!activeSlider.isEmpty()) {
                finishScoreboardSliderDrag();
            }
            if (dragDirty) {
                if (dragUndoSnapshot != null && !dragUndoSnapshot.matchesCurrent()) {
                    recordUndo(dragUndoSnapshot);
                    GtbStructuresSettings.INSTANCE.save();
                }
            } else if (toggleSelectedOnRelease && dragDistance <= 1) {
                deselect();
            }
            if (resizeEdges != 0) {
                if (resizeUndoSnapshot != null && !resizeUndoSnapshot.matchesCurrent()) {
                    recordUndo(resizeUndoSnapshot);
                    GtbStructuresSettings.INSTANCE.save();
                }
                resizeEdges = 0;
                resizeUndoSnapshot = null;
            }
            dragArmed = false;
            dragDirty = false;
            dragUndoSnapshot = null;
            toggleSelectedOnRelease = false;
            dragDistance = 0;
            dragPanelFollows = false;
        }

        selected = labelById(selectedId);
        if (selected != null) {
            int arrowMask = input.arrowMask();
            boolean moved = false;
            boolean applyArrowMove = shouldApplyArrowMove(arrowMask);
            ConfigSnapshot beforeArrowMove = applyArrowMove ? ConfigSnapshot.capture() : null;
            if (applyArrowMove) {
                if ((arrowMask & ARROW_LEFT) != 0) {
                    selected.target.movePixels(-1, 0, selected.width(), selected.height(), screenWidth, screenHeight);
                    moved = true;
                }
                if ((arrowMask & ARROW_RIGHT) != 0) {
                    selected.target.movePixels(1, 0, selected.width(), selected.height(), screenWidth, screenHeight);
                    moved = true;
                }
                if ((arrowMask & ARROW_UP) != 0) {
                    selected.target.movePixels(0, -1, selected.width(), selected.height(), screenWidth, screenHeight);
                    moved = true;
                }
                if ((arrowMask & ARROW_DOWN) != 0) {
                    selected.target.movePixels(0, 1, selected.width(), selected.height(), screenWidth, screenHeight);
                    moved = true;
                }
                if (moved && beforeArrowMove != null && !beforeArrowMove.matchesCurrent()) {
                    if (arrowUndoSnapshot == null) {
                        arrowUndoSnapshot = beforeArrowMove;
                        recordUndo(beforeArrowMove);
                    }
                    GtbStructuresSettings.INSTANCE.save();
                }
            }
        } else {
            resetArrowRepeat();
        }

        if (deletePressed && selected != null && selected.target.canDelete()) {
            recordUndo();
            selected.target.disable();
            GtbStructuresSettings.INSTANCE.save();
            deselect();
        }

        updateHoverAmounts();
        previousLeftDown = input.leftDown;
        previousRightDown = input.rightDown;
        previousDeleteDown = input.deleteDown;
        previousEscapeDown = input.escapeDown;
        previousZDown = input.zDown;
    }

    private void selectOnPress(Label hovered, boolean modifier) {
        if (hovered == null) {
            deselect();
            return;
        }
        if (modifier && hovered.id.equals(selectedId)) deselect();
        else select(hovered.id);
    }

    private void select(String id) {
        String next = id == null ? "" : id;
        if (!next.equals(selectedId) && !next.equals(activePanelId)) clearActivePanel();
        selectedId = next;
    }

    private void deselect() {
        selectedId = "";
        clearActivePanel();
        resetArrowRepeat();
    }

    private void clearActivePanel() {
        activePanelId = "";
        pinnedPanelFamily = "";
        pinnedPanelX = 0;
        pinnedPanelY = 0;
        pinnedPanelAnchorX = 0;
        pinnedPanelAnchorSide = 0;
        pinnedPanelAnchorValid = false;
    }

    private void recordUndo() {
        recordUndo(ConfigSnapshot.capture());
    }

    private void recordUndo(ConfigSnapshot snapshot) {
        if (snapshot == null || snapshot.matchesCurrent()) return;
        undoStack.add(snapshot);
        if (undoStack.size() > HISTORY_LIMIT) undoStack.remove(0);
        redoStack.clear();
    }

    private void undoConfigChange() {
        if (undoStack.isEmpty()) return;
        ConfigSnapshot current = ConfigSnapshot.capture();
        ConfigSnapshot previous = undoStack.remove(undoStack.size() - 1);
        redoStack.add(current);
        if (redoStack.size() > HISTORY_LIMIT) redoStack.remove(0);
        previous.apply();
        GtbStructuresSettings.INSTANCE.save();
    }

    private void redoConfigChange() {
        if (redoStack.isEmpty()) return;
        ConfigSnapshot current = ConfigSnapshot.capture();
        ConfigSnapshot next = redoStack.remove(redoStack.size() - 1);
        undoStack.add(current);
        if (undoStack.size() > HISTORY_LIMIT) undoStack.remove(0);
        next.apply();
        GtbStructuresSettings.INSTANCE.save();
    }

    private void updateHoverAmounts() {
        for (Label label : labels) {
            float current = hoverAmounts.containsKey(label.id) ? hoverAmounts.get(label.id).floatValue() : 0F;
            boolean active = label.id.equals(hoveredId) || label.id.equals(selectedId);
            float next = active ? Math.min(1F, current + HOVER_STEP_IN) : Math.max(0F, current - HOVER_STEP_OUT);
            hoverAmounts.put(label.id, Float.valueOf(next));
        }
    }

    private boolean handleLabelsPanelClick(EditorRenderer renderer, int screenWidth, int screenHeight,
                                           int mouseX, int mouseY) {
        if (!labelsPanelOpen) return false;
        Rect panel = labelsPanelRect(screenWidth, screenHeight);
        if (!panel.contains(mouseX, mouseY)) return false;
        if (panelCloseRect(panel).contains(mouseX, mouseY)) {
            if (headerEditing) finishHeaderEditing();
            shortcutGroupManager.closeToLabels();
            guessSoundPanel.close();
            labelsPanelOpen = false;
            labelsScrollbarDragging = false;
            guessSoundScrollbarDragging = false;
            shortcutScrollbarDragging = false;
            return true;
        }

        if (guessSoundPanel.isOpen()) return handleGuessSoundsClick(renderer, panel, mouseX, mouseY);
        clampLabelsScroll(screenWidth, screenHeight);
        if (shortcutGroupManager.inSubview()) {
            return handleShortcutLanguageGroupSubviewClick(renderer, panel, mouseX, mouseY);
        }
        if (beginLabelsScrollbarDrag(panel, mouseX, mouseY)) return true;
        if (!labelsBodyViewport(panel).contains(mouseX, mouseY)) return true;

        int x = panel.x1 + PANEL_PAD;
        int y = labelsBodyY(panel) - labelsScrollOffset;
        GtbStructuresSettings masterSettings = GtbStructuresSettings.INSTANCE;
        if (toggleHit(renderer, x, y, "Toggle Mod", mouseX, mouseY)) {
            recordUndo();
            masterSettings.modEnabled = !masterSettings.modEnabled;
            masterSettings.save();
            labelsScrollOffset = 0;
            if (!masterSettings.modEnabled) deselect();
            return true;
        }
        y += ROW_HEIGHT;
        if (!masterSettings.modEnabled) return true;
        y = menuSectionYAfter(y);
        if (handleHeaderRowClick(renderer, panel, x, y, mouseX, mouseY)) return true;
        y += HEADER_ROW_HEIGHT;
        y = menuSectionYAfter(y);
        if (toggleHit(renderer, x, y, "Cooldown Counter", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            applyCooldownState(toggleCooldownLabelState(settings.chatCooldownCounter,
                    settings.chatCooldownTimerEnabled, settings.chatCooldownBarEnabled));
            settings.save();
            if (!settings.chatCooldownCounter) clearPanelForTarget("cooldown");
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(renderer, x, y, "Environment", mouseX, mouseY)) {
            toggleEnvironmentOverlay();
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(renderer, x, y, "Handheld Items", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.handheldItemOverlay = !settings.handheldItemOverlay;
            settings.save();
            if (!settings.handheldItemOverlay) clearPanelForTarget("handheld");
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(renderer, x, y, "Hint Structure", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.enabled = !settings.enabled;
            settings.save();
            if (!settings.enabled) clearPanelForTarget("hint");
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(renderer, x, y, "Custom Scoreboard", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.sbConfig.toggleCustom();
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        y = menuSectionYAfter(y);
        if (toggleHit(renderer, x, y, "Stats Tracking", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.trackingEnabled = !settings.trackingEnabled;
            GameStats.INSTANCE.setTrackingEnabled(settings.trackingEnabled);
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(renderer, x, y, "Auto Requeue", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.autoRequeue = !settings.autoRequeue;
            GameStats.INSTANCE.setAutoRequeue(settings.autoRequeue);
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructuresSettings.INSTANCE.autoRequeue) {
            if (handleAutoRequeueDelayRowClick(renderer, panel, x + CHILD_ROW_INDENT, y, mouseX, mouseY)) {
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (toggleHit(renderer, x, y, "Bypass /l Protection For /lrj", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.lrjBypassLobbyProtection = !settings.lrjBypassLobbyProtection;
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        y = menuSectionYAfter(y);
        if (toggleHit(renderer, x, y, "Guess Peek", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.guessPeek = !settings.guessPeek;
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructuresSettings.INSTANCE.guessPeek) {
            y += ROW_HEIGHT; // Display Option label
            int displayX = x + CHILD_ROW_INDENT * 2;
            if (toggleHit(renderer, displayX, y, "Shown When Hovering", mouseX, mouseY)) {
                GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
                recordUndo();
                settings.guessPeekDisplay = "hover";
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, displayX, y, "Shown As Suffix", mouseX, mouseY)) {
                GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
                recordUndo();
                settings.guessPeekDisplay = "suffix";
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Show Lang Codes", mouseX, mouseY)) {
                GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
                recordUndo();
                settings.guessPeekShowLangCodes = !settings.guessPeekShowLangCodes;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (toggleHit(renderer, x, y, "Show Themes Matching The Initial Hint", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.showAllMatching = !settings.showAllMatching;
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructuresSettings.INSTANCE.showAllMatching) {
            if (toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Show English Translations", mouseX, mouseY)) {
                GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
                recordUndo();
                settings.showAllMatchingShowEnglish = !settings.showAllMatchingShowEnglish;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Show Total Theme Count", mouseX, mouseY)) {
                GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
                recordUndo();
                settings.showAllMatchingShowCount = !settings.showAllMatchingShowCount;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (toggleHit(renderer, x, y, "Reveal Theme Point Value In Chat", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.themePointReveal = !settings.themePointReveal;
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructuresSettings.INSTANCE.themePointReveal) {
            if (toggleHit(renderer, x + CHILD_ROW_INDENT, y,
                    "Still Reveal After Correct Guess", mouseX, mouseY)) {
                GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
                recordUndo();
                settings.themePointRevealShowAfterGuess = !settings.themePointRevealShowAfterGuess;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (toggleHit(renderer, x, y, "Shortcut Reminder", mouseX, mouseY)) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            recordUndo();
            settings.shortcutReminder = !settings.shortcutReminder;
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (settings.shortcutReminder) {
            if (toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Show Multiwords", mouseX, mouseY)) {
                recordUndo();
                settings.shortcutReminderShowMultiwords = !settings.shortcutReminderShowMultiwords;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (settings.shortcutReminderShowMultiwords) {
                if (toggleHit(renderer, x + CHILD_ROW_INDENT * 2, y,
                        "Show All Multiwords", mouseX, mouseY)) {
                    recordUndo();
                    settings.shortcutReminderShowAllMultiwords = !settings.shortcutReminderShowAllMultiwords;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
            }
            if (toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Show Non-ASCII", mouseX, mouseY)) {
                recordUndo();
                settings.setShortcutReminderShowNonAscii(!settings.shortcutReminderShowNonAscii);
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (settings.shortcutReminderShowNonAscii) {
                int categoryX = x + CHILD_ROW_INDENT * 2;
                if (toggleHit(renderer, categoryX, y, "Extended Latin", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(GtbStructuresSettings.ShortcutNonAsciiCategory.EXTENDED_LATIN);
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, categoryX, y, "Cyrillic", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(GtbStructuresSettings.ShortcutNonAsciiCategory.CYRILLIC);
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, categoryX, y, "Hanzi", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(GtbStructuresSettings.ShortcutNonAsciiCategory.HANZI);
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, categoryX, y, "Kanji & Kana", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(GtbStructuresSettings.ShortcutNonAsciiCategory.JAPANESE);
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, categoryX, y, "Hangul", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(GtbStructuresSettings.ShortcutNonAsciiCategory.HANGUL);
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
            }
            if (toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Show All Shortcuts", mouseX, mouseY)) {
                recordUndo();
                settings.shortcutReminderShowAllShortcuts = !settings.shortcutReminderShowAllShortcuts;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Language Group Shortcuts", mouseX, mouseY)) {
            ConfigSnapshot before = ConfigSnapshot.capture();
            ShortcutLanguageGroups.setEnabled(!ShortcutLanguageGroups.isEnabled());
            if (!before.matchesCurrent()) {
                recordUndo(before);
                settings.save();
            }
            return true;
        }
        y += ROW_HEIGHT;
        if (languageGroupManagerLinkRect(panel, y).contains(mouseX, mouseY)) {
            shortcutGroupManager.openManager();
            return true;
        }
        y += ROW_HEIGHT;
        y = menuSectionYAfter(y);
        if (toggleHit(renderer, x, y, "Scramble Guess", mouseX, mouseY)) {
            recordUndo();
            settings.scrambleGuess = !settings.scrambleGuess;
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        if (settings.scrambleGuess
                && toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Fancy Mode", mouseX, mouseY)) {
            recordUndo();
            settings.scrambleGuessFancy = !settings.scrambleGuessFancy;
            settings.save();
            return true;
        }
        if (settings.scrambleGuess) y += ROW_HEIGHT;
        if (toggleHit(renderer, x, y, "Troll Mode", mouseX, mouseY)) {
            recordUndo();
            settings.trollMode = !settings.trollMode;
            settings.save();
            return true;
        }
        y += ROW_HEIGHT;
        if (settings.trollMode && toggleHit(renderer, x + CHILD_ROW_INDENT, y,
                "Disable upon game start", mouseX, mouseY)) {
            recordUndo();
            settings.trollModeAutoDisable = !settings.trollModeAutoDisable;
            settings.save();
            return true;
        }
        if (settings.trollMode) y += ROW_HEIGHT;
        if (new Rect(x + CHECK_SIZE + 5, y, panel.x2 - PANEL_PAD, y + ROW_HEIGHT).contains(mouseX, mouseY)) {
            MinecraftClientSoundBridge.ensureCatalog(); guessSoundPanel.open(); return true;
        }
        return true;
    }

    public boolean onHeaderKeyPressed(int keyCode) {
        if (guessSoundPanel.isOpen()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                if (headerEditing) finishHeaderEditing();
                guessSoundPanel.close(); return true;
            }
            if (guessSoundPanel.isSearchEditing() && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
                guessSoundPanel.finishSearch(); return true;
            }
        }
        if (shortcutGroupManager.inSubview()) {
            if (shortcutGroupManager.editing()
                    && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
                saveShortcutLanguageGroupDraft();
                return true;
            }
            return shortcutGroupManager.handleKey(keyCode);
        }
        if (!headerEditing) return false;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            finishHeaderEditing();
            return true;
        }
        return false;
    }

    private boolean handleHeaderRowClick(EditorRenderer renderer, Rect panel, int x, int y,
                                         int mouseX, int mouseY) {
        Rect row = new Rect(x, y, panel.x2 - PANEL_PAD, y + HEADER_ROW_HEIGHT);
        if (!row.contains(mouseX, mouseY)) return false;
        if (headerEditing) return true;
        if (headerResetRect(renderer, x, y).contains(mouseX, mouseY)) {
            if (!ChatHeader.DEFAULT_RAW.equals(ChatHeader.raw())) {
                recordUndo();
                ChatHeader.reset();
                CommandGtb.refreshHeader();
                GtbStructuresSettings.INSTANCE.save();
            }
            return true;
        }
        if (headerValueRect(renderer, x, y).contains(mouseX, mouseY)) {
            headerEditing = true;
            PreviewScreenController.beginHeaderEditing(ChatHeader.raw());
        }
        return true;
    }

    private void finishHeaderEditing() {
        if (!headerEditing) return;
        String requested = PreviewScreenController.finishHeaderEditing();
        headerEditing = false;
        if (autoRequeueDelayEditing) {
            autoRequeueDelayEditing = false;
            commitAutoRequeueDelay(requested);
            return;
        }
        if (sliderValueEditing) {
            String sliderId = sliderValueEditingId;
            sliderValueEditing = false;
            sliderValueEditingId = "";
            commitSliderValue(sliderId, requested);
            sliderValueInitialText = "";
            return;
        }
        String prepared = ChatHeader.prepare(requested);
        if (prepared == null) {
            GameStats.INSTANCE.queueNotificationMessage(headerInvalidNoticeForTest());
        } else if (!prepared.equals(ChatHeader.raw())) {
            ConfigSnapshot before = ConfigSnapshot.capture();
            ChatHeader.set(requested);
            CommandGtb.refreshHeader();
            recordUndo(before);
            GtbStructuresSettings.INSTANCE.save();
        }
    }

    private static int headerValueX(EditorRenderer renderer, int x) {
        return x;
    }

    private static Rect headerValueRect(EditorRenderer renderer, int x, int y) {
        int valueX = headerValueX(renderer, x);
        return new Rect(valueX, y, valueX + renderer.textWidth(ChatHeader.rendered()),
                y + HEADER_ROW_HEIGHT);
    }

    private static Rect headerResetRect(EditorRenderer renderer, int x, int y) {
        int resetX = headerValueX(renderer, x) + renderer.textWidth(ChatHeader.rendered()) + 7;
        return new Rect(resetX, y, resetX + renderer.textWidth("Reset"), y + HEADER_ROW_HEIGHT);
    }

    private Rect headerInputRect(EditorRenderer renderer, Rect panel) {
        if (autoRequeueDelayEditing) return autoRequeueDelayInputRect(renderer, panel);
        if (sliderValueEditing) return sliderValueInputRect(renderer, panel);
        int x = panel.x1 + PANEL_PAD;
        int y = labelsBodyY(panel) - labelsScrollOffset;
        y = menuSectionYAfter(y);
        int left = headerValueX(renderer, x) - 2;
        return new Rect(left, y, Math.max(left + 16,
                panel.x2 - PANEL_PAD - LABELS_SCROLLBAR_WIDTH - 3), y + HEADER_ROW_HEIGHT);
    }

    private Rect autoRequeueDelayInputRect(EditorRenderer renderer, Rect panel) {
        int x = panel.x1 + PANEL_PAD;
        int y = labelsBodyY(panel) - labelsScrollOffset;
        y += ROW_HEIGHT;
        y = menuSectionYAfter(y);
        y += HEADER_ROW_HEIGHT;
        y = menuSectionYAfter(y);
        y += ROW_HEIGHT * 5;
        y = menuSectionYAfter(y);
        y += ROW_HEIGHT * 2;
        return autoRequeueDelayValueRect(renderer, x + CHILD_ROW_INDENT, y);
    }

    private void draw(EditorRenderer renderer, InputState input, int screenWidth, int screenHeight) {
        if (renderer == null || input == null) return;
        drawMouseX = input.mouseX;
        drawMouseY = input.mouseY;
        drawLeftDown = input.leftDown;
        drawSettingsPanel(renderer, screenWidth, screenHeight);
        int buttonMouseX = input.mouseX;
        int buttonMouseY = input.mouseY;
        if (isPointInOpenPanel(screenWidth, screenHeight, input.mouseX, input.mouseY)) {
            buttonMouseX = Integer.MIN_VALUE;
            buttonMouseY = Integer.MIN_VALUE;
        }
        drawLabelsButton(renderer, screenWidth, buttonMouseX, buttonMouseY);
        if (labelsPanelOpen) drawLabelsPanel(renderer, screenWidth, screenHeight);
        drawDoneButton(renderer, screenWidth, screenHeight, buttonMouseX, buttonMouseY);
    }

    private static void drawPadded(EditorRenderer renderer, Label label, int color) {
        renderer.fill(label.x1 - LABEL_BG_PAD_X, label.y1 - LABEL_BG_PAD_Y,
                label.x2 + LABEL_BG_PAD_X, label.y2 + LABEL_BG_PAD_Y, color);
    }

    private static void drawPadded(BackgroundRenderer renderer, Label label, int color) {
        renderer.fill(label.x1 - LABEL_BG_PAD_X, label.y1 - LABEL_BG_PAD_Y,
                label.x2 + LABEL_BG_PAD_X, label.y2 + LABEL_BG_PAD_Y, color);
    }

    private static void drawLabelsButton(EditorRenderer renderer, int screenWidth, int mouseX, int mouseY) {
        Rect rect = labelsButtonRect(screenWidth);
        drawVanillaButton(renderer, rect, INSTANCE.labelsPanelOpen ? "][" : "[]", 1001, mouseX, mouseY);
    }

    private static void drawDoneButton(EditorRenderer renderer, int screenWidth, int screenHeight,
                                       int mouseX, int mouseY) {
        drawVanillaButton(renderer, doneButtonRect(screenWidth, screenHeight), "Done", 1002, mouseX, mouseY);
    }

    private static void drawVanillaButton(EditorRenderer renderer, Rect rect, String text, int id,
                                          int mouseX, int mouseY) {
        if (renderer.drawVanillaButton(rect.x1, rect.y1, rect.width(), rect.height(),
                text, mouseX, mouseY)) {
            return;
        }
        boolean hovered = rect.contains(mouseX, mouseY);
        renderer.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0xFF000000);
        renderer.fill(rect.x1 + 1, rect.y1 + 1, rect.x2 - 1, rect.y2 - 1,
                hovered ? 0xFF5C7FA9 : 0xFF4A4A4A);
        renderer.fill(rect.x1 + 1, rect.y1 + 1, rect.x2 - 1, rect.y1 + rect.height() / 2,
                hovered ? 0xFF6E95C8 : 0xFF666666);
        drawOutline(renderer, rect, hovered ? 0xFFFFFFFF : 0xFF9A9A9A);
        renderer.drawString(text, rect.x1 + (rect.width() - renderer.textWidth(text)) / 2,
                rect.y1 + (rect.height() - renderer.fontHeight()) / 2, 0xFFE0E0E0, true);
    }

    private void drawLabelsPanel(EditorRenderer renderer, int screenWidth, int screenHeight) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        Rect panel = labelsPanelRect(screenWidth, screenHeight);
        clampLabelsScroll(screenWidth, screenHeight);
        drawPanel(renderer, panel);
        drawLabelsPanelCloseButton(renderer, panel);
        if (guessSoundPanel.isOpen()) { drawGuessSounds(renderer, panel); return; }
        if (shortcutGroupManager.view() == ShortcutLanguageGroupPreviewManager.View.GROUPS) {
            drawShortcutLanguageGroupManager(renderer, panel);
            return;
        }
        if (shortcutGroupManager.view() == ShortcutLanguageGroupPreviewManager.View.EDITOR) {
            drawShortcutLanguageGroupEditor(renderer, panel);
            return;
        }
        int x = panel.x1 + PANEL_PAD;
        int y = panel.y1 + PANEL_PAD;
        renderer.drawString("\u00a7f\u00a7nMenu", x, y, PANEL_TEXT, true);
        Rect viewport = labelsBodyViewport(panel);
        renderer.enableScissor(viewport.x1, viewport.y1, viewport.x2, viewport.y2);
        try {
            y = labelsBodyY(panel) - labelsScrollOffset;
            drawToggleRow(renderer, x, y, "Toggle Mod", settings.modEnabled);
            y += ROW_HEIGHT;
            if (!settings.modEnabled) return;
            y = drawMenuSection(renderer, x, y, "Header");
            drawHeaderRow(renderer, panel, x, y);
            y += HEADER_ROW_HEIGHT;
            y = drawMenuSection(renderer, x, y, "Labels");
            drawToggleRow(renderer, x, y, "Cooldown Counter",
                    cooldownLabelChecked(settings.chatCooldownCounter,
                            settings.chatCooldownTimerEnabled, settings.chatCooldownBarEnabled));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Environment", environmentLabelVisible(settings));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Handheld Items", settings.handheldItemOverlay);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Hint Structure", settings.enabled);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Custom Scoreboard", settings.sbConfig.isCustomEnabled());
            y += ROW_HEIGHT;
            y = drawMenuSection(renderer, x, y, "Game");
            drawToggleRow(renderer, x, y, "Stats Tracking", settings.trackingEnabled);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Auto Requeue", settings.autoRequeue);
            y += ROW_HEIGHT;
            if (settings.autoRequeue) {
                drawAutoRequeueDelayRow(renderer, panel, x + CHILD_ROW_INDENT, y);
                y += ROW_HEIGHT;
            }
            drawToggleRow(renderer, x, y, "Bypass /l Protection For /lrj",
                    settings.lrjBypassLobbyProtection);
            y += ROW_HEIGHT;
            y = drawMenuSection(renderer, x, y, "Chat");
            drawToggleRow(renderer, x, y, "Guess Peek", settings.guessPeek);
            y += ROW_HEIGHT;
            if (settings.guessPeek) {
                String display = GtbStructuresSettings.normalizeGuessPeekDisplay(settings.guessPeekDisplay);
                drawSection(renderer, x + CHILD_ROW_INDENT,
                        toggleTextY(y, renderer.fontHeight()), "Display Option");
                y += ROW_HEIGHT;
                int displayX = x + CHILD_ROW_INDENT * 2;
                drawToggleRow(renderer, displayX, y, "Shown When Hovering", "hover".equals(display));
                y += ROW_HEIGHT;
                drawToggleRow(renderer, displayX, y, "Shown As Suffix", "suffix".equals(display));
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Show Lang Codes", settings.guessPeekShowLangCodes);
                y += ROW_HEIGHT;
            }
            drawToggleRow(renderer, x, y, "Show Themes Matching The Initial Hint", settings.showAllMatching);
            y += ROW_HEIGHT;
            if (settings.showAllMatching) {
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Show English Translations",
                        settings.showAllMatchingShowEnglish);
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Show Total Theme Count",
                        settings.showAllMatchingShowCount);
                y += ROW_HEIGHT;
            }
            drawToggleRow(renderer, x, y, "Reveal Theme Point Value In Chat",
                    settings.themePointReveal);
            y += ROW_HEIGHT;
            if (settings.themePointReveal) {
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y,
                        "Still Reveal After Correct Guess", settings.themePointRevealShowAfterGuess);
                y += ROW_HEIGHT;
            }
            drawToggleRow(renderer, x, y, "Shortcut Reminder", settings.shortcutReminder);
            y += ROW_HEIGHT;
            if (settings.shortcutReminder) {
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Show Multiwords",
                        settings.shortcutReminderShowMultiwords);
                y += ROW_HEIGHT;
                if (settings.shortcutReminderShowMultiwords) {
                    drawToggleRow(renderer, x + CHILD_ROW_INDENT * 2, y, "Show All Multiwords",
                            settings.shortcutReminderShowAllMultiwords);
                    y += ROW_HEIGHT;
                }
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Show Non-ASCII",
                        settings.shortcutReminderShowNonAscii);
                y += ROW_HEIGHT;
                if (settings.shortcutReminderShowNonAscii) {
                    int categoryX = x + CHILD_ROW_INDENT * 2;
                    drawToggleRow(renderer, categoryX, y, "Extended Latin",
                            settings.shortcutNonAsciiCategoryEnabled(
                                    GtbStructuresSettings.ShortcutNonAsciiCategory.EXTENDED_LATIN));
                    y += ROW_HEIGHT;
                    drawToggleRow(renderer, categoryX, y, "Cyrillic",
                            settings.shortcutNonAsciiCategoryEnabled(
                                    GtbStructuresSettings.ShortcutNonAsciiCategory.CYRILLIC));
                    y += ROW_HEIGHT;
                    drawToggleRow(renderer, categoryX, y, "Hanzi",
                            settings.shortcutNonAsciiCategoryEnabled(
                                    GtbStructuresSettings.ShortcutNonAsciiCategory.HANZI));
                    y += ROW_HEIGHT;
                    drawToggleRow(renderer, categoryX, y, "Kanji & Kana",
                            settings.shortcutNonAsciiCategoryEnabled(
                                    GtbStructuresSettings.ShortcutNonAsciiCategory.JAPANESE));
                    y += ROW_HEIGHT;
                    drawToggleRow(renderer, categoryX, y, "Hangul",
                            settings.shortcutNonAsciiCategoryEnabled(
                                    GtbStructuresSettings.ShortcutNonAsciiCategory.HANGUL));
                    y += ROW_HEIGHT;
                }
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Show All Shortcuts",
                        settings.shortcutReminderShowAllShortcuts);
                y += ROW_HEIGHT;
            }
            // These controls are independent of the ordinary Shortcut Reminder master.
            drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Language Group Shortcuts",
                    ShortcutLanguageGroups.isEnabled());
            y += ROW_HEIGHT;
            String manage = "Manage Language Groups";
            Rect manageLink = languageGroupManagerLinkRect(panel, y);
            boolean manageHovered = manageLink.contains(drawMouseX, drawMouseY);
            renderer.drawString((manageHovered ? "\u00a7f\u00a7n" : "\u00a77") + manage,
                    manageLink.x1, toggleTextY(y, renderer.fontHeight()),
                    manageHovered ? PANEL_TEXT : PANEL_MUTED, true);
            String count = "\u00a77(" + ShortcutLanguageGroups.groups().size() + ")  >";
            renderer.drawString(count, panel.x2 - PANEL_PAD - renderer.textWidth(count) - 3,
                    toggleTextY(y, renderer.fontHeight()), PANEL_MUTED, true);
            y += ROW_HEIGHT;
            y = drawMenuSection(renderer, x, y, "Experimental");
            drawToggleRow(renderer, x, y, "Scramble Guess", settings.scrambleGuess);
            y += ROW_HEIGHT;
            if (settings.scrambleGuess) {
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Fancy Mode",
                        settings.scrambleGuessFancy);
                y += ROW_HEIGHT;
            }
            drawToggleRow(renderer, x, y, "Troll Mode", settings.trollMode);
            y += ROW_HEIGHT;
            if (settings.trollMode) {
                drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Disable upon game start",
                        settings.trollModeAutoDisable);
                y += ROW_HEIGHT;
            }
            int soundTextX = x + CHECK_SIZE + 5;
            Rect soundLink = new Rect(soundTextX, y, panel.x2 - PANEL_PAD, y + ROW_HEIGHT);
            boolean soundHovered = soundLink.contains(drawMouseX, drawMouseY);
            renderer.drawString((soundHovered ? "\u00a7f\u00a7n" : "\u00a77") + "Guess Sounds",
                    soundTextX, toggleTextY(y, renderer.fontHeight()),
                    soundHovered ? PANEL_TEXT : PANEL_MUTED, true);
            renderer.drawString("\u00a77>", panel.x2 - PANEL_PAD - renderer.textWidth(">") - 3,
                    toggleTextY(y, renderer.fontHeight()), PANEL_MUTED, true);
        } finally {
            renderer.disableScissor();
        }
        drawLabelsScrollbar(renderer, panel);
    }


    private static GuessSoundsUiLogic.Layout guessSoundsLayout(Rect panel) {
        return new GuessSoundsUiLogic.Layout(panel.x1, panel.y1, panel.width(), panel.height());
    }

    private static Rect guessSoundScrollbarTrack(Rect panel, GuessSoundsUiLogic.Layout layout) {
        return new Rect(panel.x2 - 10, layout.listY, panel.x2 - 6,
                layout.listY + layout.listHeight);
    }

    private static Rect guessSoundScrollbarThumb(Rect panel, GuessSoundsUiLogic.Layout layout,
                                                  int itemCount, int offset) {
        int maxScroll = GuessSoundsUiLogic.maxScroll(itemCount, layout.visibleRows);
        Rect track = guessSoundScrollbarTrack(panel, layout);
        if (maxScroll <= 0 || track.height() <= 0) return null;
        int thumbHeight = Math.min(track.height(), Math.max(12,
                track.height() * Math.max(1, layout.visibleRows) / Math.max(1, itemCount)));
        int travel = track.height() - thumbHeight;
        int thumbY = travel <= 0 ? 0 : (int) Math.round((double)
                GuessSoundsUiLogic.clampScroll(offset, itemCount, layout.visibleRows) * travel / maxScroll);
        return new Rect(track.x1, track.y1 + thumbY, track.x2, track.y1 + thumbY + thumbHeight);
    }

    private boolean beginGuessSoundScrollbarDrag(Rect panel, GuessSoundsUiLogic.Layout layout,
                                                 int mouseX, int mouseY) {
        Rect thumb = guessSoundScrollbarThumb(panel, layout, guessSoundPanel.rows().size(),
                guessSoundPanel.scroll());
        if (thumb == null) return false;
        Rect track = guessSoundScrollbarTrack(panel, layout);
        if (!track.contains(mouseX, mouseY)) return false;
        if (thumb.contains(mouseX, mouseY)) {
            guessSoundScrollbarDragOffset = mouseY - thumb.y1;
        } else {
            guessSoundScrollbarDragOffset = thumb.height() / 2;
            updateGuessSoundScrollbarDrag(panel, mouseY);
        }
        guessSoundScrollbarDragging = true;
        return true;
    }

    private void updateGuessSoundScrollbarDrag(Rect panel, int mouseY) {
        GuessSoundsUiLogic.Layout layout = guessSoundsLayout(panel);
        Rect thumb = guessSoundScrollbarThumb(panel, layout, guessSoundPanel.rows().size(),
                guessSoundPanel.scroll());
        if (thumb == null) {
            guessSoundScrollbarDragging = false;
            return;
        }
        Rect track = guessSoundScrollbarTrack(panel, layout);
        int maxScroll = GuessSoundsUiLogic.maxScroll(guessSoundPanel.rows().size(), layout.visibleRows);
        int travel = Math.max(0, track.height() - thumb.height());
        if (travel == 0 || maxScroll <= 0) return;
        int thumbY = Math.max(0, Math.min(travel,
                mouseY - guessSoundScrollbarDragOffset - track.y1));
        guessSoundPanel.setScrollFromScrollbar(
                (int) Math.round((double) thumbY * maxScroll / travel), layout);
    }

    private boolean handleGuessSoundsClick(EditorRenderer renderer, Rect panel, int mouseX, int mouseY) {
        int x = panel.x1 + PANEL_PAD;
        if (new Rect(x, panel.y1 + PANEL_PAD - 2, x + 64,
                panel.y1 + PANEL_PAD + TITLE_HEIGHT + 2).contains(mouseX, mouseY)) {
            guessSoundPanel.close(); return true;
        }
        GuessSoundsUiLogic.Layout layout = guessSoundsLayout(panel);
        if (beginGuessSoundScrollbarDrag(panel, layout, mouseX, mouseY)) return true;
        if (GuessSoundsUiLogic.contains(mouseX, mouseY, layout.searchX, layout.searchY, layout.searchWidth, 16)) {
            guessSoundPanel.beginSearch(); return true;
        }
        guessSoundPanel.finishSearch();
        int tab = GuessSoundsUiLogic.tabAt(layout, mouseX, mouseY);
        if (tab >= 0) { guessSoundPanel.selectTab(tab + 1); return true; }
        if (GuessSoundsUiLogic.contains(mouseX, mouseY, layout.testX, layout.tabsY,
                GuessSoundsUiLogic.TEST_WIDTH, GuessSoundsUiLogic.BUTTON_HEIGHT)) {
            MinecraftClientSoundBridge.preview(guessSoundPanel.points()); return true;
        }
        String choice = null;
        if (GuessSoundsUiLogic.contains(mouseX, mouseY, layout.resetX, layout.tabsY,
                GuessSoundsUiLogic.RESET_WIDTH, GuessSoundsUiLogic.BUTTON_HEIGHT)) choice = GuessSounds.DEFAULT_SOUND;
        else choice = guessSoundPanel.rowAt(mouseX, mouseY, layout);
        if (choice != null) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if (!choice.equals(settings.guessSound(guessSoundPanel.points()))) {
                recordUndo(); settings.setGuessSound(guessSoundPanel.points(), choice); settings.save();
            }
            return true;
        }
        SliderSpec slider = GUESS_SOUND_VOLUME_SLIDER;
        handleSliderRowClick(renderer, slider, layout.searchX, layout.volumeY, mouseX, mouseY);
        return true;
    }

    private void drawGuessSounds(EditorRenderer renderer, Rect panel) {
        MinecraftClientSoundBridge.ensureCatalog();
        drawLanguageGroupSubviewTitle(renderer, panel, "< Labels", "Guess Sounds");
        GuessSoundsUiLogic.Layout layout = guessSoundsLayout(panel);
        for (int tab = 0; tab < 3; tab++) {
            Rect button = new Rect(layout.searchX + tab * GuessSoundsUiLogic.TAB_STEP, layout.tabsY,
                    layout.searchX + tab * GuessSoundsUiLogic.TAB_STEP + GuessSoundsUiLogic.TAB_WIDTH,
                    layout.tabsY + GuessSoundsUiLogic.BUTTON_HEIGHT);
            drawVanillaButton(renderer, button, GuessSoundsUiLogic.tabCaption(tab), 9200 + tab, drawMouseX, drawMouseY);
            if (guessSoundPanel.points() == tab + 1) renderer.fill(button.x1, button.y2, button.x2, button.y2 + 2, PANEL_TEXT);
        }
        drawVanillaButton(renderer, new Rect(layout.testX, layout.tabsY, layout.testX + GuessSoundsUiLogic.TEST_WIDTH,
                layout.tabsY + GuessSoundsUiLogic.BUTTON_HEIGHT), "\u00a7fTest", 9300, drawMouseX, drawMouseY);
        drawVanillaButton(renderer, new Rect(layout.resetX, layout.tabsY, layout.resetX + GuessSoundsUiLogic.RESET_WIDTH,
                layout.tabsY + GuessSoundsUiLogic.BUTTON_HEIGHT), "\u00a7fReset", 9301, drawMouseX, drawMouseY);
        if (guessSoundPanel.isSearchEditing()) guessSoundPanel.positionSearch(layout);
        else {
            renderer.fill(layout.searchX, layout.searchY, layout.searchX + layout.searchWidth, layout.searchY + 16, 0x66000000);
            drawOutline(renderer, new Rect(layout.searchX, layout.searchY, layout.searchX + layout.searchWidth, layout.searchY + 16), PANEL_BORDER);
            String search = guessSoundPanel.search();
            renderer.drawString(trimTextToWidth(renderer, search.isEmpty() ? "\u00a77Search sounds" : "\u00a7f" + search,
                    layout.searchWidth - 8), layout.searchX + 4, layout.searchY + 4, PANEL_TEXT, true);
        }
        guessSoundPanel.clamp(layout);
        List<GuessSoundsUiLogic.Choice> rows = guessSoundPanel.rows();
        renderer.enableScissor(layout.listX, layout.listY, layout.listX + layout.searchWidth, layout.listY + layout.listHeight);
        try {
            for (int row = 0; row < layout.visibleRows && row + guessSoundPanel.scroll() < rows.size(); row++) {
                GuessSoundsUiLogic.Choice choice = rows.get(row + guessSoundPanel.scroll());
                int y = layout.listY + row * GuessSoundsUiLogic.ROW_HEIGHT;
                boolean selected = choice.id.equals(GtbStructuresSettings.INSTANCE.guessSound(guessSoundPanel.points()));
                if (selected || GuessSoundsUiLogic.contains(drawMouseX, drawMouseY, layout.listX, y, layout.listWidth, GuessSoundsUiLogic.ROW_HEIGHT))
                    renderer.fill(layout.listX, y, layout.listX + layout.listWidth, y + GuessSoundsUiLogic.ROW_HEIGHT - 1,
                            selected ? 0x80506A8A : 0x304A5260);
                renderer.drawSoundIcon(choice.id, layout.listX + 2, y + 2);
                int textX = layout.listX + 22, width = Math.max(1, layout.listWidth - 24);
                renderer.drawString(trimTextToWidth(renderer, choice.label, width), textX, y + 2, PANEL_TEXT, true);
                renderer.drawString(trimTextToWidth(renderer, choice.id, width), textX, y + 11, PANEL_MUTED, true);
            }
        } finally { renderer.disableScissor(); }
        Rect track = guessSoundScrollbarTrack(panel, layout);
        Rect thumb = guessSoundScrollbarThumb(panel, layout, rows.size(), guessSoundPanel.scroll());
        if (thumb != null) {
            renderer.fill(track.x1, track.y1, track.x2, track.y2, 0x442A313B);
            renderer.fill(thumb.x1, thumb.y1, thumb.x2, thumb.y2,
                    guessSoundScrollbarDragging ? 0xFFD5DCE5 : PANEL_MUTED);
            drawControlFeedback(renderer, "guess-sound-scrollbar", thumb);
        }
        drawSliderRow(renderer, layout.searchX, layout.volumeY, GUESS_SOUND_VOLUME_SLIDER);
    }

    private void drawShortcutLanguageGroupManager(EditorRenderer renderer, Rect panel) {
        int x = panel.x1 + PANEL_PAD;
        drawLanguageGroupSubviewTitle(renderer, panel, "< Labels", "Language Groups");
        Rect create = new Rect(x, labelsBodyY(panel), x + 96,
                labelsBodyY(panel) + LANGUAGE_GROUP_BUTTON_HEIGHT);
        drawVanillaButton(renderer, create, "+ New Group", 1110, drawMouseX, drawMouseY);
        Rect viewport = new Rect(x, create.y2 + 4, panel.x2 - PANEL_PAD, panel.y2 - PANEL_PAD);
        List<ShortcutLanguageGroups.Group> groups = ShortcutLanguageGroups.groups();
        shortcutGroupManager.clampScrolls(viewport.height(), ROW_HEIGHT * 2, 0, ROW_HEIGHT);
        renderer.enableScissor(viewport.x1, viewport.y1, viewport.x2, viewport.y2);
        try {
            int y = viewport.y1 - shortcutGroupManager.groupScroll();
            if (groups.isEmpty()) {
                renderer.drawString("\u00a77No language groups yet.", x, y + 3,
                        PANEL_MUTED, true);
            }
            for (ShortcutLanguageGroups.Group group : groups) {
                String name = trimTextToWidth(renderer, group.name,
                        Math.max(1, viewport.width() - CHECK_SIZE - 22));
                drawToggleRow(renderer, x, y, name, group.enabled);
                String summary = languageGroupSummary(renderer, group,
                        Math.max(1, viewport.width() - CHILD_ROW_INDENT - 15));
                renderer.drawString("\u00a77" + summary, x + CHILD_ROW_INDENT,
                        toggleTextY(y + ROW_HEIGHT, renderer.fontHeight()), PANEL_MUTED, true);
                renderer.drawString("\u00a77>", panel.x2 - PANEL_PAD - 10,
                        toggleTextY(y, renderer.fontHeight()), PANEL_MUTED, true);
                y += ROW_HEIGHT * 2;
            }
        } finally {
            renderer.disableScissor();
        }
        drawSubviewScrollbar(renderer, viewport, groups.size() * ROW_HEIGHT * 2,
                shortcutGroupManager.groupScroll());
    }

    private int shortcutLanguageGroupViewportHeight(Rect panel) {
        if (shortcutGroupManager.view() == ShortcutLanguageGroupPreviewManager.View.GROUPS) {
            return Math.max(0, panel.y2 - PANEL_PAD
                    - (labelsBodyY(panel) + LANGUAGE_GROUP_BUTTON_HEIGHT + 4));
        }
        int listTop = labelsBodyY(panel) + 10 + 20 + ROW_HEIGHT * 2;
        if (shortcutGroupManager.draftRawTranslation()) listTop += ROW_HEIGHT;
        listTop += 10 + 20 + LANGUAGE_GROUP_BUTTON_HEIGHT + 2;
        int footerY = panel.y2 - PANEL_PAD - LANGUAGE_GROUP_BUTTON_HEIGHT;
        return Math.max(0, footerY - 12 - listTop);
    }

    private void drawShortcutLanguageGroupEditor(EditorRenderer renderer, Rect panel) {
        int x = panel.x1 + PANEL_PAD;
        drawLanguageGroupSubviewTitle(renderer, panel, "< Groups",
                shortcutGroupManager.isNew() ? "New Group" : "Edit Group");
        int y = labelsBodyY(panel);
        renderer.drawString("\u00a77Name", x, y, PANEL_MUTED, true);
        y += 10;
        Rect name = new Rect(x, y, panel.x2 - PANEL_PAD, y + 16);
        drawInactiveTextField(renderer, name);
        if (shortcutGroupManager.inlineField() == ShortcutLanguageGroupPreviewManager.Field.NAME) {
            shortcutGroupManager.positionInlineField(name.x1, name.y1, name.width(), name.height());
        } else {
            String value = trimTextToWidth(renderer, shortcutGroupManager.draftName(),
                    Math.max(1, name.width() - 8));
            renderer.drawString("\u00a7f" + value, x + 4, y + 4, PANEL_TEXT, true);
        }
        y += 20;
        drawToggleRow(renderer, x, y, "Enabled", shortcutGroupManager.draftEnabled());
        y += ROW_HEIGHT;
        drawToggleRow(renderer, x, y, "Raw Translation", shortcutGroupManager.draftRawTranslation());
        y += ROW_HEIGHT;
        if (shortcutGroupManager.draftRawTranslation()) {
            drawToggleRow(renderer, x + CHILD_ROW_INDENT, y, "Toggle All Lowercase",
                    shortcutGroupManager.draftRawTranslationAllLowercase());
            y += ROW_HEIGHT;
        }
        renderer.drawString("\u00a77Search Languages", x, y, PANEL_MUTED, true);
        y += 10;
        Rect search = new Rect(x, y, panel.x2 - PANEL_PAD, y + 16);
        drawInactiveTextField(renderer, search);
        if (shortcutGroupManager.inlineField() == ShortcutLanguageGroupPreviewManager.Field.SEARCH) {
            shortcutGroupManager.positionInlineField(search.x1, search.y1, search.width(), search.height());
        } else {
            String value = shortcutGroupManager.draftSearch();
            String shown = trimTextToWidth(renderer, value.isEmpty() ? "Search..." : value,
                    Math.max(1, search.width() - 8));
            renderer.drawString("\u00a77" + shown, x + 4, y + 4, PANEL_MUTED, true);
        }
        y += 20;
        Rect all = new Rect(x, y, x + 66, y + LANGUAGE_GROUP_BUTTON_HEIGHT);
        Rect none = new Rect(all.x2 + 4, y, all.x2 + 80, y + LANGUAGE_GROUP_BUTTON_HEIGHT);
        drawVanillaButton(renderer, all, "Select All", 1111, drawMouseX, drawMouseY);
        drawVanillaButton(renderer, none, "Select None", 1112, drawMouseX, drawMouseY);
        y += LANGUAGE_GROUP_BUTTON_HEIGHT + 2;
        int footerY = panel.y2 - PANEL_PAD - LANGUAGE_GROUP_BUTTON_HEIGHT;
        Rect viewport = new Rect(x, y, panel.x2 - PANEL_PAD, footerY - 12);
        List<ShortcutLanguageGroupPreviewManager.LanguageOption> options =
                shortcutGroupManager.filteredLanguages();
        shortcutGroupManager.clampScrolls(0, ROW_HEIGHT * 2,
                viewport.height(), ROW_HEIGHT);
        renderer.enableScissor(viewport.x1, viewport.y1, viewport.x2, viewport.y2);
        try {
            int listY = viewport.y1 - shortcutGroupManager.languageScroll();
            for (ShortcutLanguageGroupPreviewManager.LanguageOption option : options) {
                String caption = trimTextToWidth(renderer, option.caption(),
                        Math.max(1, viewport.width() - CHECK_SIZE - 20));
                drawToggleRow(renderer, x, listY, caption, option.selected);
                listY += ROW_HEIGHT;
            }
        } finally {
            renderer.disableScissor();
        }
        drawSubviewScrollbar(renderer, viewport, options.size() * ROW_HEIGHT,
                shortcutGroupManager.languageScroll());
        if (!shortcutGroupManager.validationError().isEmpty()) {
            String notice = CommandGtbRuntime.shortcutLanguageGroupValidationNotice(
                    shortcutGroupManager.validationError(), shortcutGroupManager.validationDetail());
            renderer.drawString(trimTextToWidth(renderer, notice, panel.width() - PANEL_PAD * 2),
                    x, footerY - 11, 0xFFFF8D9A, true);
        }
        if (shortcutGroupManager.deleteConfirmation()) {
            renderer.drawString("\u00a7cDelete this group?", x, footerY + 5, 0xFFFF8D9A, true);
            drawVanillaButton(renderer, new Rect(panel.x2 - 110, footerY, panel.x2 - 56,
                    footerY + LANGUAGE_GROUP_BUTTON_HEIGHT), "Delete", 1116, drawMouseX, drawMouseY);
            drawVanillaButton(renderer, new Rect(panel.x2 - 52, footerY, panel.x2 - PANEL_PAD,
                    footerY + LANGUAGE_GROUP_BUTTON_HEIGHT), "Keep", 1117, drawMouseX, drawMouseY);
        } else {
            if (!shortcutGroupManager.isNew()) {
                drawVanillaButton(renderer, new Rect(x, footerY, x + 54,
                        footerY + LANGUAGE_GROUP_BUTTON_HEIGHT), "Delete", 1113, drawMouseX, drawMouseY);
            }
            drawVanillaButton(renderer, new Rect(panel.x2 - 112, footerY, panel.x2 - 58,
                    footerY + LANGUAGE_GROUP_BUTTON_HEIGHT), "Cancel", 1114, drawMouseX, drawMouseY);
            drawVanillaButton(renderer, new Rect(panel.x2 - 54, footerY, panel.x2 - PANEL_PAD,
                    footerY + LANGUAGE_GROUP_BUTTON_HEIGHT), "Save", 1115, drawMouseX, drawMouseY);
        }
    }

    private boolean handleShortcutLanguageGroupSubviewClick(EditorRenderer renderer, Rect panel,
                                                            int mouseX, int mouseY) {
        shortcutGroupManager.finishInlineField();
        if (beginShortcutSubviewScrollbarDrag(panel, mouseX, mouseY)) return true;
        int x = panel.x1 + PANEL_PAD;
        if (new Rect(x, panel.y1 + PANEL_PAD - 2, x + 64,
                panel.y1 + PANEL_PAD + TITLE_HEIGHT + 2).contains(mouseX, mouseY)) {
            shortcutGroupManager.back();
            return true;
        }
        if (shortcutGroupManager.view() == ShortcutLanguageGroupPreviewManager.View.GROUPS) {
            Rect create = new Rect(x, labelsBodyY(panel), x + 96,
                    labelsBodyY(panel) + LANGUAGE_GROUP_BUTTON_HEIGHT);
            if (create.contains(mouseX, mouseY)) {
                shortcutGroupManager.beginNew();
                return true;
            }
            int y = create.y2 + 4 - shortcutGroupManager.groupScroll();
            int index = 1;
            for (ShortcutLanguageGroups.Group ignored : ShortcutLanguageGroups.groups()) {
                if (new Rect(x, y, x + CHILD_ROW_INDENT, y + ROW_HEIGHT).contains(mouseX, mouseY)
                        && mouseY >= create.y2 + 4 && mouseY < panel.y2 - PANEL_PAD) {
                    toggleShortcutLanguageGroup(index);
                    return true;
                }
                if (new Rect(x + CHILD_ROW_INDENT, y, panel.x2 - PANEL_PAD,
                        y + ROW_HEIGHT * 2).contains(mouseX, mouseY)
                        && mouseY >= create.y2 + 4 && mouseY < panel.y2 - PANEL_PAD) {
                    shortcutGroupManager.beginEdit(index);
                    return true;
                }
                y += ROW_HEIGHT * 2;
                index++;
            }
            return true;
        }
        int y = labelsBodyY(panel) + 10;
        Rect name = new Rect(x, y, panel.x2 - PANEL_PAD, y + 16);
        if (name.contains(mouseX, mouseY)) {
            shortcutGroupManager.beginInlineField(ShortcutLanguageGroupPreviewManager.Field.NAME);
            return true;
        }
        y += 20;
        if (toggleHit(renderer, x, y, "Enabled", mouseX, mouseY)) {
            shortcutGroupManager.toggleDraftEnabled();
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(renderer, x, y, "Raw Translation", mouseX, mouseY)) {
            shortcutGroupManager.toggleDraftRawTranslation();
            return true;
        }
        y += ROW_HEIGHT;
        if (shortcutGroupManager.draftRawTranslation()) {
            if (toggleHit(renderer, x + CHILD_ROW_INDENT, y, "Toggle All Lowercase", mouseX, mouseY)) {
                shortcutGroupManager.toggleDraftRawTranslationAllLowercase();
                return true;
            }
            y += ROW_HEIGHT;
        }
        y += 10;
        Rect search = new Rect(x, y, panel.x2 - PANEL_PAD, y + 16);
        if (search.contains(mouseX, mouseY)) {
            shortcutGroupManager.beginInlineField(ShortcutLanguageGroupPreviewManager.Field.SEARCH);
            return true;
        }
        y += 20;
        Rect all = new Rect(x, y, x + 66, y + LANGUAGE_GROUP_BUTTON_HEIGHT);
        Rect none = new Rect(all.x2 + 4, y, all.x2 + 80, y + LANGUAGE_GROUP_BUTTON_HEIGHT);
        if (all.contains(mouseX, mouseY)) {
            shortcutGroupManager.selectAll();
            return true;
        }
        if (none.contains(mouseX, mouseY)) {
            shortcutGroupManager.selectNone();
            return true;
        }
        y += LANGUAGE_GROUP_BUTTON_HEIGHT + 2;
        int languageListTop = y;
        int footerY = panel.y2 - PANEL_PAD - LANGUAGE_GROUP_BUTTON_HEIGHT;
        for (ShortcutLanguageGroupPreviewManager.LanguageOption option
                : shortcutGroupManager.filteredLanguages()) {
            int rowY = y - shortcutGroupManager.languageScroll();
            if (toggleHit(renderer, x, rowY, option.caption(), mouseX, mouseY)
                    && mouseY >= languageListTop && mouseY < footerY - 12) {
                shortcutGroupManager.toggleLanguage(option.code);
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (shortcutGroupManager.deleteConfirmation()) {
            if (new Rect(panel.x2 - 110, footerY, panel.x2 - 56,
                    footerY + LANGUAGE_GROUP_BUTTON_HEIGHT).contains(mouseX, mouseY)) {
                deleteShortcutLanguageGroupDraft();
                return true;
            }
            if (new Rect(panel.x2 - 52, footerY, panel.x2 - PANEL_PAD,
                    footerY + LANGUAGE_GROUP_BUTTON_HEIGHT).contains(mouseX, mouseY)) {
                shortcutGroupManager.cancelDelete();
                return true;
            }
        } else {
            if (!shortcutGroupManager.isNew()
                    && new Rect(x, footerY, x + 54, footerY + LANGUAGE_GROUP_BUTTON_HEIGHT)
                    .contains(mouseX, mouseY)) {
                shortcutGroupManager.requestDelete();
                return true;
            }
            if (new Rect(panel.x2 - 112, footerY, panel.x2 - 58,
                    footerY + LANGUAGE_GROUP_BUTTON_HEIGHT).contains(mouseX, mouseY)) {
                shortcutGroupManager.openManager();
                return true;
            }
            if (new Rect(panel.x2 - 54, footerY, panel.x2 - PANEL_PAD,
                    footerY + LANGUAGE_GROUP_BUTTON_HEIGHT).contains(mouseX, mouseY)) {
                saveShortcutLanguageGroupDraft();
                return true;
            }
        }
        return true;
    }

    private void toggleShortcutLanguageGroup(int index) {
        ConfigSnapshot before = ConfigSnapshot.capture();
        ShortcutLanguageGroups.Result result = shortcutGroupManager.toggleGroup(index);
        if (result.success && !before.matchesCurrent()) {
            recordUndo(before);
            GtbStructuresSettings.INSTANCE.save();
        }
    }

    private void saveShortcutLanguageGroupDraft() {
        ConfigSnapshot before = ConfigSnapshot.capture();
        ShortcutLanguageGroups.Result result = shortcutGroupManager.saveDraft();
        if (result.success && !before.matchesCurrent()) {
            recordUndo(before);
            GtbStructuresSettings.INSTANCE.save();
        } else if (!result.success) {
            queueLanguageGroupValidation(result);
        }
    }

    private void deleteShortcutLanguageGroupDraft() {
        ConfigSnapshot before = ConfigSnapshot.capture();
        ShortcutLanguageGroups.Result result = shortcutGroupManager.deleteDraft();
        if (result.success && !before.matchesCurrent()) {
            recordUndo(before);
            GtbStructuresSettings.INSTANCE.save();
        } else if (!result.success) {
            queueLanguageGroupValidation(result);
        }
    }

    private static void queueLanguageGroupValidation(ShortcutLanguageGroups.Result result) {
        GameStats.INSTANCE.queueNotificationMessage(
                CommandGtbRuntime.shortcutLanguageGroupValidationNotice(result.error, result.detail));
    }

    private static Rect languageGroupManagerLinkRect(Rect panel, int y) {
        return new Rect(panel.x1 + PANEL_PAD + CHILD_ROW_INDENT * 2, y,
                panel.x2 - PANEL_PAD, y + ROW_HEIGHT);
    }

    private static void drawLanguageGroupSubviewTitle(EditorRenderer renderer, Rect panel,
                                                       String back, String title) {
        int y = panel.y1 + PANEL_PAD;
        renderer.drawString("\u00a7f\u00a7n" + back, panel.x1 + PANEL_PAD, y,
                PANEL_TEXT, true);
        int titleX = panel.x1 + (panel.width() - renderer.textWidth(title)) / 2;
        renderer.drawString("\u00a7f" + title, titleX, y, PANEL_TEXT, true);
    }

    private static void drawInactiveTextField(EditorRenderer renderer, Rect rect) {
        renderer.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0xFF000000);
        renderer.fill(rect.x1 + 1, rect.y1 + 1, rect.x2 - 1, rect.y2 - 1, 0xFF202020);
        drawOutline(renderer, rect, 0xFF9A9A9A);
    }

    private void drawSubviewScrollbar(EditorRenderer renderer, Rect viewport,
                                      int contentHeight, int offset) {
        Rect track = subviewScrollbarTrack(viewport);
        Rect thumb = subviewScrollbarThumb(viewport, contentHeight, offset);
        if (thumb == null) return;
        renderer.fill(track.x1, track.y1, track.x2, track.y2, 0x442A313B);
        renderer.fill(thumb.x1, thumb.y1, thumb.x2, thumb.y2,
                shortcutScrollbarDragging ? 0xFFD5DCE5 : 0xFF9EA8B5);
        drawControlFeedback(renderer, "shortcut-scrollbar", thumb);
    }

    private static Rect subviewScrollbarTrack(Rect viewport) {
        return new Rect(viewport.x2 - LABELS_SCROLLBAR_WIDTH, viewport.y1,
                viewport.x2, viewport.y2);
    }

    private static Rect subviewScrollbarThumb(Rect viewport, int contentHeight, int offset) {
        int maxScroll = Math.max(0, contentHeight - viewport.height());
        if (maxScroll <= 0 || viewport.height() <= 0) return null;
        int thumbHeight = Math.max(LABELS_SCROLLBAR_MIN_THUMB,
                viewport.height() * viewport.height() / Math.max(1, contentHeight));
        thumbHeight = Math.min(viewport.height(), thumbHeight);
        int travel = viewport.height() - thumbHeight;
        int thumbY = travel <= 0 ? 0
                : (int) Math.round((double) Math.max(0, Math.min(maxScroll, offset))
                * travel / maxScroll);
        return new Rect(viewport.x2 - LABELS_SCROLLBAR_WIDTH, viewport.y1 + thumbY,
                viewport.x2, viewport.y1 + thumbY + thumbHeight);
    }

    private Rect shortcutSubviewViewport(Rect panel) {
        int x = panel.x1 + PANEL_PAD;
        if (shortcutGroupManager.view() == ShortcutLanguageGroupPreviewManager.View.GROUPS) {
            int top = labelsBodyY(panel) + LANGUAGE_GROUP_BUTTON_HEIGHT + 4;
            return new Rect(x, top, panel.x2 - PANEL_PAD, panel.y2 - PANEL_PAD);
        }
        int listTop = labelsBodyY(panel) + 10 + 20 + ROW_HEIGHT * 2;
        if (shortcutGroupManager.draftRawTranslation()) listTop += ROW_HEIGHT;
        listTop += 10 + 20 + LANGUAGE_GROUP_BUTTON_HEIGHT + 2;
        int footerY = panel.y2 - PANEL_PAD - LANGUAGE_GROUP_BUTTON_HEIGHT;
        return new Rect(x, listTop, panel.x2 - PANEL_PAD, footerY - 12);
    }

    private int shortcutSubviewContentHeight() {
        if (shortcutGroupManager.view() == ShortcutLanguageGroupPreviewManager.View.GROUPS) {
            return ShortcutLanguageGroups.groups().size() * ROW_HEIGHT * 2;
        }
        return shortcutGroupManager.filteredLanguages().size() * ROW_HEIGHT;
    }

    private boolean beginShortcutSubviewScrollbarDrag(Rect panel, int mouseX, int mouseY) {
        Rect viewport = shortcutSubviewViewport(panel);
        Rect thumb = subviewScrollbarThumb(viewport, shortcutSubviewContentHeight(),
                shortcutGroupManager.view() == ShortcutLanguageGroupPreviewManager.View.GROUPS
                        ? shortcutGroupManager.groupScroll() : shortcutGroupManager.languageScroll());
        if (thumb == null || !subviewScrollbarTrack(viewport).contains(mouseX, mouseY)) return false;
        if (thumb.contains(mouseX, mouseY)) shortcutScrollbarDragOffset = mouseY - thumb.y1;
        else {
            shortcutScrollbarDragOffset = thumb.height() / 2;
            updateShortcutScrollbarDrag(panel, mouseY);
        }
        shortcutScrollbarDragging = true;
        return true;
    }

    private void updateShortcutScrollbarDrag(Rect panel, int mouseY) {
        Rect viewport = shortcutSubviewViewport(panel);
        boolean groups = shortcutGroupManager.view() == ShortcutLanguageGroupPreviewManager.View.GROUPS;
        int offset = groups ? shortcutGroupManager.groupScroll() : shortcutGroupManager.languageScroll();
        Rect thumb = subviewScrollbarThumb(viewport, shortcutSubviewContentHeight(), offset);
        if (thumb == null) {
            shortcutScrollbarDragging = false;
            return;
        }
        Rect track = subviewScrollbarTrack(viewport);
        int maxScroll = Math.max(0, shortcutSubviewContentHeight() - viewport.height());
        int travel = Math.max(0, track.height() - thumb.height());
        if (travel == 0 || maxScroll <= 0) return;
        int thumbY = Math.max(0, Math.min(travel,
                mouseY - shortcutScrollbarDragOffset - track.y1));
        shortcutGroupManager.setScrollFromScrollbar(
                (int) Math.round((double) thumbY * maxScroll / travel),
                viewport.height(), ROW_HEIGHT);
    }

    private static String languageGroupSummary(EditorRenderer renderer,
                                               ShortcutLanguageGroups.Group group,
                                               int maxWidth) {
        StringBuilder value = new StringBuilder();
        for (String code : group.languages) {
            if (value.length() > 0) value.append(", ");
            value.append(ShortcutLanguageGroups.isSupportedLanguage(code)
                    ? ShortcutLanguageGroups.languageDisplayName(code) : "Unavailable (" + code + ")");
        }
        String full = value.toString();
        return renderer.textWidth(full) <= maxWidth
                ? full : group.languages.size() + " languages";
    }

    private static String trimTextToWidth(EditorRenderer renderer, String value, int maxWidth) {
        String text = value == null ? "" : value;
        if (renderer.textWidth(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = renderer.textWidth(suffix);
        while (!text.isEmpty() && renderer.textWidth(text) + suffixWidth > maxWidth) {
            int codePoints = text.codePointCount(0, text.length());
            text = text.substring(0, text.offsetByCodePoints(0, codePoints - 1));
        }
        return text + suffix;
    }

    private static int drawMenuSection(EditorRenderer renderer, int x, int y, String text) {
        y += SECTION_MARGIN_Y;
        renderer.drawString("\u00a77" + text, x, y, PANEL_MUTED, true);
        return y + renderer.fontHeight() + SECTION_MARGIN_Y + 1;
    }

    private static void drawToggleRow(EditorRenderer renderer, int x, int y, String text, boolean selected) {
        drawToggleRow(renderer, x, y, text, selected, false, false, false);
    }

    private void drawHeaderRow(EditorRenderer renderer, Rect panel, int x, int y) {
        int textY = y + (HEADER_ROW_HEIGHT - renderer.fontHeight()) / 2;
        int valueX = headerValueX(renderer, x);
        if (headerEditing && !autoRequeueDelayEditing && !sliderValueEditing) {
            Rect field = headerInputRect(renderer, panel);
            PreviewScreenController.positionHeaderEditor(field.x1, field.y1,
                    field.width(), field.height());
            return;
        }
        renderer.drawString(ChatHeader.rendered() + "\u00a7r", valueX, textY, PANEL_TEXT, true);
        Rect reset = headerResetRect(renderer, x, y);
        boolean resetHovered = reset.contains(drawMouseX, drawMouseY);
        renderer.drawString(resetHovered ? "\u00a7f\u00a7nReset" : "\u00a77Reset",
                reset.x1, textY, resetHovered ? PANEL_TEXT : PANEL_MUTED, true);
    }

    private void drawAutoRequeueDelayRow(EditorRenderer renderer, Rect panel, int x, int y) {
        int textY = toggleTextY(y, renderer.fontHeight());
        renderer.drawString(AUTO_REQUEUE_DELAY_LABEL, x, textY, PANEL_TEXT, true);
        if (autoRequeueDelayEditing) {
            Rect field = autoRequeueDelayInputRect(renderer, panel);
            PreviewScreenController.positionHeaderEditor(field.x1, field.y1,
                    field.width(), field.height());
            return;
        }
        Rect value = autoRequeueDelayValueRect(renderer, x, y);
        renderer.drawString("\u00a77" + GtbStructuresSettings.formatAutoRequeueDelaySeconds(
                        GtbStructuresSettings.INSTANCE.autoRequeueDelaySeconds) + "s",
                value.x1, textY, PANEL_MUTED, true);
    }

    private boolean handleAutoRequeueDelayRowClick(EditorRenderer renderer, Rect panel, int x, int y,
                                                    int mouseX, int mouseY) {
        Rect value = autoRequeueDelayValueRect(renderer, x, y);
        if (!value.contains(mouseX, mouseY)) return false;
        if (autoRequeueDelayEditing) return true;
        headerEditing = true;
        autoRequeueDelayEditing = true;
        PreviewScreenController.beginHeaderEditing(GtbStructuresSettings.formatAutoRequeueDelaySeconds(
                GtbStructuresSettings.INSTANCE.autoRequeueDelaySeconds));
        return true;
    }

    private static Rect autoRequeueDelayValueRect(EditorRenderer renderer, int x, int y) {
        int left = x + renderer.textWidth(AUTO_REQUEUE_DELAY_LABEL) + 5;
        return new Rect(left, y - 1, left + AUTO_REQUEUE_DELAY_EDITOR_WIDTH, y + ROW_HEIGHT + 1);
    }

    private void commitAutoRequeueDelay(String requested) {
        if (requested == null) return;
        Float delay = GtbStructuresSettings.parseAutoRequeueDelaySeconds(requested);
        if (delay == null) {
            GameStats.INSTANCE.queueNotificationMessage(CommandGtb.head
                    + "Please enter a time from 0.0 to 10.0 seconds.");
            return;
        }
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (Float.compare(settings.autoRequeueDelaySeconds, delay.floatValue()) == 0) return;
        ConfigSnapshot before = ConfigSnapshot.capture();
        settings.autoRequeueDelaySeconds = delay.floatValue();
        GameStats.INSTANCE.setAutoRequeueDelaySeconds(settings.autoRequeueDelaySeconds);
        recordUndo(before);
        settings.save();
    }

    private static void drawToggleRow(EditorRenderer renderer, int x, int y, String text,
                                      boolean selected, boolean multi, boolean showBackground) {
        drawToggleRow(renderer, x, y, text, selected, multi, showBackground, false);
    }

    private static void drawToggleRow(EditorRenderer renderer, int x, int y, String text,
                                      boolean selected, boolean multi,
                                      boolean showBackground, boolean backgroundSelected) {
        int checkY = toggleCheckY(y, renderer.fontHeight());
        int textY = toggleTextY(y, renderer.fontHeight());
        drawCheckBox(renderer, x, checkY, selected, multi);
        renderer.drawString((selected ? "\u00a7f" : "\u00a77") + text,
                x + CHECK_SIZE + 5, textY, selected ? PANEL_TEXT : PANEL_MUTED, true);
        if (showBackground) {
            int bgX = x + BACKGROUND_COLUMN_OFFSET;
            drawCheckBox(renderer, bgX, checkY, backgroundSelected, false);
            renderer.drawString((backgroundSelected ? "\u00a7f" : "\u00a77") + "Background",
                    bgX + CHECK_SIZE + 5, textY, backgroundSelected ? PANEL_TEXT : PANEL_MUTED, true);
        }
    }

    private void drawSettingsPanel(EditorRenderer renderer, int screenWidth, int screenHeight) {
        Label label = activePanelLabel();
        if (label == null) {
            clearActivePanel();
            return;
        }
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        Rect panel = settingsPanelRect(label, screenWidth, screenHeight);
        drawPanel(renderer, panel);
        drawPanelCloseButton(renderer, panel);
        int x = panel.x1 + PANEL_PAD;
        int y = panel.y1 + PANEL_PAD;
        renderer.drawString("\u00a7f\u00a7n" + settingsTitle(label), x, y, PANEL_TEXT, true);
        y += TITLE_HEIGHT + 5;

        String key = label.target.key;
        if ("scoreboard".equals(key)) {
            drawSection(renderer, x, y, "Skin");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Vanilla", settings.sbConfig.isSkin("vanilla"));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Default", settings.sbConfig.isSkin("default"));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Hypixel", settings.sbConfig.isSkin("hypixel"));
            if (!scoreboardStyleOptionsVisible(settings.sbConfig.skin)) return;
            y += ROW_HEIGHT + 2;
            drawSection(renderer, x, y, "Visuals");
            y += ROW_HEIGHT;
            drawSliderRow(renderer, x, y, "Background Opacity", settings.sbConfig.backgroundOpacity);
            y += SLIDER_ROW_HEIGHT;
            drawSliderRow(renderer, x, y, "Highlight Intensity", settings.sbConfig.highlightIntensity);
            y += SLIDER_ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Text Shadow", settings.sbConfig.textShadow);
            y += ROW_HEIGHT + 2;
            drawSection(renderer, x, y, "Details");
            y += ROW_HEIGHT;
            drawTriStateRow(renderer, x, y, "Show Points Gained In Round",
                    settings.sbConfig.showPointsGainedInRound);
            y += ROW_HEIGHT;
            drawTriStateRow(renderer, x, y, "Show Places", settings.sbConfig.showPlaces);
            y += ROW_HEIGHT;
            drawTriStateRow(renderer, x, y, "Show Titles", settings.sbConfig.showTitles);
            y += ROW_HEIGHT;
            drawTriStateRow(renderer, x, y, "Show Emblems", settings.sbConfig.showEmblems);
            y += ROW_HEIGHT;
            if (scoreboardDefaultBuilderOptionVisible(settings.sbConfig.skin)) {
                drawToggleRow(renderer, x, y, "Explicitly Show Builders",
                        settings.sbConfig.defaultExplicitlyShowBuilders);
                y += ROW_HEIGHT;
            } else if (scoreboardHypixelRoundsOptionVisible(settings.sbConfig.skin)) {
                drawTriStateRow(renderer, x, y, "Show Rounds", settings.sbConfig.hypixelShowRounds);
                y += ROW_HEIGHT;
            }
            drawToggleRow(renderer, x, y, "Place Left Players at Bottom",
                    settings.sbConfig.leftPlayersAtBottom);
        } else if ("hint".equals(key)) {
            drawSliderRow(renderer, x, y, HINT_SCALE_SLIDER);
            y += SLIDER_ROW_HEIGHT;
            drawSection(renderer, x, y, "Background");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "On", settings.hintStructureBackground);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Off", !settings.hintStructureBackground);
            y += ROW_HEIGHT + 2;
            drawSection(renderer, x, y, "Color Target");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Brackets", "bracket".equals(hintColorTarget));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Numbers", "number".equals(hintColorTarget));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Dashes", "hyphen".equals(hintColorTarget));
            y += ROW_HEIGHT + 3;
            drawColorSwatches(renderer, x, y, currentHintColor());
        } else if ("actionbar".equals(key)) {
            drawSection(renderer, x, y, "Background");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "On", settings.hintBackgroundOverlay);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Off", !settings.hintBackgroundOverlay);
        } else if ("cooldown".equals(key)) {
            drawSliderRow(renderer, x, y, COOLDOWN_SCALE_SLIDER);
            y += SLIDER_ROW_HEIGHT;
            drawSection(renderer, x, y, "Shown Parts");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Timer", settings.chatCooldownTimerEnabled, true,
                    settings.chatCooldownTimerEnabled, settings.chatCooldownBackground);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Bar", settings.chatCooldownBarEnabled, true,
                    settings.chatCooldownBarEnabled, settings.chatCooldownBarBackground);
            y += ROW_HEIGHT + 2;
            drawSection(renderer, x, y, "Color Target");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Timer", "timer".equals(cooldownColorTarget));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Bar", "bar".equals(cooldownColorTarget));
            y += ROW_HEIGHT + 3;
            drawColorSwatches(renderer, x, y, currentCooldownColor());
        } else if ("handheld".equals(key)) {
            drawSliderRow(renderer, x, y, HANDHELD_SCALE_SLIDER);
            y += SLIDER_ROW_HEIGHT;
            drawSection(renderer, x, y, "Visible Rows");
            y += ROW_HEIGHT;
            drawStepper(renderer, x, y, settings.handheldItemMaxVisible);
            y += STEPPER_BUTTON_SIZE + 4;
            drawSection(renderer, x, y, "Background");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "On", settings.handheldItemBackground);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Off", !settings.handheldItemBackground);
            y += ROW_HEIGHT + 2;
            drawSection(renderer, x, y, "Arrangement");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Vertical", settings.handheldItemVerticalLayout);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Horizontal", !settings.handheldItemVerticalLayout);
            y += ROW_HEIGHT + 2;
            if (settings.handheldItemVerticalLayout) {
                drawSection(renderer, x, y, "Newest Item");
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Newest At Top",
                        !settings.handheldItemVerticalNewestAtBottom);
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Newest At Bottom",
                        settings.handheldItemVerticalNewestAtBottom);
                y += ROW_HEIGHT + 2;
                drawSection(renderer, x, y, "Captions");
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Item Name", settings.handheldItemCaptionName);
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Item ID", settings.handheldItemCaptionId);
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Metadata", settings.handheldItemCaptionMetadata);
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Spawn Egg Mob Name", settings.handheldItemCaptionSpawnEggMob);
                y += ROW_HEIGHT + 2;
            } else {
                drawSection(renderer, x, y, "Newest Item");
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Newest At Left",
                        !settings.handheldItemHorizontalNewestAtRight);
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Newest At Right",
                        settings.handheldItemHorizontalNewestAtRight);
                y += ROW_HEIGHT + 2;
                drawSection(renderer, x, y, "Vertical Position");
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Newest At Top",
                        !settings.handheldItemHorizontalNewestAtBottom);
                y += ROW_HEIGHT;
                drawToggleRow(renderer, x, y, "Newest At Bottom",
                        settings.handheldItemHorizontalNewestAtBottom);
                y += ROW_HEIGHT + 2;
            }
            drawSection(renderer, x, y, "Spawn Egg Skin");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Classic", "classic".equals(settings.spawnEggSkin));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "New", "new".equals(settings.spawnEggSkin));
        } else if (key.startsWith("environment")) {
            drawSliderRow(renderer, x, y, ENVIRONMENT_SCALE_SLIDER);
            y += SLIDER_ROW_HEIGHT;
            drawSection(renderer, x, y, "Shown Labels");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Weather", environmentRowVisible("weather"), true, false);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Biome", environmentRowVisible("biome"), true, false);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Time", environmentRowVisible("time"), true, false);
            y += ROW_HEIGHT + 2;
            drawSection(renderer, x, y, "Notify Changes In Chat");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Weather", environmentChatNoticeEnabled("weather"), true, false);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Biome", environmentChatNoticeEnabled("biome"), true, false);
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Time", environmentChatNoticeEnabled("time"), true, false);
            y += ROW_HEIGHT + 2;
            drawSection(renderer, x, y, "Time Item");
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Clock", "clock".equals(settings.environmentTimeIconPreset));
            y += ROW_HEIGHT;
            drawToggleRow(renderer, x, y, "Colored Blocks", "blocks".equals(settings.environmentTimeIconPreset));
        }
    }

    private boolean handleSettingsPanelClick(EditorRenderer renderer, int screenWidth, int screenHeight,
                                             int mouseX, int mouseY) {
        Label label = activePanelLabel();
        if (label == null) {
            clearActivePanel();
            return false;
        }
        Rect panel = settingsPanelRect(label, screenWidth, screenHeight);
        if (!panel.contains(mouseX, mouseY)) return false;
        if (panelCloseRect(panel).contains(mouseX, mouseY)) {
            if (headerEditing) finishHeaderEditing();
            clearActivePanel();
            return true;
        }

        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        int x = panel.x1 + PANEL_PAD;
        int y = panel.y1 + PANEL_PAD + TITLE_HEIGHT + 5;
        String key = label.target.key;
        if ("scoreboard".equals(key)) {
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Vanilla", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.setSkin("vanilla");
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Default", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.setSkin("default");
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Hypixel", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.setSkin("hypixel");
                settings.save();
                return true;
            }
            if (!scoreboardStyleOptionsVisible(settings.sbConfig.skin)) return true;
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (sliderRowRect(x, y).contains(mouseX, mouseY)) {
                beginScoreboardSliderDrag("backgroundOpacity", sliderTrackRect(x, y).x1, mouseX);
                return true;
            }
            y += SLIDER_ROW_HEIGHT;
            if (sliderRowRect(x, y).contains(mouseX, mouseY)) {
                beginScoreboardSliderDrag("highlightIntensity", sliderTrackRect(x, y).x1, mouseX);
                return true;
            }
            y += SLIDER_ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Text Shadow", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.textShadow = !settings.sbConfig.textShadow;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (triStateHit(renderer, x, y, "Show Points Gained In Round", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.showPointsGainedInRound =
                        ScoreboardConfig.cycleOption(settings.sbConfig.showPointsGainedInRound);
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (triStateHit(renderer, x, y, "Show Places", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.showPlaces = ScoreboardConfig.cycleOption(settings.sbConfig.showPlaces);
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (triStateHit(renderer, x, y, "Show Titles", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.showTitles = ScoreboardConfig.cycleOption(settings.sbConfig.showTitles);
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (triStateHit(renderer, x, y, "Show Emblems", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.showEmblems = ScoreboardConfig.cycleOption(settings.sbConfig.showEmblems);
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (scoreboardDefaultBuilderOptionVisible(settings.sbConfig.skin)) {
                if (toggleHit(renderer, x, y, "Explicitly Show Builders", mouseX, mouseY)) {
                    recordUndo();
                    settings.sbConfig.defaultExplicitlyShowBuilders =
                            !settings.sbConfig.defaultExplicitlyShowBuilders;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
            } else if (scoreboardHypixelRoundsOptionVisible(settings.sbConfig.skin)) {
                if (triStateHit(renderer, x, y, "Show Rounds", mouseX, mouseY)) {
                    recordUndo();
                    settings.sbConfig.hypixelShowRounds =
                            ScoreboardConfig.cycleOption(settings.sbConfig.hypixelShowRounds);
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
            }
            if (toggleHit(renderer, x, y, "Place Left Players at Bottom", mouseX, mouseY)) {
                recordUndo();
                settings.sbConfig.leftPlayersAtBottom = !settings.sbConfig.leftPlayersAtBottom;
                settings.save();
                return true;
            }
        } else if ("hint".equals(key)) {
            if (handleSliderRowClick(renderer, HINT_SCALE_SLIDER, x, y, mouseX, mouseY)) return true;
            y += SLIDER_ROW_HEIGHT;
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "On", mouseX, mouseY)) {
                recordUndo();
                settings.hintStructureBackground = true;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Off", mouseX, mouseY)) {
                recordUndo();
                settings.hintStructureBackground = false;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Brackets", mouseX, mouseY)) {
                hintColorTarget = "bracket";
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Numbers", mouseX, mouseY)) {
                hintColorTarget = "number";
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Dashes", mouseX, mouseY)) {
                hintColorTarget = "hyphen";
                return true;
            }
            y += ROW_HEIGHT + 3;
            String color = colorAt(panel.x1 + PANEL_PAD, y, mouseX, mouseY);
            if (color != null) {
                recordUndo();
                setHintColorTarget(color);
                settings.save();
                return true;
            }
        } else if ("actionbar".equals(key)) {
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "On", mouseX, mouseY)) {
                recordUndo();
                settings.hintBackgroundOverlay = true;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Off", mouseX, mouseY)) {
                recordUndo();
                settings.hintBackgroundOverlay = false;
                settings.save();
                return true;
            }
        } else if ("cooldown".equals(key)) {
            if (handleSliderRowClick(renderer, COOLDOWN_SCALE_SLIDER, x, y, mouseX, mouseY)) return true;
            y += SLIDER_ROW_HEIGHT;
            y += ROW_HEIGHT;
            boolean timerBackground = settings.chatCooldownTimerEnabled
                    && toggleHit(renderer, x + BACKGROUND_COLUMN_OFFSET, y, "Background", mouseX, mouseY);
            if (toggleHit(renderer, x, y, "Timer", mouseX, mouseY) || timerBackground) {
                recordUndo();
                if (timerBackground) {
                    settings.chatCooldownBackground = !settings.chatCooldownBackground;
                } else {
                    applyCooldownState(toggleCooldownPartState(settings.chatCooldownCounter,
                            settings.chatCooldownTimerEnabled, settings.chatCooldownBarEnabled, true));
                }
                settings.save();
                if (!settings.chatCooldownCounter) clearPanelForTarget("cooldown");
                return true;
            }
            y += ROW_HEIGHT;
            boolean barBackground = settings.chatCooldownBarEnabled
                    && toggleHit(renderer, x + BACKGROUND_COLUMN_OFFSET, y, "Background", mouseX, mouseY);
            if (toggleHit(renderer, x, y, "Bar", mouseX, mouseY) || barBackground) {
                recordUndo();
                if (barBackground) {
                    settings.chatCooldownBarBackground = !settings.chatCooldownBarBackground;
                } else {
                    applyCooldownState(toggleCooldownPartState(settings.chatCooldownCounter,
                            settings.chatCooldownTimerEnabled, settings.chatCooldownBarEnabled, false));
                }
                settings.save();
                if (!settings.chatCooldownCounter) clearPanelForTarget("cooldown");
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Timer", mouseX, mouseY)) {
                cooldownColorTarget = "timer";
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Bar", mouseX, mouseY)) {
                cooldownColorTarget = "bar";
                return true;
            }
            y += ROW_HEIGHT + 3;
            String color = colorAt(panel.x1 + PANEL_PAD, y, mouseX, mouseY);
            if (color != null) {
                recordUndo();
                setCooldownColorTarget(color);
                settings.save();
                return true;
            }
        } else if ("handheld".equals(key)) {
            if (handleSliderRowClick(renderer, HANDHELD_SCALE_SLIDER, x, y, mouseX, mouseY)) return true;
            y += SLIDER_ROW_HEIGHT;
            y += ROW_HEIGHT;
            if (stepperRowRect(panel, y).contains(mouseX, mouseY)) {
                if (stepperMinusRect(x, y).contains(mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemMaxVisible = Math.max(2, settings.handheldItemMaxVisible - 1);
                    settings.save();
                } else if (stepperPlusRect(x, y).contains(mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemMaxVisible = Math.min(10, settings.handheldItemMaxVisible + 1);
                    settings.save();
                }
                return true;
            }
            y += STEPPER_BUTTON_SIZE + 4 + ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "On", mouseX, mouseY)) {
                recordUndo();
                settings.handheldItemBackground = true;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Off", mouseX, mouseY)) {
                recordUndo();
                settings.handheldItemBackground = false;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Vertical", mouseX, mouseY)) {
                recordUndo();
                settings.handheldItemVerticalLayout = true;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Horizontal", mouseX, mouseY)) {
                recordUndo();
                settings.handheldItemVerticalLayout = false;
                settings.save();
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (settings.handheldItemVerticalLayout) {
                if (toggleHit(renderer, x, y, "Newest At Top", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemVerticalNewestAtBottom = false;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, x, y, "Newest At Bottom", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemVerticalNewestAtBottom = true;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT + 2 + ROW_HEIGHT;
                if (toggleHit(renderer, x, y, "Item Name", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemCaptionName = !settings.handheldItemCaptionName;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, x, y, "Item ID", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemCaptionId = !settings.handheldItemCaptionId;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, x, y, "Metadata", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemCaptionMetadata = !settings.handheldItemCaptionMetadata;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, x, y, "Spawn Egg Mob Name", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemCaptionSpawnEggMob = !settings.handheldItemCaptionSpawnEggMob;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            } else {
                if (toggleHit(renderer, x, y, "Newest At Left", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemHorizontalNewestAtRight = false;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, x, y, "Newest At Right", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemHorizontalNewestAtRight = true;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT + 2 + ROW_HEIGHT;
                if (toggleHit(renderer, x, y, "Newest At Top", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemHorizontalNewestAtBottom = false;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(renderer, x, y, "Newest At Bottom", mouseX, mouseY)) {
                    recordUndo();
                    settings.handheldItemHorizontalNewestAtBottom = true;
                    settings.save();
                    return true;
                }
                y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            }
            if (toggleHit(renderer, x, y, "Classic", mouseX, mouseY)) {
                recordUndo();
                settings.spawnEggSkin = "classic";
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "New", mouseX, mouseY)) {
                recordUndo();
                settings.spawnEggSkin = "new";
                settings.save();
                return true;
            }
        } else if (key.startsWith("environment")) {
            if (handleSliderRowClick(renderer, ENVIRONMENT_SCALE_SLIDER, x, y, mouseX, mouseY)) return true;
            y += SLIDER_ROW_HEIGHT;
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Weather", mouseX, mouseY)) {
                toggleEnvironmentRow("weather");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Biome", mouseX, mouseY)) {
                toggleEnvironmentRow("biome");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Time", mouseX, mouseY)) {
                toggleEnvironmentRow("time");
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Weather", mouseX, mouseY)) {
                toggleEnvironmentChatNotice("weather");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Biome", mouseX, mouseY)) {
                toggleEnvironmentChatNotice("biome");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Time", mouseX, mouseY)) {
                toggleEnvironmentChatNotice("time");
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Clock", mouseX, mouseY)) {
                recordUndo();
                settings.environmentTimeIconPreset = "clock";
                settings.save();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(renderer, x, y, "Colored Blocks", mouseX, mouseY)) {
                recordUndo();
                settings.environmentTimeIconPreset = "blocks";
                settings.save();
                return true;
            }
        }
        return true;
    }

    private void beginScoreboardSliderDrag(String slider, int trackX, int mouseX) {
        activeSlider = slider == null ? "" : slider;
        activeSliderX = trackX;
        sliderUndoSnapshot = ConfigSnapshot.capture();
        sliderDirty = false;
        SliderSpec spec = sliderSpec(activeSlider);
        if (spec != null && spec.bottomLeftScale() && scaleChangeMatchesPreview(spec.scalePanelFamily())) {
            frozenScaleDragFamily = spec.scalePanelFamily();
            scaleDragMenuRebasePending = false;
        }
        updateActiveScoreboardSlider(mouseX);
    }

    private void updateActiveScoreboardSlider(int mouseX) {
        if (activeSlider.isEmpty()) return;
        SliderSpec slider = sliderSpec(activeSlider);
        if (slider == null) return;
        float fraction = HudPosition.clamp01((float) (mouseX - activeSliderX) / (float) SLIDER_WIDTH);
        float value = slider.valueAtFraction(fraction);
        if (Math.abs(slider.value() - value) > 0.0001F) {
            slider.setValue(value);
            sliderDirty = true;
        }
    }

    private void finishScoreboardSliderDrag() {
        if (sliderDirty && sliderUndoSnapshot != null && !sliderUndoSnapshot.matchesCurrent()) {
            recordUndo(sliderUndoSnapshot);
            GtbStructuresSettings.INSTANCE.save();
        }
        if (!frozenScaleDragFamily.isEmpty()) scaleDragMenuRebasePending = true;
        activeSlider = "";
        sliderUndoSnapshot = null;
        sliderDirty = false;
    }

    private static void drawPanel(EditorRenderer renderer, Rect panel) {
        renderer.fill(panel.x1, panel.y1, panel.x2, panel.y2, PANEL_BACKGROUND);
        drawOutline(renderer, panel, PANEL_BORDER);
    }

    private static void drawPanelCloseButton(EditorRenderer renderer, Rect panel) {
        Rect close = panelCloseRect(panel);
        renderer.fill(close.x1, close.y1, close.x2, close.y2, PANEL_CLOSE_BG);
        INSTANCE.drawControlFeedback(renderer, "close:" + close.x1 + ":" + close.y1, close);
        drawOutline(renderer, close, PANEL_CLOSE_BORDER);
        drawXIcon(renderer, close, 0xFFFF8D9A);
    }

    private static void drawLabelsPanelCloseButton(EditorRenderer renderer, Rect panel) {
        drawPanelCloseButton(renderer, panel);
    }

    private static Rect panelCloseRect(Rect panel) {
        return new Rect(panel.x2 - PANEL_PAD - CLOSE_SIZE, panel.y1 + PANEL_PAD,
                panel.x2 - PANEL_PAD, panel.y1 + PANEL_PAD + CLOSE_SIZE);
    }

    private static void drawSection(EditorRenderer renderer, int x, int y, String text) {
        renderer.drawString("\u00a77" + text, x, y, PANEL_MUTED, true);
    }

    private static void drawTriStateRow(EditorRenderer renderer, int x, int y, String text,
                                        ScoreboardConfig.DisplayOption option) {
        option = ScoreboardConfig.normalizeOption(option);
        int checkY = toggleCheckY(y, renderer.fontHeight());
        int textY = toggleTextY(y, renderer.fontHeight());
        drawTriStateBox(renderer, x, checkY, option);
        boolean active = option != ScoreboardConfig.DisplayOption.OFF;
        renderer.drawString((active ? "\u00a7f" : "\u00a77") + text,
                x + CHECK_SIZE + 5, textY, active ? PANEL_TEXT : PANEL_MUTED, true);
        if (option == ScoreboardConfig.DisplayOption.EXPANDED
                && triStateButtonRect(x, y, renderer.fontHeight()).contains(INSTANCE.drawMouseX, INSTANCE.drawMouseY)) {
            drawTooltip(renderer, "\u00a7fOnly when expanded");
        }
    }

    private static void drawSliderRow(EditorRenderer renderer, int x, int y, String text, float value) {
        int textY = toggleTextY(y, renderer.fontHeight());
        renderer.drawString("\u00a7f" + text, x, textY, PANEL_TEXT, true);
        Rect track = sliderTrackRect(x, y);
        int cy = track.y1 + track.height() / 2;
        int accent = selectionAccent();
        int knobCenter = track.x1 + Math.round(HudPosition.clamp01(value) * SLIDER_WIDTH);
        renderer.fill(track.x1, cy, track.x2, cy + 1, 0xAA7A8492);
        renderer.fill(track.x1, cy - 1, knobCenter, cy + 2, accent);
        Rect knob = new Rect(knobCenter - SLIDER_KNOB_WIDTH / 2, cy - 5,
                knobCenter - SLIDER_KNOB_WIDTH / 2 + SLIDER_KNOB_WIDTH, cy + 6);
        renderer.fill(knob.x1, knob.y1, knob.x2, knob.y2, 0xCC101217);
        INSTANCE.drawControlFeedback(renderer, "slider:" + x + ":" + y, sliderRowRect(x, y));
        drawOutline(renderer, knob, accent);
    }

    private void drawSliderRow(EditorRenderer renderer, int x, int y, SliderSpec slider) {
        int textY = toggleTextY(y, renderer.fontHeight());
        renderer.drawString("\u00a77" + slider.caption, x, textY, PANEL_MUTED, true);
        int captionWidth = renderer.textWidth(slider.caption);
        Rect track = sliderTrackRect(x, y, captionWidth);
        int centerY = track.y1 + track.height() / 2;
        int accent = selectionAccent();
        int knobCenter = track.x1 + Math.round(slider.fractionForValue(slider.value()) * SLIDER_WIDTH);
        renderer.fill(track.x1, centerY, track.x2, centerY + 1, 0xAA7A8492);
        renderer.fill(track.x1, centerY - 1, sliderFillEnd(track.x1, knobCenter), centerY + 2, accent);
        Rect knob = new Rect(knobCenter - SLIDER_KNOB_WIDTH / 2, centerY - 5,
                knobCenter - SLIDER_KNOB_WIDTH / 2 + SLIDER_KNOB_WIDTH, centerY + 6);
        renderer.fill(knob.x1, knob.y1, knob.x2, knob.y2, 0xCC101217);
        drawControlFeedback(renderer, "slider:" + slider.id + ":" + x + ":" + y,
                sliderHighlightRect(x, y, captionWidth));
        drawOutline(renderer, knob, accent);
        Rect value = sliderValueFieldRect(x, y, captionWidth);
        if (sliderValueEditing && slider.id.equals(sliderValueEditingId)) {
            PreviewScreenController.positionHeaderEditor(value.x1, value.y1, value.width(), value.height());
        } else {
            renderer.drawString("\u00a77" + slider.formatDisplay(slider.value()), value.x1, textY,
                    PANEL_MUTED, true);
        }
    }

    private boolean handleSliderRowClick(EditorRenderer renderer, SliderSpec slider, int x, int y,
                                         int mouseX, int mouseY) {
        int captionWidth = renderer.textWidth(slider.caption);
        Rect value = sliderValueFieldRect(x, y, captionWidth);
        if (value.contains(mouseX, mouseY)) {
            beginSliderValueEditing(slider);
            return true;
        }
        if (!sliderRowRect(x, y, captionWidth).contains(mouseX, mouseY)) return false;
        if (sliderValueEditing) finishHeaderEditing();
        beginScoreboardSliderDrag(slider.id, sliderTrackRect(x, y, captionWidth).x1, mouseX);
        return true;
    }

    private void beginSliderValueEditing(SliderSpec slider) {
        if (slider == null) return;
        if (headerEditing) finishHeaderEditing();
        headerEditing = true;
        sliderValueEditing = true;
        sliderValueEditingId = slider.id;
        sliderValueInitialText = slider.formatInput(slider.value());
        PreviewScreenController.beginHeaderEditing(sliderValueInitialText);
    }

    private Rect sliderValueInputRect(EditorRenderer renderer, Rect panel) {
        Label label = activePanelLabel();
        SliderSpec slider = sliderSpec(sliderValueEditingId);
        if (guessSoundPanel.isOpen() && slider == GUESS_SOUND_VOLUME_SLIDER) {
            GuessSoundsUiLogic.Layout layout = guessSoundsLayout(panel);
            return sliderValueFieldRect(layout.searchX, layout.volumeY, renderer.textWidth(slider.caption));
        }
        if (label == null || slider == null) return new Rect(panel.x1, panel.y1, panel.x1 + 1, panel.y1 + 1);
        int x = panel.x1 + PANEL_PAD;
        int y = panel.y1 + PANEL_PAD + TITLE_HEIGHT + 5;
        return sliderValueFieldRect(x, y, renderer.textWidth(slider.caption));
    }

    private void commitSliderValue(String id, String requested) {
        SliderSpec slider = sliderSpec(id);
        if (slider == null) return;
        if (sliderValueInitialText.equals(requested)) return;
        Float value = slider.parse(requested);
        if (value == null) {
            GameStats.INSTANCE.queueNotificationMessage(slider.invalidNotice());
            return;
        }
        if (Math.abs(slider.value() - value.floatValue()) <= 0.0001F) return;
        ConfigSnapshot before = ConfigSnapshot.capture();
        beginDiscreteScaleChange(slider.scalePanelFamily());
        slider.setValue(value.floatValue());
        endDiscreteScaleChange(slider.scalePanelFamily());
        recordUndo(before);
        GtbStructuresSettings.INSTANCE.save();
    }

    private static void drawStepper(EditorRenderer renderer, int x, int y, int value) {
        Rect minus = stepperMinusRect(x, y);
        Rect plus = stepperPlusRect(x, y);
        drawSmallButton(renderer, minus, false);
        String valueText = String.valueOf(value);
        renderer.drawString("\u00a7f" + valueText,
                x + STEPPER_VALUE_CENTER - renderer.textWidth(valueText) / 2,
                y + (STEPPER_BUTTON_SIZE - renderer.fontHeight()) / 2, PANEL_TEXT, true);
        drawSmallButton(renderer, plus, true);
    }

    private static Rect stepperMinusRect(int x, int y) {
        return new Rect(x, y, x + STEPPER_BUTTON_SIZE, y + STEPPER_BUTTON_SIZE);
    }

    private static Rect stepperPlusRect(int x, int y) {
        return new Rect(x + STEPPER_PLUS_X, y, x + STEPPER_PLUS_X + STEPPER_BUTTON_SIZE,
                y + STEPPER_BUTTON_SIZE);
    }

    private static Rect stepperRowRect(Rect panel, int y) {
        return new Rect(panel.x1 + PANEL_PAD, y, panel.x2 - PANEL_PAD, y + STEPPER_BUTTON_SIZE);
    }

    private static void drawSmallButton(EditorRenderer renderer, Rect rect, boolean plus) {
        renderer.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0x663A414B);
        INSTANCE.drawControlFeedback(renderer, "stepper:" + rect.x1 + ":" + rect.y1, rect);
        drawOutline(renderer, rect, PANEL_BORDER);
        int cx = rect.x1 + rect.width() / 2;
        int cy = rect.y1 + rect.height() / 2;
        renderer.fill(cx - 3, cy, cx + 4, cy + 1, PANEL_TEXT);
        if (plus) renderer.fill(cx, cy - 3, cx + 1, cy + 4, PANEL_TEXT);
    }

    private static void drawCheckBox(EditorRenderer renderer, int x, int y, boolean selected, boolean multi) {
        Rect box = new Rect(x, y, x + CHECK_SIZE, y + CHECK_SIZE);
        renderer.fill(box.x1, box.y1, box.x2, box.y2, 0x33000000);
        INSTANCE.drawControlFeedback(renderer, "check:" + x + ":" + y, box);
        drawOutline(renderer, box, 0xAA7A8492);
        if (!selected) return;
        if (multi) {
            int c = selectionAccent();
            renderer.fill(x + 2, y + 4, x + 3, y + 6, c);
            renderer.fill(x + 3, y + 5, x + 4, y + 7, c);
            renderer.fill(x + 4, y + 4, x + 5, y + 6, c);
            renderer.fill(x + 5, y + 3, x + 6, y + 5, c);
            renderer.fill(x + 6, y + 2, x + 7, y + 4, c);
        } else {
            renderer.fill(x + 3, y + 3, x + 7, y + 7, selectionAccent());
        }
    }

    private static void drawTriStateBox(EditorRenderer renderer, int x, int y,
                                        ScoreboardConfig.DisplayOption option) {
        Rect box = new Rect(x, y, x + CHECK_SIZE, y + CHECK_SIZE);
        renderer.fill(box.x1, box.y1, box.x2, box.y2, 0x33000000);
        INSTANCE.drawControlFeedback(renderer, "tri:" + x + ":" + y, box);
        drawOutline(renderer, box, 0xAA7A8492);
        int c = selectionAccent();
        if (option == ScoreboardConfig.DisplayOption.ON) {
            renderer.fill(x + 3, y + 3, x + 7, y + 7, c);
        } else if (option == ScoreboardConfig.DisplayOption.EXPANDED) {
            for (int dy = 0; dy < 4; dy++) {
                int width = 4 - dy;
                renderer.fill(x + 3, y + 3 + dy, x + 3 + width, y + 4 + dy, c);
            }
        }
    }

    private static void drawTooltip(EditorRenderer renderer, String text) {
        int x = INSTANCE.drawMouseX + 9;
        int width = renderer.textWidth(text) + 6;
        int height = renderer.fontHeight() + 6;
        int y = INSTANCE.drawMouseY - height - 7;
        if (y < 2) y = INSTANCE.drawMouseY + 9;
        renderer.fill(x, y, x + width, y + height, 0xF0101010);
        drawOutline(renderer, new Rect(x, y, x + width, y + height), 0xAA7A8492);
        renderer.drawString(text, x + 3, y + 3, PANEL_TEXT, true);
    }

    private static void drawColorSwatches(EditorRenderer renderer, int x, int y, String selected) {
        for (int i = 0; i < COLOR_CODES.length; i++) {
            Rect swatch = colorSwatchRect(x, y, i);
            int color = MinecraftFormatting.minecraftColorArgb("\u00a7" + COLOR_CODES[i], 0xFF);
            renderer.fill(swatch.x1, swatch.y1, swatch.x2, swatch.y2, color);
            INSTANCE.drawControlFeedback(renderer, "color:" + i + ":" + x + ":" + y, swatch);
            drawOutline(renderer, swatch, 0xAA111111);
            if (("\u00a7" + COLOR_CODES[i]).equals(selected)) {
                drawOutline(renderer, new Rect(swatch.x1 - 2, swatch.y1 - 2,
                        swatch.x2 + 2, swatch.y2 + 2), 0xFFFFFFFF);
            }
        }
    }

    private void drawControlFeedback(EditorRenderer renderer, String id, Rect rect) {
        boolean hovered = rect.contains(drawMouseX, drawMouseY);
        float current = controlHoverAmounts.containsKey(id) ? controlHoverAmounts.get(id).floatValue() : 0F;
        float next = hovered ? Math.min(1F, current + HOVER_STEP_IN) : Math.max(0F, current - HOVER_STEP_OUT);
        controlHoverAmounts.put(id, Float.valueOf(next));
        if (next > 0.01F) {
            int alpha = Math.min(0x35, Math.max(0, (int) (0x35 * next)));
            renderer.fill(rect.x1, rect.y1, rect.x2, rect.y2, (alpha << 24) | 0xFFFFFF);
        }
        if (hovered && drawLeftDown) {
            renderer.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0x33000000);
        }
    }

    private static int selectionAccent() {
        return MinecraftFormatting.minecraftColorArgb(GtbStructuresSettings.INSTANCE.colorBrackets, 0xFF);
    }

    private static Rect colorSwatchRect(int x, int y, int index) {
        int col = index % 8;
        int row = index / 8;
        int sx = x + col * 13;
        int sy = y + row * 13;
        return new Rect(sx, sy, sx + 10, sy + 10);
    }

    private static String colorAt(int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < COLOR_CODES.length; i++) {
            if (colorSwatchRect(x, y, i).contains(mouseX, mouseY)) return "\u00a7" + COLOR_CODES[i];
        }
        return null;
    }

    private static String currentHintColor() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        String target = INSTANCE.hintColorTarget;
        if ("bracket".equals(target)) {
            return MinecraftFormatting.normalizeMinecraftColorCode(settings.colorBrackets, "\u00a76");
        }
        if ("hyphen".equals(target)) {
            return MinecraftFormatting.normalizeMinecraftColorCode(settings.colorHyphens, "\u00a77");
        }
        return MinecraftFormatting.normalizeMinecraftColorCode(settings.colorNumbers, "\u00a7e");
    }

    private static String currentCooldownColor() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        return "bar".equals(INSTANCE.cooldownColorTarget)
                ? MinecraftFormatting.normalizeMinecraftColorCode(settings.chatCooldownBarColor, "\u00a7e")
                : MinecraftFormatting.normalizeMinecraftColorCode(settings.chatCooldownTimerColor, "\u00a7f");
    }

    private void setHintColorTarget(String color) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if ("bracket".equals(hintColorTarget)) {
            settings.colorBrackets = color;
        } else if ("hyphen".equals(hintColorTarget)) {
            settings.colorHyphens = color;
        } else {
            settings.colorNumbers = color;
        }
    }

    private void setCooldownColorTarget(String color) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if ("bar".equals(cooldownColorTarget)) {
            settings.chatCooldownBarColor = color;
        } else {
            settings.chatCooldownTimerColor = color;
        }
    }

    private static void drawXIcon(EditorRenderer renderer, Rect rect, int color) {
        int cx = rect.x1 + rect.width() / 2;
        int cy = rect.y1 + rect.height() / 2;
        renderer.fill(cx - 2, cy - 2, cx - 1, cy - 1, color);
        renderer.fill(cx - 1, cy - 1, cx, cy, color);
        renderer.fill(cx, cy, cx + 1, cy + 1, color);
        renderer.fill(cx + 1, cy + 1, cx + 2, cy + 2, color);
        renderer.fill(cx + 2, cy + 2, cx + 3, cy + 3, color);
        renderer.fill(cx + 2, cy - 2, cx + 3, cy - 1, color);
        renderer.fill(cx + 1, cy - 1, cx + 2, cy, color);
        renderer.fill(cx - 1, cy + 1, cx, cy + 2, color);
        renderer.fill(cx - 2, cy + 2, cx - 1, cy + 3, color);
    }

    private void drawBackground(EditorRenderer renderer, Label label) {
        Float amount = hoverAmounts.get(label.id);
        if (amount != null && amount.floatValue() > 0.01F) {
            int alpha = Math.min(0x48, Math.max(0, (int) (0x48 * amount.floatValue())));
            drawPadded(renderer, label, (alpha << 24) | 0x808080);
        }

        if (label.id.equals(selectedId)) {
            boolean pressedInside = drawLeftDown && label.contains(drawMouseX, drawMouseY);
            int alpha = pressedInside ? 0x78 : 0x56;
            int rgb = MinecraftFormatting.minecraftColorArgb(
                    GtbStructuresSettings.INSTANCE.colorNumbers, 0xFF) & 0x00FFFFFF;
            drawPadded(renderer, label, (alpha << 24) | rgb);
            drawResizeHandles(renderer, label, rgb);
        }
    }

    private void drawBackground(BackgroundRenderer renderer, Label label) {
        Float amount = hoverAmounts.get(label.id);
        if (amount != null && amount.floatValue() > 0.01F) {
            int alpha = Math.min(0x48, Math.max(0, (int) (0x48 * amount.floatValue())));
            drawPadded(renderer, label, (alpha << 24) | 0x808080);
        }

        if (label.id.equals(selectedId)) {
            boolean pressedInside = drawLeftDown && label.contains(drawMouseX, drawMouseY);
            int alpha = pressedInside ? 0x78 : 0x56;
            int rgb = MinecraftFormatting.minecraftColorArgb(
                    GtbStructuresSettings.INSTANCE.colorNumbers, 0xFF) & 0x00FFFFFF;
            drawPadded(renderer, label, (alpha << 24) | rgb);
            drawResizeHandles(renderer, label, rgb);
        }
    }

    private static void drawResizeHandles(EditorRenderer renderer, Label label, int rgb) {
        if (label == null || !"handheld".equals(label.target.key)) return;
        int color = 0xFF000000 | rgb;
        Rect frame = handheldResizeFrame(label);
        fillResizeHandle(renderer, frame.x1, frame.y1, color);
        fillResizeHandle(renderer, frame.x2, frame.y1, color);
        fillResizeHandle(renderer, frame.x1, frame.y2, color);
        fillResizeHandle(renderer, frame.x2, frame.y2, color);
    }

    private static void drawResizeHandles(BackgroundRenderer renderer, Label label, int rgb) {
        if (label == null || !"handheld".equals(label.target.key)) return;
        int color = 0xFF000000 | rgb;
        Rect frame = handheldResizeFrame(label);
        fillResizeHandle(renderer, frame.x1, frame.y1, color);
        fillResizeHandle(renderer, frame.x2, frame.y1, color);
        fillResizeHandle(renderer, frame.x1, frame.y2, color);
        fillResizeHandle(renderer, frame.x2, frame.y2, color);
    }

    private static void fillResizeHandle(EditorRenderer renderer, int x, int y, int color) {
        renderer.fill(x - RESIZE_HANDLE_VISUAL_RADIUS, y - RESIZE_HANDLE_VISUAL_RADIUS,
                x + RESIZE_HANDLE_VISUAL_RADIUS + 1, y + RESIZE_HANDLE_VISUAL_RADIUS + 1, color);
    }

    private static void fillResizeHandle(BackgroundRenderer renderer, int x, int y, int color) {
        renderer.fill(x - RESIZE_HANDLE_VISUAL_RADIUS, y - RESIZE_HANDLE_VISUAL_RADIUS,
                x + RESIZE_HANDLE_VISUAL_RADIUS + 1, y + RESIZE_HANDLE_VISUAL_RADIUS + 1, color);
    }

    private static void drawOutline(EditorRenderer renderer, Rect rect, int color) {
        renderer.fill(rect.x1, rect.y1, rect.x1 + 1, rect.y2, color);
        renderer.fill(rect.x2 - 1, rect.y1, rect.x2, rect.y2, color);
        if (rect.width() > 2) {
            renderer.fill(rect.x1 + 1, rect.y1, rect.x2 - 1, rect.y1 + 1, color);
            renderer.fill(rect.x1 + 1, rect.y2 - 1, rect.x2 - 1, rect.y2, color);
        }
    }

    private static boolean isPointInEditorPanel(EditorRenderer renderer, int screenWidth, int screenHeight,
                                                int mouseX, int mouseY) {
        if (isPointInOpenPanel(screenWidth, screenHeight, mouseX, mouseY)) return true;
        if (doneButtonRect(screenWidth, screenHeight).contains(mouseX, mouseY)) return true;
        if (labelsButtonRect(screenWidth).contains(mouseX, mouseY)) return true;
        return false;
    }

    private static boolean isPointInOpenPanel(int screenWidth, int screenHeight,
                                              int mouseX, int mouseY) {
        Label label = INSTANCE.activePanelLabel();
        if (label != null && INSTANCE.settingsPanelRect(label, screenWidth, screenHeight).contains(mouseX, mouseY)) {
            return true;
        }
        if (INSTANCE.labelsPanelOpen && labelsPanelRect(screenWidth, screenHeight).contains(mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    private static Rect labelsButtonRect(int screenWidth) {
        int inset = LABELS_BUTTON_SIZE / 2;
        int x2 = screenWidth - MARGIN - inset;
        int y1 = MARGIN + inset;
        return new Rect(x2 - LABELS_BUTTON_SIZE, y1, x2, y1 + LABELS_BUTTON_SIZE);
    }

    private static Rect doneButtonRect(int screenWidth, int screenHeight) {
        int x = (screenWidth - DONE_BUTTON_WIDTH) / 2;
        int y = screenHeight - DONE_BUTTON_HEIGHT - 8;
        return new Rect(x, y, x + DONE_BUTTON_WIDTH, y + DONE_BUTTON_HEIGHT);
    }

    private static int panelBottomLimit(int screenWidth, int screenHeight) {
        return Math.max(MARGIN, doneButtonRect(screenWidth, screenHeight).y1 - DONE_BUTTON_HEIGHT / 2);
    }

    private static Rect labelsPanelRect(int screenWidth, int screenHeight) {
        int x2 = screenWidth - MARGIN;
        int y = labelsButtonRect(screenWidth).y2 + 4;
        int bottomLimit = panelBottomLimit(screenWidth, screenHeight);
        int available = bottomLimit - y;
        if (available < PANEL_PAD * 2 + TITLE_HEIGHT + ROW_HEIGHT) {
            y = MARGIN;
            available = bottomLimit - y;
        }
        int height = labelsPanelHeightForAvailableForTest(labelsPanelHeight(), available);
        return new Rect(x2 - LABELS_PANEL_WIDTH, y, x2, y + height);
    }

    private static Rect labelsBodyViewport(Rect panel) {
        int bodyY = labelsBodyY(panel);
        return new Rect(panel.x1 + 1, bodyY, panel.x2 - 1,
                Math.max(bodyY, panel.y2 - PANEL_PAD));
    }

    private static int labelsBodyY(Rect panel) {
        return panel.y1 + PANEL_PAD + TITLE_HEIGHT + 4;
    }

    private static int labelsPanelHeight() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (!settings.modEnabled) return collapsedLabelsPanelHeight();
        int rows = 6;
        rows += 2 + (settings.autoRequeue ? 1 : 0);
        rows += 1;
        rows += 1 + (settings.guessPeek ? 4 : 0);
        rows += 1 + (settings.showAllMatching ? 2 : 0);
        rows += 1 + (settings.themePointReveal ? 1 : 0);
        rows += 1;
        if (settings.shortcutReminder) {
            rows += 3;
            if (settings.shortcutReminderShowMultiwords) rows += 1;
            if (settings.shortcutReminderShowNonAscii) rows += 5;
        }
        // These language-group controls remain present when Shortcut Reminder is disabled.
        rows += 2;
        rows += 3 + (settings.scrambleGuess ? 1 : 0) + (settings.trollMode ? 1 : 0);
        rows += 1; // Keep the final Guess Sounds link clear of the viewport scissor edge.
        return PANEL_PAD * 2 + TITLE_HEIGHT + 4
                + rows * ROW_HEIGHT
                + (HEADER_ROW_HEIGHT - ROW_HEIGHT)
                + 5 * sectionBlockHeight();
    }

    private static int collapsedLabelsPanelHeight() {
        return PANEL_PAD * 2 + TITLE_HEIGHT + 4 + ROW_HEIGHT;
    }

    private static int labelsBodyContentHeight() {
        return labelsPanelHeight() - (PANEL_PAD * 2 + TITLE_HEIGHT + 4);
    }

    private static int labelsMaxScroll(Rect panel) {
        return labelsMaxScrollForTest(labelsBodyContentHeight(), labelsBodyViewport(panel).height());
    }

    static int labelsMaxScrollForTest(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - Math.max(0, viewportHeight));
    }

    static int labelsPanelHeightForAvailableForTest(int contentHeight, int availableHeight) {
        return Math.min(Math.max(1, contentHeight), Math.max(1, availableHeight));
    }

    static int collapsedLabelsPanelHeightForTest() {
        return collapsedLabelsPanelHeight();
    }

    static boolean targetVisibleForMasterForTest(boolean modEnabled, String targetKey) {
        return targetVisibleForMaster(modEnabled, targetKey);
    }

    static boolean targetSelectableForMasterForTest(boolean modEnabled, String targetKey) {
        return modEnabled && targetVisibleForMaster(true, targetKey);
    }

    private static boolean targetVisibleForMaster(boolean modEnabled, String targetKey) {
        return modEnabled || "actionbar".equals(targetKey) || "scoreboard".equals(targetKey);
    }

    static int labelsScrollbarOffsetForTest(int mouseY, int dragOffset, int trackY,
                                            int trackHeight, int thumbHeight, int maxScroll) {
        int travel = Math.max(0, trackHeight - thumbHeight);
        if (travel == 0 || maxScroll <= 0) return 0;
        int thumbY = Math.max(0, Math.min(travel, mouseY - dragOffset - trackY));
        return (int) Math.round((double) thumbY * maxScroll / travel);
    }

    private static Rect labelsScrollbarTrack(Rect panel) {
        Rect viewport = labelsBodyViewport(panel);
        int x2 = panel.x2 - 2;
        return new Rect(x2 - LABELS_SCROLLBAR_WIDTH, viewport.y1, x2, viewport.y2);
    }

    private static Rect labelsScrollbarThumb(Rect panel, int scrollOffset) {
        Rect track = labelsScrollbarTrack(panel);
        int maxScroll = labelsMaxScroll(panel);
        if (maxScroll <= 0 || track.height() <= 0) return null;
        int viewportHeight = labelsBodyViewport(panel).height();
        int contentHeight = labelsBodyContentHeight();
        int thumbHeight = Math.max(LABELS_SCROLLBAR_MIN_THUMB,
                contentHeight <= 0 ? track.height()
                        : track.height() * viewportHeight / contentHeight);
        thumbHeight = Math.min(track.height(), thumbHeight);
        int travel = track.height() - thumbHeight;
        int thumbY = travel <= 0 ? 0
                : (int) Math.round((double) Math.max(0, Math.min(maxScroll, scrollOffset))
                * travel / maxScroll);
        return new Rect(track.x1, track.y1 + thumbY,
                track.x2, track.y1 + thumbY + thumbHeight);
    }

    private void drawLabelsScrollbar(EditorRenderer renderer, Rect panel) {
        Rect thumb = labelsScrollbarThumb(panel, labelsScrollOffset);
        if (thumb == null) return;
        Rect track = labelsScrollbarTrack(panel);
        renderer.fill(track.x1, track.y1, track.x2, track.y2, 0x442A313B);
        renderer.fill(thumb.x1, thumb.y1, thumb.x2, thumb.y2,
                labelsScrollbarDragging ? 0xFFD5DCE5 : 0xFF9EA8B5);
        drawControlFeedback(renderer, "labels-scrollbar", thumb);
    }

    private void clampLabelsScroll(int screenWidth, int screenHeight) {
        int maxScroll = labelsMaxScroll(labelsPanelRect(screenWidth, screenHeight));
        labelsScrollOffset = Math.max(0, Math.min(maxScroll, labelsScrollOffset));
        if (maxScroll == 0) labelsScrollbarDragging = false;
    }

    private void scrollLabelsBy(int amount, int screenWidth, int screenHeight) {
        labelsScrollOffset += amount;
        clampLabelsScroll(screenWidth, screenHeight);
    }

    private boolean beginLabelsScrollbarDrag(Rect panel, int mouseX, int mouseY) {
        Rect thumb = labelsScrollbarThumb(panel, labelsScrollOffset);
        if (thumb == null) return false;
        Rect track = labelsScrollbarTrack(panel);
        if (!track.contains(mouseX, mouseY)) return false;
        if (thumb.contains(mouseX, mouseY)) {
            labelsScrollbarDragOffset = mouseY - thumb.y1;
        } else {
            labelsScrollbarDragOffset = thumb.height() / 2;
            labelsScrollOffset = labelsScrollbarOffsetForTest(
                    mouseY, labelsScrollbarDragOffset, track.y1,
                    track.height(), thumb.height(), labelsMaxScroll(panel));
        }
        labelsScrollbarDragging = true;
        return true;
    }

    private void updateLabelsScrollbarDrag(int screenWidth, int screenHeight, int mouseY) {
        Rect panel = labelsPanelRect(screenWidth, screenHeight);
        Rect thumb = labelsScrollbarThumb(panel, labelsScrollOffset);
        if (thumb == null) {
            labelsScrollbarDragging = false;
            return;
        }
        Rect track = labelsScrollbarTrack(panel);
        labelsScrollOffset = labelsScrollbarOffsetForTest(
                mouseY, labelsScrollbarDragOffset, track.y1,
                track.height(), thumb.height(), labelsMaxScroll(panel));
    }

    private static int menuSectionYAfter(int y) {
        return y + sectionBlockHeight();
    }

    private static boolean toggleHit(EditorRenderer renderer, int x, int y, String text, int mouseX, int mouseY) {
        return toggleRect(x, y, renderer.textWidth(text)).contains(mouseX, mouseY);
    }

    private static boolean triStateHit(EditorRenderer renderer, int x, int y, String text,
                                       int mouseX, int mouseY) {
        return triStateRowRect(x, y, renderer.textWidth(text)).contains(mouseX, mouseY);
    }

    private void clearPanelForTarget(String targetKey) {
        Label active = labelById(activePanelId);
        if (active != null && targetKey.equals(active.target.key)) clearActivePanel();
        Label selected = labelById(selectedId);
        if (selected != null && targetKey.equals(selected.target.key)) deselect();
    }

    private void clearPanelForTargetFamily(String targetKey) {
        Label active = labelById(activePanelId);
        if (active != null && active.target.key.startsWith(targetKey)) clearActivePanel();
        Label selected = labelById(selectedId);
        if (selected != null && selected.target.key.startsWith(targetKey)) deselect();
    }

    private static int resizeEdgesAt(Label label, int mouseX, int mouseY) {
        if (label == null || !"handheld".equals(label.target.key)) return 0;
        Rect frame = handheldResizeFrame(label);
        if (mouseX < frame.x1 - RESIZE_HANDLE_HIT_RADIUS || mouseX > frame.x2 + RESIZE_HANDLE_HIT_RADIUS
                || mouseY < frame.y1 - RESIZE_HANDLE_HIT_RADIUS || mouseY > frame.y2 + RESIZE_HANDLE_HIT_RADIUS) {
            return 0;
        }
        int edges = 0;
        if (Math.abs(mouseX - frame.x1) <= RESIZE_HANDLE_HIT_RADIUS) edges |= RESIZE_LEFT;
        if (Math.abs(mouseX - frame.x2) <= RESIZE_HANDLE_HIT_RADIUS) edges |= RESIZE_RIGHT;
        if (Math.abs(mouseY - frame.y1) <= RESIZE_HANDLE_HIT_RADIUS) edges |= RESIZE_TOP;
        if (Math.abs(mouseY - frame.y2) <= RESIZE_HANDLE_HIT_RADIUS) edges |= RESIZE_BOTTOM;
        return edges;
    }

    private static Rect handheldResizeFrame(Label label) {
        return new Rect(label.x1 - LABEL_BG_PAD_X, label.y1 - LABEL_BG_PAD_Y,
                label.x2 + LABEL_BG_PAD_X, label.y2 + LABEL_BG_PAD_Y);
    }

    private void resizeHandheldPanel(int dx, int dy, int screenWidth, int screenHeight) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        dx *= RESIZE_DRAG_MULTIPLIER;
        dy *= RESIZE_DRAG_MULTIPLIER;
        float scale = HudPanelScale.normalize(settings.handheldItemScale);
        int width = resizeStartWidth;
        int height = resizeStartHeight;
        int left = dragStartLabelX;
        int top = dragStartLabelY;
        if ((resizeEdges & (RESIZE_LEFT | RESIZE_RIGHT)) != 0) {
            int displayedWidth = Math.max(1, resizeStartWidth
                    + ((resizeEdges & RESIZE_LEFT) != 0 ? -dx : dx));
            int baseWidth = HudPanelScale.basePixels(displayedWidth, scale);
            width = HudPanelScale.scaledPixels(GtbStructuresSettings.handheldPanelWidthPixels(
                    handheldPanelResizeRatioForTest(baseWidth, screenWidth, true), screenWidth), scale);
            if ((resizeEdges & RESIZE_LEFT) != 0) left = dragStartLabelX + resizeStartWidth - width;
        }
        if ((resizeEdges & (RESIZE_TOP | RESIZE_BOTTOM)) != 0) {
            int displayedHeight = Math.max(1, resizeStartHeight
                    + ((resizeEdges & RESIZE_TOP) != 0 ? -dy : dy));
            int baseHeight = HudPanelScale.basePixels(displayedHeight, scale);
            height = HudPanelScale.scaledPixels(GtbStructuresSettings.handheldPanelHeightPixels(
                    handheldPanelResizeRatioForTest(baseHeight, screenHeight, false), screenHeight), scale);
            if ((resizeEdges & RESIZE_TOP) != 0) top = dragStartLabelY + resizeStartHeight - height;
        }
        if ((resizeEdges & (RESIZE_LEFT | RESIZE_RIGHT)) != 0) {
            settings.handheldItemPanelWidthRatio = handheldPanelResizeRatioForTest(
                    HudPanelScale.basePixels(width, scale), screenWidth, true);
        }
        if ((resizeEdges & (RESIZE_TOP | RESIZE_BOTTOM)) != 0) {
            settings.handheldItemPanelHeightRatio = handheldPanelResizeRatioForTest(
                    HudPanelScale.basePixels(height, scale), screenHeight, false);
        }
        setHandheldPositionFromPanelBounds(settings, left, top, width, height, screenWidth, screenHeight);
    }

    static float handheldPanelResizeRatioForTest(int pixels, int screenDimension, boolean width) {
        return width
                ? GtbStructuresSettings.handheldPanelWidthRatioForPixels(pixels, screenDimension)
                : GtbStructuresSettings.handheldPanelHeightRatioForPixels(pixels, screenDimension);
    }

    static int handheldPanelDisplayedPixelsAfterResizeForTest(int displayedPixels, int screenDimension,
                                                               boolean width, float scale) {
        float normalizedScale = HudPanelScale.normalize(scale);
        int basePixels = HudPanelScale.basePixels(displayedPixels, normalizedScale);
        int canonicalBasePixels = width
                ? GtbStructuresSettings.handheldPanelWidthPixels(
                handheldPanelResizeRatioForTest(basePixels, screenDimension, true), screenDimension)
                : GtbStructuresSettings.handheldPanelHeightPixels(
                handheldPanelResizeRatioForTest(basePixels, screenDimension, false), screenDimension);
        return HudPanelScale.scaledPixels(canonicalBasePixels, normalizedScale);
    }

    private static void setHandheldPositionFromPanelBounds(GtbStructuresSettings settings, int left, int top,
                                                           int width, int height, int screenWidth, int screenHeight) {
        settings.handheldItemPosX = normalizedPanelCoordinate(left, MARGIN,
                Math.max(MARGIN, screenWidth - width - MARGIN));
        settings.handheldItemPosY = normalizedPanelCoordinate(top + height, height + MARGIN,
                Math.max(height + MARGIN, screenHeight - MARGIN));
    }

    private static float normalizedPanelCoordinate(int value, int min, int max) {
        if (max <= min) return 0F;
        return HudPosition.clamp01((float) (value - min) / (float) (max - min));
    }

    private static void toggleShortcutNonAsciiCategory(GtbStructuresSettings.ShortcutNonAsciiCategory category) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        settings.setShortcutNonAsciiCategoryEnabled(category,
                !settings.shortcutNonAsciiCategoryEnabled(category));
    }

    private Label topLabelAt(int x, int y) {
        for (int i = labels.size() - 1; i >= 0; i--) {
            Label label = labels.get(i);
            if (label.contains(x, y)) return label;
        }
        return null;
    }

    private Label labelById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (int i = labels.size() - 1; i >= 0; i--) {
            Label label = labels.get(i);
            if (id.equals(label.id)) return label;
        }
        return null;
    }

    private Label activePanelLabel() {
        Label label = labelById(activePanelId);
        if (label != null) return label;
        if (pinnedPanelFamily.isEmpty()) return null;
        for (int i = labels.size() - 1; i >= 0; i--) {
            Label candidate = labels.get(i);
            if (pinnedPanelFamily.equals(panelFamily(candidate))) {
                activePanelId = candidate.id;
                return candidate;
            }
        }
        return null;
    }

    private void pinPanel(Label label, int screenWidth, int screenHeight) {
        if (label == null) return;
        Rect natural = naturalSettingsPanelRect(label, screenWidth, screenHeight,
                settingsPanelWidth(label), settingsPanelHeight(label));
        pinnedPanelFamily = panelFamily(label);
        pinnedPanelX = natural.x1;
        pinnedPanelY = natural.y1;
        rememberPinnedPanelAnchor(label, natural);
    }

    private void armPanelFollow(Label selected, int screenWidth, int screenHeight) {
        dragPanelFollows = selected != null
                && !pinnedPanelFamily.isEmpty()
                && pinnedPanelFamily.equals(panelFamily(selected));
        if (!dragPanelFollows) return;
        Label panelLabel = activePanelLabel();
        if (panelLabel == null) {
            dragPanelFollows = false;
            return;
        }
        Rect panel = settingsPanelRect(panelLabel, screenWidth, screenHeight);
        dragStartPanelX = panel.x1;
        dragStartPanelY = panel.y1;
        dragPanelLabelX = selected.x1;
        dragPanelLabelY = selected.y1;
    }

    private void movePinnedPanelWithDrag(Label selected, int screenWidth, int screenHeight) {
        if (!dragPanelFollows || selected == null) return;
        Label panelLabel = activePanelLabel();
        if (panelLabel == null || !panelFamily(selected).equals(panelFamily(panelLabel))) {
            dragPanelFollows = false;
            return;
        }

        Rect draggedLabel = draggedLabelRect(selected, screenWidth, screenHeight);

        int width = settingsPanelWidth(panelLabel);
        int height = settingsPanelHeight(panelLabel);
        Rect clamped = clampPanelRect(
                dragStartPanelX + draggedLabel.x1 - dragPanelLabelX,
                dragStartPanelY + draggedLabel.y1 - dragPanelLabelY,
                width, height, screenWidth, screenHeight);
        Rect moved = moveEdgePinnedPanelAwayFromDraggedLabel(
                clamped, draggedLabel, screenWidth, pinnedPanelAnchorSide);
        if (moved != clamped) {
            dragStartPanelX = moved.x1;
            dragStartPanelY = moved.y1;
            dragPanelLabelX = draggedLabel.x1;
            dragPanelLabelY = draggedLabel.y1;
        }
        pinnedPanelX = moved.x1;
        pinnedPanelY = moved.y1;
        rememberPinnedPanelAnchor(draggedLabel, moved);
    }

    private Rect draggedLabelRect(Label selected, int screenWidth, int screenHeight) {
        int currentLabelX = dragStartLabelX;
        int currentLabelY = dragStartLabelY;
        if (selected.target.moveX) {
            currentLabelX = HudPosition.left(selected.target.x(), screenWidth, selected.width(), MARGIN);
        }
        if (selected.target.moveMode == MoveMode.SCOREBOARD_Y) {
            currentLabelY += dragStartScoreboardOffset - GtbStructuresSettings.INSTANCE.sbConfig.heightOffset;
        } else if (selected.target.moveY) {
            if (selected.target.moveMode == MoveMode.BOTTOM_LEFT) {
                currentLabelY = HudPosition.bottom(selected.target.y(),
                        screenHeight, selected.height(), MARGIN) - selected.height();
            } else {
                currentLabelY = HudPosition.top(selected.target.y(), screenHeight, selected.height(), MARGIN);
            }
        }
        return new Rect(currentLabelX, currentLabelY,
                currentLabelX + selected.width(), currentLabelY + selected.height());
    }

    private static Rect moveEdgePinnedPanelAwayFromDraggedLabel(Rect panel, Rect label,
                                                                 int screenWidth, int anchorSide) {
        if (panel == null || label == null || !overlapsVertically(panel, label)) return panel;

        if (anchorSide > 0
                && panel.x2 == screenWidth - MARGIN
                && label.x1 < panel.x2 && label.x2 >= panel.x1) {
            int oppositeX = label.x1 - panel.width() - SETTINGS_PANEL_GAP;
            if (oppositeX >= MARGIN) {
                return new Rect(oppositeX, panel.y1,
                        oppositeX + panel.width(), panel.y2);
            }
        } else if (anchorSide < 0
                && panel.x1 == MARGIN
                && label.x1 <= panel.x2 && label.x2 > panel.x1) {
            int oppositeX = label.x2 + SETTINGS_PANEL_GAP;
            if (oppositeX + panel.width() <= screenWidth - MARGIN) {
                return new Rect(oppositeX, panel.y1,
                        oppositeX + panel.width(), panel.y2);
            }
        }
        return panel;
    }

    private static boolean overlapsVertically(Rect first, Rect second) {
        return first.y1 < second.y2 && second.y1 < first.y2;
    }

    private Rect settingsPanelRect(Label label, int screenWidth, int screenHeight) {
        int width = settingsPanelWidth(label);
        int height = settingsPanelHeight(label);
        int x;
        int y;
        String family = panelFamily(label);
        Rect anchorLabel = currentPanelAnchorRect(label, screenWidth, screenHeight);
        boolean panelPositionFrozen = (dragArmed && dragPanelFollows) || scaleDragMenuFrozenForFamily(family);
        if (!pinnedPanelFamily.isEmpty() && pinnedPanelFamily.equals(family)) {
            x = panelPositionFrozen ? pinnedPanelX : pinnedPanelXFollowingLabel(anchorLabel, width, screenWidth);
            y = pinnedPanelY;
        } else {
            Rect natural = naturalSettingsPanelRect(label, screenWidth, screenHeight, width, height);
            x = natural.x1;
            y = natural.y1;
        }
        Rect clamped = clampPanelRect(x, y, width, height, screenWidth, screenHeight);
        pinnedPanelFamily = family;
        pinnedPanelX = clamped.x1;
        pinnedPanelY = clamped.y1;
        rememberPinnedPanelAnchor(anchorLabel, clamped);
        return clamped;
    }

    private Rect currentPanelAnchorRect(Label label, int screenWidth, int screenHeight) {
        if (dragArmed && dragPanelFollows) {
            Label selected = labelById(selectedId);
            if (selected != null && panelFamily(selected).equals(panelFamily(label))) {
                return draggedLabelRect(selected, screenWidth, screenHeight);
            }
        }
        return new Rect(label.x1, label.y1, label.x2, label.y2);
    }

    private static Rect naturalSettingsPanelRect(Label label, int screenWidth, int screenHeight,
                                                 int width, int height) {
        int x = label.x2 + SETTINGS_PANEL_GAP;
        if (x + width > screenWidth - MARGIN) x = label.x1 - width - SETTINGS_PANEL_GAP;
        int y = label.y1;
        return clampPanelRect(x, y, width, height, screenWidth, screenHeight);
    }

    private static Rect clampPanelRect(int x, int y, int width, int height,
                                       int screenWidth, int screenHeight) {
        if (x < MARGIN) x = MARGIN;
        if (x + width > screenWidth - MARGIN) {
            x = Math.max(MARGIN, screenWidth - width - MARGIN);
        }
        int bottomLimit = panelBottomLimit(screenWidth, screenHeight);
        if (y + height > bottomLimit) y = bottomLimit - height;
        if (y < MARGIN) y = MARGIN;
        return new Rect(x, y, x + width, y + height);
    }

    private static int settingsPanelWidth(Label label) {
        return settingsPanelWidthForKey(label.target.key);
    }

    private static int settingsPanelWidthForKey(String key) {
        if ("scoreboard".equals(key) || "hint".equals(key) || "cooldown".equals(key)
                || "handheld".equals(key) || "environment".equals(key)
                || (key != null && key.startsWith("environment_"))) return 230;
        return 128;
    }

    private static int settingsPanelHeight(Label label) {
        String key = label.target.key;
        if ("hint".equals(key)) return 154;
        if ("handheld".equals(key)) {
            return GtbStructuresSettings.INSTANCE.handheldItemVerticalLayout ? 283 : 259;
        }
        if ("environment".equals(key) || key.startsWith("environment_")) return 178;
        if ("cooldown".equals(key)) return 142;
        if ("scoreboard".equals(key)) {
            return scoreboardSettingsPanelHeight(GtbStructuresSettings.INSTANCE.sbConfig.skin);
        }
        return 62;
    }

    private static int scoreboardSettingsPanelHeight(String skin) {
        return scoreboardStyleOptionsVisible(skin) ? 216 : 74;
    }

    private static boolean scoreboardStyleOptionsVisible(String skin) {
        return !"vanilla".equals(ScoreboardConfig.normalizeSkin(skin));
    }

    private static boolean scoreboardDefaultBuilderOptionVisible(String skin) {
        return "default".equals(ScoreboardConfig.normalizeSkin(skin));
    }

    private static boolean scoreboardHypixelRoundsOptionVisible(String skin) {
        return "hypixel".equals(ScoreboardConfig.normalizeSkin(skin));
    }

    static boolean scoreboardDefaultBuilderOptionVisibleForTest(String skin) {
        return scoreboardDefaultBuilderOptionVisible(skin);
    }

    static boolean scoreboardHypixelRoundsOptionVisibleForTest(String skin) {
        return scoreboardHypixelRoundsOptionVisible(skin);
    }

    static boolean scoreboardStyleOptionsVisibleForTest(String skin) {
        return scoreboardStyleOptionsVisible(skin);
    }

    static int scoreboardSettingsPanelHeightForTest(String skin) {
        return scoreboardSettingsPanelHeight(skin);
    }

    static int settingsPanelWidthForTest(String key) {
        return settingsPanelWidthForKey(key);
    }

    static String headerInvalidNoticeForTest() {
        return CommandGtb.head + "Header must be at most \u00a7f"
                + ChatHeader.MAX_VISIBLE_LENGTH + "\u00a77 visible characters.";
    }

    static int settingsPanelHeightForTest(String key, boolean handheldVerticalLayout) {
        if ("hint".equals(key)) return 154;
        if ("cooldown".equals(key)) return 142;
        if ("handheld".equals(key)) return handheldVerticalLayout ? 283 : 259;
        if ("environment".equals(key)) return 178;
        return 0;
    }

    private static String settingsTitle(Label label) {
        String key = label.target.key;
        if ("scoreboard".equals(key)) return "Custom Scoreboard";
        if ("hint".equals(key)) return "Hint Structure";
        if ("actionbar".equals(key)) return "Actionbar";
        if ("cooldown".equals(key)) return "Cooldown Counter";
        if ("handheld".equals(key)) return "Handheld Items";
        if (key.startsWith("environment")) return "Environment";
        return "Settings";
    }

    private static String panelFamily(Label label) {
        if (label == null || label.target == null) return "";
        return label.target.key.startsWith("environment") ? "environment" : label.target.key;
    }

    private boolean shouldApplyArrowMove(int arrowMask) {
        if (arrowMask == 0) {
            resetArrowRepeat();
            return false;
        }
        if (arrowMask != previousArrowMask) {
            previousArrowMask = arrowMask;
            arrowHoldFrames = 0;
            arrowRepeatFrames = 0;
            return true;
        }
        arrowHoldFrames++;
        arrowRepeatFrames++;
        if (arrowMoveAllowed(previousArrowMask, arrowMask, arrowRepeatFrames, arrowHoldFrames)) {
            arrowRepeatFrames = 0;
            return true;
        }
        return false;
    }

    private void resetArrowRepeat() {
        previousArrowMask = 0;
        arrowHoldFrames = 0;
        arrowRepeatFrames = 0;
        arrowUndoSnapshot = null;
    }

    private static void applyCooldownState(CooldownState state) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        settings.chatCooldownCounter = state.counter;
        settings.chatCooldownTimerEnabled = state.timer;
        settings.chatCooldownBarEnabled = state.bar;
    }

    private static boolean environmentLabelVisible(GtbStructuresSettings settings) {
        return settings.environmentOverlay
                && (settings.environmentWeatherEnabled
                || settings.environmentBiomeEnabled
                || settings.environmentTimeEnabled);
    }

    private static boolean environmentRowVisible(String row) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if (!settings.environmentOverlay) return false;
        if ("weather".equals(row)) return settings.environmentWeatherEnabled;
        if ("biome".equals(row)) return settings.environmentBiomeEnabled;
        if ("time".equals(row)) return settings.environmentTimeEnabled;
        return false;
    }

    private static boolean environmentChatNoticeEnabled(String row) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if ("weather".equals(row)) return settings.environmentWeatherChatNotice;
        if ("biome".equals(row)) return settings.environmentBiomeChatNotice;
        if ("time".equals(row)) return settings.environmentTimeChatNotice;
        return false;
    }

    private void toggleEnvironmentChatNotice(String row) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        recordUndo();
        if ("weather".equals(row)) {
            settings.environmentWeatherChatNotice = !settings.environmentWeatherChatNotice;
        } else if ("biome".equals(row)) {
            settings.environmentBiomeChatNotice = !settings.environmentBiomeChatNotice;
        } else if ("time".equals(row)) {
            settings.environmentTimeChatNotice = !settings.environmentTimeChatNotice;
        }
        settings.save();
    }

    private void toggleEnvironmentRow(String row) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        boolean visible = environmentRowVisible(row);
        recordUndo();
        if ("weather".equals(row)) {
            settings.environmentWeatherEnabled = !visible;
        } else if ("biome".equals(row)) {
            settings.environmentBiomeEnabled = !visible;
        } else if ("time".equals(row)) {
            settings.environmentTimeEnabled = !visible;
        }
        if (!visible) settings.environmentOverlay = true;
        settings.save();
        if (visible) clearPanelForTarget("environment_" + row);
    }

    private void toggleEnvironmentOverlay() {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        recordUndo();
        if (environmentLabelVisible(settings)) {
            settings.environmentOverlay = false;
            settings.save();
            clearPanelForTargetFamily("environment");
            return;
        } else {
            settings.environmentOverlay = true;
            if (!settings.environmentWeatherEnabled
                    && !settings.environmentBiomeEnabled
                    && !settings.environmentTimeEnabled) {
                settings.environmentWeatherEnabled = true;
                settings.environmentBiomeEnabled = true;
                settings.environmentTimeEnabled = true;
            }
        }
        settings.save();
    }

    public static boolean selectedForTest(String current, String hovered, boolean modifier) {
        String selected = current == null ? "" : current;
        if (hovered == null || hovered.isEmpty()) return false;
        if (modifier && hovered.equals(selected)) selected = "";
        else selected = hovered;
        return hovered.equals(selected);
    }

    public static float moveTopLeftXForTest(float current, int screenWidth, int labelWidth, int dx) {
        return moveNormalizedLeft(current, screenWidth, labelWidth, dx);
    }

    public static float dragTopLeftXFromAnchorForTest(float start, int screenWidth, int labelWidth, int dx) {
        return moveNormalizedLeft(start, screenWidth, labelWidth, dx);
    }

    public static int moveScoreboardOffsetForTest(int currentOffset, int dy) {
        return clampScoreboardOffset(currentOffset - dy);
    }

    public static int panelXAfterDragForTest(int panelStart, int labelStart, int labelCurrent,
                                             int panelWidth, int screenWidth) {
        return followPanelCoordinate(panelStart, labelStart, labelCurrent,
                MARGIN, Math.max(MARGIN, screenWidth - panelWidth - MARGIN));
    }

    public static int panelXAfterLabelWidthChangeForTest(int panelStart, int panelWidth,
                                                         int oldLabelX1, int oldLabelX2,
                                                         int newLabelX1, int newLabelX2,
                                                         int screenWidth) {
        int side = panelAnchorSide(oldLabelX1, oldLabelX2, panelStart, panelStart + panelWidth);
        int oldAnchor = labelAnchorX(oldLabelX1, oldLabelX2, side);
        int newAnchor = labelAnchorX(newLabelX1, newLabelX2, side);
        return followPanelCoordinate(panelStart, oldAnchor, newAnchor,
                MARGIN, Math.max(MARGIN, screenWidth - panelWidth - MARGIN));
    }

    public static boolean cooldownLabelChecked(boolean counter, boolean timer, boolean bar) {
        return counter && (timer || bar);
    }

    public static CooldownState toggleCooldownLabelState(boolean counter, boolean timer, boolean bar) {
        if (cooldownLabelChecked(counter, timer, bar)) {
            return new CooldownState(false, timer, bar);
        }
        if (!timer && !bar) {
            return new CooldownState(true, true, true);
        }
        return new CooldownState(true, timer, bar);
    }

    public static CooldownState toggleCooldownPartState(boolean counter, boolean timer, boolean bar,
                                                       boolean timerPart) {
        boolean targetEnabled = timerPart ? timer : bar;
        boolean otherEnabled = timerPart ? bar : timer;
        if (targetEnabled && !otherEnabled) {
            return new CooldownState(false, timer, bar);
        }
        return timerPart
                ? new CooldownState(counter, !timer, bar)
                : new CooldownState(counter, timer, !bar);
    }

    public static boolean arrowMoveAllowedForTest(int previousMask, int currentMask, int repeatedFrames) {
        return arrowMoveAllowed(previousMask, currentMask, repeatedFrames, repeatedFrames);
    }

    public static boolean arrowMoveAllowedForTest(int previousMask, int currentMask,
                                                  int repeatedFrames, int holdFrames) {
        return arrowMoveAllowed(previousMask, currentMask, repeatedFrames, holdFrames);
    }

    public static int arrowRepeatIntervalForTest(int holdFrames) {
        return arrowRepeatInterval(holdFrames);
    }

    public static int toggleCheckYForTest(int rowY, int fontHeight) {
        return toggleCheckY(rowY, fontHeight);
    }

    public static int toggleTextYForTest(int rowY, int fontHeight) {
        return toggleTextY(rowY, fontHeight);
    }

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                        boolean shortcutReminder, boolean showNonAscii) {
        return labelsPanelHeightForTest(guessPeek, showMatching,
                shortcutReminder, showNonAscii, false, true, false, false);
    }

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                        boolean shortcutReminder, boolean showNonAscii,
                                        boolean showMultiwords) {
        return labelsPanelHeightForTest(guessPeek, showMatching,
                shortcutReminder, showNonAscii, showMultiwords, true, false, false);
    }

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                        boolean shortcutReminder, boolean showNonAscii,
                                        boolean showMultiwords, boolean themePointReveal) {
        return labelsPanelHeightForTest(guessPeek, showMatching, shortcutReminder, showNonAscii,
                showMultiwords, themePointReveal, false, false);
    }

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                         boolean shortcutReminder, boolean showNonAscii,
                                         boolean showMultiwords, boolean themePointReveal,
                                         boolean trollMode) {
        return labelsPanelHeightForTest(guessPeek, showMatching, shortcutReminder, showNonAscii,
                showMultiwords, themePointReveal, trollMode, false);
    }

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                        boolean shortcutReminder, boolean showNonAscii,
                                        boolean showMultiwords, boolean themePointReveal,
                                        boolean trollMode, boolean autoRequeue) {
        int rows = 6;
        rows += 3 + (autoRequeue ? 1 : 0);
        rows += 1 + (guessPeek ? 4 : 0);
        rows += 1 + (showMatching ? 2 : 0);
        rows += 1 + (themePointReveal ? 1 : 0);
        rows += 1;
        if (shortcutReminder) {
            rows += 3;
            if (showMultiwords) rows += 1;
            if (showNonAscii) rows += 5;
        }
        // Language Group Shortcuts and Manage Language Groups are always present.
        rows += 2;
        rows += 3 + (trollMode ? 1 : 0);
        rows += 1; // Bottom scroll clearance for the final Guess Sounds link.
        return PANEL_PAD * 2 + TITLE_HEIGHT + 4
                + rows * ROW_HEIGHT
                + (HEADER_ROW_HEIGHT - ROW_HEIGHT)
                + 5 * sectionBlockHeight();
    }

    static int headerBlockHeightForTest() {
        return sectionBlockHeight() + HEADER_ROW_HEIGHT;
    }

    static boolean autoRequeueDelayVisibleForTest(boolean autoRequeue) {
        return autoRequeue;
    }

    static String autoRequeueDelayIdleTextForTest(float value) {
        return "\u00a77" + GtbStructuresSettings.formatAutoRequeueDelaySeconds(value) + "s";
    }

    static int autoRequeueDelayEditorWidthForTest() {
        return AUTO_REQUEUE_DELAY_EDITOR_WIDTH;
    }

    static int autoRequeueDelayEditorTopForTest(int rowY) {
        return rowY - 1;
    }

    static int autoRequeueDelayEditorHeightForTest() {
        return ROW_HEIGHT + 2;
    }

    static boolean commitAutoRequeueDelayForTest(String requested) {
        float before = GtbStructuresSettings.INSTANCE.autoRequeueDelaySeconds;
        INSTANCE.commitAutoRequeueDelay(requested);
        return Float.compare(before, GtbStructuresSettings.INSTANCE.autoRequeueDelaySeconds) != 0;
    }

    static boolean toggleHitForTest(int x, int y, int textWidth, int mouseX, int mouseY) {
        int right = x + CHECK_SIZE + 5 + Math.max(0, textWidth);
        return mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
    }

    static int experimentalRowsForTest(boolean scrambleGuess) {
        return experimentalRowsForTest(scrambleGuess, false);
    }

    static int experimentalRowsForTest(boolean scrambleGuess, boolean trollMode) {
        return 3 + (scrambleGuess ? 1 : 0) + (trollMode ? 1 : 0);
    }

    public static void setShortcutReminderShowNonAscii(ShortcutReminder.Options options, boolean enabled) {
        if (options == null) return;
        if (enabled && !hasShortcutNonAsciiCategoryEnabled(options)) {
            options.showExtendedLatin = true;
        }
        options.showNonAscii = enabled;
    }

    public static void setShortcutNonAsciiCategoryEnabled(ShortcutReminder.Options options,
                                                          ShortcutNonAsciiCategory category,
                                                          boolean enabled) {
        if (options == null || category == null) return;
        if (!enabled && shortcutNonAsciiCategoryEnabled(options, category)
                && shortcutNonAsciiCategoryCount(options) == 1) {
            options.showNonAscii = false;
            return;
        }

        setShortcutNonAsciiCategoryValue(options, category, enabled);
        if (!hasShortcutNonAsciiCategoryEnabled(options)) {
            options.showNonAscii = false;
        }
    }

    public static boolean shortcutNonAsciiCategoryEnabled(ShortcutReminder.Options options,
                                                          ShortcutNonAsciiCategory category) {
        if (options == null || category == null) return false;
        if (category == ShortcutNonAsciiCategory.EXTENDED_LATIN) return options.showExtendedLatin;
        if (category == ShortcutNonAsciiCategory.CYRILLIC) return options.showCyrillic;
        if (category == ShortcutNonAsciiCategory.HANZI) return options.showHanzi;
        if (category == ShortcutNonAsciiCategory.JAPANESE) return options.showJapanese;
        return options.showHangul;
    }

    public static boolean hasShortcutNonAsciiCategoryEnabled(ShortcutReminder.Options options) {
        return shortcutNonAsciiCategoryCount(options) > 0;
    }

    private static int shortcutNonAsciiCategoryCount(ShortcutReminder.Options options) {
        int count = 0;
        if (options != null) {
            if (options.showExtendedLatin) count++;
            if (options.showCyrillic) count++;
            if (options.showHanzi) count++;
            if (options.showJapanese) count++;
            if (options.showHangul) count++;
        }
        return count;
    }

    private static void setShortcutNonAsciiCategoryValue(ShortcutReminder.Options options,
                                                         ShortcutNonAsciiCategory category,
                                                         boolean enabled) {
        if (category == ShortcutNonAsciiCategory.EXTENDED_LATIN) {
            options.showExtendedLatin = enabled;
        } else if (category == ShortcutNonAsciiCategory.CYRILLIC) {
            options.showCyrillic = enabled;
        } else if (category == ShortcutNonAsciiCategory.HANZI) {
            options.showHanzi = enabled;
        } else if (category == ShortcutNonAsciiCategory.JAPANESE) {
            options.showJapanese = enabled;
        } else {
            options.showHangul = enabled;
        }
    }

    private static boolean arrowMoveAllowed(int previousMask, int currentMask,
                                            int repeatedFrames, int holdFrames) {
        if (currentMask == 0) return false;
        if (currentMask != previousMask) return true;
        return repeatedFrames >= arrowRepeatInterval(holdFrames);
    }

    private static int arrowRepeatInterval(int holdFrames) {
        return holdFrames >= ARROW_ACCELERATION_START_FRAMES
                ? ARROW_ACCELERATED_REPEAT_FRAMES
                : ARROW_REPEAT_FRAMES;
    }

    private static int toggleTextY(int rowY, int fontHeight) {
        return rowY + (ROW_HEIGHT - fontHeight) / 2;
    }

    private static int toggleCheckY(int rowY, int fontHeight) {
        return toggleTextY(rowY, fontHeight) - 1;
    }

    private static int sectionBlockHeight() {
        return SECTION_MARGIN_Y * 2 + SECTION_TEXT_HEIGHT + 1;
    }

    private int pinnedPanelXFollowingLabel(Rect label, int panelWidth, int screenWidth) {
        if (!pinnedPanelAnchorValid) return pinnedPanelX;
        return followPanelCoordinate(pinnedPanelX, pinnedPanelAnchorX,
                labelAnchorX(label, pinnedPanelAnchorSide), MARGIN,
                Math.max(MARGIN, screenWidth - panelWidth - MARGIN));
    }

    private void rememberPinnedPanelAnchor(Label label, Rect panel) {
        rememberPinnedPanelAnchor(label == null ? null : new Rect(label.x1, label.y1, label.x2, label.y2), panel);
    }

    private void rememberPinnedPanelAnchor(Rect label, Rect panel) {
        if (label == null || panel == null) {
            pinnedPanelAnchorValid = false;
            return;
        }
        pinnedPanelAnchorSide = panelAnchorSide(label, panel);
        pinnedPanelAnchorX = labelAnchorX(label, pinnedPanelAnchorSide);
        pinnedPanelAnchorValid = true;
    }

    private static int panelAnchorSide(Label label, Rect panel) {
        return panelAnchorSide(label.x1, label.x2, panel.x1, panel.x2);
    }

    private static int panelAnchorSide(Rect label, Rect panel) {
        return panelAnchorSide(label.x1, label.x2, panel.x1, panel.x2);
    }

    private static int panelAnchorSide(int labelX1, int labelX2, int panelX1, int panelX2) {
        int leftGap = Math.abs(panelX2 - labelX1);
        int rightGap = Math.abs(panelX1 - labelX2);
        return rightGap < leftGap ? 1 : -1;
    }

    private static int labelAnchorX(Label label, int side) {
        return labelAnchorX(label.x1, label.x2, side);
    }

    private static int labelAnchorX(Rect label, int side) {
        return labelAnchorX(label.x1, label.x2, side);
    }

    private static int labelAnchorX(int labelX1, int labelX2, int side) {
        return side > 0 ? labelX2 : labelX1;
    }

    private static int followPanelCoordinate(int panelStart, int labelStart, int labelCurrent,
                                             int min, int max) {
        int value = panelStart + labelCurrent - labelStart;
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static float moveNormalizedLeft(float current, int screenWidth, int labelWidth, int dx) {
        int min = MARGIN;
        int max = Math.max(min, screenWidth - labelWidth - MARGIN);
        int currentLeft = HudPosition.left(current, screenWidth, labelWidth, MARGIN);
        return normalizedPoint(currentLeft + dx, min, max);
    }

    private static float moveNormalizedTop(float current, int screenHeight, int labelHeight, int dy) {
        int min = MARGIN;
        int max = Math.max(min, screenHeight - labelHeight - MARGIN);
        int currentTop = HudPosition.top(current, screenHeight, labelHeight, MARGIN);
        return normalizedPoint(currentTop + dy, min, max);
    }

    private static float moveNormalizedBottom(float current, int screenHeight, int labelHeight, int dy) {
        int min = labelHeight + MARGIN;
        int max = Math.max(min, screenHeight - MARGIN);
        int currentBottom = HudPosition.bottom(current, screenHeight, labelHeight, MARGIN);
        return normalizedPoint(currentBottom + dy, min, max);
    }

    private static float normalizedPoint(int value, int min, int max) {
        if (max <= min) return 0F;
        if (value < min) value = min;
        if (value > max) value = max;
        return (float) (value - min) / (float) (max - min);
    }

    private static int clampScoreboardOffset(int value) {
        if (value < -200) return -200;
        if (value > 200) return 200;
        return value;
    }

    private static Rect toggleRect(int x, int y, int textWidth) {
        int right = x + CHECK_SIZE + 5 + Math.max(0, textWidth);
        return new Rect(x, y, right, y + ROW_HEIGHT);
    }

    private static Rect triStateRowRect(int x, int y, int textWidth) {
        return toggleRect(x, y, textWidth);
    }

    private static Rect triStateButtonRect(int x, int y, int fontHeight) {
        int checkY = toggleCheckY(y, fontHeight);
        return new Rect(x, checkY, x + CHECK_SIZE, checkY + CHECK_SIZE);
    }

    private static Rect sliderRowRect(int x, int y) {
        return new Rect(x, y, x + 196, y + SLIDER_ROW_HEIGHT);
    }

    private static Rect sliderTrackRect(int x, int y) {
        int trackX = x + 118;
        int cy = y + SLIDER_ROW_HEIGHT / 2 - 2;
        return new Rect(trackX, cy - 2, trackX + SLIDER_WIDTH, cy + 3);
    }

    private static float sliderValueAt(int x, int mouseX) {
        Rect track = sliderTrackRect(x, 0);
        return HudPosition.clamp01((float) (mouseX - track.x1) / (float) SLIDER_WIDTH);
    }

    static int sliderTrackCenterYForTest(int x, int y) {
        Rect track = sliderTrackRect(x, y);
        return track.y1 + track.height() / 2;
    }

    static float sliderValueForTest(int x, int mouseX) {
        return sliderValueAt(x, mouseX);
    }

    static int panelScaleSliderTrackXForTest(int x, int captionWidth) {
        return sliderTrackRect(x, 0, captionWidth).x1;
    }

    static int panelScaleSliderValueFieldXForTest(int x, int captionWidth) {
        return sliderValueFieldRect(x, 0, captionWidth).x1;
    }

    static int sliderHoverLeftForTest(int x, int y, int captionWidth) {
        return sliderHighlightRect(x, y, captionWidth).x1;
    }

    static int sliderHoverRightForTest(int x, int y, int captionWidth) {
        return sliderHighlightRect(x, y, captionWidth).x2;
    }

    static int sliderHoverTopForTest(int x, int y, int captionWidth) {
        return sliderHighlightRect(x, y, captionWidth).y1;
    }

    static int sliderHoverBottomForTest(int x, int y, int captionWidth) {
        return sliderHighlightRect(x, y, captionWidth).y2;
    }

    static boolean sliderHitForTest(int x, int y, int captionWidth, int mouseX, int mouseY) {
        return sliderRowRect(x, y, captionWidth).contains(mouseX, mouseY);
    }

    static int sliderFillEndForTest(int trackX, int knobCenter) {
        return sliderFillEnd(trackX, knobCenter);
    }

    static String sliderInputDisplayTextForTest(String sliderId, float value) {
        SliderSpec slider = sliderSpec(sliderId);
        return slider == null ? "" : slider.formatInput(value);
    }

    static float sliderValueAfterCommitForTest(String sliderId, float current,
                                                String requested, boolean dirty) {
        if (!dirty) return current;
        SliderSpec slider = sliderSpec(sliderId);
        if (slider == null) return current;
        Float parsed = slider.parse(requested);
        return parsed == null ? current : parsed.floatValue();
    }

    private static Rect sliderTrackRect(int x, int y, int captionWidth) {
        int trackX = x + Math.max(0, captionWidth) + 7;
        int trackY = y + (SLIDER_ROW_HEIGHT - 10) / 2 - 4;
        return new Rect(trackX, trackY, trackX + SLIDER_WIDTH, trackY + 10);
    }

    private static Rect sliderRowRect(int x, int y, int captionWidth) {
        Rect track = sliderTrackRect(x, y, captionWidth);
        return new Rect(track.x1 - 3, y - 1, track.x2 + 5, y + SLIDER_ROW_HEIGHT - 1);
    }

    private static Rect sliderHighlightRect(int x, int y, int captionWidth) {
        Rect row = sliderRowRect(x, y, captionWidth);
        Rect track = sliderTrackRect(x, y, captionWidth);
        int centerY = track.y1 + track.height() / 2;
        return new Rect(row.x1, centerY - (SLIDER_ROW_HEIGHT - 1) / 2,
                row.x2, centerY + SLIDER_ROW_HEIGHT / 2);
    }

    private static Rect sliderValueFieldRect(int x, int y, int captionWidth) {
        Rect row = sliderRowRect(x, y, captionWidth);
        int fieldX = row.x2 + SLIDER_VALUE_EDITOR_GAP;
        return new Rect(fieldX, y - 2, fieldX + SLIDER_VALUE_EDITOR_WIDTH,
                y + SLIDER_ROW_HEIGHT - 4);
    }

    private static int sliderFillEnd(int trackX, int knobCenter) {
        return Math.max(trackX, knobCenter - 1);
    }

    private static final SliderSpec BACKGROUND_OPACITY_SLIDER =
            new SliderSpec("backgroundOpacity", "Background Opacity", 0F, 1F);
    private static final SliderSpec HIGHLIGHT_INTENSITY_SLIDER =
            new SliderSpec("highlightIntensity", "Highlight Intensity", 0F, 1F);
    private static final SliderSpec HINT_SCALE_SLIDER =
            new SliderSpec("hintStructureScale", "Scale", HudPanelScale.MIN, HudPanelScale.MAX);
    private static final SliderSpec COOLDOWN_SCALE_SLIDER =
            new SliderSpec("chatCooldownScale", "Scale", HudPanelScale.MIN, HudPanelScale.MAX);
    private static final SliderSpec HANDHELD_SCALE_SLIDER =
            new SliderSpec("handheldItemScale", "Scale", HudPanelScale.MIN, HudPanelScale.MAX);
    private static final SliderSpec ENVIRONMENT_SCALE_SLIDER =
            new SliderSpec("environmentScale", "Scale", HudPanelScale.MIN, HudPanelScale.MAX);

    private static final SliderSpec GUESS_SOUND_VOLUME_SLIDER =
            new SliderSpec("guessSoundVolume", "Overall volume", 0F, GuessSounds.MAX_VOLUME);

    private static SliderSpec sliderSpec(String id) {
        if (GUESS_SOUND_VOLUME_SLIDER.id.equals(id)) return GUESS_SOUND_VOLUME_SLIDER;
        if (BACKGROUND_OPACITY_SLIDER.id.equals(id)) return BACKGROUND_OPACITY_SLIDER;
        if (HIGHLIGHT_INTENSITY_SLIDER.id.equals(id)) return HIGHLIGHT_INTENSITY_SLIDER;
        if (HINT_SCALE_SLIDER.id.equals(id)) return HINT_SCALE_SLIDER;
        if (COOLDOWN_SCALE_SLIDER.id.equals(id)) return COOLDOWN_SCALE_SLIDER;
        if (HANDHELD_SCALE_SLIDER.id.equals(id)) return HANDHELD_SCALE_SLIDER;
        if (ENVIRONMENT_SCALE_SLIDER.id.equals(id)) return ENVIRONMENT_SCALE_SLIDER;
        return null;
    }

    private void beginDiscreteScaleChange(String panel) {
        activeDiscreteScaleChange = null;
        String family = normalizeScalePanelFamily(panel);
        if (!scaleChangeMatchesPreview(family)) return;
        Label label = activePanelLabel();
        if (label == null) return;
        activeDiscreteScaleChange = new ScaleChangeAnchor(family, panelScale(family),
                label.x1, label.y1, label.x2, label.y2, pinnedPanelX, pinnedPanelY,
                pinnedPanelAnchorValid ? pinnedPanelAnchorSide : -1);
    }

    private void endDiscreteScaleChange(String panel) {
        ScaleChangeAnchor anchor = activeDiscreteScaleChange;
        activeDiscreteScaleChange = null;
        String family = normalizeScalePanelFamily(panel);
        if (anchor == null || !anchor.family.equals(family)
                || Float.compare(anchor.scale, panelScale(family)) == 0) return;
        pendingDiscreteScaleChange = anchor;
    }

    private boolean scaleChangeMatchesPreview(String family) {
        if (!InterfacePreview.isActive() || !isBottomLeftScaleFamily(family)
                || !family.equals(pinnedPanelFamily)) return false;
        Label label = activePanelLabel();
        return label != null && family.equals(panelFamily(label));
    }

    private static String normalizeScalePanelFamily(String panel) {
        if (panel == null) return "";
        String family = panel.toLowerCase(Locale.ROOT);
        if (family.startsWith("environment")) return "environment";
        if ("handhelditem".equals(family)) return "handheld";
        return family;
    }

    private static boolean isBottomLeftScaleFamily(String family) {
        return "handheld".equals(family) || "environment".equals(family);
    }

    private static float panelScale(String family) {
        GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
        if ("hint".equals(family)) return settings.hintStructureScale;
        if ("cooldown".equals(family)) return settings.chatCooldownScale;
        if ("handheld".equals(family)) return settings.handheldItemScale;
        if ("environment".equals(family)) return settings.environmentScale;
        return Float.NaN;
    }

    private void applyPendingDiscreteScaleChange(int screenWidth, int screenHeight) {
        ScaleChangeAnchor anchor = pendingDiscreteScaleChange;
        if (anchor == null) return;
        pendingDiscreteScaleChange = null;
        if (!scaleChangeMatchesPreview(anchor.family)) return;
        Label label = activePanelLabel();
        if (label == null || !anchor.family.equals(panelFamily(label))) return;
        ScaleChangePlacement placement = discreteScalePlacement(anchor.x1, anchor.y1, anchor.x2, anchor.y2,
                label.x1, label.y1, label.x2, label.y2, anchor.menuX, anchor.menuY, anchor.anchorSide);
        int dx = placement.labelX - label.x1;
        int dy = placement.labelY - label.y1;
        label.target.movePixels(dx, dy, label.width(), label.height(), screenWidth, screenHeight);
        for (int i = 0; i < labels.size(); i++) {
            Label current = labels.get(i);
            if (anchor.family.equals(panelFamily(current))) {
                labels.set(i, new Label(current.id, current.x1 + dx, current.y1 + dy,
                        current.x2 + dx, current.y2 + dy, current.target));
            }
        }
        Label adjusted = activePanelLabel();
        if (adjusted == null) return;
        Rect menu = clampPanelRect(placement.menuX, placement.menuY,
                settingsPanelWidth(adjusted), settingsPanelHeight(adjusted), screenWidth, screenHeight);
        pinnedPanelX = menu.x1;
        pinnedPanelY = menu.y1;
        rememberPinnedPanelAnchor(adjusted, menu);
        GtbStructuresSettings.INSTANCE.save();
    }

    private boolean scaleDragMenuFrozenForFamily(String family) {
        return !frozenScaleDragFamily.isEmpty() && frozenScaleDragFamily.equals(family);
    }

    private void rebaseScaleDragMenu(int screenWidth, int screenHeight) {
        if (!scaleDragMenuRebasePending || !activeSlider.isEmpty()) return;
        Label label = activePanelLabel();
        if (label != null && frozenScaleDragFamily.equals(panelFamily(label))) {
            Rect menu = clampPanelRect(pinnedPanelX, pinnedPanelY,
                    settingsPanelWidth(label), settingsPanelHeight(label), screenWidth, screenHeight);
            pinnedPanelX = menu.x1;
            pinnedPanelY = menu.y1;
            rememberPinnedPanelAnchor(label, menu);
        }
        scaleDragMenuRebasePending = false;
        frozenScaleDragFamily = "";
    }

    static ScaleChangePlacement discreteScalePlacementForTest(
            int oldX1, int oldY1, int oldX2, int oldY2,
            int newX1, int newY1, int newX2, int newY2,
            int menuX, int menuY, int anchorSide) {
        return discreteScalePlacement(oldX1, oldY1, oldX2, oldY2,
                newX1, newY1, newX2, newY2, menuX, menuY, anchorSide);
    }

    private static ScaleChangePlacement discreteScalePlacement(
            int oldX1, int oldY1, int oldX2, int oldY2,
            int newX1, int newY1, int newX2, int newY2,
            int menuX, int menuY, int anchorSide) {
        int adjustedX = oldX1;
        int adjustedY = oldY2 - (newY2 - newY1);
        int oldAnchorX = labelAnchorX(oldX1, oldX2, anchorSide);
        int newAnchorX = labelAnchorX(adjustedX, adjustedX + (newX2 - newX1), anchorSide);
        return new ScaleChangePlacement(adjustedX, adjustedY,
                menuX + newAnchorX - oldAnchorX, menuY + adjustedY - oldY1);
    }

    static final class ScaleChangePlacement {
        final int labelX;
        final int labelY;
        final int menuX;
        final int menuY;

        private ScaleChangePlacement(int labelX, int labelY, int menuX, int menuY) {
            this.labelX = labelX;
            this.labelY = labelY;
            this.menuX = menuX;
            this.menuY = menuY;
        }
    }

    private static final class ScaleChangeAnchor {
        private final String family;
        private final float scale;
        private final int x1;
        private final int y1;
        private final int x2;
        private final int y2;
        private final int menuX;
        private final int menuY;
        private final int anchorSide;

        private ScaleChangeAnchor(String family, float scale, int x1, int y1, int x2, int y2,
                                  int menuX, int menuY, int anchorSide) {
            this.family = family;
            this.scale = scale;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.menuX = menuX;
            this.menuY = menuY;
            this.anchorSide = anchorSide;
        }
    }

    private static final class SliderSpec {
        private final String id;
        private final String caption;
        private final float min;
        private final float max;

        private SliderSpec(String id, String caption, float min, float max) {
            this.id = id;
            this.caption = caption;
            this.min = min;
            this.max = max;
        }

        private float value() {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if ("guessSoundVolume".equals(id)) return settings.guessSoundVolume;
            if ("backgroundOpacity".equals(id)) return settings.sbConfig.backgroundOpacity;
            if ("highlightIntensity".equals(id)) return settings.sbConfig.highlightIntensity;
            if ("hintStructureScale".equals(id)) return settings.hintStructureScale;
            if ("chatCooldownScale".equals(id)) return settings.chatCooldownScale;
            if ("handheldItemScale".equals(id)) return settings.handheldItemScale;
            return settings.environmentScale;
        }

        private void setValue(float value) {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            value = isScale() ? HudPanelScale.normalize(value) : Math.max(min, Math.min(max, value));
            if ("guessSoundVolume".equals(id)) settings.guessSoundVolume = value;
            else if ("backgroundOpacity".equals(id)) settings.sbConfig.backgroundOpacity = value;
            else if ("highlightIntensity".equals(id)) settings.sbConfig.highlightIntensity = value;
            else if ("hintStructureScale".equals(id)) settings.hintStructureScale = value;
            else if ("chatCooldownScale".equals(id)) settings.chatCooldownScale = value;
            else if ("handheldItemScale".equals(id)) settings.handheldItemScale = value;
            else settings.environmentScale = value;
        }

        private float fractionForValue(float value) {
            return HudPosition.clamp01((value - min) / (max - min));
        }

        private float valueAtFraction(float fraction) {
            return min + HudPosition.clamp01(fraction) * (max - min);
        }

        private Float parse(String raw) {
            return "guessSoundVolume".equals(id) ? GtbStructuresSettings.parseGuessSoundVolume(raw)
                    : isScale() ? HudPanelScale.parse(raw) : null;
        }

        private String formatDisplay(float value) {
            if ("guessSoundVolume".equals(id)) return GtbStructuresSettings.formatGuessSoundVolume(value);
            return isScale() ? HudPanelScale.formatPercent(value) : String.valueOf(Math.round(value * 100F)) + "%";
        }

        private String formatInput(float value) {
            if ("guessSoundVolume".equals(id)) return GtbStructuresSettings.formatGuessSoundVolume(value);
            return new BigDecimal(Float.toString(value))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
        }

        private boolean isScale() {
            return id.endsWith("Scale");
        }

        private String scalePanelFamily() {
            if ("hintStructureScale".equals(id)) return "hint";
            if ("chatCooldownScale".equals(id)) return "cooldown";
            if ("handheldItemScale".equals(id)) return "handheld";
            if ("environmentScale".equals(id)) return "environment";
            return "";
        }

        private boolean bottomLeftScale() {
            return isBottomLeftScaleFamily(scalePanelFamily());
        }

        private String invalidNotice() {
            if ("guessSoundVolume".equals(id)) return CommandGtb.head + "Volume must be between 0.0 and 30.0.";
            return CommandGtb.head + "Usage: /gtbs " + panelScaleCommandName(scalePanelFamily())
                    + " scale [0.5-1.5|50%-150%]";
        }
    }

    private static String panelScaleCommandName(String family) {
        if ("hint".equals(family)) return "hintstructure";
        if ("cooldown".equals(family)) return "cooldowncounter";
        if ("handheld".equals(family)) return "handhelditem";
        return "environment";
    }

    public enum ShortcutNonAsciiCategory {
        EXTENDED_LATIN,
        CYRILLIC,
        HANZI,
        JAPANESE,
        HANGUL
    }

    private enum MoveMode {
        FIXED,
        TOP_LEFT,
        BOTTOM_LEFT,
        SCOREBOARD_Y
    }

    public static final class Target {
        public final String key;
        private final MoveMode moveMode;
        private final boolean moveX;
        private final boolean moveY;

        private Target(String key, MoveMode moveMode, boolean moveX, boolean moveY) {
            this.key = key == null ? "" : key.toLowerCase(Locale.ROOT);
            this.moveMode = moveMode;
            this.moveX = moveX;
            this.moveY = moveY;
        }

        public boolean canMoveX() {
            return moveX;
        }

        public boolean canMoveY() {
            return moveY;
        }

        public boolean movesScoreboardY() {
            return moveMode == MoveMode.SCOREBOARD_Y;
        }

        private boolean canMove() {
            return moveX || moveY;
        }

        private boolean canDelete() {
            return !"actionbar".equals(key);
        }

        private void movePixels(int dx, int dy, int width, int height, int screenWidth, int screenHeight) {
            if (!moveX) dx = 0;
            if (!moveY) dy = 0;
            if (dx == 0 && dy == 0) return;

            if (moveMode == MoveMode.SCOREBOARD_Y) {
                if (dy != 0) GtbStructuresSettings.INSTANCE.sbConfig.heightOffset =
                        clampScoreboardOffset(GtbStructuresSettings.INSTANCE.sbConfig.heightOffset - dy);
                return;
            }

            if (moveMode == MoveMode.BOTTOM_LEFT) {
                if (dx != 0) setX(moveNormalizedLeft(x(), screenWidth, width, dx));
                if (dy != 0) setY(moveNormalizedBottom(y(), screenHeight, height, dy));
                return;
            }

            if (dx != 0) setX(moveNormalizedLeft(x(), screenWidth, width, dx));
            if (dy != 0) setY(moveNormalizedTop(y(), screenHeight, height, dy));
        }

        private void moveFromAnchor(int dx, int dy, int width, int height, int screenWidth, int screenHeight,
                                    float startX, float startY, int startScoreboardOffset) {
            if (!moveX) dx = 0;
            if (!moveY) dy = 0;
            if (dx == 0 && dy == 0) return;

            if (moveMode == MoveMode.SCOREBOARD_Y) {
                if (dy != 0) GtbStructuresSettings.INSTANCE.sbConfig.heightOffset =
                        clampScoreboardOffset(startScoreboardOffset - dy);
                return;
            }

            if (moveMode == MoveMode.BOTTOM_LEFT) {
                if (dx != 0) setX(moveNormalizedLeft(startX, screenWidth, width, dx));
                if (dy != 0) setY(moveNormalizedBottom(startY, screenHeight, height, dy));
                return;
            }

            if (dx != 0) setX(moveNormalizedLeft(startX, screenWidth, width, dx));
            if (dy != 0) setY(moveNormalizedTop(startY, screenHeight, height, dy));
        }

        private void disable() {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if ("actionbar".equals(key)) {
                InterfacePreview.setMockActionbarVisible(false);
            } else if ("hint".equals(key)) {
                settings.enabled = false;
            } else if ("cooldown".equals(key)) {
                settings.chatCooldownCounter = false;
            } else if ("handheld".equals(key)) {
                settings.handheldItemOverlay = false;
            } else if ("environment_weather".equals(key)) {
                settings.environmentWeatherEnabled = false;
            } else if ("environment_biome".equals(key)) {
                settings.environmentBiomeEnabled = false;
            } else if ("environment_time".equals(key)) {
                settings.environmentTimeEnabled = false;
            } else if ("environment".equals(key)) {
                settings.environmentOverlay = false;
            } else if ("scoreboard".equals(key)) {
                settings.sbConfig.setSkin("vanilla");
            }
        }

        private float x() {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if ("hint".equals(key)) return settings.posX;
            if ("cooldown".equals(key)) return settings.chatCooldownPosX;
            if ("handheld".equals(key)) return settings.handheldItemPosX;
            return settings.environmentPosX;
        }

        private float y() {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if ("hint".equals(key)) return settings.posY;
            if ("cooldown".equals(key)) return settings.chatCooldownPosY;
            if ("handheld".equals(key)) return settings.handheldItemPosY;
            return settings.environmentPosY;
        }

        private void setX(float value) {
            value = HudPosition.clamp01(value);
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if ("hint".equals(key)) {
                settings.posX = value;
            } else if ("cooldown".equals(key)) {
                settings.chatCooldownPosX = value;
            } else if ("handheld".equals(key)) {
                settings.handheldItemPosX = value;
            } else {
                settings.environmentPosX = value;
            }
        }

        private void setY(float value) {
            value = HudPosition.clamp01(value);
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            if ("hint".equals(key)) {
                settings.posY = value;
            } else if ("cooldown".equals(key)) {
                settings.chatCooldownPosY = value;
            } else if ("handheld".equals(key)) {
                settings.handheldItemPosY = value;
            } else {
                settings.environmentPosY = value;
            }
        }
    }

    public static final class CooldownState {
        public final boolean counter;
        public final boolean timer;
        public final boolean bar;

        CooldownState(boolean counter, boolean timer, boolean bar) {
            this.counter = counter;
            this.timer = timer;
            this.bar = bar;
        }
    }

    private static final class ConfigSnapshot {
        private final String guessSound1, guessSound2, guessSound3;
        private final float guessSoundVolume;
        private final boolean modEnabled;
        private final boolean enabled;
        private final boolean hintBackgroundOverlay;
        private final boolean hintStructureBackground;
        private final boolean handheldItemOverlay;
        private final boolean handheldItemBackground;
        private final boolean handheldItemCaptionName;
        private final boolean handheldItemCaptionId;
        private final boolean handheldItemCaptionMetadata;
        private final boolean handheldItemCaptionSpawnEggMob;
        private final boolean handheldItemVerticalLayout;
        private final boolean handheldItemVerticalNewestAtBottom;
        private final boolean handheldItemHorizontalNewestAtRight;
        private final boolean handheldItemHorizontalNewestAtBottom;
        private final boolean environmentOverlay;
        private final boolean environmentBackground;
        private final boolean environmentWeatherEnabled;
        private final boolean environmentBiomeEnabled;
        private final boolean environmentTimeEnabled;
        private final boolean environmentWeatherChatNotice;
        private final boolean environmentBiomeChatNotice;
        private final boolean environmentTimeChatNotice;
        private final boolean chatCooldownCounter;
        private final boolean chatCooldownTimerEnabled;
        private final boolean chatCooldownBarEnabled;
        private final boolean chatCooldownBackground;
        private final boolean chatCooldownBarBackground;
        private final boolean scoreboardEnabled;
        private final float scoreboardBackgroundOpacity;
        private final float scoreboardHighlightIntensity;
        private final float hintStructureScale;
        private final float chatCooldownScale;
        private final float handheldItemScale;
        private final float environmentScale;
        private final ScoreboardConfig.DisplayOption scoreboardShowPointsGainedInRound;
        private final ScoreboardConfig.DisplayOption scoreboardShowPlaces;
        private final ScoreboardConfig.DisplayOption scoreboardShowTitles;
        private final ScoreboardConfig.DisplayOption scoreboardShowEmblems;
        private final boolean scoreboardDefaultExplicitlyShowBuilders;
        private final ScoreboardConfig.DisplayOption scoreboardHypixelShowRounds;
        private final boolean scoreboardLeftPlayersAtBottom;
        private final boolean scoreboardTextShadow;
        private final boolean trackingEnabled;
        private final boolean autoRequeue;
        private final boolean lrjBypassLobbyProtection;
        private final float autoRequeueDelaySeconds;
        private final boolean guessPeek;
        private final boolean guessPeekShowLangCodes;
        private final String guessPeekDisplay;
        private final boolean scrambleGuess;
        private final boolean scrambleGuessFancy;
        private final boolean trollMode;
        private final boolean trollModeAutoDisable;
        private final String chatHeaderRaw;
        private final boolean showAllMatching;
        private final boolean showAllMatchingShowEnglish;
        private final boolean showAllMatchingShowCount;
        private final boolean gtwShowCount;
        private final boolean themePointReveal;
        private final boolean themePointRevealShowAfterGuess;
        private final boolean shortcutReminder;
        private final boolean shortcutReminderShowNonAscii;
        private final boolean shortcutReminderShowExtendedLatin;
        private final boolean shortcutReminderShowCyrillic;
        private final boolean shortcutReminderShowHanzi;
        private final boolean shortcutReminderShowJapanese;
        private final boolean shortcutReminderShowHangul;
        private final boolean shortcutReminderShowAllShortcuts;
        private final boolean shortcutReminderShowMultiwords;
        private final boolean shortcutReminderShowAllMultiwords;
        private final boolean copyTranslationPrefix;
        private final boolean copyTranslationCaps;
        private final boolean gtwShowEnglish;
        private final float posX;
        private final float posY;
        private final float handheldItemPosX;
        private final float handheldItemPosY;
        private final float environmentPosX;
        private final float environmentPosY;
        private final float chatCooldownPosX;
        private final float chatCooldownPosY;
        private final int handheldItemMaxVisible;
        private final float handheldItemPanelWidthRatio;
        private final float handheldItemPanelHeightRatio;
        private final int scoreboardHeightOffset;
        private final String spawnEggSkin;
        private final String environmentTimeIconPreset;
        private final String colorNumbers;
        private final String colorHyphens;
        private final String colorBrackets;
        private final String chatCooldownBarColor;
        private final String chatCooldownTimerColor;
        private final String scoreboardSkin;
        private final ShortcutLanguageGroups.State shortcutLanguageGroups;

        private ConfigSnapshot() {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            guessSound1 = settings.guessSound1; guessSound2 = settings.guessSound2; guessSound3 = settings.guessSound3;
            guessSoundVolume = settings.guessSoundVolume;
            modEnabled = settings.modEnabled;
            enabled = settings.enabled;
            hintBackgroundOverlay = settings.hintBackgroundOverlay;
            hintStructureBackground = settings.hintStructureBackground;
            handheldItemOverlay = settings.handheldItemOverlay;
            handheldItemBackground = settings.handheldItemBackground;
            handheldItemCaptionName = settings.handheldItemCaptionName;
            handheldItemCaptionId = settings.handheldItemCaptionId;
            handheldItemCaptionMetadata = settings.handheldItemCaptionMetadata;
            handheldItemCaptionSpawnEggMob = settings.handheldItemCaptionSpawnEggMob;
            handheldItemVerticalLayout = settings.handheldItemVerticalLayout;
            handheldItemVerticalNewestAtBottom = settings.handheldItemVerticalNewestAtBottom;
            handheldItemHorizontalNewestAtRight = settings.handheldItemHorizontalNewestAtRight;
            handheldItemHorizontalNewestAtBottom = settings.handheldItemHorizontalNewestAtBottom;
            environmentOverlay = settings.environmentOverlay;
            environmentBackground = settings.environmentBackground;
            environmentWeatherEnabled = settings.environmentWeatherEnabled;
            environmentBiomeEnabled = settings.environmentBiomeEnabled;
            environmentTimeEnabled = settings.environmentTimeEnabled;
            environmentWeatherChatNotice = settings.environmentWeatherChatNotice;
            environmentBiomeChatNotice = settings.environmentBiomeChatNotice;
            environmentTimeChatNotice = settings.environmentTimeChatNotice;
            chatCooldownCounter = settings.chatCooldownCounter;
            chatCooldownTimerEnabled = settings.chatCooldownTimerEnabled;
            chatCooldownBarEnabled = settings.chatCooldownBarEnabled;
            chatCooldownBackground = settings.chatCooldownBackground;
            chatCooldownBarBackground = settings.chatCooldownBarBackground;
            scoreboardEnabled = settings.sbConfig.isCustomEnabled();
            scoreboardBackgroundOpacity = settings.sbConfig.backgroundOpacity;
            scoreboardHighlightIntensity = settings.sbConfig.highlightIntensity;
            hintStructureScale = settings.hintStructureScale;
            chatCooldownScale = settings.chatCooldownScale;
            handheldItemScale = settings.handheldItemScale;
            environmentScale = settings.environmentScale;
            scoreboardShowPointsGainedInRound = settings.sbConfig.showPointsGainedInRound;
            scoreboardShowPlaces = settings.sbConfig.showPlaces;
            scoreboardShowTitles = settings.sbConfig.showTitles;
            scoreboardShowEmblems = settings.sbConfig.showEmblems;
            scoreboardDefaultExplicitlyShowBuilders = settings.sbConfig.defaultExplicitlyShowBuilders;
            scoreboardHypixelShowRounds = settings.sbConfig.hypixelShowRounds;
            scoreboardLeftPlayersAtBottom = settings.sbConfig.leftPlayersAtBottom;
            scoreboardTextShadow = settings.sbConfig.textShadow;
            trackingEnabled = settings.trackingEnabled;
            autoRequeue = settings.autoRequeue;
            lrjBypassLobbyProtection = settings.lrjBypassLobbyProtection;
            autoRequeueDelaySeconds = settings.autoRequeueDelaySeconds;
            guessPeek = settings.guessPeek;
            guessPeekShowLangCodes = settings.guessPeekShowLangCodes;
            guessPeekDisplay = settings.guessPeekDisplay;
            scrambleGuess = settings.scrambleGuess;
            scrambleGuessFancy = settings.scrambleGuessFancy;
            trollMode = settings.trollMode;
            trollModeAutoDisable = settings.trollModeAutoDisable;
            chatHeaderRaw = ChatHeader.raw();
            showAllMatching = settings.showAllMatching;
            showAllMatchingShowEnglish = settings.showAllMatchingShowEnglish;
            showAllMatchingShowCount = settings.showAllMatchingShowCount;
            gtwShowCount = settings.gtwShowCount;
            themePointReveal = settings.themePointReveal;
            themePointRevealShowAfterGuess = settings.themePointRevealShowAfterGuess;
            shortcutReminder = settings.shortcutReminder;
            shortcutReminderShowNonAscii = settings.shortcutReminderShowNonAscii;
            shortcutReminderShowExtendedLatin = settings.shortcutReminderShowExtendedLatin;
            shortcutReminderShowCyrillic = settings.shortcutReminderShowCyrillic;
            shortcutReminderShowHanzi = settings.shortcutReminderShowHanzi;
            shortcutReminderShowJapanese = settings.shortcutReminderShowJapanese;
            shortcutReminderShowHangul = settings.shortcutReminderShowHangul;
            shortcutReminderShowAllShortcuts = settings.shortcutReminderShowAllShortcuts;
            shortcutReminderShowMultiwords = settings.shortcutReminderShowMultiwords;
            shortcutReminderShowAllMultiwords = settings.shortcutReminderShowAllMultiwords;
            copyTranslationPrefix = settings.copyTranslationPrefix;
            copyTranslationCaps = settings.copyTranslationCaps;
            gtwShowEnglish = settings.gtwShowEnglish;
            posX = settings.posX;
            posY = settings.posY;
            handheldItemPosX = settings.handheldItemPosX;
            handheldItemPosY = settings.handheldItemPosY;
            environmentPosX = settings.environmentPosX;
            environmentPosY = settings.environmentPosY;
            chatCooldownPosX = settings.chatCooldownPosX;
            chatCooldownPosY = settings.chatCooldownPosY;
            handheldItemMaxVisible = settings.handheldItemMaxVisible;
            handheldItemPanelWidthRatio = settings.handheldItemPanelWidthRatio;
            handheldItemPanelHeightRatio = settings.handheldItemPanelHeightRatio;
            scoreboardHeightOffset = settings.sbConfig.heightOffset;
            spawnEggSkin = settings.spawnEggSkin;
            environmentTimeIconPreset = settings.environmentTimeIconPreset;
            colorNumbers = settings.colorNumbers;
            colorHyphens = settings.colorHyphens;
            colorBrackets = settings.colorBrackets;
            chatCooldownBarColor = settings.chatCooldownBarColor;
            chatCooldownTimerColor = settings.chatCooldownTimerColor;
            scoreboardSkin = settings.sbConfig.skin;
            shortcutLanguageGroups = ShortcutLanguageGroups.snapshot();
        }

        private static ConfigSnapshot capture() {
            return new ConfigSnapshot();
        }

        private void apply() {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            settings.guessSound1 = guessSound1; settings.guessSound2 = guessSound2; settings.guessSound3 = guessSound3;
            settings.guessSoundVolume = guessSoundVolume;
            settings.modEnabled = modEnabled;
            settings.enabled = enabled;
            settings.hintBackgroundOverlay = hintBackgroundOverlay;
            settings.hintStructureBackground = hintStructureBackground;
            settings.handheldItemOverlay = handheldItemOverlay;
            settings.handheldItemBackground = handheldItemBackground;
            settings.handheldItemCaptionName = handheldItemCaptionName;
            settings.handheldItemCaptionId = handheldItemCaptionId;
            settings.handheldItemCaptionMetadata = handheldItemCaptionMetadata;
            settings.handheldItemCaptionSpawnEggMob = handheldItemCaptionSpawnEggMob;
            settings.handheldItemVerticalLayout = handheldItemVerticalLayout;
            settings.handheldItemVerticalNewestAtBottom = handheldItemVerticalNewestAtBottom;
            settings.handheldItemHorizontalNewestAtRight = handheldItemHorizontalNewestAtRight;
            settings.handheldItemHorizontalNewestAtBottom = handheldItemHorizontalNewestAtBottom;
            settings.environmentOverlay = environmentOverlay;
            settings.environmentBackground = environmentBackground;
            settings.environmentWeatherEnabled = environmentWeatherEnabled;
            settings.environmentBiomeEnabled = environmentBiomeEnabled;
            settings.environmentTimeEnabled = environmentTimeEnabled;
            settings.environmentWeatherChatNotice = environmentWeatherChatNotice;
            settings.environmentBiomeChatNotice = environmentBiomeChatNotice;
            settings.environmentTimeChatNotice = environmentTimeChatNotice;
            settings.chatCooldownCounter = chatCooldownCounter;
            settings.chatCooldownTimerEnabled = chatCooldownTimerEnabled;
            settings.chatCooldownBarEnabled = chatCooldownBarEnabled;
            settings.chatCooldownBackground = chatCooldownBackground;
            settings.chatCooldownBarBackground = chatCooldownBarBackground;
            if (scoreboardEnabled && !"vanilla".equals(ScoreboardConfig.normalizeSkin(scoreboardSkin))) {
                settings.sbConfig.setSkin(scoreboardSkin);
            } else {
                settings.sbConfig.setSkin("vanilla");
            }
            settings.sbConfig.backgroundOpacity = scoreboardBackgroundOpacity;
            settings.sbConfig.highlightIntensity = scoreboardHighlightIntensity;
            settings.hintStructureScale = hintStructureScale;
            settings.chatCooldownScale = chatCooldownScale;
            settings.handheldItemScale = handheldItemScale;
            settings.environmentScale = environmentScale;
            settings.sbConfig.showPointsGainedInRound = scoreboardShowPointsGainedInRound;
            settings.sbConfig.showPlaces = scoreboardShowPlaces;
            settings.sbConfig.showTitles = scoreboardShowTitles;
            settings.sbConfig.showEmblems = scoreboardShowEmblems;
            settings.sbConfig.defaultExplicitlyShowBuilders = scoreboardDefaultExplicitlyShowBuilders;
            settings.sbConfig.hypixelShowRounds = scoreboardHypixelShowRounds;
            settings.sbConfig.leftPlayersAtBottom = scoreboardLeftPlayersAtBottom;
            settings.sbConfig.textShadow = scoreboardTextShadow;
            settings.trackingEnabled = trackingEnabled;
            settings.autoRequeue = autoRequeue;
            settings.lrjBypassLobbyProtection = lrjBypassLobbyProtection;
            settings.autoRequeueDelaySeconds = autoRequeueDelaySeconds;
            settings.guessPeek = guessPeek;
            settings.guessPeekShowLangCodes = guessPeekShowLangCodes;
            settings.guessPeekDisplay = guessPeekDisplay;
            settings.scrambleGuess = scrambleGuess;
            settings.scrambleGuessFancy = scrambleGuessFancy;
            settings.trollMode = trollMode;
            settings.trollModeAutoDisable = trollModeAutoDisable;
            ChatHeader.set(chatHeaderRaw);
            CommandGtb.refreshHeader();
            settings.showAllMatching = showAllMatching;
            settings.showAllMatchingShowEnglish = showAllMatchingShowEnglish;
            settings.showAllMatchingShowCount = showAllMatchingShowCount;
            settings.gtwShowCount = gtwShowCount;
            settings.themePointReveal = themePointReveal;
            settings.themePointRevealShowAfterGuess = themePointRevealShowAfterGuess;
            settings.shortcutReminder = shortcutReminder;
            settings.shortcutReminderShowNonAscii = shortcutReminderShowNonAscii;
            settings.shortcutReminderShowExtendedLatin = shortcutReminderShowExtendedLatin;
            settings.shortcutReminderShowCyrillic = shortcutReminderShowCyrillic;
            settings.shortcutReminderShowHanzi = shortcutReminderShowHanzi;
            settings.shortcutReminderShowJapanese = shortcutReminderShowJapanese;
            settings.shortcutReminderShowHangul = shortcutReminderShowHangul;
            settings.shortcutReminderShowAllShortcuts = shortcutReminderShowAllShortcuts;
            settings.shortcutReminderShowMultiwords = shortcutReminderShowMultiwords;
            settings.shortcutReminderShowAllMultiwords = shortcutReminderShowAllMultiwords;
            settings.copyTranslationPrefix = copyTranslationPrefix;
            settings.copyTranslationCaps = copyTranslationCaps;
            settings.gtwShowEnglish = gtwShowEnglish;
            GameStats.INSTANCE.setTrackingEnabled(trackingEnabled);
            GameStats.INSTANCE.setAutoRequeueDelaySeconds(autoRequeueDelaySeconds);
            GameStats.INSTANCE.setAutoRequeue(autoRequeue);
            settings.posX = posX;
            settings.posY = posY;
            settings.handheldItemPosX = handheldItemPosX;
            settings.handheldItemPosY = handheldItemPosY;
            settings.environmentPosX = environmentPosX;
            settings.environmentPosY = environmentPosY;
            settings.chatCooldownPosX = chatCooldownPosX;
            settings.chatCooldownPosY = chatCooldownPosY;
            settings.handheldItemMaxVisible = handheldItemMaxVisible;
            settings.handheldItemPanelWidthRatio = handheldItemPanelWidthRatio;
            settings.handheldItemPanelHeightRatio = handheldItemPanelHeightRatio;
            settings.sbConfig.heightOffset = scoreboardHeightOffset;
            settings.spawnEggSkin = spawnEggSkin;
            settings.environmentTimeIconPreset = environmentTimeIconPreset;
            settings.colorNumbers = colorNumbers;
            settings.colorHyphens = colorHyphens;
            settings.colorBrackets = colorBrackets;
            settings.chatCooldownBarColor = chatCooldownBarColor;
            settings.chatCooldownTimerColor = chatCooldownTimerColor;
            ShortcutLanguageGroups.restore(shortcutLanguageGroups);
        }

        private boolean matchesCurrent() {
            GtbStructuresSettings settings = GtbStructuresSettings.INSTANCE;
            return same(guessSound1, settings.guessSound1) && same(guessSound2, settings.guessSound2)
                    && same(guessSound3, settings.guessSound3) && Float.compare(guessSoundVolume, settings.guessSoundVolume) == 0
                    && modEnabled == settings.modEnabled
                    && enabled == settings.enabled
                    && hintBackgroundOverlay == settings.hintBackgroundOverlay
                    && hintStructureBackground == settings.hintStructureBackground
                    && handheldItemOverlay == settings.handheldItemOverlay
                    && handheldItemBackground == settings.handheldItemBackground
                    && handheldItemCaptionName == settings.handheldItemCaptionName
                    && handheldItemCaptionId == settings.handheldItemCaptionId
                    && handheldItemCaptionMetadata == settings.handheldItemCaptionMetadata
                    && handheldItemCaptionSpawnEggMob == settings.handheldItemCaptionSpawnEggMob
                    && handheldItemVerticalLayout == settings.handheldItemVerticalLayout
                    && handheldItemVerticalNewestAtBottom == settings.handheldItemVerticalNewestAtBottom
                    && handheldItemHorizontalNewestAtRight == settings.handheldItemHorizontalNewestAtRight
                    && handheldItemHorizontalNewestAtBottom == settings.handheldItemHorizontalNewestAtBottom
                    && environmentOverlay == settings.environmentOverlay
                    && environmentBackground == settings.environmentBackground
                    && environmentWeatherEnabled == settings.environmentWeatherEnabled
                    && environmentBiomeEnabled == settings.environmentBiomeEnabled
                    && environmentTimeEnabled == settings.environmentTimeEnabled
                    && environmentWeatherChatNotice == settings.environmentWeatherChatNotice
                    && environmentBiomeChatNotice == settings.environmentBiomeChatNotice
                    && environmentTimeChatNotice == settings.environmentTimeChatNotice
                    && chatCooldownCounter == settings.chatCooldownCounter
                    && chatCooldownTimerEnabled == settings.chatCooldownTimerEnabled
                    && chatCooldownBarEnabled == settings.chatCooldownBarEnabled
                    && chatCooldownBackground == settings.chatCooldownBackground
                    && chatCooldownBarBackground == settings.chatCooldownBarBackground
                    && scoreboardEnabled == settings.sbConfig.isCustomEnabled()
                    && scoreboardBackgroundOpacity == settings.sbConfig.backgroundOpacity
                    && scoreboardHighlightIntensity == settings.sbConfig.highlightIntensity
                    && Float.compare(hintStructureScale, settings.hintStructureScale) == 0
                    && Float.compare(chatCooldownScale, settings.chatCooldownScale) == 0
                    && Float.compare(handheldItemScale, settings.handheldItemScale) == 0
                    && Float.compare(environmentScale, settings.environmentScale) == 0
                    && scoreboardShowPointsGainedInRound == settings.sbConfig.showPointsGainedInRound
                    && scoreboardShowPlaces == settings.sbConfig.showPlaces
                    && scoreboardShowTitles == settings.sbConfig.showTitles
                    && scoreboardShowEmblems == settings.sbConfig.showEmblems
                    && scoreboardDefaultExplicitlyShowBuilders
                            == settings.sbConfig.defaultExplicitlyShowBuilders
                    && scoreboardHypixelShowRounds == settings.sbConfig.hypixelShowRounds
                    && scoreboardLeftPlayersAtBottom == settings.sbConfig.leftPlayersAtBottom
                    && scoreboardTextShadow == settings.sbConfig.textShadow
                    && trackingEnabled == settings.trackingEnabled
                    && autoRequeue == settings.autoRequeue
                    && lrjBypassLobbyProtection == settings.lrjBypassLobbyProtection
                    && Float.compare(autoRequeueDelaySeconds, settings.autoRequeueDelaySeconds) == 0
                    && guessPeek == settings.guessPeek
                    && guessPeekShowLangCodes == settings.guessPeekShowLangCodes
                    && guessPeekDisplay.equals(settings.guessPeekDisplay)
                    && scrambleGuess == settings.scrambleGuess
                    && scrambleGuessFancy == settings.scrambleGuessFancy
                    && trollMode == settings.trollMode
                    && trollModeAutoDisable == settings.trollModeAutoDisable
                    && same(chatHeaderRaw, ChatHeader.raw())
                    && showAllMatching == settings.showAllMatching
                    && showAllMatchingShowEnglish == settings.showAllMatchingShowEnglish
                    && showAllMatchingShowCount == settings.showAllMatchingShowCount
                    && gtwShowCount == settings.gtwShowCount
                    && themePointReveal == settings.themePointReveal
                    && themePointRevealShowAfterGuess == settings.themePointRevealShowAfterGuess
                    && shortcutReminder == settings.shortcutReminder
                    && shortcutReminderShowNonAscii == settings.shortcutReminderShowNonAscii
                    && shortcutReminderShowExtendedLatin == settings.shortcutReminderShowExtendedLatin
                    && shortcutReminderShowCyrillic == settings.shortcutReminderShowCyrillic
                    && shortcutReminderShowHanzi == settings.shortcutReminderShowHanzi
                    && shortcutReminderShowJapanese == settings.shortcutReminderShowJapanese
                    && shortcutReminderShowHangul == settings.shortcutReminderShowHangul
                    && shortcutReminderShowAllShortcuts == settings.shortcutReminderShowAllShortcuts
                    && shortcutReminderShowMultiwords == settings.shortcutReminderShowMultiwords
                    && shortcutReminderShowAllMultiwords == settings.shortcutReminderShowAllMultiwords
                    && copyTranslationPrefix == settings.copyTranslationPrefix
                    && copyTranslationCaps == settings.copyTranslationCaps
                    && gtwShowEnglish == settings.gtwShowEnglish
                    && posX == settings.posX
                    && posY == settings.posY
                    && handheldItemPosX == settings.handheldItemPosX
                    && handheldItemPosY == settings.handheldItemPosY
                    && environmentPosX == settings.environmentPosX
                    && environmentPosY == settings.environmentPosY
                    && chatCooldownPosX == settings.chatCooldownPosX
                    && chatCooldownPosY == settings.chatCooldownPosY
                    && handheldItemMaxVisible == settings.handheldItemMaxVisible
                    && handheldItemPanelWidthRatio == settings.handheldItemPanelWidthRatio
                    && handheldItemPanelHeightRatio == settings.handheldItemPanelHeightRatio
                    && scoreboardHeightOffset == settings.sbConfig.heightOffset
                    && same(spawnEggSkin, settings.spawnEggSkin)
                    && same(environmentTimeIconPreset, settings.environmentTimeIconPreset)
                    && same(colorNumbers, settings.colorNumbers)
                    && same(colorHyphens, settings.colorHyphens)
                    && same(colorBrackets, settings.colorBrackets)
                    && same(chatCooldownBarColor, settings.chatCooldownBarColor)
                    && same(chatCooldownTimerColor, settings.chatCooldownTimerColor)
                    && same(scoreboardSkin, settings.sbConfig.skin)
                    && shortcutLanguageGroups.equals(ShortcutLanguageGroups.snapshot());
        }

        private static boolean same(String left, String right) {
            if (left == null) return right == null;
            return left.equals(right);
        }
    }

    private static final class Label {
        private final String id;
        private final int x1;
        private final int y1;
        private final int x2;
        private final int y2;
        private final Target target;

        private Label(String id, int x1, int y1, int x2, int y2, Target target) {
            this.id = id;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.target = target;
        }

        private int width() {
            return x2 - x1;
        }

        private int height() {
            return y2 - y1;
        }

        private boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private static final class Rect {
        private final int x1;
        private final int y1;
        private final int x2;
        private final int y2;

        private Rect(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        private int width() {
            return x2 - x1;
        }

        private int height() {
            return y2 - y1;
        }

        private boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    public static final class InputState {
        public final int mouseX;
        public final int mouseY;
        public final boolean leftDown;
        public final boolean rightDown;
        public final boolean escapeDown;
        public final boolean modifierDown;
        public final boolean leftArrowDown;
        public final boolean rightArrowDown;
        public final boolean upArrowDown;
        public final boolean downArrowDown;
        public final boolean deleteDown;
        public final boolean zDown;
        public final boolean shiftDown;
        public final boolean controlDown;
        public final double scrollDelta;

        public InputState(int mouseX, int mouseY, boolean leftDown, boolean rightDown,
                          boolean escapeDown, boolean modifierDown,
                          boolean leftArrowDown, boolean rightArrowDown,
                          boolean upArrowDown, boolean downArrowDown) {
            this(mouseX, mouseY, leftDown, rightDown, escapeDown, modifierDown,
                    leftArrowDown, rightArrowDown, upArrowDown, downArrowDown, 0.0D);
        }

        public InputState(int mouseX, int mouseY, boolean leftDown, boolean rightDown,
                          boolean escapeDown, boolean modifierDown,
                          boolean leftArrowDown, boolean rightArrowDown,
                          boolean upArrowDown, boolean downArrowDown,
                          double scrollDelta) {
            this(mouseX, mouseY, leftDown, rightDown, escapeDown, modifierDown,
                    leftArrowDown, rightArrowDown, upArrowDown, downArrowDown,
                    false, false, false, modifierDown, scrollDelta);
        }

        public InputState(int mouseX, int mouseY, boolean leftDown, boolean rightDown,
                          boolean escapeDown, boolean modifierDown,
                          boolean leftArrowDown, boolean rightArrowDown,
                          boolean upArrowDown, boolean downArrowDown,
                          boolean deleteDown, boolean zDown,
                          boolean shiftDown, boolean controlDown) {
            this(mouseX, mouseY, leftDown, rightDown, escapeDown, modifierDown,
                    leftArrowDown, rightArrowDown, upArrowDown, downArrowDown,
                    deleteDown, zDown, shiftDown, controlDown, 0.0D);
        }

        public InputState(int mouseX, int mouseY, boolean leftDown, boolean rightDown,
                          boolean escapeDown, boolean modifierDown,
                          boolean leftArrowDown, boolean rightArrowDown,
                          boolean upArrowDown, boolean downArrowDown,
                          boolean deleteDown, boolean zDown,
                          boolean shiftDown, boolean controlDown,
                          double scrollDelta) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.leftDown = leftDown;
            this.rightDown = rightDown;
            this.escapeDown = escapeDown;
            this.modifierDown = modifierDown;
            this.leftArrowDown = leftArrowDown;
            this.rightArrowDown = rightArrowDown;
            this.upArrowDown = upArrowDown;
            this.downArrowDown = downArrowDown;
            this.deleteDown = deleteDown;
            this.zDown = zDown;
            this.shiftDown = shiftDown;
            this.controlDown = controlDown;
            this.scrollDelta = scrollDelta;
        }

        private int arrowMask() {
            int mask = 0;
            if (leftArrowDown) mask |= ARROW_LEFT;
            if (rightArrowDown) mask |= ARROW_RIGHT;
            if (upArrowDown) mask |= ARROW_UP;
            if (downArrowDown) mask |= ARROW_DOWN;
            return mask;
        }
    }

    public interface EditorRenderer {
        int fontHeight();
        int textWidth(String text);
        void fill(int left, int top, int right, int bottom, int color);
        void drawString(String text, int x, int y, int color, boolean shadow);
        default boolean drawVanillaButton(int x, int y, int width, int height,
                                          String text, int mouseX, int mouseY) {
            return false;
        }
        default void drawSoundIcon(String soundId, int x, int y) { }
        default void enableScissor(int left, int top, int right, int bottom) {
        }
        default void disableScissor() {
        }
    }

    public interface BackgroundRenderer {
        void fill(int left, int top, int right, int bottom, int color);
    }
}
