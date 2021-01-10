package visu_log;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Spliterator;

class MyRevWalk extends RevWalk {

    public MyRevWalk(Repository repo) {
        super(repo);
    }

    @Override
    protected MyCommit createCommit(AnyObjectId id) {
        return new MyCommit(id);
    }

    public Spliterator<Commit> mySpliterator() {
        //noinspection unchecked
        return (Spliterator<Commit>) (Spliterator<?>) super.spliterator();
    }

    @Override
    public MyCommit parseCommit(AnyObjectId id) throws IOException {
        return (MyCommit) super.parseCommit(id);
    }

    @SuppressWarnings("RedundantSuppression")
    public void myMarkStart(Collection<? extends Commit> c) throws IOException {
        //noinspection unchecked,RedundantCast
        super.markStart((Collection<RevCommit>) (Collection<?>) c);
    }

    private static class MyCommit extends RevCommit implements Commit {

        protected MyCommit(AnyObjectId id) {
            super(id);
        }

        @Override
        public Commit getMyParent(int nth) {
            return (Commit) super.getParent(nth);
        }

        @Override
        public List<Commit> getMyParents() {
            //noinspection unchecked
            return (List<Commit>) (List<?>) Arrays.asList(super.getParents());
        }

        @Override
        public String getSha() {
            return getId().abbreviate(7).name();
        }
    }
}
