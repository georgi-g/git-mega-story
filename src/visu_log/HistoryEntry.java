package visu_log;

import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;

public class HistoryEntry {
    public final RevCommit commit;
    public final RevCommit parent;
    public final Column column;
    public int commitId;
    public TypeOfBackReference backReference;
    public TypeOfParent typeOfParent;
    public List<HistoryEntry> joinedForSameParent = new ArrayList<>();
    public final boolean isLabeled;

    HistoryEntry(RevCommit commit, Column column, int commitId) {
        this.commit = commit;
        this.column = column;
        this.commitId = commitId;
        this.parent = null;
        column.appendEntry(this);
        joinedForSameParent.add(this);
        isLabeled = false;
    }

    HistoryEntry(RevCommit commit, Column column, int commitId, RevCommit parent, boolean isLabeled) {
        this.isLabeled = isLabeled;
        joinedForSameParent.add(this);
        this.commit = commit;
        this.column = column;
        this.commitId = commitId;
        this.parent = parent;
        HistoryEntry maybeJoined = column.entries.peekLast();
        if (maybeJoined != null && maybeJoined.parent != null && maybeJoined.parent != commit) {
            if (maybeJoined.parent != this.parent) {
                throw new RuntimeException("Don't want to join foreign columns yet.");
            } else {
                joinedForSameParent.addAll(maybeJoined.joinedForSameParent);
            }
        }
        column.appendEntry(this);
    }
}
