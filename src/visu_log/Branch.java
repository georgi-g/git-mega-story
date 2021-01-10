package visu_log;

class Branch {
    final Commit commmit;
    final String name;

    public Branch(String name, Commit commmit) {
        this.commmit = commmit;
        this.name = name;
    }
}
