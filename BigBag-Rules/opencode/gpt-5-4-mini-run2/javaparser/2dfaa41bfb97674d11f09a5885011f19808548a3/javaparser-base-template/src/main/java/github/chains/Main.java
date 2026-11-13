package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        AtomicInteger updatedFiles = new AtomicInteger();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> rewrite(path, updatedFiles));
        }

        System.out.println("Updated " + updatedFiles.get() + " file(s)");
    }

    private static void rewrite(Path file, AtomicInteger updatedFiles) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (isMockitoInvocationGetArgumentAt(call)) {
                    call.setName("getArgument");
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                updatedFiles.incrementAndGet();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean isMockitoInvocationGetArgumentAt(MethodCallExpr call) {
        return "getArgumentAt".equals(call.getNameAsString())
            && call.getArguments().size() == 2
            && call.getScope().isPresent();
    }
}
