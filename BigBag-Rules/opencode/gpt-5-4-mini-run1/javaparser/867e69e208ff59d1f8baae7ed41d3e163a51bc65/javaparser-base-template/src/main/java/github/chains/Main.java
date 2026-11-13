package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

    static {
        TYPE_RENAMES.put(
                "org.apache.thrift.transport.TFastFramedTransport",
                "org.apache.thrift.transport.layered.TFastFramedTransport");
        TYPE_RENAMES.put(
                "org.apache.thrift.transport.TFramedTransport",
                "org.apache.thrift.transport.layered.TFramedTransport");
    }

    private static final String THRIFT_PROTOCOL_TYPE = "org.apache.thrift.protocol.TProtocol";
    private static final String THRIFT_TEXCEPTION = "org.apache.thrift.TException";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        StaticJavaParser.setConfiguration(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8));

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteJavaFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree", e);
        }
    }

    private static void rewriteJavaFile(Path javaFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = rewriteImports(compilationUnit)
                    | rewriteTypeUsages(compilationUnit)
                    | rewriteObjectCreations(compilationUnit)
                    | rewriteTTypeConstants(compilationUnit)
                    | rewriteFramedTransportConstructors(compilationUnit)
                    | rewriteConstructorReferences(compilationUnit)
                    | addProtocolSizeMethods(compilationUnit);
            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + javaFile, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String currentName = importDeclaration.getNameAsString();
            String rewritten = rewriteQualifiedName(currentName);
            if (!currentName.equals(rewritten)) {
                importDeclaration.setName(rewritten);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypeUsages(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            String currentText = type.asString();
            String rewritten = rewriteQualifiedName(currentText);
            if (!currentText.equals(rewritten)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(rewritten));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteObjectCreations(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ObjectCreationExpr expr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            String originalType = expr.getType().asString();
            String rewritten = rewriteQualifiedName(originalType);
            if (!expr.getType().asString().equals(rewritten)) {
                expr.setType(StaticJavaParser.parseClassOrInterfaceType(rewritten));
                changed = true;
            }
            if (isFramedTransport(originalType) || originalType.endsWith("FramedTransport")) {
                changed |= wrapInTryCatchIfNeeded(expr, THRIFT_TRANSPORT_EXCEPTION);
            }
        }
        return changed;
    }

    private static boolean rewriteTTypeConstants(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (FieldAccessExpr expr : compilationUnit.findAll(FieldAccessExpr.class)) {
            if ("BINARY".equals(expr.getNameAsString()) && expr.getScope().toString().endsWith("TType")) {
                expr.setName("STRING");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteConstructorReferences(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (MethodReferenceExpr expr : compilationUnit.findAll(MethodReferenceExpr.class)) {
            String scope = expr.getScope().toString();
            if (scope.endsWith("TSerializer") && expr.getIdentifier().equals("new")) {
                expr.replace(StaticJavaParser.parseExpression(
                        "() -> { try { return new org.apache.thrift.TSerializer(); } catch (Exception e) { throw new RuntimeException(e); } }"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteFramedTransportConstructors(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ConstructorDeclaration constructor : compilationUnit.findAll(ConstructorDeclaration.class)) {
            if (constructor.getParentNode().isEmpty() || !(constructor.getParentNode().get() instanceof ClassOrInterfaceDeclaration)) {
                continue;
            }
            ClassOrInterfaceDeclaration owner = (ClassOrInterfaceDeclaration) constructor.getParentNode().get();
            boolean extendsFramedTransport = owner.getExtendedTypes().stream()
                    .map(ClassOrInterfaceType::asString)
                    .anyMatch(type -> type.equals("TFramedTransport")
                            || type.equals("org.apache.thrift.transport.layered.TFramedTransport")
                            || type.endsWith(".TFramedTransport"));
            if (!extendsFramedTransport) {
                continue;
            }
            if (constructor.getThrownExceptions().stream().anyMatch(t -> t.toString().contains("TTransportException"))) {
                continue;
            }
            constructor.addThrownException(StaticJavaParser.parseClassOrInterfaceType(THRIFT_TRANSPORT_EXCEPTION));
            changed = true;
        }
        return changed;
    }

    private static boolean addProtocolSizeMethods(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ClassOrInterfaceDeclaration declaration : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (declaration.isInterface() || declaration.isAbstract()) {
                continue;
            }
            if (declaration.getExtendedTypes().stream().noneMatch(t -> {
                String text = t.asString();
                return THRIFT_PROTOCOL_TYPE.equals(text) || "TProtocol".equals(text);
            })) {
                continue;
            }
            if (declaration.getMethodsByName("getMinSerializedSize").stream().anyMatch(m -> m.getParameters().size() == 1)) {
                continue;
            }

            MethodDeclaration method = declaration.addMethod("getMinSerializedSize", com.github.javaparser.ast.Modifier.Keyword.PUBLIC);
            method.addAnnotation("Override");
            method.setType("int");
            method.addParameter("byte", "type");
            method.addThrownException(StaticJavaParser.parseClassOrInterfaceType(THRIFT_TEXCEPTION));

            BlockStmt body = StaticJavaParser.parseBlock(String.join("\n",
                    "{",
                    "  switch (type) {",
                    "    case org.apache.thrift.protocol.TType.BOOL:",
                    "    case org.apache.thrift.protocol.TType.BYTE:",
                    "      return 1;",
                    "    case org.apache.thrift.protocol.TType.I16:",
                    "      return 2;",
                    "    case org.apache.thrift.protocol.TType.I32:",
                    "      return 4;",
                    "    case org.apache.thrift.protocol.TType.I64:",
                    "    case org.apache.thrift.protocol.TType.DOUBLE:",
                    "      return 8;",
                    "    case org.apache.thrift.protocol.TType.STRING:",
                    "      return 4;",
                    "    default:",
                    "      return 0;",
                    "  }",
                    "}"));
            method.setBody(body);
            changed = true;
        }

        if (changed) {
            compilationUnit.addImport("org.apache.thrift.protocol.TType");
        }
        return changed;
    }

    private static final String THRIFT_TRANSPORT_EXCEPTION = "org.apache.thrift.transport.TTransportException";

    private static boolean isFramedTransport(String typeName) {
        return "org.apache.thrift.transport.layered.TFramedTransport".equals(typeName)
                || "TFramedTransport".equals(typeName);
    }

    private static boolean wrapInTryCatchIfNeeded(ObjectCreationExpr expr, String thrownType) {
        if (expr.findAncestor(CallableDeclaration.class)
                .map(callable -> declaresException(callable, thrownType))
                .orElse(false)) {
            return false;
        }

        return expr.findAncestor(Statement.class)
                .map(statement -> {
                    String wrapped = String.join("\n",
                            "try {",
                            "  " + statement.toString(),
                            "} catch (" + thrownType + " e) {",
                            "  throw new RuntimeException(e);",
                            "}");
                    statement.replace(StaticJavaParser.parseStatement(wrapped));
                    return true;
                })
                .orElse(false);
    }

    private static boolean declaresException(CallableDeclaration<?> callable, String thrownType) {
        String simpleName = thrownType.substring(thrownType.lastIndexOf('.') + 1);
        return callable.getThrownExceptions().stream().anyMatch(t -> {
            String text = t.toString();
            return text.equals(thrownType) || text.equals(simpleName) || text.endsWith("." + simpleName);
        });
    }

    private static String rewriteQualifiedName(String name) {
        String rewritten = name;
        for (Map.Entry<String, String> entry : TYPE_RENAMES.entrySet()) {
            String oldName = entry.getKey();
            String newName = entry.getValue();
            if (rewritten.equals(oldName) || rewritten.startsWith(oldName + ".")) {
                rewritten = newName + rewritten.substring(oldName.length());
            }
        }
        return rewritten;
    }
}
