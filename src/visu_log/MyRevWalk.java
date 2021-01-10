package visu_log;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.util.Spliterator;

class MyRevWalk extends RevWalk {

    public MyRevWalk(Repository repo) {
        super(repo);
    }

    @Override
    protected Commit createCommit(AnyObjectId id) {
        return new Commit(id);
    }

    public Spliterator<Commit> mySpliterator() {
        //noinspection unchecked
        return (Spliterator<Commit>) (Spliterator<?>) super.spliterator();
    }
}
