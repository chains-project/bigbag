package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    private static final String SCRIPT_RESULT_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String GET_JS_RESULT = "getJavaScriptResult";

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());
        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> transform(parser, path));
        }
    }

    private static void transform(final JavaParser parser, final Path path) {
        try {
            CompilationUnit cu = parser.parse(path, StandardCharsets.UTF_8).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            LexicalPreservingPrinter.setup(cu);
            boolean[] changed = {false};

            cu.getImports().removeIf(importDecl -> {
                boolean match = SCRIPT_RESULT_TYPE.equals(importDecl.getNameAsString());
                changed[0] |= match;
                return match;
            });

            cu.findAll(VariableDeclarator.class).forEach(variable -> {
                if (isScriptResultType(variable.getType().asString()) && variable.getInitializer().isPresent()) {
                    Optional<Expression> replacement = unwrapScriptResult(variable.getInitializer().get());
                    if (replacement.isPresent()) {
                        variable.setType("Object");
                        variable.setInitializer(replacement.get().clone());
                        changed[0] = true;
                    }
                }
            });

            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (!GET_JS_RESULT.equals(call.getNameAsString()) || call.getScope().isEmpty()) {
                    return;
                }

                Expression scope = call.getScope().get();
                Optional<Expression> replacement = unwrapScriptResult(scope);
                if (replacement.isPresent()) {
                    call.replace(replacement.get().clone());
                    changed[0] = true;
                    return;
                }

                if (scope.isNameExpr()) {
                    call.replace(scope.clone());
                    changed[0] = true;
                }
            });

            cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
                if (isScriptResultType(creation.getType().asString())) {
                    Optional<Expression> replacement = unwrapScriptResult(creation);
                    if (replacement.isPresent()) {
                        creation.replace(replacement.get().clone());
                        changed[0] = true;
                    }
                }
            });

            if (changed[0]) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean isScriptResultType(final String typeName) {
        return SCRIPT_RESULT_TYPE.equals(typeName) || typeName.endsWith(".ScriptResult");
    }

    private static Optional<Expression> unwrapScriptResult(final Expression expression) {
        if (expression.isMethodCallExpr()) {
            MethodCallExpr call = expression.asMethodCallExpr();
            if ("toString".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                return unwrapScriptResult(call.getScope().get());
            }
        }
        if (expression instanceof ObjectCreationExpr) {
            ObjectCreationExpr creation = (ObjectCreationExpr) expression;
            if (isScriptResultType(creation.getType().asString()) && creation.getArguments().size() == 1) {
                return Optional.of(creation.getArgument(0));
            }
        }
        if (expression instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expression;
            if (GET_JS_RESULT.equals(call.getNameAsString()) && call.getScope().isPresent()) {
                return unwrapScriptResult(call.getScope().get());
            }
        }
        if (expression instanceof NameExpr) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
