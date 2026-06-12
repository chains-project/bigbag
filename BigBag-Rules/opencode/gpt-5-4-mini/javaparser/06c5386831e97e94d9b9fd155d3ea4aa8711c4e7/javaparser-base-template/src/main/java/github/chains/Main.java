package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static final String OLD_SCRIPT_RESULT_FQN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_GETTER = "getJavaScriptResult";
    private static final String NEW_RESULT_TYPE = "Object";

    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try {
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewriteIfNeeded);
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to traverse source tree: " + root, exception);
        }
    }

    private static void rewriteIfNeeded(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;
            Map<String, Expression> scriptResultVariables = new HashMap<>();

            changed |= collectAndRewriteOldScriptResultDeclarations(cu, scriptResultVariables);
            changed |= removeOldScriptResultImport(cu);
            changed |= replaceJavaScriptResultCalls(cu, scriptResultVariables);

            if (changed) {
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to rewrite " + path, exception);
        }
    }

    private static boolean removeOldScriptResultImport(CompilationUnit cu) {
        return cu.getImports().removeIf(Main::isOldScriptResultImport);
    }

    private static boolean isOldScriptResultImport(ImportDeclaration importDeclaration) {
        return !importDeclaration.isAsterisk() && OLD_SCRIPT_RESULT_FQN.equals(importDeclaration.getNameAsString());
    }

    private static boolean collectAndRewriteOldScriptResultDeclarations(CompilationUnit cu,
            Map<String, Expression> scriptResultVariables) {
        final boolean[] changed = {false};
        cu.findAll(VariableDeclarator.class).forEach(variable -> {
            if (variable.getType().isPrimitiveType() || variable.getInitializer().isEmpty()) {
                return;
            }

            Expression initializer = variable.getInitializer().get();
            if (!(initializer instanceof ObjectCreationExpr)) {
                return;
            }

            ObjectCreationExpr creationExpr = (ObjectCreationExpr) initializer;
            if (!isOldScriptResultCreation(creationExpr) || creationExpr.getArguments().isEmpty()) {
                return;
            }

            scriptResultVariables.put(variable.getNameAsString(), creationExpr.getArgument(0).clone());
            variable.setType(new ClassOrInterfaceType(null, NEW_RESULT_TYPE));
            variable.setInitializer(creationExpr.getArgument(0).clone());
            changed[0] = true;
        });
        return changed[0];
    }

    private static boolean replaceJavaScriptResultCalls(CompilationUnit cu, Map<String, Expression> scriptResultVariables) {
        final boolean[] changed = {false};
        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            if (!OLD_GETTER.equals(methodCall.getNameAsString()) || methodCall.getScope().isEmpty()) {
                return;
            }

            Expression scope = methodCall.getScope().get();
            if (scope instanceof ObjectCreationExpr) {
                ObjectCreationExpr creationExpr = (ObjectCreationExpr) scope;
                if (!isOldScriptResultCreation(creationExpr) || creationExpr.getArguments().isEmpty()) {
                    return;
                }

                methodCall.replace(creationExpr.getArgument(0).clone());
                changed[0] = true;
                return;
            }

            if (scope.isNameExpr() && scriptResultVariables.containsKey(scope.asNameExpr().getNameAsString())) {
                methodCall.replace(scope.clone());
                changed[0] = true;
            }
        });
        return changed[0];
    }

    private static boolean isOldScriptResultCreation(ObjectCreationExpr creationExpr) {
        String typeName = creationExpr.getType().getNameWithScope();
        return "ScriptResult".equals(typeName) || OLD_SCRIPT_RESULT_FQN.equals(typeName);
    }
}
