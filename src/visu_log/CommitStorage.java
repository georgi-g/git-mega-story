package visu_log;

public class CommitStorage {
    static HistoryEntry newEntryBackReferenceWithoutParent(Commit revCommit, Column theColumn, int commitId) {
        return new HistoryEntry(revCommit, theColumn, commitId, TypeOfBackReference.YES, TypeOfParent.NONE);
    }

    static HistoryEntry newEntryForParent(Commit revCommit, Commit parent, Column theColumn, TypeOfBackReference backReference, int commitId, boolean isLabeled) {

        TypeOfParent typeOfParent;

        switch (revCommit.getParentCount()) {
            default:
                typeOfParent = parent == revCommit.getMyParent(0) ? TypeOfParent.MERGE_MAIN : TypeOfParent.MERGE_STH;
                break;
            case 1:
                typeOfParent = TypeOfParent.SINGLE_PARENT;
                break;
            case 0:
                typeOfParent = TypeOfParent.INITIAL;
                break;
        }

        return new HistoryEntry(revCommit, theColumn, commitId, parent, backReference, typeOfParent, isLabeled);
    }
}
