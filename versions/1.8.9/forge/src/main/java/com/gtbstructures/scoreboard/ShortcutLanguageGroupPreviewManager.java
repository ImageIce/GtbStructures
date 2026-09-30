package com.gtbstructures.scoreboard;

import com.gtbstructures.translation.ShortcutLanguageGroups;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Draft and navigation state for the drill-down language-group Preview editor. */
final class ShortcutLanguageGroupPreviewManager {
    enum View { LABELS, GROUPS, EDITOR }
    enum KeyAction { NONE, CONSUMED, SAVE }

    static final class LanguageOption {
        final String code;
        final String displayName;
        final boolean available;
        final boolean selected;

        LanguageOption(String code, String displayName, boolean available, boolean selected) {
            this.code = code;
            this.displayName = displayName;
            this.available = available;
            this.selected = selected;
        }

        String caption() {
            return available ? displayName + " (" + code + ")"
                    : "Unavailable (" + code + ")";
        }
    }

    private View view = View.LABELS;
    private String editingId = "";
    private boolean draftEnabled = true;
    private boolean draftRawTranslation;
    private boolean draftRawTranslationAllLowercase;
    private final LinkedHashSet<String> draftLanguages = new LinkedHashSet<String>();
    private String draftName = "";
    private String search = "";
    private String validationError = "";
    private String validationDetail = "";
    private boolean deleteConfirmation;
    private int groupScroll;
    private int languageScroll;
    private GuiTextField nameField;
    private GuiTextField searchField;
    private boolean repeatEventsWereEnabled;

    View view() {
        return view;
    }

    boolean inSubview() {
        return view != View.LABELS;
    }

    boolean editing() {
        return view == View.EDITOR;
    }

    void reset() {
        leaveEditorFields();
        view = View.LABELS;
        groupScroll = 0;
        clearDraft();
    }

    void openManager() {
        leaveEditorFields();
        view = View.GROUPS;
        groupScroll = 0;
        clearDraft();
    }

    void closeToLabels() {
        leaveEditorFields();
        view = View.LABELS;
        groupScroll = 0;
        clearDraft();
    }

    void back() {
        if (view == View.EDITOR) {
            openManager();
        } else if (view == View.GROUPS) {
            closeToLabels();
        }
    }

    void beginNew() {
        beginEditor("", "", true, false, false, new ArrayList<String>());
    }

    boolean beginEdit(int oneBasedIndex) {
        ShortcutLanguageGroups.Group group = ShortcutLanguageGroups.groupAt(oneBasedIndex);
        if (group == null) return false;
        beginEditor(group.id, group.name, group.enabled, group.rawTranslation,
                group.rawTranslationAllLowercase, group.languages);
        return true;
    }

    private void beginEditor(String id, String name, boolean enabled,
                             boolean rawTranslation,
                             boolean rawTranslationAllLowercase,
                             List<String> languages) {
        leaveEditorFields();
        view = View.EDITOR;
        editingId = id == null ? "" : id;
        draftName = name == null ? "" : name;
        draftEnabled = enabled;
        draftRawTranslation = rawTranslation;
        draftRawTranslationAllLowercase = rawTranslationAllLowercase;
        draftLanguages.clear();
        if (languages != null) draftLanguages.addAll(languages);
        search = "";
        validationError = "";
        validationDetail = "";
        deleteConfirmation = false;
        languageScroll = 0;
        repeatEventsWereEnabled = Keyboard.areRepeatEventsEnabled();
        Keyboard.enableRepeatEvents(true);
    }

    boolean isNew() {
        return editingId.isEmpty();
    }

    String editingId() {
        return editingId;
    }

    String draftName() {
        return nameField == null ? draftName : nameField.getText();
    }

    void setDraftNameForTest(String name) {
        draftName = name == null ? "" : name;
        if (nameField != null) nameField.setText(draftName);
        clearValidation();
    }

    boolean draftEnabled() {
        return draftEnabled;
    }

    void toggleDraftEnabled() {
        draftEnabled = !draftEnabled;
        clearValidation();
    }

    boolean draftRawTranslation() {
        return draftRawTranslation;
    }

    void toggleDraftRawTranslation() {
        draftRawTranslation = !draftRawTranslation;
        clearValidation();
    }

    boolean draftRawTranslationAllLowercase() {
        return draftRawTranslationAllLowercase;
    }

