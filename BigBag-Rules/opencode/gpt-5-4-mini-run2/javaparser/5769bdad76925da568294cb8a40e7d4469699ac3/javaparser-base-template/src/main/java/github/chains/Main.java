package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String NEW_METHOD = "getStatus";

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        for (Path file : javaFiles) {
            transform(file);
        }
    }

    private static void transform(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        for (FieldAccessExpr fieldAccess : cu.findAll(FieldAccessExpr.class)) {
            if (!"status".equals(fieldAccess.getNameAsString())) {
                continue;
            }
            if (!(fieldAccess.getScope() instanceof MethodCallExpr)) {
                continue;
            }
            MethodCallExpr call = (MethodCallExpr) fieldAccess.getScope();
            if (!"getCompare".equals(call.getNameAsString()) || call.getArguments().size() != 2) {
                continue;
            }
            fieldAccess.replace(new MethodCallExpr(call.clone(), NEW_METHOD));
            changed = true;
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

}
