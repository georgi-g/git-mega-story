package visu_log;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiceReplacerTest {
    NiceReplacer replacer = new NiceReplacer();

    @Test
    void replaceSpacesInbetween() {

        assertEquals(" ╰─│┣╮──╰ │ │  ", replacer.addConnectionsToBranchRefs(" ╰ │┣╮  ╰ │ │  "));
        assertEquals("  ││┣╮──╰ │ │  ", replacer.addConnectionsToBranchRefs("  ││┣╮  ╰ │ │  "));
        assertEquals(" ╰─│┣     │ │  ", replacer.addConnectionsToBranchRefs(" ╰ │┣     │ │  "));
    }

    @Test
    void testMatchSpacesInbetween() {
        Matcher matcher = replacer.spacesInbetweenBranches.matcher("╰  ╯");
        assertTrue(matcher.find());
        assertEquals(" ", matcher.group(1));
        assertTrue(replacer.spacesInbetweenBranches.matcher("╰  ╰").find());
    }

    @Test
    void doStroke() {
        assertEquals(" ╰ ┼┣╮  ╰ │ │  ", replacer.doStrokes(" ╰ │┣╮  ╰ │ │  "));
        assertEquals("  ││┣╮  ╰ │ │  ", replacer.doStrokes("  ││┣╮  ╰ │ │  "));
    }

    @Test
    void turnBranches() {
        assertEquals(" ╰ ┣ ╮  ╯ │ │  ", replacer.turnBranches(" ╰ ┣ ╮  ╰ │ │  "));
        assertEquals(" ╰   ╮  ╰ │ │  ", replacer.turnBranches(" ╰   ╮  ╰ │ │  "));
        assertEquals(" ╰ ┿ ╮  ╯ │ │  ", replacer.turnBranches(" ╰ ┿ ╮  ╰ │ │  "));
    }

    @Test
    void makeDoublesidedMerges() {
        assertEquals(" ╰ ╋ ╮  ╯ │ │  ", replacer.makeDoublesidedMerges(" ╰ ┣ ╮  ╯ │ │  "));
        assertEquals(" ╰ ╋ ╮  ╰ │ │  ", replacer.makeDoublesidedMerges(" ╰ ┣ ╮  ╰ │ │  "));
        assertEquals("   ┣ ╮  ╰ │ │  ", replacer.makeDoublesidedMerges("   ┣ ╮  ╰ │ │  "));
    }
}