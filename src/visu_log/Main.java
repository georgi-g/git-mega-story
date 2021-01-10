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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
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

        System.out.println("Retrieve All commits");
        List<RevCommit> master = StreamSupport.stream(revWalk.spliterator(), false).limit(2000).collect(Collectors.toList());

        System.out.println("Sorting everything");
        master.sort(new CommitComparator(revWalk, false));

        System.out.println("Log fetched");


        printAllCommits(master);

        System.out.println("Sort Commits into Columns");
        List<Column> columns = sortCommitsIntoColumns(branches, master);

        System.out.println("Create Table from dropping Columns");
        ArrayList<List<HistoryEntry>> table = createTableFromDroppingColumns(columns);
        System.out.println("Rewrite secondary dropping");
        rewriteSecondaryDropping(table);

        System.out.println("create simplified graph");
        SimpleTextBasedGraph.StringifiedGraph graph = SimpleTextBasedGraph.printGraph(branches, table);

        System.out.println(graph.header);

        PrintStream printWriter = new PrintStream(System.out, true, "UTF-8");
        graph.rows.forEach(r -> printWriter.println(r.branchesLine + "  " + r.description));
        printWriter.flush();
        //graph.rows.forEach(r -> System.out.println(r.branchesLine + "  " + r.description));

        System.out.println("compress table");
        compressTable(table);

        System.out.println("create create svg");
        String svg = SvgDrawing.createSvg(table, branches);

        try (FileWriter b = new FileWriter(new File("mega-story.html"))) {
            b.write(svg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Column> sortCommitsIntoColumns(List<Ref> branches, List<RevCommit> commits) {
        Column theParentColumn = Column.createNewList();

        final int[] branchId = {0};
        for (int i = 0; i < commits.size(); i++) {
            RevCommit revCommit = commits.get(i);
            if (i % 100 == 0)
                System.out.println("Calculating entry: " + i + " of " + commits.size());
            calculateEntryForCommit(revCommit, branches, theParentColumn, branchId, i);
        }


        return theParentColumn.getColumnStream().collect(Collectors.toList());
    }

    private static void printAllCommits(List<RevCommit> commits) {
        commits.forEach(revCommit -> {
            System.out.println("Commit Name " + revCommit.getId().getName());
//            System.out.println(revCommit.getId());
//            System.out.println(revCommit.getId().abbreviate(8).name());
//            System.out.println(revCommit.getName());
            for (RevCommit parent : revCommit.getParents()) {
                System.out.println("- Parent Name " + parent.getId().getName());
            }
            //noinspection unused
            PersonIdent authorIdent = revCommit.getAuthorIdent();
//            System.out.println("author " + authorIdent.getName());
//            System.out.println("message " + revCommit.getShortMessage());
//            System.out.println("fullMessage " + revCommit.getFullMessage());
        });
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
            nextIsLabeled |= next.get(i) != null && next.get(i).isLabeled;
            prevIsLabeled |= previous.get(i) != null && previous.get(i).isLabeled;
        }

        return rowIsJoinable && !(nextIsLabeled && prevIsLabeled);
    }

    private static void compressTable(ArrayList<List<HistoryEntry>> table) {
        if (table.size() < 2) {
            return;
        }

        HistoryEntry dummyEntry = new HistoryEntry(null, Column.createNewList(), -1);

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

}

