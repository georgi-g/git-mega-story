package visu_log;

import java.util.List;
import java.util.ListIterator;

public class TableRewriting {
    static void rewriteSecondaryDropping(List<? extends List<? extends TableEntry>> table) {
        Integer[] moveAdvises = new Integer[table.get(0).size()];

        for (int lineId = table.size() - 1; lineId >= 0; lineId--) {
            List<? extends TableEntry> row = table.get(lineId);
            int mainNodeColumn = findMainNode(row);

            for (int i = 0; i < row.size(); i++) {
                // clear advice if something is at the target
                if (moveAdvises[i] != null && row.get(moveAdvises[i]) != null) {
                    moveAdvises[i] = null;
                }
                // move merge references from i to moveAdvises[i]
                else if (moveAdvises[i] != null && row.get(i) != null) {
                    // move if it is merge_sth
                    if (row.get(i).getEntries().stream().allMatch(e -> e.typeOfParent == TypeOfParent.MERGE_STH)) {
                        moveElement(row, i, moveAdvises[i]);
                    }
                    // everything else cancels the advice
                    else {
                        moveAdvises[i] = null;
                    }
                }
            }

            // create advice: backreferences indicate the source, mainNode indicates the target of the suggested move
            for (int i = 0; i < row.size(); i++) {
                if (i != mainNodeColumn && row.get(i) != null && row.get(i).getEntries().stream().allMatch(e -> e.backReference == TypeOfBackReference.YES)) {
                    moveAdvises[i] = mainNodeColumn;
                }
            }
        }

        // find and delete the back references that arent used any more
        boolean[] parentIsPresent = new boolean[table.get(0).size()];
        for (List<? extends TableEntry> row : table) {
            for (int i = 0; i < row.size(); i++) {
                TableEntry te = row.get(i);
                if (te != null) {
                    if (!parentIsPresent[i]) {
                        // delete the unused back reference
                        if (te.getEntries().stream().allMatch(e -> e.typeOfParent == TypeOfParent.NONE && e.backReference == TypeOfBackReference.YES)) {
                            row.set(i, null);
                        }
                    }

                    parentIsPresent[i] = te.getEntries().stream().anyMatch(e -> e.typeOfParent.hasParent());
                }
            }
        }
    }

    private static <T extends TableEntry> void moveElement(List<T> row, int from, int to) {
        row.set(to, row.get(from));
        row.set(from, null);
    }

    static void removeEmptyColumns(List<? extends List<? extends TableEntry>> table) {
        boolean[] hasElements = new boolean[table.get(0).size()];

        for (List<? extends TableEntry> row : table) {
            for (int i = 0; i < row.size(); i++) {
                TableEntry e = row.get(i);
                if (e != null) {
                    hasElements[i] = true;
                }
            }
        }

        for (List<? extends TableEntry> row : table) {
            for (int i = hasElements.length - 1; i >= 0; i--) {
                if (!hasElements[i])
                    row.remove(i);
            }
        }
    }

    private static <T extends TableEntry> void fillDummy(List<T> row, T dummy) {
        int mainNode = findMainNode(row);

        boolean fill = false;
        for (int i = 0; i < mainNode; i++) {
            if (fill && row.get(i) == null) {
                row.set(i, dummy);
            }
            if (row.get(i) != null) {
                fill = true;
            }
        }

        fill = false;
        for (int i = row.size() - 1; i > mainNode; i--) {
            if (fill && row.get(i) == null) {
                row.set(i, dummy);
            }
            if (row.get(i) != null) {
                fill = true;
            }
        }
    }

    private static boolean isJoinable(List<? extends TableEntry> previous, List<? extends TableEntry> next) {
        boolean rowIsJoinable = true;
        boolean nextIsLabeled = false;
        boolean prevIsLabeled = false;
        for (int i = 0; i < next.size(); i++) {
            rowIsJoinable &= previous.get(i) == null || next.get(i) == null;
            nextIsLabeled |= next.get(i) != null && next.get(i).isLabeled();
            prevIsLabeled |= previous.get(i) != null && previous.get(i).isLabeled();
        }

        return rowIsJoinable && !(nextIsLabeled && prevIsLabeled);
    }

    static void compressTable(List<List<HistoryEntry>> table) {
        if (table.size() < 2) {
            return;
        }

        HistoryEntry dummyEntry = new HistoryEntry(null, Column.createNewList(), -1, null, null);

        fillDummy(table.get(0), dummyEntry);


        for (ListIterator<List<HistoryEntry>> it = table.listIterator(1); it.hasNext(); ) {
            List<HistoryEntry> next = it.next();

            fillDummy(next, dummyEntry);

            List<HistoryEntry> previous = null;

            for (int idPrevious = it.previousIndex() - 1; idPrevious >= 0 && isJoinable(table.get(idPrevious), next); idPrevious--) {
                previous = table.get(idPrevious);
            }

            if (previous == null)
                continue;

            for (int i = 0; i < next.size(); i++) {
                HistoryEntry entry = next.get(i);
                if (entry != null) {
                    previous.set(i, entry);
                }
            }
            it.remove();
        }


        repairIds(table, dummyEntry);
    }

    public static void repairIds(List<? extends List<? extends TableEntry>> table) {
        repairIds(table, null);
    }

    private static void repairIds(List<? extends List<? extends TableEntry>> table, TableEntry dummyEntry) {
        for (int row = 0; row < table.size(); row++) {
            List<? extends TableEntry> rowEntries = table.get(row);
            for (int i = 0; i < rowEntries.size(); i++) {
                TableEntry e = rowEntries.get(i);
                if (e == dummyEntry) {
                    rowEntries.set(i, null);
                } else if (e != null) {
                    int finalRow = row;
                    e.getEntries().forEach(ee -> ee.commitId = finalRow);
                }
            }
        }
    }

    private static int findMainNode(List<? extends TableEntry> row) {
        for (int parentColumn = 0; parentColumn < row.size(); parentColumn++) {
            TableEntry parent = row.get(parentColumn);
            if (parent != null && parent.isMainNode()) {
                return parentColumn;
            }
        }
        throw new RuntimeException("Main Node not found");
    }
}
