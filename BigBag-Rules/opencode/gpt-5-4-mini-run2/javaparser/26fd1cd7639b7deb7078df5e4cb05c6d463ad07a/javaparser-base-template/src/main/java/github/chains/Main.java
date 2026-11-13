package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (var paths = Files.walk(root)) {
            paths.filter(Main::isJavaSource)
                    .forEach(path -> rewriteFile(parser, path));
        }
    }

    private static boolean isJavaSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith(".java") && !normalized.contains("/target/");
    }

    private static void rewriteFile(JavaParser parser, Path path) {
        try {
            Optional<CompilationUnit> parsed = parser.parse(path).getResult();
            if (parsed.isEmpty()) {
                return;
            }

            CompilationUnit cu = parsed.get();
            LexicalPreservingPrinter.setup(cu);

            if (transform(cu)) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to rewrite " + path, ex);
        }
    }

    private static boolean transform(CompilationUnit cu) {
        boolean changed = false;
        Map<String, Expression> wrappedByVariable = new HashMap<>();

        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            Optional<Expression> initializer = variable.getInitializer();
            if (initializer.isEmpty()) {
                continue;
            }

            Expression init = initializer.get();
            if (!isScriptResultType(variable.getType()) || !isScriptResultCreation(init)) {
                continue;
            }

            Expression wrapped = unwrapScriptResult(init).clone();
            wrappedByVariable.put(variable.getNameAsString(), wrapped.clone());
            variable.setType(new ClassOrInterfaceType(null, "Object"));
            variable.setInitializer(wrapped);
            changed = true;
        }

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"getJavaScriptResult".equals(call.getNameAsString()) || call.getScope().isEmpty()) {
                continue;
            }

            Expression scope = call.getScope().get();
            if (isScriptResultCreation(scope)) {
                call.replace(unwrapScriptResult(scope).clone());
                changed = true;
            } else if (scope.isNameExpr() && wrappedByVariable.containsKey(scope.asNameExpr().getNameAsString())) {
                call.replace(scope.clone());
                changed = true;
            }
        }

        if (cu.getImports().removeIf(i -> OLD_SCRIPT_RESULT.equals(i.getNameAsString()))) {
            changed = true;
        }

        return changed;
    }

    private static boolean isScriptResultType(Type type) {
        return type.isClassOrInterfaceType() && matchesScriptResult(type.asClassOrInterfaceType());
    }

    private static boolean matchesScriptResult(ClassOrInterfaceType type) {
        return type.getNameAsString().equals("ScriptResult") || OLD_SCRIPT_RESULT.equals(type.getNameWithScope());
    }

    private static boolean isScriptResultCreation(Expression expression) {
        return expression.isObjectCreationExpr() && matchesScriptResult(expression.asObjectCreationExpr());
    }

    private static boolean matchesScriptResult(ObjectCreationExpr creation) {
        return creation.getType().getNameAsString().equals("ScriptResult")
                || OLD_SCRIPT_RESULT.equals(creation.getType().getNameWithScope());
    }

    private static Expression unwrapScriptResult(Expression expression) {
        ObjectCreationExpr creation = expression.asObjectCreationExpr();
        if (creation.getArguments().size() != 1) {
            throw new IllegalArgumentException("Unexpected ScriptResult constructor shape: " + creation);
        }
        return creation.getArgument(0);
    }
}
