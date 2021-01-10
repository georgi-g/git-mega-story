package visu_log;

import java.util.*;
import java.util.stream.Collectors;

public class TableCreator {
    static List<List<HistoryEntry>> createTableFromDroppingColumns(List<Column> columns) {
        List<Deque<HistoryEntry>> droppingColumns = columns.stream().map(c -> new ArrayDeque<>(c.entries)).collect(Collectors.toList());

        List<List<HistoryEntry>> table = new ArrayList<>();
        for (int currentLineNumber = 0; ; currentLineNumber++) {
            boolean commitsFound = false;
            List<HistoryEntry> entries = new ArrayList<>();
            for (Deque<HistoryEntry> c : droppingColumns) {
                if (!c.isEmpty() && c.peek().commitId < currentLineNumber)
                    throw new RuntimeException("Wrong structure. Commit Ids in a column must increase");

                HistoryEntry historyEntry = !c.isEmpty() && c.peek().commitId == currentLineNumber ? c.poll() : null;
                if (historyEntry != null) {
                    commitsFound = true;
                    entries.add(historyEntry);
                } else {
                    entries.add(null);
                }
            }
            if (commitsFound)
                table.add(entries);
            else if (droppingColumns.stream().allMatch(Collection::isEmpty)) {
                break;
            }
        }
        return table;
    }
}
