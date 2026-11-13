package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Main {
    private static final String OLD_SCRIPT_RESULT_FQN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SCRIPT_RESULT_SIMPLE = "ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(final String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        JavaParser parser = new JavaParser(new ParserConfiguration());

        Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        transformFile(parser, path);
                    }
                    catch (IOException exception) {
                        throw new RuntimeException("Failed to transform " + path, exception);
                    }
                });
    }

    private static void transformFile(final JavaParser parser, final Path file) throws IOException {
        CompilationUnit cu = parser.parse(file).getResult().orElse(null);
        if (cu == null || !usesOldScriptResult(cu)) {
            return;
        }

        LexicalPreservingPrinter.setup(cu);
        Set<String> oldTypedVariables = collectOldTypedVariables(cu);
        boolean[] changed = {false};

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (OLD_METHOD.equals(call.getNameAsString()) && call.getArguments().isEmpty() && call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                if (isOldScriptResultConstruction(scope)) {
                    call.replace(scope.asObjectCreationExpr().getArgument(0).clone());
                    changed[0] = true;
                }
                else if (isOldScriptResultVariable(scope, oldTypedVariables)) {
                    call.replace(scope.clone());
                    changed[0] = true;
                }
            }
        });

        cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
            if (isOldScriptResultType(creation.getType())) {
                Expression replacement = creation.getArguments().isEmpty() ? creation.clone() : creation.getArgument(0).clone();
                creation.replace(replacement);
                changed[0] = true;
            }
        });

        cu.findAll(VariableDeclarator.class).forEach(variable -> {
            if (isOldScriptResultType(variable.getType())) {
                variable.setType("Object");
                if (variable.getInitializer().isPresent() && variable.getInitializer().get().isObjectCreationExpr()) {
                    ObjectCreationExpr creation = variable.getInitializer().get().asObjectCreationExpr();
                    if (isOldScriptResultType(creation.getType()) && !creation.getArguments().isEmpty()) {
                        variable.setInitializer(creation.getArgument(0).clone());
                    }
                }
                changed[0] = true;
            }
        });

        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            if (isOldScriptResultType(type)) {
                type.replace(new ClassOrInterfaceType(null, "Object"));
                changed[0] = true;
            }
        });

        cu.getImports().removeIf(importDeclaration -> OLD_SCRIPT_RESULT_FQN.equals(importDeclaration.getNameAsString()));

        if (changed[0]) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> collectOldTypedVariables(final CompilationUnit cu) {
        Set<String> names = new HashSet<>();
        cu.findAll(VariableDeclarator.class).forEach(variable -> {
            if (isOldScriptResultType(variable.getType())) {
                names.add(variable.getNameAsString());
            }
        });
        return names;
    }

    private static boolean usesOldScriptResult(final CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(importDeclaration -> OLD_SCRIPT_RESULT_FQN.equals(importDeclaration.getNameAsString()))
                || cu.findAll(ClassOrInterfaceType.class).stream().anyMatch(Main::isOldScriptResultType)
                || cu.findAll(ObjectCreationExpr.class).stream().anyMatch(expr -> isOldScriptResultType(expr.getType()))
                || cu.findAll(MethodCallExpr.class).stream().anyMatch(call -> OLD_METHOD.equals(call.getNameAsString()));
    }

    private static boolean isOldScriptResultVariable(final Expression scope, final Set<String> oldTypedVariables) {
        return scope.isNameExpr() && oldTypedVariables.contains(scope.asNameExpr().getNameAsString());
    }

    private static boolean isOldScriptResultConstruction(final Expression scope) {
        return scope.isObjectCreationExpr() && isOldScriptResultType(scope.asObjectCreationExpr().getType());
    }

    private static boolean isOldScriptResultType(final ClassOrInterfaceType type) {
        return OLD_SCRIPT_RESULT_FQN.equals(type.asString()) || OLD_SCRIPT_RESULT_SIMPLE.equals(type.getNameAsString());
    }

    private static boolean isOldScriptResultType(final Type type) {
        return type.isClassOrInterfaceType() && isOldScriptResultType(type.asClassOrInterfaceType());
    }
}