    void toggleDraftRawTranslationAllLowercase() {
        draftRawTranslationAllLowercase = !draftRawTranslationAllLowercase;
        clearValidation();
    }

    List<String> draftLanguages() {
        return new ArrayList<String>(draftLanguages);
    }

    void toggleLanguage(String code) {
        String normalized = ShortcutLanguageGroups.normalizeLanguageCode(code);
        if (normalized == null) return;
        if (!draftLanguages.remove(normalized)) draftLanguages.add(normalized);
        clearValidation();
    }

    void selectAll() {
        LinkedHashSet<String> unavailable = new LinkedHashSet<String>();
        for (String code : draftLanguages) {
            if (!ShortcutLanguageGroups.isSupportedLanguage(code)) unavailable.add(code);
        }
        draftLanguages.clear();
        draftLanguages.addAll(ShortcutLanguageGroups.supportedLanguageCodes());
        draftLanguages.addAll(unavailable);
        clearValidation();
    }

    void selectNone() {
        draftLanguages.clear();
        clearValidation();
    }

    List<LanguageOption> filteredLanguages() {
        List<String> codes = new ArrayList<String>(ShortcutLanguageGroups.supportedLanguageCodes());
        for (String selected : draftLanguages) {
            if (!ShortcutLanguageGroups.isSupportedLanguage(selected)
                    && !codes.contains(selected)) codes.add(selected);
        }
        String needle = searchText().trim().toLowerCase(Locale.ROOT);
        List<LanguageOption> options = new ArrayList<LanguageOption>();
        for (String code : codes) {
            boolean available = ShortcutLanguageGroups.isSupportedLanguage(code);
            String name = available
                    ? ShortcutLanguageGroups.languageDisplayName(code) : "Unavailable";
            if (!needle.isEmpty()
                    && !code.toLowerCase(Locale.ROOT).contains(needle)
                    && !name.toLowerCase(Locale.ROOT).contains(needle)) continue;
            options.add(new LanguageOption(code, name, available,
                    draftLanguages.contains(code)));
        }
        return options;
    }

    ShortcutLanguageGroups.Result saveDraft() {
        draftName = draftName();
        ShortcutLanguageGroups.Result result = isNew()
                ? ShortcutLanguageGroups.create(draftName, draftRawTranslation,
                        draftRawTranslationAllLowercase, draftLanguages)
                : ShortcutLanguageGroups.update(
                        editingId, draftName, draftEnabled, draftRawTranslation,
                        draftRawTranslationAllLowercase, draftLanguages);
        if (result.success) {
            openManager();
        } else {
            setValidation(result.error, result.detail);
        }
        return result;
    }

    ShortcutLanguageGroups.Result deleteDraft() {
        int index = editingIndex();
        ShortcutLanguageGroups.Result result = ShortcutLanguageGroups.delete(index);
        if (result.success) {
            openManager();
        } else {
            setValidation(result.error, result.detail);
        }
        return result;
    }

    ShortcutLanguageGroups.Result toggleGroup(int oneBasedIndex) {
        ShortcutLanguageGroups.Group group = ShortcutLanguageGroups.groupAt(oneBasedIndex);
        if (group == null) {
            return ShortcutLanguageGroups.setGroupEnabled(oneBasedIndex, false);
        }
        return ShortcutLanguageGroups.setGroupEnabled(oneBasedIndex, !group.enabled);
    }

    private int editingIndex() {
        List<ShortcutLanguageGroups.Group> groups = ShortcutLanguageGroups.groups();
        for (int i = 0; i < groups.size(); i++) {
            if (editingId.equals(groups.get(i).id)) return i + 1;
        }
        return -1;
    }

    void requestDelete() {
        if (!isNew()) deleteConfirmation = true;
    }

    void cancelDelete() {
        deleteConfirmation = false;
    }

    boolean deleteConfirmation() {
        return deleteConfirmation;
    }

    String validationError() {
        return validationError;
    }

    String validationDetail() {
        return validationDetail;
    }

    void ensureTextFields(FontRenderer fr,
                          int nameX, int nameY, int nameWidth, int nameHeight,
                          int searchX, int searchY, int searchWidth, int searchHeight) {
        if (!editing() || fr == null) return;
        if (nameField == null) {
            nameField = new GuiTextField(1101, fr, nameX, nameY, nameWidth, nameHeight);
            nameField.setMaxStringLength(128);
            nameField.setText(draftName);
        }
        if (searchField == null) {
            searchField = new GuiTextField(1102, fr, searchX, searchY, searchWidth, searchHeight);
            searchField.setMaxStringLength(64);
            searchField.setText(search);
        }
        place(nameField, nameX, nameY, nameWidth, nameHeight);
        place(searchField, searchX, searchY, searchWidth, searchHeight);
    }

