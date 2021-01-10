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

            List<HistoryEntry> referencingEntries = theParentColumn.getColumnStream()
                    .map(c -> c.entries.peekLast())
                    .filter(Objects::nonNull)
                    .filter(e -> e.parent == revCommit)
                    .collect(Collectors.toList());

            Map<RevCommit, List<HistoryEntry>> danglingParents = theParentColumn.getColumnStream()
                    .map(c -> c.entries.peekLast())
                    .filter(Objects::nonNull)
                    .filter(e -> Objects.nonNull(e.parent))
                    .collect(Collectors.groupingBy(e -> e.parent));

            boolean commitIsLabeledByABranch = branches.stream().anyMatch(b -> b.getObjectId().equals(revCommit.getId()));


            boolean alwaysCreateNewColumnsForEachParentOfAMultiParentCommit = false;
            boolean alwaysCreateNewColumns = false;
            boolean joinDroppingColumns = true;
            boolean forceCreateNewColumnsForLabeledCommits = true;

            final boolean[] alreadyReused = {false};
            Set<RevCommit> usedParents = new HashSet<>();
            referencingEntries
                    .forEach(h -> {
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
                            newEntryForParent(ll, revCommit, parent, h.column, TypeOfBackReference.YES);
                            alreadyReused[0] = true;
                        } else {
                            // here we create a backreference, since we found a column waiting for this commit
                            newEntryBackReferenceWithoutParent(ll, revCommit, h.column);
                        }
                    });

            //noinspection ConstantConditions
            if (joinDroppingColumns) {
                Arrays.stream(revCommit.getParents())
                        .filter(p -> !usedParents.contains(p))
                        .filter(p -> revCommit.getParent(0) != p)
                        .filter(danglingParents::containsKey)
                        .forEach(parent -> {
                            HistoryEntry dangling = danglingParents.get(parent).get(0);
                            newEntryForParent(ll, revCommit, parent, dangling.column, TypeOfBackReference.YES);
                            usedParents.add(parent);
                        });
            }


            // here we create new column for each entry, so no history entries in column waiting for it
            if (revCommit.getParentCount() > 0) {
                boolean increase = false;
                for (RevCommit parent : revCommit.getParents()) {
                    if (!usedParents.contains(parent)) {
                        increase = true;
                        newEntryForParent(ll, revCommit, parent, theParentColumn.createSubColumn(branchId[0]), TypeOfBackReference.NO);
                    }
                }
                if (increase)
                    branchId[0]++;
            } else {
                if (!alreadyReused[0]) {
                    newEntryForParent(ll, revCommit, null, theParentColumn.createSubColumn(branchId[0]), TypeOfBackReference.NO);
                    branchId[0]++;
                }
            }

        });


        List<Column> columns = theParentColumn.getColumnStream().collect(Collectors.toList());

        System.out.println(columns.stream().map(c -> Integer.toString(c.branchId)).collect(Collectors.joining("  ")));


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

                    brancPic.append(historyEntry.typeOfParent.getSymbol(historyEntry.backReference));

                } else {
                    if (!c.isEmpty() && columnsHavingCommits.contains(c))
                        brancPic.append("│");
                    else {
                        brancPic.append(" ");
                    }
                }
                brancPic.append("  ");
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

    private static void newEntryBackReferenceWithoutParent(LinkedList<HistoryEntry> ll, RevCommit revCommit, Column theColumn) {
        HistoryEntry historyEntry = new HistoryEntry(theColumn);
        historyEntry.commit = revCommit;
        historyEntry.backReference = TypeOfBackReference.YES;
        historyEntry.typeOfParent = TypeOfParent.NONE;
        ll.add(historyEntry);
    }

    private static void newEntryForParent(LinkedList<HistoryEntry> ll, RevCommit revCommit, RevCommit parent, Column theColumn, TypeOfBackReference backReference) {
        HistoryEntry historyEntry = new HistoryEntry(theColumn);
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
        ll.add(historyEntry);
    }

    private enum TypeOfParent {
        SINGLE_PARENT("┿", "┯"),
        INITIAL("┷", "╸"),
        MERGE_STH("╭", "╮"),
        MERGE_MAIN("┣", "┏"),
        NONE("╰", "x");

        private final String withBackReference;
        private final String withoutBackReference;

        TypeOfParent(String withBackReference, String withoutBackReference) {
            this.withBackReference = withBackReference;
            this.withoutBackReference = withoutBackReference;
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

    private static class HistoryEntry {
        RevCommit commit;
        RevCommit parent;
        final Column column;
        TypeOfBackReference backReference;
        TypeOfParent typeOfParent;

        private HistoryEntry(Column column) {
            this.column = column;
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
    }

}
