package visu_log;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

    // transitionHeight < splitPoint*2
    // commitHeight - commitWidth < incommingFromChildTransitionHeight
    static int incommingFromChildSplitPoint = 12;
    static int incommingFromChildTransitionHeight = 13;


    static String path = "\t<path d=\"%s\" stroke-width=\"%d\" fill=\"none\" stroke=\"#%06x\"/>";
    static String pathMerge = "\t<path d=\"%s\" stroke-width=\"%d\" stroke-dasharray=\"4 1\" fill=\"none\" stroke=\"#%06x\"/>";

    static String usualCommit = "\t<circle cx=\"%d\" cy=\"%d\" r=\"2\" fill=\"#%06x\" stroke=\"#000000\"/>";
    static String labeledCommit = "\t<circle cx=\"%d\" cy=\"%d\" r=\"4\" fill=\"#%06x\" stroke=\"#000000\"/>";
    static String debugPoint = "\t<circle cx=\"%d\" cy=\"%d\" r=\"2\" fill=\"none\" stroke=\"#%06x\"/>";
    static String label = "<text class=\"text_branch\" x=\"%d\" y=\"%d\" fill=\"black\" alignment-baseline=\"middle\">%s</text>\n";
    static String rect = "<rect class=\"rect_branch\" rx=\"5\" ry=\"5\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#eeffe4\" stroke=\"#307f00\" fill=\"none\" />\n";

    static Color[] colors = new Color[]{
            new Color(74, 156, 255),
            new Color(236, 62, 255),
            new Color(77, 255, 77),
            new Color(255, 193, 100),
            new Color(255, 81, 81),
            new Color(255, 233, 91),
    };


    static void createSvg(ArrayList<List<Main.HistoryEntry>> table, List<Ref> branches) {


        List<String> result = new ArrayList<>();
        List<String> commits = new ArrayList<>();


        int maxColumnSoFar = 0;
        for (List<Main.HistoryEntry> entries : table) {
            for (int c = 0; c < entries.size(); c++) {
                if (entries.get(c) != null) {
                    maxColumnSoFar = Math.max(maxColumnSoFar, c);
                }
            }
            for (int c = 0; c < entries.size(); c++) {
                int colorLine = colors[c % colors.length].darker().getRGB() & 0xffffff;
                int colorLineMerge = colors[c % colors.length].darker().getRGB() & 0xffffff;
                int color = colors[c % colors.length].getRGB() & 0xffffff;
                Main.HistoryEntry historyEntry = entries.get(c);

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
                            commits.add(drawCommit(historyEntry, c, branches, color, maxColumnSoFar));
                            break;
                    }
                }
            }
        }

        result.addAll(commits);
        int diagramSize = table.size() * commitHeight + topOffset * 2;

        String svgFrame = "<style type=\"text/css\">\n" +
                "\tcircle:hover, rect:hover + circle, circle.commitHover {r: 5;}\n" +
                "\tpath:hover {stroke-width: 4;}\n" +
                "\ttext {font-size: x-small; font-family: Arial, Helvetica, sans-serif}\n" +
                "</style>\n" +
                "<svg width=\"4000\" height=\"%d\">" +
                "\n\n%s\n\n" +
                "</svg>";

        String script = "<script type=\"application/ecmascript\"> " +
                "    let x = document.getElementsByClassName(\"commit_branches\");\n" +
                "    \n" +
                "    \n" +
                "    for (let i = 0; i < x.length; i++) {\n" +
                "        let currentXDelta = -1;\n" +
                "        let groupNodes = x[i].querySelectorAll('g');\n" +
                "        for (let gNode = 0; gNode < groupNodes.length; gNode++) {\n" +
                "            let textNode = groupNodes[gNode].querySelector('text');\n" +
                "            let rectNode = groupNodes[gNode].querySelector('rect');\n" +
                "            if (!textNode || !rectNode)\n" +
                "                continue;\n" +
                "            if (currentXDelta < 0)\n" +
                "                currentXDelta = textNode.getAttribute(\"x\");\n" +
                "            else\n" +
                "                textNode.setAttribute(\"x\", currentXDelta);\n" +
                "            let f = textNode.getBBox();\n" +
                "            currentXDelta = f.x + f.width + 10;\n" +
                "            rectNode.setAttribute(\"width\", f.width + 4);\n" +
                "            rectNode.setAttribute(\"height\", f.height + 4);\n" +
                "            rectNode.setAttribute(\"x\", f.x - 2);\n" +
                "            rectNode.setAttribute(\"y\", f.y - 2);\n" +
                "        }\n" +
                "    }\n" +
                "</script>";


        String svg = String.format(svgFrame, diagramSize, String.join("\n", result));
        svg += script;
        //System.out.println(svg);

        try (FileWriter b = new FileWriter(new File("D:\\Documents\\programming-things\\draw-git-log\\test.html"))) {
            b.write(svg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @SuppressWarnings("UnnecessaryLocalVariable")
    private static Path drawSecondaryParentConnection(Main.HistoryEntry entry, int myColumn, ArrayList<List<Main.HistoryEntry>> table) {
        int mainNodeColumn = findMainNodeFor(entry.commit, table.get(entry.commitId));
        if (mainNodeColumn < 0)
            throw new RuntimeException("My Main Node not found");

        int theLine = entry.commitId;

        int startX = leftOffset + mainNodeColumn * commitWidth;
        int startY = topOffset + theLine * commitHeight;

        String m = String.format("M %d, %d ", startX, startY);

        int myX = leftOffset + myColumn * commitWidth;
        int myY = startY;

        String l1 = String.format("L %d, %d ", myX + circleDistanceX * Integer.signum(mainNodeColumn - myColumn), myY);
        String c2 = String.format("Q %d, %d, %d, %d ", myX, myY, myX, myY + circleDistanceY);

        Path path = new Path();
        path.startPoint = m;
        path.path = l1 + c2;
        path.parentColumn = myColumn;

        return path;

    }

    private static String drawCommit(Main.HistoryEntry historyEntry, int columnPosition, List<Ref> branches, int color, int maximalFilledColumnSoFar) {
        String result = "";
        List<String> branchesOnCommit = branches.stream()
                .filter(b -> b.getObjectId().equals(historyEntry.commit.toObjectId()))
                .map(Ref::getName)
                .map(s -> s.replace("refs/heads/", ""))
                .collect(Collectors.toList());

        int startX = leftOffset + columnPosition * commitWidth;
        int startY = historyEntry.commitId * commitHeight + topOffset;
        int labelX = leftOffset + maximalFilledColumnSoFar * commitWidth + 10;

        if (!branchesOnCommit.isEmpty()) {
            result += String.format(labeledCommit, startX, startY, color);
        } else {
            result += String.format(usualCommit, startX, startY, color);
        }

        if (!branchesOnCommit.isEmpty()) {
            StringBuilder sb = new StringBuilder("<g class=\"commit_branches\">");
            for (String branchName : branchesOnCommit) {
                sb.append("<g>")
                        .append(String.format(rect, labelX, startY, 20, 20))
                        .append(String.format(label, labelX, startY, branchName))
                        .append("</g>");
            }
            sb.append("</g>");
            result += sb;
        }
        return result;
    }

    private static Optional<Path> drawMainParentConnection(Main.HistoryEntry entry, int columnPosition, ArrayList<List<Main.HistoryEntry>> table) {
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
    private static Path getPathToParent(Main.HistoryEntry entry, int columnPosition, int startingId, int parentId, int parentColumn) {
        int startX = leftOffset + columnPosition * commitWidth;
        int startY = topOffset + startingId * commitHeight + (entry.typeOfParent == Main.TypeOfParent.MERGE_STH ? circleDistanceY : 0);
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
                    pointSameColumnY += (entry.typeOfParent == Main.TypeOfParent.MERGE_STH ? circleDistanceY : 0);
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
                    pointSameColumnY += (entry.typeOfParent == Main.TypeOfParent.MERGE_STH ? circleDistanceY : 0);
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

    private static int findMainNodeFor(RevCommit commit, List<Main.HistoryEntry> row) {
        for (int parentColumn = 0; parentColumn < row.size(); parentColumn++) {
            Main.HistoryEntry parent = row.get(parentColumn);
            if (parent != null && parent.typeOfParent.isMainNode() && parent.commit == commit) {
                return parentColumn;
            }
        }
        return -1;
    }
}
