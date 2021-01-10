package visu_log;

public enum TypeOfParent {
    SINGLE_PARENT("┿", "┯", true, true),
    INITIAL("┷", "╸", true, false),
    MERGE_STH("╭", "╭", false, true),
    MERGE_MAIN("┣", "┏", true, true),
    NONE("╰", "x", false, false);

    private final String withBackReference;
    private final String withoutBackReference;
    private final boolean isMainNode;
    private final boolean hasParent;

    TypeOfParent(String withBackReference, String withoutBackReference, boolean isMainNode, boolean hasParent) {
        this.withBackReference = withBackReference;
        this.withoutBackReference = withoutBackReference;
        this.isMainNode = isMainNode;
        this.hasParent = hasParent;
    }

    boolean isMainNode() {
        return isMainNode;
    }

    boolean hasParent() {
        return hasParent;
    }

    String getSymbol(TypeOfBackReference backReference) {
        switch (backReference) {
            case NO:
                return withoutBackReference;
            case YES:
            default:
                return withBackReference;
        }
    }
}
