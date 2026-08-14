package com.gtbstructures.hotbar;

import com.gtbstructures.GtbStructures;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

public final class SavedHotbars {
    public static final SavedHotbars INSTANCE = new SavedHotbars();

    private static final int HOTBAR_COUNT = 9;
    private static final int HOTBAR_SIZE = 9;
    private static final int PLAYER_HOTBAR_CONTAINER_BASE_SLOT = 36;
    private static final int HOTBAR_NBT_DATA_VERSION = 1343;
    private static final String HOTBAR_FILE = "hotbar.nbt";
    private static final String KEY_CATEGORY = "key.categories.gtbstructures.saved_hotbars";
    private static final int CREATIVE_GRID_LEFT = 9;
    private static final int CREATIVE_GRID_TOP = 18;
    private static final int CREATIVE_GRID_COLUMNS = 9;
    private static final int CREATIVE_VISIBLE_ROWS = 5;
    private static final int CREATIVE_SLOT_SIZE = 18;
    private static final int CREATIVE_GUI_HEIGHT = 136;
    private static final int CUSTOM_TAB_WIDTH = 26;
    private static final int VANILLA_TAB_TEXTURE_WIDTH = 28;
    private static final int CUSTOM_TAB_HEIGHT = 32;
    private static final int HIDDEN_TAB_OVERLAP = 4;
    private static final int TOP_TAB_HIT_Y = -32;
    private static final int TOP_TAB_DRAW_Y = -28;
    private static final int BOTTOM_TAB_HIT_Y = CREATIVE_GUI_HEIGHT;
    private static final int BOTTOM_TAB_DRAW_Y = CREATIVE_GUI_HEIGHT - HIDDEN_TAB_OVERLAP;
    private static final int[] TOP_TAB_X = {0, 27, 54, 81, 108, 142, 169};
    private static final int[] BOTTOM_TAB_X = {0, 27, 54, 81, 108, 169};
    private static final ResourceLocation CREATIVE_TABS_TEXTURE =
            new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");
    private static final TabGui TAB_GUI = new TabGui();
    private static final String HINT_PAPER_MARKER = "GtbSavedHotbarHint";

    private static final KeyBinding SAVE_TOOLBAR = new KeyBinding(
            "key.gtbstructures.save_toolbar_activator", Keyboard.KEY_C, KEY_CATEGORY);
    private static final KeyBinding LOAD_TOOLBAR = new KeyBinding(
            "key.gtbstructures.load_toolbar_activator", Keyboard.KEY_X, KEY_CATEGORY);

