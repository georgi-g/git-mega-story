package visu_log;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestHelper {
    static List<List<List<HistoryEntry>>> tables = new ArrayList<>();

    void showResults(Path tmpDir) {
        if (tables.isEmpty())
            return;

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

    @SafeVarargs
    final void addResults(List<List<HistoryEntry>>... tables) {
        TestHelper.tables.addAll(Arrays.stream(tables).collect(Collectors.toList()));
    }

}
