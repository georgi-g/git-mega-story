package visu_log;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ColumnsSorter {
    static boolean alwaysCreateNewColumnsForEachParentOfAMultiParentCommit = false;
    static boolean alwaysCreateNewColumns = false;
    static boolean joinDroppingColumns = true;
    static boolean mayJoinDroppingMainColumn = false;
    static boolean forceCreateNewColumnsForLabeledCommits = true;
    static boolean sortColumnsByBranchesRank = true;

    static List<Column> sortCommitsIntoColumns(List<Branch> branches, List<? extends Commit> commits) {
        Column theParentColumn = Column.createNewList();

        final int[] branchId = {0};
        for (int i = 0; i < commits.size(); i++) {
            Commit revCommit = commits.get(i);
            if (i % 100 == 0)
                System.out.println("Calculating entry: " + i + " of " + commits.size());
            List<Branch> commitsBranches = branches.stream().filter(b -> b.commit == revCommit).collect(Collectors.toList());
            calculateEntryForCommit(revCommit, commitsBranches, theParentColumn, branchId, i);
        }


        return theParentColumn.getColumnStream().collect(Collectors.toList());
    }

    static void sortSecondaryDroppingIntoTheDirectionOfTheirParent(List<Column> columns) {
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            // fixme later: better repair dangling columns before entering this function.
            if (column.rank == null || ((Column.ReferenceInfos) column.rank).parent == null) {
                int finalI = i;
                column.rank = () -> finalI;
            }
        }

        columns.sort(Comparator.comparing((Column c) -> c.cluster.cc.id).thenComparing(c -> c.rank.getAsDouble()));
    }

    private static void calculateEntryForCommit(Commit revCommit, List<Branch> branches, Column theParentColumn, int[] branchId, int commitId) {
        Map<Commit, Map<Boolean, List<Column>>> columnsWithDanglingParents = theParentColumn.getColumnStream()
                .filter(c -> !c.entries.isEmpty())
                .filter(c -> c.getLastEntry().parent != null)
                .collect(Collectors.groupingBy(column -> column.getLastEntry().parent,
                        Collectors.partitioningBy(column -> column.getLastEntry().joinedForSameParent.stream().anyMatch(hh -> hh.typeOfParent.isMainNode()))));

        List<HistoryEntry> mainReferencingEntries = columnsWithDanglingParents.containsKey(revCommit) ? columnsWithDanglingParents.get(revCommit).get(true).stream().map(Column::getLastEntry).collect(Collectors.toList()) : new ArrayList<>();
        List<HistoryEntry> secondaryReferencingEntries = columnsWithDanglingParents.containsKey(revCommit) ? columnsWithDanglingParents.get(revCommit).get(false).stream().map(Column::getLastEntry).collect(Collectors.toList()) : new ArrayList<>();
        long numberOfMainReferencingEntries = mainReferencingEntries.size();

        boolean commitIsLabeledByABranch = !branches.isEmpty();

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
        TypeOfBackReference backReferenceForFirstParent = TypeOfBackReference.YES;
        if (!(needNewMainBranchBecauseLabeled || needBranchBecauseMultiParent || alwaysCreateNewColumns))
            // try to join a referencing column if new branch is not required
            columnForFirstParent = Stream.of(mainReferencingEntries, secondaryReferencingEntries)
                    .flatMap(Collection::stream)
                    .findFirst()
                    .map(h -> h.column)
                    .orElse(null);

        // maybe: if branch an important one, it may not be sorted after not important branches, so check that first
        if (sortColumnsByBranchesRank) {
            OptionalInt myRank = branches.stream().mapToInt(b -> b.ranking).min();
            if (myRank.isPresent()) {
                List<Column> allColumns = theParentColumn.getColumnStream().collect(Collectors.toList());
                Optional<Column> higherRankColumn = allColumns.stream()
                        .filter(c -> {
                            // this is an error to check only the first entry in column, because column might get a main column later. we need to check the minimal rank of all labled

                            // get the column, that is higher rank than one of the actual. (the columns that have to be after current)
                            if (c.entries.isEmpty() || !c.entries.peekFirst().typeOfParent.isMainNode())
                                return false;

                            OptionalInt otherRank = c.entries.peekFirst().branches.stream().mapToInt(b -> b.ranking).min();
                            if (!otherRank.isPresent())
                                return true;

                            return otherRank.getAsInt() > myRank.getAsInt();
                        })
                        .findFirst();
                if (higherRankColumn.isPresent()) {
                    // override the initial selection. The rank requires new column before the selected.
                    if (columnForFirstParent == null || allColumns.indexOf(columnForFirstParent) > allColumns.indexOf(higherRankColumn.get())) {
                        columnForFirstParent = higherRankColumn.get().createSubColumnBefore(branchId[0]);
                        backReferenceForFirstParent = TypeOfBackReference.NO;
                        branchId[0]++;
                    }
                }
            }
        }

        // if needNewMainBranchBecauseLabeled stil may use column of secondary references
        if (columnForFirstParent == null && needNewMainBranchBecauseLabeled) {
            columnForFirstParent = secondaryReferencingEntries.stream().findFirst().map(h -> h.column).orElse(null);
        }

        if (columnForFirstParent == null)
            backReferenceForFirstParent = TypeOfBackReference.NO;


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
        HistoryEntry theMainEntry = CommitStorage.newEntryForParent(revCommit, revCommit.getParentCount() > 0 ? revCommit.getMyParent(0) : null, columnForFirstParent, backReferenceForFirstParent, commitId, branches);
        // clear the rank for main columns for the case column was a secondary before.
        columnForFirstParent.rank = null;


        Column finalColumnForFirstParent = columnForFirstParent;
        Stream.of(mainReferencingEntries, secondaryReferencingEntries).flatMap(Collection::stream).filter(c -> c.column != finalColumnForFirstParent).forEach(h -> {
            // here we have a column having history entries waiting for this commit so we create a backreference
            CommitStorage.newEntryBackReferenceWithoutParent(revCommit, h.column, commitId);

            // update reference infos
            addParent(h.column, theMainEntry);
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
                            columnToUse = Optional.of(columnForSecondary(mainDroppingColumns.get(0).createSubColumnBefore(branchId[0]++)));
                        } else {
                            columnToUse = Stream.of(
                                    columnsWithDanglingParents.get(parent).get(false),
                                    mayJoinDroppingMainColumn ? columnsWithDanglingParents.get(parent).get(true) : Collections.<Column>emptyList())
                                    .flatMap(Collection::stream).findFirst();
                        }

                        if (columnToUse.isPresent()) {
                            CommitStorage.newEntryForParent(revCommit, parent, columnToUse.get(), TypeOfBackReference.NO, commitId);
                            joined = true;
                            addChild(columnToUse.get(), finalColumnForFirstParent);
                        }
                    }

                    if (!joined) {
                        increase.set(true);
                        Column column = columnForSecondary(theParentColumn.createSubColumn(branchId[0]));
                        CommitStorage.newEntryForParent(revCommit, parent, column, TypeOfBackReference.NO, commitId);
                        addChild(column, finalColumnForFirstParent);
                        ((Column.ReferenceInfos) column.rank).children.add(finalColumnForFirstParent);
                    }
                });

        if (increase.get())
            branchId[0]++;
    }

    private static void addChild(Column column, Column child) {
        if (column.rank != null) {
            ((Column.ReferenceInfos) column.rank).children.add(child);
        }

        column.cluster.join(child.cluster);
    }

    private static void addParent(Column column, HistoryEntry parent) {
        if (column.rank != null) {
            ((Column.ReferenceInfos) column.rank).parent = parent;
        }

        column.cluster.join(parent.column.cluster);
    }

    private static Column columnForSecondary(Column column) {
        column.rank = new Column.ReferenceInfos();
        return column;
    }

    public static void assignClusterIds(List<Column> columns) {
        IdentityHashMap<Column.ClusterCollector, List<Column>> clusters = columns.stream().collect(Collectors.groupingBy(c -> c.cluster.cc, IdentityHashMap::new, Collectors.toList()));
        System.out.println("Number of clusters: " + clusters.keySet().size());
        List<Column.ClusterCollector> sortedClusters = clusters.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Column.ClusterCollector, List<Column>> e) -> e.getValue().get(0).entries.stream().findFirst().map(ee -> ee.commitId).orElse(0)))
                .map(Map.Entry::getKey).collect(Collectors.toList());
        AtomicInteger clusterId = new AtomicInteger();
        sortedClusters.forEach(c -> c.id = clusterId.getAndIncrement());
    }
}

