package visu_log;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColumnsSorterTest {
    @TempDir
    static Path tmpDir;

    static TestHelper testHelper = new TestHelper();

    @AfterAll
    static void afterAll() {
        testHelper.showResults(tmpDir);
    }

    @SafeVarargs
    private void addResults(List<List<HistoryEntry>>... tables) {
        testHelper.addResults(tables);
    }


    @Test
    void testSth() {
        Commit commit = TestCommit.createCommit("M");
        Commit commit2 = TestCommit.createCommit("M", commit);

        Column parentColumn = new Column();

        HistoryEntry parent2 = CommitStorage.newEntryForParent(commit2, commit, parentColumn, TypeOfBackReference.NO, 0);
        HistoryEntry parent = CommitStorage.newEntryForParent(commit, null, parentColumn, TypeOfBackReference.NO, 1);


        Column ref1 = new Column();
        Column ref2 = new Column();

        Column child1 = new Column();
        Column child2 = new Column();

        ref1.rank = new Column.ReferenceInfos();
        ref2.rank = new Column.ReferenceInfos();
        ((Column.ReferenceInfos) ref1.rank).parent = parent;
        ((Column.ReferenceInfos) ref1.rank).children.add(child1);
        ((Column.ReferenceInfos) ref2.rank).parent = parent2;
        ((Column.ReferenceInfos) ref2.rank).children.add(child2);

        List<Column> columns = new ArrayList<>(List.of(parentColumn, ref1, ref2, child1, child2));

        ColumnsSorter.sortSecondaryDroppingIntoTheDirectionOfTheirParent(columns);

        assertSame(parentColumn, columns.get(0));
        assertSame(ref2, columns.get(1));
        assertSame(ref1, columns.get(2));
        assertSame(child1, columns.get(3));
        assertSame(child2, columns.get(4));
    }

    @Test
    void sortColumnsLeft() {
        Commit commit = TestCommit.createCommit("M");
        Commit commit2 = TestCommit.createCommit("M", commit);

        Column parentColumn = new Column();

        HistoryEntry parent2 = CommitStorage.newEntryForParent(commit2, commit, parentColumn, TypeOfBackReference.NO, 0);
        HistoryEntry parent = CommitStorage.newEntryForParent(commit, null, parentColumn, TypeOfBackReference.NO, 1);


        Column ref1 = new Column();
        Column ref2 = new Column();

        Column child1 = new Column();
        Column child2 = new Column();

        ref1.rank = new Column.ReferenceInfos();
        ref2.rank = new Column.ReferenceInfos();
        ((Column.ReferenceInfos) ref1.rank).parent = parent;
        ((Column.ReferenceInfos) ref1.rank).children.add(child1);
        ((Column.ReferenceInfos) ref2.rank).parent = parent2;
        ((Column.ReferenceInfos) ref2.rank).children.add(child2);

        List<Column> columns = new ArrayList<>(List.of(child1, child2, parentColumn, ref1, ref2));

        ColumnsSorter.sortSecondaryDroppingIntoTheDirectionOfTheirParent(columns);

        assertSame(child1, columns.get(0));
        assertSame(child2, columns.get(1));
        assertSame(ref1, columns.get(2));
        assertSame(ref2, columns.get(3));
        assertSame(parentColumn, columns.get(4));
    }

    @Test
    void sortIntoColumns() {
        Commit commit = TestCommit.createCommit("M");
        Commit commit2 = TestCommit.createCommit("M", commit);

        Branch b = new Branch("B", commit2, 0);

        List<Column> columns = ColumnsSorter.sortCommitsIntoColumns(List.of(b), List.of(commit2, commit));
        columns.forEach(this::validateColumn);


        System.out.println(SimpleTextBasedGraph.getString(SimpleTextBasedGraph.printGraph(TableCreator.createTableFromDroppingColumns(columns))));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void backReferenceForNewColumnsIfBranchHasLowerRank() {
        Commit commit = TestCommit.createCommit("M");
        Commit commit2 = TestCommit.createCommit("M", commit);

        Commit commit3 = TestCommit.createCommit("M", commit2);

        Branch b = new Branch("B", commit2, 0);
        Branch b2 = new Branch("B", commit3, 1);

        List<Column> columns = ColumnsSorter.sortCommitsIntoColumns(List.of(b, b2), List.of(commit3, commit2, commit));
        System.out.println(SimpleTextBasedGraph.getString(SimpleTextBasedGraph.printGraph(TableCreator.createTableFromDroppingColumns(columns))));

        assertSame(commit2, columns.get(1).entries.peekFirst().commit);
        assertSame(TypeOfBackReference.NO, columns.get(1).entries.peekFirst().backReference);

        columns.forEach(this::validateColumn);
    }

    void validateColumn(Column column) {
        List<HistoryEntry> entries = new ArrayList<>(column.entries);
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry entry = entries.get(i);
            if (i < entries.size() - 1) {
                assertTrue(entry.typeOfParent.hasParent());
                assertSame((entries.get(i + 1).commit), entry.parent);
            } else {
                assertFalse(entry.typeOfParent.hasParent());
            }

            if (i > 0) {
                assertEquals(TypeOfBackReference.YES, entry.backReference);
                assertSame((entries.get(i - 1).parent), entry.commit);
            } else {
                assertSame(TypeOfBackReference.NO, entry.backReference);
            }
        }
    }
}