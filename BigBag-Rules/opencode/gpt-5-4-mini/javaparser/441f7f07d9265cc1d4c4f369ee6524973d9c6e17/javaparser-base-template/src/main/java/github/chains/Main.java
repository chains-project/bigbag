package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";
    private static final String OLD_IMPORT = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(final String[] args) throws IOException {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewrite);
        }
    }

    private static void rewrite(final Path file) {
        try {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(original).getResult()
                    .orElseThrow(() -> new IllegalStateException("Could not parse " + file));

            LexicalPreservingPrinter.setup(cu);
            Map<String, Expression> scriptResultVars = new HashMap<>();
            boolean changed = false;

            for (VariableDeclarator var : cu.findAll(VariableDeclarator.class)) {
                if (OLD_TYPE.equals(var.getType().asString()) && var.getInitializer().isPresent()) {
                    Optional<Expression> replacement = unwrapScriptResult(var.getInitializer().get());
                    if (replacement.isPresent()) {
                        scriptResultVars.put(var.getNameAsString(), replacement.get().clone());
                        var.setType(new ClassOrInterfaceType(null, "Object"));
                        var.setInitializer(replacement.get().clone());
                        changed = true;
                    }
                }
            }

            for (MethodCallExpr methodCall : cu.findAll(MethodCallExpr.class)) {
                if (!OLD_METHOD.equals(methodCall.getNameAsString())) {
                    continue;
                }

                Optional<Expression> scope = methodCall.getScope();
                if (!scope.isPresent()) {
                    continue;
                }

                Optional<Expression> replacement = unwrapScriptResult(scope.get());
                if (replacement.isPresent()) {
                    methodCall.replace(replacement.get().clone());
                    changed = true;
                    continue;
                }

                if (scope.get().isNameExpr()) {
                    String name = scope.get().asNameExpr().getNameAsString();
                    if (scriptResultVars.containsKey(name)) {
                        methodCall.replace(scriptResultVars.get(name).clone());
                        changed = true;
                    }
                }
            }

            if (changed) {
                cu.getImports().removeIf(importDecl -> OLD_IMPORT.equals(importDecl.getNameAsString())
                        || importDecl.getNameAsString().endsWith('.' + OLD_TYPE));
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to rewrite " + file, exception);
        }
    }

    private static Optional<Expression> unwrapScriptResult(final Expression expression) {
        if (expression.isObjectCreationExpr()) {
            ObjectCreationExpr creation = expression.asObjectCreationExpr();
            if (OLD_TYPE.equals(creation.getType().getNameAsString()) && !creation.getArguments().isEmpty()) {
                return Optional.of(creation.getArgument(0));
            }
        }
        if (expression.isMethodCallExpr()) {
            MethodCallExpr call = expression.asMethodCallExpr();
            if (OLD_METHOD.equals(call.getNameAsString()) && call.getScope().isPresent()) {
                return unwrapScriptResult(call.getScope().get());
            }
        }
        return Optional.empty();
    }
}
