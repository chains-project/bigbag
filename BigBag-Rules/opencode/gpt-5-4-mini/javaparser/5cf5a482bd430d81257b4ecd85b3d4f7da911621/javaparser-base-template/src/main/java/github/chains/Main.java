package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.mvc";
    private static final String NEW_PREFIX = "jakarta.mvc";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("Source root does not exist or is not a directory: " + root);
            System.exit(1);
        }

        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            rewriteFile(parser, file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root: " + root, e);
        }
    }

    private static void rewriteFile(JavaParser parser, Path file) throws IOException {
        ParseResult<CompilationUnit> result = parser.parse(file);
        Optional<CompilationUnit> parsed = result.getResult();
        if (!parsed.isPresent()) {
            return;
        }

        CompilationUnit cu = parsed.get();
        boolean changed = rewriteImports(cu) | rewriteNames(cu);
        if (!changed) {
            return;
        }

        Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                importDeclaration.setName(name.replace(OLD_PREFIX, NEW_PREFIX));
                changed = true;
            }
        }
        cu.getPackageDeclaration().ifPresent(pkg -> {
            String name = pkg.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                pkg.setName(name.replace(OLD_PREFIX, NEW_PREFIX));
            }
        });
        return changed;
    }

    private static boolean rewriteNames(Node node) {
        boolean changed = false;

        for (Node child : node.getChildNodes()) {
            changed |= rewriteNames(child);
        }

        if (node instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType type = (ClassOrInterfaceType) node;
            String name = type.asString();
            if (name.startsWith(OLD_PREFIX)) {
                type.replace(new ClassOrInterfaceType(name.replace(OLD_PREFIX, NEW_PREFIX)));
                changed = true;
            }
        } else if (node instanceof NameExpr) {
            NameExpr nameExpr = (NameExpr) node;
            String name = nameExpr.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                nameExpr.replace(new NameExpr(name.replace(OLD_PREFIX, NEW_PREFIX)));
                changed = true;
            }
        } else if (node instanceof AnnotationExpr) {
            AnnotationExpr annotationExpr = (AnnotationExpr) node;
            String name = annotationExpr.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                annotationExpr.replace(annotationExpr.clone().setName(name.replace(OLD_PREFIX, NEW_PREFIX)));
                changed = true;
            }
            if (annotationExpr instanceof SingleMemberAnnotationExpr) {
                SingleMemberAnnotationExpr single = (SingleMemberAnnotationExpr) annotationExpr;
                changed |= rewriteNames(single.getMemberValue());
            } else if (annotationExpr instanceof NormalAnnotationExpr) {
                NormalAnnotationExpr normal = (NormalAnnotationExpr) annotationExpr;
                changed |= normal.getPairs().stream().map(pair -> rewriteNames(pair.getValue())).reduce(false, Boolean::logicalOr);
            }
        }

        return changed;
    }
}
