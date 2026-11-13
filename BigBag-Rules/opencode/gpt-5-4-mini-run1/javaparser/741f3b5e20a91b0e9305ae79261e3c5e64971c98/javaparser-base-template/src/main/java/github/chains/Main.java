package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            ReadValueStringRewriteVisitor visitor = new ReadValueStringRewriteVisitor();
            visitor.visit(cu, null);
            if (visitor.changed) {
                ensureStringReaderImport(cu);
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static void ensureStringReaderImport(CompilationUnit cu) {
        boolean present = cu.getImports().stream()
                .anyMatch(importDeclaration -> importDeclaration.getNameAsString().equals("java.io.StringReader"));
        if (!present) {
            cu.addImport(StringReader.class);
        }
    }

    private static final class ReadValueStringRewriteVisitor extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public Visitable visit(MethodCallExpr call, Void arg) {
            Visitable visited = super.visit(call, arg);
            if (!"readValue".equals(call.getNameAsString()) || call.getArguments().size() != 2) {
                return visited;
            }

            Expression jsonExpr = call.getArgument(0);
            Expression targetType = call.getArgument(1);
            if (!(targetType instanceof ClassExpr)) {
                return visited;
            }
            if (jsonExpr.isObjectCreationExpr() && jsonExpr.asObjectCreationExpr().getType().asString().equals(StringReader.class.getSimpleName())) {
                return visited;
            }

            MethodCallExpr readerForCall = new MethodCallExpr(
                    call.getScope().orElse(null),
                    "readerFor",
                    NodeList.nodeList(targetType.clone())
            );
            MethodCallExpr replacement = new MethodCallExpr(
                    readerForCall,
                    "readValue",
                    NodeList.nodeList(new ObjectCreationExpr(
                            null,
                            new ClassOrInterfaceType(null, StringReader.class.getSimpleName()),
                            NodeList.nodeList(jsonExpr.clone())
                    ))
            );

            changed = true;
            return replacement;
        }
    }
}
