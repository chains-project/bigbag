package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        try {
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean usesLegacyScriptResult = cu.getImports().stream()
                    .anyMatch(importDeclaration -> importDeclaration.getNameAsString()
                            .equals("com.gargoylesoftware.htmlunit.ScriptResult"));
            if (!usesLegacyScriptResult) {
                return;
            }

            cu.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString()
                    .equals("com.gargoylesoftware.htmlunit.ScriptResult"));

            cu.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.getNameAsString().equals("getJavaScriptResult"))
                    .forEach(call -> call.getScope().ifPresent(scope -> call.replace(scope.clone())));

            cu.findAll(ObjectCreationExpr.class).stream()
                    .filter(Main::isLegacyScriptResultCreation)
                    .forEach(creation -> creation.replace(creation.getArgument(0).clone()));

            cu.findAll(ClassOrInterfaceType.class).stream()
                    .filter(type -> type.getNameAsString().equals("ScriptResult"))
                    .filter(type -> !type.getParentNode().map(parent -> parent instanceof ObjectCreationExpr).orElse(false))
                    .forEach(type -> type.replace(new ClassOrInterfaceType(null, "Object")));

            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static boolean isLegacyScriptResultCreation(ObjectCreationExpr creation) {
        return creation.getType().getNameAsString().equals("ScriptResult")
                && creation.getArguments().size() == 1;
    }
}
