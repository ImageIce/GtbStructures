package com.gtbstructures.scoreboard;

import com.gtbstructures.translation.ShortcutLanguageGroups;
import com.gtbstructures.util.FabricInputBridge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Draft/navigation state for the Preview language-group drill-down. */
final class ShortcutLanguageGroupPreviewManager {
    enum View { LABELS, GROUPS, EDITOR }
    enum Field { NONE, NAME, SEARCH }

    static final class LanguageOption {
        final String code, displayName;
        final boolean available, selected;
        LanguageOption(String code, String displayName, boolean available, boolean selected) {
            this.code = code; this.displayName = displayName;
            this.available = available; this.selected = selected;
        }
        String caption() { return available ? displayName + " (" + code + ")" : "Unavailable (" + code + ")"; }
    }

    private View view = View.LABELS;
    private Field field = Field.NONE;
    private String editingId = "", name = "", search = "", error = "", detail = "";
    private boolean enabled = true, rawTranslation, rawTranslationAllLowercase, deleteConfirmation;
    private int groupScroll, languageScroll;
    private final LinkedHashSet<String> languages = new LinkedHashSet<String>();

    View view() { return view; }
    boolean inSubview() { return view != View.LABELS; }
    boolean editing() { return view == View.EDITOR; }
    boolean isNew() { return editingId.isEmpty(); }
    boolean draftEnabled() { return enabled; }
    boolean draftRawTranslation() { return rawTranslation; }
    boolean draftRawTranslationAllLowercase() { return rawTranslationAllLowercase; }
    String draftName() { syncInlineField(); return name; }
    String draftSearch() { syncInlineField(); return search; }
    void setDraftNameForTest(String value) { name = value == null ? "" : value; clearValidation(); }
    void setDraftSearchForTest(String value) { search = value == null ? "" : value; languageScroll = 0; }
    String validationError() { return error; }
    String validationDetail() { return detail; }
    boolean deleteConfirmation() { return deleteConfirmation; }
    int groupScroll() { return groupScroll; }
    int languageScroll() { return languageScroll; }

    void reset() { cancelInlineField(); view = View.LABELS; groupScroll = 0; clearDraft(); }
    void openManager() { cancelInlineField(); view = View.GROUPS; groupScroll = 0; clearDraft(); }
    void closeToLabels() { reset(); }
    void back() { if (view == View.EDITOR) openManager(); else if (view == View.GROUPS) closeToLabels(); }

    void beginNew() { beginEditor("", "", true, false, false, new ArrayList<String>()); }
    boolean beginEdit(int oneBasedIndex) {
        ShortcutLanguageGroups.Group group = ShortcutLanguageGroups.groupAt(oneBasedIndex);
        if (group == null) return false;
        beginEditor(group.id, group.name, group.enabled, group.rawTranslation,
                group.rawTranslationAllLowercase, group.languages);
        return true;
    }
    private void beginEditor(String id, String groupName, boolean groupEnabled, boolean raw,
                             boolean lowercase, List<String> groupLanguages) {
        cancelInlineField(); view = View.EDITOR; editingId = id == null ? "" : id;
        name = groupName == null ? "" : groupName; enabled = groupEnabled;
        rawTranslation = raw; rawTranslationAllLowercase = lowercase;
        languages.clear(); if (groupLanguages != null) languages.addAll(groupLanguages);
        search = ""; error = ""; detail = ""; deleteConfirmation = false; languageScroll = 0;
    }
    void toggleDraftEnabled() { enabled = !enabled; clearValidation(); }
    void toggleDraftRawTranslation() { rawTranslation = !rawTranslation; clearValidation(); }
    void toggleDraftRawTranslationAllLowercase() { rawTranslationAllLowercase = !rawTranslationAllLowercase; clearValidation(); }
    void toggleLanguage(String code) { String normalized = ShortcutLanguageGroups.normalizeLanguageCode(code); if (normalized == null) return; if (!languages.remove(normalized)) languages.add(normalized); clearValidation(); }
    void selectAll() { LinkedHashSet<String> unknown = new LinkedHashSet<String>(); for (String code : languages) if (!ShortcutLanguageGroups.isSupportedLanguage(code)) unknown.add(code); languages.clear(); languages.addAll(ShortcutLanguageGroups.supportedLanguageCodes()); languages.addAll(unknown); clearValidation(); }
    void selectNone() { languages.clear(); clearValidation(); }
    void requestDelete() { if (!isNew()) deleteConfirmation = true; }
    void cancelDelete() { deleteConfirmation = false; }

