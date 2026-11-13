package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Main {

    private static final String OLD_LOGGER_FQN = "ch.qos.logback.classic.Logger";
    private static final String NEW_LOGGER_FQN = "org.slf4j.Logger";
    private static final Set<String> SLF4J_METHODS = Set.of(
            "trace", "debug", "info", "warn", "error",
            "isTraceEnabled", "isDebugEnabled", "isInfoEnabled", "isWarnEnabled", "isErrorEnabled",
            "getName", "atTrace", "atDebug", "atInfo", "atWarn", "atError"
    );

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root argument");
        }
        Path sourceRoot = Paths.get(args[0]);
        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path javaFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            Set<String> affectedVariables = new HashSet<>();

            if (cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(OLD_LOGGER_FQN))) {
                cu.getImports().removeIf(i -> i.getNameAsString().equals(OLD_LOGGER_FQN));
                if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_LOGGER_FQN))) {
                    cu.addImport(NEW_LOGGER_FQN);
                }
                changed = true;
            }

            for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
                if (isOldLoggerType(variable.getType().asString())) {
                    variable.setType(new ClassOrInterfaceType(null, "Logger"));
                    affectedVariables.add(variable.getNameAsString());
                    changed = true;
                }
            }

            if (!affectedVariables.isEmpty()) {
                new VoidVisitorAdapter<Set<String>>() {
                    @Override
                    public void visit(MethodCallExpr n, Set<String> vars) {
                        super.visit(n, vars);
                        if (!n.getScope().isPresent() || !n.getScope().get().isNameExpr()) {
                            return;
                        }
                        NameExpr scope = n.getScope().get().asNameExpr();
                        if (!vars.contains(scope.getNameAsString())) {
                            return;
                        }
                        if (SLF4J_METHODS.contains(n.getNameAsString())) {
                            return;
                        }

                        MethodCallExpr replacement = new MethodCallExpr(null, "invokeLogbackMethod");
                        replacement.addArgument(scope.clone());
                        replacement.addArgument(new StringLiteralExpr(n.getNameAsString()));
                        for (Expression argument : n.getArguments()) {
                            replacement.addArgument(argument.clone());
                        }
                        n.replace(replacement);
                    }
                }.visit(cu, affectedVariables);
                changed = true;
                addHelperMethod(cu);
            }

            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to transform " + javaFile, e);
        }
    }

    private static boolean isOldLoggerType(String typeName) {
        return OLD_LOGGER_FQN.equals(typeName) || "Logger".equals(typeName);
    }

    private static void addHelperMethod(CompilationUnit cu) {
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!type.getMethodsByName("invokeLogbackMethod").isEmpty()) {
                continue;
            }

            MethodDeclaration method = type.addMethod("invokeLogbackMethod");
            method.setPrivate(true);
            method.setStatic(true);
            method.setType("void");
            method.addParameter(Object.class, "target");
            method.addParameter(String.class, "methodName");
            method.addParameter(Object[].class, "args");
            method.getParameter(method.getParameters().size() - 1).setVarArgs(true);
            method.setBody(StaticJavaParser.parseBlock(String.join("\n",
                    "{",
                    "    try {",
                    "        java.lang.reflect.Method method = java.util.Arrays.stream(target.getClass().getMethods())",
                    "                .filter(candidate -> candidate.getName().equals(methodName))",
                    "                .filter(candidate -> candidate.getParameterCount() == args.length)",
                    "                .filter(candidate -> isCompatible(candidate.getParameterTypes(), args))",
                    "                .findFirst()",
                    "                .orElseThrow(() -> new IllegalStateException(\"No compatible method found: \" + methodName));",
                    "        method.setAccessible(true);",
                    "        method.invoke(target, args);",
                    "    } catch (ReflectiveOperationException e) {",
                    "        throw new IllegalStateException(\"Unable to invoke method: \" + methodName, e);",
                    "    }",
                    "}")));

            MethodDeclaration compatibility = type.addMethod("isCompatible");
            compatibility.setPrivate(true);
            compatibility.setStatic(true);
            compatibility.setType(boolean.class);
            compatibility.addParameter(Class[].class, "parameterTypes");
            compatibility.addParameter(Object[].class, "args");
            compatibility.setBody(StaticJavaParser.parseBlock(String.join("\n",
                    "{",
                    "    if (parameterTypes.length != args.length) {",
                    "        return false;",
                    "    }",
                    "    for (int i = 0; i < parameterTypes.length; i++) {",
                    "        Object arg = args[i];",
                    "        if (arg == null) {",
                    "            continue;",
                    "        }",
                    "        Class<?> parameterType = parameterTypes[i];",
                    "        if (parameterType.isPrimitive()) {",
                    "            if (!primitiveWrapper(parameterType).isInstance(arg)) {",
                    "                return false;",
                    "            }",
                    "        } else if (!parameterType.isAssignableFrom(arg.getClass())) {",
                    "            return false;",
                    "        }",
                    "    }",
                    "    return true;",
                    "}")));

            MethodDeclaration primitiveWrapper = type.addMethod("primitiveWrapper");
            primitiveWrapper.setPrivate(true);
            primitiveWrapper.setStatic(true);
            primitiveWrapper.setType(Class.class);
            primitiveWrapper.addParameter(Class.class, "primitiveType");
            primitiveWrapper.setBody(StaticJavaParser.parseBlock(String.join("\n",
                    "{",
                    "    if (primitiveType == boolean.class) return Boolean.class;",
                    "    if (primitiveType == byte.class) return Byte.class;",
                    "    if (primitiveType == short.class) return Short.class;",
                    "    if (primitiveType == int.class) return Integer.class;",
                    "    if (primitiveType == long.class) return Long.class;",
                    "    if (primitiveType == float.class) return Float.class;",
                    "    if (primitiveType == double.class) return Double.class;",
                    "    if (primitiveType == char.class) return Character.class;",
                    "    return primitiveType;",
                    "}")));

            return;
        }
    }
}
