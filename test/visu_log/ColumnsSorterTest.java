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
    private void addResults(List<List<TableEntry>>... tables) {
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
        validateAllColumns(columns);


        System.out.println(SimpleTextBasedGraph.getString(SimpleTextBasedGraph.printGraph(TableCreator.createTableFromDroppingColumns(columns))));
    }

    @Test
    void noExtraBranchIfJoinedLowestRankColumn() {
        Commit commit = TestCommit.createCommit("M");
        Commit commit2 = TestCommit.createCommit("M", commit);

        Commit commit3 = TestCommit.createCommit("M", commit2);

        Branch b = new Branch("Master", commit2, 0);
        Branch b2 = new Branch("B", commit3, 1);

        List<Column> columns = ColumnsSorter.sortCommitsIntoColumns(List.of(b, b2), List.of(commit3, commit2, commit));
        System.out.println(SimpleTextBasedGraph.getString(SimpleTextBasedGraph.printGraph(TableCreator.createTableFromDroppingColumns(columns))));

        assertSame(2, columns.size());

        validateAllColumns(columns);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void backReferenceForNewColumnsIfBranchHasLowerRank() {
        Commit commit = TestCommit.createCommit("M");
        Commit commit2 = TestCommit.createCommit("M", commit);

        Commit commit3 = TestCommit.createCommit("M", commit2);
        Commit commitFeature = TestCommit.createCommit("M", commit);

        Branch b = new Branch("Master", commit2, 0);
        Branch b2 = new Branch("B", commit3, 1);
        Branch bFeature = new Branch("Feature", commitFeature, 1);

        List<Column> columns = ColumnsSorter.sortCommitsIntoColumns(List.of(b, b2, bFeature), List.of(commitFeature, commit3, commit2, commit));
        System.out.println(SimpleTextBasedGraph.getString(SimpleTextBasedGraph.printGraph(TableCreator.createTableFromDroppingColumns(columns))));

        assertSame(commit2, columns.get(1).entries.peekFirst().commit);
        assertSame(TypeOfBackReference.NO, columns.get(1).entries.peekFirst().backReference);

        validateAllColumns(columns);
    }

    public static void validateAllColumns(List<Column> columns) {
        columns.forEach(ColumnsSorterTest::validateColumn);
    }

    static void validateColumn(Column column) {
        List<HistoryEntry> entries = new ArrayList<>(column.entries);
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry entry = entries.get(i);
            if (i < entries.size() - 1) {
                assertTrue(entry.typeOfParent.hasParent(), String.format("(Column %d): Entry is not last so should have a parent: Entry %d", column.branchId, i));
                boolean parentFound = false;
                for (int next = i + 1; next < entries.size() && !parentFound; next++) {
                    assertTrue(entries.get(next).commit == entry.parent || entries.get(next).parent == entry.parent, String.format("(Column %d): Entry %d has parent but it does not match the commit of Entry %d", column.branchId, i, next));
                    if (entries.get(next).commit == entry.parent) {
                        parentFound = true;
                    }
                }
                assertTrue(parentFound, String.format("(Column %d): Parent found for entry %d", column.branchId, i));
            } else {
                assertFalse(entry.typeOfParent.hasParent(), String.format("(Column %d): Last Entry %d in Column Should not have Parent. Or maybe a Backreference is missing.", column.branchId, i));
            }

            if (entry.typeOfParent == TypeOfParent.MERGE_STH) {
                assertEquals(TypeOfBackReference.NO, entry.backReference, String.format("(Column %d) Secondary Parents do not have Back References. Entry %d", column.branchId, i));
            }

            if (i > 0) {
                if (entry.backReference == TypeOfBackReference.YES)
                    assertSame(entries.get(i - 1).parent, entry.commit, String.format("(Column %d): Back Reference for entry %d does not match the parent of entry %d", column.branchId, i, i - 1));
            } else {
                assertSame(TypeOfBackReference.NO, entry.backReference, "First Entry");
            }
        }
    }
}