    void beginInlineField(Field requested) {
        if (!editing()) return;
        syncInlineField(); field = requested == null ? Field.NONE : requested;
        if (field != Field.NONE) {
            PreviewScreenController.beginTextEditing(field == Field.NAME ? name : search,
                    field == Field.NAME
                            ? PreviewScreenController.TextFieldPurpose.GROUP_NAME
                            : PreviewScreenController.TextFieldPurpose.GROUP_SEARCH);
        }
    }
    Field inlineField() { return field; }
    void positionInlineField(int x, int y, int width, int height) { if (field != Field.NONE) PreviewScreenController.positionHeaderEditor(x, y, width, height); }
    void syncInlineField() { if (field == Field.NONE) return; String value = PreviewScreenController.headerEditingValue(field == Field.NAME ? name : search); if (field == Field.NAME) name = value; else { if (!search.equals(value)) languageScroll = 0; search = value; } }
    void finishInlineField() { syncInlineField(); if (field != Field.NONE) PreviewScreenController.finishHeaderEditing(); field = Field.NONE; }
    void cancelInlineField() { if (field != Field.NONE) PreviewScreenController.cancelHeaderEditing(); field = Field.NONE; }
    boolean handleKey(int keyCode) {
        if (FabricInputBridge.isKeyCode(keyCode, FabricInputBridge.Key.ESCAPE)) { cancelInlineField(); back(); return true; }
        if (FabricInputBridge.isKeyCode(keyCode, FabricInputBridge.Key.ENTER)
                || FabricInputBridge.isKeyCode(keyCode, FabricInputBridge.Key.NUMPAD_ENTER)) {
            finishInlineField(); return true;
        }
        return false;
    }

    List<LanguageOption> filteredLanguages() {
        syncInlineField(); List<String> codes = new ArrayList<String>(ShortcutLanguageGroups.supportedLanguageCodes());
        for (String code : languages) if (!ShortcutLanguageGroups.isSupportedLanguage(code) && !codes.contains(code)) codes.add(code);
        String needle = search.trim().toLowerCase(Locale.ROOT); List<LanguageOption> result = new ArrayList<LanguageOption>();
        for (String code : codes) { boolean available = ShortcutLanguageGroups.isSupportedLanguage(code); String display = available ? ShortcutLanguageGroups.languageDisplayName(code) : "Unavailable";
            if (!needle.isEmpty() && !code.toLowerCase(Locale.ROOT).contains(needle) && !display.toLowerCase(Locale.ROOT).contains(needle)) continue;
            result.add(new LanguageOption(code, display, available, languages.contains(code))); }
        return result;
    }
    ShortcutLanguageGroups.Result saveDraft() { syncInlineField(); ShortcutLanguageGroups.Result result = isNew() ? ShortcutLanguageGroups.create(name, rawTranslation, rawTranslationAllLowercase, languages) : ShortcutLanguageGroups.update(editingId, name, enabled, rawTranslation, rawTranslationAllLowercase, languages); if (result.success) openManager(); else { error = result.error; detail = result.detail; deleteConfirmation = false; } return result; }
    ShortcutLanguageGroups.Result deleteDraft() { int index = editingIndex(); ShortcutLanguageGroups.Result result = ShortcutLanguageGroups.delete(index); if (result.success) openManager(); else { error = result.error; detail = result.detail; } return result; }
    ShortcutLanguageGroups.Result toggleGroup(int index) { ShortcutLanguageGroups.Group group = ShortcutLanguageGroups.groupAt(index); return ShortcutLanguageGroups.setGroupEnabled(index, group != null && !group.enabled); }
    void scrollBy(int amount, int viewportHeight, int rowHeight) { int content = view == View.GROUPS ? ShortcutLanguageGroups.groups().size() * rowHeight * 2 : filteredLanguages().size() * rowHeight; if (view == View.GROUPS) groupScroll = clampScroll(groupScroll + amount, content, viewportHeight); else if (view == View.EDITOR) languageScroll = clampScroll(languageScroll + amount, content, viewportHeight); }
    void setScrollFromScrollbar(int value, int viewportHeight, int rowHeight) { int content = view == View.GROUPS ? ShortcutLanguageGroups.groups().size() * rowHeight * 2 : filteredLanguages().size() * rowHeight; if (view == View.GROUPS) groupScroll = clampScroll(value, content, viewportHeight); else if (view == View.EDITOR) languageScroll = clampScroll(value, content, viewportHeight); }
    void clampScrolls(int groupViewportHeight, int groupRowHeight,
                      int languageViewportHeight, int languageRowHeight) {
        groupScroll = clampScroll(groupScroll,
                ShortcutLanguageGroups.groups().size() * groupRowHeight,
                groupViewportHeight);
        languageScroll = clampScroll(languageScroll,
                filteredLanguages().size() * languageRowHeight,
                languageViewportHeight);
    }
    static int clampScroll(int value, int content, int viewport) { return Math.max(0, Math.min(Math.max(0, content - Math.max(0, viewport)), value)); }
    private int editingIndex() { List<ShortcutLanguageGroups.Group> groups = ShortcutLanguageGroups.groups(); for (int i=0;i<groups.size();i++) if (editingId.equals(groups.get(i).id)) return i+1; return -1; }
    private void clearValidation() { error = ""; detail = ""; }
    private void clearDraft() { editingId=""; name=""; search=""; enabled=true; rawTranslation=false; rawTranslationAllLowercase=false; languages.clear(); clearValidation(); deleteConfirmation=false; languageScroll=0; }
}
