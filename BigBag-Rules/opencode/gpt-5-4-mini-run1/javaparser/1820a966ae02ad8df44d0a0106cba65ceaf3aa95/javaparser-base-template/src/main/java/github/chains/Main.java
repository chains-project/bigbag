package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_LOGGER = "ch.qos.logback.classic.Logger";
    private static final String NEW_LOGGER = "org.slf4j.Logger";
    private static final String NEW_SLF4J_VERSION = "2.0.4";
    private static final Set<String> LOGBACK_ONLY_METHODS = Set.of(
            "setLevel",
            "addAppender",
            "detachAppender",
            "detachAndStopAllAppenders",
            "getAppender",
            "isAttached"
    );

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java") || p.getFileName().toString().equals("pom.xml"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList())) {
                if (path.getFileName().toString().equals("pom.xml")) {
                    transformPom(path);
                } else {
                    transform(path);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk source tree", e);
        }
    }

    private static void transformPom(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (!content.contains("<artifactId>logback-classic</artifactId>") || !content.contains("<version>1.4.4</version>")) {
            return;
        }
        String updated = Pattern.compile("(<artifactId>slf4j-api</artifactId>\\s*<version>)([^<]+)(</version>)", Pattern.DOTALL)
                .matcher(content)
                .replaceAll("$1" + NEW_SLF4J_VERSION + "$3");
        if (!updated.contains("<artifactId>logback-core</artifactId>")) {
            updated = updated.replace("<dependency>\n            <groupId>ch.qos.logback</groupId>\n            <artifactId>logback-classic</artifactId>\n            <version>1.4.4</version>\n        </dependency>",
                    "<dependency>\n            <groupId>ch.qos.logback</groupId>\n            <artifactId>logback-classic</artifactId>\n            <version>1.4.4</version>\n        </dependency>\n        <dependency>\n            <groupId>ch.qos.logback</groupId>\n            <artifactId>logback-core</artifactId>\n            <version>1.4.4</version>\n        </dependency>");
        }
        if (!updated.equals(content)) {
            Files.writeString(path, updated, StandardCharsets.UTF_8);
        }
    }

    private static void transform(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            boolean touched = false;

            if (cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(OLD_LOGGER))) {
                if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_LOGGER))) {
                    cu.addImport(NEW_LOGGER);
                }
                touched = true;
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if (type.toString().equals(OLD_LOGGER) || type.getNameAsString().equals("Logger")) {
                    if (type.toString().equals(OLD_LOGGER)) {
                        type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_LOGGER));
                        touched = true;
                    }
                }
            }

            for (CastExpr cast : cu.findAll(CastExpr.class)) {
                if (cast.getType().isClassOrInterfaceType()) {
                    String typeName = cast.getType().asClassOrInterfaceType().toString();
                    if (typeName.equals(OLD_LOGGER)) {
                        cast.replace(cast.getExpression());
                        touched = true;
                    }
                }
            }

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (!LOGBACK_ONLY_METHODS.contains(call.getNameAsString())) {
                    continue;
                }
                if (call.getScope().isEmpty()) {
                    continue;
                }
                Expression scope = call.getScope().get();
                String replacement = buildCompatCall(scope, call);
                call.replace(StaticJavaParser.parseExpression(replacement));
                touched = true;
            }

            if (touched) {
                addHelperMethod(cu);
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to transform " + path, e);
        }
    }

    private static String buildCompatCall(Expression scope, MethodCallExpr call) {
        String args = call.getArguments().stream().map(Expression::toString).collect(Collectors.joining(", "));
        switch (call.getNameAsString()) {
            case "setLevel":
                return String.format("__logbackCompatInvoke(%s, \"setLevel\", new Class<?>[]{ch.qos.logback.classic.Level.class}, new Object[]{%s})",
                        scope, args);
            case "addAppender":
            case "detachAppender":
            case "isAttached":
                return String.format("__logbackCompatInvoke(%s, \"%s\", new Class<?>[]{ch.qos.logback.core.Appender.class}, new Object[]{%s})",
                        scope, call.getNameAsString(), args);
            case "getAppender":
                return String.format("__logbackCompatInvoke(%s, \"getAppender\", new Class<?>[]{java.lang.String.class}, new Object[]{%s})",
                        scope, args);
            case "detachAndStopAllAppenders":
                return String.format("__logbackCompatInvoke(%s, \"detachAndStopAllAppenders\", new Class<?>[]{}, new Object[]{})",
                        scope);
            default:
                throw new IllegalArgumentException("Unsupported method: " + call.getNameAsString());
        }
    }

    private static void addHelperMethod(CompilationUnit cu) {
        String helper = "private static Object __logbackCompatInvoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] arguments) {"
                + " try {"
                + " java.lang.reflect.Method method = target.getClass().getMethod(methodName, parameterTypes);"
                + " method.setAccessible(true);"
                + " return method.invoke(target, arguments);"
                + " } catch (ReflectiveOperationException e) {"
                + " throw new IllegalStateException(\"Unable to invoke logback method \" + methodName, e);"
                + " }"
                + " }";

        for (ClassOrInterfaceDeclaration declaration : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean hasHelper = declaration.getMethodsByName("__logbackCompatInvoke").size() > 0;
            if (!hasHelper) {
                BodyDeclaration<?> body = StaticJavaParser.parseBodyDeclaration(helper);
                declaration.addMember(body);
                return;
            }
        }
    }
}
