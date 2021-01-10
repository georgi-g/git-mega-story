package visu_log;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Arrays;
import java.util.List;

public class Commit extends RevCommit {
    protected Commit(AnyObjectId id) {
        super(id);
    }

    public Commit getMyParent(int nth) {
        return (Commit) super.getParent(nth);
    }

    public List<Commit> getMyParents() {
        //noinspection unchecked
        return (List<Commit>) (List<?>) Arrays.asList(super.getParents());
    }
}

