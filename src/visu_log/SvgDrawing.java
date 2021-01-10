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
    static int commitWidth = 30;
    static int commitHeight = 40;

    static int circleDistanceX = 15;
    static int circleDistanceY = 15;

    static int incomingSecondaryMergeTransitionHeight = 7;

    // transitionHeight < splitPoint*2
    // commitHeight - commitWidth < incommingFromChildTransitionHeight
    static int incomingFromChildSplitPoint = 15;
    static int incomingFromChildTransitionHeight = 18;

    private static final int labelOffset = 20;
    private static final int commitDescriptionOffset = 20;

    static String path = "\t<path d=\"%s\" fill=\"none\" stroke=\"#%06x\"/>";
    static String pathMerge = "\t<path d=\"%s\" stroke-dasharray=\"4 1\" fill=\"none\" stroke=\"#%06x\"/>";

    static String usualCommit = "\t<circle class=\"%s\" cx=\"%d\" cy=\"%d\" r=\"5\" fill=\"#%06x\" stroke=\"#000000\"/>";
    static String labeledCommit = "\t<circle class=\"%s\" cx=\"%d\" cy=\"%d\" r=\"7\" fill=\"#%06x\" stroke=\"#000000\"/>";
    static String debugPoint = "\t<circle cx=\"%d\" cy=\"%d\" r=\"2\" fill=\"none\" stroke=\"#%06x\"/>";
    static String label = "<text class=\"text_branch\" x=\"%d\" y=\"%d\" fill=\"black\" alignment-baseline=\"middle\">%s</text>";
    static String rect = "<rect class=\"rect_branch\" rx=\"5\" ry=\"5\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#%06x\" stroke=\"#%06x\"/>";

    private static final String commitDescription = "" +
            "        <g transform=\"translate(%d,%d)\" class=\"commit_branches commit_g\">\n" +
            "            <g class=\"%s\">\n" +
            "                <rect x=\"20\" y=\"-50\" rx=\"5\" width=\"200\" height=\"20\" fill=\"#%06x\" stroke=\"#%06x\"/>\n" +
            "                <text x=\"%d\" fill=\"black\" alignment-baseline=\"middle\">%s</text>\n" +
            "            </g>\n" +
            "        </g>\n";

    static Color[] colors = new Color[]{
            new Color(74, 156, 255),
            new Color(236, 62, 255),
            new Color(77, 255, 77),
            new Color(255, 193, 100),
            new Color(255, 81, 81),
            new Color(255, 233, 91),
    };

    static Color fillDescription = new Color(217, 233, 255);
    static Color strokeDescription = new Color(0, 90, 201);

    static Color fillLabel = new Color(0xee, 0xff, 0xe4);
    static Color strokeLabel = new Color(0x30, 0x7f, 0x00);


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
                int colorLine = getColor(colors[c % colors.length].darker());
                int colorLineMerge = getColor(colors[c % colors.length].darker());
                int color = getColor(colors[c % colors.length]);
                HistoryEntry historyEntry = entries.get(c);

                if (historyEntry != null) {
                    maxColumnSoFar = Math.max(maxColumnSoFar, c);

                    switch (historyEntry.typeOfParent) {
                        case MERGE_MAIN:
                        case SINGLE_PARENT: {
                            Optional<Path> path = drawMainParentConnection(historyEntry, c, table);
                            if (path.isPresent()) {
                                result.add(String.format(SvgDrawing.path, path.get().startPoint + path.get().path, colorLine));
                                maxColumnSoFar = Math.max(path.get().parentColumn, maxColumnSoFar);
                            }
                            break;
                        }
                        case MERGE_STH:
                            Path secondaryStartPath = drawSecondaryParentConnection(historyEntry, c, table);

                            Optional<Path> path = drawMainParentConnection(historyEntry, c, table);
                            if (path.isPresent()) {
                                result.add(String.format(SvgDrawing.pathMerge, secondaryStartPath.startPoint + secondaryStartPath.path + path.get().path, colorLineMerge));
                                maxColumnSoFar = Math.max(path.get().parentColumn, maxColumnSoFar);
                                maxColumnSoFar = Math.max(secondaryStartPath.parentColumn, maxColumnSoFar);
                            } else {
                                result.add(String.format(SvgDrawing.pathMerge, secondaryStartPath.startPoint + secondaryStartPath.path, colorLineMerge));
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
                "\tcircle:hover, rect:hover + circle, circle.commitHover {r: 9;}\n" +
                "\tpath {stroke-width: 1;}\n" +
                "\tpath:hover {stroke-width: 4;}\n" +
                "\ttext {font-family: Arial, Helvetica, sans-serif}\n" +
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
        int labelX = leftOffset + maximalFilledColumnSoFar * commitWidth + labelOffset;

        String theClass = "c" + historyEntry.commit.getSha();
        if (!branchesOnCommit.isEmpty()) {
            commit = String.format(labeledCommit, theClass, startX, startY, color);
        } else {
            commit = String.format(usualCommit, theClass, startX, startY, color);
        }

        String descriptionText = theClass + " " + historyEntry.commit.getSubject();
        String description = String.format(commitDescription, startX, startY, theClass, getColor(fillDescription), getColor(strokeDescription), commitDescriptionOffset, descriptionText);


        if (!branchesOnCommit.isEmpty()) {
            StringBuilder sb = new StringBuilder("\n<g class=\"commit_branches\">\n");
            for (String branchName : branchesOnCommit) {
                sb.append("\t<g>\n")
                        .append(String.format(rect, labelX, startY, 20, 20, getColor(fillLabel), getColor(strokeLabel))).append("\n")
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

    private static int getColor(Color color) {
        return color.getRGB() & 0xffffff;
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
                int cp1Y_Pre = parentY - incomingFromChildTransitionHeight;

                //noinspection SuspiciousNameCombination because the height is put in x
                int theXDeviation = incomingFromChildTransitionHeight;
                int nextPointX = startX + theXDeviation * parentIsRight;
                int nextPointY = cp1Y_Pre;

                m += String.format("Q %d, %d, %d, %d ", cp1X_Pre, cp1Y_Pre, nextPointX, nextPointY);

                // connecting long distance line
                int nextPointX_ = parentX - (commitWidth * 2 - theXDeviation) * parentIsRight;
                int nextPointY_ = cp1Y_Pre;
                m += String.format("L %d, %d ", nextPointX_, nextPointY_);

                // go to parent
                int cp1X = parentX - commitWidth * parentIsRight;
                int cp1Y = parentY - incomingFromChildTransitionHeight;

                int splitPointX = parentX - commitWidth / 2 * parentIsRight;
                int splitPointY = parentY - incomingFromChildSplitPoint;

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
                int cp1Y = parentY - incomingFromChildTransitionHeight;

                int splitPointX = parentX - commitWidth / 2 * parentIsRight;
                int splitPointY = parentY - incomingFromChildSplitPoint;

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
