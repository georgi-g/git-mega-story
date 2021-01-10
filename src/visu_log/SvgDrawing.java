package visu_log;

import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SvgDrawing {
    static int topOffset = 10;
    static int leftOffset = 10;
    static int commitWidth = 20;
    static int commitHeight = 26;

    static int circleDistanceX = 10;
    static int circleDistanceY = 10;

    // transitionHeight < splitPoint*2
    // commitHeight - commitWidth < incommingFromChildTransitionHeight
    static int incommingFromChildSplitPoint = 12;
    static int incommingFromChildTransitionHeight = 13;


    static String path = "\t<path d=\"%s\" stroke-width=\"1\" stroke=\"#2927f3\" fill=\"none\"/>";

    static String commit = "\t<circle cx=\"%d\" cy=\"%d\" r=\"4\" fill=\"#79c753\" stroke=\"none\"/>";
    static String debugPoint = "\t<circle cx=\"%d\" cy=\"%d\" r=\"2\" fill=\"none\" stroke=\"#034f84\"/>";


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
                            break;
                        case MERGE_STH:
                            result.add(drawSecondaryParentConnection(historyEntry, c, table));
                            result.add(drawMainParentConnection(historyEntry, c, table));
                            break;
                    }

                    switch (historyEntry.typeOfParent) {
                        case MERGE_MAIN:
                        case INITIAL:
                        case SINGLE_PARENT:
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
    private static String drawSecondaryParentConnection(Main.HistoryEntry entry, int myColumn, ArrayList<List<Main.HistoryEntry>> table) {
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

        return String.format(path, m + l1 + c2);

    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private static String drawMainParentConnection(Main.HistoryEntry entry, int columnPosition, ArrayList<List<Main.HistoryEntry>> table) {
        int startingId = entry.commitId;

        for (int parentId = startingId + 1; parentId < table.size(); parentId++) {
            int parentColumn = findMainNodeFor(entry.parent, table.get(parentId));
            if (parentColumn >= 0) {

                int startX = leftOffset + columnPosition * commitWidth;
                int startY = topOffset + startingId * commitHeight + (entry.typeOfParent == Main.TypeOfParent.MERGE_STH ? circleDistanceY : 0);
                int parentX = leftOffset + parentColumn * commitWidth;
                int parentY = topOffset + parentId * commitHeight;


                if (parentColumn == columnPosition) {
                    String m = String.format("M %d, %d ", startX, startY);
                    m += String.format("L %d, %d ", parentX, parentY);

                    return String.format(path, m);
                } else {

                    if (Math.abs(parentColumn - columnPosition) > 1) {
                        String debugPoints = "";

                        String m = String.format("M %d, %d ", startX, startY);
                        int parentIsRight = Integer.signum(parentColumn - columnPosition);

                        int pointSameColumnX = startX;
                        int pointSameColumnY = parentY - commitHeight;

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


                        return String.format(path, m) + debugPoints;
                    } else {
                        String m = String.format("M %d, %d ", startX, startY);
                        int parentIsRight = Integer.signum(parentColumn - columnPosition);

                        int pointSameColumnX = startX;
                        int pointSameColumnY = parentY - commitHeight;

                        m += String.format("L %d, %d ", pointSameColumnX, pointSameColumnY);

                        // to to parent
                        int cp1X = startX;
                        int cp1Y = parentY - incommingFromChildTransitionHeight;

                        int splitPointX = parentX - commitWidth / 2 * parentIsRight;
                        int splitPointY = parentY - incommingFromChildSplitPoint;

                        m += String.format("Q %d, %d, %d, %d ", cp1X, cp1Y, splitPointX, splitPointY);
                        m += String.format("T %d, %d ", parentX, parentY);

                        return String.format(path, m);
                    }

                }
            }
        }
        throw new RuntimeException("There was no parent");
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
