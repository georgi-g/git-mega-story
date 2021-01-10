package visu_log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class SvgDrawingTest {

    @TempDir
    Path tmpDir;

    @Test
    void createSvgThreeCommits() {
        Commit initial = TestCommit.createCommit();
        Commit next = TestCommit.createCommit(initial);
        Commit next2 = TestCommit.createCommit(next);
        Branch b = new Branch("master", next2);

        Column c = new Column();

        CommitStorage.newEntryForParent(next2, next, c, TypeOfBackReference.NO, 0, Collections.singletonList(b));
        CommitStorage.newEntryForParent(next, initial, c, TypeOfBackReference.YES, 1);
        CommitStorage.newEntryForParent(initial, null, c, TypeOfBackReference.YES, 5);

        List<List<HistoryEntry>> entries = TableCreator.createTableFromDroppingColumns(List.of(c));

        showResults(entries);
    }

    @Test
    void createSvgTwoBranches() {

        Commit initial = TestCommit.createCommit();
        Commit fork = TestCommit.createCommit(initial);
        Commit featureCommit = TestCommit.createCommit(fork);
        Commit masterCommit = TestCommit.createCommit(fork);
        Commit masterMergeCommit = TestCommit.createCommit(masterCommit, featureCommit);

        Branch b = new Branch("master", masterMergeCommit);

        Column c1 = new Column();
        Column c2 = new Column();

        CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 0, Collections.singletonList(b));
        CommitStorage.newEntryForParent(masterCommit, fork, c1, TypeOfBackReference.YES, 2);
        CommitStorage.newEntryForParent(fork, initial, c1, TypeOfBackReference.YES, 3);
        CommitStorage.newEntryForParent(initial, null, c1, TypeOfBackReference.YES, 4);

        CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c2, TypeOfBackReference.NO, 0);
        CommitStorage.newEntryForParent(featureCommit, fork, c2, TypeOfBackReference.YES, 1);

        List<List<HistoryEntry>> entries = TableCreator.createTableFromDroppingColumns(List.of(c1, c2));

        showResults(entries);
    }

    @Test
    void createSvgCrossingMerge() {

        Commit initial = TestCommit.createCommit("A");
        Commit fork = TestCommit.createCommit(initial);
        Commit featureCommit = TestCommit.createCommit(fork);
        Commit masterCommit = TestCommit.createCommit("B", fork);
        Commit masterMergeCommit = TestCommit.createCommit("C", masterCommit, featureCommit);
        Commit oneMoreMasterCommit = TestCommit.createCommit(masterMergeCommit);

        Commit anotherFeature = TestCommit.createCommit("F", fork);

        Branch b = new Branch("master", masterMergeCommit);

        Column c1 = new Column();
        Column c2 = new Column();
        Column c3 = new Column();

        CommitStorage.newEntryForParent(oneMoreMasterCommit, masterMergeCommit, c1, TypeOfBackReference.NO, 0, Collections.singletonList(b));
        CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.YES, 1);
        CommitStorage.newEntryForParent(masterCommit, fork, c1, TypeOfBackReference.YES, 2);
        CommitStorage.newEntryForParent(fork, initial, c1, TypeOfBackReference.YES, 3);
        CommitStorage.newEntryForParent(initial, null, c1, TypeOfBackReference.YES, 4);

        CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c2, TypeOfBackReference.NO, 1);
        CommitStorage.newEntryForParent(featureCommit, fork, c2, TypeOfBackReference.YES, 2);

        CommitStorage.newEntryForParent(anotherFeature, fork, c3, TypeOfBackReference.NO, 1);


        List<List<HistoryEntry>> entries = TableCreator.createTableFromDroppingColumns(List.of(c1, c3, c2));

        showResults(entries);
    }

    @Test
    void secondaryParent() {

        Commit featureCommit = TestCommit.createCommit("F");
        Commit featureCommit2 = TestCommit.createCommit("F2", featureCommit);
        Commit masterCommit = TestCommit.createCommit("B");
        Commit masterMergeCommit = TestCommit.createCommit("M", masterCommit, featureCommit);

        List<List<HistoryEntry>> entries1;
        {
            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(masterCommit, null, c1, TypeOfBackReference.YES, 2);
            CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c3, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit2, featureCommit, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit, null, c2, TypeOfBackReference.NO, 2);


            entries1 = TableCreator.createTableFromDroppingColumns(List.of(c1, c2, c3));
        }

        List<List<HistoryEntry>> entries1_higher;
        {
            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(masterCommit, null, c1, TypeOfBackReference.YES, 2);
            CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c3, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit2, featureCommit, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit, null, c2, TypeOfBackReference.NO, 3);


            entries1_higher = TableCreator.createTableFromDroppingColumns(List.of(c1, c2, c3));
        }

        List<List<HistoryEntry>> entries1_higher2;
        {
            Commit oneMore = TestCommit.createCommit("M");

            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(masterCommit, null, c1, TypeOfBackReference.YES, 2);
            CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c3, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit2, featureCommit, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(oneMore, null, c1, TypeOfBackReference.NO, 3);
            CommitStorage.newEntryForParent(featureCommit, null, c2, TypeOfBackReference.NO, 4);


            entries1_higher2 = TableCreator.createTableFromDroppingColumns(List.of(c1, c2, c3));
        }

        List<List<HistoryEntry>> distance_1_WithIntermediateColumn;
        {
            Commit oneMoreMerge = TestCommit.createCommit("M", masterMergeCommit, featureCommit);

            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(oneMoreMerge, masterMergeCommit, c1, TypeOfBackReference.NO, 0);
            CommitStorage.newEntryForParent(oneMoreMerge, featureCommit, c3, TypeOfBackReference.NO, 0);
            CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(masterCommit, null, c1, TypeOfBackReference.YES, 2);
            CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c3, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit2, featureCommit, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit, null, c2, TypeOfBackReference.NO, 2);


            distance_1_WithIntermediateColumn = TableCreator.createTableFromDroppingColumns(List.of(c1, c3, c2));
        }

        List<List<HistoryEntry>> distance_2_withIntermediateColumn;
        {
            Commit oneMoreMerge = TestCommit.createCommit("M", masterMergeCommit, featureCommit);

            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(oneMoreMerge, masterMergeCommit, c1, TypeOfBackReference.NO, 0);
            CommitStorage.newEntryForParent(oneMoreMerge, featureCommit, c3, TypeOfBackReference.NO, 0);
            CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(masterCommit, null, c1, TypeOfBackReference.YES, 2);
            CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c3, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit2, featureCommit, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(featureCommit, null, c2, TypeOfBackReference.NO, 2);


            distance_2_withIntermediateColumn = TableCreator.createTableFromDroppingColumns(List.of(c1, new Column(), c3, new Column(), c2));
        }

        List<List<HistoryEntry>> entries2;
        {
            Column c1 = new Column();
            Column c2 = new Column();

            CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(masterCommit, null, c1, TypeOfBackReference.YES, 3);
            CommitStorage.newEntryForParent(featureCommit2, featureCommit, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c2, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(featureCommit, null, c2, TypeOfBackReference.NO, 3);

            entries2 = TableCreator.createTableFromDroppingColumns(List.of(c1, c2));
        }

        List<List<HistoryEntry>> entries3;
        {
            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();

            CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(masterCommit, null, c1, TypeOfBackReference.YES, 3);
            CommitStorage.newEntryForParent(featureCommit2, featureCommit, c2, TypeOfBackReference.NO, 1);
            CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c2, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(featureCommit, null, c2, TypeOfBackReference.NO, 3);

            entries3 = TableCreator.createTableFromDroppingColumns(List.of(c1, c3, c2));
        }

        List<List<HistoryEntry>> entries4;
        {
            Commit additionalCommit = TestCommit.createCommit("F");
            Commit additionalCommit2 = TestCommit.createCommit("F");

            Column c1 = new Column();
            Column c2 = new Column();
            Column c3 = new Column();
            Column c4 = new Column();

            CommitStorage.newEntryForParent(masterMergeCommit, masterCommit, c1, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(masterCommit, null, c1, TypeOfBackReference.YES, 3);

            CommitStorage.newEntryForParent(masterMergeCommit, featureCommit, c2, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(featureCommit, null, c2, TypeOfBackReference.NO, 4);

            CommitStorage.newEntryForParent(masterMergeCommit, additionalCommit, c3, TypeOfBackReference.NO, 2);

            CommitStorage.newEntryForParent(masterMergeCommit, additionalCommit2, c4, TypeOfBackReference.NO, 2);
            CommitStorage.newEntryForParent(additionalCommit2, null, c4, TypeOfBackReference.NO, 3);

            CommitStorage.newEntryForParent(additionalCommit, null, c4, TypeOfBackReference.NO, 4);

            CommitStorage.newEntryBackReferenceWithoutParent(additionalCommit, c3, 4);
            entries4 = TableCreator.createTableFromDroppingColumns(List.of(c1, c2, c3, c4));
        }

        showResults(entries2, entries3, entries4, entries1, entries1_higher, entries1_higher2, distance_1_WithIntermediateColumn, distance_2_withIntermediateColumn);
    }

    @Test
    void createSvgCompressedOverlappingMerge() {

        Commit commitF1 = TestCommit.createCommit();
        Commit commitF2 = TestCommit.createCommit();
        Commit mergeCommitFeature = TestCommit.createCommit(commitF1, commitF2);

        Commit commitM1 = TestCommit.createCommit();
        Commit commitM2 = TestCommit.createCommit();
        Commit mergeCommitMaster = TestCommit.createCommit(commitM1, commitM2);

        Column cm1 = new Column();
        Column cm2 = new Column();

        Column cf1 = new Column();
        Column cf2 = new Column();

        CommitStorage.newEntryForParent(mergeCommitMaster, commitM1, cm1, TypeOfBackReference.NO, 0);
        CommitStorage.newEntryForParent(mergeCommitMaster, commitM2, cm2, TypeOfBackReference.NO, 0);
        CommitStorage.newEntryForParent(commitM1, null, cm1, TypeOfBackReference.YES, 1);
        CommitStorage.newEntryForParent(commitM2, null, cm2, TypeOfBackReference.YES, 1);

        CommitStorage.newEntryForParent(mergeCommitFeature, commitF1, cf1, TypeOfBackReference.NO, 0);
        CommitStorage.newEntryForParent(mergeCommitFeature, commitF2, cf2, TypeOfBackReference.NO, 0);
        CommitStorage.newEntryForParent(commitF1, null, cf1, TypeOfBackReference.YES, 1);
        CommitStorage.newEntryForParent(commitF2, null, cf2, TypeOfBackReference.YES, 1);

        List<List<HistoryEntry>> entries = TableCreator.createTableFromDroppingColumns(List.of(cf1, cm1, cf2, cm2));

        showResults(entries);
    }

    @SafeVarargs
    private void showResults(List<List<HistoryEntry>>... tables) {
        Path output = tmpDir.resolve("out.html");
        System.out.println(output.toUri());

        StringBuilder sb = new StringBuilder();

        for (List<List<HistoryEntry>> table : tables) {
            TableRewriting.repairIds(table);
            String svg = new SvgDrawing(new DescriptionProvider()).createSvg(table);
            sb.append(svg);
        }


        try {

            try (Writer w = new OutputStreamWriter(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
                w.write(sb.toString());
            }

            Thread.sleep(10000);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

class DescriptionProvider implements SvgDrawing.DescriptionProvider {
    @Override
    public String getDescription(Commit commit) {
        TestCommit c = (TestCommit) commit;
        return c.getDescription();
    }
}

class TestCommit implements Commit {
    final List<Commit> parent;
    final String description;

    static Commit createCommit(String description, Commit... parent) {
        return new TestCommit(description, parent);
    }

    static Commit createCommit(Commit... parent) {
        return new TestCommit(null, parent);
    }

    TestCommit(String description, Commit... parent) {
        this.parent = Arrays.asList(parent);
        this.description = description;
    }

    @Override
    public int getParentCount() {
        return parent.size();
    }

    @Override
    public Commit getMyParent(int nth) {
        return parent.get(nth);
    }

    @Override
    public List<Commit> getMyParents() {
        return parent;
    }

    @Override
    public String getSha() {
        return "" + hashCode();
    }

    public String getDescription() {
        return description != null ? description : getSha();
    }
}