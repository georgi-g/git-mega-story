package visu_log;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Main {

    public static void main(String[] args) throws IOException, GitAPIException {
        Git g = Git.open(args.length > 0 ? new File(args[0]) : new File("."));
        Repository repository = g.getRepository();
        System.out.println("Loaded ...");
        List<Ref> branches = g.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
        for (Ref branch : branches) {
            System.out.println("Branch:" + branch);
//            System.out.println(" Name: " + branch.getName());
//            System.out.println(" ID: " + branch.getObjectId());
//            System.out.println(" Peeled: " + branch.getPeeledObjectId());
        }

        //Iterable<RevCommit> master = g.log().add(repository.resolve("test-head")).call();
        //List<RevCommit> master = StreamSupport.stream(g.log().all().call().spliterator(), false).collect(Collectors.toList());

        final RevWalk revWalk = new RevWalk(repository);

        revWalk.markStart(branches.stream()
                .map(Ref::getObjectId)
                .map(b -> {
                    try {
                        return revWalk.parseCommit(b);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList()));
        revWalk.sort(RevSort.TOPO);

        //noinspection FuseStreamOperations
        List<RevCommit> master = StreamSupport.stream(revWalk.spliterator(), false).collect(Collectors.toList());

        master.sort(new CommitComparator(revWalk, false));

        System.out.println("Log fetched");
//        master.forEach(revCommit ->
//        {
//            System.out.println(revCommit.getId());
//            System.out.println(revCommit.getName());
//            System.out.println(revCommit.getId().abbreviate(8).name());
//            System.out.println(revCommit.getId().getName());
//            System.out.println(revCommit.getShortMessage());
//        });


        master.forEach(revCommit -> {
            System.out.println("Commit Name " + revCommit.getId().getName());
            for (RevCommit parent : revCommit.getParents()) {
                System.out.println("- Parent Name " + parent.getId().getName());
            }
            //noinspection unused
            PersonIdent authorIdent = revCommit.getAuthorIdent();
//            System.out.println("author " + authorIdent.getName());
//            System.out.println("message " + revCommit.getShortMessage());
//            System.out.println("fullMessage " + revCommit.getFullMessage());
        });


        Column theParentColumn = Column.createNewList();

        final int[] branchId = {0};
        for (int i = 0, masterSize = master.size(); i < masterSize; i++) {
            RevCommit revCommit = master.get(i);
            calculateEntryForCommit(revCommit, branches, theParentColumn, branchId, i);
        }


        List<Column> columns = theParentColumn.getColumnStream().collect(Collectors.toList());

        ArrayList<List<HistoryEntry>> table = createTableFromDroppingColumns(columns);
        rewriteSecondaryDropping(table);
        compressTable(table);

        StringifiedGraph graph = printGraph(branches, table);

        System.out.println(graph.header);

        graph.rows.forEach(r -> System.out.println(r.branchesLine + "  " + r.description));

        SvgDrawing.createSvg(table, branches);
    }

    static boolean alwaysCreateNewColumnsForEachParentOfAMultiParentCommit = false;
    static boolean alwaysCreateNewColumns = false;
    static boolean joinDroppingColumns = true;
    static boolean mayJoinDroppingMainColumn = false;
    static boolean forceCreateNewColumnsForLabeledCommits = true;

    private static void calculateEntryForCommit(RevCommit revCommit, List<Ref> branches, Column theParentColumn, int[] branchId, int commitId) {
        //noinspection ConstantConditions
        Map<RevCommit, Map<Boolean, List<Column>>> columnsWithDanglingParents = theParentColumn.getColumnStream()
                .filter(c -> c.entries.size() > 0)
                .filter(c -> c.entries.peekLast().parent != null)
                .collect(Collectors.groupingBy(c -> c.entries.peekLast().parent,
                        Collectors.partitioningBy(h -> h.getLastEntry().joinedForSameParent.stream().anyMatch(hh -> hh.typeOfParent.isMainNode()))));

        List<HistoryEntry> mainReferencingEntries = columnsWithDanglingParents.containsKey(revCommit) ? columnsWithDanglingParents.get(revCommit).get(true).stream().map(Column::getLastEntry).collect(Collectors.toList()) : new ArrayList<>();
        List<HistoryEntry> secondaryReferencingEntries = columnsWithDanglingParents.containsKey(revCommit) ? columnsWithDanglingParents.get(revCommit).get(false).stream().map(Column::getLastEntry).collect(Collectors.toList()) : new ArrayList<>();
        long numberOfMainReferencingEntries = mainReferencingEntries.size();

        boolean commitIsLabeledByABranch = branches.stream().anyMatch(b -> b.getObjectId().equals(revCommit.getId()));

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
        newEntryForParent(revCommit, revCommit.getParentCount() > 0 ? revCommit.getParent(0) : null, columnForFirstParent, backReferenceForFirstParent, commitId, commitIsLabeledByABranch);


        Column finalColumnForFirstParent = columnForFirstParent;
        Stream.of(mainReferencingEntries, secondaryReferencingEntries).flatMap(Collection::stream).filter(c -> c.column != finalColumnForFirstParent).forEach(h -> {
            // here we have a column having history entries waiting for this commit so we create a backreference
            newEntryBackReferenceWithoutParent(revCommit, h.column, commitId);
        });

        // secondary parents may join the dropping columns
        AtomicBoolean increase = new AtomicBoolean(false);
        Arrays.stream(revCommit.getParents())
                .filter(p -> revCommit.getParent(0) != p)
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
                            newEntryForParent(revCommit, parent, columnToUse.get(), TypeOfBackReference.NO, commitId, false);
                            joined = true;
                        }
                    }

                    if (!joined) {
                        increase.set(true);
                        newEntryForParent(revCommit, parent, theParentColumn.createSubColumn(branchId[0]), TypeOfBackReference.NO, commitId, false);
                    }
                });

        if (increase.get())
            branchId[0]++;
    }

    private static ArrayList<List<HistoryEntry>> createTableFromDroppingColumns(List<Column> columns) {
        List<Deque<HistoryEntry>> droppingColumns = columns.stream().map(c -> new ArrayDeque<>(c.entries)).collect(Collectors.toList());

        ArrayList<List<HistoryEntry>> table = new ArrayList<>();
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
            if (!commitsFound)
                break;
            table.add(entries);
        }
        return table;
    }

    private static void rewriteSecondaryDropping(ArrayList<List<HistoryEntry>> table) {
        Integer[] moves = new Integer[table.get(0).size()];

        for (int lineId = table.size() - 1; lineId >= 0; lineId--) {
            List<HistoryEntry> row = table.get(lineId);
            int mainNodeColumn = findMainNode(row);

            for (int i = 0; i < row.size(); i++) {
                // clear if something is in there
                if (moves[i] != null && row.get(moves[i]) != null) {
                    moves[i] = null;
                }
                // move else
                else if (moves[i] != null && row.get(i) != null) {
                    // move if it is merge_sth
                    if (row.get(i).typeOfParent == TypeOfParent.MERGE_STH) {
                        row.set(moves[i], row.get(i));
                        row.set(i, null);
                    }
                    // everything else blocks other moves
                    else {
                        moves[i] = null;
                    }
                }
            }

            for (int i = 0; i < row.size(); i++) {
                if (i != mainNodeColumn && row.get(i) != null && row.get(i).backReference == TypeOfBackReference.YES) {
                    moves[i] = mainNodeColumn;
                }
            }
        }

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

                    parentIsPresent[i] = e.typeOfParent.hasParent;
                }
            }
        }
    }

    private static void compressTable(ArrayList<List<HistoryEntry>> table) {
        if (table.size() < 2) {
            return;
        }

        next_row:
        for (ListIterator<List<HistoryEntry>> it = table.listIterator(1); it.hasNext(); ) {
            List<HistoryEntry> previous = it.previous();
            it.next();
            List<HistoryEntry> next = it.next();

            long numberOfEntriesInMyRow = next.stream().filter(Objects::nonNull).count();
            boolean previousMayBeJoined = previous.stream().filter(Objects::nonNull).allMatch(e -> e.typeOfParent == TypeOfParent.SINGLE_PARENT || e.typeOfParent == TypeOfParent.INITIAL);

            if (numberOfEntriesInMyRow > 1 || !previousMayBeJoined)
                continue;

            for (int i = 0; i < next.size(); i++) {
                HistoryEntry entry = next.get(i);
                if (entry != null && previous.get(i) == null && !entry.isLabeled) {
                    previous.set(i, entry);
                    it.remove();
                    continue next_row; // because I wanted to use this
                }
            }
        }


        for (int row = 0; row < table.size(); row++) {
            List<HistoryEntry> rowEntries = table.get(row);
            for (HistoryEntry e : rowEntries) {
                if (e != null)
                    e.commitId = row;
            }
        }
    }

    private static int findMainNode(List<Main.HistoryEntry> row) {
        for (int parentColumn = 0; parentColumn < row.size(); parentColumn++) {
            Main.HistoryEntry parent = row.get(parentColumn);
            if (parent != null && parent.typeOfParent.isMainNode()) {
                return parentColumn;
            }
        }
        return -1;
    }

    private static StringifiedGraph printGraph(List<Ref> branches, ArrayList<List<HistoryEntry>> table) {

        StringifiedGraph graph = new StringifiedGraph();

        //graph.header = columns.stream().map(c -> Integer.toString(c.branchId)).collect(Collectors.joining("  "));
        graph.header = "no header today";


        Set<Object> columnsHavingCommits = new HashSet<>();

        //List<Deque<HistoryEntry>> droppingColumns = columns.stream().map(c -> new ArrayDeque<>(c.entries)).collect(Collectors.toList());

        for (List<HistoryEntry> entries : table) {
            HistoryEntry someEntry = null;
            StringBuilder brancPic = new StringBuilder();
            for (int c = 0; c < entries.size(); c++) {
                HistoryEntry historyEntry = entries.get(c);

                if (historyEntry != null) {
                    someEntry = historyEntry;
                    if (historyEntry.typeOfParent == TypeOfParent.INITIAL || historyEntry.typeOfParent == TypeOfParent.NONE)
                        columnsHavingCommits.remove(c);
                    else
                        columnsHavingCommits.add(c);

                    brancPic.append(historyEntry.typeOfParent.getSymbol(historyEntry.backReference));

                } else {
                    if (columnsHavingCommits.contains(c))
                        brancPic.append("│");
                    else {
                        brancPic.append(" ");
                    }
                }
                brancPic.append("  ");
            }

            HistoryEntry finalSomeEntry = someEntry;
            if (someEntry == null)
                throw new RuntimeException("Commit did not Appear");
            String branchesLine = brancPic.toString();
            branchesLine = new NiceReplacer().fulReplace(branchesLine);

            StringifiedGraph.Row r = new StringifiedGraph.Row();
            r.branchesLine = branchesLine;
            r.description = someEntry.commitId + " " + someEntry.commit.getId().getName() + " " + branches.stream().filter(b -> b.getObjectId().equals(finalSomeEntry.commit.getId())).map(Ref::getName).collect(Collectors.joining(" "));
            graph.rows.add(r);
        }

        return graph;
    }

    private static void newEntryBackReferenceWithoutParent(RevCommit revCommit, Column theColumn, int commitId) {
        HistoryEntry historyEntry = new HistoryEntry(revCommit, theColumn, commitId);
        historyEntry.backReference = TypeOfBackReference.YES;
        historyEntry.typeOfParent = TypeOfParent.NONE;
    }

    private static void newEntryForParent(RevCommit revCommit, RevCommit parent, Column theColumn, TypeOfBackReference backReference, int commitId, boolean isLabeled) {
        HistoryEntry historyEntry = new HistoryEntry(revCommit, theColumn, commitId, parent, isLabeled);

        switch (revCommit.getParentCount()) {
            default:
                historyEntry.typeOfParent = parent == revCommit.getParent(0) ? TypeOfParent.MERGE_MAIN : TypeOfParent.MERGE_STH;
                break;
            case 1:
                historyEntry.typeOfParent = TypeOfParent.SINGLE_PARENT;
                break;
            case 0:
                historyEntry.typeOfParent = TypeOfParent.INITIAL;
                break;
        }

        historyEntry.backReference = backReference;
    }

    public enum TypeOfParent {
        SINGLE_PARENT("┿", "┯", true, true),
        INITIAL("┷", "╸", true, false),
        MERGE_STH("╭", "╭", false, true),
        MERGE_MAIN("┣", "┏", true, true),
        NONE("╰", "x", false, false);

        private final String withBackReference;
        private final String withoutBackReference;
        private final boolean isMainNode;
        private final boolean hasParent;

        TypeOfParent(String withBackReference, String withoutBackReference, boolean isMainNode, boolean hasParent) {
            this.withBackReference = withBackReference;
            this.withoutBackReference = withoutBackReference;
            this.isMainNode = isMainNode;
            this.hasParent = hasParent;
        }

        boolean isMainNode() {
            return isMainNode;
        }

        String getSymbol(TypeOfBackReference backReference) {
            switch (backReference) {
                case NO:
                    return withoutBackReference;
                case YES:
                default:
                    return withBackReference;
            }
        }
    }

    private enum TypeOfBackReference {
        YES, NO
    }

    public static class HistoryEntry {
        public final RevCommit commit;
        public final RevCommit parent;
        public final Column column;
        public int commitId;
        public TypeOfBackReference backReference;
        public TypeOfParent typeOfParent;
        public List<HistoryEntry> joinedForSameParent = new ArrayList<>();
        public final boolean isLabeled;

        private HistoryEntry(RevCommit commit, Column column, int commitId) {
            this.commit = commit;
            this.column = column;
            this.commitId = commitId;
            this.parent = null;
            column.appendEntry(this);
            joinedForSameParent.add(this);
            isLabeled = false;
        }

        private HistoryEntry(RevCommit commit, Column column, int commitId, RevCommit parent, boolean isLabeled) {
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

    private static class Column {
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
            entries.add(he);
        }

        public HistoryEntry getLastEntry() {
            if (entries.size() > 0)
                return entries.peekLast();
            throw new RuntimeException("Column is Empty");
        }
    }

}

class StringifiedGraph {
    String header;
    List<Row> rows = new ArrayList<>();

    static class Row {
        String branchesLine;
        String description;
    }

}