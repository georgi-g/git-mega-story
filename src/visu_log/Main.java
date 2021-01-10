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

        List<RevCommit> master = StreamSupport.stream(revWalk.spliterator(), false).collect(Collectors.toList());
//        revWalk.iterator().forEachRemaining(revCommit -> {
//            System.out.println("from rev walk: " + revCommit.getId().getName());
//        });

//        master.sort((o1, o2) -> {
//            try {
//                RevCommit parsed1 = revWalk.parseCommit(o1.getId());
//                RevCommit parsed2 = revWalk.parseCommit(o2.getId());
//                if (revWalk.isMergedInto(parsed2, parsed1))
//                    return -1;
//                if (revWalk.isMergedInto(parsed1, parsed2))
//                    return +1;
//                return 0;
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });

        //noinspection ConstantConditions
        if (true) {
            master.sort((o1, o2) -> {
                try {
                    if (revWalk.isMergedInto(o2, o1))
                        return -1;
                    if (revWalk.isMergedInto(o1, o2))
                        return +1;
                    if (o1.getParents().length == 1 && o2.getParents().length > 1) {
                        for (int i = 1; i < o2.getParents().length; i++) {
                            if (o2.getParent(i) == o1.getParent(0))
                                return -1;
                        }
                    } else if (o2.getParents().length == 1 && o1.getParents().length > 1) {
                        for (int i = 1; i < o1.getParents().length; i++) {
                            if (o1.getParent(i) == o2.getParent(0))
                                return 1;
                        }
                    }

                    return 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

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


        Column theParentColumn = new Column();

//        for (int i = 0; i < branches.size(); i++) {
//            Ref b = branches.get(i);
//            HistoryEntry e = new HistoryEntry();
//            e.column = new Column();
//            e.column.branchId = i;
//            e.commit = repository.parseCommit(b.getObjectId());
//            ll.add(e);
//        }

//        for (int i = 1; i < ll.size(); i++) {
//            ll.get(i-1).column.nextColumn = ll.get(i).column;
//        }

        final int[] branchId = {0};
        for (int i = 0, masterSize = master.size(); i < masterSize; i++) {
            RevCommit revCommit = master.get(i);
            calculateEntryForCommit(revCommit, branches, theParentColumn, branchId, i);
        }


        List<Column> columns = theParentColumn.getColumnStream().collect(Collectors.toList());

        ArrayList<List<HistoryEntry>> table = createTableFromDroppingColumns(columns);

        StringifiedGraph graph = printGraph(branches, table);

        System.out.println(graph.header);

        graph.rows.forEach(r -> System.out.println(r.branchesLine + "  " + r.description));

        SvgDrawing.createSvg(table);
    }

    private static void calculateEntryForCommit(RevCommit revCommit, List<Ref> branches, Column theParentColumn, int[] branchId, int commitId) {
        //noinspection ConstantConditions
        Map<RevCommit, List<Column>> columnsWithDanglingParents = theParentColumn.getColumnStream()
                .filter(c -> c.entries.size() > 0)
                .filter(c -> c.entries.peekLast().parent != null)
                .collect(Collectors.groupingBy(c -> c.entries.peekLast().parent));

        List<HistoryEntry> referencingEntries = columnsWithDanglingParents.getOrDefault(revCommit, new ArrayList<>()).stream().map(Column::getLastEntry).collect(Collectors.toList());

        boolean commitIsLabeledByABranch = branches.stream().anyMatch(b -> b.getObjectId().equals(revCommit.getId()));


        boolean alwaysCreateNewColumnsForEachParentOfAMultiParentCommit = false;
        boolean alwaysCreateNewColumns = false;
        boolean joinDroppingColumns = true;
        boolean forceCreateNewColumnsForLabeledCommits = false;

        final boolean[] alreadyReused = {false};
        Set<RevCommit> usedParents = new HashSet<>();
        referencingEntries.forEach(h -> {
            //noinspection ConstantConditions
            boolean reuseColumn = !alreadyReused[0] &&
                    (revCommit.getParentCount() <= 1 || !alwaysCreateNewColumnsForEachParentOfAMultiParentCommit);

            //noinspection ConstantConditions
            reuseColumn &= !(forceCreateNewColumnsForLabeledCommits && commitIsLabeledByABranch && (h.typeOfParent != TypeOfParent.MERGE_STH && h.typeOfParent != TypeOfParent.SINGLE_PARENT));
            // here we have a column having history entries waiting for this commit
            //noinspection ConstantConditions
            if (reuseColumn && !alwaysCreateNewColumns) {
                //    newEntry(ll, revCommit, h.column);
                RevCommit parent = revCommit.getParentCount() > 0 ? revCommit.getParent(0) : null;
                if (parent != null)
                    usedParents.add(parent);
                newEntryForParent(revCommit, parent, h.column, TypeOfBackReference.YES, commitId);
                alreadyReused[0] = true;
            } else {
                // here we create a backreference, since we found a column waiting for this commit
                newEntryBackReferenceWithoutParent(revCommit, h.column, commitId);
            }
        });

        //noinspection ConstantConditions
        if (joinDroppingColumns) {
            Arrays.stream(revCommit.getParents())
                    .filter(p -> !usedParents.contains(p))
                    .filter(p -> revCommit.getParent(0) != p)
                    .filter(columnsWithDanglingParents::containsKey)
                    .forEach(parent -> {
                        Column dangling = columnsWithDanglingParents.get(parent).get(0);
                        newEntryForParent(revCommit, parent, dangling, TypeOfBackReference.YES, commitId);
                        usedParents.add(parent);
                    });
        }


        // here we create new column for each entry, so no history entries in column waiting for it
        if (revCommit.getParentCount() > 0) {
            boolean increase = false;
            for (RevCommit parent : revCommit.getParents()) {
                if (!usedParents.contains(parent)) {
                    increase = true;
                    newEntryForParent(revCommit, parent, theParentColumn.createSubColumn(branchId[0]), TypeOfBackReference.NO, commitId);
                }
            }
            if (increase)
                branchId[0]++;
        } else {
            if (!alreadyReused[0]) {
                newEntryForParent(revCommit, null, theParentColumn.createSubColumn(branchId[0]), TypeOfBackReference.NO, commitId);
                branchId[0]++;
            }
        }
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
        HistoryEntry historyEntry = new HistoryEntry(theColumn, commitId);
        historyEntry.commit = revCommit;
        historyEntry.backReference = TypeOfBackReference.YES;
        historyEntry.typeOfParent = TypeOfParent.NONE;
    }

    private static void newEntryForParent(RevCommit revCommit, RevCommit parent, Column theColumn, TypeOfBackReference backReference, int commitId) {
        HistoryEntry historyEntry = new HistoryEntry(theColumn, commitId);
        historyEntry.commit = revCommit;

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
        historyEntry.parent = parent;
    }

    public enum TypeOfParent {
        SINGLE_PARENT("┿", "┯", true),
        INITIAL("┷", "╸", true),
        MERGE_STH("╭", "╮", false),
        MERGE_MAIN("┣", "┏", true),
        NONE("╰", "x", false);

        private final String withBackReference;
        private final String withoutBackReference;
        private final boolean isMainNode;

        TypeOfParent(String withBackReference, String withoutBackReference, boolean isMainNode) {
            this.withBackReference = withBackReference;
            this.withoutBackReference = withoutBackReference;
            this.isMainNode = isMainNode;
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
        public RevCommit commit;
        public RevCommit parent;
        public final Column column;
        public final int commitId;
        public TypeOfBackReference backReference;
        public TypeOfParent typeOfParent;

        private HistoryEntry(Column column, int commitId) {
            this.column = column;
            this.commitId = commitId;
            column.appendEntry(this);
        }
    }

    private static class Column {
        int branchId;
        List<Column> subColumns = new ArrayList<>();
        Column nextColumn;
        Deque<HistoryEntry> entries = new ArrayDeque<>();

        Stream<Column> getColumnStream() {
            if (nextColumn != null)
                return Stream.of(
                        Stream.of(this),
                        subColumns.stream().flatMap(Column::getColumnStream),
                        nextColumn.getColumnStream())
                        .flatMap(s -> s);
            else
                return Stream.of(
                        Stream.of(this),
                        subColumns.stream().flatMap(Column::getColumnStream))
                        .flatMap(s -> s);
        }

        Column createSubColumn(int branchId) {
            Column sc = new Column();
            sc.branchId = branchId;
            subColumns.add(sc);
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