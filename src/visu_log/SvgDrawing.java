package visu_log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SvgDrawing {
    static int topOffset = 10;
    static int leftOffset = 10;
    static int commitWidth = 20;
    static int commitHeight = 25;
    static String l = "<path d=\"M%d,%d  L%d,%d\" stroke-width=\"2\" stroke=\"#dc4132\"/>";
    static String circle = "<path d=\"M%d,%d C %d,%d,%d,%d, %d,%d\" stroke-width=\"2\" stroke=\"#dc4132\" fill=\"none\"/>";
    static String circleAndLine = "<path d=\"%s\" stroke-width=\"2\" stroke=\"#dc4132\" fill=\"none\"/>";

    static void createSvg(ArrayList<List<Main.HistoryEntry>> table) {


        List<String> result = new ArrayList<>();

        for (List<Main.HistoryEntry> entries : table) {
            for (int c = 0; c < entries.size(); c++) {
                Main.HistoryEntry historyEntry = entries.get(c);

                if (historyEntry != null) {

                    switch (historyEntry.typeOfParent) {
                        case MERGE_MAIN:
                        case SINGLE_PARENT:
                            result.add(drawMainParentConnection(historyEntry, c, table));
                    }

                    switch (historyEntry.typeOfParent) {
                        case MERGE_MAIN:
                        case INITIAL:
                        case SINGLE_PARENT:
                            String commit = "\t<circle cx=\"%d\" cy=\"%d\" r=\"4\" fill=\"#79c753\" stroke=\"none\"/>";
                            String commitDots = String.format(commit, leftOffset + c * commitWidth, historyEntry.commitId * commitHeight + topOffset);
                            result.add(commitDots);
                    }
                }
            }
        }


        String svgFrame = "<svg width=\"4000\" height=\"%d\">\n\n%s\n\n</svg>";
        String svg = String.format(svgFrame, String.join("\n", result));
        System.out.println(svg);

        try (FileWriter b = new FileWriter(new File("D:\\Documents\\programming-things\\draw-git-log\\test.html"))) {
            b.write(svg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private static String drawMainParentConnection(Main.HistoryEntry entry, int columnPosition, ArrayList<List<Main.HistoryEntry>> table) {
        int startingId = entry.commitId;

        for (int parentId = startingId + 1; parentId < table.size(); parentId++) {
            for (int parentColumn = 0; parentColumn < table.get(parentId).size(); parentColumn++) {
                Main.HistoryEntry parent = table.get(parentId).get(parentColumn);

                if (parent != null && parent.typeOfParent.isMainNode() && parent.commit == entry.parent) {
                    if (parentColumn == columnPosition) {
                        return String.format(l, leftOffset + columnPosition * commitWidth, topOffset + startingId * commitHeight, leftOffset + columnPosition * commitWidth, topOffset + parentId * commitHeight);
                    } else if (parentColumn < columnPosition) {
                        int circleDistanceX = commitWidth;
                        int circleDistanceY = commitHeight;

                        int startX = leftOffset + columnPosition * commitWidth;
                        int startY = topOffset + startingId * commitHeight;

                        String m = String.format("M %d, %d ", startX, startY);

                        int parentX = leftOffset + parentColumn * commitWidth;
                        int parentY = topOffset + parentId * commitHeight;

                        int crossPointX = startX;
                        int crossPointY = parentY;

                        String l1 = String.format("L %d, %d ", crossPointX, crossPointY - circleDistanceY);
                        String c2 = String.format("Q %d, %d, %d, %d ", crossPointX, crossPointY, crossPointX - circleDistanceX, crossPointY);
                        String l3 = String.format("L %d, %d ", parentX, parentY);

                        return String.format(circleAndLine, m + l1 + c2 + l3);
                    } else {
                        throw new RuntimeException("Kann ich noch nicht");
                    }
                }
            }
        }
        throw new RuntimeException("There was no parent");
    }
}
