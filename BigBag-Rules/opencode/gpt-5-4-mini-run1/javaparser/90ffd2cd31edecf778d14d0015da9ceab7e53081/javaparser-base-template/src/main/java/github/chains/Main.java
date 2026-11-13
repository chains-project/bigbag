package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_LOGGER = "ch.qos.logback.classic.Logger";
    private static final String NEW_LOGGER = "org.slf4j.Logger";
    private static final String LEVEL_TYPE = "ch.qos.logback.classic.Level";
    private static final String APPENDER_TYPE = "ch.qos.logback.core.Appender";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                transform(path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transform(Path path) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(Files.readString(path, StandardCharsets.UTF_8));
        if (!referencesOldLogger(cu)) {
            return;
        }

        Set<String> loggerNames = new HashSet<>();

        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            if (isOldLoggerType(variable.getType(), cu)) {
                loggerNames.add(variable.getNameAsString());
                variable.setType(NEW_LOGGER);
            }
        }

        for (CastExpr castExpr : cu.findAll(CastExpr.class)) {
            if (isOldLoggerType(castExpr.getType(), cu)) {
                castExpr.replace(castExpr.getExpression());
            }
        }

        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getScope().isPresent() || !call.getScope().get().isNameExpr()) {
                continue;
            }

            String scopeName = call.getScope().get().asNameExpr().getNameAsString();
            if (!loggerNames.contains(scopeName)) {
                continue;
            }

            String methodName = call.getNameAsString();
            if (!methodName.equals("setLevel") && !methodName.equals("addAppender")) {
                continue;
            }

            MethodCallExpr helper = new MethodCallExpr(null, "invokeLogbackMethod");
            helper.addArgument(new NameExpr(scopeName));
            helper.addArgument(new StringLiteralExpr(methodName));

            ArrayInitializerExpr parameterTypes = new ArrayInitializerExpr();
            parameterTypes.setValues(NodeList.nodeList(new ClassExpr(StaticJavaParser.parseType(methodName.equals("setLevel") ? LEVEL_TYPE : APPENDER_TYPE))));
            helper.addArgument(parameterTypes);

            for (Expression arg : call.getArguments()) {
                helper.addArgument(arg.clone());
            }

            call.replace(helper);
            changed = true;
        }

        if (changed) {
            ensureHelper(cu);
            Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean referencesOldLogger(CompilationUnit cu) {
        return cu.toString().contains(OLD_LOGGER) || cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(OLD_LOGGER));
    }

    private static boolean isOldLoggerType(Type type, CompilationUnit cu) {
        if (type.isClassOrInterfaceType()) {
            String name = type.asClassOrInterfaceType().getNameWithScope();
            if (OLD_LOGGER.equals(name)) {
                return true;
            }
            return "Logger".equals(name) && cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(OLD_LOGGER));
        }
        return false;
    }

    private static void ensureHelper(CompilationUnit cu) {
        ClassOrInterfaceDeclaration owner = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner == null || !owner.getMethodsByName("invokeLogbackMethod").isEmpty()) {
            return;
        }

        MethodDeclaration method = owner.addMethod("invokeLogbackMethod",
                com.github.javaparser.ast.Modifier.Keyword.PRIVATE,
                com.github.javaparser.ast.Modifier.Keyword.STATIC);
        method.setType(void.class);
        method.addParameter(Object.class, "target");
        method.addParameter(String.class, "methodName");
        method.addParameter(StaticJavaParser.parseType("Class<?>[]"), "parameterTypes");
        method.addParameter(StaticJavaParser.parseType("Object[]"), "args");

        BlockStmt body = StaticJavaParser.parseBlock("{ try { target.getClass().getMethod(methodName, parameterTypes).invoke(target, args); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); } }");
        method.setBody(body);
    }
}
