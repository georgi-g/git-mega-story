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
            PersonIdent authorIdent = revCommit.getAuthorIdent();
//            System.out.println("author " + authorIdent.getName());
//            System.out.println("message " + revCommit.getShortMessage());
//            System.out.println("fullMessage " + revCommit.getFullMessage());
        });


        LinkedList<HistoryEntry> ll = new LinkedList<>();

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
        master.forEach(revCommit -> {
            final boolean[] inserted = {false};

            new ArrayList<>(ll).forEach(h -> {
                if (revCommit == h.parent) {
                    if (true) {
                        //if (revCommit.getParentCount() <= 1) {
                        if (!inserted[0]) {
                            newEntry(ll, revCommit, h.column);
                            inserted[0] = true;
                        } else {
                            newEntryBackReference(ll, revCommit, h.column);
                        }
                    } else {
                        newEntryBackReference(ll, revCommit, h.column);
                    }
                }
            });

            if (!inserted[0])
                newEntry(ll, revCommit, theParentColumn.createSubColumn(branchId[0]++));


//            List<Ref> containingBranches = branches.stream().filter(b -> b.getObjectId().equals(revCommit.toObjectId())).collect(Collectors.toList());
//            containingBranches.forEach(b -> {
//                newEntry(ll, revCommit, theParentColumn.createSubColumn(branchId[0]++));
//            });

        });


        List<Column> columns = theParentColumn.getColumnStream().collect(Collectors.toList());

        columns.forEach(c -> {
            System.out.print(c.branchId);
        });
        System.out.println("  ");


        Set<Object> columnsHavingCommits = new HashSet<>();

        List<Deque<HistoryEntry>> droppingColumns = columns.stream().map(c -> new ArrayDeque<>(c.entries)).collect(Collectors.toList());

        master.forEach(revCommit -> {
            boolean commitDidAppear = false;
            StringBuilder brancPic = new StringBuilder();
            for (Deque<HistoryEntry> c : droppingColumns) {

                HistoryEntry historyEntry = !c.isEmpty() && c.peek().commit == revCommit ? c.poll() : null;

                if (historyEntry != null) {
                    commitDidAppear = true;
                    columnsHavingCommits.add(c);


                    switch (historyEntry.typeOfEntry) {
                        case NORMAL:
                            brancPic.append("┿");
                            break;
                        case BACK_REFERENCE:
                            brancPic.append("╰");
                            break;
                        case INITIAL:
                            brancPic.append("\u2537");
                            break;
                        case MERGE_STH:
                            brancPic.append("\u256e");
                            break;
                        case MERGE_MAIN:
                            brancPic.append("\u2523");
                            break;
                    }

//                    if (first)
//                        System.out.print("\u251c");
//                    else
//                        System.out.print("\u256e");
                    //System.out.print(c.branchId);
                } else {
                    if (!c.isEmpty() && columnsHavingCommits.contains(c))
                        brancPic.append("│");
                    else {
                        brancPic.append(" ");
                    }
                }
            }
            if (!commitDidAppear)
                throw new RuntimeException("Commit did not Appear");
            String branchesLine = brancPic.toString();
            branchesLine = new NiceReplacer().fulReplace(branchesLine);

            System.out.print(branchesLine + "  " + revCommit.getId().getName());
            System.out.println(" " + branches.stream().filter(b -> b.getObjectId().equals(revCommit.getId())).map(Ref::getName).collect(Collectors.joining(" ")));
        });
        if (droppingColumns.stream().anyMatch(c -> !c.isEmpty())) {
            throw new RuntimeException("Not all commits were consumed.");
        }

        /*
         * BB | B
         * x  |
         *  x | x
         * x
         * xx
         * xx
         * xx
         */

    }

    private static void newEntryBackReference(LinkedList<HistoryEntry> ll, RevCommit revCommit, Column theColumn) {
        HistoryEntry historyEntry = new HistoryEntry(theColumn);
        historyEntry.commit = revCommit;
        historyEntry.typeOfEntry = TypeOfEntry.BACK_REFERENCE;
        ll.add(historyEntry);
    }

    private static void newEntry(LinkedList<HistoryEntry> ll, RevCommit revCommit, Column theColumn) {
        if (revCommit.getParents().length > 1) {
            AtomicBoolean usedColumn = new AtomicBoolean(false);
            Arrays.stream(revCommit.getParents())
                    .forEach(p -> {
                        TypeOfEntry typeOfEntry = usedColumn.get() ? TypeOfEntry.MERGE_STH : TypeOfEntry.MERGE_MAIN;
                        Column column = usedColumn.getAndSet(true) ? theColumn.createSubColumn(theColumn.branchId) : theColumn;

                        HistoryEntry historyEntry = new HistoryEntry(column);
                        historyEntry.typeOfEntry = typeOfEntry;
                        historyEntry.commit = revCommit;
                        historyEntry.parent = p;
                        ll.add(historyEntry);
                    });
        } else if (revCommit.getParents().length == 1) {
            RevCommit p = revCommit.getParents()[0];
            HistoryEntry historyEntry = new HistoryEntry(theColumn);
            historyEntry.commit = revCommit;
            historyEntry.typeOfEntry = TypeOfEntry.NORMAL;
            historyEntry.parent = p;
            ll.add(historyEntry);
        } else {
            HistoryEntry historyEntry = new HistoryEntry(theColumn);
            historyEntry.commit = revCommit;
            historyEntry.typeOfEntry = TypeOfEntry.INITIAL;
            ll.add(historyEntry);
        }
    }

    private enum TypeOfEntry {
        NORMAL, INITIAL, MERGE_STH, MERGE_MAIN, BACK_REFERENCE
    }

    private static class HistoryEntry {
        RevCommit commit;
        RevCommit parent;
        final Column column;
        TypeOfEntry typeOfEntry;

        private HistoryEntry(Column column) {
            this.column = column;
            column.appendEntry(this);
        }
    }

    private static class Column {
        int branchId;
        List<Column> subColumns = new ArrayList<>();
        Column nextColumn;
        List<HistoryEntry> entries = new ArrayList<>();

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
    }

}
