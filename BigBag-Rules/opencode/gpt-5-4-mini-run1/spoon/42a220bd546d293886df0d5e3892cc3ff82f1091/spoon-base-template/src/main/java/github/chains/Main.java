package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
    private static final String OLD_TYPE = "org.apache.commons.codec.digest.DigestUtils";
    private static final String OLD_METHOD = "md5Hex";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        File sourceDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(sourceDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        Set<CtType<?>> typesNeedingHelper = new LinkedHashSet<>();
        List<CtInvocation<?>> matches = new ArrayList<>();
        Map<CtType<?>, CtMethod<String>> helperMethods = new LinkedHashMap<>();

        List<CtInvocation> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation invocation : invocations) {
            if (!isOldMd5HexCall(invocation)) {
                continue;
            }

            CtType<?> owner = invocation.getParent(CtType.class);
            if (owner == null) {
                continue;
            }

            CtType<?> topLevel = owner.getTopLevelType();
            if (topLevel == null) {
                topLevel = owner;
            }

            typesNeedingHelper.add(topLevel);
            matches.add(invocation);
        }

        for (CtType<?> type : typesNeedingHelper) {
            helperMethods.put(type, ensureMd5Helper(type, factory));
        }

        for (CtInvocation<?> invocation : matches) {
            CtType<?> owner = invocation.getParent(CtType.class);
            if (owner == null) {
                continue;
            }
            CtType<?> topLevel = owner.getTopLevelType();
            if (topLevel == null) {
                topLevel = owner;
            }

            CtExpression<?> argument = (CtExpression<?>) invocation.getArguments().get(0);
            invocation.replace(factory.Code().createInvocation(
                    factory.Code().createTypeAccess(topLevel.getReference()),
                    factory.Method().createReference(helperMethods.get(topLevel)),
                    argument.clone()));
        }

        launcher.prettyprint();
    }

    private static boolean isOldMd5HexCall(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || !OLD_METHOD.equals(executable.getSimpleName()) || invocation.getArguments().size() != 1) {
            return false;
        }

        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType == null) {
            return false;
        }

        String declaringName = declaringType.getQualifiedName();
        return OLD_TYPE.equals(declaringName) || OLD_TYPE.endsWith(declaringType.getSimpleName());
    }

    private static CtMethod<String> ensureMd5Helper(CtType<?> type, Factory factory) {
        for (CtMethod<?> method : type.getMethods()) {
            if (OLD_METHOD.equals(method.getSimpleName()) && method.getParameters().size() == 1) {
                @SuppressWarnings("unchecked")
                CtMethod<String> existing = (CtMethod<String>) method;
                return existing;
            }
        }

        CtMethod<String> method = factory.createMethod();
        method.setSimpleName(OLD_METHOD);
        method.setType(factory.Type().STRING);
        method.addModifier(ModifierKind.PRIVATE);
        method.addModifier(ModifierKind.STATIC);

        method.addParameter(factory.createParameter());
        method.getParameters().get(0).setSimpleName("value");
        method.getParameters().get(0).setType(factory.Type().STRING);

        CtBlock<String> body = factory.Core().createBlock();
        body.addStatement(factory.Code().createCodeSnippetStatement("if (value == null) return null;"));
        body.addStatement(factory.Code().createCodeSnippetStatement(
                "try {\n" +
                "    java.security.MessageDigest digest = java.security.MessageDigest.getInstance(\"MD5\");\n" +
                "    byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));\n" +
                "    StringBuilder hex = new StringBuilder(hashed.length * 2);\n" +
                "    for (byte b : hashed) {\n" +
                "        String h = java.lang.Integer.toHexString(0xff & b);\n" +
                "        if (h.length() == 1) {\n" +
                "            hex.append('0');\n" +
                "        }\n" +
                "        hex.append(h);\n" +
                "    }\n" +
                "    return hex.toString();\n" +
                "} catch (java.security.NoSuchAlgorithmException e) {\n" +
                "    throw new IllegalStateException(\"MD5 algorithm not available\", e);\n" +
                "}"));

        method.setBody(body);

        type.addMethod(method);
        return method;
    }

}
