package com.gtbstructures.scoreboard;

import com.gtbstructures.sound.GuessSounds;
import java.util.ArrayList;
import java.util.List;

/** Navigation and filtered, virtualized sound rows inside the existing menu. */
final class GuessSoundsPreviewPanel {
    private boolean opened, searchEditing;
    private int tab = 2, scroll;
    private String search = "", filteredSearch = null;
    private GuessSounds.Catalog source;
    private List<GuessSoundsUiLogic.Choice> filtered = List.of();

    boolean isOpen() { return opened; }
    int points() { return tab + 1; }
    int scroll() { return scroll; }
    boolean isSearchEditing() { return searchEditing; }
    String search() { syncSearch(); return search; }

    void open() { close(); opened = true; tab = 2; scroll = 0; search = ""; filteredSearch = null; }
    void close() { finishSearch(); opened = false; }
    void selectTab(int value) { tab = GuessSoundsUiLogic.tabForPoints(value); scroll = 0; }
    void setSearchForTest(String value) { search = value == null ? "" : value; scroll = 0; }

    void beginSearch() {
        searchEditing = true;
        PreviewScreenController.beginTextEditing(search, PreviewScreenController.TextFieldPurpose.GROUP_SEARCH);
    }
    void positionSearch(GuessSoundsUiLogic.Layout layout) {
        if (searchEditing) PreviewScreenController.positionHeaderEditor(layout.searchX, layout.searchY, layout.searchWidth, 16);
    }
    void finishSearch() {
        if (!searchEditing) return;
        syncSearch();
        PreviewScreenController.finishHeaderEditing();
        searchEditing = false;
    }
    private void syncSearch() {
        if (!searchEditing) return;
        String value = PreviewScreenController.headerEditingValue(search);
        if (!search.equals(value)) { search = value; scroll = 0; }
    }
    List<GuessSoundsUiLogic.Choice> rows() {
        syncSearch();
        GuessSounds.Catalog catalog = GuessSounds.catalog();
        if (source != catalog || !search.equals(filteredSearch)) {
            List<GuessSoundsUiLogic.Choice> choices = new ArrayList<>();
            choices.add(new GuessSoundsUiLogic.Choice(GuessSounds.NONE, "None"));
            choices.add(new GuessSoundsUiLogic.Choice(GuessSounds.RANDOM, "Random"));
            for (GuessSounds.Entry entry : catalog.entries()) choices.add(new GuessSoundsUiLogic.Choice(entry.id, entry.caption));
            filtered = GuessSoundsUiLogic.filter(choices, search);
            source = catalog; filteredSearch = search;
        }
        return filtered;
    }
    void clamp(GuessSoundsUiLogic.Layout layout) { scroll = GuessSoundsUiLogic.clampScroll(scroll, rows().size(), layout.visibleRows); }
    void scrollBy(int amount, GuessSoundsUiLogic.Layout layout) {
        scroll = GuessSoundsUiLogic.clampScroll(scroll + amount, rows().size(), layout.visibleRows);
    }
    void setScrollFromScrollbar(int value, GuessSoundsUiLogic.Layout layout) {
        scroll = GuessSoundsUiLogic.clampScroll(value, rows().size(), layout.visibleRows);
    }
    boolean scrollAt(int mouseX, int mouseY, double delta, GuessSoundsUiLogic.Layout layout) {
        if (!opened || delta == 0 || !GuessSoundsUiLogic.contains(mouseX, mouseY,
                layout.listX, layout.listY, layout.searchWidth, layout.listHeight)) return false;
        scrollBy(delta > 0 ? -3 : 3, layout);
        return true;
    }
    String rowAt(int mouseX, int mouseY, GuessSoundsUiLogic.Layout layout) {
        clamp(layout);
        if (!GuessSoundsUiLogic.contains(mouseX, mouseY, layout.listX, layout.listY, layout.listWidth, layout.listHeight)) return null;
        int index = scroll + (mouseY - layout.listY) / GuessSoundsUiLogic.ROW_HEIGHT;
        return index >= rows().size() ? null : rows().get(index).id;
    }
}
