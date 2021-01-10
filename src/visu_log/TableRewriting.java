package visu_log;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class TableRewriting {
    static void rewriteSecondaryDropping(ArrayList<List<HistoryEntry>> table) {
        Integer[] moveAdvises = new Integer[table.get(0).size()];

        for (int lineId = table.size() - 1; lineId >= 0; lineId--) {
            List<HistoryEntry> row = table.get(lineId);
            int mainNodeColumn = findMainNode(row);

            for (int i = 0; i < row.size(); i++) {
                // clear advice if something is at the target
                if (moveAdvises[i] != null && row.get(moveAdvises[i]) != null) {
                    moveAdvises[i] = null;
                }
                // move merge references from i to moveAdvises[i]
                else if (moveAdvises[i] != null && row.get(i) != null) {
                    // move if it is merge_sth
                    if (row.get(i).typeOfParent == TypeOfParent.MERGE_STH) {
                        row.set(moveAdvises[i], row.get(i));
                        row.set(i, null);
                    }
                    // everything else cancels the advice
                    else {
                        moveAdvises[i] = null;
                    }
                }
            }

            // create advice: backreferences indicate the source, mainNode indicates the target of the suggested move
            for (int i = 0; i < row.size(); i++) {
                if (i != mainNodeColumn && row.get(i) != null && row.get(i).backReference == TypeOfBackReference.YES) {
                    moveAdvises[i] = mainNodeColumn;
                }
            }
        }

        // find and delete the back references that arent used any more
        boolean[] parentIsPresent = new boolean[table.get(0).size()];
        for (List<HistoryEntry> row : table) {
            for (int i = 0; i < row.size(); i++) {
                HistoryEntry e = row.get(i);
                if (e != null) {
                    if (!parentIsPresent[i]) {
                        // delete the unused back reference
                        if (e.typeOfParent == TypeOfParent.NONE && e.backReference == TypeOfBackReference.YES) {
                            row.set(i, null);
                        }
                    }

                    parentIsPresent[i] = e.typeOfParent.hasParent();
                }
            }
        }
    }

    private static void fillDummy(List<HistoryEntry> row, HistoryEntry dummy) {
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

    private static boolean isJoinable(List<HistoryEntry> previous, List<HistoryEntry> next) {
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

    static void compressTable(ArrayList<List<HistoryEntry>> table) {
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

    public static void repairIds(List<List<HistoryEntry>> table) {
        repairIds(table, null);
    }

    private static void repairIds(List<List<HistoryEntry>> table, HistoryEntry dummyEntry) {
        for (int row = 0; row < table.size(); row++) {
            List<HistoryEntry> rowEntries = table.get(row);
            for (int i = 0; i < rowEntries.size(); i++) {
                HistoryEntry e = rowEntries.get(i);
                if (e == dummyEntry) {
                    rowEntries.set(i, null);
                } else if (e != null)
                    e.commitId = row;
            }
        }
    }

    private static int findMainNode(List<HistoryEntry> row) {
        for (int parentColumn = 0; parentColumn < row.size(); parentColumn++) {
            HistoryEntry parent = row.get(parentColumn);
            if (parent != null && parent.typeOfParent.isMainNode()) {
                return parentColumn;
            }
        }
        throw new RuntimeException("Main Node not found");
    }
}
