package visu_log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class NiceReplacer {
    //Pattern lastThingi = Pattern.compile(".*(?:[^─])((?:─)+)(?:[^╯])*$");
    //Pattern lastThingi = Pattern.compile("(─)(?=([─│])*)$");


    public String fulReplace(String branchesLine) {
        return Stream.of(branchesLine)
                .map(this::doStrokes)
                .map(this::addConnectionsToBranchRefs)
                .map(this::turnBranches)
                .map(this::makeDoublesidedMerges)
                .map(this::makeDoublesidedMerges2)
                .map(this::turnMergePoints)
                .findAny()
                .get();
    }

    Pattern spacesInbetweenBranches = Pattern.compile("(?<=[╯┿┣╰╮┯╭╸┏┷].*)( )(?=(.*[╯┿┣╰╮┯╭╸┏┷].*))");

    //Pattern findDashIfFollowedByBranch = Pattern.compile("(─)(?!(.*[╯╮].*))");
    public String addConnectionsToBranchRefs(String branchesLine) {
        Matcher matcher = spacesInbetweenBranches.matcher(branchesLine);
        return matcher.replaceAll("─");
        //if (matcher.matches())
        //    branchesLine = branchesLine.substring(0, matcher.start(1)) + matcher.group(1).replaceAll(".", " ");
    }

    Pattern verticalLinesInbetweenBranchRefs = Pattern.compile("(?<=[╯┿┣╰╮┯╭╸┏].*)(│)(?=.*[╯┿┣╰╮┯╭╸┏])");

    public String doStrokes(String branchesLine) {
        return doReplace(verticalLinesInbetweenBranchRefs, branchesLine, "\u253c");
    }

    Pattern reverseBranches = Pattern.compile("(?<=[╋┣┿┯┷].+)(╰)");

    public String turnBranches(String s) {
        return doReplace(reverseBranches, s, "╯");
    }

    Pattern reverseMergePoints = Pattern.compile("(?<=[╋┣┿┯].*)(╭)");

    public String turnMergePoints(String s) {
        return doReplace(reverseMergePoints, s, "╮");
    }

    Pattern mergeAfterBranch = Pattern.compile("(?<=[╯╰╮╭].*)(┣)");

    public String makeDoublesidedMerges(String s) {
        return doReplace(mergeAfterBranch, s, "╋");
    }

    Pattern mergeWithoutBackReferenceAfterBranch = Pattern.compile("(?<=[╯╰╮╭].*)(┏)");

    public String makeDoublesidedMerges2(String s) {
        return doReplace(mergeWithoutBackReferenceAfterBranch, s, "┯");
    }

    private String doReplace(Pattern pattern, String string, String replacement) {
        return pattern.matcher(string).replaceAll(replacement);
    }
}
