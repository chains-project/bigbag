package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try {
            List<Path> javaFiles = new ArrayList<>();
            try (var paths = Files.walk(sourceRoot)) {
                paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .forEach(javaFiles::add);
            }

            for (Path file : javaFiles) {
                String original = Files.readString(file, StandardCharsets.UTF_8);
                CompilationUnit cu = StaticJavaParser.parse(original);
                LexicalPreservingPrinter.setup(cu);

                boolean changed = false;
                for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                    changed |= removeDeprecatedIntrospectionExceptionThrows(method);
                }

                if (changed) {
                    Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean removeDeprecatedIntrospectionExceptionThrows(MethodDeclaration method) {
        NodeList<ReferenceType> thrownExceptions = method.getThrownExceptions();
        if (thrownExceptions.isEmpty()) {
            return false;
        }

        boolean isGetPropertiesOverride = "getProperties".equals(method.getNameAsString())
                && method.getParameters().size() == 1
                && method.getParameter(0).getType() instanceof ClassOrInterfaceType;
        boolean callsSuperGetProperties = method.findAll(MethodCallExpr.class).stream()
                .anyMatch(call -> call.getScope().isPresent()
                        && call.getScope().get() instanceof SuperExpr
                        && "getProperties".equals(call.getNameAsString()));

        if (!isGetPropertiesOverride && !callsSuperGetProperties) {
            return false;
        }

        NodeList<ReferenceType> updated = new NodeList<>();
        boolean removed = false;
        for (ReferenceType thrownException : thrownExceptions) {
            if (isIntrospectionException(thrownException)) {
                removed = true;
            } else {
                updated.add(thrownException);
            }
        }

        if (!removed) {
            return false;
        }

        method.setThrownExceptions(updated);
        return true;
    }

    private static boolean isIntrospectionException(ReferenceType type) {
        String name = type.toString();
        return name.equals("IntrospectionException") || name.endsWith(".IntrospectionException");
    }

}
