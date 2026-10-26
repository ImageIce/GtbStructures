package com.gtbstructures.commands;

/** Immutable metadata for rendering clickable help pagination in its header row. */
public final class HelpPageNavigation {
    public final int rowIndex;
    public final String prefix;
    public final int page;
    public final int pageCount;
    public final String suffix;
    public final String previousCommand;
    public final String nextCommand;
    public final boolean previousEnabled;
    public final boolean nextEnabled;

    public HelpPageNavigation(int rowIndex, String prefix, int page, int pageCount, String suffix,
            String previousCommand, String nextCommand) {
        if (rowIndex < 0 || page < 1 || pageCount < 1 || page > pageCount) {
            throw new IllegalArgumentException("Invalid help page navigation bounds");
        }
        this.rowIndex = rowIndex;
        this.prefix = prefix == null ? "" : prefix;
        this.page = page;
        this.pageCount = pageCount;
        this.suffix = suffix == null ? "" : suffix;
        this.previousCommand = previousCommand;
        this.nextCommand = nextCommand;
        this.previousEnabled = previousCommand != null;
        this.nextEnabled = nextCommand != null;
    }
}
