package visu_log;

import java.util.ArrayList;
import java.util.List;

public class HistoryEntry {
    public final Commit commit;
    public final Commit parent;
    public final Column column;
    public final TypeOfBackReference backReference;
    public final TypeOfParent typeOfParent;
    public final List<HistoryEntry> joinedForSameParent = new ArrayList<>();
    public final boolean isLabeled;

    public int commitId;

    HistoryEntry(Commit commit, Column column, int commitId, TypeOfBackReference backReference, TypeOfParent typeOfParent) {
        this.backReference = backReference;
        this.typeOfParent = typeOfParent;
        this.isLabeled = false;
        joinedForSameParent.add(this);
        this.commit = commit;
        this.column = column;
        this.commitId = commitId;
        this.parent = null;
        column.appendEntry(this);
    }

    HistoryEntry(Commit commit, Column column, int commitId, Commit parent, TypeOfBackReference backReference, TypeOfParent typeOfParent, boolean isLabeled) {
        this.backReference = backReference;
        this.typeOfParent = typeOfParent;
        this.isLabeled = isLabeled;
        joinedForSameParent.add(this);
        this.commit = commit;
        this.column = column;
        this.commitId = commitId;
        this.parent = parent;
        HistoryEntry maybeJoined = column.entries.peekLast();
        if (maybeJoined != null && maybeJoined.parent != null && maybeJoined.parent != commit) {
            if (maybeJoined.parent != parent) {
                throw new RuntimeException("Don't want to join foreign columns yet.");
            } else {
                joinedForSameParent.addAll(maybeJoined.joinedForSameParent);
            }
        }
        // important to do not before peekLast
        column.appendEntry(this);
    }
}
