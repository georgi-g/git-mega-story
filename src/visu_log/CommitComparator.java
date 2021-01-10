package visu_log;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.IOException;
import java.util.Comparator;

class CommitComparator implements Comparator<RevCommit> {

    private final RevWalk revWalk;
    private boolean fancySort;

    CommitComparator(RevWalk revWalk, boolean fancySort) {
        this.revWalk = revWalk;
        this.fancySort = fancySort;
    }

    private RevCommit getFirstParentOfAllParents(RevCommit c) {
        while (c.getParentCount() > 0)
            c = c.getParent(0);
        return c;
    }

    @Override
    public int compare(RevCommit o1, RevCommit o2) {
        try {
            if (o1 == o2) {
                return 0;
            }


            if (revWalk.isMergedInto(o2, o1))
                return -1;
            if (revWalk.isMergedInto(o1, o2))
                return +1;

            // here the commits are not parents of each other

            try {
                revWalk.reset();
                revWalk.setRevFilter(RevFilter.MERGE_BASE);
                revWalk.markStart(o1);
                revWalk.markStart(o2);

                if (revWalk.next() == null) {

                    RevCommit firstParent1 = getFirstParentOfAllParents(o1);
                    RevCommit firstParent2 = getFirstParentOfAllParents(o2);

                    return -Comparator.comparing(RevCommit::getCommitTime).thenComparing(RevCommit::name).compare(firstParent1, firstParent2);
                }
            } finally {
                revWalk.reset();
                revWalk.setRevFilter(RevFilter.ALL);
            }

            if (fancySort) {

                if (o1.getParents().length == 1 && o2.getParents().length > 1) {
                    for (int i = 1; i < o2.getParents().length; i++) {
                        if (o2.getParent(i) == o1.getParent(0))
                            return -1;
                    }
                } else if (o2.getParents().length == 1 && o1.getParents().length > 1) {
                    for (int i = 1; i < o1.getParents().length; i++) {
                        if (o1.getParent(i) == o2.getParent(0))
                            return 1;
                    }
                }
            }

            return 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
