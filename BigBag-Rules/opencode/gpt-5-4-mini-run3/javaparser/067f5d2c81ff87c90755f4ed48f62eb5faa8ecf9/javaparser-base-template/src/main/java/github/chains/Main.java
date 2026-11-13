package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String TTHROWABLE = "org.apache.thrift.transport.TTransportException";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }

        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(root)) {
            javaFiles = stream.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }

        for (Path file : javaFiles) {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;

            for (TryStmt tryStmt : cu.findAll(TryStmt.class)) {
                if (tryStmt.getCatchClauses().stream()
                    .noneMatch(catchClause -> catchClause.getParameter().getType().toString().equals(TTHROWABLE)
                        || catchClause.getParameter().getType().toString().endsWith("TTransportException"))) {
                    continue;
                }

                if (tryStmt.getTryBlock().getStatements().size() != 1) {
                    continue;
                }

                Statement statement = tryStmt.getTryBlock().getStatement(0).clone();
                BlockStmt parent = tryStmt.getParentNode().filter(BlockStmt.class::isInstance).map(BlockStmt.class::cast).orElse(null);
                if (parent == null) {
                    continue;
                }

                parent.replace(tryStmt, statement);
                changed = true;
            }

            if (changed) {
                Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
