package visu_log;

import java.util.List;

public interface Commit {

    int getParentCount();

    Commit getMyParent(int nth);

    List<Commit> getMyParents();

    String getSha();

    String getSubject();
}

