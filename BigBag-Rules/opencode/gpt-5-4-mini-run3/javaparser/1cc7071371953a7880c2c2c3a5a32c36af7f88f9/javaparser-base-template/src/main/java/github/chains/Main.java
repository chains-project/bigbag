package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static final String NEW_PREFIX = "net.bytebuddy";

    public static void main(String[] args) {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());
        int changedFiles = 0;

        for (Path sourceFile : collectJavaSources(sourceRoot)) {
            changedFiles += rewriteFile(parser, sourceFile);
        }

        System.out.println("Updated " + changedFiles + " file(s) under " + sourceRoot.toAbsolutePath().normalize());
    }

    private static List<Path> collectJavaSources(Path sourceRoot) {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan source tree " + sourceRoot, e);
        }
    }

    private static int rewriteFile(JavaParser parser, Path sourceFile) {
        try {
            CompilationUnit compilationUnit = parser.parse(sourceFile).getResult()
                    .orElseThrow(() -> new IllegalStateException("Could not parse " + sourceFile));
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean modified = rewriteImports(compilationUnit) | rewriteQualifiedNames(compilationUnit);
            if (modified) {
                Files.writeString(sourceFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
                return 1;
            }
            return 0;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite " + sourceFile, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit compilationUnit) {
        boolean modified = false;
        NodeList<ImportDeclaration> imports = compilationUnit.getImports();

        for (int i = 0; i < imports.size(); i++) {
            ImportDeclaration importDeclaration = imports.get(i);
            String name = importDeclaration.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                imports.set(i, new ImportDeclaration(
                        name.replace(OLD_PREFIX, NEW_PREFIX),
                        importDeclaration.isStatic(),
                        importDeclaration.isAsterisk()));
                modified = true;
            }
        }

        return modified;
    }

    private static boolean rewriteQualifiedNames(CompilationUnit compilationUnit) {
        boolean modified = false;
        for (Node node : compilationUnit.findAll(Node.class)) {
            if (node instanceof ClassOrInterfaceType) {
                modified |= rewriteType((ClassOrInterfaceType) node);
            } else if (node instanceof NameExpr) {
                modified |= rewriteNameExpr((NameExpr) node);
            } else if (node instanceof AnnotationExpr) {
                modified |= rewriteAnnotation((AnnotationExpr) node);
            }
        }
        return modified;
    }

    private static boolean rewriteType(ClassOrInterfaceType type) {
        boolean modified = false;
        Optional<ClassOrInterfaceType> scope = type.getScope();
        if (scope.isPresent()) {
            modified |= rewriteType(scope.get());
        }

        String before = type.getNameAsString();
        String after = rewriteQualifiedName(before);
        if (!before.equals(after)) {
            type.setName(after);
            modified = true;
        }
        return modified;
    }

    private static boolean rewriteNameExpr(NameExpr nameExpr) {
        String before = nameExpr.getNameAsString();
        String after = rewriteQualifiedName(before);
        if (!before.equals(after)) {
            nameExpr.setName(after);
            return true;
        }
        return false;
    }

    private static boolean rewriteAnnotation(AnnotationExpr annotationExpr) {
        String before = annotationExpr.getNameAsString();
        String after = rewriteQualifiedName(before);
        if (!before.equals(after)) {
            annotationExpr.setName(after);
            return true;
        }
        return false;
    }

    private static String rewriteQualifiedName(String name) {
        return name.startsWith(OLD_PREFIX) ? NEW_PREFIX + name.substring(OLD_PREFIX.length()) : name;
    }
}
