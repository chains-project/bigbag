package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path root = Paths.get(args[0]);
        JavaParser parser = new JavaParser(new ParserConfiguration());

        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(path -> transformFile(parser, path));
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            CompilationUnit cu = parser.parse(path).getResult().orElseThrow(() ->
                    new IllegalStateException("Unable to parse " + path));
            LexicalPreservingPrinter.setup(cu);

            AtomicBoolean changed = new AtomicBoolean(false);
            new ScriptResultTransformer(changed).visit(cu, null);
            if (changed.get()) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to transform " + path, ex);
        }
    }

    private static final class ScriptResultTransformer extends ModifierVisitor<Void> {
        private final AtomicBoolean changed;

        private ScriptResultTransformer(AtomicBoolean changed) {
            this.changed = changed;
        }

        @Override
        public Visitable visit(CompilationUnit cu, Void unused) {
            boolean removed = cu.getImports().removeIf(importDecl ->
                    importDecl.getNameAsString().equals(OLD_TYPE));
            changed.compareAndSet(false, removed);
            return super.visit(cu, unused);
        }

        @Override
        public Visitable visit(MethodCallExpr call, Void unused) {
            if (isGetJavaScriptResultCall(call) && call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                if (scope.isObjectCreationExpr() && isScriptResultConstruction(scope.asObjectCreationExpr())) {
                    changed.set(true);
                    return scope.asObjectCreationExpr().getArgument(0).clone();
                }
            }
            return super.visit(call, unused);
        }

        private boolean isGetJavaScriptResultCall(MethodCallExpr call) {
            return "getJavaScriptResult".equals(call.getNameAsString());
        }

        private boolean isScriptResultConstruction(ObjectCreationExpr creation) {
            return "ScriptResult".equals(creation.getType().getNameAsString());
        }
    }
}
