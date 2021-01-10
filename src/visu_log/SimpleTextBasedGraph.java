package visu_log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleTextBasedGraph {
    static StringifiedGraph printGraph(List<List<TableEntry>> table) {

        StringifiedGraph graph = new StringifiedGraph();

        //graph.header = columns.stream().map(c -> Integer.toString(c.branchId)).collect(Collectors.joining("  "));
        graph.header = "no header today";


        Set<Object> columnsHavingCommits = new HashSet<>();

        //List<Deque<HistoryEntry>> droppingColumns = columns.stream().map(c -> new ArrayDeque<>(c.entries)).collect(Collectors.toList());

        for (List<TableEntry> entries : table) {
            HistoryEntry someEntry = null;
            StringBuilder branchPic = new StringBuilder();
            for (int c = 0; c < entries.size(); c++) {
                HistoryEntry historyEntry = getOnlyHistoryEntry(entries.get(c));

                if (historyEntry != null) {
                    someEntry = historyEntry;
                    if (historyEntry.typeOfParent == TypeOfParent.INITIAL || historyEntry.typeOfParent == TypeOfParent.NONE)
                        columnsHavingCommits.remove(c);
                    else
                        columnsHavingCommits.add(c);

                    branchPic.append(historyEntry.typeOfParent.getSymbol(historyEntry.backReference));

                } else {
                    if (columnsHavingCommits.contains(c))
                        branchPic.append("│");
                    else {
                        branchPic.append(" ");
                    }
                }
                branchPic.append("  ");
            }

            if (someEntry == null)
                throw new RuntimeException("Commit did not Appear");
            String branchesLine = branchPic.toString();
            branchesLine = new NiceReplacer().fulReplace(branchesLine);

            StringifiedGraph.Row r = new StringifiedGraph.Row();
            r.branchesLine = branchesLine;
            String branchesNames = someEntry.branches.stream().map(b -> b.name).collect(Collectors.joining(" "));
            r.description = someEntry.commitId + " " + someEntry.commit.getSha() + " " + branchesNames;
            graph.rows.add(r);
        }

        return graph;
    }

    private static HistoryEntry getOnlyHistoryEntry(TableEntry entry) {
        if (entry == null)
            return null;

        List<HistoryEntry> entries = entry.getEntries();

        if (entries.size() != 1)
            throw new RuntimeException("Stringified Graph cannot handle joined Entries. Found Entries: " + entries.size());

        return entries.get(0);
    }

    static String getString(StringifiedGraph graph) {
        return graph.rows.stream().map(r -> r.branchesLine + " " + r.description).collect(Collectors.joining("\n"));
    }

    static class StringifiedGraph {
        String header;
        List<Row> rows = new ArrayList<>();

        static class Row {
            String branchesLine;
            String description;
        }

    }
}
