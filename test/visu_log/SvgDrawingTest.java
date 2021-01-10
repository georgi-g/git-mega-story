package visu_log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class SvgDrawingTest {

    @TempDir
    Path tmpDir;

    @Test
    void createSvg() throws InterruptedException, IOException {


        System.out.println(tmpDir.toUri());

        Path output = tmpDir.resolve("out.html");
        System.out.println(output.toUri());

        Commit initial = TestCommit.createCommit();
        Commit next = TestCommit.createCommit(initial);
        Commit next2 = TestCommit.createCommit(next);
        Branch b = new Branch("master", next2);

        Column c = new Column();

        CommitStorage.newEntryForParent(next2, next, c, TypeOfBackReference.NO, 0, true);
        CommitStorage.newEntryForParent(next, initial, c, TypeOfBackReference.YES, 1, false);
        CommitStorage.newEntryForParent(initial, null, c, TypeOfBackReference.YES, 5, false);

        List<List<HistoryEntry>> entries = TableCreator.createTableFromDroppingColumns(List.of(c));
        TableRewriting.repairIds(entries);
        List<Branch> branches = List.of(b);

        String svg = SvgDrawing.createSvg(entries, branches);

        try (Writer w = new OutputStreamWriter(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            w.write(svg);
        }

        Thread.sleep(10000);

    }
}

class TestCommit implements Commit {
    final Commit parent;

    static Commit createCommit() {
        return new TestCommit(null);
    }

    static Commit createCommit(Commit parent) {
        return new TestCommit(parent);
    }

    TestCommit(Commit parent) {
        this.parent = parent;
    }

    @Override
    public int getParentCount() {
        return parent != null ? 1 : 0;
    }

    @Override
    public Commit getMyParent(int nth) {
        return parent;
    }

    @Override
    public List<Commit> getMyParents() {
        return List.of(parent);
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