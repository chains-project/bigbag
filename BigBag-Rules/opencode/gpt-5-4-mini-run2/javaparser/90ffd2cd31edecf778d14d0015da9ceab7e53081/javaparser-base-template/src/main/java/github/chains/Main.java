package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_LOGGER_FQN = "ch.qos.logback.classic.Logger";
    private static final String OLD_LEVEL_FQN = "ch.qos.logback.classic.Level";
    private static final String OLD_APPENDER_FQN = "ch.qos.logback.core.Appender";
    private static final Set<String> LOGGER_MUTATOR_NAMES = Set.of("setLevel", "addAppender");

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(1);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains(Path.of("target").toString()))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree " + root, e);
        }
    }

    private static void transformFile(Path javaFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile, StandardCharsets.UTF_8);
            LoggerMutationRewriter rewriter = new LoggerMutationRewriter();
            rewriter.visit(compilationUnit, null);
            if (!rewriter.changed()) {
                return;
            }
            rewriter.ensureHelperMethod(compilationUnit);
            removeOldLoggerImport(compilationUnit);
            Files.writeString(javaFile, compilationUnit.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static void removeOldLoggerImport(CompilationUnit compilationUnit) {
        compilationUnit.getImports().removeIf(importDeclaration ->
                importDeclaration.getNameAsString().equals(OLD_LOGGER_FQN));
    }

    private static boolean isOldLoggerType(Type type) {
        return type.isClassOrInterfaceType()
                && isOldLoggerType(type.asClassOrInterfaceType());
    }

    private static boolean isOldLoggerType(ClassOrInterfaceType type) {
        return OLD_LOGGER_FQN.equals(type.asString())
                || ("Logger".equals(type.getNameAsString()) && type.getScope().isEmpty());
    }

    private static final class LoggerMutationRewriter extends ModifierVisitor<Void> {
        private final Set<String> loggerNames = new HashSet<>();
        private boolean changed;
        private boolean helperNeeded;

        boolean changed() {
            return changed;
        }

        @Override
        public Visitable visit(MethodCallExpr methodCallExpr, Void arg) {
            if (isLoggerMutationCall(methodCallExpr)) {
                helperNeeded = true;
                changed = true;
                return buildReflectiveInvocation(methodCallExpr);
            }
            return super.visit(methodCallExpr, arg);
        }

        @Override
        public Visitable visit(ClassOrInterfaceType type, Void arg) {
            if (isOldLoggerType(type)) {
                changed = true;
                return StaticJavaParser.parseClassOrInterfaceType("Object");
            }
            return super.visit(type, arg);
        }

        @Override
        public Visitable visit(CastExpr castExpr, Void arg) {
            if (isOldLoggerType(castExpr.getType())) {
                changed = true;
                return castExpr.getExpression().accept(this, arg);
            }
            return super.visit(castExpr, arg);
        }

        @Override
        public Visitable visit(VariableDeclarator variableDeclarator, Void arg) {
            if (isOldLoggerType(variableDeclarator.getType())) {
                loggerNames.add(variableDeclarator.getNameAsString());
            }
            return super.visit(variableDeclarator, arg);
        }

        private boolean isLoggerMutationCall(MethodCallExpr methodCallExpr) {
            if (!LOGGER_MUTATOR_NAMES.contains(methodCallExpr.getNameAsString())) {
                return false;
            }
            if (methodCallExpr.getArguments().size() != 1) {
                return false;
            }
            return methodCallExpr.getScope()
                    .map(this::isTrackedLoggerScope)
                    .orElse(false);
        }

        private boolean isTrackedLoggerScope(Expression scope) {
            if (scope.isNameExpr()) {
                return loggerNames.contains(scope.asNameExpr().getNameAsString());
            }
            if (scope.isFieldAccessExpr()) {
                return loggerNames.contains(scope.asFieldAccessExpr().getNameAsString());
            }
            return false;
        }

        private MethodCallExpr buildReflectiveInvocation(MethodCallExpr methodCallExpr) {
            MethodCallExpr helperCall = new MethodCallExpr();
            helperCall.setName("invokeLoggerMethodReflectively");
            helperCall.addArgument(methodCallExpr.getScope().orElseThrow().clone());
            helperCall.addArgument(new StringLiteralExpr(methodCallExpr.getNameAsString()));

            String parameterType;
            if ("setLevel".equals(methodCallExpr.getNameAsString())) {
                parameterType = OLD_LEVEL_FQN;
            } else if ("addAppender".equals(methodCallExpr.getNameAsString())) {
                parameterType = OLD_APPENDER_FQN;
            } else {
                throw new IllegalArgumentException("Unsupported logger mutation: " + methodCallExpr.getNameAsString());
            }

            helperCall.addArgument(StaticJavaParser.parseExpression("new Class[] { " + parameterType + ".class }"));
            methodCallExpr.getArguments().forEach(argument -> helperCall.addArgument(argument.clone()));
            return helperCall;
        }

        void ensureHelperMethod(CompilationUnit compilationUnit) {
            if (!helperNeeded) {
                return;
            }

            List<ClassOrInterfaceDeclaration> declarations = compilationUnit.findAll(ClassOrInterfaceDeclaration.class);
            if (declarations.isEmpty()) {
                return;
            }

            ClassOrInterfaceDeclaration type = declarations.get(0);
            if (type.getMethodsByName("invokeLoggerMethodReflectively").size() > 0) {
                return;
            }

            type.addMember(StaticJavaParser.parseBodyDeclaration("private static void invokeLoggerMethodReflectively(Object target, String methodName, Class<?>[] parameterTypes, Object... args) { try { java.lang.reflect.Method method = target.getClass().getMethod(methodName, parameterTypes); method.invoke(target, args); } catch (ReflectiveOperationException e) { throw new IllegalStateException(\"Failed to invoke logger method \" + methodName, e); } }"));
        }

        @Override
        public Visitable visit(Parameter parameter, Void arg) {
            if (isOldLoggerType(parameter.getType())) {
                loggerNames.add(parameter.getNameAsString());
            }
            return super.visit(parameter, arg);
        }
    }
}
