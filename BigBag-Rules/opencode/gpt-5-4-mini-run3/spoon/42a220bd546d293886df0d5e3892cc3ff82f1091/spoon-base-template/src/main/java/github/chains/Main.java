package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_TYPE = "org.apache.commons.codec.digest.DigestUtils";
    private static final String OLD_METHOD = "md5Hex";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-src-dir> <output-src-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);

        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (!isTargetInvocation(invocation)) {
                    return;
                }

                CtClass<?> owner = invocation.getParent(CtClass.class);
                if (owner == null) {
                    return;
                }

                String arg = invocation.getArguments().get(0).toString();
                String replacement = "((java.util.function.Function<String, String>) (value -> { "
                        + "try { return java.lang.String.format(\"%032x\", new java.math.BigInteger(1, "
                        + "java.security.MessageDigest.getInstance(\"MD5\").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)))); "
                        + "} catch (java.security.NoSuchAlgorithmException e) { throw new java.lang.IllegalStateException(e); } "
                        + "})).apply(" + arg + ")";
                invocation.replace(invocation.getFactory().Code().createCodeSnippetExpression(replacement));
            }
        });

        launcher.run();
    }

    private static boolean isTargetInvocation(CtInvocation<?> invocation) {
        if (invocation.getExecutable() == null || invocation.getExecutable().getDeclaringType() == null) {
            return false;
        }

        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        return OLD_TYPE.equals(declaringType.getQualifiedName())
                && OLD_METHOD.equals(invocation.getExecutable().getSimpleName())
                && invocation.getArguments().size() == 1;
    }
}
