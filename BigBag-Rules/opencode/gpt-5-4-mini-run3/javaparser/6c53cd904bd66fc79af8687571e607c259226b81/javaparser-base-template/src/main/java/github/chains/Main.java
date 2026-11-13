package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_FQCN = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_FQCN = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";
    private static final String OLD_SIMPLE_NAME = "StrutsPrepareAndExecuteFilter";
    private static final String NEW_SIMPLE_NAME = "StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            System.err.println("Source root does not exist or is not a directory: " + sourceRoot);
            System.exit(1);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root " + sourceRoot, e);
        }
    }

    private static void transformFile(JavaParser parser, Path file) {
        try {
            Optional<CompilationUnit> parsed = parser.parse(file).getResult();
            if (!parsed.isPresent()) {
                return;
            }

            CompilationUnit compilationUnit = parsed.get();
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            changed |= rewriteImports(compilationUnit);
            changed |= rewriteTypes(compilationUnit);

            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (OLD_FQCN.equals(importDeclaration.getNameAsString())) {
                importDeclaration.setName(NEW_FQCN);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypes(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ObjectCreationExpr creationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if (creationExpr.getType().getNameAsString().equals(OLD_SIMPLE_NAME)) {
                creationExpr.getType().setName(NEW_SIMPLE_NAME);
                changed = true;
            }
        }

        return changed;
    }
}
