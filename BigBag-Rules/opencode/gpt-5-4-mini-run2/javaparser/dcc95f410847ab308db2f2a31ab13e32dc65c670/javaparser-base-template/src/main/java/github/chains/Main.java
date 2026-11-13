package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String LOGBACK_LOGGER = "ch.qos.logback.classic.Logger";
    private static final String SLF4J_LOGGER = "org.slf4j.Logger";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path sourceRoot = Paths.get(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        try {
            try (var stream = Files.walk(sourceRoot)) {
                stream.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
            }
            javaFiles.sort(Comparator.naturalOrder());
            for (Path javaFile : javaFiles) {
                transformFile(javaFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, e);
        }
    }

    private static void transformFile(Path javaFile) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;
        Set<String> logbackLoggerNames = new HashSet<>();
        Set<ClassOrInterfaceDeclaration> classesNeedingHelper = new HashSet<>();

        for (var importDeclaration : compilationUnit.getImports()) {
            if (importDeclaration.getNameAsString().equals(LOGBACK_LOGGER)) {
                importDeclaration.setName(SLF4J_LOGGER);
                changed = true;
            }
        }

        for (VariableDeclarator variableDeclarator : compilationUnit.findAll(VariableDeclarator.class)) {
            if (isLogbackLoggerType(variableDeclarator.getType())) {
                logbackLoggerNames.add(variableDeclarator.getNameAsString());
                variableDeclarator.setType(SLF4J_LOGGER);
                changed = true;

                if (variableDeclarator.getInitializer().isPresent() && variableDeclarator.getInitializer().get().isCastExpr()) {
                    CastExpr castExpr = variableDeclarator.getInitializer().get().asCastExpr();
                    if (isLogbackLoggerType(castExpr.getType())) {
                        variableDeclarator.setInitializer(castExpr.getExpression());
                        changed = true;
                    }
                }
            }
        }

        for (CastExpr castExpr : compilationUnit.findAll(CastExpr.class)) {
            if (isLogbackLoggerType(castExpr.getType())) {
                castExpr.replace(castExpr.getExpression());
                changed = true;
            }
        }

        for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!methodCallExpr.getScope().isPresent()) {
                continue;
            }

            String methodName = methodCallExpr.getNameAsString();
            if (!methodName.equals("setLevel") && !methodName.equals("addAppender")) {
                continue;
            }

            Expression scope = methodCallExpr.getScope().get();
            if (!referencesLogbackLogger(scope, logbackLoggerNames)) {
                continue;
            }

            String parameterType = methodName.equals("setLevel")
                    ? "ch.qos.logback.classic.Level.class"
                    : "ch.qos.logback.core.Appender.class";

            ClassOrInterfaceDeclaration enclosingClass = methodCallExpr.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
            if (enclosingClass != null) {
                classesNeedingHelper.add(enclosingClass);
            }

            String replacement = String.format(
                    "invokeLogbackMethod(%s, \"%s\", new Class<?>[]{%s}, new Object[]{%s})",
                    scope,
                    methodName,
                    parameterType,
                    methodCallExpr.getArgument(0));
            methodCallExpr.replace(StaticJavaParser.parseExpression(replacement));
            changed = true;
        }

        if (!classesNeedingHelper.isEmpty()) {
            for (ClassOrInterfaceDeclaration classDeclaration : classesNeedingHelper) {
                if (classDeclaration.getMethodsByName("invokeLogbackMethod").isEmpty()) {
                    classDeclaration.addMember(StaticJavaParser.parseBodyDeclaration(
                            "private static void invokeLogbackMethod(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {\n" +
                            "    try {\n" +
                            "        java.lang.reflect.Method method = target.getClass().getMethod(methodName, parameterTypes);\n" +
                            "        method.invoke(target, args);\n" +
                            "    } catch (ReflectiveOperationException e) {\n" +
                            "        throw new IllegalStateException(\"Unable to invoke logback logger method \" + methodName, e);\n" +
                            "    }\n" +
                            "}"));
                    changed = true;
                }
            }
        }

        if (changed) {
            Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }

    private static boolean isLogbackLoggerType(Type type) {
        if (!type.isClassOrInterfaceType()) {
            return false;
        }
        ClassOrInterfaceType classType = type.asClassOrInterfaceType();
        return classType.asString().equals(LOGBACK_LOGGER) || classType.getNameAsString().equals("Logger");
    }

    private static boolean referencesLogbackLogger(Expression expression, Set<String> logbackLoggerNames) {
        if (expression.isNameExpr()) {
            return logbackLoggerNames.contains(expression.asNameExpr().getNameAsString());
        }
        if (expression.isFieldAccessExpr()) {
            return logbackLoggerNames.contains(expression.asFieldAccessExpr().getNameAsString());
        }
        if (expression.isCastExpr()) {
            return isLogbackLoggerType(expression.asCastExpr().getType());
        }
        if (expression.isMethodCallExpr()) {
            return expression.toString().contains("LoggerFactory.getLogger");
        }
        return false;
    }

}
