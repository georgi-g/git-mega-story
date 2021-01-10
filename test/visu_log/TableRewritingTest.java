package visu_log;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class TableRewritingTest {

    @TempDir
    static Path tmpDir;

    static TestHelper testHelper = new TestHelper();

    @AfterAll
    static void afterAll() {
        testHelper.showResults(tmpDir);
    }

    @Test
    void compressTable() {

        Commit initial = TestCommit.createCommit("A");
        Commit interestingCommit = TestCommit.createCommit(initial);
        Commit merge1 = TestCommit.createCommit("m1", initial, interestingCommit);
        Commit merge2 = TestCommit.createCommit("m2", initial, interestingCommit);

        {
            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(merge1, initial, c1, TypeOfBackReference.YES, 0);
            CommitStorage.newEntryForParent(merge1, interestingCommit, c2, TypeOfBackReference.YES, 0);

            CommitStorage.newEntryForParent(merge2, initial, c3, TypeOfBackReference.YES, 1);
            CommitStorage.newEntryForParent(merge2, interestingCommit, c2, TypeOfBackReference.YES, 1);

            CommitStorage.newEntryForParent(interestingCommit, initial, c2, TypeOfBackReference.YES, 2);
            CommitStorage.newEntryForParent(initial, null, c1, TypeOfBackReference.YES, 3);

            testHelper.addResults(TableCreator.createTableFromDroppingColumns(List.of(c1, c2, c3)));
        }

        {
            Column newList = Column.createNewList();
            Column c1 = newList.createSubColumn(1);
            Column c2 = newList.createSubColumn(2);
            Column c3 = newList.createSubColumn(3);

            CommitStorage.newEntryForParent(merge1, initial, c1, TypeOfBackReference.NO, 0);
            CommitStorage.newEntryForParent(merge1, interestingCommit, c2, TypeOfBackReference.NO, 0);

            CommitStorage.newEntryForParent(merge2, initial, c3, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(merge2, interestingCommit, c2, TypeOfBackReference.NO, 1);

            CommitStorage.newEntryForParent(interestingCommit, initial, c2, TypeOfBackReference.YES, 2);
            CommitStorage.newEntryForParent(initial, null, c1, TypeOfBackReference.YES, 3);
            CommitStorage.newEntryBackReferenceWithoutParent(initial, c2, 3);
            CommitStorage.newEntryBackReferenceWithoutParent(initial, c3, 3);

            List<Column> columns = List.of(c1, c2, c3);
            ColumnsSorterTest.validateAllColumns(columns);
            List<List<TableEntry>> tableFromDroppingColumns = TableCreator.createTableFromDroppingColumns(columns);
            TableRewriting.compressTable(tableFromDroppingColumns);
            testHelper.addResults(tableFromDroppingColumns);
        }

    }

    @Test
    void compressTable2() {

        Commit initial = TestCommit.createCommit("A");
        Commit commit1 = TestCommit.createCommit("B", initial);
        Commit commit2 = TestCommit.createCommit("C", commit1);

        Commit merge1 = TestCommit.createCommit("m1", initial, commit1);
        Commit merge2 = TestCommit.createCommit("m2", initial, commit1);

        {
            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(commit2, commit1, c1, TypeOfBackReference.NO, 0);

            CommitStorage.newEntryForParent(merge1, initial, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(merge1, commit1, c1, TypeOfBackReference.NO, 1);

            CommitStorage.newEntryForParent(merge2, initial, c3, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(merge2, commit1, c1, TypeOfBackReference.NO, 2);

            CommitStorage.newEntryForParent(commit1, initial, c1, TypeOfBackReference.YES, 3);

            CommitStorage.newEntryForParent(initial, null, c1, TypeOfBackReference.YES, 4);
            CommitStorage.newEntryBackReferenceWithoutParent(initial, c2, 4);
            CommitStorage.newEntryBackReferenceWithoutParent(initial, c3, 4);

            List<Column> columns = List.of(c1, c2, c3);
            ColumnsSorterTest.validateAllColumns(columns);
            testHelper.addResults(TableCreator.createTableFromDroppingColumns(List.of(c1, c2, c3)));
        }

        {
            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(commit2, commit1, c1, TypeOfBackReference.NO, 0);

            CommitStorage.newEntryForParent(merge1, initial, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(merge1, commit1, c1, TypeOfBackReference.NO, 1);

            CommitStorage.newEntryForParent(merge2, initial, c3, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(merge2, commit1, c1, TypeOfBackReference.NO, 2);

            CommitStorage.newEntryForParent(commit1, initial, c1, TypeOfBackReference.YES, 3);

            CommitStorage.newEntryForParent(initial, null, c1, TypeOfBackReference.YES, 4);
            CommitStorage.newEntryBackReferenceWithoutParent(initial, c2, 4);
            CommitStorage.newEntryBackReferenceWithoutParent(initial, c3, 4);

            List<Column> columns = List.of(c1, c2, c3);
            ColumnsSorterTest.validateAllColumns(columns);
            List<List<TableEntry>> tableFromDroppingColumns = TableCreator.createTableFromDroppingColumns(List.of(c1, c2, c3));
            TableRewriting.compressTable(tableFromDroppingColumns);
            testHelper.addResults(tableFromDroppingColumns);
        }
    }
}