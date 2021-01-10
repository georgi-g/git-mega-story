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
}
