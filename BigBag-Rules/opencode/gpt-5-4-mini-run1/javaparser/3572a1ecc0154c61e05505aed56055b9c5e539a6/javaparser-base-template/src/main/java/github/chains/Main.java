package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
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
import java.util.HashSet;

public class Main {
    private static final String OLD_PACKAGE = "org.apache.thrift.transport.";
    private static final String NEW_PACKAGE = "org.apache.thrift.transport.layered.";
    private static final List<String> TARGET_TYPES = List.of("TFastFramedTransport", "TFramedTransport");
    private static final Set<String> THROWING_CONSTRUCTORS = Set.of(
            "TIOStreamTransport",
            "TMemoryInputTransport",
            "TMemoryBuffer",
            "TFastFramedTransport",
            "TFramedTransport",
            "TSerializer",
            "TDeserializer");

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory path");
        }

        Path sourceRoot = Paths.get(args[0]);
        Path outputRoot = args.length > 1 ? Paths.get(args[1]) : sourceRoot;

        StaticJavaParser.setConfiguration(new ParserConfiguration());

        Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> transformFile(sourceRoot, outputRoot, path));
    }

    private static void transformFile(Path sourceRoot, Path outputRoot, Path inputFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(inputFile, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteQualifiedTypeUsages(cu);
            changed |= wrapThrowingConstructorCalls(cu);
            changed |= rewriteThrowingMethodReferences(cu);
            changed |= addMissingTProtocolMethod(cu);
            changed |= addTransportExceptionToFramedConstructors(cu);

            if (changed) {
                Path relative = sourceRoot.relativize(inputFile);
                Path outputFile = outputRoot.resolve(relative);
                Files.createDirectories(outputFile.getParent());
                Files.writeString(outputFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            } else if (!sourceRoot.equals(outputRoot)) {
                Path relative = sourceRoot.relativize(inputFile);
                Path outputFile = outputRoot.resolve(relative);
                Files.createDirectories(outputFile.getParent());
                Files.copy(inputFile, outputFile);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + inputFile, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.startsWith(OLD_PACKAGE) && TARGET_TYPES.stream().anyMatch(name::endsWith)) {
                importDeclaration.setName(name.replace(OLD_PACKAGE, NEW_PACKAGE));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedTypeUsages(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String text = type.toString();
            if (text.startsWith(OLD_PACKAGE) && TARGET_TYPES.stream().anyMatch(text::endsWith)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(text.replace(OLD_PACKAGE, NEW_PACKAGE)));
                changed = true;
            }
        }

        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            String text = creationExpr.getType().toString();
            if (text.startsWith(OLD_PACKAGE) && TARGET_TYPES.stream().anyMatch(text::endsWith)) {
                creationExpr.setType(StaticJavaParser.parseClassOrInterfaceType(text.replace(OLD_PACKAGE, NEW_PACKAGE)));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean wrapThrowingConstructorCalls(CompilationUnit cu) {
        List<ObjectCreationExpr> creations = new ArrayList<>(cu.findAll(ObjectCreationExpr.class));
        boolean changed = false;

        if (creations.stream().anyMatch(Main::isThrowingConstructorCall)) {
            ensureConstructHelper(cu);
        }

        for (ObjectCreationExpr creationExpr : creations) {
            if (!isThrowingConstructorCall(creationExpr)) {
                continue;
            }

            String type = resolveTypeName(creationExpr.getType().toString());
            String args = creationExpr.getArguments().isEmpty()
                    ? ""
                    : creationExpr.getArguments().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("");
            String replacement = args.isEmpty()
                    ? "construct(\"" + type + "\")"
                    : "construct(\"" + type + "\", " + args + ")";
            creationExpr.replace(StaticJavaParser.parseExpression(replacement));
            changed = true;
        }

        return changed;
    }

    private static boolean isThrowingConstructorCall(ObjectCreationExpr creationExpr) {
        String typeName = creationExpr.getType().getNameAsString();
        return THROWING_CONSTRUCTORS.contains(typeName)
                || typeName.endsWith("TFramedTransport")
                || typeName.endsWith("TFastFramedTransport");
    }

    private static void ensureConstructHelper(CompilationUnit cu) {
        ClassOrInterfaceDeclaration owner = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner == null) {
            return;
        }

        boolean hasHelperMethod = owner.getMembers().stream().anyMatch(member ->
                member instanceof MethodDeclaration && ((MethodDeclaration) member).getNameAsString().equals("construct"));
        if (hasHelperMethod) {
            return;
        }

        owner.addMember(StaticJavaParser.parseBodyDeclaration(
                "private static <T> T construct(String className, Object... args) {\n" +
                "  try {\n" +
                "    Class<?> type = Class.forName(className);\n" +
                "    for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {\n" +
                "      Class<?>[] parameterTypes = constructor.getParameterTypes();\n" +
                "      if (parameterTypes.length != args.length) continue;\n" +
                "      boolean matches = true;\n" +
                "      for (int i = 0; i < parameterTypes.length; i++) {\n" +
                "        if (!matchesParameter(parameterTypes[i], args[i])) { matches = false; break; }\n" +
                "      }\n" +
                "      if (matches) {\n" +
                "        constructor.setAccessible(true);\n" +
                "        return (T) constructor.newInstance(args);\n" +
                "      }\n" +
                "    }\n" +
                "    throw new IllegalStateException(\"No matching constructor: \" + className);\n" +
                "  } catch (ReflectiveOperationException e) {\n" +
                "    throw new IllegalStateException(e);\n" +
                "  }\n" +
                "}"));
        owner.addMember(StaticJavaParser.parseBodyDeclaration(
                "private static boolean matchesParameter(Class<?> parameterType, Object arg) {\n" +
                "  if (arg == null) { return !parameterType.isPrimitive(); }\n" +
                "  Class<?> valueType = arg.getClass();\n" +
                "  if (parameterType.isPrimitive()) {\n" +
                "    return (parameterType == boolean.class && valueType == Boolean.class)\n" +
                "        || (parameterType == byte.class && valueType == Byte.class)\n" +
                "        || (parameterType == short.class && valueType == Short.class)\n" +
                "        || (parameterType == int.class && valueType == Integer.class)\n" +
                "        || (parameterType == long.class && valueType == Long.class)\n" +
                "        || (parameterType == float.class && valueType == Float.class)\n" +
                "        || (parameterType == double.class && valueType == Double.class)\n" +
                "        || (parameterType == char.class && valueType == Character.class);\n" +
                "  }\n" +
                "  return parameterType.isAssignableFrom(valueType);\n" +
                "}"));
    }

    private static boolean rewriteThrowingMethodReferences(CompilationUnit cu) {
        boolean changed = false;
        for (MethodReferenceExpr ref : cu.findAll(MethodReferenceExpr.class)) {
            String scope = ref.getScope().toString();
            String identifier = ref.getIdentifier();
            if ("new".equals(identifier) && ("TSerializer".equals(scope) || "TDeserializer".equals(scope))) {
                ensureConstructHelper(cu);
                ref.replace(StaticJavaParser.parseExpression("() -> construct(\"org.apache.thrift." + scope + "\")"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean addMissingTProtocolMethod(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean extendsTProtocol = type.getExtendedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .anyMatch("TProtocol"::equals);
            if (!extendsTProtocol) {
                continue;
            }
            boolean hasMethod = type.getMethodsByName("getMinSerializedSize").stream()
                    .anyMatch(method -> method.getParameters().size() == 1);
            if (hasMethod) {
                continue;
            }
            type.addMember(StaticJavaParser.parseBodyDeclaration(
                    "public int getMinSerializedSize(byte b) throws org.apache.thrift.TException { return 0; }"));
            changed = true;
        }
        return changed;
    }

    private static String resolveTypeName(String type) {
        if (type.startsWith(OLD_PACKAGE)) {
            return type.replace(OLD_PACKAGE, NEW_PACKAGE);
        }
        return type;
    }

    private static boolean addTransportExceptionToFramedConstructors(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean extendsFramedTransport = type.getExtendedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .anyMatch(name -> "TFramedTransport".equals(name) || "TFastFramedTransport".equals(name));
            if (!extendsFramedTransport) {
                continue;
            }
            for (ConstructorDeclaration ctor : type.getConstructors()) {
                boolean declaresTransportException = ctor.getThrownExceptions().stream()
                        .anyMatch(t -> t.toString().endsWith("TTransportException"));
                if (!declaresTransportException) {
                    ctor.addThrownException(StaticJavaParser.parseClassOrInterfaceType("org.apache.thrift.transport.TTransportException"));
                    changed = true;
                }
            }
        }
        return changed;
    }
}
