package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final String LOGBACK_LOGGER = "ch.qos.logback.classic.Logger";
    private static final String SLF4J_LOGGER = "org.slf4j.Logger";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected source directory argument");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            Set<String> loggerNames = collectLoggerNames(cu);
            boolean changed = false;

            for (CastExpr castExpr : cu.findAll(CastExpr.class)) {
                if (isLogbackLoggerType(castExpr.getType(), cu)) {
                    castExpr.replace(castExpr.getExpression());
                    changed = true;
                }
            }

            for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
                if (isLogbackLoggerType(variable.getType(), cu)) {
                    variable.setType(SLF4J_LOGGER);
                    changed = true;
                }
            }

            for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                if (isLogbackLoggerType(field.getElementType(), cu)) {
                    field.getVariables().forEach(v -> v.setType(SLF4J_LOGGER));
                    changed = true;
                }
            }

            for (Parameter parameter : cu.findAll(Parameter.class)) {
                if (isLogbackLoggerType(parameter.getType(), cu)) {
                    parameter.setType(SLF4J_LOGGER);
                    changed = true;
                }
            }

            if (!loggerNames.isEmpty()) {
                for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                    Optional<Expression> scope = call.getScope();
                    if (scope.isEmpty() || !(scope.get() instanceof NameExpr)) {
                        continue;
                    }

                    String scopeName = ((NameExpr) scope.get()).getNameAsString();
                    if (!loggerNames.contains(scopeName)) {
                        continue;
                    }

                    if (call.getArguments().size() == 1 && "setLevel".equals(call.getNameAsString())) {
                        call.replace(helperCall("invokeLogbackSetLevel", scope.get(), call.getArgument(0)));
                        changed = true;
                    } else if (call.getArguments().size() == 1 && "addAppender".equals(call.getNameAsString())) {
                        call.replace(helperCall("invokeLogbackAddAppender", scope.get(), call.getArgument(0)));
                        changed = true;
                    }
                }
            }

            if (changed) {
                ensureHelpers(cu);
                cu.getImports().removeIf(i -> i.getNameAsString().equals(LOGBACK_LOGGER));
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static Set<String> collectLoggerNames(CompilationUnit cu) {
        Set<String> names = new HashSet<>();
        cu.findAll(VariableDeclarator.class).forEach(v -> {
            if (isLogbackLoggerType(v.getType(), cu)) {
                names.add(v.getNameAsString());
            }
        });
        cu.findAll(FieldDeclaration.class).forEach(f -> {
            if (isLogbackLoggerType(f.getElementType(), cu)) {
                f.getVariables().forEach(v -> names.add(v.getNameAsString()));
            }
        });
        cu.findAll(Parameter.class).forEach(p -> {
            if (isLogbackLoggerType(p.getType(), cu)) {
                names.add(p.getNameAsString());
            }
        });
        return names;
    }

    private static boolean isLogbackLoggerType(Type type, CompilationUnit cu) {
        if (!type.isClassOrInterfaceType()) {
            return false;
        }
        ClassOrInterfaceType t = type.asClassOrInterfaceType();
        if (LOGBACK_LOGGER.equals(t.asString())) {
            return true;
        }
        return "Logger".equals(t.getNameAsString()) && cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().equals(LOGBACK_LOGGER));
    }

    private static MethodCallExpr helperCall(String helperName, Expression logger, Expression arg) {
        MethodCallExpr helper = new MethodCallExpr(null, helperName);
        helper.addArgument(logger.clone());
        helper.addArgument(arg.clone());
        return helper;
    }

    private static void ensureHelpers(CompilationUnit cu) {
        ClassOrInterfaceDeclaration type = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
        if (type == null) {
            return;
        }

        if (type.getMethodsByName("invokeLogbackSetLevel").isEmpty()) {
            type.addMember(StaticJavaParser.parseBodyDeclaration(
                    "private static void invokeLogbackSetLevel(org.slf4j.Logger logger, ch.qos.logback.classic.Level level) {\n" +
                    "    try {\n" +
                    "        logger.getClass().getMethod(\"setLevel\", ch.qos.logback.classic.Level.class).invoke(logger, level);\n" +
                    "    } catch (ReflectiveOperationException e) {\n" +
                    "        throw new RuntimeException(e);\n" +
                    "    }\n" +
                    "}"));
        }

        if (type.getMethodsByName("invokeLogbackAddAppender").isEmpty()) {
            type.addMember(StaticJavaParser.parseBodyDeclaration(
                    "private static void invokeLogbackAddAppender(org.slf4j.Logger logger, ch.qos.logback.core.Appender<?> appender) {\n" +
                    "    try {\n" +
                    "        logger.getClass().getMethod(\"addAppender\", ch.qos.logback.core.Appender.class).invoke(logger, appender);\n" +
                    "    } catch (ReflectiveOperationException e) {\n" +
                    "        throw new RuntimeException(e);\n" +
                    "    }\n" +
                    "}"));
        }
    }
}
