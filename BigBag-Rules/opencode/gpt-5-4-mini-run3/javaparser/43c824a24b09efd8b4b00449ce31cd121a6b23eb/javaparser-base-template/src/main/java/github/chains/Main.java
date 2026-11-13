package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "de.gwdg.metadataqa.api.json.JsonBranch";
    private static final String NEW_TYPE = "de.gwdg.metadataqa.api.json.DataElement";
    private static final String OLD_METHOD = "getJsonPath";
    private static final String NEW_METHOD = "getPath";
    private static final Pattern OLD_FQCN = Pattern.compile("\\bde\\.gwdg\\.metadataqa\\.api\\.json\\.JsonBranch\\b");
    private static final Pattern OLD_SIMPLE_TYPE = Pattern.compile("\\bJsonBranch\\b");
    private static final Pattern OLD_METHOD_CALL = Pattern.compile("\\bgetJsonPath\\b");

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(javaFiles::add);
        }

        int modified = 0;
        for (Path file : javaFiles) {
            modified += transform(file);
        }

        System.out.println("Updated files: " + modified);
    }

    private static int transform(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(source);

        boolean parsedChanged = false;
        parsedChanged |= replaceMethodCalls(cu);

        String updated = source;
        updated = OLD_FQCN.matcher(updated).replaceAll(NEW_TYPE);
        updated = OLD_SIMPLE_TYPE.matcher(updated).replaceAll("DataElement");
        updated = OLD_METHOD_CALL.matcher(updated).replaceAll(NEW_METHOD);

        if (parsedChanged || !updated.equals(source)) {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
            return 1;
        }
        return 0;
    }

    private static boolean replaceMethodCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals(OLD_METHOD)) {
                call.setName(NEW_METHOD);
                changed = true;
            }
        }
        return changed;
    }
}
