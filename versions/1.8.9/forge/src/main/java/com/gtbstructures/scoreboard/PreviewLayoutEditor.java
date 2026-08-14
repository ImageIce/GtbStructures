package com.gtbstructures.scoreboard;

import com.gtbstructures.GtbStructures;
import com.gtbstructures.chat.ChatHeader;
import com.gtbstructures.commands.CommandGtb;
import com.gtbstructures.items.HandheldItemTracker;
import com.gtbstructures.overlay.HudPosition;
import com.gtbstructures.stats.GameStats;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

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
    private static final int SETTINGS_PANEL_GAP = 6;
    private static final int ARROW_REPEAT_FRAMES = 8;
    private static final int ARROW_ACCELERATED_REPEAT_FRAMES = 3;
    private static final int ARROW_ACCELERATION_START_FRAMES = 24;
    private static final int ARROW_LEFT = 1;
    private static final int ARROW_RIGHT = 2;
    private static final int ARROW_UP = 4;
    private static final int ARROW_DOWN = 8;
    private static final float HOVER_STEP_IN = 0.50F;
    private static final float HOVER_STEP_OUT = 0.42F;
    private static final int MODAL_BACKGROUND = 0xD8181818;
    private static final int PANEL_BACKGROUND = 0xE0101217;
    private static final int PANEL_BORDER = 0x553A4654;
    private static final int PANEL_TEXT = 0xFFFFFFFF;
    private static final int PANEL_MUTED = 0xFF9EA8B5;
    private static final int PANEL_CLOSE_BG = 0x70302028;
    private static final int PANEL_CLOSE_BORDER = 0x88AA4252;
    private static final int PANEL_PAD = 6;
    private static final int TITLE_HEIGHT = 11;
    private static final int ROW_HEIGHT = 12;
    private static final int HEADER_CONTROL_ROW_HEIGHT = 20;
    private static final int MENU_SECTION_COUNT = 5;
    private static final int CHECK_SIZE = 10;
    private static final int CLOSE_SIZE = 11;
    private static final int LABELS_PANEL_WIDTH = 268;
    private static final int LABELS_BUTTON_SIZE = 20;
    private static final int LABELS_SCROLLBAR_WIDTH = 4;
    private static final int LABELS_SCROLLBAR_MIN_THUMB = 16;
    private static final int LABELS_SCROLL_STEP = ROW_HEIGHT * 3;
    private static final int ENVIRONMENT_SETTINGS_PANEL_HEIGHT = 162;
    private static final boolean ENVIRONMENT_CHAT_NOTICE_MULTI_SELECT = true;
    private static final int DONE_BUTTON_WIDTH = 58;
    private static final int DONE_BUTTON_HEIGHT = 20;
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
    private static final int HISTORY_LIMIT = 40;
    private static final int HANDHELD_RESIZE_GRIP = 4;
    private static final int RESIZE_LEFT = 1;
    private static final int RESIZE_RIGHT = 2;
    private static final int RESIZE_TOP = 4;
    private static final int RESIZE_BOTTOM = 8;
    private static final int LABEL_BG_PAD_X = 3;
    private static final int LABEL_BG_PAD_Y = 2;
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
    private boolean labelsPanelOpen;
    private int labelsScrollOffset;
    private boolean labelsScrollbarDragging;
    private int labelsScrollbarDragOffset;
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
    private boolean dragPanelFollows;
    private boolean resizingHandheld;
    private int handheldResizeEdges;
    private String activeSlider = "";
    private int activeSliderX;
    private ConfigSnapshot sliderUndoSnapshot;
    private boolean sliderDirty;
    private GuiTextField headerTextField;
    private boolean headerEditing;
    private boolean headerRepeatEventsWereEnabled;
    private int drawMouseX;
    private int drawMouseY;
    private boolean drawLeftDown;

    private PreviewLayoutEditor() {
    }

    public static void register(String id, int x, int y, int width, int height, Target target) {
        if (!InterfacePreview.isActive()) return;
        INSTANCE.registerInternal(id, x, y, width, height, target);
    }

    public static boolean selectedForTest(String current, String hovered, boolean modifier) {
        PreviewLayoutEditor editor = new PreviewLayoutEditor();
        editor.selectedId = current == null ? "" : current;
        editor.selectOnPress(hovered == null ? null : new Label(hovered, 0, 0, 10, 10, HINT_STRUCTURE), modifier);
        return hovered != null && hovered.equals(editor.selectedId);
    }

    public static float moveTopLeftXForTest(float current, int screenWidth, int labelWidth, int dx) {
        return moveNormalizedLeft(current, screenWidth, labelWidth, dx);
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

    public static float dragTopLeftXFromAnchorForTest(float start, int screenWidth, int labelWidth, int dx) {
        return moveNormalizedLeft(start, screenWidth, labelWidth, dx);
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

    public boolean onKeyTyped(char typedChar, int keyCode) {
        if (!headerEditing) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            finishHeaderEditing();
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            finishHeaderEditing();
            return true;
        }
        if (headerTextField == null) return true;
        String previous = headerTextField.getText();
        int previousCursor = headerTextField.getCursorPosition();
        int previousSelection = headerTextField.getSelectionEnd();
        headerTextField.textboxKeyTyped(typedChar, keyCode);
        if (!validHeaderEditCandidate(headerTextField.getText())) {
            headerTextField.setText(previous);
            headerTextField.setCursorPosition(previousCursor);
            headerTextField.setSelectionPos(previousSelection);
        }
        return true;
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
        labelsScrollOffset = 0;
        labelsScrollbarDragging = false;
        labelsScrollbarDragOffset = 0;
        previousLeftDown = false;
        previousRightDown = false;
        previousDeleteDown = false;
        previousEscapeDown = false;
        previousZDown = false;
        previousArrowMask = 0;
        arrowHoldFrames = 0;
        arrowRepeatFrames = 0;
        dragUndoSnapshot = null;
        arrowUndoSnapshot = null;
        dragArmed = false;
        dragDirty = false;
        toggleSelectedOnRelease = false;
        dragDistance = 0;
        dragPanelFollows = false;
        resizingHandheld = false;
        handheldResizeEdges = 0;
        activeSlider = "";
        sliderUndoSnapshot = null;
        sliderDirty = false;
        headerTextField = null;
        headerEditing = false;
        headerRepeatEventsWereEnabled = false;
        drawMouseX = 0;
        drawMouseY = 0;
        drawLeftDown = false;
    }

    @SubscribeEvent
    public void onRenderPre(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        labels.clear();
        if (!InterfacePreview.isActive()) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        beginForegroundDraw();
        try {
            Gui.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), MODAL_BACKGROUND);
        } finally {
            endForegroundDraw();
        }
    }

    @SubscribeEvent
    public void onRenderPost(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CHAT) return;
        if (!InterfacePreview.isActive()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        int mouseX = mouseX(mc, sr);
        int mouseY = mouseY(mc, sr);
        handleInput(sr, mouseX, mouseY);
        drawEditorUi(mc, sr, mouseX, mouseY);
    }

    private void registerInternal(String id, int x, int y, int width, int height, Target target) {
        if (id == null || id.trim().isEmpty() || target == null || width <= 0 || height <= 0) return;
        if (!targetVisibleForMaster(GtbStructures.modEnabled, target.key)) return;
        Label label = new Label(id, x, y, x + width, y + height, target);
        labels.add(label);
        drawBackground(label);
    }

    private void handleInput(ScaledResolution sr, int mouseX, int mouseY) {
        boolean overEditorPanel = isPointInEditorPanel(sr, mouseX, mouseY);
        Label hovered = !GtbStructures.modEnabled || overEditorPanel ? null : topLabelAt(mouseX, mouseY);
        hoveredId = hovered == null ? "" : hovered.id;
        Label selected = labelById(selectedId);
        if (!GtbStructures.modEnabled && selected != null) {
            deselect();
            selected = null;
        }
        if (selected == null && !selectedId.isEmpty()) {
            selectedId = "";
            selected = null;
        }

        boolean leftDown = Mouse.isButtonDown(0);
        boolean rightDown = Mouse.isButtonDown(1);
        boolean leftPressed = leftDown && !previousLeftDown;
        boolean leftReleased = !leftDown && previousLeftDown;
        boolean rightPressed = rightDown && !previousRightDown;
        boolean escapeDown = Keyboard.isKeyDown(Keyboard.KEY_ESCAPE);
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean controlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean modifier = shiftDown || controlDown;
        boolean zDown = Keyboard.isKeyDown(Keyboard.KEY_Z);

        int wheel = Mouse.getDWheel();
        if (wheel != 0 && labelsPanelOpen && labelsPanelRect(sr).contains(mouseX, mouseY)) {
            scrollLabelsBy(wheel > 0 ? -LABELS_SCROLL_STEP : LABELS_SCROLL_STEP, sr);
        }

        if (!headerEditing && controlDown && zDown && !previousZDown) {
            if (shiftDown) {
                redoConfigChange();
            } else {
                undoConfigChange();
            }
            previousLeftDown = leftDown;
            previousRightDown = rightDown;
            previousDeleteDown = Keyboard.isKeyDown(Keyboard.KEY_DELETE);
            previousEscapeDown = escapeDown;
            previousZDown = zDown;
            return;
        }

        boolean consumedLeftPress = false;
        boolean consumedRightPress = false;

        if (leftPressed && headerEditing && !headerTextFieldRect(sr).contains(mouseX, mouseY)) {
            finishHeaderEditing();
        }

        if (leftPressed) {
            if (doneButtonRect(sr).contains(mouseX, mouseY)) {
                InterfacePreview.INSTANCE.disableSilently();
                return;
            } else if (handleLabelsPanelClick(sr, mouseX, mouseY)) {
                consumedLeftPress = true;
            } else if (handleSettingsPanelClick(sr, mouseX, mouseY)) {
                consumedLeftPress = true;
            }
        }

        if (headerEditing) {
            updateHoverAmounts();
            previousLeftDown = leftDown;
            previousRightDown = rightDown;
            previousDeleteDown = Keyboard.isKeyDown(Keyboard.KEY_DELETE);
            previousEscapeDown = escapeDown;
            previousZDown = zDown;
            return;
        }

        if (rightPressed) {
            if (hovered != null) {
                select(hovered.id);
                activePanelId = hovered.id;
                pinPanel(hovered, sr);
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
            } else if (handheldResizeEdges(hovered, mouseX, mouseY) != 0) {
                select(hovered.id);
                selected = hovered;
                resizingHandheld = true;
                handheldResizeEdges = handheldResizeEdges(hovered, mouseX, mouseY);
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragStartLabelX = selected.x1;
                dragStartLabelY = selected.y1;
                dragUndoSnapshot = ConfigSnapshot.capture();
            } else if (modifier) {
                selectOnPress(hovered, true);
            } else {
                boolean same = hovered.id.equals(selectedId);
                select(hovered.id);
                selected = hovered;
                dragArmed = true;
                toggleSelectedOnRelease = same;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragStartX = selected.target.x();
                dragStartY = selected.target.y();
                dragStartScoreboardOffset = GtbStructures.sbConfig.heightOffset;
                dragStartLabelX = selected.x1;
                dragStartLabelY = selected.y1;
                armPanelFollow(selected, sr);
                dragUndoSnapshot = ConfigSnapshot.capture();
            }
        }

        if (rightPressed && !consumedRightPress && (selected != null && (hovered == null || !hovered.id.equals(selected.id)))) {
            deselect();
            selected = null;
        }

        if (leftDown && !activeSlider.isEmpty()) {
            updateActiveScoreboardSlider(mouseX);
        }
        if (leftDown && labelsScrollbarDragging) {
            updateLabelsScrollbarDrag(sr, mouseY);
        }

        if (leftDown && resizingHandheld && selected != null) {
            applyHandheldResize(selected, sr, mouseX, mouseY);
        }

        if (leftDown && dragArmed && selected != null) {
            int dx = mouseX - dragStartMouseX;
            int dy = mouseY - dragStartMouseY;
            if (dx != 0 || dy != 0) {
                selected.target.moveFromAnchor(dx, dy, selected.width(), selected.height(),
                        sr.getScaledWidth(), sr.getScaledHeight(),
                        dragStartX, dragStartY, dragStartScoreboardOffset);
                movePinnedPanelWithDrag(selected, sr);
                dragDirty = true;
                dragDistance = Math.abs(dx) + Math.abs(dy);
            }
        }

        if (leftReleased) {
            labelsScrollbarDragging = false;
            if (!activeSlider.isEmpty()) {
                finishScoreboardSliderDrag();
            }
            if (dragDirty) {
                if (dragUndoSnapshot != null && !dragUndoSnapshot.matchesCurrent()) {
                    recordUndo(dragUndoSnapshot);
                    GtbStructures.saveConfig();
                }
            } else if (toggleSelectedOnRelease && dragDistance <= 1) {
                deselect();
            }
            dragArmed = false;
            dragDirty = false;
            dragUndoSnapshot = null;
            toggleSelectedOnRelease = false;
            dragDistance = 0;
            dragPanelFollows = false;
            resizingHandheld = false;
            handheldResizeEdges = 0;
        }

        selected = labelById(selectedId);
        if (selected != null) {
            boolean moved = false;
            int arrowMask = arrowMask();
            boolean applyArrowMove = shouldApplyArrowMove(arrowMask);
            ConfigSnapshot beforeArrowMove = applyArrowMove ? ConfigSnapshot.capture() : null;
            if (applyArrowMove && (arrowMask & ARROW_LEFT) != 0) {
                selected.target.movePixels(-1, 0, selected.width(), selected.height(),
                        sr.getScaledWidth(), sr.getScaledHeight());
                moved = true;
            }
            if (applyArrowMove && (arrowMask & ARROW_RIGHT) != 0) {
                selected.target.movePixels(1, 0, selected.width(), selected.height(),
                        sr.getScaledWidth(), sr.getScaledHeight());
                moved = true;
            }
            if (applyArrowMove && (arrowMask & ARROW_UP) != 0) {
                selected.target.movePixels(0, -1, selected.width(), selected.height(),
                        sr.getScaledWidth(), sr.getScaledHeight());
                moved = true;
            }
            if (applyArrowMove && (arrowMask & ARROW_DOWN) != 0) {
                selected.target.movePixels(0, 1, selected.width(), selected.height(),
                        sr.getScaledWidth(), sr.getScaledHeight());
                moved = true;
            }
            if (moved && beforeArrowMove != null && !beforeArrowMove.matchesCurrent()) {
                if (arrowUndoSnapshot == null) {
                    arrowUndoSnapshot = beforeArrowMove;
                    recordUndo(beforeArrowMove);
                }
                GtbStructures.saveConfig();
            }
        } else {
            resetArrowRepeat();
        }

        boolean deleteDown = Keyboard.isKeyDown(Keyboard.KEY_DELETE);
        if (deleteDown && !previousDeleteDown && selected != null && selected.target.canDelete()) {
            recordUndo();
            selected.target.disable();
            GtbStructures.saveConfig();
            deselect();
        }

        updateHoverAmounts();

        previousLeftDown = leftDown;
        previousRightDown = rightDown;
        previousDeleteDown = deleteDown;
        previousEscapeDown = escapeDown;
        previousZDown = zDown;
    }

    private static int handheldResizeEdges(Label label, int mouseX, int mouseY) {
        if (label == null || !"handhelditem".equals(label.id)) return 0;
        int edges = 0;
        if (mouseX - label.x1 < HANDHELD_RESIZE_GRIP) edges |= RESIZE_LEFT;
        if (label.x2 - 1 - mouseX < HANDHELD_RESIZE_GRIP) edges |= RESIZE_RIGHT;
        if (mouseY - label.y1 < HANDHELD_RESIZE_GRIP) edges |= RESIZE_TOP;
        if (label.y2 - 1 - mouseY < HANDHELD_RESIZE_GRIP) edges |= RESIZE_BOTTOM;
        return edges;
    }

    private void applyHandheldResize(Label label, ScaledResolution sr, int mouseX, int mouseY) {
        int left = dragStartLabelX;
        int top = dragStartLabelY;
        int right = label.x2;
        int bottom = label.y2;
        if ((handheldResizeEdges & RESIZE_LEFT) != 0) left = mouseX;
        if ((handheldResizeEdges & RESIZE_RIGHT) != 0) right = mouseX + 1;
        if ((handheldResizeEdges & RESIZE_TOP) != 0) top = mouseY;
        if ((handheldResizeEdges & RESIZE_BOTTOM) != 0) bottom = mouseY + 1;

        int width = HandheldItemTracker.panelWidthPixels(
                (float) (right - left) / (float) Math.max(1, sr.getScaledWidth()), sr.getScaledWidth());
        int height = HandheldItemTracker.panelHeightPixels(
                (float) (bottom - top) / (float) Math.max(1, sr.getScaledHeight()), sr.getScaledHeight());
        if ((handheldResizeEdges & RESIZE_LEFT) != 0) left = right - width;
        else right = left + width;
        if ((handheldResizeEdges & RESIZE_TOP) != 0) top = bottom - height;
        else bottom = top + height;

        GtbStructures.handheldItemPanelWidthRatio = handheldPanelResizeRatioForTest(width,
                sr.getScaledWidth(), true);
        GtbStructures.handheldItemPanelHeightRatio = handheldPanelResizeRatioForTest(height,
                sr.getScaledHeight(), false);
        int effectiveHeight = Math.max(height, label.height());
        label.target.setX(normalizedPoint(left, MARGIN,
                Math.max(MARGIN, sr.getScaledWidth() - width - MARGIN)));
        label.target.setY(normalizedPoint(bottom, effectiveHeight + MARGIN,
                Math.max(effectiveHeight + MARGIN, sr.getScaledHeight() - MARGIN)));
        dragDirty = true;
    }

    static float handheldPanelResizeRatioForTest(int pixels, int screenDimension, boolean width) {
        return width ? HandheldItemTracker.panelWidthRatioForPixels(pixels, screenDimension)
                : HandheldItemTracker.panelHeightRatioForPixels(pixels, screenDimension);
    }

    private void selectOnPress(Label hovered, boolean modifier) {
        if (hovered == null) {
            deselect();
            return;
        }
        if (modifier && hovered.id.equals(selectedId)) {
            deselect();
        } else {
            select(hovered.id);
        }
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

    private static int arrowMask() {
        int mask = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) mask |= ARROW_LEFT;
        if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) mask |= ARROW_RIGHT;
        if (Keyboard.isKeyDown(Keyboard.KEY_UP)) mask |= ARROW_UP;
        if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) mask |= ARROW_DOWN;
        return mask;
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

    private static boolean arrowMoveAllowed(int previousMask, int currentMask, int repeatedFrames, int holdFrames) {
        if (currentMask == 0) return false;
        if (currentMask != previousMask) return true;
        return repeatedFrames >= arrowRepeatInterval(holdFrames);
    }

    private static int arrowRepeatInterval(int holdFrames) {
        return holdFrames >= ARROW_ACCELERATION_START_FRAMES
                ? ARROW_ACCELERATED_REPEAT_FRAMES
                : ARROW_REPEAT_FRAMES;
    }

    private void recordUndo() {
        recordUndo(ConfigSnapshot.capture());
    }

    private void recordUndo(ConfigSnapshot snapshot) {
        if (snapshot == null) return;
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
        GtbStructures.saveConfig();
        resetArrowRepeat();
        dragUndoSnapshot = null;
    }

    private void redoConfigChange() {
        if (redoStack.isEmpty()) return;
        ConfigSnapshot current = ConfigSnapshot.capture();
        ConfigSnapshot next = redoStack.remove(redoStack.size() - 1);
        undoStack.add(current);
        if (undoStack.size() > HISTORY_LIMIT) undoStack.remove(0);
        next.apply();
        GtbStructures.saveConfig();
        resetArrowRepeat();
        dragUndoSnapshot = null;
    }

    private void updateHoverAmounts() {
        for (Label label : labels) {
            float current = hoverAmounts.containsKey(label.id) ? hoverAmounts.get(label.id) : 0F;
            float next = label.id.equals(hoveredId)
                    ? Math.min(1F, current + HOVER_STEP_IN)
                    : Math.max(0F, current - HOVER_STEP_OUT);
            hoverAmounts.put(label.id, Float.valueOf(next));
        }

        List<String> stale = new ArrayList<String>();
        for (Map.Entry<String, Float> entry : hoverAmounts.entrySet()) {
            if (labelById(entry.getKey()) == null && entry.getValue().floatValue() <= 0.01F) stale.add(entry.getKey());
        }
        for (String key : stale) {
            hoverAmounts.remove(key);
        }
    }

    private void drawEditorUi(Minecraft mc, ScaledResolution sr, int mouseX, int mouseY) {
        drawMouseX = mouseX;
        drawMouseY = mouseY;
        drawLeftDown = Mouse.isButtonDown(0);
        beginForegroundDraw();
        try {
            drawSettingsPanel(mc, sr);
            drawLabelsButton(mc, sr, mouseX, mouseY);
            if (labelsPanelOpen) {
                drawLabelsPanel(mc, sr);
            }
            drawDoneButton(mc, sr, mouseX, mouseY);
        } finally {
            endForegroundDraw();
        }
    }

    private static void drawLabelsButton(Minecraft mc, ScaledResolution sr, int mouseX, int mouseY) {
        Rect button = labelsButtonRect(sr);
        drawVanillaButton(mc, button, labelsPanelOpenStaticText(), 1001, mouseX, mouseY);
    }

    private static String labelsPanelOpenStaticText() {
        return INSTANCE.labelsPanelOpen ? "][" : "[]";
    }

    private void drawLabelsPanel(Minecraft mc, ScaledResolution sr) {
        FontRenderer fr = mc.fontRendererObj;
        Rect panel = labelsPanelRect(sr);
        clampLabelsScroll(sr);
        drawPanel(panel);
        drawLabelsPanelCloseButton(panel);
        int x = panel.x1 + PANEL_PAD;
        int y = panel.y1 + PANEL_PAD;
        fr.drawStringWithShadow("\u00a7f\u00a7nMenu", x, y, PANEL_TEXT);
        Rect viewport = labelsBodyViewport(panel);
        beginScissor(mc, sr, viewport);
        try {
            y = labelsBodyY(panel) - labelsScrollOffset;
            drawToggleRow(fr, x, y, "Toggle Mod", GtbStructures.modEnabled, true, false);
            y += ROW_HEIGHT;
            if (!GtbStructures.modEnabled) return;
            y = drawMenuSection(fr, x, y, "Header");
            drawHeaderRow(fr, panel, x, y);
            y += HEADER_CONTROL_ROW_HEIGHT;
            y = drawMenuSection(fr, x, y, "Labels");
            drawToggleRow(fr, x, y, "Cooldown Counter", cooldownLabelChecked(
                    GtbStructures.chatCooldownCounter,
                    GtbStructures.chatCooldownTimerEnabled,
                    GtbStructures.chatCooldownBarEnabled), true, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Environment", environmentLabelVisible(), true, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Handheld Items", GtbStructures.handheldItemOverlay, true, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Hint Structure", GtbStructures.enabled, true, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Custom Scoreboard", GtbStructures.sbConfig.isCustomEnabled(), true, false);
            y += ROW_HEIGHT;
            y = drawMenuSection(fr, x, y, "Game");
            drawToggleRow(fr, x, y, "Stats Tracking", GtbStructures.trackingEnabled, true, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Auto Requeue", GtbStructures.autoRequeue, true, false);
            y += ROW_HEIGHT;
            y = drawMenuSection(fr, x, y, "Chat");
            drawToggleRow(fr, x, y, "Guess Peek", GtbStructures.guessPeek, true, false);
            y += ROW_HEIGHT;
            if (GtbStructures.guessPeek) {
                String guessPeekDisplay = GtbStructures.normalizeGuessPeekDisplay(
                        GtbStructures.guessPeekDisplay);
                drawSection(fr, x + CHILD_ROW_INDENT, toggleTextY(y, fr.FONT_HEIGHT),
                        "Display Option");
                y += ROW_HEIGHT;
                int displayX = x + CHILD_ROW_INDENT * 2;
                drawToggleRow(fr, displayX, y, "Shown When Hovering",
                        "hover".equals(guessPeekDisplay), false, false);
                y += ROW_HEIGHT;
                drawToggleRow(fr, displayX, y, "Shown As Suffix",
                        "suffix".equals(guessPeekDisplay), false, false);
                y += ROW_HEIGHT;
                drawToggleRow(fr, x + CHILD_ROW_INDENT, y, "Show Lang Codes", GtbStructures.guessPeekShowLangCodes, true, false);
                y += ROW_HEIGHT;
            }
            drawToggleRow(fr, x, y, "Show Themes Matching The Initial Hint", GtbStructures.showAllMatching, true, false);
            y += ROW_HEIGHT;
            if (GtbStructures.showAllMatching) {
                drawToggleRow(fr, x + CHILD_ROW_INDENT, y, "Show English Translations", GtbStructures.showAllMatchingShowEnglish, true, false);
                y += ROW_HEIGHT;
            }
            drawToggleRow(fr, x, y, "Reveal Theme Point Value In Chat",
                    GtbStructures.themePointReveal, true, false);
            y += ROW_HEIGHT;
            if (GtbStructures.themePointReveal) {
                drawToggleRow(fr, x + CHILD_ROW_INDENT, y, "Still Reveal After Correct Guess",
                        GtbStructures.themePointRevealShowAfterGuess, true, false);
                y += ROW_HEIGHT;
            }
            drawToggleRow(fr, x, y, "Shortcut Reminder", GtbStructures.shortcutReminder, true, false);
            y += ROW_HEIGHT;
            if (GtbStructures.shortcutReminder) {
                drawToggleRow(fr, x + CHILD_ROW_INDENT, y, "Show Multiwords",
                        GtbStructures.shortcutReminderShowMultiwords, true, false);
                y += ROW_HEIGHT;
                if (GtbStructures.shortcutReminderShowMultiwords) {
                    drawToggleRow(fr, x + CHILD_ROW_INDENT * 2, y, "Show All Multiwords",
                            GtbStructures.shortcutReminderShowAllMultiwords, true, false);
                    y += ROW_HEIGHT;
                }
                drawToggleRow(fr, x + CHILD_ROW_INDENT, y, "Show Non-ASCII",
                        GtbStructures.shortcutReminderShowNonAscii, true, false);
                y += ROW_HEIGHT;
                if (GtbStructures.shortcutReminderShowNonAscii) {
                    int categoryX = x + CHILD_ROW_INDENT * 2;
                    drawToggleRow(fr, categoryX, y, "Extended Latin",
                            GtbStructures.shortcutReminderShowExtendedLatin, true, false);
                    y += ROW_HEIGHT;
                    drawToggleRow(fr, categoryX, y, "Cyrillic",
                            GtbStructures.shortcutReminderShowCyrillic, true, false);
                    y += ROW_HEIGHT;
                    drawToggleRow(fr, categoryX, y, "Hanzi",
                            GtbStructures.shortcutReminderShowHanzi, true, false);
                    y += ROW_HEIGHT;
                    drawToggleRow(fr, categoryX, y, "Kanji & Kana",
                            GtbStructures.shortcutReminderShowJapanese, true, false);
                    y += ROW_HEIGHT;
                    drawToggleRow(fr, categoryX, y, "Hangul",
                            GtbStructures.shortcutReminderShowHangul, true, false);
                    y += ROW_HEIGHT;
                }
                drawToggleRow(fr, x + CHILD_ROW_INDENT, y, "Show All Shortcuts",
                        GtbStructures.shortcutReminderShowAllShortcuts, true, false);
                y += ROW_HEIGHT;
            }
            y = drawMenuSection(fr, x, y, "Experimental");
            drawToggleRow(fr, x, y, "Scramble Guess", GtbStructures.scrambleGuess, true, false);
            y += ROW_HEIGHT;
            if (GtbStructures.scrambleGuess) {
                drawToggleRow(fr, x + CHILD_ROW_INDENT, y, "Fancy Mode",
                        GtbStructures.scrambleGuessFancy, true, false);
                y += ROW_HEIGHT;
            }
            drawToggleRow(fr, x, y, "Troll Mode", GtbStructures.trollMode, true, false);
            y += ROW_HEIGHT;
            if (GtbStructures.trollMode) {
                drawToggleRow(fr, x + CHILD_ROW_INDENT, y, "Disable upon game start",
                        GtbStructures.trollModeAutoDisable, true, false);
            }
        } finally {
            GL11.glPopAttrib();
        }
        drawLabelsScrollbar(panel);
    }

    private static Rect labelsPanelRect(ScaledResolution sr) {
        int x2 = sr.getScaledWidth() - MARGIN;
        int y = labelsButtonRect(sr).y2 + 4;
        int bottomLimit = panelBottomLimit(sr);
        int available = bottomLimit - y;
        if (available < PANEL_PAD * 2 + TITLE_HEIGHT + ROW_HEIGHT) {
            y = MARGIN;
            available = bottomLimit - y;
        }
        int height = labelsPanelHeightForAvailableForTest(labelsPanelHeight(), available);
        return new Rect(x2 - LABELS_PANEL_WIDTH, y, x2, y + height);
    }

    private static Rect labelsBodyViewport(Rect panel) {
        return new Rect(panel.x1 + 1, labelsBodyY(panel), panel.x2 - 1,
                Math.max(labelsBodyY(panel), panel.y2 - PANEL_PAD));
    }

    private static int labelsBodyY(Rect panel) {
        return panel.y1 + PANEL_PAD + TITLE_HEIGHT + 4;
    }

    private static int labelsPanelHeight() {
        if (!GtbStructures.modEnabled) return collapsedLabelsPanelHeight();
        int rows = labelsMenuRowCount(
                GtbStructures.guessPeek,
                GtbStructures.showAllMatching,
                GtbStructures.themePointReveal,
                GtbStructures.shortcutReminder,
                GtbStructures.shortcutReminderShowNonAscii,
                GtbStructures.shortcutReminderShowMultiwords,
                GtbStructures.scrambleGuess,
                GtbStructures.trollMode);
        return PANEL_PAD * 2 + TITLE_HEIGHT + 4
                + (rows + 1) * ROW_HEIGHT
                + (HEADER_CONTROL_ROW_HEIGHT - ROW_HEIGHT)
                + MENU_SECTION_COUNT * sectionBlockHeight();
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

    private static void beginScissor(Minecraft mc, ScaledResolution sr, Rect rect) {
        int scale = sr.getScaleFactor();
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(rect.x1 * scale,
                mc.displayHeight - rect.y2 * scale,
                Math.max(0, rect.width() * scale),
                Math.max(0, rect.height() * scale));
    }

    private static Rect labelsScrollbarTrack(Rect panel) {
        Rect viewport = labelsBodyViewport(panel);
        int x2 = panel.x2 - 2;
        return new Rect(x2 - LABELS_SCROLLBAR_WIDTH, viewport.y1,
                x2, viewport.y2);
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

    private void drawLabelsScrollbar(Rect panel) {
        Rect thumb = labelsScrollbarThumb(panel, labelsScrollOffset);
        if (thumb == null) return;
        Rect track = labelsScrollbarTrack(panel);
        Gui.drawRect(track.x1, track.y1, track.x2, track.y2, 0x442A313B);
        Gui.drawRect(thumb.x1, thumb.y1, thumb.x2, thumb.y2,
                labelsScrollbarDragging ? 0xFFD5DCE5 : 0xFF9EA8B5);
        drawControlFeedback("labels-scrollbar", thumb);
    }

    private void clampLabelsScroll(ScaledResolution sr) {
        int maxScroll = labelsMaxScroll(labelsPanelRect(sr));
        labelsScrollOffset = Math.max(0, Math.min(maxScroll, labelsScrollOffset));
        if (maxScroll == 0) labelsScrollbarDragging = false;
    }

    private void scrollLabelsBy(int amount, ScaledResolution sr) {
        labelsScrollOffset += amount;
        clampLabelsScroll(sr);
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

    private void updateLabelsScrollbarDrag(ScaledResolution sr, int mouseY) {
        Rect panel = labelsPanelRect(sr);
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

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                        boolean shortcutReminder, boolean showNonAscii) {
        return labelsPanelHeightForTest(guessPeek, showMatching,
                shortcutReminder, showNonAscii, false);
    }

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                        boolean shortcutReminder, boolean showNonAscii,
                                        boolean showMultiwords) {
        return labelsPanelHeightForTest(guessPeek, showMatching, true,
                shortcutReminder, showNonAscii, showMultiwords, false);
    }

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                        boolean themePointReveal, boolean shortcutReminder,
                                        boolean showNonAscii, boolean showMultiwords,
                                        boolean scrambleGuess) {
        return labelsPanelHeightForTest(guessPeek, showMatching, themePointReveal,
                shortcutReminder, showNonAscii, showMultiwords, scrambleGuess, false);
    }

    static int labelsPanelHeightForTest(boolean guessPeek, boolean showMatching,
                                        boolean themePointReveal, boolean shortcutReminder,
                                        boolean showNonAscii, boolean showMultiwords,
                                        boolean scrambleGuess, boolean trollMode) {
        int rows = labelsMenuRowCount(guessPeek, showMatching, themePointReveal,
                shortcutReminder, showNonAscii, showMultiwords, scrambleGuess, trollMode);
        return PANEL_PAD * 2 + TITLE_HEIGHT + 4
                + rows * ROW_HEIGHT
                + (HEADER_CONTROL_ROW_HEIGHT - ROW_HEIGHT)
                + MENU_SECTION_COUNT * sectionBlockHeight();
    }

    static int labelsMenuRowCountForTest(boolean guessPeek, boolean showMatching,
                                         boolean themePointReveal, boolean shortcutReminder,
                                         boolean showNonAscii, boolean showMultiwords,
                                         boolean scrambleGuess) {
        return labelsMenuRowCountForTest(guessPeek, showMatching, themePointReveal,
                shortcutReminder, showNonAscii, showMultiwords, scrambleGuess, false);
    }

    static int labelsMenuRowCountForTest(boolean guessPeek, boolean showMatching,
                                         boolean themePointReveal, boolean shortcutReminder,
                                         boolean showNonAscii, boolean showMultiwords,
                                         boolean scrambleGuess, boolean trollMode) {
        return labelsMenuRowCount(guessPeek, showMatching, themePointReveal,
                shortcutReminder, showNonAscii, showMultiwords, scrambleGuess, trollMode);
    }

    static int headerControlRowHeightForTest() {
        return HEADER_CONTROL_ROW_HEIGHT;
    }

    private static int labelsMenuRowCount(boolean guessPeek, boolean showMatching,
                                          boolean themePointReveal, boolean shortcutReminder,
                                          boolean showNonAscii, boolean showMultiwords,
                                          boolean scrambleGuess, boolean trollMode) {
        int rows = 5; // visible canvas labels
        rows += 2; // game settings
        rows += 1; // chat header
        rows += 1 + (guessPeek ? 4 : 0); // display label, two modes, and language codes
        rows += 1 + (showMatching ? 1 : 0);
        rows += 1 + (themePointReveal ? 1 : 0);
        rows += 1; // shortcut reminder
        if (shortcutReminder) {
            rows += 3;
            if (showMultiwords) rows += 1;
            if (showNonAscii) rows += 5;
        }
        rows += 2 + (scrambleGuess ? 1 : 0) + (trollMode ? 1 : 0);
        return rows;
    }

    private static int menuSectionYAfterForClick(int y) {
        return y + sectionBlockHeight();
    }

    private static Rect labelsButtonRect(ScaledResolution sr) {
        int inset = LABELS_BUTTON_SIZE / 2;
        int x2 = sr.getScaledWidth() - MARGIN - inset;
        int y1 = MARGIN + inset;
        return new Rect(x2 - LABELS_BUTTON_SIZE, y1, x2, y1 + LABELS_BUTTON_SIZE);
    }

    private static Rect doneButtonRect(ScaledResolution sr) {
        int x = (sr.getScaledWidth() - DONE_BUTTON_WIDTH) / 2;
        int y = sr.getScaledHeight() - DONE_BUTTON_HEIGHT - 8;
        return new Rect(x, y, x + DONE_BUTTON_WIDTH, y + DONE_BUTTON_HEIGHT);
    }

    private static int panelBottomLimit(ScaledResolution sr) {
        return Math.max(MARGIN, doneButtonRect(sr).y1 - DONE_BUTTON_HEIGHT / 2);
    }

    private static void drawDoneButton(Minecraft mc, ScaledResolution sr, int mouseX, int mouseY) {
        Rect button = doneButtonRect(sr);
        drawVanillaButton(mc, button, "Done", 1002, mouseX, mouseY);
    }

    private static void drawVanillaButton(Minecraft mc, Rect rect, String text, int id, int mouseX, int mouseY) {
        GuiButton button = new GuiButton(id, rect.x1, rect.y1, rect.width(), rect.height(), text);
        button.drawButton(mc, mouseX, mouseY);
    }

    private boolean handleLabelsPanelClick(ScaledResolution sr, int mouseX, int mouseY) {
        if (labelsButtonRect(sr).contains(mouseX, mouseY)) {
            labelsPanelOpen = !labelsPanelOpen;
            labelsScrollbarDragging = false;
            if (labelsPanelOpen) labelsScrollOffset = 0;
            return true;
        }
        if (!labelsPanelOpen) return false;
        Rect panel = labelsPanelRect(sr);
        if (!panel.contains(mouseX, mouseY)) return false;
        if (panelCloseRect(panel).contains(mouseX, mouseY)) {
            labelsPanelOpen = false;
            labelsScrollbarDragging = false;
            return true;
        }
        clampLabelsScroll(sr);
        if (beginLabelsScrollbarDrag(panel, mouseX, mouseY)) return true;
        if (!labelsBodyViewport(panel).contains(mouseX, mouseY)) return true;
        FontRenderer fr = Minecraft.getMinecraft() == null ? null : Minecraft.getMinecraft().fontRendererObj;
        if (fr == null) return true;
        int x = panel.x1 + PANEL_PAD;
        int y = labelsBodyY(panel) - labelsScrollOffset;
        if (toggleHit(fr, x, y, "Toggle Mod", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.modEnabled = !GtbStructures.modEnabled;
            GtbStructures.saveConfig();
            labelsScrollOffset = 0;
            if (!GtbStructures.modEnabled) deselect();
            return true;
        }
        y += ROW_HEIGHT;
        if (!GtbStructures.modEnabled) return true;
        y = menuSectionYAfterForClick(y);
        if (handleHeaderRowClick(fr, panel, x, y, mouseX, mouseY)) return true;
        y += HEADER_CONTROL_ROW_HEIGHT;
        y = menuSectionYAfterForClick(y);
        if (toggleHit(fr, x, y, "Cooldown Counter", mouseX, mouseY)) {
            recordUndo();
            applyCooldownState(toggleCooldownLabelState(
                    GtbStructures.chatCooldownCounter,
                    GtbStructures.chatCooldownTimerEnabled,
                    GtbStructures.chatCooldownBarEnabled));
            GtbStructures.saveConfig();
            if (!GtbStructures.chatCooldownCounter) clearPanelForTarget("cooldown");
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(fr, x, y, "Environment", mouseX, mouseY)) {
            toggleEnvironmentOverlay();
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(fr, x, y, "Handheld Items", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.handheldItemOverlay = !GtbStructures.handheldItemOverlay;
            GtbStructures.saveConfig();
            if (!GtbStructures.handheldItemOverlay) clearPanelForTarget("handheld");
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(fr, x, y, "Hint Structure", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.enabled = !GtbStructures.enabled;
            GtbStructures.saveConfig();
            if (!GtbStructures.enabled) clearPanelForTarget("hint");
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(fr, x, y, "Custom Scoreboard", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.sbConfig.toggleCustom();
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        y = menuSectionYAfterForClick(y);
        if (toggleHit(fr, x, y, "Stats Tracking", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.trackingEnabled = !GtbStructures.trackingEnabled;
            GameStats.INSTANCE.setTrackingEnabled(GtbStructures.trackingEnabled);
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        if (toggleHit(fr, x, y, "Auto Requeue", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.autoRequeue = !GtbStructures.autoRequeue;
            GameStats.INSTANCE.setAutoRequeue(GtbStructures.autoRequeue);
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        y = menuSectionYAfterForClick(y);
        if (toggleHit(fr, x, y, "Guess Peek", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.guessPeek = !GtbStructures.guessPeek;
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructures.guessPeek) {
            y += ROW_HEIGHT; // Display Option label
            int displayX = x + CHILD_ROW_INDENT * 2;
            if (toggleHit(fr, displayX, y, "Shown When Hovering", mouseX, mouseY)) {
                setGuessPeekDisplay("hover");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, displayX, y, "Shown As Suffix", mouseX, mouseY)) {
                setGuessPeekDisplay("suffix");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x + CHILD_ROW_INDENT, y, "Show Lang Codes", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.guessPeekShowLangCodes = !GtbStructures.guessPeekShowLangCodes;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (toggleHit(fr, x, y, "Show Themes Matching The Initial Hint", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.showAllMatching = !GtbStructures.showAllMatching;
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructures.showAllMatching) {
            if (toggleHit(fr, x + CHILD_ROW_INDENT, y, "Show English Translations", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.showAllMatchingShowEnglish = !GtbStructures.showAllMatchingShowEnglish;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (toggleHit(fr, x, y, "Reveal Theme Point Value In Chat", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.themePointReveal = !GtbStructures.themePointReveal;
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructures.themePointReveal) {
            if (toggleHit(fr, x + CHILD_ROW_INDENT, y,
                    "Still Reveal After Correct Guess", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.themePointRevealShowAfterGuess =
                        !GtbStructures.themePointRevealShowAfterGuess;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
        }
        if (toggleHit(fr, x, y, "Shortcut Reminder", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.shortcutReminder = !GtbStructures.shortcutReminder;
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructures.shortcutReminder) {
            if (toggleHit(fr, x + CHILD_ROW_INDENT, y, "Show Multiwords", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.shortcutReminderShowMultiwords =
                        !GtbStructures.shortcutReminderShowMultiwords;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (GtbStructures.shortcutReminderShowMultiwords) {
                if (toggleHit(fr, x + CHILD_ROW_INDENT * 2, y,
                        "Show All Multiwords", mouseX, mouseY)) {
                    recordUndo();
                    GtbStructures.shortcutReminderShowAllMultiwords =
                            !GtbStructures.shortcutReminderShowAllMultiwords;
                    GtbStructures.saveConfig();
                    return true;
                }
                y += ROW_HEIGHT;
            }
            if (toggleHit(fr, x + CHILD_ROW_INDENT, y, "Show Non-ASCII", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.setShortcutReminderShowNonAscii(
                        !GtbStructures.shortcutReminderShowNonAscii);
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (GtbStructures.shortcutReminderShowNonAscii) {
                int categoryX = x + CHILD_ROW_INDENT * 2;
                if (toggleHit(fr, categoryX, y, "Extended Latin", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(
                            GtbStructures.ShortcutNonAsciiCategory.EXTENDED_LATIN);
                    GtbStructures.saveConfig();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, categoryX, y, "Cyrillic", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(
                            GtbStructures.ShortcutNonAsciiCategory.CYRILLIC);
                    GtbStructures.saveConfig();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, categoryX, y, "Hanzi", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(
                            GtbStructures.ShortcutNonAsciiCategory.HANZI);
                    GtbStructures.saveConfig();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, categoryX, y, "Kanji & Kana", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(
                            GtbStructures.ShortcutNonAsciiCategory.JAPANESE);
                    GtbStructures.saveConfig();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, categoryX, y, "Hangul", mouseX, mouseY)) {
                    recordUndo();
                    toggleShortcutNonAsciiCategory(
                            GtbStructures.ShortcutNonAsciiCategory.HANGUL);
                    GtbStructures.saveConfig();
                    return true;
                }
                y += ROW_HEIGHT;
            }
            if (toggleHit(fr, x + CHILD_ROW_INDENT, y, "Show All Shortcuts", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.shortcutReminderShowAllShortcuts =
                        !GtbStructures.shortcutReminderShowAllShortcuts;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
        }
        y = menuSectionYAfterForClick(y);
        if (toggleHit(fr, x, y, "Scramble Guess", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.scrambleGuess = !GtbStructures.scrambleGuess;
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructures.scrambleGuess
                && toggleHit(fr, x + CHILD_ROW_INDENT, y, "Fancy Mode", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.scrambleGuessFancy = !GtbStructures.scrambleGuessFancy;
            GtbStructures.saveConfig();
            return true;
        }
        if (GtbStructures.scrambleGuess) y += ROW_HEIGHT;
        if (toggleHit(fr, x, y, "Troll Mode", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.trollMode = !GtbStructures.trollMode;
            GtbStructures.saveConfig();
            return true;
        }
        y += ROW_HEIGHT;
        if (GtbStructures.trollMode
                && toggleHit(fr, x + CHILD_ROW_INDENT, y, "Disable upon game start", mouseX, mouseY)) {
            recordUndo();
            GtbStructures.trollModeAutoDisable = !GtbStructures.trollModeAutoDisable;
            GtbStructures.saveConfig();
            return true;
        }
        return true;
    }

    private void drawHeaderRow(FontRenderer fr, Rect panel, int x, int y) {
        if (headerEditing) {
            Rect fieldRect = headerTextFieldRect(panel, x, y, fr);
            ensureHeaderTextField(fr, fieldRect);
            headerTextField.updateCursorCounter();
            headerTextField.drawTextBox();
            return;
        }
        int valueX = headerValueX(fr, x);
        int textY = y + (HEADER_CONTROL_ROW_HEIGHT - fr.FONT_HEIGHT) / 2;
        String rendered = ChatHeader.rendered();
        fr.drawStringWithShadow(rendered + "\u00a7r", valueX, textY, PANEL_TEXT);
        Rect reset = headerResetRect(fr, x, y);
        boolean resetHovered = reset.contains(drawMouseX, drawMouseY);
        fr.drawStringWithShadow((resetHovered ? "\u00a7f\u00a7n" : "\u00a77") + "Reset",
                reset.x1, textY, resetHovered ? PANEL_TEXT : PANEL_MUTED);
    }

    private boolean handleHeaderRowClick(FontRenderer fr, Rect panel, int x, int y,
                                         int mouseX, int mouseY) {
        Rect row = new Rect(x, y, panel.x2 - PANEL_PAD, y + HEADER_CONTROL_ROW_HEIGHT);
        if (!row.contains(mouseX, mouseY)) return false;
        if (headerEditing) {
            ensureHeaderTextField(fr, headerTextFieldRect(panel, x, y, fr));
            headerTextField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        if (headerResetRect(fr, x, y).contains(mouseX, mouseY)) {
            if (!ChatHeader.DEFAULT_RAW.equals(ChatHeader.raw())) {
                recordUndo();
                ChatHeader.reset();
                CommandGtb.refreshHeader();
                GtbStructures.saveConfig();
            }
            return true;
        }
        if (headerValueRect(fr, x, y).contains(mouseX, mouseY)) {
            beginHeaderEditing(fr, headerTextFieldRect(panel, x, y, fr));
        }
        return true;
    }

    private void beginHeaderEditing(FontRenderer fr, Rect rect) {
        headerEditing = true;
        headerRepeatEventsWereEnabled = Keyboard.areRepeatEventsEnabled();
        Keyboard.enableRepeatEvents(true);
        headerTextField = new GuiTextField(1003, fr, rect.x1, rect.y1, rect.width(), rect.height());
        headerTextField.setMaxStringLength(256);
        headerTextField.setText(ChatHeader.raw());
        headerTextField.setFocused(true);
        headerTextField.setCursorPositionEnd();
    }

    private void ensureHeaderTextField(FontRenderer fr, Rect rect) {
        if (headerTextField == null) {
            beginHeaderEditing(fr, rect);
            return;
        }
        headerTextField.xPosition = rect.x1;
        headerTextField.yPosition = rect.y1;
        headerTextField.width = rect.width();
        headerTextField.height = rect.height();
    }

    private void finishHeaderEditing() {
        if (!headerEditing) return;
        String requested = headerTextField == null ? ChatHeader.raw() : headerTextField.getText();
        String prepared = ChatHeader.prepare(requested);
        if (prepared != null && !prepared.equals(ChatHeader.raw())) {
            ConfigSnapshot before = ConfigSnapshot.capture();
            ChatHeader.set(requested);
            CommandGtb.refreshHeader();
            recordUndo(before);
            GtbStructures.saveConfig();
        }
        if (headerTextField != null) headerTextField.setFocused(false);
        headerTextField = null;
        headerEditing = false;
        Keyboard.enableRepeatEvents(headerRepeatEventsWereEnabled);
        headerRepeatEventsWereEnabled = false;
    }

    private static boolean validHeaderEditCandidate(String value) {
        if (ChatHeader.visibleLength(value) <= ChatHeader.MAX_VISIBLE_LENGTH) return true;
        if (value == null || value.isEmpty()) return false;
        char last = value.charAt(value.length() - 1);
        return (last == '&' || last == '\u00a7')
                && ChatHeader.visibleLength(value.substring(0, value.length() - 1))
                <= ChatHeader.MAX_VISIBLE_LENGTH;
    }

    private static int headerValueX(FontRenderer fr, int x) {
        return x;
    }

    private static Rect headerValueRect(FontRenderer fr, int x, int y) {
        int valueX = headerValueX(fr, x);
        return new Rect(valueX, y, valueX + fr.getStringWidth(ChatHeader.rendered()),
                y + HEADER_CONTROL_ROW_HEIGHT);
    }

    private static Rect headerResetRect(FontRenderer fr, int x, int y) {
        int resetX = headerValueX(fr, x) + fr.getStringWidth(ChatHeader.rendered()) + 7;
        return new Rect(resetX, y, resetX + fr.getStringWidth("Reset"),
                y + HEADER_CONTROL_ROW_HEIGHT);
    }

    private static Rect headerTextFieldRect(Rect panel, int x, int y, FontRenderer fr) {
        int fieldX = headerValueX(fr, x);
        int fieldRight = panel.x2 - PANEL_PAD - LABELS_SCROLLBAR_WIDTH - 3;
        return new Rect(fieldX, y, Math.max(fieldX + 16, fieldRight),
                y + HEADER_CONTROL_ROW_HEIGHT);
    }

    private Rect headerTextFieldRect(ScaledResolution sr) {
        if (!labelsPanelOpen) return new Rect(0, 0, 0, 0);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return new Rect(0, 0, 0, 0);
        Rect panel = labelsPanelRect(sr);
        int x = panel.x1 + PANEL_PAD;
        int y = labelsBodyY(panel) - labelsScrollOffset;
        y = menuSectionYAfterForClick(y);
        return headerTextFieldRect(panel, x, y, mc.fontRendererObj);
    }

    private void setGuessPeekDisplay(String display) {
        String normalized = GtbStructures.normalizeGuessPeekDisplay(display);
        if (normalized.equals(GtbStructures.guessPeekDisplay)) return;
        recordUndo();
        GtbStructures.guessPeekDisplay = normalized;
        GtbStructures.saveConfig();
    }

    private static void toggleShortcutNonAsciiCategory(
            GtbStructures.ShortcutNonAsciiCategory category) {
        GtbStructures.setShortcutNonAsciiCategoryEnabled(
                category, !GtbStructures.shortcutNonAsciiCategoryEnabled(category));
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

    private static boolean environmentLabelVisible() {
        return GtbStructures.environmentOverlay
                && (GtbStructures.environmentWeatherEnabled
                || GtbStructures.environmentBiomeEnabled
                || GtbStructures.environmentTimeEnabled);
    }

    static boolean environmentLabelVisibleForTest() {
        return environmentLabelVisible();
    }

    private static boolean environmentRowVisible(String row) {
        if (!GtbStructures.environmentOverlay) return false;
        if ("weather".equals(row)) return GtbStructures.environmentWeatherEnabled;
        if ("biome".equals(row)) return GtbStructures.environmentBiomeEnabled;
        if ("time".equals(row)) return GtbStructures.environmentTimeEnabled;
        return false;
    }

    private void toggleEnvironmentRow(String row) {
        boolean visible = environmentRowVisible(row);
        recordUndo();
        if ("weather".equals(row)) {
            GtbStructures.environmentWeatherEnabled = !visible;
        } else if ("biome".equals(row)) {
            GtbStructures.environmentBiomeEnabled = !visible;
        } else if ("time".equals(row)) {
            GtbStructures.environmentTimeEnabled = !visible;
        }
        if (!visible) GtbStructures.environmentOverlay = true;
        GtbStructures.saveConfig();
        if (visible) clearPanelForTarget("environment_" + row);
    }

    private static boolean environmentChatNoticeEnabled(String row) {
        if ("weather".equals(row)) return GtbStructures.environmentWeatherChatNotice;
        if ("biome".equals(row)) return GtbStructures.environmentBiomeChatNotice;
        return GtbStructures.environmentTimeChatNotice;
    }

    static boolean environmentChatNoticeEnabledForTest(String row) {
        return environmentChatNoticeEnabled(row);
    }

    private void toggleEnvironmentChatNotice(String row) {
        recordUndo();
        boolean enabled = !environmentChatNoticeEnabled(row);
        if ("weather".equals(row)) {
            GtbStructures.environmentWeatherChatNotice = enabled;
        } else if ("biome".equals(row)) {
            GtbStructures.environmentBiomeChatNotice = enabled;
        } else {
            GtbStructures.environmentTimeChatNotice = enabled;
        }
        GtbStructures.saveConfig();
    }

    private void toggleEnvironmentOverlay() {
        recordUndo();
        if (environmentLabelVisible()) {
            GtbStructures.environmentOverlay = false;
            GtbStructures.saveConfig();
            clearPanelForTargetFamily("environment");
            return;
        }
        GtbStructures.environmentOverlay = true;
        if (!GtbStructures.environmentWeatherEnabled
                && !GtbStructures.environmentBiomeEnabled
                && !GtbStructures.environmentTimeEnabled) {
            GtbStructures.environmentWeatherEnabled = true;
            GtbStructures.environmentBiomeEnabled = true;
            GtbStructures.environmentTimeEnabled = true;
        }
        GtbStructures.saveConfig();
    }

    private void drawSettingsPanel(Minecraft mc, ScaledResolution sr) {
        Label label = activePanelLabel();
        if (label == null) {
            clearActivePanel();
            return;
        }
        FontRenderer fr = mc.fontRendererObj;
        Rect panel = settingsPanelRect(label, sr);
        drawPanel(panel);
        drawPanelCloseButton(panel);
        int x = panel.x1 + PANEL_PAD;
        int y = panel.y1 + PANEL_PAD;
        fr.drawStringWithShadow("\u00a7f\u00a7n" + settingsTitle(label), x, y, PANEL_TEXT);
        y += TITLE_HEIGHT + 5;

        String key = label.target.key;
        if ("scoreboard".equals(key)) {
            drawSection(fr, x, y, "Skin");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Vanilla", GtbStructures.sbConfig.isSkin("vanilla"), false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Default", GtbStructures.sbConfig.isSkin("default"), false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Hypixel", GtbStructures.sbConfig.isSkin("hypixel"), false, false);
            if (!scoreboardStyleOptionsVisible(GtbStructures.sbConfig.skin)) return;
            y += ROW_HEIGHT + 2;
            drawSection(fr, x, y, "Visuals");
            y += ROW_HEIGHT;
            drawSliderRow(fr, x, y, "Background Opacity", GtbStructures.sbConfig.backgroundOpacity);
            y += SLIDER_ROW_HEIGHT;
            drawSliderRow(fr, x, y, "Highlight Intensity", GtbStructures.sbConfig.highlightIntensity);
            y += SLIDER_ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Text Shadow", GtbStructures.sbConfig.textShadow, false, false);
            y += ROW_HEIGHT + 2;
            drawSection(fr, x, y, "Details");
            y += ROW_HEIGHT;
            drawTriStateRow(fr, x, y, "Show Points Gained In Round",
                    GtbStructures.sbConfig.showPointsGainedInRound);
            y += ROW_HEIGHT;
            drawTriStateRow(fr, x, y, "Show Places", GtbStructures.sbConfig.showPlaces);
            y += ROW_HEIGHT;
            drawTriStateRow(fr, x, y, "Show Titles", GtbStructures.sbConfig.showTitles);
            y += ROW_HEIGHT;
            drawTriStateRow(fr, x, y, "Show Emblems", GtbStructures.sbConfig.showEmblems);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Place Left Players at Bottom",
                    GtbStructures.sbConfig.leftPlayersAtBottom, false, false);
        } else if ("hint".equals(key)) {
            drawSection(fr, x, y, "Background");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "On", GtbStructures.hintStructureBackground, false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Off", !GtbStructures.hintStructureBackground, false, false);
            y += ROW_HEIGHT + 2;
            drawSection(fr, x, y, "Color Target");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Brackets", "bracket".equals(hintColorTarget), false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Numbers", "number".equals(hintColorTarget), false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Dashes", "hyphen".equals(hintColorTarget), false, false);
            y += ROW_HEIGHT + 3;
            drawColorSwatches(x, y, currentHintColor());
        } else if ("actionbar".equals(key)) {
            drawSection(fr, x, y, "Background");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "On", GtbStructures.hintBackgroundOverlay, false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Off", !GtbStructures.hintBackgroundOverlay, false, false);
        } else if ("cooldown".equals(key)) {
            drawSection(fr, x, y, "Shown Parts");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Timer", GtbStructures.chatCooldownTimerEnabled, true,
                    GtbStructures.chatCooldownTimerEnabled, GtbStructures.chatCooldownBackground);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Bar", GtbStructures.chatCooldownBarEnabled, true,
                    GtbStructures.chatCooldownBarEnabled, GtbStructures.chatCooldownBarBackground);
            y += ROW_HEIGHT + 2;
            drawSection(fr, x, y, "Color Target");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Timer", "timer".equals(cooldownColorTarget), false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Bar", "bar".equals(cooldownColorTarget), false, false);
            y += ROW_HEIGHT + 3;
            drawColorSwatches(x, y, currentCooldownColor());
        } else if ("handheld".equals(key)) {
            drawSection(fr, x, y, "Visible Rows");
            y += ROW_HEIGHT;
            drawStepper(fr, x, y, GtbStructures.handheldItemMaxVisible, 2, 10);
            y += STEPPER_BUTTON_SIZE + 4;
            drawSection(fr, x, y, "Background");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "On", GtbStructures.handheldItemBackground, false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Off", !GtbStructures.handheldItemBackground, false, false);
            y += ROW_HEIGHT + 2;
            drawSection(fr, x, y, "Arrangement");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Vertical", GtbStructures.handheldItemVerticalLayout, false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Horizontal", !GtbStructures.handheldItemVerticalLayout, false, false);
            y += ROW_HEIGHT + 2;
            if (GtbStructures.handheldItemVerticalLayout) {
                drawSection(fr, x, y, "Newest Item");
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Newest At Top", !GtbStructures.handheldItemVerticalNewestAtBottom, false, false);
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Newest At Bottom", GtbStructures.handheldItemVerticalNewestAtBottom, false, false);
                y += ROW_HEIGHT + 2;
                drawSection(fr, x, y, "Captions");
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Item Name", GtbStructures.handheldItemCaptionName, true, false);
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Item ID", GtbStructures.handheldItemCaptionId, true, false);
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Metadata", GtbStructures.handheldItemCaptionMetadata, true, false);
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Spawn Egg Mob Name", GtbStructures.handheldItemCaptionSpawnEggMob, true, false);
                y += ROW_HEIGHT + 2;
            } else {
                drawSection(fr, x, y, "Newest Item");
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Newest At Left", !GtbStructures.handheldItemHorizontalNewestAtRight, false, false);
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Newest At Right", GtbStructures.handheldItemHorizontalNewestAtRight, false, false);
                y += ROW_HEIGHT + 2;
                drawSection(fr, x, y, "Vertical Position");
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Newest At Top", !GtbStructures.handheldItemHorizontalNewestAtBottom, false, false);
                y += ROW_HEIGHT;
                drawToggleRow(fr, x, y, "Newest At Bottom", GtbStructures.handheldItemHorizontalNewestAtBottom, false, false);
                y += ROW_HEIGHT + 2;
            }
            drawSection(fr, x, y, "Spawn Egg Skin");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Classic", "classic".equals(GtbStructures.spawnEggSkin), false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "New", "new".equals(GtbStructures.spawnEggSkin), false, false);
        } else if (key.startsWith("environment")) {
            drawSection(fr, x, y, "Shown Labels");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Weather", environmentRowVisible("weather"), true, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Biome", environmentRowVisible("biome"), true, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Time", environmentRowVisible("time"), true, false);
            y += ROW_HEIGHT + 2;
            drawSection(fr, x, y, "Notify Changes In Chat");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Weather", environmentChatNoticeEnabled("weather"),
                    ENVIRONMENT_CHAT_NOTICE_MULTI_SELECT, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Biome", environmentChatNoticeEnabled("biome"),
                    ENVIRONMENT_CHAT_NOTICE_MULTI_SELECT, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Time", environmentChatNoticeEnabled("time"),
                    ENVIRONMENT_CHAT_NOTICE_MULTI_SELECT, false);
            y += ROW_HEIGHT + 2;
            drawSection(fr, x, y, "Time Item");
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Clock", "clock".equals(GtbStructures.environmentTimeIconPreset), false, false);
            y += ROW_HEIGHT;
            drawToggleRow(fr, x, y, "Colored Blocks", "blocks".equals(GtbStructures.environmentTimeIconPreset), false, false);
        }
    }

    private boolean handleSettingsPanelClick(ScaledResolution sr, int mouseX, int mouseY) {
        Label label = activePanelLabel();
        if (label == null) {
            clearActivePanel();
            return false;
        }
        Rect panel = settingsPanelRect(label, sr);
        if (!panel.contains(mouseX, mouseY)) return false;
        if (panelCloseRect(panel).contains(mouseX, mouseY)) {
            clearActivePanel();
            return true;
        }
        FontRenderer fr = Minecraft.getMinecraft() == null ? null : Minecraft.getMinecraft().fontRendererObj;
        if (fr == null) return true;
        int x = panel.x1 + PANEL_PAD;
        int y = panel.y1 + PANEL_PAD + TITLE_HEIGHT + 5;
        String key = label.target.key;
        if ("scoreboard".equals(key)) {
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Vanilla", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.setSkin("vanilla");
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Default", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.setSkin("default");
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Hypixel", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.setSkin("hypixel");
                GtbStructures.saveConfig();
                return true;
            }
            if (!scoreboardStyleOptionsVisible(GtbStructures.sbConfig.skin)) return true;
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (sliderRowRect(x, y).contains(mouseX, mouseY)) {
                beginScoreboardSliderDrag("backgroundOpacity", x, mouseX);
                return true;
            }
            y += SLIDER_ROW_HEIGHT;
            if (sliderRowRect(x, y).contains(mouseX, mouseY)) {
                beginScoreboardSliderDrag("highlightIntensity", x, mouseX);
                return true;
            }
            y += SLIDER_ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Text Shadow", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.textShadow = !GtbStructures.sbConfig.textShadow;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (triStateHit(fr, x, y, "Show Points Gained In Round", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.showPointsGainedInRound =
                        ScoreboardConfig.cycleOption(GtbStructures.sbConfig.showPointsGainedInRound);
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (triStateHit(fr, x, y, "Show Places", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.showPlaces =
                        ScoreboardConfig.cycleOption(GtbStructures.sbConfig.showPlaces);
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (triStateHit(fr, x, y, "Show Titles", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.showTitles =
                        ScoreboardConfig.cycleOption(GtbStructures.sbConfig.showTitles);
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (triStateHit(fr, x, y, "Show Emblems", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.showEmblems =
                        ScoreboardConfig.cycleOption(GtbStructures.sbConfig.showEmblems);
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Place Left Players at Bottom", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.sbConfig.leftPlayersAtBottom =
                        !GtbStructures.sbConfig.leftPlayersAtBottom;
                GtbStructures.saveConfig();
                return true;
            }
        } else if ("hint".equals(key)) {
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "On", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.hintStructureBackground = true;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Off", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.hintStructureBackground = false;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Brackets", mouseX, mouseY)) {
                hintColorTarget = "bracket";
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Numbers", mouseX, mouseY)) {
                hintColorTarget = "number";
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Dashes", mouseX, mouseY)) {
                hintColorTarget = "hyphen";
                return true;
            }
            y += ROW_HEIGHT + 3;
            String color = colorAt(panel.x1 + PANEL_PAD, y, mouseX, mouseY);
            if (color != null) {
                recordUndo();
                setHintColorTarget(color);
                GtbStructures.saveConfig();
                return true;
            }
        } else if ("actionbar".equals(key)) {
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "On", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.hintBackgroundOverlay = true;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Off", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.hintBackgroundOverlay = false;
                GtbStructures.saveConfig();
                return true;
            }
        } else if ("cooldown".equals(key)) {
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Timer", mouseX, mouseY)
                    || (GtbStructures.chatCooldownTimerEnabled
                    && toggleHit(fr, x + BACKGROUND_COLUMN_OFFSET, y, "Background", mouseX, mouseY))) {
                recordUndo();
                if (GtbStructures.chatCooldownTimerEnabled
                        && toggleHit(fr, x + BACKGROUND_COLUMN_OFFSET, y, "Background", mouseX, mouseY)) {
                    GtbStructures.chatCooldownBackground = !GtbStructures.chatCooldownBackground;
                } else {
                    applyCooldownState(toggleCooldownPartState(
                            GtbStructures.chatCooldownCounter,
                            GtbStructures.chatCooldownTimerEnabled,
                            GtbStructures.chatCooldownBarEnabled, true));
                }
                GtbStructures.saveConfig();
                if (!GtbStructures.chatCooldownCounter) clearPanelForTarget("cooldown");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Bar", mouseX, mouseY)
                    || (GtbStructures.chatCooldownBarEnabled
                    && toggleHit(fr, x + BACKGROUND_COLUMN_OFFSET, y, "Background", mouseX, mouseY))) {
                recordUndo();
                if (GtbStructures.chatCooldownBarEnabled
                        && toggleHit(fr, x + BACKGROUND_COLUMN_OFFSET, y, "Background", mouseX, mouseY)) {
                    GtbStructures.chatCooldownBarBackground = !GtbStructures.chatCooldownBarBackground;
                } else {
                    applyCooldownState(toggleCooldownPartState(
                            GtbStructures.chatCooldownCounter,
                            GtbStructures.chatCooldownTimerEnabled,
                            GtbStructures.chatCooldownBarEnabled, false));
                }
                GtbStructures.saveConfig();
                if (!GtbStructures.chatCooldownCounter) clearPanelForTarget("cooldown");
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Timer", mouseX, mouseY)) {
                cooldownColorTarget = "timer";
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Bar", mouseX, mouseY)) {
                cooldownColorTarget = "bar";
                return true;
            }
            y += ROW_HEIGHT + 3;
            String color = colorAt(panel.x1 + PANEL_PAD, y, mouseX, mouseY);
            if (color != null) {
                recordUndo();
                setCooldownColorTarget(color);
                GtbStructures.saveConfig();
                return true;
            }
        } else if ("handheld".equals(key)) {
            y += ROW_HEIGHT;
            if (stepperRowRect(panel, y).contains(mouseX, mouseY)) {
                if (stepperMinusRect(x, y).contains(mouseX, mouseY)) {
                    recordUndo();
                    GtbStructures.handheldItemMaxVisible = Math.max(2, GtbStructures.handheldItemMaxVisible - 1);
                    GtbStructures.saveConfig();
                } else if (stepperPlusRect(x, y).contains(mouseX, mouseY)) {
                    recordUndo();
                    GtbStructures.handheldItemMaxVisible = Math.min(10, GtbStructures.handheldItemMaxVisible + 1);
                    GtbStructures.saveConfig();
                }
                return true;
            }
            y += STEPPER_BUTTON_SIZE + 4 + ROW_HEIGHT;
            if (toggleHit(fr, x, y, "On", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.handheldItemBackground = true;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Off", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.handheldItemBackground = false;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Vertical", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.handheldItemVerticalLayout = true;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Horizontal", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.handheldItemVerticalLayout = false;
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (GtbStructures.handheldItemVerticalLayout) {
                if (toggleHit(fr, x, y, "Newest At Top", mouseX, mouseY)) {
                    recordUndo();
                    GtbStructures.handheldItemVerticalNewestAtBottom = false;
                    GtbStructures.saveConfig();
                    return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, x, y, "Newest At Bottom", mouseX, mouseY)) {
                    recordUndo();
                    GtbStructures.handheldItemVerticalNewestAtBottom = true;
                    GtbStructures.saveConfig();
                    return true;
                }
                y += ROW_HEIGHT + 2 + ROW_HEIGHT;
                if (toggleHit(fr, x, y, "Item Name", mouseX, mouseY)) {
                    recordUndo(); GtbStructures.handheldItemCaptionName = !GtbStructures.handheldItemCaptionName; GtbStructures.saveConfig(); return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, x, y, "Item ID", mouseX, mouseY)) {
                    recordUndo(); GtbStructures.handheldItemCaptionId = !GtbStructures.handheldItemCaptionId; GtbStructures.saveConfig(); return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, x, y, "Metadata", mouseX, mouseY)) {
                    recordUndo(); GtbStructures.handheldItemCaptionMetadata = !GtbStructures.handheldItemCaptionMetadata; GtbStructures.saveConfig(); return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, x, y, "Spawn Egg Mob Name", mouseX, mouseY)) {
                    recordUndo(); GtbStructures.handheldItemCaptionSpawnEggMob = !GtbStructures.handheldItemCaptionSpawnEggMob; GtbStructures.saveConfig(); return true;
                }
                y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            } else {
                if (toggleHit(fr, x, y, "Newest At Left", mouseX, mouseY)) {
                    recordUndo(); GtbStructures.handheldItemHorizontalNewestAtRight = false; GtbStructures.saveConfig(); return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, x, y, "Newest At Right", mouseX, mouseY)) {
                    recordUndo(); GtbStructures.handheldItemHorizontalNewestAtRight = true; GtbStructures.saveConfig(); return true;
                }
                y += ROW_HEIGHT + 2 + ROW_HEIGHT;
                if (toggleHit(fr, x, y, "Newest At Top", mouseX, mouseY)) {
                    recordUndo(); GtbStructures.handheldItemHorizontalNewestAtBottom = false; GtbStructures.saveConfig(); return true;
                }
                y += ROW_HEIGHT;
                if (toggleHit(fr, x, y, "Newest At Bottom", mouseX, mouseY)) {
                    recordUndo(); GtbStructures.handheldItemHorizontalNewestAtBottom = true; GtbStructures.saveConfig(); return true;
                }
                y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            }
            if (toggleHit(fr, x, y, "Classic", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.spawnEggSkin = "classic";
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "New", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.spawnEggSkin = "new";
                GtbStructures.saveConfig();
                return true;
            }
        } else if (key.startsWith("environment")) {
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Weather", mouseX, mouseY)) {
                toggleEnvironmentRow("weather");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Biome", mouseX, mouseY)) {
                toggleEnvironmentRow("biome");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Time", mouseX, mouseY)) {
                toggleEnvironmentRow("time");
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Weather", mouseX, mouseY)) {
                toggleEnvironmentChatNotice("weather");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Biome", mouseX, mouseY)) {
                toggleEnvironmentChatNotice("biome");
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Time", mouseX, mouseY)) {
                toggleEnvironmentChatNotice("time");
                return true;
            }
            y += ROW_HEIGHT + 2 + ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Clock", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.environmentTimeIconPreset = "clock";
                GtbStructures.saveConfig();
                return true;
            }
            y += ROW_HEIGHT;
            if (toggleHit(fr, x, y, "Colored Blocks", mouseX, mouseY)) {
                recordUndo();
                GtbStructures.environmentTimeIconPreset = "blocks";
                GtbStructures.saveConfig();
                return true;
            }
        }
        return true;
    }

    private void beginScoreboardSliderDrag(String slider, int x, int mouseX) {
        activeSlider = slider == null ? "" : slider;
        activeSliderX = x;
        sliderUndoSnapshot = ConfigSnapshot.capture();
        sliderDirty = false;
        updateActiveScoreboardSlider(mouseX);
    }

    private void updateActiveScoreboardSlider(int mouseX) {
        if (activeSlider.isEmpty()) return;
        float value = sliderValueAt(activeSliderX, mouseX);
        if ("backgroundOpacity".equals(activeSlider)) {
            if (Math.abs(GtbStructures.sbConfig.backgroundOpacity - value) > 0.0001F) {
                GtbStructures.sbConfig.backgroundOpacity = value;
                sliderDirty = true;
            }
        } else if ("highlightIntensity".equals(activeSlider)) {
            if (Math.abs(GtbStructures.sbConfig.highlightIntensity - value) > 0.0001F) {
                GtbStructures.sbConfig.highlightIntensity = value;
                sliderDirty = true;
            }
        }
    }

    private void finishScoreboardSliderDrag() {
        if (sliderDirty && sliderUndoSnapshot != null && !sliderUndoSnapshot.matchesCurrent()) {
            recordUndo(sliderUndoSnapshot);
            GtbStructures.saveConfig();
        }
        activeSlider = "";
        sliderUndoSnapshot = null;
        sliderDirty = false;
    }

    private boolean isPointInEditorPanel(ScaledResolution sr, int mouseX, int mouseY) {
        if (doneButtonRect(sr).contains(mouseX, mouseY)) return true;
        if (labelsButtonRect(sr).contains(mouseX, mouseY)) return true;
        if (labelsPanelOpen && labelsPanelRect(sr).contains(mouseX, mouseY)) return true;
        Label label = activePanelLabel();
        return label != null && settingsPanelRect(label, sr).contains(mouseX, mouseY);
    }

    private Rect settingsPanelRect(Label label, ScaledResolution sr) {
        int width = settingsPanelWidth(label);
        int height = settingsPanelHeight(label);
        int x;
        int y;
        String family = panelFamily(label);
        boolean labelDragControlsPanel = dragArmed && dragPanelFollows;
        if (!pinnedPanelFamily.isEmpty() && pinnedPanelFamily.equals(family)) {
            x = labelDragControlsPanel ? pinnedPanelX
                    : pinnedPanelXFollowingLabel(label, width, sr.getScaledWidth());
            y = pinnedPanelY;
        } else {
            Rect natural = naturalSettingsPanelRect(label, sr, width, height);
            x = natural.x1;
            y = natural.y1;
        }
        Rect clamped = clampPanelRect(x, y, width, height, sr);
        pinnedPanelFamily = family;
        pinnedPanelX = clamped.x1;
        pinnedPanelY = clamped.y1;
        if (!labelDragControlsPanel) rememberPinnedPanelAnchor(label, clamped);
        return clamped;
    }

    private static Rect naturalSettingsPanelRect(Label label, ScaledResolution sr, int width, int height) {
        int x = label.x2 + SETTINGS_PANEL_GAP;
        if (x + width > sr.getScaledWidth() - MARGIN) {
            x = label.x1 - width - SETTINGS_PANEL_GAP;
        }
        int y = label.y1;
        return clampPanelRect(x, y, width, height, sr);
    }

    private static Rect clampPanelRect(int x, int y, int width, int height, ScaledResolution sr) {
        if (x < MARGIN) x = MARGIN;
        if (x + width > sr.getScaledWidth() - MARGIN) x = Math.max(MARGIN, sr.getScaledWidth() - width - MARGIN);
        int bottomLimit = panelBottomLimit(sr);
        if (y + height > bottomLimit) y = bottomLimit - height;
        if (y < MARGIN) y = MARGIN;
        return new Rect(x, y, x + width, y + height);
    }

    private static int settingsPanelWidth(Label label) {
        String key = label.target.key;
        if ("cooldown".equals(key)) return 155;
        if ("handheld".equals(key)) return 145;
        if ("environment".equals(key) || key.startsWith("environment_")) return 150;
        if ("hint".equals(key)) return 145;
        if ("scoreboard".equals(key)) return 202;
        return 128;
    }

    private static int settingsPanelHeight(Label label) {
        String key = label.target.key;
        if ("hint".equals(key)) return 150;
        if ("handheld".equals(key)) return 282;
        if ("environment".equals(key) || key.startsWith("environment_")) return ENVIRONMENT_SETTINGS_PANEL_HEIGHT;
        if ("cooldown".equals(key)) return 126;
        if ("scoreboard".equals(key)) {
            return scoreboardSettingsPanelHeight(GtbStructures.sbConfig.skin);
        }
        return 62;
    }

    private static int scoreboardSettingsPanelHeight(String skin) {
        return scoreboardStyleOptionsVisible(skin) ? 204 : 78;
    }

    private static boolean scoreboardStyleOptionsVisible(String skin) {
        return !"vanilla".equals(ScoreboardConfig.normalizeSkin(skin));
    }

    static boolean scoreboardStyleOptionsVisibleForTest(String skin) {
        return scoreboardStyleOptionsVisible(skin);
    }

    static int scoreboardSettingsPanelHeightForTest(String skin) {
        return scoreboardSettingsPanelHeight(skin);
    }

    static int environmentSettingsPanelHeightForTest() {
        return ENVIRONMENT_SETTINGS_PANEL_HEIGHT;
    }

    static boolean environmentChatNoticeRowsUseMultiSelectForTest() {
        return ENVIRONMENT_CHAT_NOTICE_MULTI_SELECT;
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

    private static void drawPanel(Rect panel) {
        Gui.drawRect(panel.x1, panel.y1, panel.x2, panel.y2, PANEL_BACKGROUND);
        drawOutline(panel, PANEL_BORDER);
    }

    private static void drawPanelCloseButton(Rect panel) {
        Rect close = panelCloseRect(panel);
        Gui.drawRect(close.x1, close.y1, close.x2, close.y2, PANEL_CLOSE_BG);
        INSTANCE.drawControlFeedback("close:" + close.x1 + ":" + close.y1, close);
        drawOutline(close, PANEL_CLOSE_BORDER);
        drawXIcon(close, 0xFFFF8D9A);
    }

    private static void drawLabelsPanelCloseButton(Rect panel) {
        drawPanelCloseButton(panel);
    }

    private static Rect panelCloseRect(Rect panel) {
        return new Rect(panel.x2 - PANEL_PAD - CLOSE_SIZE, panel.y1 + PANEL_PAD,
                panel.x2 - PANEL_PAD, panel.y1 + PANEL_PAD + CLOSE_SIZE);
    }

    private static void drawSection(FontRenderer fr, int x, int y, String text) {
        fr.drawStringWithShadow("\u00a77" + text, x, y, PANEL_MUTED);
    }

    private static int drawMenuSection(FontRenderer fr, int x, int y, String text) {
        y += SECTION_MARGIN_Y;
        drawSection(fr, x, y, text);
        return y + fr.FONT_HEIGHT + SECTION_MARGIN_Y + 1;
    }

    private static int sectionBlockHeight() {
        return SECTION_MARGIN_Y * 2 + SECTION_TEXT_HEIGHT + 1;
    }

    private static void drawToggleRow(FontRenderer fr, int x, int y, String text,
                                      boolean selected, boolean multi, boolean showBackground) {
        drawToggleRow(fr, x, y, text, selected, multi, showBackground, false);
    }

    private static void drawToggleRow(FontRenderer fr, int x, int y, String text,
                                      boolean selected, boolean multi,
                                      boolean showBackground, boolean backgroundSelected) {
        int checkY = toggleCheckY(y, fr.FONT_HEIGHT);
        int textY = toggleTextY(y, fr.FONT_HEIGHT);
        drawCheckBox(x, checkY, selected, multi);
        fr.drawStringWithShadow((selected ? "\u00a7f" : "\u00a77") + text,
                x + CHECK_SIZE + 5, textY, selected ? PANEL_TEXT : PANEL_MUTED);
        if (showBackground) {
            int bgX = x + BACKGROUND_COLUMN_OFFSET;
            drawCheckBox(bgX, checkY, backgroundSelected, false);
            fr.drawStringWithShadow((backgroundSelected ? "\u00a7f" : "\u00a77") + "Background",
                    bgX + CHECK_SIZE + 5, textY, backgroundSelected ? PANEL_TEXT : PANEL_MUTED);
        }
    }

    private static void drawTriStateRow(FontRenderer fr, int x, int y, String text,
                                        ScoreboardConfig.DisplayOption option) {
        option = ScoreboardConfig.normalizeOption(option, ScoreboardConfig.DisplayOption.OFF);
        int checkY = toggleCheckY(y, fr.FONT_HEIGHT);
        int textY = toggleTextY(y, fr.FONT_HEIGHT);
        drawTriStateBox(x, checkY, option);
        boolean active = option != ScoreboardConfig.DisplayOption.OFF;
        fr.drawStringWithShadow((active ? "\u00a7f" : "\u00a77") + text,
                x + CHECK_SIZE + 5, textY, active ? PANEL_TEXT : PANEL_MUTED);
        if (option == ScoreboardConfig.DisplayOption.EXPANDED
                && triStateButtonRect(x, y, fr.FONT_HEIGHT).contains(INSTANCE.drawMouseX, INSTANCE.drawMouseY)) {
            drawTooltip(fr, "\u00a7fOnly when expanded");
        }
    }

    private static void drawSliderRow(FontRenderer fr, int x, int y, String text, float value) {
        int textY = toggleTextY(y, fr.FONT_HEIGHT);
        fr.drawStringWithShadow("\u00a7f" + text, x, textY, PANEL_TEXT);
        Rect track = sliderTrackRect(x, y);
        int cy = track.y1 + track.height() / 2;
        int accent = selectionAccent();
        int knobCenter = track.x1 + Math.round(clamp01(value) * SLIDER_WIDTH);
        Gui.drawRect(track.x1, cy, track.x2, cy + 1, 0xAA7A8492);
        Gui.drawRect(track.x1, cy - 1, knobCenter, cy + 2, accent);
        Rect knob = new Rect(knobCenter - SLIDER_KNOB_WIDTH / 2, cy - 5,
                knobCenter - SLIDER_KNOB_WIDTH / 2 + SLIDER_KNOB_WIDTH, cy + 6);
        Gui.drawRect(knob.x1, knob.y1, knob.x2, knob.y2, 0xCC101217);
        INSTANCE.drawControlFeedback("slider:" + x + ":" + y, sliderRowRect(x, y));
        drawOutline(knob, accent);
    }

    private static int toggleTextY(int rowY, int fontHeight) {
        return rowY + (ROW_HEIGHT - fontHeight) / 2;
    }

    private static int toggleCheckY(int rowY, int fontHeight) {
        return toggleTextY(rowY, fontHeight) - 1;
    }

    private static void drawStepper(FontRenderer fr, int x, int y, int value, int min, int max) {
        Rect minus = stepperMinusRect(x, y);
        Rect plus = stepperPlusRect(x, y);
        drawSmallButton(minus, false);
        String valueText = String.valueOf(value);
        fr.drawStringWithShadow("\u00a7f" + valueText, x + STEPPER_VALUE_CENTER - fr.getStringWidth(valueText) / 2,
                y + (STEPPER_BUTTON_SIZE - fr.FONT_HEIGHT) / 2, PANEL_TEXT);
        drawSmallButton(plus, true);
    }

    private static Rect stepperMinusRect(int x, int y) {
        return new Rect(x, y, x + STEPPER_BUTTON_SIZE, y + STEPPER_BUTTON_SIZE);
    }

    private static Rect stepperPlusRect(int x, int y) {
        return new Rect(x + STEPPER_PLUS_X, y, x + STEPPER_PLUS_X + STEPPER_BUTTON_SIZE,
                y + STEPPER_BUTTON_SIZE);
    }

    private static void drawSmallButton(Rect rect, boolean plus) {
        Gui.drawRect(rect.x1, rect.y1, rect.x2, rect.y2, 0x663A414B);
        INSTANCE.drawControlFeedback("stepper:" + rect.x1 + ":" + rect.y1, rect);
        drawOutline(rect, PANEL_BORDER);
        int cx = rect.x1 + rect.width() / 2;
        int cy = rect.y1 + rect.height() / 2;
        Gui.drawRect(cx - 3, cy, cx + 4, cy + 1, PANEL_TEXT);
        if (plus) Gui.drawRect(cx, cy - 3, cx + 1, cy + 4, PANEL_TEXT);
    }

    private static void drawCheckBox(int x, int y, boolean selected, boolean multi) {
        Rect box = new Rect(x, y, x + CHECK_SIZE, y + CHECK_SIZE);
        Gui.drawRect(box.x1, box.y1, box.x2, box.y2, 0x33000000);
        INSTANCE.drawControlFeedback("check:" + x + ":" + y, box);
        drawOutline(box, 0xAA7A8492);
        if (!selected) return;
        if (multi) {
            int c = selectionAccent();
            Gui.drawRect(x + 2, y + 4, x + 3, y + 6, c);
            Gui.drawRect(x + 3, y + 5, x + 4, y + 7, c);
            Gui.drawRect(x + 4, y + 4, x + 5, y + 6, c);
            Gui.drawRect(x + 5, y + 3, x + 6, y + 5, c);
            Gui.drawRect(x + 6, y + 2, x + 7, y + 4, c);
        } else {
            Gui.drawRect(x + 3, y + 3, x + 7, y + 7, selectionAccent());
        }
    }

    private static void drawTriStateBox(int x, int y, ScoreboardConfig.DisplayOption option) {
        Rect box = new Rect(x, y, x + CHECK_SIZE, y + CHECK_SIZE);
        Gui.drawRect(box.x1, box.y1, box.x2, box.y2, 0x33000000);
        INSTANCE.drawControlFeedback("tri:" + x + ":" + y, box);
        drawOutline(box, 0xAA7A8492);
        int c = selectionAccent();
        if (option == ScoreboardConfig.DisplayOption.ON) {
            Gui.drawRect(x + 3, y + 3, x + 7, y + 7, c);
        } else if (option == ScoreboardConfig.DisplayOption.EXPANDED) {
            for (int dy = 0; dy < 4; dy++) {
                int width = 4 - dy;
                Gui.drawRect(x + 3, y + 3 + dy, x + 3 + width, y + 4 + dy, c);
            }
        }
    }

    private static void drawTooltip(FontRenderer fr, String text) {
        int x = INSTANCE.drawMouseX + 9;
        int width = fr.getStringWidth(text) + 6;
        int height = fr.FONT_HEIGHT + 6;
        int y = INSTANCE.drawMouseY - height - 7;
        if (y < 2) y = INSTANCE.drawMouseY + 9;
        Gui.drawRect(x, y, x + width, y + height, 0xF0101010);
        drawOutline(new Rect(x, y, x + width, y + height), 0xAA7A8492);
        fr.drawStringWithShadow(text, x + 3, y + 3, PANEL_TEXT);
    }

    private static void drawColorSwatches(int x, int y, String selected) {
        for (int i = 0; i < COLOR_CODES.length; i++) {
            Rect swatch = colorSwatchRect(x, y, i);
            int color = GtbStructures.minecraftColorArgb("\u00a7" + COLOR_CODES[i], 0xFF);
            Gui.drawRect(swatch.x1, swatch.y1, swatch.x2, swatch.y2, color);
            INSTANCE.drawControlFeedback("color:" + i + ":" + x + ":" + y, swatch);
            drawOutline(swatch, 0xAA111111);
            if (("\u00a7" + COLOR_CODES[i]).equals(selected)) {
                drawOutline(new Rect(swatch.x1 - 2, swatch.y1 - 2, swatch.x2 + 2, swatch.y2 + 2), 0xFFFFFFFF);
            }
        }
    }

    private void drawControlFeedback(String id, Rect rect) {
        boolean hovered = rect.contains(drawMouseX, drawMouseY);
        float current = controlHoverAmounts.containsKey(id) ? controlHoverAmounts.get(id) : 0F;
        float next = hovered ? Math.min(1F, current + 0.50F) : Math.max(0F, current - 0.42F);
        controlHoverAmounts.put(id, Float.valueOf(next));
        if (next > 0.01F) {
            int alpha = Math.min(0x35, Math.max(0, (int) (0x35 * next)));
            Gui.drawRect(rect.x1, rect.y1, rect.x2, rect.y2, (alpha << 24) | 0xFFFFFF);
        }
        if (hovered && drawLeftDown) {
            Gui.drawRect(rect.x1, rect.y1, rect.x2, rect.y2, 0x33000000);
        }
    }

    private static int selectionAccent() {
        return GtbStructures.minecraftColorArgb(GtbStructures.colorBrackets, 0xFF);
    }

    private static float clamp01(float value) {
        if (value < 0F) return 0F;
        if (value > 1F) return 1F;
        return value;
    }

    private static void beginForegroundDraw() {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
    }

    private static void endForegroundDraw() {
        GlStateManager.enableDepth();
        GlStateManager.disableLighting();
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
        String target = INSTANCE.hintColorTarget;
        if ("bracket".equals(target)) return GtbStructures.normalizeMinecraftColorCode(GtbStructures.colorBrackets, "\u00a76");
        if ("hyphen".equals(target)) return GtbStructures.normalizeMinecraftColorCode(GtbStructures.colorHyphens, "\u00a77");
        return GtbStructures.normalizeMinecraftColorCode(GtbStructures.colorNumbers, "\u00a7e");
    }

    private static String currentCooldownColor() {
        return "bar".equals(INSTANCE.cooldownColorTarget)
                ? GtbStructures.normalizeMinecraftColorCode(GtbStructures.chatCooldownBarColor, "\u00a7e")
                : GtbStructures.normalizeMinecraftColorCode(GtbStructures.chatCooldownTimerColor, "\u00a7f");
    }

    private void setHintColorTarget(String color) {
        if ("bracket".equals(hintColorTarget)) {
            GtbStructures.colorBrackets = color;
        } else if ("hyphen".equals(hintColorTarget)) {
            GtbStructures.colorHyphens = color;
        } else {
            GtbStructures.colorNumbers = color;
        }
    }

    private void setCooldownColorTarget(String color) {
        if ("bar".equals(cooldownColorTarget)) {
            GtbStructures.chatCooldownBarColor = color;
        } else {
            GtbStructures.chatCooldownTimerColor = color;
        }
    }

    private static boolean toggleHit(FontRenderer fr, int x, int y, String text, int mouseX, int mouseY) {
        return toggleRect(fr, x, y, text).contains(mouseX, mouseY);
    }

    private static boolean triStateHit(FontRenderer fr, int x, int y, String text, int mouseX, int mouseY) {
        return toggleRect(fr, x, y, text).contains(mouseX, mouseY);
    }

    static boolean toggleHitForTest(int x, int y, int textWidth, int mouseX, int mouseY) {
        return toggleRect(x, y, textWidth).contains(mouseX, mouseY);
    }

    private static Rect toggleRect(FontRenderer fr, int x, int y, String text) {
        int width = fr == null ? 0 : fr.getStringWidth(text == null ? "" : text);
        return toggleRect(x, y, width);
    }

    private static Rect toggleRect(int x, int y, int textWidth) {
        int right = x + CHECK_SIZE + 5 + Math.max(0, textWidth);
        return new Rect(x, y, right, y + ROW_HEIGHT);
    }

    private static Rect triStateButtonRect(int x, int y, int fontHeight) {
        int checkY = toggleCheckY(y, fontHeight);
        return new Rect(x, checkY, x + CHECK_SIZE, checkY + CHECK_SIZE);
    }

    private static Rect sliderTrackRect(int x, int y) {
        int sx = x + 112;
        int sy = y + (SLIDER_ROW_HEIGHT - 10) / 2 - 2;
        return new Rect(sx, sy, sx + SLIDER_WIDTH, sy + 10);
    }

    private static Rect sliderRowRect(int x, int y) {
        Rect track = sliderTrackRect(x, y);
        return new Rect(track.x1 - 3, y + 1, track.x2 + 3, y + SLIDER_ROW_HEIGHT - 1);
    }

    private static float sliderValueAt(int x, int mouseX) {
        Rect track = sliderTrackRect(x, 0);
        return clamp01((mouseX - track.x1) / (float) SLIDER_WIDTH);
    }

    static boolean sliderHitForTest(int x, int y, int mouseX, int mouseY) {
        return sliderRowRect(x, y).contains(mouseX, mouseY);
    }

    static int sliderTrackCenterYForTest(int x, int y) {
        Rect track = sliderTrackRect(x, y);
        return track.y1 + track.height() / 2;
    }

    static float sliderValueForTest(int x, int mouseX) {
        return sliderValueAt(x, mouseX);
    }

    static ScoreboardConfig.DisplayOption cycleDisplayOptionForTest(ScoreboardConfig.DisplayOption option) {
        return ScoreboardConfig.cycleOption(option);
    }

    private static Rect rowRect(Rect panel, int y) {
        return new Rect(panel.x1 + PANEL_PAD, y, panel.x2 - PANEL_PAD, y + ROW_HEIGHT);
    }

    private static Rect stepperRowRect(Rect panel, int y) {
        return new Rect(panel.x1 + PANEL_PAD, y, panel.x2 - PANEL_PAD, y + STEPPER_BUTTON_SIZE);
    }

    private static void drawOutline(Rect rect, int color) {
        Gui.drawRect(rect.x1, rect.y1, rect.x1 + 1, rect.y2, color);
        Gui.drawRect(rect.x2 - 1, rect.y1, rect.x2, rect.y2, color);
        if (rect.width() > 2) {
            Gui.drawRect(rect.x1 + 1, rect.y1, rect.x2 - 1, rect.y1 + 1, color);
            Gui.drawRect(rect.x1 + 1, rect.y2 - 1, rect.x2 - 1, rect.y2, color);
        }
    }

    private static void drawCenteredText(FontRenderer fr, String text, Rect rect, int color) {
        int x = rect.x1 + (rect.width() - fr.getStringWidth(text)) / 2;
        int y = rect.y1 + (rect.height() - fr.FONT_HEIGHT) / 2;
        fr.drawString(text, x, y, color);
    }

    private static void drawXIcon(Rect rect, int color) {
        int cx = rect.x1 + rect.width() / 2;
        int cy = rect.y1 + rect.height() / 2;
        Gui.drawRect(cx - 2, cy - 2, cx - 1, cy - 1, color);
        Gui.drawRect(cx - 1, cy - 1, cx, cy, color);
        Gui.drawRect(cx, cy, cx + 1, cy + 1, color);
        Gui.drawRect(cx + 1, cy + 1, cx + 2, cy + 2, color);
        Gui.drawRect(cx + 2, cy + 2, cx + 3, cy + 3, color);
        Gui.drawRect(cx + 2, cy - 2, cx + 3, cy - 1, color);
        Gui.drawRect(cx + 1, cy - 1, cx + 2, cy, color);
        Gui.drawRect(cx - 1, cy + 1, cx, cy + 2, color);
        Gui.drawRect(cx - 2, cy + 2, cx - 1, cy + 3, color);
    }

    private void drawBackground(Label label) {
        Float amount = hoverAmounts.get(label.id);
        if (amount != null && amount.floatValue() > 0.01F) {
            int alpha = Math.min(0x48, Math.max(0, (int) (0x48 * amount.floatValue())));
            drawPadded(label, (alpha << 24) | 0x808080);
        }

        if (label.id.equals(selectedId)) {
            Minecraft mc = Minecraft.getMinecraft();
            int mouseX = 0;
            int mouseY = 0;
            if (mc != null) {
                ScaledResolution sr = new ScaledResolution(mc);
                mouseX = mouseX(mc, sr);
                mouseY = mouseY(mc, sr);
            }
            boolean pressedInside = Mouse.isButtonDown(0) && label.contains(mouseX, mouseY);
            int alpha = pressedInside ? 0x78 : 0x56;
            int rgb = GtbStructures.minecraftColorArgb(GtbStructures.colorNumbers, 0xFF) & 0x00FFFFFF;
            drawPadded(label, (alpha << 24) | rgb);
            if ("handhelditem".equals(label.id)) drawHandheldResizeHandles(label, 0xFF000000 | rgb);
        }
    }

    private static void drawHandheldResizeHandles(Label label, int color) {
        Gui.drawRect(label.x1, label.y1, label.x1 + HANDHELD_RESIZE_GRIP,
                label.y1 + HANDHELD_RESIZE_GRIP, color);
        Gui.drawRect(label.x2 - HANDHELD_RESIZE_GRIP, label.y1, label.x2,
                label.y1 + HANDHELD_RESIZE_GRIP, color);
        Gui.drawRect(label.x1, label.y2 - HANDHELD_RESIZE_GRIP,
                label.x1 + HANDHELD_RESIZE_GRIP, label.y2, color);
        Gui.drawRect(label.x2 - HANDHELD_RESIZE_GRIP, label.y2 - HANDHELD_RESIZE_GRIP,
                label.x2, label.y2, color);
    }

    private static void drawPadded(Label label, int color) {
        Gui.drawRect(label.x1 - LABEL_BG_PAD_X, label.y1 - LABEL_BG_PAD_Y,
                label.x2 + LABEL_BG_PAD_X, label.y2 + LABEL_BG_PAD_Y, color);
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

    private void pinPanel(Label label, ScaledResolution sr) {
        if (label == null) return;
        Rect natural = naturalSettingsPanelRect(label, sr, settingsPanelWidth(label), settingsPanelHeight(label));
        pinnedPanelFamily = panelFamily(label);
        pinnedPanelX = natural.x1;
        pinnedPanelY = natural.y1;
        rememberPinnedPanelAnchor(label, natural);
    }

    private void armPanelFollow(Label selected, ScaledResolution sr) {
        dragPanelFollows = selected != null
                && !pinnedPanelFamily.isEmpty()
                && pinnedPanelFamily.equals(panelFamily(selected));
        if (!dragPanelFollows) return;
        Label panelLabel = activePanelLabel();
        if (panelLabel == null) {
            dragPanelFollows = false;
            return;
        }
        Rect panel = settingsPanelRect(panelLabel, sr);
        dragStartPanelX = panel.x1;
        dragStartPanelY = panel.y1;
    }

    private void movePinnedPanelWithDrag(Label selected, ScaledResolution sr) {
        if (!dragPanelFollows || selected == null) return;
        Label panelLabel = activePanelLabel();
        if (panelLabel == null || !panelFamily(selected).equals(panelFamily(panelLabel))) {
            dragPanelFollows = false;
            return;
        }

        int currentLabelX = dragStartLabelX;
        int currentLabelY = dragStartLabelY;
        if (selected.target.moveX) {
            currentLabelX = HudPosition.left(selected.target.x(),
                    sr.getScaledWidth(), selected.width(), MARGIN);
        }
        if (selected.target.moveMode == MoveMode.SCOREBOARD_Y) {
            currentLabelY += dragStartScoreboardOffset - GtbStructures.sbConfig.heightOffset;
        } else if (selected.target.moveY) {
            if (selected.target.moveMode == MoveMode.BOTTOM_LEFT) {
                currentLabelY = HudPosition.bottom(selected.target.y(),
                        sr.getScaledHeight(), selected.height(), MARGIN) - selected.height();
            } else {
                currentLabelY = HudPosition.top(selected.target.y(),
                        sr.getScaledHeight(), selected.height(), MARGIN);
            }
        }

        int width = settingsPanelWidth(panelLabel);
        int height = settingsPanelHeight(panelLabel);
        Rect clamped = clampPanelRect(
                dragStartPanelX + currentLabelX - dragStartLabelX,
                dragStartPanelY + currentLabelY - dragStartLabelY,
                width, height, sr);
        Rect currentLabel = new Rect(currentLabelX, currentLabelY,
                currentLabelX + selected.width(), currentLabelY + selected.height());
        int anchorSide = pinnedPanelAnchorValid ? pinnedPanelAnchorSide
                : panelAnchorSide(currentLabel.x1, currentLabel.x2, clamped.x1, clamped.x2);
        PanelDragPlacement placement = avoidEdgePinnedPanelOverlap(
                clamped, currentLabel, sr.getScaledWidth(), anchorSide);
        pinnedPanelX = placement.panelX;
        pinnedPanelY = placement.panelY;
        pinnedPanelAnchorSide = placement.anchorSide;
        pinnedPanelAnchorX = placement.anchorX;
        pinnedPanelAnchorValid = true;
        if (placement.swapped) {
            dragStartPanelX = placement.panelX;
            dragStartPanelY = placement.panelY;
            dragStartLabelX = currentLabelX;
            dragStartLabelY = currentLabelY;
        }
    }

    static PanelDragPlacement panelDragPlacementForTest(
            int panelX, int panelY, int panelWidth, int panelHeight,
            int anchorSide, int labelX, int labelY, int labelWidth, int labelHeight,
            int screenWidth) {
        return avoidEdgePinnedPanelOverlap(
                new Rect(panelX, panelY, panelX + panelWidth, panelY + panelHeight),
                new Rect(labelX, labelY, labelX + labelWidth, labelY + labelHeight),
                screenWidth, anchorSide);
    }

    private static PanelDragPlacement avoidEdgePinnedPanelOverlap(
            Rect panel, Rect label, int screenWidth, int anchorSide) {
        int resolvedSide = anchorSide == 0
                ? panelAnchorSide(label.x1, label.x2, panel.x1, panel.x2)
                : anchorSide;
        if (!verticalRangesOverlap(panel, label)
                || !horizontalRangesTouchOrOverlap(panel, label)) {
            return new PanelDragPlacement(panel.x1, panel.y1, resolvedSide,
                    labelAnchorX(label.x1, label.x2, resolvedSide), false);
        }

        int rightEdge = screenWidth - MARGIN;
        if (resolvedSide > 0 && panel.x2 >= rightEdge) {
            int swappedX = label.x1 - panel.width() - SETTINGS_PANEL_GAP;
            if (swappedX >= MARGIN) {
                return new PanelDragPlacement(swappedX, panel.y1, -1, label.x1, true);
            }
        } else if (resolvedSide < 0 && panel.x1 <= MARGIN) {
            int swappedX = label.x2 + SETTINGS_PANEL_GAP;
            if (swappedX + panel.width() <= rightEdge) {
                return new PanelDragPlacement(swappedX, panel.y1, 1, label.x2, true);
            }
        }

        return new PanelDragPlacement(panel.x1, panel.y1, resolvedSide,
                labelAnchorX(label.x1, label.x2, resolvedSide), false);
    }

    private static boolean verticalRangesOverlap(Rect first, Rect second) {
        return first.y1 < second.y2 && first.y2 > second.y1;
    }

    private static boolean horizontalRangesTouchOrOverlap(Rect first, Rect second) {
        return first.x1 <= second.x2 && first.x2 >= second.x1;
    }

    private int pinnedPanelXFollowingLabel(Label label, int panelWidth, int screenWidth) {
        if (!pinnedPanelAnchorValid) return pinnedPanelX;
        return followPanelCoordinate(pinnedPanelX, pinnedPanelAnchorX,
                labelAnchorX(label, pinnedPanelAnchorSide), MARGIN,
                Math.max(MARGIN, screenWidth - panelWidth - MARGIN));
    }

    private void rememberPinnedPanelAnchor(Label label, Rect panel) {
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

    private static int panelAnchorSide(int labelX1, int labelX2, int panelX1, int panelX2) {
        int leftGap = Math.abs(panelX2 - labelX1);
        int rightGap = Math.abs(panelX1 - labelX2);
        return rightGap < leftGap ? 1 : -1;
    }

    private static int labelAnchorX(Label label, int side) {
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

    static boolean cooldownLabelChecked(boolean counter, boolean timer, boolean bar) {
        return counter && (timer || bar);
    }

    static CooldownState toggleCooldownLabelState(boolean counter, boolean timer, boolean bar) {
        if (cooldownLabelChecked(counter, timer, bar)) {
            return new CooldownState(false, timer, bar);
        }
        if (!timer && !bar) {
            return new CooldownState(true, true, true);
        }
        return new CooldownState(true, timer, bar);
    }

    static CooldownState toggleCooldownPartState(boolean counter, boolean timer, boolean bar,
                                                 boolean timerPart) {
        boolean targetEnabled = timerPart ? timer : bar;
        boolean otherEnabled = timerPart ? bar : timer;
        if (targetEnabled && !otherEnabled) {
            // Disabling the final visible part disables the counter but retains
            // that one-part layout for the next enable from the Labels menu.
            return new CooldownState(false, timer, bar);
        }
        return timerPart
                ? new CooldownState(counter, !timer, bar)
                : new CooldownState(counter, timer, !bar);
    }

    private static void applyCooldownState(CooldownState state) {
        GtbStructures.chatCooldownCounter = state.counter;
        GtbStructures.chatCooldownTimerEnabled = state.timer;
        GtbStructures.chatCooldownBarEnabled = state.bar;
    }

    private static String panelFamily(Label label) {
        if (label == null || label.target == null) return "";
        return label.target.key.startsWith("environment") ? "environment" : label.target.key;
    }

    private static int mouseX(Minecraft mc, ScaledResolution sr) {
        int displayWidth = Math.max(1, mc.displayWidth);
        return Mouse.getX() * sr.getScaledWidth() / displayWidth;
    }

    private static int mouseY(Minecraft mc, ScaledResolution sr) {
        int displayHeight = Math.max(1, mc.displayHeight);
        return sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / displayHeight - 1;
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

    public static final class Target {
        private final String key;
        private final MoveMode moveMode;
        private final boolean moveX;
        private final boolean moveY;

        private Target(String key, MoveMode moveMode, boolean moveX, boolean moveY) {
            this.key = key == null ? "" : key.toLowerCase(Locale.ROOT);
            this.moveMode = moveMode;
            this.moveX = moveX;
            this.moveY = moveY;
        }

        private boolean canDelete() {
            return !"actionbar".equals(key);
        }

        private void movePixels(int dx, int dy, int width, int height, int screenWidth, int screenHeight) {
            if (!moveX) dx = 0;
            if (!moveY) dy = 0;
            if (dx == 0 && dy == 0) return;

            if (moveMode == MoveMode.SCOREBOARD_Y) {
                if (dy != 0) GtbStructures.sbConfig.heightOffset = clampScoreboardOffset(GtbStructures.sbConfig.heightOffset - dy);
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
                if (dy != 0) GtbStructures.sbConfig.heightOffset = clampScoreboardOffset(startScoreboardOffset - dy);
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
            if ("actionbar".equals(key)) {
                InterfacePreview.setMockActionbarVisible(false);
            } else if ("hint".equals(key)) {
                GtbStructures.enabled = false;
            } else if ("cooldown".equals(key)) {
                GtbStructures.chatCooldownCounter = false;
            } else if ("handheld".equals(key)) {
                GtbStructures.handheldItemOverlay = false;
            } else if ("environment_weather".equals(key)) {
                GtbStructures.environmentWeatherEnabled = false;
            } else if ("environment_biome".equals(key)) {
                GtbStructures.environmentBiomeEnabled = false;
            } else if ("environment_time".equals(key)) {
                GtbStructures.environmentTimeEnabled = false;
            } else if ("environment".equals(key)) {
                GtbStructures.environmentOverlay = false;
            } else if ("scoreboard".equals(key)) {
                GtbStructures.sbConfig.setSkin("vanilla");
            }
        }

        private float x() {
            if ("hint".equals(key)) return GtbStructures.posX;
            if ("cooldown".equals(key)) return GtbStructures.chatCooldownPosX;
            if ("handheld".equals(key)) return GtbStructures.handheldItemPosX;
            return GtbStructures.environmentPosX;
        }

        private float y() {
            if ("hint".equals(key)) return GtbStructures.posY;
            if ("cooldown".equals(key)) return GtbStructures.chatCooldownPosY;
            if ("handheld".equals(key)) return GtbStructures.handheldItemPosY;
            return GtbStructures.environmentPosY;
        }

        private void setX(float value) {
            value = HudPosition.clamp01(value);
            if ("hint".equals(key)) {
                GtbStructures.posX = value;
            } else if ("cooldown".equals(key)) {
                GtbStructures.chatCooldownPosX = value;
            } else if ("handheld".equals(key)) {
                GtbStructures.handheldItemPosX = value;
            } else {
                GtbStructures.environmentPosX = value;
            }
        }

        private void setY(float value) {
            value = HudPosition.clamp01(value);
            if ("hint".equals(key)) {
                GtbStructures.posY = value;
            } else if ("cooldown".equals(key)) {
                GtbStructures.chatCooldownPosY = value;
            } else if ("handheld".equals(key)) {
                GtbStructures.handheldItemPosY = value;
            } else {
                GtbStructures.environmentPosY = value;
            }
        }
    }

    private enum MoveMode {
        FIXED,
        TOP_LEFT,
        BOTTOM_LEFT,
        SCOREBOARD_Y
    }

    static final class CooldownState {
        final boolean counter;
        final boolean timer;
        final boolean bar;

        CooldownState(boolean counter, boolean timer, boolean bar) {
            this.counter = counter;
            this.timer = timer;
            this.bar = bar;
        }
    }

    static final class PanelDragPlacement {
        final int panelX;
        final int panelY;
        final int anchorSide;
        final int anchorX;
        final boolean swapped;

        PanelDragPlacement(int panelX, int panelY, int anchorSide, int anchorX,
                           boolean swapped) {
            this.panelX = panelX;
            this.panelY = panelY;
            this.anchorSide = anchorSide;
            this.anchorX = anchorX;
            this.swapped = swapped;
        }
    }

    private static final class ConfigSnapshot {
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
        private final ScoreboardConfig.DisplayOption scoreboardShowPointsGainedInRound;
        private final ScoreboardConfig.DisplayOption scoreboardShowPlaces;
        private final ScoreboardConfig.DisplayOption scoreboardShowTitles;
        private final ScoreboardConfig.DisplayOption scoreboardShowEmblems;
        private final boolean scoreboardTextShadow;
        private final boolean scoreboardLeftPlayersAtBottom;
        private final boolean trackingEnabled;
        private final boolean autoRequeue;
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

        private ConfigSnapshot() {
            modEnabled = GtbStructures.modEnabled;
            enabled = GtbStructures.enabled;
            hintBackgroundOverlay = GtbStructures.hintBackgroundOverlay;
            hintStructureBackground = GtbStructures.hintStructureBackground;
            handheldItemOverlay = GtbStructures.handheldItemOverlay;
            handheldItemBackground = GtbStructures.handheldItemBackground;
            handheldItemCaptionName = GtbStructures.handheldItemCaptionName;
            handheldItemCaptionId = GtbStructures.handheldItemCaptionId;
            handheldItemCaptionMetadata = GtbStructures.handheldItemCaptionMetadata;
            handheldItemCaptionSpawnEggMob = GtbStructures.handheldItemCaptionSpawnEggMob;
            handheldItemVerticalLayout = GtbStructures.handheldItemVerticalLayout;
            handheldItemVerticalNewestAtBottom = GtbStructures.handheldItemVerticalNewestAtBottom;
            handheldItemHorizontalNewestAtRight = GtbStructures.handheldItemHorizontalNewestAtRight;
            handheldItemHorizontalNewestAtBottom = GtbStructures.handheldItemHorizontalNewestAtBottom;
            environmentOverlay = GtbStructures.environmentOverlay;
            environmentBackground = GtbStructures.environmentBackground;
            environmentWeatherEnabled = GtbStructures.environmentWeatherEnabled;
            environmentBiomeEnabled = GtbStructures.environmentBiomeEnabled;
            environmentTimeEnabled = GtbStructures.environmentTimeEnabled;
            environmentWeatherChatNotice = GtbStructures.environmentWeatherChatNotice;
            environmentBiomeChatNotice = GtbStructures.environmentBiomeChatNotice;
            environmentTimeChatNotice = GtbStructures.environmentTimeChatNotice;
            chatCooldownCounter = GtbStructures.chatCooldownCounter;
            chatCooldownTimerEnabled = GtbStructures.chatCooldownTimerEnabled;
            chatCooldownBarEnabled = GtbStructures.chatCooldownBarEnabled;
            chatCooldownBackground = GtbStructures.chatCooldownBackground;
            chatCooldownBarBackground = GtbStructures.chatCooldownBarBackground;
            scoreboardEnabled = GtbStructures.sbConfig.isCustomEnabled();
            scoreboardBackgroundOpacity = GtbStructures.sbConfig.backgroundOpacity;
            scoreboardHighlightIntensity = GtbStructures.sbConfig.highlightIntensity;
            scoreboardShowPointsGainedInRound = GtbStructures.sbConfig.showPointsGainedInRound;
            scoreboardShowPlaces = GtbStructures.sbConfig.showPlaces;
            scoreboardShowTitles = GtbStructures.sbConfig.showTitles;
            scoreboardShowEmblems = GtbStructures.sbConfig.showEmblems;
            scoreboardTextShadow = GtbStructures.sbConfig.textShadow;
            scoreboardLeftPlayersAtBottom = GtbStructures.sbConfig.leftPlayersAtBottom;
            trackingEnabled = GtbStructures.trackingEnabled;
            autoRequeue = GtbStructures.autoRequeue;
            guessPeek = GtbStructures.guessPeek;
            guessPeekShowLangCodes = GtbStructures.guessPeekShowLangCodes;
            guessPeekDisplay = GtbStructures.normalizeGuessPeekDisplay(GtbStructures.guessPeekDisplay);
            scrambleGuess = GtbStructures.scrambleGuess;
            scrambleGuessFancy = GtbStructures.scrambleGuessFancy;
            trollMode = GtbStructures.trollMode;
            trollModeAutoDisable = GtbStructures.trollModeAutoDisable;
            chatHeaderRaw = ChatHeader.raw();
            showAllMatching = GtbStructures.showAllMatching;
            showAllMatchingShowEnglish = GtbStructures.showAllMatchingShowEnglish;
            themePointReveal = GtbStructures.themePointReveal;
            themePointRevealShowAfterGuess = GtbStructures.themePointRevealShowAfterGuess;
            shortcutReminder = GtbStructures.shortcutReminder;
            shortcutReminderShowNonAscii = GtbStructures.shortcutReminderShowNonAscii;
            shortcutReminderShowExtendedLatin = GtbStructures.shortcutReminderShowExtendedLatin;
            shortcutReminderShowCyrillic = GtbStructures.shortcutReminderShowCyrillic;
            shortcutReminderShowHanzi = GtbStructures.shortcutReminderShowHanzi;
            shortcutReminderShowJapanese = GtbStructures.shortcutReminderShowJapanese;
            shortcutReminderShowHangul = GtbStructures.shortcutReminderShowHangul;
            shortcutReminderShowAllShortcuts = GtbStructures.shortcutReminderShowAllShortcuts;
            shortcutReminderShowMultiwords = GtbStructures.shortcutReminderShowMultiwords;
            shortcutReminderShowAllMultiwords = GtbStructures.shortcutReminderShowAllMultiwords;
            copyTranslationPrefix = GtbStructures.copyTranslationPrefix;
            gtwShowEnglish = GtbStructures.gtwShowEnglish;
            posX = GtbStructures.posX;
            posY = GtbStructures.posY;
            handheldItemPosX = GtbStructures.handheldItemPosX;
            handheldItemPosY = GtbStructures.handheldItemPosY;
            environmentPosX = GtbStructures.environmentPosX;
            environmentPosY = GtbStructures.environmentPosY;
            chatCooldownPosX = GtbStructures.chatCooldownPosX;
            chatCooldownPosY = GtbStructures.chatCooldownPosY;
            handheldItemMaxVisible = GtbStructures.handheldItemMaxVisible;
            handheldItemPanelWidthRatio = GtbStructures.handheldItemPanelWidthRatio;
            handheldItemPanelHeightRatio = GtbStructures.handheldItemPanelHeightRatio;
            scoreboardHeightOffset = GtbStructures.sbConfig.heightOffset;
            spawnEggSkin = GtbStructures.spawnEggSkin;
            environmentTimeIconPreset = GtbStructures.environmentTimeIconPreset;
            colorNumbers = GtbStructures.colorNumbers;
            colorHyphens = GtbStructures.colorHyphens;
            colorBrackets = GtbStructures.colorBrackets;
            chatCooldownBarColor = GtbStructures.chatCooldownBarColor;
            chatCooldownTimerColor = GtbStructures.chatCooldownTimerColor;
            scoreboardSkin = GtbStructures.sbConfig.skin;
        }

        private static ConfigSnapshot capture() {
            return new ConfigSnapshot();
        }

        private void apply() {
            GtbStructures.modEnabled = modEnabled;
            GtbStructures.enabled = enabled;
            GtbStructures.hintBackgroundOverlay = hintBackgroundOverlay;
            GtbStructures.hintStructureBackground = hintStructureBackground;
            GtbStructures.handheldItemOverlay = handheldItemOverlay;
            GtbStructures.handheldItemBackground = handheldItemBackground;
            GtbStructures.handheldItemCaptionName = handheldItemCaptionName;
            GtbStructures.handheldItemCaptionId = handheldItemCaptionId;
            GtbStructures.handheldItemCaptionMetadata = handheldItemCaptionMetadata;
            GtbStructures.handheldItemCaptionSpawnEggMob = handheldItemCaptionSpawnEggMob;
            GtbStructures.handheldItemVerticalLayout = handheldItemVerticalLayout;
            GtbStructures.handheldItemVerticalNewestAtBottom = handheldItemVerticalNewestAtBottom;
            GtbStructures.handheldItemHorizontalNewestAtRight = handheldItemHorizontalNewestAtRight;
            GtbStructures.handheldItemHorizontalNewestAtBottom = handheldItemHorizontalNewestAtBottom;
            GtbStructures.environmentOverlay = environmentOverlay;
            GtbStructures.environmentBackground = environmentBackground;
            GtbStructures.environmentWeatherEnabled = environmentWeatherEnabled;
            GtbStructures.environmentBiomeEnabled = environmentBiomeEnabled;
            GtbStructures.environmentTimeEnabled = environmentTimeEnabled;
            GtbStructures.environmentWeatherChatNotice = environmentWeatherChatNotice;
            GtbStructures.environmentBiomeChatNotice = environmentBiomeChatNotice;
            GtbStructures.environmentTimeChatNotice = environmentTimeChatNotice;
            GtbStructures.chatCooldownCounter = chatCooldownCounter;
            GtbStructures.chatCooldownTimerEnabled = chatCooldownTimerEnabled;
            GtbStructures.chatCooldownBarEnabled = chatCooldownBarEnabled;
            GtbStructures.chatCooldownBackground = chatCooldownBackground;
            GtbStructures.chatCooldownBarBackground = chatCooldownBarBackground;
            if (scoreboardEnabled && !"vanilla".equals(ScoreboardConfig.normalizeSkin(scoreboardSkin))) {
                GtbStructures.sbConfig.setSkin(scoreboardSkin);
            } else {
                GtbStructures.sbConfig.setSkin("vanilla");
            }
            GtbStructures.sbConfig.backgroundOpacity = scoreboardBackgroundOpacity;
            GtbStructures.sbConfig.highlightIntensity = scoreboardHighlightIntensity;
            GtbStructures.sbConfig.showPointsGainedInRound = scoreboardShowPointsGainedInRound;
            GtbStructures.sbConfig.showPlaces = scoreboardShowPlaces;
            GtbStructures.sbConfig.showTitles = scoreboardShowTitles;
            GtbStructures.sbConfig.showEmblems = scoreboardShowEmblems;
            GtbStructures.sbConfig.textShadow = scoreboardTextShadow;
            GtbStructures.sbConfig.leftPlayersAtBottom = scoreboardLeftPlayersAtBottom;
            GtbStructures.sbConfig.clampVisuals();
            GtbStructures.trackingEnabled = trackingEnabled;
            GtbStructures.autoRequeue = autoRequeue;
            GtbStructures.guessPeek = guessPeek;
            GtbStructures.guessPeekShowLangCodes = guessPeekShowLangCodes;
            GtbStructures.guessPeekDisplay = guessPeekDisplay;
            GtbStructures.scrambleGuess = scrambleGuess;
            GtbStructures.scrambleGuessFancy = scrambleGuessFancy;
            GtbStructures.trollMode = trollMode;
            GtbStructures.trollModeAutoDisable = trollModeAutoDisable;
            ChatHeader.set(chatHeaderRaw);
            CommandGtb.refreshHeader();
            GtbStructures.showAllMatching = showAllMatching;
            GtbStructures.showAllMatchingShowEnglish = showAllMatchingShowEnglish;
            GtbStructures.themePointReveal = themePointReveal;
            GtbStructures.themePointRevealShowAfterGuess = themePointRevealShowAfterGuess;
            GtbStructures.shortcutReminder = shortcutReminder;
            GtbStructures.shortcutReminderShowNonAscii = shortcutReminderShowNonAscii;
            GtbStructures.shortcutReminderShowExtendedLatin = shortcutReminderShowExtendedLatin;
            GtbStructures.shortcutReminderShowCyrillic = shortcutReminderShowCyrillic;
            GtbStructures.shortcutReminderShowHanzi = shortcutReminderShowHanzi;
            GtbStructures.shortcutReminderShowJapanese = shortcutReminderShowJapanese;
            GtbStructures.shortcutReminderShowHangul = shortcutReminderShowHangul;
            GtbStructures.shortcutReminderShowAllShortcuts = shortcutReminderShowAllShortcuts;
            GtbStructures.shortcutReminderShowMultiwords = shortcutReminderShowMultiwords;
            GtbStructures.shortcutReminderShowAllMultiwords = shortcutReminderShowAllMultiwords;
            GtbStructures.copyTranslationPrefix = copyTranslationPrefix;
            GtbStructures.gtwShowEnglish = gtwShowEnglish;
            GameStats.INSTANCE.setTrackingEnabled(trackingEnabled);
            GameStats.INSTANCE.setAutoRequeue(autoRequeue);
            GtbStructures.posX = posX;
            GtbStructures.posY = posY;
            GtbStructures.handheldItemPosX = handheldItemPosX;
            GtbStructures.handheldItemPosY = handheldItemPosY;
            GtbStructures.environmentPosX = environmentPosX;
            GtbStructures.environmentPosY = environmentPosY;
            GtbStructures.chatCooldownPosX = chatCooldownPosX;
            GtbStructures.chatCooldownPosY = chatCooldownPosY;
            GtbStructures.handheldItemMaxVisible = handheldItemMaxVisible;
            GtbStructures.handheldItemPanelWidthRatio = handheldItemPanelWidthRatio;
            GtbStructures.handheldItemPanelHeightRatio = handheldItemPanelHeightRatio;
            GtbStructures.sbConfig.heightOffset = scoreboardHeightOffset;
            GtbStructures.spawnEggSkin = spawnEggSkin;
            GtbStructures.environmentTimeIconPreset = environmentTimeIconPreset;
            GtbStructures.colorNumbers = colorNumbers;
            GtbStructures.colorHyphens = colorHyphens;
            GtbStructures.colorBrackets = colorBrackets;
            GtbStructures.chatCooldownBarColor = chatCooldownBarColor;
            GtbStructures.chatCooldownTimerColor = chatCooldownTimerColor;
        }

        private boolean matchesCurrent() {
            return modEnabled == GtbStructures.modEnabled
                    && enabled == GtbStructures.enabled
                    && hintBackgroundOverlay == GtbStructures.hintBackgroundOverlay
                    && hintStructureBackground == GtbStructures.hintStructureBackground
                    && handheldItemOverlay == GtbStructures.handheldItemOverlay
                    && handheldItemBackground == GtbStructures.handheldItemBackground
                    && handheldItemCaptionName == GtbStructures.handheldItemCaptionName
                    && handheldItemCaptionId == GtbStructures.handheldItemCaptionId
                    && handheldItemCaptionMetadata == GtbStructures.handheldItemCaptionMetadata
                    && handheldItemCaptionSpawnEggMob == GtbStructures.handheldItemCaptionSpawnEggMob
                    && handheldItemVerticalLayout == GtbStructures.handheldItemVerticalLayout
                    && handheldItemVerticalNewestAtBottom == GtbStructures.handheldItemVerticalNewestAtBottom
                    && handheldItemHorizontalNewestAtRight == GtbStructures.handheldItemHorizontalNewestAtRight
                    && handheldItemHorizontalNewestAtBottom == GtbStructures.handheldItemHorizontalNewestAtBottom
                    && environmentOverlay == GtbStructures.environmentOverlay
                    && environmentBackground == GtbStructures.environmentBackground
                    && environmentWeatherEnabled == GtbStructures.environmentWeatherEnabled
                    && environmentBiomeEnabled == GtbStructures.environmentBiomeEnabled
                    && environmentTimeEnabled == GtbStructures.environmentTimeEnabled
                    && environmentWeatherChatNotice == GtbStructures.environmentWeatherChatNotice
                    && environmentBiomeChatNotice == GtbStructures.environmentBiomeChatNotice
                    && environmentTimeChatNotice == GtbStructures.environmentTimeChatNotice
                    && chatCooldownCounter == GtbStructures.chatCooldownCounter
                    && chatCooldownTimerEnabled == GtbStructures.chatCooldownTimerEnabled
                    && chatCooldownBarEnabled == GtbStructures.chatCooldownBarEnabled
                    && chatCooldownBackground == GtbStructures.chatCooldownBackground
                    && chatCooldownBarBackground == GtbStructures.chatCooldownBarBackground
                    && scoreboardEnabled == GtbStructures.sbConfig.isCustomEnabled()
                    && scoreboardBackgroundOpacity == GtbStructures.sbConfig.backgroundOpacity
                    && scoreboardHighlightIntensity == GtbStructures.sbConfig.highlightIntensity
                    && scoreboardShowPointsGainedInRound == GtbStructures.sbConfig.showPointsGainedInRound
                    && scoreboardShowPlaces == GtbStructures.sbConfig.showPlaces
                    && scoreboardShowTitles == GtbStructures.sbConfig.showTitles
                    && scoreboardShowEmblems == GtbStructures.sbConfig.showEmblems
                    && scoreboardTextShadow == GtbStructures.sbConfig.textShadow
                    && scoreboardLeftPlayersAtBottom == GtbStructures.sbConfig.leftPlayersAtBottom
                    && trackingEnabled == GtbStructures.trackingEnabled
                    && autoRequeue == GtbStructures.autoRequeue
                    && guessPeek == GtbStructures.guessPeek
                    && guessPeekShowLangCodes == GtbStructures.guessPeekShowLangCodes
                    && guessPeekDisplay.equals(GtbStructures.normalizeGuessPeekDisplay(
                            GtbStructures.guessPeekDisplay))
                    && scrambleGuess == GtbStructures.scrambleGuess
                    && scrambleGuessFancy == GtbStructures.scrambleGuessFancy
                    && trollMode == GtbStructures.trollMode
                    && trollModeAutoDisable == GtbStructures.trollModeAutoDisable
                    && same(chatHeaderRaw, ChatHeader.raw())
                    && showAllMatching == GtbStructures.showAllMatching
                    && showAllMatchingShowEnglish == GtbStructures.showAllMatchingShowEnglish
                    && themePointReveal == GtbStructures.themePointReveal
                    && themePointRevealShowAfterGuess == GtbStructures.themePointRevealShowAfterGuess
                    && shortcutReminder == GtbStructures.shortcutReminder
                    && shortcutReminderShowNonAscii == GtbStructures.shortcutReminderShowNonAscii
                    && shortcutReminderShowExtendedLatin == GtbStructures.shortcutReminderShowExtendedLatin
                    && shortcutReminderShowCyrillic == GtbStructures.shortcutReminderShowCyrillic
                    && shortcutReminderShowHanzi == GtbStructures.shortcutReminderShowHanzi
                    && shortcutReminderShowJapanese == GtbStructures.shortcutReminderShowJapanese
                    && shortcutReminderShowHangul == GtbStructures.shortcutReminderShowHangul
                    && shortcutReminderShowAllShortcuts == GtbStructures.shortcutReminderShowAllShortcuts
                    && shortcutReminderShowMultiwords == GtbStructures.shortcutReminderShowMultiwords
                    && shortcutReminderShowAllMultiwords == GtbStructures.shortcutReminderShowAllMultiwords
                    && copyTranslationPrefix == GtbStructures.copyTranslationPrefix
                    && gtwShowEnglish == GtbStructures.gtwShowEnglish
                    && posX == GtbStructures.posX
                    && posY == GtbStructures.posY
                    && handheldItemPosX == GtbStructures.handheldItemPosX
                    && handheldItemPosY == GtbStructures.handheldItemPosY
                    && environmentPosX == GtbStructures.environmentPosX
                    && environmentPosY == GtbStructures.environmentPosY
                    && chatCooldownPosX == GtbStructures.chatCooldownPosX
                    && chatCooldownPosY == GtbStructures.chatCooldownPosY
                    && handheldItemMaxVisible == GtbStructures.handheldItemMaxVisible
                    && handheldItemPanelWidthRatio == GtbStructures.handheldItemPanelWidthRatio
                    && handheldItemPanelHeightRatio == GtbStructures.handheldItemPanelHeightRatio
                    && scoreboardHeightOffset == GtbStructures.sbConfig.heightOffset
                    && same(spawnEggSkin, GtbStructures.spawnEggSkin)
                    && same(environmentTimeIconPreset, GtbStructures.environmentTimeIconPreset)
                    && same(colorNumbers, GtbStructures.colorNumbers)
                    && same(colorHyphens, GtbStructures.colorHyphens)
                    && same(colorBrackets, GtbStructures.colorBrackets)
                    && same(chatCooldownBarColor, GtbStructures.chatCooldownBarColor)
                    && same(chatCooldownTimerColor, GtbStructures.chatCooldownTimerColor)
                    && same(scoreboardSkin, GtbStructures.sbConfig.skin);
        }

        private static boolean same(String left, String right) {
            if (left == null) return right == null;
            return left.equals(right);
        }
    }

    private static final class Rect {
        final int x1;
        final int y1;
        final int x2;
        final int y2;

        Rect(int x1, int y1, int x2, int y2) {
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
        }

        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }

        int width() {
            return x2 - x1;
        }

        int height() {
            return y2 - y1;
        }
    }

    private static final class Label {
        final String id;
        final int x1;
        final int y1;
        final int x2;
        final int y2;
        final Target target;

        Label(String id, int x1, int y1, int x2, int y2, Target target) {
            this.id = id == null ? "" : id;
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
            this.target = target;
        }

        int width() {
            return x2 - x1;
        }

        int height() {
            return y2 - y1;
        }

        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }
}
