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

    private void showResults(List<List<HistoryEntry>> entries) {
        Path output = tmpDir.resolve("out.html");
        System.out.println(output.toUri());

        TableRewriting.repairIds(entries);

        String svg = SvgDrawing.createSvg(entries);

        try {

            try (Writer w = new OutputStreamWriter(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
                w.write(svg);
            }

            Thread.sleep(10000);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

class TestCommit implements Commit {
    final List<Commit> parent;

    static Commit createCommit(Commit... parent) {
        return new TestCommit(parent);
    }

    TestCommit(Commit... parent) {
        this.parent = Arrays.asList(parent);
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

    @Override
    public String getSubject() {
        return "Subject";
    }
}