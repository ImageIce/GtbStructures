package com.gtbstructures.commands;

/** Metadata for rendering the inline page controls on a help result row. */
public final class HelpPageNavigation {
    public static final String PREVIOUS_SYMBOL = "\u00ab";
    public static final String NEXT_SYMBOL = "\u00bb";
    public static final String PREVIOUS_LABEL = "Previous page";
    public static final String NEXT_LABEL = "Next page";
    public static final String ENABLED_FORMATTING = "\u00a76";
    public static final String DISABLED_FORMATTING = "\u00a78";

    public final int rowIndex;
    public final String prefix;
    public final int page;
    public final int pageCount;
    public final String suffix;
    public final String previousCommand;
    public final String nextCommand;
    public final boolean previousEnabled;
    public final boolean nextEnabled;

    public static String hoverText(String label, String command) {
        return label + "\n" + command;
    }

    public HelpPageNavigation(int rowIndex, String prefix, int page, int pageCount,
                              String suffix, String previousCommand, String nextCommand) {
        if (rowIndex < 0) throw new IllegalArgumentException("rowIndex must not be negative");
        if (pageCount < 1 || page < 1 || page > pageCount) {
            throw new IllegalArgumentException("page outside result bounds");
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
