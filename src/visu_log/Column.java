package visu_log;

import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

class Column {
    int branchId;
    Cluster cluster = new Cluster();
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
        if (he.column != this)
            throw new RuntimeException("HistoryEntry is not allowed to be here.");
        entries.add(he);
    }

    public HistoryEntry getLastEntry() {
        if (entries.size() > 0)
            return entries.peekLast();
        throw new RuntimeException("Column is Empty");
    }

    DoubleSupplier rank;

    static class ReferenceInfos implements DoubleSupplier {
        HistoryEntry parent;
        List<Column> children = new ArrayList<>();

        @Override
        public double getAsDouble() {
            double parentRank = parent.column.rank.getAsDouble();
            int childrenSum = children.stream().mapToInt(c -> Double.compare(c.rank.getAsDouble(), parentRank)).sum();
            boolean columnRight = childrenSum >= 0;
            double mirror = columnRight ? 1 : -1;
            //noinspection ConstantConditions // should not be empty
            double maxId = parent.column.entries.peekLast().commitId + 2;
            return parentRank + (parent.commitId + 1) / maxId * mirror;
        }
    }

    static class Cluster {
        ClusterCollector cc = new ClusterCollector();

        {
            cc.allClusterMembers.add(this);
        }

        public void join(Cluster other) {
            if (other.cc == cc)
                return;

            other.cc.collect(cc.allClusterMembers);
            if (cc != other.cc)
                throw new RuntimeException("ClusterCollector must set all ccs, also mine");
        }
    }

    static class ClusterCollector {
        int id;

        Set<Cluster> allClusterMembers = Collections.newSetFromMap(new IdentityHashMap<>());

        private void collect(Set<Cluster> clusters) {
            allClusterMembers.addAll(clusters);
            clusters.forEach(c -> c.cc = this);
        }
    }
}
