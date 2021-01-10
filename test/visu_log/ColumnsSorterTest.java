package visu_log;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

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
}