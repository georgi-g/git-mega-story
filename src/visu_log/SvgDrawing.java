package visu_log;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SvgDrawing {
    static int topOffset = 20;
    static int leftOffset = 20;
    static int commitWidth = 20;
    static int commitHeight = 26;

    static int circleDistanceX = 10;
    static int circleDistanceY = 10;

    static int incomingSecondaryMergeTransitionHeight = 4;

    // transitionHeight < splitPoint*2
    // commitHeight - commitWidth < incommingFromChildTransitionHeight
    static int incommingFromChildSplitPoint = 12;
    static int incommingFromChildTransitionHeight = 13;


    static String path = "\t<path d=\"%s\" stroke-width=\"%d\" fill=\"none\" stroke=\"#%06x\"/>";
    static String pathMerge = "\t<path d=\"%s\" stroke-width=\"%d\" stroke-dasharray=\"4 1\" fill=\"none\" stroke=\"#%06x\"/>";

    static String usualCommit = "\t<circle class=\"%s\" cx=\"%d\" cy=\"%d\" r=\"2\" fill=\"#%06x\" stroke=\"#000000\"/>";
    static String labeledCommit = "\t<circle class=\"%s\" cx=\"%d\" cy=\"%d\" r=\"4\" fill=\"#%06x\" stroke=\"#000000\"/>";
    static String debugPoint = "\t<circle cx=\"%d\" cy=\"%d\" r=\"2\" fill=\"none\" stroke=\"#%06x\"/>";
    static String label = "<text class=\"text_branch\" x=\"%d\" y=\"%d\" fill=\"black\" alignment-baseline=\"middle\">%s</text>";
    static String rect = "<rect class=\"rect_branch\" rx=\"5\" ry=\"5\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#eeffe4\" stroke=\"#307f00\"/>";

    static Color[] colors = new Color[]{
            new Color(74, 156, 255),
            new Color(236, 62, 255),
            new Color(77, 255, 77),
            new Color(255, 193, 100),
            new Color(255, 81, 81),
            new Color(255, 233, 91),
    };


    static String createSvg(List<List<HistoryEntry>> table) {


        List<String> result = new ArrayList<>();
        List<String> commits = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();


        int maxColumnSoFar = 0;
        for (List<HistoryEntry> entries : table) {
            for (int c = 0; c < entries.size(); c++) {
                if (entries.get(c) != null) {
                    maxColumnSoFar = Math.max(maxColumnSoFar, c);
                }
            }
            for (int c = 0; c < entries.size(); c++) {
                int colorLine = colors[c % colors.length].darker().getRGB() & 0xffffff;
                int colorLineMerge = colors[c % colors.length].darker().getRGB() & 0xffffff;
                int color = colors[c % colors.length].getRGB() & 0xffffff;
                HistoryEntry historyEntry = entries.get(c);

                if (historyEntry != null) {
                    maxColumnSoFar = Math.max(maxColumnSoFar, c);

                    switch (historyEntry.typeOfParent) {
                        case MERGE_MAIN:
                        case SINGLE_PARENT: {
                            Optional<Path> path = drawMainParentConnection(historyEntry, c, table);
                            if (path.isPresent()) {
                                result.add(String.format(SvgDrawing.path, path.get().startPoint + path.get().path, 1, colorLine));
                                maxColumnSoFar = Math.max(path.get().parentColumn, maxColumnSoFar);
                            }
                            break;
                        }
                        case MERGE_STH:
                            Path secondaryStartPath = drawSecondaryParentConnection(historyEntry, c, table);

                            Optional<Path> path = drawMainParentConnection(historyEntry, c, table);
                            if (path.isPresent()) {
                                result.add(String.format(SvgDrawing.pathMerge, secondaryStartPath.startPoint + secondaryStartPath.path + path.get().path, 1, colorLineMerge));
                                maxColumnSoFar = Math.max(path.get().parentColumn, maxColumnSoFar);
                                maxColumnSoFar = Math.max(secondaryStartPath.parentColumn, maxColumnSoFar);
                            } else {
                                result.add(String.format(SvgDrawing.pathMerge, secondaryStartPath.startPoint + secondaryStartPath.path, 1, colorLineMerge));
                                maxColumnSoFar = Math.max(secondaryStartPath.parentColumn, maxColumnSoFar);
                                maxColumnSoFar = Math.max(secondaryStartPath.parentColumn, maxColumnSoFar);
                            }
                            break;
                    }

                    switch (historyEntry.typeOfParent) {
                        case MERGE_MAIN:
                        case INITIAL:
                        case SINGLE_PARENT:
                            Commit commit = drawCommit(historyEntry, c, color, maxColumnSoFar);
                            commits.add(commit.commit);
                            descriptions.add(commit.description);
                            break;
                    }
                }
            }
        }

        result.addAll(commits);
        result.addAll(descriptions);
        int diagramSize = table.size() * commitHeight + topOffset * 2;

        String svgFrame = "<style type=\"text/css\">\n" +
                "\tcircle:hover, rect:hover + circle, circle.commitHover {r: 5;}\n" +
                "\tpath:hover {stroke-width: 4;}\n" +
                "\ttext {font-size: x-small; font-family: Arial, Helvetica, sans-serif}\n" +
                "\t.commit_g > g {display: none;}\n" +
                "\tg.show_group {display: unset;}\n" +
                "\tg.show_group_click {display: unset;}\n" +
                "\t.show_group_click > rect {stroke-width:1.5;}\n" +
                "</style>\n" +
                "<svg width=\"4000\" height=\"%d\">" +
                "\n\n%s\n\n" +
                "</svg>";

        String jsMagic = new BufferedReader(new InputStreamReader(SvgDrawing.class.getResourceAsStream("the-magic.js"))).lines().collect(Collectors.joining("\n"));
        String script = "<script type=\"application/ecmascript\">\n " + jsMagic + "</script>";


        String svg = String.format(svgFrame, diagramSize, String.join("\n", result));
        svg += script;
        //System.out.println(svg);

        return svg;
    }


    @SuppressWarnings("UnnecessaryLocalVariable")
    private static Path drawSecondaryParentConnection(HistoryEntry entry, int myColumn, List<List<HistoryEntry>> table) {
        int mainNodeColumn = findMainNodeFor(entry.commit, table.get(entry.commitId));
        if (mainNodeColumn < 0)
            throw new RuntimeException("My Main Node not found");

        int myColumnIsRight = Integer.signum(myColumn - mainNodeColumn);

        int theLine = entry.commitId;

        int startX = leftOffset + mainNodeColumn * commitWidth;
        int startY = topOffset + theLine * commitHeight;

        String m = String.format("M %d, %d ", startX, startY);

        int mergeTransX = startX + commitWidth / 2 * myColumnIsRight;
        int mergeTransY = startY + incomingSecondaryMergeTransitionHeight;

        String c1 = String.format("Q %d, %d, %d, %d ", (mergeTransX + startX) / 2, mergeTransY, mergeTransX, mergeTransY);

        int myX = leftOffset + myColumn * commitWidth;
        int myY = mergeTransY;

        String l1 = String.format("L %d, %d ", myX - circleDistanceX * myColumnIsRight, myY);
        String c2 = String.format("Q %d, %d, %d, %d ", myX, myY, myX, startY + circleDistanceY);

        Path path = new Path();
        path.startPoint = m;
        path.path = c1 + l1 + c2;
        path.parentColumn = myColumn;

        return path;

    }

    private static Commit drawCommit(HistoryEntry historyEntry, int columnPosition, int color, int maximalFilledColumnSoFar) {
        String commit;
        List<String> branchesOnCommit = historyEntry.branches.stream()
                .map(b -> b.name)
                .map(s -> s.replace("refs/heads/", ""))
                .collect(Collectors.toList());

        int startX = leftOffset + columnPosition * commitWidth;
        int startY = historyEntry.commitId * commitHeight + topOffset;
        int labelX = leftOffset + maximalFilledColumnSoFar * commitWidth + 10;

        String theClass = "c" + historyEntry.commit.getSha();
        if (!branchesOnCommit.isEmpty()) {
            commit = String.format(labeledCommit, theClass, startX, startY, color);
        } else {
            commit = String.format(usualCommit, theClass, startX, startY, color);
        }

        Color c = new Color(217, 233, 255);
        Color stroke = new Color(0, 90, 201);

        //Color cc = new Color(0xee, 0xff, 0xe4);
        //Color ccc = new Color(0x30, 0x7f, 0x00);

        String commitDescription =
                "            <g transform=\"translate(%d,%d)\" class=\"commit_branches commit_g\">\n" +
                        "            <g class=\"%s\">\n" +
                        "                <rect rx=\"5\" x=\"20\" y=\"-10\" width=\"200\" height=\"20\" fill=\"#%06x\" stroke=\"#%06x\"/>\n" +
                        "                <text x=\"20\" fill=\"black\" alignment-baseline=\"middle\">%s</text>\n" +
                        "            </g>\n" +
                        "            </g>\n";

        String description = String.format(commitDescription, startX, startY, theClass, c.getRGB() & 0xffffff, stroke.getRGB() & 0xffffff, theClass + " " + historyEntry.commit.getSubject());


        if (!branchesOnCommit.isEmpty()) {
            StringBuilder sb = new StringBuilder("\n<g class=\"commit_branches\">\n");
            for (String branchName : branchesOnCommit) {
                sb.append("\t<g>\n")
                        .append(String.format(rect, labelX, startY, 20, 20)).append("\n")
                        .append(String.format(label, labelX, startY, branchName)).append("\n")
                        .append("\t</g>\n");
            }
            sb.append("</g>");
            commit += sb;
        }

        Commit res = new Commit();
        res.description = description;
        res.commit = commit;

        return res;
    }

    // optional because if the history was truncated (and without parent rewrite) we have dangling parent references
    private static Optional<Path> drawMainParentConnection(HistoryEntry entry, int columnPosition, List<List<HistoryEntry>> table) {
        int startingId = entry.commitId;

        for (int parentId = startingId + 1; parentId < table.size(); parentId++) {
            int parentColumn = findMainNodeFor(entry.parent, table.get(parentId));
            if (parentColumn >= 0) {


                return Optional.of(getPathToParent(entry, columnPosition, startingId, parentId, parentColumn));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private static Path getPathToParent(HistoryEntry entry, int columnPosition, int startingId, int parentId, int parentColumn) {
        int startX = leftOffset + columnPosition * commitWidth;
        int startY = topOffset + startingId * commitHeight + (entry.typeOfParent == TypeOfParent.MERGE_STH ? circleDistanceY : 0);
        int parentX = leftOffset + parentColumn * commitWidth;
        int parentY = topOffset + parentId * commitHeight;

        String startPoint = String.format("M %d, %d ", startX, startY);
        String m = "";


        if (parentColumn == columnPosition) {
            m += String.format("L %d, %d ", parentX, parentY);
        } else {

            if (Math.abs(parentColumn - columnPosition) > 1) {
                String debugPoints = "";

                int parentIsRight = Integer.signum(parentColumn - columnPosition);

                int pointSameColumnX = startX;
                int pointSameColumnY = parentY - commitHeight;

                if (parentId == startingId + 1) {
                    pointSameColumnY += (entry.typeOfParent == TypeOfParent.MERGE_STH ? circleDistanceY : 0);
                }

                m += String.format("L %d, %d ", pointSameColumnX, pointSameColumnY);

                int cp1X_Pre = startX;
                int cp1Y_Pre = parentY - incommingFromChildTransitionHeight;

                //noinspection SuspiciousNameCombination because the height is put in x
                int theXDeviation = incommingFromChildTransitionHeight;
                int nextPointX = startX + theXDeviation * parentIsRight;
                int nextPointY = cp1Y_Pre;

                m += String.format("Q %d, %d, %d, %d ", cp1X_Pre, cp1Y_Pre, nextPointX, nextPointY);

                // connecting long distance line
                int nextPointX_ = parentX - (commitWidth * 2 - theXDeviation) * parentIsRight;
                int nextPointY_ = cp1Y_Pre;
                m += String.format("L %d, %d ", nextPointX_, nextPointY_);

                // go to parent
                int cp1X = parentX - commitWidth * parentIsRight;
                int cp1Y = parentY - incommingFromChildTransitionHeight;

                int splitPointX = parentX - commitWidth / 2 * parentIsRight;
                int splitPointY = parentY - incommingFromChildSplitPoint;

                m += String.format("Q %d, %d, %d, %d ", cp1X, cp1Y, splitPointX, splitPointY);
                m += String.format("T %d, %d ", parentX, parentY);

                //debugPoints += String.format(debugPoint, cp1X_Pre, cp1Y_Pre);
                //debugPoints += String.format(debugPoint, nextPointX, nextPointY);
                //debugPoints += String.format(debugPoint, nextPointX_, nextPointY_);
                //debugPoints += String.format(debugPoint, cp1X, cp1Y);
                //debugPoints += String.format(debugPoint, splitPointX, splitPointY);


            } else {
                int parentIsRight = Integer.signum(parentColumn - columnPosition);

                int pointSameColumnX = startX;
                int pointSameColumnY = parentY - commitHeight;

                if (parentId == startingId + 1) {
                    pointSameColumnY += (entry.typeOfParent == TypeOfParent.MERGE_STH ? circleDistanceY : 0);
                }

                m += String.format("L %d, %d ", pointSameColumnX, pointSameColumnY);

                // go to parent
                int cp1X = startX;
                int cp1Y = parentY - incommingFromChildTransitionHeight;

                int splitPointX = parentX - commitWidth / 2 * parentIsRight;
                int splitPointY = parentY - incommingFromChildSplitPoint;

                m += String.format("Q %d, %d, %d, %d ", cp1X, cp1Y, splitPointX, splitPointY);
                m += String.format("T %d, %d ", parentX, parentY);

            }

        }
        Path path = new Path();
        path.startPoint = startPoint;
        path.path = m;
        path.parentColumn = parentColumn;
        return path;
    }

    private static class Path {
        String startPoint;
        String path;
        int parentColumn;
    }

    private static class Commit {
        String commit;
        String description;
    }

    private static int findMainNodeFor(visu_log.Commit commit, List<HistoryEntry> row) {
        for (int parentColumn = 0; parentColumn < row.size(); parentColumn++) {
            HistoryEntry parent = row.get(parentColumn);
            if (parent != null && parent.typeOfParent.isMainNode() && parent.commit == commit) {
                return parentColumn;
            }
        }
        return -1;
    }
}
