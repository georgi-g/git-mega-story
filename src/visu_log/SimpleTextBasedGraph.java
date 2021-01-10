package visu_log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleTextBasedGraph {
    static StringifiedGraph printGraph(List<Branch> branches, List<List<HistoryEntry>> table) {

        StringifiedGraph graph = new StringifiedGraph();

        //graph.header = columns.stream().map(c -> Integer.toString(c.branchId)).collect(Collectors.joining("  "));
        graph.header = "no header today";


        Set<Object> columnsHavingCommits = new HashSet<>();

        //List<Deque<HistoryEntry>> droppingColumns = columns.stream().map(c -> new ArrayDeque<>(c.entries)).collect(Collectors.toList());

        for (List<HistoryEntry> entries : table) {
            HistoryEntry someEntry = null;
            StringBuilder branchPic = new StringBuilder();
            for (int c = 0; c < entries.size(); c++) {
                HistoryEntry historyEntry = entries.get(c);

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

            HistoryEntry finalSomeEntry = someEntry;
            if (someEntry == null)
                throw new RuntimeException("Commit did not Appear");
            String branchesLine = branchPic.toString();
            branchesLine = new NiceReplacer().fulReplace(branchesLine);

            StringifiedGraph.Row r = new StringifiedGraph.Row();
            r.branchesLine = branchesLine;
            r.description = someEntry.commitId + " " + someEntry.commit.getSha() + " " + branches.stream().filter(b -> b.commit == finalSomeEntry.commit).map(b -> b.name).collect(Collectors.joining(" "));
            graph.rows.add(r);
        }

        return graph;
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
