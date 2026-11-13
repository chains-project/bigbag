package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String ENCODER_BASE_FQN = "ch.qos.logback.core.encoder.EncoderBase";
    private static final String OVERRIDE_FQN = "java.lang.Override";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());

        launcher.buildModel();

        for (CtType<?> type : new ArrayList<>(launcher.getModel().getAllTypes())) {
            if (type instanceof CtClass && extendsType((CtClass<?>) type, ENCODER_BASE_FQN)) {
                transformEncoderClass((CtClass<?>) type);
            }
        }

        launcher.prettyprint();
    }

    private static void transformEncoderClass(CtClass<?> type) {
        CtMethod<?> doEncode = findMethod(type, "doEncode", 1);
        CtMethod<?> init = findMethod(type, "init", 1);
        CtMethod<?> close = findMethod(type, "close", 0);

        if (doEncode == null || init == null || close == null) {
            return;
        }

        removeOverrideAnnotation(init);
        removeOverrideAnnotation(doEncode);
        removeOverrideAnnotation(close);

        if (findMethod(type, "encode", 1) == null) {
            type.addMethod(createEncodeMethod(type, doEncode));
        }
        if (findMethod(type, "headerBytes", 0) == null) {
            type.addMethod(createNullByteArrayMethod(type, "headerBytes"));
        }
        if (findMethod(type, "footerBytes", 0) == null) {
            type.addMethod(createNullByteArrayMethod(type, "footerBytes"));
        }
    }

    private static CtMethod<?> createEncodeMethod(CtClass<?> owner, CtMethod<?> doEncode) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        CtTypeReference returnType = owner.getFactory().createArrayReference(
                (CtTypeReference) owner.getFactory().Type().bytePrimitiveType());
        CtMethod<?> method = owner.getFactory().createMethod();
        method.setSimpleName("encode");
        method.setType(returnType);
        method.setModifiers(java.util.EnumSet.of(ModifierKind.PUBLIC));

        CtParameter<?> parameter = cloneParameter(owner, doEncode);
        method.addParameter(parameter);

        CtBlock<?> body = owner.getFactory().createBlock();
        String eventName = parameter.getSimpleName();
        body.addStatement(owner.getFactory().Code().createCodeSnippetStatement(
                "java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();"));
        body.addStatement(owner.getFactory().Code().createCodeSnippetStatement(
                "try {\n"
                        + "  init(buffer);\n"
                        + "  doEncode(" + eventName + ");\n"
                        + "  close();\n"
                        + "  return buffer.toByteArray();\n"
                        + "} catch (Exception e) {\n"
                        + "  throw new RuntimeException(e);\n"
                        + "}"));
        method.setBody(body);
        return method;
    }

    private static CtMethod<?> createNullByteArrayMethod(CtClass<?> owner, String name) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        CtTypeReference returnType = owner.getFactory().createArrayReference(
                (CtTypeReference) owner.getFactory().Type().bytePrimitiveType());
        CtMethod<?> method = owner.getFactory().createMethod();
        method.setSimpleName(name);
        method.setType(returnType);
        method.setModifiers(java.util.EnumSet.of(ModifierKind.PUBLIC));
        CtBlock<?> body = owner.getFactory().createBlock();
        body.addStatement(owner.getFactory().Code().createCodeSnippetStatement("return null;"));
        method.setBody(body);
        return method;
    }

    private static CtParameter<?> cloneParameter(CtClass<?> owner, CtMethod<?> method) {
        CtParameter<?> original = method.getParameters().get(0);
        CtParameter<?> parameter = owner.getFactory().createParameter();
        parameter.setSimpleName(original.getSimpleName());
        parameter.setType(original.getType());
        return parameter;
    }

    private static void removeOverrideAnnotation(CtMethod<?> method) {
        method.getAnnotations().removeIf(annotation -> OVERRIDE_FQN.equals(
                annotation.getAnnotationType().getQualifiedName()));
    }

    private static CtMethod<?> findMethod(CtClass<?> type, String name, int paramCount) {
        for (CtMethod<?> method : type.getMethods()) {
            if (name.equals(method.getSimpleName()) && method.getParameters().size() == paramCount) {
                return method;
            }
        }
        return null;
    }

    private static boolean extendsType(CtClass<?> type, String qualifiedName) {
        CtTypeReference<?> superType = type.getSuperclass();
        while (superType != null) {
            if (qualifiedName.equals(superType.getQualifiedName())) {
                return true;
            }
            CtType<?> declaration = superType.getTypeDeclaration();
            if (!(declaration instanceof CtClass)) {
                break;
            }
            superType = ((CtClass<?>) declaration).getSuperclass();
        }
        return false;
    }
}
