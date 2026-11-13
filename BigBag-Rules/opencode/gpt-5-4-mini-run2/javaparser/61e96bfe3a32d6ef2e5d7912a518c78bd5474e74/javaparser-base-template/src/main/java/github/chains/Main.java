package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String ENCODER_BASE = "ch.qos.logback.core.encoder.EncoderBase";
    private static final String ENCODER = "ch.qos.logback.core.encoder.Encoder";
    private static final String OUTPUT_STREAM = "java.io.OutputStream";
    private static final String BYTE_ARRAY_OUTPUT_STREAM = "java.io.ByteArrayOutputStream";

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Main::isJavaFile).forEach(path -> rewrite(parser, path));
        }
    }

    private static boolean isJavaFile(Path path) {
        String value = path.toString();
        return value.endsWith(".java") && !value.contains("/target/") && !value.contains("\\target\\");
    }

    private static void rewrite(JavaParser parser, Path path) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(path);
            if (result.getResult().isEmpty()) {
                return;
            }

            CompilationUnit cu = result.getResult().get();
            boolean changed = false;

            for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (isTargetEncoder(type)) {
                    changed |= transformEncoder(type);
                }
            }

            if (changed) {
                cu.addImport(BYTE_ARRAY_OUTPUT_STREAM);
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // Best effort across the entire source tree.
        }
    }

    private static boolean isTargetEncoder(ClassOrInterfaceDeclaration type) {
        return type.getExtendedTypes().stream().anyMatch(t -> matchesType(t.asString(), ENCODER_BASE))
            || type.getImplementedTypes().stream().anyMatch(t -> matchesType(t.asString(), ENCODER))
            || !type.getMethodsByName("doEncode").isEmpty();
    }

    private static boolean matchesType(String actual, String expected) {
        return actual.equals(expected) || actual.endsWith('.' + simpleName(expected));
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx < 0 ? fqcn : fqcn.substring(idx + 1);
    }

    private static boolean transformEncoder(ClassOrInterfaceDeclaration type) {
        boolean changed = false;
        Optional<String> streamField = findOutputStreamField(type);
        String fieldName = streamField.orElse("buffer");

        if (streamField.isPresent()) {
            changed |= upgradeStreamField(type, fieldName);
        }

        for (MethodDeclaration method : type.getMethods()) {
            switch (method.getNameAsString()) {
                case "init":
                    changed |= transformInit(method, fieldName);
                    break;
                case "doEncode":
                    changed |= transformDoEncode(method, fieldName);
                    break;
                case "close":
                    changed |= method.getAnnotations().removeIf(a -> a.getNameAsString().equals("Override"));
                    break;
                default:
                    break;
            }
        }

        changed |= ensureLifecycleMethod(type, "headerBytes");
        changed |= ensureLifecycleMethod(type, "footerBytes");
        return changed;
    }

    private static Optional<String> findOutputStreamField(ClassOrInterfaceDeclaration type) {
        for (FieldDeclaration field : type.getFields()) {
            if (field.getVariables().size() != 1) {
                continue;
            }
            if (matchesType(field.getElementType().asString(), OUTPUT_STREAM)) {
                return Optional.of(field.getVariable(0).getNameAsString());
            }
        }
        return Optional.empty();
    }

    private static boolean upgradeStreamField(ClassOrInterfaceDeclaration type, String fieldName) {
        boolean changed = false;
        for (FieldDeclaration field : type.getFields()) {
            if (field.getVariables().size() != 1) {
                continue;
            }
            if (!field.getVariable(0).getNameAsString().equals(fieldName)) {
                continue;
            }
            if (!matchesType(field.getElementType().asString(), BYTE_ARRAY_OUTPUT_STREAM)) {
                field.getVariable(0).setType(StaticJavaParser.parseType(BYTE_ARRAY_OUTPUT_STREAM));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean transformInit(MethodDeclaration method, String fieldName) {
        boolean changed = method.getAnnotations().removeIf(a -> a.getNameAsString().equals("Override"));
        if (!method.getParameters().isEmpty()) {
            Parameter parameter = method.getParameter(0);
            if (matchesType(parameter.getType().asString(), OUTPUT_STREAM)) {
                parameter.setType(StaticJavaParser.parseType(BYTE_ARRAY_OUTPUT_STREAM));
                changed = true;
            }
        }

        method.getBody().ifPresent(body -> {
            body.findAll(ObjectCreationExpr.class).forEach(expr -> {
                if (expr.getType().asString().endsWith("TIOStreamTransport") && !expr.getArguments().isEmpty()) {
                    expr.getArguments().set(0, StaticJavaParser.parseExpression("this." + fieldName));
                }
                if (expr.getType().asString().endsWith("ByteArrayOutputStream")) {
                    expr.getArguments().clear();
                }
            });
            if (body.findAll(SimpleName.class).stream().noneMatch(n -> n.asString().equals(fieldName))) {
                body.addStatement(0, StaticJavaParser.parseStatement("this." + fieldName + " = new " + BYTE_ARRAY_OUTPUT_STREAM + "();"));
            }
        });

        return changed;
    }

    private static boolean transformDoEncode(MethodDeclaration method, String fieldName) {
        boolean changed = method.getAnnotations().removeIf(a -> a.getNameAsString().equals("Override"));
        if (!method.getType().asString().equals("byte[]")) {
            method.setType(StaticJavaParser.parseType("byte[]"));
            changed = true;
        }
        if (!method.getNameAsString().equals("encode")) {
            method.setName("encode");
            changed = true;
        }

        method.getBody().ifPresent(body -> {
            if (body.getStatements().isEmpty() || !body.getStatement(body.getStatements().size() - 1).isReturnStmt()) {
                body.addStatement(new ReturnStmt(StaticJavaParser.parseExpression("this." + fieldName + ".toByteArray()")));
            }
            body.addStatement(StaticJavaParser.parseStatement("this." + fieldName + ".reset();"));
        });

        return changed;
    }

    private static boolean ensureLifecycleMethod(ClassOrInterfaceDeclaration type, String methodName) {
        if (!type.getMethodsByName(methodName).isEmpty()) {
            return false;
        }

        MethodDeclaration method = type.addMethod(methodName);
        method.addAnnotation(new MarkerAnnotationExpr("Override"));
        method.setType(StaticJavaParser.parseType("byte[]"));
        method.setBody(new BlockStmt().addStatement(new ReturnStmt(StaticJavaParser.parseExpression("new byte[0]"))));
        return true;
    }
}
