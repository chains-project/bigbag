package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {
    private static final String SERIALIZER = "org.apache.thrift.TSerializer";
    private static final String DESERIALIZER = "org.apache.thrift.TDeserializer";
    private static final String FACTORY = "org.apache.thrift.protocol.TProtocolFactory";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> rewriteFile(parser, path));
        }
    }

    private static void rewriteFile(JavaParser parser, Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            CompilationUnit cu = parser.parse(source).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            LexicalPreservingPrinter.setup(cu);
            boolean changed = rewriteCompilationUnit(cu);
            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean rewriteCompilationUnit(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            Map<String, MethodDeclaration> helpers = new HashMap<>();
            dedupeAndRepairGeneratedHelpers(clazz);
            List<ObjectCreationExpr> creations = clazz.findAll(ObjectCreationExpr.class);
            for (ObjectCreationExpr creation : creations) {
                if (isInsideGeneratedHelper(creation)) {
                    continue;
                }
                if (!isTargetThriftConstructor(creation)) {
                    continue;
                }

                String helperName = helperName(creation);
                if (!hasHelper(clazz, helperName) && !helpers.containsKey(helperName)) {
                    helpers.put(helperName, createHelper(creation, helperName));
                }

                creation.replace(new MethodCallExpr(helperName));
                changed = true;
            }

            if (!helpers.isEmpty()) {
                helpers.values().forEach(clazz::addMember);
            }
        }
        return changed;
    }

    private static void dedupeAndRepairGeneratedHelpers(ClassOrInterfaceDeclaration clazz) {
        Map<String, MethodDeclaration> seen = new HashMap<>();
        for (MethodDeclaration method : clazz.getMethods()) {
            if (!isGeneratedHelper(method)) {
                continue;
            }

            String name = method.getNameAsString();
            if (!seen.containsKey(name)) {
                seen.put(name, method);
                repairGeneratedHelper(method);
            } else {
                method.remove();
            }
        }
    }

    private static boolean hasHelper(ClassOrInterfaceDeclaration clazz, String helperName) {
        return clazz.getMethodsByName(helperName).stream().anyMatch(Main::isGeneratedHelper);
    }

    private static boolean isInsideGeneratedHelper(ObjectCreationExpr creation) {
        return creation.findAncestor(MethodDeclaration.class)
            .filter(Main::isGeneratedHelper)
            .isPresent();
    }

    private static boolean isGeneratedHelper(MethodDeclaration method) {
        return method.getNameAsString().startsWith("__thriftCreate")
            && (Objects.equals(method.getType().asString(), SERIALIZER)
            || Objects.equals(method.getType().asString(), DESERIALIZER)
            || method.getType().asString().endsWith("TSerializer")
            || method.getType().asString().endsWith("TDeserializer"));
    }

    private static void repairGeneratedHelper(MethodDeclaration method) {
        String returnType = method.getType().asString();
        boolean serializer = returnType.endsWith("TSerializer");
        boolean deserializer = returnType.endsWith("TDeserializer");
        if (!serializer && !deserializer) {
            return;
        }

        boolean factoryBased = method.getParameters().size() == 1;
        String body = factoryBased
            ? "{ return new " + returnType + "(protocolFactory); }"
            : "{ try { return new " + returnType + "(); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); } }";
        method.setBody(StaticJavaParser.parseBlock(body));
    }

    private static boolean isTargetThriftConstructor(ObjectCreationExpr creation) {
        String type = creation.getType().toString();
        int arity = creation.getArguments().size();
        return (arity == 0 || arity == 1)
            && (SERIALIZER.equals(type) || DESERIALIZER.equals(type)
            || "TSerializer".equals(creation.getType().getNameAsString())
            || "TDeserializer".equals(creation.getType().getNameAsString()));
    }

    private static String helperName(ObjectCreationExpr creation) {
        String simple = creation.getType().getNameAsString();
        return "__thriftCreate" + simple + "_" + creation.getArguments().size();
    }

    private static MethodDeclaration createHelper(ObjectCreationExpr creation, String helperName) {
        String targetType = creation.getType().toString();
        String params = creation.getArguments().isEmpty() ? "" : FACTORY + " protocolFactory";
        String body = creation.getArguments().isEmpty()
            ? "{ try { return new " + targetType + "(); } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); } }"
            : "{ return new " + targetType + "(protocolFactory); }";
        return StaticJavaParser.parseBodyDeclaration(
            "private static " + targetType + " " + helperName + "(" + params + ") " + body)
            .asMethodDeclaration();
    }
}