    public static final CreativeTabs SAVED_HOTBARS_TAB = new CreativeTabs("gtbstructures.saved_hotbars") {
        @Override
        public Item getTabIconItem() {
            return Item.getItemFromBlock(Blocks.bookshelf);
        }

        @Override
        public String getTranslatedTabLabel() {
            return "Saved Hotbars";
        }

        @Override
        public int getTabPage() {
            return 0;
        }

        @Override
        public int getTabColumn() {
            return -100;
        }

        @Override
        public boolean isTabInFirstRow() {
            return true;
        }

        @Override
        public void displayAllReleventItems(List<ItemStack> items) {
            if (!featureEnabledForMaster(GtbStructures.modEnabled)) return;
            ItemStack[][] hotbars = INSTANCE.loadHotbars();
            for (int hotbar = 0; hotbar < HOTBAR_COUNT; hotbar++) {
                boolean empty = rowEmpty(hotbars[hotbar]);
                for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
                    int expectedIndex = SavedHotbarLogic.flatHotbarIndex(hotbar, slot, HOTBAR_SIZE);
                    while (items.size() < expectedIndex) {
                        items.add(null);
                    }
                    ItemStack stack = copyStack(hotbars[hotbar][slot]);
                    if (empty && slot == hotbar) {
                        stack = hintPaper(hotbar);
                    }
                    items.add(stack);
                }
            }
        }
    };

    private boolean registeredBindings;
    private static boolean customTabWrappersInstalled;
    private final boolean[] hotbarKeyDown = new boolean[HOTBAR_COUNT];
    private static Field selectedTabIndexField;
    private static Field currentScrollField;
    private static Field guiLeftField;
    private static Field guiTopField;
    private static Field tabPageField;
    private static Field maxPagesField;
    private static Method setCurrentCreativeTabMethod;

    private SavedHotbars() {
    }

    public void init() {
        installCustomTabWrappers();
        if (registeredBindings) return;
        ClientRegistry.registerKeyBinding(SAVE_TOOLBAR);
        ClientRegistry.registerKeyBinding(LOAD_TOOLBAR);
        registeredBindings = true;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!featureEnabledForMaster(GtbStructures.modEnabled)) return;
        handleKeyboardEvent(null);
    }

    @SubscribeEvent
    public void onGuiKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!featureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!(event.gui instanceof GuiContainerCreative)) return;
        if (handleKeyboardEvent(event.gui)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGuiMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!featureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!(event.gui instanceof GuiContainerCreative)) return;
        GuiContainerCreative gui = (GuiContainerCreative) event.gui;
        if (handleCustomTabClick(gui) || shouldCancelSavedHotbarClick(gui)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onCreativeInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!featureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!(event.gui instanceof GuiContainerCreative)) return;
        removeCreativePagerButtons(event.buttonList);
        forceSingleCreativePage((GuiContainerCreative) event.gui);
    }

    @SubscribeEvent
    public void onCreativeBackgroundDrawn(GuiScreenEvent.BackgroundDrawnEvent event) {
        if (!featureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!(event.gui instanceof GuiContainerCreative)) return;
        GuiContainerCreative gui = (GuiContainerCreative) event.gui;
        forceSingleCreativePage(gui);
        drawCustomTopTabs(gui, false);
        drawCustomTopTabs(gui, true);
        drawCustomBottomTabs(gui, false);
        drawCustomBottomTabs(gui, true);
    }

    @SubscribeEvent
    public void onCreativeDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!featureEnabledForMaster(GtbStructures.modEnabled)) return;
        if (!(event.gui instanceof GuiContainerCreative)) return;
        GuiContainerCreative gui = (GuiContainerCreative) event.gui;
        forceSingleCreativePage(gui);
        if (!selectedTabWouldCoverTooltip(gui, event.mouseX, event.mouseY, true)) {
            drawCustomTopTabs(gui, true);
        }
        if (!selectedTabWouldCoverTooltip(gui, event.mouseX, event.mouseY, false)) {
            drawCustomBottomTabs(gui, true);
        }
    }

    static int containerHotbarSlotForTest(int hotbarSlot) {
        return containerHotbarSlot(hotbarSlot);
    }

    public static boolean featureEnabledForMaster(boolean modEnabled) {
        return modEnabled;
    }

    public static boolean nativeCreativeTabsForMaster(boolean modEnabled) {
        return !featureEnabledForMaster(modEnabled);
    }

    static int hotbarSlotForKeyForTest(int eventKey, int[] keyCodes) {
        return hotbarSlotForKey(eventKey, keyCodes);
    }

    static String hotbarKeyForTest(int hotbarIndex) {
        return hotbarKey(hotbarIndex);
    }

    static boolean shouldHandleHotbarChordForTest(boolean saveDown, boolean loadDown, boolean keyAlreadyDown) {
        return SavedHotbarLogic.shouldHandleHotbarChord(saveDown, loadDown, keyAlreadyDown);
    }

    static boolean shouldApplyLoadedSlotForTest(NBTTagCompound tag, boolean decodedStackPresent) {
        return shouldApplyLoadedSlot(tag, decodedStackPresent);
    }

    static int savedCellIndexForTest(int mouseX, int mouseY, int guiLeft, int guiTop,
                                     float scroll, int itemCount) {
        return savedCellIndex(mouseX, mouseY, guiLeft, guiTop, scroll, itemCount);
    }

    private boolean handleKeyboardEvent(Object gui) {
        if (!Keyboard.getEventKeyState()) {
            int released = hotbarSlotForKey(Minecraft.getMinecraft(), Keyboard.getEventKey());
            if (released >= 0) hotbarKeyDown[released] = false;
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (!canUse(mc, gui)) return false;

        boolean saveDown = isKeyDown(SAVE_TOOLBAR);
        boolean loadDown = isKeyDown(LOAD_TOOLBAR);
        if (!SavedHotbarLogic.shouldHandleHotbarChord(saveDown, loadDown, false)) return false;

        int hotbarSlot = hotbarSlotForKey(mc, Keyboard.getEventKey());
        if (hotbarSlot < 0) return false;
        if (!SavedHotbarLogic.shouldHandleHotbarChord(saveDown, loadDown, hotbarKeyDown[hotbarSlot])) return true;
        hotbarKeyDown[hotbarSlot] = true;

        if (saveDown) {
            saveHotbar(mc, hotbarSlot);
        } else {
            loadHotbar(mc, hotbarSlot);
        }
        return true;
    }

    private static boolean canUse(Minecraft mc, Object gui) {
        if (mc == null || mc.thePlayer == null || mc.playerController == null) return false;
        if (!mc.playerController.isInCreativeMode()) return false;
        return mc.currentScreen == null || gui instanceof GuiContainerCreative;
    }

    private void saveHotbar(Minecraft mc, int hotbarIndex) {
        ItemStack[][] hotbars = loadHotbars();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            hotbars[hotbarIndex][slot] = copyStack(mc.thePlayer.inventory.mainInventory[slot]);
        }
        if (writeHotbars(hotbars)) {
            showOverlay(mc, "inventory.hotbarSaved", hotbarIndex + 1);
        }
    }

    private void loadHotbar(Minecraft mc, int hotbarIndex) {
        NBTTagList list = readRoot().getTagList(hotbarKey(hotbarIndex), 10);
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            NBTTagCompound tag = slot < list.tagCount() ? list.getCompoundTagAt(slot) : new NBTTagCompound();
            ItemStack stack = isEmptySlotTag(tag) ? null : ItemStack.loadItemStackFromNBT(tag);
            if (!shouldApplyLoadedSlot(tag, stack != null)) {
                continue;
            }
            mc.thePlayer.inventory.mainInventory[slot] = stack;
            mc.playerController.sendSlotPacket(copyStack(stack), containerHotbarSlot(slot));
        }
        mc.thePlayer.inventory.markDirty();
        showOverlay(mc, "inventory.hotbarRestored", hotbarIndex + 1);
    }

    private boolean shouldCancelSavedHotbarClick(GuiContainerCreative gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.thePlayer.inventory == null) return false;
        if (!isSavedHotbarsTabSelected(gui)) return false;
        if (Mouse.getEventButton() < 0 || !Mouse.getEventButtonState()) return false;

        int index = savedCellIndex(mouseX(mc), mouseY(mc), guiLeft(gui), guiTop(gui), currentScroll(gui),
                HOTBAR_COUNT * HOTBAR_SIZE);
        if (index < 0) return false;
        int hotbar = index / HOTBAR_SIZE;
        int slot = index % HOTBAR_SIZE;
        ItemStack[][] hotbars = loadHotbars();
        if (hotbar < hotbars.length && SavedHotbarLogic.isEmptyRowHintSlot(hotbar, slot, HOTBAR_SIZE)
                && rowEmpty(hotbars[hotbar])) {
            return true;
        }

        if (mc.thePlayer.inventory.getItemStack() == null) return false;
        NBTTagList list = readRoot().getTagList(hotbarKey(index / HOTBAR_SIZE), 10);
        NBTTagCompound tag = slot < list.tagCount()
                ? list.getCompoundTagAt(slot)
                : new NBTTagCompound();
        return isEmptySlotTag(tag) || ItemStack.loadItemStackFromNBT(tag) == null;
    }

    private ItemStack[][] loadHotbars() {
        ItemStack[][] hotbars = new ItemStack[HOTBAR_COUNT][HOTBAR_SIZE];
        NBTTagCompound root = readRoot();
        for (int hotbar = 0; hotbar < HOTBAR_COUNT; hotbar++) {
            NBTTagList list = root.getTagList(hotbarKey(hotbar), 10);
            for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
                if (slot < list.tagCount()) {
                    hotbars[hotbar][slot] = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(slot));
                }
            }
        }
        return hotbars;
    }

    private boolean writeHotbars(ItemStack[][] hotbars) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("DataVersion", HOTBAR_NBT_DATA_VERSION);
        for (int hotbar = 0; hotbar < HOTBAR_COUNT; hotbar++) {
            NBTTagList list = new NBTTagList();
            for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
                NBTTagCompound item = new NBTTagCompound();
                ItemStack stack = null;
                if (hotbars != null && hotbar < hotbars.length && hotbars[hotbar] != null
                        && slot < hotbars[hotbar].length) {
                    stack = hotbars[hotbar][slot];
                }
                if (stack != null) {
                    stack.writeToNBT(item);
                }
                list.appendTag(item);
            }
            root.setTag(hotbarKey(hotbar), list);
        }

        File file = hotbarFile();
        try {
            CompressedStreamTools.write(root, file);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static NBTTagCompound readRoot() {
        File file = hotbarFile();
        if (!file.isFile()) return new NBTTagCompound();
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            return root == null ? new NBTTagCompound() : root;
        } catch (IOException ignored) {
            try {
                FileInputStream in = new FileInputStream(file);
                try {
                    NBTTagCompound root = CompressedStreamTools.readCompressed(in);
                    return root == null ? new NBTTagCompound() : root;
                } finally {
                    in.close();
                }
            } catch (IOException ignoredAgain) {
                return new NBTTagCompound();
            }
        }
    }

    private static File hotbarFile() {
        Minecraft mc = Minecraft.getMinecraft();
        File dir = mc == null || mc.mcDataDir == null ? new File(".") : mc.mcDataDir;
        return new File(dir, HOTBAR_FILE);
    }

    private static int hotbarSlotForKey(Minecraft mc, int eventKey) {
        if (mc == null || mc.gameSettings == null || mc.gameSettings.keyBindsHotbar == null) return -1;
        int[] keys = new int[Math.min(HOTBAR_COUNT, mc.gameSettings.keyBindsHotbar.length)];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = mc.gameSettings.keyBindsHotbar[i].getKeyCode();
        }
        return hotbarSlotForKey(eventKey, keys);
    }

    private static int hotbarSlotForKey(int eventKey, int[] keyCodes) {
        return SavedHotbarLogic.hotbarSlotForKey(eventKey, Keyboard.KEY_NONE, keyCodes, HOTBAR_COUNT);
    }

    private static boolean isKeyDown(KeyBinding binding) {
        int key = binding.getKeyCode();
        return key != Keyboard.KEY_NONE && key >= 0 && Keyboard.isKeyDown(key);
    }

    private static int containerHotbarSlot(int hotbarSlot) {
        return SavedHotbarLogic.containerHotbarSlot(PLAYER_HOTBAR_CONTAINER_BASE_SLOT, hotbarSlot);
    }

    private static int savedCellIndex(int mouseX, int mouseY, int guiLeft, int guiTop,
                                      float scroll, int itemCount) {
        return SavedHotbarLogic.savedCellIndex(mouseX, mouseY, guiLeft, guiTop, scroll, itemCount,
                CREATIVE_GRID_LEFT, CREATIVE_GRID_TOP, CREATIVE_GRID_COLUMNS, CREATIVE_VISIBLE_ROWS,
                CREATIVE_SLOT_SIZE);
    }

    private static boolean shouldApplyLoadedSlot(NBTTagCompound tag, boolean decodedStackPresent) {
        return decodedStackPresent || isEmptySlotTag(tag);
    }

    private static boolean isEmptySlotTag(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) return true;
        if (!tag.hasKey("id")) return true;
        String id = tag.getString("id");
        return id == null || id.isEmpty() || "air".equals(id) || "minecraft:air".equals(id);
    }

    private static boolean rowEmpty(ItemStack[] row) {
        if (row == null) return true;
        for (ItemStack stack : row) {
            if (stack != null) return false;
        }
        return true;
    }

    private static ItemStack hintPaper(int hotbarIndex) {
        ItemStack stack = new ItemStack(Items.paper);
        stack.setStackDisplayName("\u00a7fSave hotbar with " + keyDisplay(SAVE_TOOLBAR.getKeyCode())
                + " + " + hotbarKeyDisplay(hotbarIndex));
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean(HINT_PAPER_MARKER, true);
        return stack;
    }

    @SuppressWarnings("unused")
    private static boolean isHintPaper(ItemStack stack) {
        return stack != null && stack.getItem() == Items.paper && stack.hasTagCompound()
                && stack.getTagCompound().getBoolean(HINT_PAPER_MARKER);
    }

    private static String hotbarKeyDisplay(int hotbarIndex) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.gameSettings.keyBindsHotbar == null
                || hotbarIndex < 0 || hotbarIndex >= mc.gameSettings.keyBindsHotbar.length) {
            return String.valueOf(hotbarIndex + 1);
        }
        return keyDisplay(mc.gameSettings.keyBindsHotbar[hotbarIndex].getKeyCode());
    }

    private static String keyDisplay(int keyCode) {
        return GameSettings.getKeyDisplayString(keyCode);
    }

    private static boolean isSavedHotbarsTabSelected(GuiContainerCreative gui) {
        try {
            if (selectedTabIndexField == null) {
                selectedTabIndexField = ReflectionHelper.findField(GuiContainerCreative.class,
                        "selectedTabIndex", "field_147058_w");
                selectedTabIndexField.setAccessible(true);
            }
            return selectedTabIndexField.getInt(null) == SAVED_HOTBARS_TAB.getTabIndex();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ItemStack hoveredTooltipStack(GuiContainerCreative gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.thePlayer.inventory == null) return null;
        if (mc.thePlayer.inventory.getItemStack() != null) return null;
        Slot slot = gui.getSlotUnderMouse();
        return slot != null && slot.getHasStack() ? slot.getStack() : null;
    }

    private static float currentScroll(GuiContainerCreative gui) {
        try {
            if (currentScrollField == null) {
                currentScrollField = ReflectionHelper.findField(GuiContainerCreative.class,
                        "currentScroll", "field_147067_x");
                currentScrollField.setAccessible(true);
            }
            return currentScrollField.getFloat(gui);
        } catch (Throwable ignored) {
            return 0F;
        }
    }

    private static int guiLeft(GuiContainerCreative gui) {
        try {
            if (guiLeftField == null) {
                guiLeftField = ReflectionHelper.findField(GuiContainer.class, "guiLeft", "field_147003_i");
                guiLeftField.setAccessible(true);
            }
            return guiLeftField.getInt(gui);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int guiTop(GuiContainerCreative gui) {
        try {
            if (guiTopField == null) {
                guiTopField = ReflectionHelper.findField(GuiContainer.class, "guiTop", "field_147009_r");
                guiTopField.setAccessible(true);
            }
            return guiTopField.getInt(gui);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int mouseX(Minecraft mc) {
        return Mouse.getEventX() * mc.currentScreen.width / mc.displayWidth;
    }

    private static int mouseY(Minecraft mc) {
        return mc.currentScreen.height - Mouse.getEventY() * mc.currentScreen.height / mc.displayHeight - 1;
    }

    private static CreativeTabs[] customTopTabs() {
        return new CreativeTabs[] {
                CreativeTabs.tabBlock,
                CreativeTabs.tabDecorations,
                CreativeTabs.tabRedstone,
                CreativeTabs.tabTransport,
                CreativeTabs.tabMisc,
                SAVED_HOTBARS_TAB,
                CreativeTabs.tabAllSearch
        };
    }

    private static CreativeTabs[] customBottomTabs() {
        return new CreativeTabs[] {
                CreativeTabs.tabFood,
                CreativeTabs.tabTools,
                CreativeTabs.tabCombat,
                CreativeTabs.tabBrewing,
                CreativeTabs.tabMaterials
        };
    }

    private static boolean handleCustomTabClick(GuiContainerCreative gui) {
        int button = Mouse.getEventButton();
        if (button < 0 || !Mouse.getEventButtonState()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null) return false;
        int tab = customTabAt(mouseX(mc), mouseY(mc), guiLeft(gui), guiTop(gui), TOP_TAB_X, TOP_TAB_HIT_Y);
        CreativeTabs[] tabs = customTopTabs();
        if (tab < 0) {
            tab = customTabAt(mouseX(mc), mouseY(mc), guiLeft(gui), guiTop(gui), BOTTOM_TAB_X, BOTTOM_TAB_HIT_Y);
            tabs = customBottomTabs();
        }
        if (tab < 0 || tab >= tabs.length) return false;
        if (button == 0) {
            selectCreativeTab(gui, tabs[tab]);
        }
        return true;
    }

    private static int customTabAt(int mouseX, int mouseY, int guiLeft, int guiTop, int[] tabXs, int tabY) {
        return SavedHotbarLogic.compactTopTabAt(mouseX - guiLeft, mouseY - guiTop,
                tabXs, tabY, CUSTOM_TAB_WIDTH, CUSTOM_TAB_HEIGHT);
    }

    private static void removeCreativePagerButtons(List<GuiButton> buttonList) {
        if (buttonList == null) return;
        Iterator<GuiButton> iterator = buttonList.iterator();
        while (iterator.hasNext()) {
            GuiButton button = iterator.next();
            if (button != null && (button.id == 101 || button.id == 102)) {
                iterator.remove();
            }
        }
    }

    private static void forceSingleCreativePage(GuiContainerCreative gui) {
        try {
            if (tabPageField == null) {
                tabPageField = ReflectionHelper.findField(GuiContainerCreative.class, "tabPage");
                tabPageField.setAccessible(true);
            }
            tabPageField.setInt(null, 0);
        } catch (Throwable ignored) {
        }

        try {
            if (maxPagesField == null) {
                maxPagesField = ReflectionHelper.findField(GuiContainerCreative.class, "maxPages");
                maxPagesField.setAccessible(true);
            }
            maxPagesField.setInt(gui, 0);
        } catch (Throwable ignored) {
        }
    }

    private static void selectCreativeTab(GuiContainerCreative gui, CreativeTabs tab) {
        if (gui == null || tab == null) return;
        forceSingleCreativePage(gui);
        try {
            if (setCurrentCreativeTabMethod == null) {
                setCurrentCreativeTabMethod = ReflectionHelper.findMethod(GuiContainerCreative.class, gui,
                        new String[] {"setCurrentCreativeTab", "func_147050_b"}, CreativeTabs.class);
            }
            setCurrentCreativeTabMethod.invoke(gui, tab);
            forceSingleCreativePage(gui);
        } catch (Throwable ignored) {
        }
    }

    private static void drawCustomTopTabs(GuiContainerCreative gui, boolean selectedOnly) {
        drawCustomTabRow(gui, customTopTabs(), TOP_TAB_X, TOP_TAB_DRAW_Y, true, selectedOnly);
    }

    private static void drawCustomBottomTabs(GuiContainerCreative gui, boolean selectedOnly) {
        drawCustomTabRow(gui, customBottomTabs(), BOTTOM_TAB_X, BOTTOM_TAB_DRAW_Y, false, selectedOnly);
    }

    private static void drawCustomTabRow(GuiContainerCreative gui, CreativeTabs[] tabs,
                                         int[] tabXs, int tabY, boolean topRow, boolean selectedOnly) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        int selectedIndex = selectedTabIndex();
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == null) continue;
            boolean selected = tabs[i].getTabIndex() == selectedIndex;
            if (selected == selectedOnly) {
                drawCustomTab(mc, gui, tabs[i], tabXs[i], tabY, topRow, selected);
            }
        }
    }

    private static boolean selectedTabWouldCoverTooltip(GuiContainerCreative gui, int mouseX, int mouseY,
                                                        boolean topRow) {
        ItemStack stack = hoveredTooltipStack(gui);
        if (stack == null) return false;

        int[] tabBounds = selectedCustomTabBounds(gui, topRow);
        if (tabBounds == null) return false;

        int[] tooltipBounds = tooltipBounds(gui, stack, mouseX, mouseY);
        return tooltipBounds != null && intersects(tabBounds, tooltipBounds);
    }

    private static int[] selectedCustomTabBounds(GuiContainerCreative gui, boolean topRow) {
        CreativeTabs[] tabs = topRow ? customTopTabs() : customBottomTabs();
        int[] tabXs = topRow ? TOP_TAB_X : BOTTOM_TAB_X;
        int tabY = topRow ? TOP_TAB_DRAW_Y : BOTTOM_TAB_DRAW_Y;
        int selectedIndex = selectedTabIndex();
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null && tabs[i].getTabIndex() == selectedIndex) {
                return new int[] {
                        guiLeft(gui) + tabXs[i],
                        guiTop(gui) + tabY,
                        CUSTOM_TAB_WIDTH,
                        CUSTOM_TAB_HEIGHT
                };
            }
        }
        return null;
    }

    private static int[] tooltipBounds(GuiContainerCreative gui, ItemStack stack, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null || mc.thePlayer == null || mc.gameSettings == null) {
            return null;
        }
        List<String> lines = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        if (lines == null || lines.isEmpty()) return null;

        int width = 0;
        for (String line : lines) {
            int lineWidth = mc.fontRendererObj.getStringWidth(line);
            if (lineWidth > width) width = lineWidth;
        }

        int x = mouseX + 12;
        int y = mouseY - 12;
        int height = lines.size() == 1 ? 8 : 10 + (lines.size() - 1) * 10;
        if (x + width > gui.width) {
            x -= 28 + width;
        }
        if (y + height + 6 > gui.height) {
            y = gui.height - height - 6;
        }
        return new int[] {x - 3, y - 4, width + 6, height + 7};
    }

    private static boolean intersects(int[] a, int[] b) {
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0]
                && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }

    private static void drawCustomTab(Minecraft mc, GuiContainerCreative gui, CreativeTabs tab,
                                      int tabX, int tabY, boolean topRow, boolean selected) {
        int sourceColumn = tab == CreativeTabs.tabAllSearch || tab == CreativeTabs.tabInventory ? 5 : 4;
        if (tab != SAVED_HOTBARS_TAB && tab != CreativeTabs.tabAllSearch && tab != CreativeTabs.tabInventory) {
            sourceColumn = Math.min(sourceColumnForTab(tab), 4);
        }
        int textureY = topRow ? (selected ? 32 : 0) : (selected ? 96 : 64);
        int x = guiLeft(gui) + tabX;
        int y = guiTop(gui) + tabY;
        int height = CUSTOM_TAB_HEIGHT;
        if (!selected) {
            height -= HIDDEN_TAB_OVERLAP;
            if (!topRow) {
                y += HIDDEN_TAB_OVERLAP;
                textureY += HIDDEN_TAB_OVERLAP;
            }
        }

        float oldZ = mc.getRenderItem().zLevel;
        try {
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.enableBlend();
            mc.getTextureManager().bindTexture(CREATIVE_TABS_TEXTURE);
            GlStateManager.disableDepth();
            TAB_GUI.drawShortTab(x, y, sourceColumn * VANILLA_TAB_TEXTURE_WIDTH, textureY,
                    CUSTOM_TAB_WIDTH, height);

            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();
            RenderItem renderItem = mc.getRenderItem();
            renderItem.zLevel = 100F;
            ItemStack icon = tab.getIconItemStack();
            int iconX = x + (CUSTOM_TAB_WIDTH - 16) / 2;
            int iconY = guiTop(gui) + tabY + (topRow ? 9 : 7);
            renderItem.renderItemAndEffectIntoGUI(icon, iconX, iconY);
            renderItem.renderItemOverlays(mc.fontRendererObj, icon, iconX, iconY);
            renderItem.zLevel = oldZ;
        } finally {
            mc.getRenderItem().zLevel = oldZ;
            GlStateManager.disableLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.color(1F, 1F, 1F, 1F);
        }
    }

    private static int sourceColumnForTab(CreativeTabs tab) {
        if (tab == CreativeTabs.tabDecorations || tab == CreativeTabs.tabTools) return 1;
        if (tab == CreativeTabs.tabRedstone || tab == CreativeTabs.tabCombat) return 2;
        if (tab == CreativeTabs.tabTransport || tab == CreativeTabs.tabBrewing) return 3;
        if (tab == CreativeTabs.tabMisc || tab == CreativeTabs.tabMaterials) return 4;
        return 0;
    }

    private static int selectedTabIndex() {
        try {
            if (selectedTabIndexField == null) {
                selectedTabIndexField = ReflectionHelper.findField(GuiContainerCreative.class,
                        "selectedTabIndex", "field_147058_w");
                selectedTabIndexField.setAccessible(true);
            }
            return selectedTabIndexField.getInt(null);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void installCustomTabWrappers() {
        if (customTabWrappersInstalled) return;
        customTabWrappersInstalled = true;
        for (int i = 0; i < 11 && i < CreativeTabs.creativeTabArray.length; i++) {
            CreativeTabs original = CreativeTabs.creativeTabArray[i];
            if (original != null && !(original instanceof HiddenCreativeTab)) {
                new HiddenCreativeTab(i, original, i < 6);
            }
        }
    }

    private static String hotbarKey(int hotbarIndex) {
        return String.valueOf(hotbarIndex);
    }

    private static ItemStack copyStack(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static void showOverlay(Minecraft mc, String key, int hotbarNumber) {
        try {
            mc.ingameGUI.setRecordPlaying(new ChatComponentTranslation(key, hotbarNumber), false);
        } catch (Throwable t) {
            mc.ingameGUI.setRecordPlayingMessage(key + " " + hotbarNumber);
        }
    }

    private static final class TabGui extends Gui {
        private void draw(int x, int y, int textureX, int textureY, int width, int height) {
            drawTexturedModalRect(x, y, textureX, textureY, width, height);
        }

        private void drawShortTab(int x, int y, int textureX, int textureY, int width, int height) {
            int leftWidth = width / 2;
            int rightWidth = width - leftWidth;
            drawTexturedModalRect(x, y, textureX, textureY, leftWidth, height);
            drawTexturedModalRect(x + leftWidth, y,
                    textureX + VANILLA_TAB_TEXTURE_WIDTH - rightWidth, textureY, rightWidth, height);
        }
    }

    private static final class HiddenCreativeTab extends CreativeTabs {
        private final CreativeTabs delegate;
        private final boolean firstRow;

        private HiddenCreativeTab(int index, CreativeTabs delegate, boolean firstRow) {
            super(index, delegate.getTabLabel());
            this.delegate = delegate;
            this.firstRow = firstRow;
        }

        @Override
        public Item getTabIconItem() {
            return delegate.getTabIconItem();
        }

        @Override
        public int getIconItemDamage() {
            return delegate.getIconItemDamage();
        }

        @Override
        public String getTranslatedTabLabel() {
            return delegate.getTranslatedTabLabel();
        }

        @Override
        public String getBackgroundImageName() {
            return delegate.getBackgroundImageName();
        }

        @Override
        public boolean drawInForegroundOfTab() {
            return delegate.drawInForegroundOfTab();
        }

        @Override
        public boolean shouldHidePlayerInventory() {
            return delegate.shouldHidePlayerInventory();
        }

        @Override
        public int getTabColumn() {
            return featureEnabledForMaster(GtbStructures.modEnabled)
                    ? -100 : delegate.getTabColumn();
        }

        @Override
        public boolean isTabInFirstRow() {
            return featureEnabledForMaster(GtbStructures.modEnabled)
                    ? firstRow : delegate.isTabInFirstRow();
        }

        @Override
        public EnumEnchantmentType[] getRelevantEnchantmentTypes() {
            return delegate.getRelevantEnchantmentTypes();
        }

        @Override
        public boolean hasSearchBar() {
            return delegate.hasSearchBar();
        }

        @Override
        public int getSearchbarWidth() {
            return delegate.getSearchbarWidth();
        }

        @Override
        public void displayAllReleventItems(List<ItemStack> items) {
            if (nativeCreativeTabsForMaster(GtbStructures.modEnabled)) {
                delegate.displayAllReleventItems(items);
                return;
            }
            if (delegate == CreativeTabs.tabAllSearch) {
                populateAllSearchItems(items);
                return;
            }
            delegate.displayAllReleventItems(items);
        }
    }

    private static void populateAllSearchItems(List<ItemStack> items) {
        for (Item item : Item.itemRegistry) {
            if (item != null && item.getCreativeTab() != null) {
                item.getSubItems(item, null, items);
            }
        }

        for (Enchantment enchantment : Enchantment.enchantmentsBookList) {
            if (enchantment != null && enchantment.type != null) {
                Items.enchanted_book.getAll(enchantment, items);
            }
        }
    }
}
