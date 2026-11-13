package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class Main {
    private static final String OLD_LOGGER_FQN = "ch.qos.logback.classic.Logger";
    private static final String NEW_LOGGER_FQN = "org.slf4j.Logger";

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(Files.readString(file));
            Set<String> oldLoggerVariables = new HashSet<>();
            Set<ClassOrInterfaceDeclaration> touchedClasses = new HashSet<>();

            cu.accept(new ModifierVisitor<Set<ClassOrInterfaceDeclaration>>() {
                @Override
                public Visitable visit(VariableDeclarationExpr n, Set<ClassOrInterfaceDeclaration> touched) {
                    VariableDeclarationExpr updated = (VariableDeclarationExpr) super.visit(n, touched);
                    if (isOldLoggerType(updated.getElementType())) {
                        updated.setAllTypes(StaticJavaParser.parseType(NEW_LOGGER_FQN));
                        updated.getVariables().forEach(v -> oldLoggerVariables.add(v.getNameAsString()));
                        enclosingClass(updated).ifPresent(touched::add);
                    }
                    return updated;
                }

                @Override
                public Visitable visit(CastExpr n, Set<ClassOrInterfaceDeclaration> touched) {
                    CastExpr updated = (CastExpr) super.visit(n, touched);
                    if (isOldLoggerType(updated.getType())) {
                        updated.setType(NEW_LOGGER_FQN);
                        enclosingClass(updated).ifPresent(touched::add);
                    }
                    return updated;
                }

                @Override
                public Visitable visit(MethodCallExpr n, Set<ClassOrInterfaceDeclaration> touched) {
                    MethodCallExpr updated = (MethodCallExpr) super.visit(n, touched);
                    if (updated.getScope().isPresent() && updated.getScope().get().isNameExpr()) {
                        String scopeName = updated.getScope().get().asNameExpr().getNameAsString();
                        if (oldLoggerVariables.contains(scopeName) && isLoggerMutator(updated.getNameAsString())) {
                            enclosingClass(updated).ifPresent(touched::add);
                            return buildReflectiveCall(scopeName, updated);
                        }
                    }
                    return updated;
                }
            }, touchedClasses);

            if (!touchedClasses.isEmpty()) {
                normalizeImports(cu);
                touchedClasses.forEach(Main::ensureHelperMethod);
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean isOldLoggerType(Type type) {
        return type.isClassOrInterfaceType() && (
                OLD_LOGGER_FQN.equals(type.asClassOrInterfaceType().toString()) ||
                "Logger".equals(type.asClassOrInterfaceType().getNameAsString())
        );
    }

    private static boolean isLoggerMutator(String name) {
        return "setLevel".equals(name) || "addAppender".equals(name);
    }

    private static MethodCallExpr buildReflectiveCall(String scopeName, MethodCallExpr original) {
        String methodName = original.getNameAsString();
        String paramType = "setLevel".equals(methodName)
                ? "ch.qos.logback.classic.Level.class"
                : "ch.qos.logback.core.Appender.class";
        String args = original.getArguments().isEmpty() ? "" : ", " + original.getArgument(0).toString();
        return StaticJavaParser.parseExpression(
                "invokeLogbackMethod(" + scopeName + ", \"" + methodName + "\", new Class<?>[]{" + paramType + "}" + args + ")"
        ).asMethodCallExpr();
    }

    private static void normalizeImports(CompilationUnit cu) {
        cu.getImports().removeIf(i -> i.getNameAsString().equals(OLD_LOGGER_FQN));
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_LOGGER_FQN))) {
            cu.addImport(NEW_LOGGER_FQN);
        }
    }

    private static Optional<ClassOrInterfaceDeclaration> enclosingClass(Node node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class);
    }

    private static void ensureHelperMethod(ClassOrInterfaceDeclaration clazz) {
        if (!clazz.getMethodsByName("invokeLogbackMethod").isEmpty()) {
            return;
        }
        MethodDeclaration helper = StaticJavaParser.parseBodyDeclaration(
                "private static void invokeLogbackMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {\n" +
                "    try {\n" +
                "        target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);\n" +
                "    } catch (ReflectiveOperationException e) {\n" +
                "        throw new RuntimeException(e);\n" +
                "    }\n" +
                "}").asMethodDeclaration();
        clazz.addMember(helper);
    }
}