    private static void place(GuiTextField field, int x, int y, int width, int height) {
        field.xPosition = x;
        field.yPosition = y;
        field.width = width;
        field.height = height;
    }

    void drawTextFields() {
        if (nameField == null || searchField == null) return;
        nameField.updateCursorCounter();
        nameField.drawTextBox();
        searchField.updateCursorCounter();
        searchField.drawTextBox();
    }

    void mouseClicked(int mouseX, int mouseY) {
        if (nameField == null || searchField == null) return;
        nameField.mouseClicked(mouseX, mouseY, 0);
        searchField.mouseClicked(mouseX, mouseY, 0);
    }

    KeyAction onKeyTyped(char typedChar, int keyCode) {
        if (view == View.GROUPS) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeToLabels();
                return KeyAction.CONSUMED;
            }
            return KeyAction.NONE;
        }
        if (!editing()) return KeyAction.NONE;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            openManager();
            return KeyAction.CONSUMED;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            return KeyAction.SAVE;
        }
        String oldSearch = searchText();
        boolean consumed = false;
        if (nameField != null) consumed |= nameField.textboxKeyTyped(typedChar, keyCode);
        if (searchField != null) consumed |= searchField.textboxKeyTyped(typedChar, keyCode);
        draftName = draftName();
        search = searchText();
        if (!oldSearch.equals(search)) languageScroll = 0;
        if (consumed) clearValidation();
        return consumed ? KeyAction.CONSUMED : KeyAction.NONE;
    }

    String searchText() {
        return searchField == null ? search : searchField.getText();
    }

    void setSearchForTest(String value) {
        search = value == null ? "" : value;
        if (searchField != null) searchField.setText(search);
        languageScroll = 0;
    }

    int groupScroll() {
        return groupScroll;
    }

    int languageScroll() {
        return languageScroll;
    }

    void scrollBy(int amount, int viewportHeight, int rowHeight) {
        if (view == View.GROUPS) {
            int content = ShortcutLanguageGroups.groups().size() * rowHeight;
            groupScroll = clampScroll(groupScroll + amount, content, viewportHeight);
        } else if (view == View.EDITOR) {
            int content = filteredLanguages().size() * rowHeight;
            languageScroll = clampScroll(languageScroll + amount, content, viewportHeight);
        }
    }

    void setScrollFromScrollbar(int value, int viewportHeight, int rowHeight) {
        if (view == View.GROUPS) {
            groupScroll = clampScroll(value,
                    ShortcutLanguageGroups.groups().size() * rowHeight, viewportHeight);
        } else if (view == View.EDITOR) {
            languageScroll = clampScroll(value,
                    filteredLanguages().size() * rowHeight, viewportHeight);
        }
    }

    void clampScrolls(int groupViewportHeight, int groupRowHeight,
                      int languageViewportHeight, int languageRowHeight) {
        groupScroll = clampScroll(groupScroll,
                ShortcutLanguageGroups.groups().size() * groupRowHeight, groupViewportHeight);
        languageScroll = clampScroll(languageScroll,
                filteredLanguages().size() * languageRowHeight, languageViewportHeight);
    }

    static int clampScroll(int value, int contentHeight, int viewportHeight) {
        return Math.max(0, Math.min(Math.max(0, contentHeight - Math.max(0, viewportHeight)), value));
    }

    private void setValidation(String error, String detail) {
        validationError = error == null ? "" : error;
        validationDetail = detail == null ? "" : detail;
        deleteConfirmation = false;
    }

    private void clearValidation() {
        validationError = "";
        validationDetail = "";
    }

    private void clearDraft() {
        editingId = "";
        draftEnabled = true;
        draftRawTranslation = false;
        draftRawTranslationAllLowercase = false;
        draftLanguages.clear();
        draftName = "";
        search = "";
        clearValidation();
        deleteConfirmation = false;
        languageScroll = 0;
        nameField = null;
        searchField = null;
    }

    private void leaveEditorFields() {
        if (view == View.EDITOR) Keyboard.enableRepeatEvents(repeatEventsWereEnabled);
        nameField = null;
        searchField = null;
    }
}
