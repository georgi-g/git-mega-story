package visu_log;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class SimpleTextBasedGraphTest {

    @Test
    void createSvgThreeCommits() {
        Commit initial = TestCommit.createCommit();
        Commit next = TestCommit.createCommit(initial);

        Commit anotherInitial = TestCommit.createCommit(next);

        Column c = new Column();
        Column c2 = new Column();

        CommitStorage.newEntryForParent(anotherInitial, next, c2, TypeOfBackReference.NO, 0);
        CommitStorage.newEntryForParent(next, initial, c, TypeOfBackReference.NO, 1);
        CommitStorage.newEntryBackReferenceWithoutParent(next, c2, 1);
        CommitStorage.newEntryForParent(initial, null, c, TypeOfBackReference.YES, 5);

        List<List<TableEntry>> entries = TableCreator.createTableFromDroppingColumns(List.of(c, c2));

        SimpleTextBasedGraph.StringifiedGraph graph = SimpleTextBasedGraph.printGraph(entries);
        graph.rows.forEach(row -> System.out.println(row.branchesLine + " " + row.description));

        Assertions.assertEquals("   ┯  ", graph.rows.get(0).branchesLine);
        Assertions.assertEquals("┯──╯  ", graph.rows.get(1).branchesLine);
        Assertions.assertEquals("┷     ", graph.rows.get(2).branchesLine);
    }
}