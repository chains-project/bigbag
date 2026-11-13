package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String ENCODER_BASE = "ch.qos.logback.core.encoder.EncoderBase";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        File inputDir = Path.of(args[0]).toFile();
        File outputDir = args.length > 1 ? Path.of(args[1]).toFile() : new File(inputDir, "spoon-out");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(8);
        addMavenSourceRoots(launcher, inputDir);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        CtModel model = launcher.getModel();
        CtTypeReference<?> encoderBaseRef = launcher.getFactory().Type().createReference(ENCODER_BASE);
        CtTypeReference<?> byteArrayRef = launcher.getFactory().Type().createReference(byte[].class);
        for (CtClass<?> type : model.getElements(new TypeFilter<>(CtClass.class))) {
            if (!isSubTypeOf(type, encoderBaseRef)) {
                continue;
            }

            applyEncoderCompatibilityFixes(type, byteArrayRef);
        }

        launcher.prettyprint();
    }

    private static void addMavenSourceRoots(Launcher launcher, File root) {
        try (Stream<java.nio.file.Path> paths = Files.walk(root.toPath())) {
            paths.filter(path -> path.endsWith("src/main/java") || path.endsWith("src/test/java"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void applyEncoderCompatibilityFixes(
            CtClass<?> type,
            CtTypeReference<?> byteArrayRef) {

        removeOverrideAnnotations(type, "init", "doEncode", "close");
        ensureHeaderAndFooterMethods(type, byteArrayRef);
        ensureEncodeAdapter(type, byteArrayRef);
    }

    private static boolean isSubTypeOf(CtClass<?> type, CtTypeReference<?> targetSuperType) {
        CtTypeReference<?> current = type.getSuperclass();
        while (current != null) {
            if (targetSuperType.getQualifiedName().equals(current.getQualifiedName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static void removeOverrideAnnotations(CtClass<?> type, String... methodNames) {
        for (String methodName : methodNames) {
            for (CtMethod<?> method : type.getMethodsByName(methodName)) {
                method.getAnnotations().removeIf(annotation ->
                        "java.lang.Override".equals(annotation.getAnnotationType().getQualifiedName()));
            }
        }
    }

    private static void ensureHeaderAndFooterMethods(CtClass<?> type, CtTypeReference<?> byteArrayRef) {
        if (type.getMethodsByName("headerBytes").isEmpty()) {
            createNullByteArrayMethod(type, "headerBytes", byteArrayRef);
        }
        if (type.getMethodsByName("footerBytes").isEmpty()) {
            createNullByteArrayMethod(type, "footerBytes", byteArrayRef);
        }
    }

    private static void createNullByteArrayMethod(
            CtClass<?> owner,
            String name,
            CtTypeReference<?> byteArrayRef) {
        owner.getFactory().createMethod(
                owner,
                EnumSet.of(ModifierKind.PUBLIC),
                byteArrayRef,
                name,
                Collections.emptyList(),
                Collections.emptySet(),
                owner.getFactory().Code().createCtBlock(
                        owner.getFactory().Code().createCodeSnippetStatement("return null")));
    }

    private static void ensureEncodeAdapter(
            CtClass<?> type,
            CtTypeReference<?> byteArrayRef) {

        if (!type.getMethodsByName("doEncode").isEmpty() && type.getMethodsByName("encode").isEmpty()) {
            CtMethod<?> source = type.getMethodsByName("doEncode").get(0);
            List<CtParameter<?>> parameters = new ArrayList<>();
            if (!source.getParameters().isEmpty()) {
                parameters.add(source.getParameters().get(0).clone());
            }
            String parameterName = source.getParameters().get(0).getSimpleName();
            var body = type.getFactory().createBlock();
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement(
                    "java.io.ByteArrayOutputStream __buffer = new java.io.ByteArrayOutputStream()"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("try {"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("  init(__buffer)"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("  doEncode(" + parameterName + ")"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("  return __buffer.toByteArray()"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("} catch (java.io.IOException e) {"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("  throw new RuntimeException(e)"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("} finally {"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("  try { close(); } catch (java.io.IOException ignored) { }"));
            body.addStatement(type.getFactory().Code().createCodeSnippetStatement("}"));

            type.getFactory().createMethod(
                    type,
                    EnumSet.of(ModifierKind.PUBLIC),
                    byteArrayRef,
                    "encode",
                    parameters,
                    Collections.<CtTypeReference<? extends Throwable>>emptySet(),
                    body);
        }
    }
}
