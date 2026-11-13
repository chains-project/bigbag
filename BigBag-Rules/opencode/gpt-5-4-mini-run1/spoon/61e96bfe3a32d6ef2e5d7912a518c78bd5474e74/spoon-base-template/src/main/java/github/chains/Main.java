package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

import java.util.Set;

public class Main {
    private static final String ENCODER_BASE = "ch.qos.logback.core.encoder.EncoderBase";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new EncoderBaseCompatibilityProcessor());
        launcher.run();
    }

    private static final class EncoderBaseCompatibilityProcessor extends AbstractProcessor<CtClass<?>> {

        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            return candidate.isSubtypeOf(candidate.getFactory().Type().createReference(ENCODER_BASE));
        }

        @Override
        public void process(CtClass<?> type) {
            rewriteLegacyDoEncode(type);
            stripInvalidOverride(type, "init");
            stripInvalidOverride(type, "close");
            ensureMethod(type, "headerBytes");
            ensureMethod(type, "footerBytes");
        }

        private void rewriteLegacyDoEncode(CtClass<?> type) {
            CtMethod<?> legacy = type.getMethodsByName("doEncode").stream().findFirst().orElse(null);
            if (legacy == null || type.getMethodsByName("encode").stream().findFirst().isPresent()) {
                return;
            }

            legacy.setSimpleName("encode");
            legacy.setType(getFactory().Type().createArrayReference(getFactory().Type().bytePrimitiveType()));
            legacy.setThrownTypes(Set.of());

            CtBlock<?> oldBody = legacy.getBody();
            if (oldBody == null) {
                return;
            }

            CtBlock<Object> newBody = getFactory().createBlock();
            newBody.addStatement(getFactory().createCodeSnippetStatement("java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();"));
            CtMethod<?> init = type.getMethodsByName("init").stream().findFirst().orElse(null);
            if (init != null && init.getParameters().size() == 1) {
                newBody.addStatement(getFactory().createCodeSnippetStatement("init(output);"));
            }
            for (CtElement statement : oldBody.getStatements()) {
                newBody.addStatement(((spoon.reflect.code.CtStatement) statement).clone());
            }
            newBody.addStatement(getFactory().createCodeSnippetStatement("return output.toByteArray();"));
            legacy.setBody(newBody);
        }

        private void stripInvalidOverride(CtClass<?> type, String methodName) {
            for (CtMethod<?> method : type.getMethodsByName(methodName)) {
                CtAnnotation<?> overrideAnnotation = method.getAnnotations().stream()
                        .filter(annotation -> "java.lang.Override".equals(annotation.getAnnotationType().getQualifiedName()))
                        .findFirst()
                        .orElse(null);
                if (overrideAnnotation != null) {
                    method.removeAnnotation(overrideAnnotation);
                }
            }
        }

        private void ensureMethod(CtClass<?> type, String name) {
            if (!type.getMethodsByName(name).isEmpty()) {
                return;
            }

            CtMethod<byte[]> method = createNullReturningMethod(name);
            type.addMethod(method);
        }

        private CtMethod<byte[]> createNullReturningMethod(String name) {
            CtMethod<byte[]> method = getFactory().createMethod();
            method.setSimpleName(name);
            method.setType(getFactory().Type().createArrayReference(getFactory().Type().bytePrimitiveType()));
            method.setModifiers(Set.of(ModifierKind.PUBLIC));
            CtBlock<byte[]> body = getFactory().createBlock();
            body.addStatement(getFactory().createCodeSnippetStatement("return null;"));
            method.setBody(body);
            return method;
        }
    }
}
