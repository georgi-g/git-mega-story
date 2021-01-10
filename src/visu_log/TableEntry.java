package visu_log;

import java.util.List;

interface TableEntry {
    List<HistoryEntry> getEntries();

    default boolean isMainNodeFor(Commit commit) {
        List<HistoryEntry> entries = getEntries();
        return entries.size() == 1 && entries.get(0).typeOfParent.isMainNode() && entries.get(0).commit == commit;
    }
}
