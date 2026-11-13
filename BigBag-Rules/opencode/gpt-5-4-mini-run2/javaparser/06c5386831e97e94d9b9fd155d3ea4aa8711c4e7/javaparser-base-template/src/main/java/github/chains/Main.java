package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

public class Main {
    private static final String OLD_WRAPPER_FQCN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_WRAPPER_SIMPLE_NAME = "ScriptResult";
    private static final String WRAPPED_VALUE_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path root = Paths.get(args[0]);
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try {
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> transform(parser, path));
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform source tree: " + root, exception);
        }
    }

    private static void transform(JavaParser parser, Path path) {
        try {
            CompilationUnit cu = parser.parse(path).getResult()
                    .orElseThrow(() -> new IllegalStateException("Could not parse " + path));
            LexicalPreservingPrinter.setup(cu);

            OldScriptResultTransformer transformer = new OldScriptResultTransformer();
            boolean changed = transformer.apply(cu) | cu.getImports().removeIf(Main::isOldWrapperImport);

            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (Exception exception) {
            throw new RuntimeException("Failed to transform " + path, exception);
        }
    }

    private static boolean isOldWrapperImport(ImportDeclaration importDeclaration) {
        return importDeclaration.getNameAsString().equals(OLD_WRAPPER_FQCN);
    }

    private static final class OldScriptResultTransformer {
        boolean apply(CompilationUnit cu) {
            boolean changed = false;

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (WRAPPED_VALUE_METHOD.equals(call.getNameAsString()) && call.getArguments().isEmpty() && call.getScope().isPresent()) {
                    call.replace(call.getScope().get());
                    changed = true;
                }
            }

            for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
                if (isOldScriptResultCreation(creation) && !creation.getArguments().isEmpty()) {
                    creation.replace(creation.getArgument(0));
                    changed = true;
                }
            }

            for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
                if (isOldScriptResultType(variable.getType())) {
                    variable.setType("Object");
                    changed = true;
                }
            }
            return changed;
        }

        private boolean isOldScriptResultCreation(ObjectCreationExpr expression) {
            return isOldScriptResultType(expression.getType());
        }
        private boolean isOldScriptResultType(Type type) {
            String name = type.asString();
            return OLD_WRAPPER_SIMPLE_NAME.equals(name) || OLD_WRAPPER_FQCN.equals(name);
        }
    }
}
