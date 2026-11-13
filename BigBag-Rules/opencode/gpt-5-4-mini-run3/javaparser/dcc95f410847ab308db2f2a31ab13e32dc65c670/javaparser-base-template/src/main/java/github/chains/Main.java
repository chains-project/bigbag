package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String CLASSIC_LOGGER = "ch.qos.logback.classic.Logger";
    private static final String SLF4J_LOGGER = "org.slf4j.Logger";
    private static final String HELPER_METHOD = "invokeLoggerMethod";
    private static final Set<String> CLASSIC_ONLY_METHODS = Set.of(
            "addAppender",
            "detachAppender",
            "detachAndStopAllAppenders",
            "setAdditive",
            "setLevel"
    );

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).collect(Collectors.toList())) {
                transform(parser, file);
            }
        }
    }

    private static void transform(JavaParser parser, Path file) throws IOException {
        var parsed = parser.parse(file);
        if (parsed.getResult().isEmpty()) {
            return;
        }

        CompilationUnit cu = parsed.getResult().get();
        LexicalPreservingPrinter.setup(cu);

        boolean usesClassicLogger = cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(CLASSIC_LOGGER))
                || cu.findAll(ClassOrInterfaceType.class).stream().anyMatch(t -> t.asString().equals("Logger"));
        if (!usesClassicLogger) {
            return;
        }

        boolean changed = false;
        changed |= replaceClassicLoggerImport(cu);
        changed |= removeClassicLoggerCast(cu);
        changed |= rewriteClassicLoggerCalls(cu);

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean replaceClassicLoggerImport(CompilationUnit cu) {
        boolean removed = cu.getImports().removeIf(i -> i.getNameAsString().equals(CLASSIC_LOGGER));
        if (removed) {
            ensureImport(cu, SLF4J_LOGGER);
        }
        return removed;
    }

    private static boolean removeClassicLoggerCast(CompilationUnit cu) {
        boolean changed = false;
        for (CastExpr castExpr : new ArrayList<>(cu.findAll(CastExpr.class))) {
            if (castExpr.getType().isClassOrInterfaceType()
                    && castExpr.getType().asClassOrInterfaceType().asString().equals("Logger")) {
                castExpr.replace(castExpr.getExpression());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteClassicLoggerCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : new ArrayList<>(cu.findAll(MethodCallExpr.class))) {
            if (call.getScope().isEmpty() || !CLASSIC_ONLY_METHODS.contains(call.getNameAsString())) {
                continue;
            }

            String replacement = buildReflectionCall(call.getScope().get(), call.getNameAsString(), call.getArguments().stream().collect(Collectors.toList()));
            call.replace(StaticJavaParser.parseExpression(replacement));
            changed = true;
        }

        if (changed) {
            ensureImport(cu, "java.lang.reflect.Method");
            ensureHelperMethod(cu);
        }
        return changed;
    }

    private static String buildReflectionCall(Expression scope, String methodName, List<Expression> arguments) {
        String parameterTypes;
        String argValues = arguments.stream().map(Expression::toString).collect(Collectors.joining(", "));

        switch (methodName) {
            case "addAppender":
            case "detachAppender":
                parameterTypes = "new Class<?>[] { ch.qos.logback.core.Appender.class }";
                break;
            case "setLevel":
                parameterTypes = "new Class<?>[] { ch.qos.logback.classic.Level.class }";
                break;
            case "setAdditive":
                parameterTypes = "new Class<?>[] { boolean.class }";
                break;
            case "detachAndStopAllAppenders":
                parameterTypes = "new Class<?>[] {}";
                break;
            default:
                parameterTypes = "new Class<?>[] {}";
        }

        String objectArray = arguments.isEmpty() ? "new Object[] {}" : "new Object[] { " + argValues + " }";
        return HELPER_METHOD + "(" + scope + ", \"" + methodName + "\", " + parameterTypes + ", " + objectArray + ")";
    }

    private static void ensureHelperMethod(CompilationUnit cu) {
        boolean present = cu.findAll(MethodDeclaration.class).stream().anyMatch(m -> m.getNameAsString().equals(HELPER_METHOD));
        if (present) {
            return;
        }

        cu.findFirst(ClassOrInterfaceDeclaration.class)
                .ifPresent(clazz -> clazz.addMember(StaticJavaParser.parseBodyDeclaration(
                        "private static void " + HELPER_METHOD + "(Object target, String methodName, Class<?>[] parameterTypes, Object[] arguments) {"
                                + "try {"
                                + "java.lang.reflect.Method method = target.getClass().getMethod(methodName, parameterTypes);"
                                + "method.setAccessible(true);"
                                + "method.invoke(target, arguments);"
                                + "} catch (ReflectiveOperationException e) {"
                                + "throw new IllegalStateException(\"Unable to invoke logger method \" + methodName, e);"
                                + "}"
                                + "}")));
    }

    private static void ensureImport(CompilationUnit cu, String importName) {
        boolean present = cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(importName));
        if (!present) {
            cu.addImport(importName);
        }
    }
}
