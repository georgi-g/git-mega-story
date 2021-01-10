package visu_log;

class Branch {
    final Commit commit;
    final String name;
    final int ranking;

    public Branch(String name, Commit commit, int ranking) {
        this.commit = commit;
        this.name = name;
        this.ranking = ranking;
    }
}
