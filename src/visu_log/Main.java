package visu_log;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Main {

    public static void main(String[] args) throws IOException, GitAPIException {
        Git g = Git.open(args.length > 0 ? new File(args[0]) : new File("."));
        Repository repository = g.getRepository();
        System.out.println("Loaded ...");
        List<Ref> branchRefs = g.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
        for (Ref branch : branchRefs) {
            System.out.println("Branch:" + branch);
//            System.out.println(" Name: " + branch.getName());
//            System.out.println(" ID: " + branch.getObjectId());
//            System.out.println(" Peeled: " + branch.getPeeledObjectId());
        }

        //Iterable<RevCommit> master = g.log().add(repository.resolve("test-head")).call();
        //List<RevCommit> master = StreamSupport.stream(g.log().all().call().spliterator(), false).collect(Collectors.toList());

        final MyRevWalk revWalk = new MyRevWalk(repository);

        List<Branch> branches = branchRefs.stream()
                .map(b -> {
                    try {
                        Branch bb = new Branch();
                        bb.commmit = revWalk.parseCommit(b.getObjectId());
                        bb.name = b.getName();
                        return bb;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        revWalk.myMarkStart(branches.stream().map(b -> b.commmit).collect(Collectors.toList()));
        revWalk.sort(RevSort.TOPO);

        System.out.println("Retrieve All commits");
        List<? extends Commit> master = StreamSupport.stream(revWalk.mySpliterator(), false).limit(2000).collect(Collectors.toList());

        System.out.println("Sorting everything");
        master.sort(new CommitComparator(revWalk, false));

        System.out.println("Log fetched");


        //noinspection unchecked
        printAllCommits((List<? extends RevCommit>) master);

        System.out.println("Sort Commits into Columns");
        List<Column> columns = ColumnsSorter.sortCommitsIntoColumns(branches, master);

        System.out.println("Create Table from dropping Columns");
        ArrayList<List<HistoryEntry>> table = TableCreator.createTableFromDroppingColumns(columns);
        System.out.println("Rewrite secondary dropping");
        TableRewriting.rewriteSecondaryDropping(table);

        System.out.println("create simplified graph");
        SimpleTextBasedGraph.StringifiedGraph graph = SimpleTextBasedGraph.printGraph(branches, table);

        System.out.println(graph.header);

        PrintStream printWriter = new PrintStream(System.out, true, "UTF-8");
        graph.rows.forEach(r -> printWriter.println(r.branchesLine + "  " + r.description));
        printWriter.flush();
        //graph.rows.forEach(r -> System.out.println(r.branchesLine + "  " + r.description));

        System.out.println("compress table");
        TableRewriting.compressTable(table);

        System.out.println("create create svg");
        String svg = SvgDrawing.createSvg(table, branches);

        try (FileWriter b = new FileWriter(new File("mega-story.html"))) {
            b.write(svg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printAllCommits(List<? extends RevCommit> commits) {
        commits.forEach(revCommit -> {
            System.out.println("Commit Name " + revCommit.getId().getName());
//            System.out.println(revCommit.getId());
//            System.out.println(revCommit.getId().abbreviate(8).name());
//            System.out.println(revCommit.getName());
            for (RevCommit parent : revCommit.getParents()) {
                System.out.println("- Parent Name " + parent.getId().getName());
            }
            //noinspection unused
            PersonIdent authorIdent = revCommit.getAuthorIdent();
//            System.out.println("author " + authorIdent.getName());
//            System.out.println("message " + revCommit.getShortMessage());
//            System.out.println("fullMessage " + revCommit.getFullMessage());
        });
    }

}

