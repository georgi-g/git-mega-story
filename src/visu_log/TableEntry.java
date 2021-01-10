package visu_log;

import java.util.List;

interface TableEntry {
    List<HistoryEntry> getEntries();

    default boolean isMainNodeFor(Commit commit) {
        List<HistoryEntry> entries = getEntries();
        return entries.size() == 1 && entries.get(0).typeOfParent.isMainNode() && entries.get(0).commit == commit;
    }

    default boolean isMainNode() {
        return getEntries().stream().anyMatch(e -> e.typeOfParent.isMainNode());
    }

    default boolean isLabeled() {
        return getEntries().stream().anyMatch(HistoryEntry::isLabeled);
    }

    default boolean mayJoin(TableEntry candidate) {
        boolean candidateHasParent = candidate.getEntries().stream().allMatch(e -> e.typeOfParent.hasParent());
        boolean meHasParent = this.getEntries().stream().allMatch(e -> e.typeOfParent.hasParent());
        if (!candidateHasParent && meHasParent)
            return false;

        return candidate.getEntries().get(0).parent == this.getEntries().get(0).parent;
    }
}
