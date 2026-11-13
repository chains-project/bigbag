package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    private static final String ENCODER_BASE_FQN = "ch.qos.logback.core.encoder.EncoderBase";
    private static final String OUTPUT_STREAM_FQN = "java.io.OutputStream";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: source root directory");
        }

        Path sourceRoot = Paths.get(args[0]);
        try {
            Files.walk(sourceRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root " + sourceRoot, e);
        }
    }

    private static void transformFile(Path javaFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            for (ClassOrInterfaceDeclaration type : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                changed |= transformEncoderSubclass(type);
            }

            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static boolean transformEncoderSubclass(ClassOrInterfaceDeclaration type) {
        if (!isEncoderBaseSubclass(type)) {
            return false;
        }

        boolean changed = false;

        MethodDeclaration initMethod = findMethod(type, "init", 1).orElse(null);
        MethodDeclaration doEncodeMethod = findMethod(type, "doEncode", 1).orElse(null);
        MethodDeclaration closeMethod = findMethod(type, "close", 0).orElse(null);

        changed |= removeOverrideAnnotation(initMethod);
        changed |= removeOverrideAnnotation(doEncodeMethod);
        changed |= removeOverrideAnnotation(closeMethod);

        if (findMethod(type, "headerBytes", 0).isEmpty()) {
            type.addMethod("headerBytes")
                    .setType("byte[]")
                    .setPublic(true)
                    .addAnnotation("Override")
                    .setBody(StaticJavaParser.parseBlock("{ return new byte[0]; }"));
            changed = true;
        }

        if (findMethod(type, "footerBytes", 0).isEmpty()) {
            type.addMethod("footerBytes")
                    .setType("byte[]")
                    .setPublic(true)
                    .addAnnotation("Override")
                    .setBody(StaticJavaParser.parseBlock("{ return new byte[0]; }"));
            changed = true;
        }

        if (doEncodeMethod != null && findMethod(type, "encode", 1).isEmpty()) {
            Parameter input = doEncodeMethod.getParameter(0).clone();
            String body = String.join("\n",
                    "{",
                    "    java.io.ByteArrayOutputStream __buffer = new java.io.ByteArrayOutputStream();",
                    "    try {",
                    "        init(__buffer);",
                    "        doEncode(" + input.getNameAsString() + ");",
                    "        return __buffer.toByteArray();",
                    "    } catch (java.io.IOException e) {",
                    "        throw new java.io.UncheckedIOException(e);",
                    "    } finally {",
                    "        try {",
                    "            close();",
                    "        } catch (java.io.IOException e) {",
                    "            throw new java.io.UncheckedIOException(e);",
                    "        }",
                    "    }",
                    "}");

            MethodDeclaration encodeMethod = type.addMethod("encode");
            encodeMethod.setPublic(true);
            encodeMethod.setType("byte[]");
            encodeMethod.addAnnotation("Override");
            encodeMethod.addParameter(input.getType(), input.getNameAsString());
            encodeMethod.setBody(StaticJavaParser.parseBlock(body));
            changed = true;
        }

        return changed;
    }

    private static boolean isEncoderBaseSubclass(ClassOrInterfaceDeclaration type) {
        for (ClassOrInterfaceType extendedType : type.getExtendedTypes()) {
            String text = extendedType.getNameAsString();
            if (ENCODER_BASE_FQN.equals(extendedType.asString()) || "EncoderBase".equals(text)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<MethodDeclaration> findMethod(ClassOrInterfaceDeclaration type, String name, int parameterCount) {
        return type.getMethods().stream()
                .filter(method -> method.getNameAsString().equals(name))
                .filter(method -> method.getParameters().size() == parameterCount)
                .findFirst();
    }

    private static boolean removeOverrideAnnotation(MethodDeclaration method) {
        if (method == null) {
            return false;
        }

        boolean removed = false;
        for (AnnotationExpr annotation : java.util.List.copyOf(method.getAnnotations())) {
            if (annotation.getNameAsString().equals("Override")) {
                annotation.remove();
                removed = true;
            }
        }
        return removed;
    }
}
