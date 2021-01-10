package visu_log;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ColumnsSorter {
    static boolean alwaysCreateNewColumnsForEachParentOfAMultiParentCommit = false;
    static boolean alwaysCreateNewColumns = false;
    static boolean joinDroppingColumns = true;
    static boolean mayJoinDroppingMainColumn = false;
    static boolean forceCreateNewColumnsForLabeledCommits = true;

    static List<Column> sortCommitsIntoColumns(List<Branch> branches, List<? extends Commit> commits) {
        Column theParentColumn = Column.createNewList();

        final int[] branchId = {0};
        for (int i = 0; i < commits.size(); i++) {
            Commit revCommit = commits.get(i);
            if (i % 100 == 0)
                System.out.println("Calculating entry: " + i + " of " + commits.size());
            calculateEntryForCommit(revCommit, branches, theParentColumn, branchId, i);
        }


        return theParentColumn.getColumnStream().collect(Collectors.toList());
    }

    private static void calculateEntryForCommit(Commit revCommit, List<Branch> branches, Column theParentColumn, int[] branchId, int commitId) {
        //noinspection ConstantConditions
        Map<Commit, Map<Boolean, List<Column>>> columnsWithDanglingParents = theParentColumn.getColumnStream()
                .filter(c -> c.entries.size() > 0)
                .filter(c -> c.entries.peekLast().parent != null)
                .collect(Collectors.groupingBy(c -> c.entries.peekLast().parent,
                        Collectors.partitioningBy(h -> h.getLastEntry().joinedForSameParent.stream().anyMatch(hh -> hh.typeOfParent.isMainNode()))));

        List<HistoryEntry> mainReferencingEntries = columnsWithDanglingParents.containsKey(revCommit) ? columnsWithDanglingParents.get(revCommit).get(true).stream().map(Column::getLastEntry).collect(Collectors.toList()) : new ArrayList<>();
        List<HistoryEntry> secondaryReferencingEntries = columnsWithDanglingParents.containsKey(revCommit) ? columnsWithDanglingParents.get(revCommit).get(false).stream().map(Column::getLastEntry).collect(Collectors.toList()) : new ArrayList<>();
        long numberOfMainReferencingEntries = mainReferencingEntries.size();

        boolean commitIsLabeledByABranch = branches.stream().anyMatch(b -> b.commmit == revCommit);

        // having here list of columngs with entries referencing me (either having a main node or not)
        // having here list of columngs with referencing one of my parents (either having a main node or not)
        // having here list of own parents (one of which is a main reference)
        // what's the task?
        // for all nodes referencing me place something in there
        // for all of my parents create a node
        // my secondary parents might join other columns (//later maybe main parent may as main-join)
        // my main parent is placed either into one of the referencing columns or gets an own column

        // that means: all secondary parents search for join points
        //      decide whether they may join secondary columns only or also main columns


        boolean needNewMainBranchBecauseLabeled = forceCreateNewColumnsForLabeledCommits && commitIsLabeledByABranch && (numberOfMainReferencingEntries > 1);
        boolean needBranchBecauseMultiParent = revCommit.getParentCount() > 1 && alwaysCreateNewColumnsForEachParentOfAMultiParentCommit;

        // need to calculate:
        //     column for first parent // may be new, may be main referencing me, may be secondary referencing me
        //     exception: if no parents: same as placing first parent
        //     column for each of the other parents // may be new, may be referencing same but main, may be referencing same but secondary

        Column columnForFirstParent = null;
        TypeOfBackReference backReferenceForFirstParent;
        if (!(needNewMainBranchBecauseLabeled || needBranchBecauseMultiParent || alwaysCreateNewColumns))
            columnForFirstParent = Stream.of(mainReferencingEntries, secondaryReferencingEntries)
                    .flatMap(Collection::stream)
                    .findFirst()
                    .map(h -> h.column)
                    .orElse(null);

        // if needNewMainBranchBecauseLabeled stil may use column of secondary references
        if (columnForFirstParent == null && needNewMainBranchBecauseLabeled)
            columnForFirstParent = secondaryReferencingEntries.stream().findFirst().map(h -> h.column).orElse(null);

        backReferenceForFirstParent = columnForFirstParent != null ? TypeOfBackReference.YES : TypeOfBackReference.NO;

        // do not use or could not use dropping column for first parent
        if (columnForFirstParent == null) {
            if (needNewMainBranchBecauseLabeled && !mainReferencingEntries.isEmpty()) {
                columnForFirstParent = mainReferencingEntries.get(0).column.createSubColumnBefore(branchId[0]);
                branchId[0]++;
            } else {
                columnForFirstParent = theParentColumn.createSubColumn(branchId[0]);
                branchId[0]++;
            }
        }
        CommitStorage.newEntryForParent(revCommit, revCommit.getParentCount() > 0 ? revCommit.getMyParent(0) : null, columnForFirstParent, backReferenceForFirstParent, commitId, commitIsLabeledByABranch);


        Column finalColumnForFirstParent = columnForFirstParent;
        Stream.of(mainReferencingEntries, secondaryReferencingEntries).flatMap(Collection::stream).filter(c -> c.column != finalColumnForFirstParent).forEach(h -> {
            // here we have a column having history entries waiting for this commit so we create a backreference
            CommitStorage.newEntryBackReferenceWithoutParent(revCommit, h.column, commitId);
        });

        // secondary parents may join the dropping columns
        AtomicBoolean increase = new AtomicBoolean(false);
        revCommit.getMyParents().stream()
                .filter(p -> revCommit.getMyParent(0) != p)
                .forEach(parent -> {
                    boolean joined = false;
                    if (columnsWithDanglingParents.containsKey(parent) && joinDroppingColumns) {
                        List<Column> secondaryDroppingColumns = columnsWithDanglingParents.get(parent).get(false);
                        List<Column> mainDroppingColumns = columnsWithDanglingParents.get(parent).get(true);
                        Optional<Column> columnToUse;
                        if (secondaryDroppingColumns.isEmpty() && !mainDroppingColumns.isEmpty() && !mayJoinDroppingMainColumn) {
                            columnToUse = Optional.of(mainDroppingColumns.get(0).createSubColumnBefore(branchId[0]++));
                        } else {
                            columnToUse = Stream.of(
                                    columnsWithDanglingParents.get(parent).get(false),
                                    mayJoinDroppingMainColumn ? columnsWithDanglingParents.get(parent).get(true) : Collections.<Column>emptyList())
                                    .flatMap(Collection::stream).findFirst();
                        }

                        if (columnToUse.isPresent()) {
                            CommitStorage.newEntryForParent(revCommit, parent, columnToUse.get(), TypeOfBackReference.NO, commitId, false);
                            joined = true;
                        }
                    }

                    if (!joined) {
                        increase.set(true);
                        CommitStorage.newEntryForParent(revCommit, parent, theParentColumn.createSubColumn(branchId[0]), TypeOfBackReference.NO, commitId, false);
                    }
                });

        if (increase.get())
            branchId[0]++;
    }

}
