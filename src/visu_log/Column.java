package visu_log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

class Column {
    int branchId;
    List<Column> subColumns = new ArrayList<>();
    Deque<HistoryEntry> entries = new ArrayDeque<>();

    Stream<Column> getColumnStream() {
        return subColumns.stream()
                .flatMap(c -> {
                    if (c != this)
                        return c.getColumnStream();
                    else
                        return Stream.of(c);
                });
    }

    Column() {
        subColumns.add(this);
    }

    static Column createNewList() {
        return new Column();
    }

    Column createSubColumn(int branchId) {
        Column sc = new Column();
        sc.branchId = branchId;
        subColumns.add(sc);
        return sc;
    }

    public Column createSubColumnBefore(int branchId) {
        int myIndex = subColumns.indexOf(this);
        Column sc = new Column();
        sc.branchId = branchId;
        subColumns.add(myIndex, sc);
        return sc;
    }

    public void appendEntry(HistoryEntry he) {
        if (he.column != this)
            throw new RuntimeException("HistoryEntry is not allowed to be here.");
        entries.add(he);
    }

    public HistoryEntry getLastEntry() {
        if (entries.size() > 0)
            return entries.peekLast();
        throw new RuntimeException("Column is Empty");
    }
}
