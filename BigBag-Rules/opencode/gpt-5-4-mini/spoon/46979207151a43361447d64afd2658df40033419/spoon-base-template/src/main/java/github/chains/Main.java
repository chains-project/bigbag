package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        addJavaSources(launcher, Paths.get(args[0]));
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        launcher.getModel().getElements((CtInvocation<?> invocation) -> isDeprecatedSingletonAccessor(invocation))
                .forEach(Main::replaceWithInstanceField);

        launcher.prettyprint();
        replaceRemainingSingletonAccessors(Paths.get(args[1]));
    }

    private static void addJavaSources(Launcher launcher, Path root) {
        try {
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read input sources from " + root, e);
        }
    }

    private static void replaceRemainingSingletonAccessors(Path root) {
        try {
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFileIfNeeded);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to update rewritten sources in " + root, e);
        }
    }

    private static void rewriteFileIfNeeded(Path path) {
        try {
            String content = Files.readString(path);
            String updated = content.replace("JAXBToStringStrategy.getInstance()", "JAXBToStringStrategy.INSTANCE");
            if (!content.equals(updated)) {
                Files.writeString(path, updated);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to rewrite " + path, e);
        }
    }

    private static boolean isDeprecatedSingletonAccessor(CtInvocation<?> invocation) {
        if (!"getInstance".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }

        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return false;
        }

        if (target instanceof CtTypeAccess) {
            CtTypeReference<?> targetType = ((CtTypeAccess<?>) target).getAccessedType();
            String qualifiedName = targetType == null ? null : targetType.getQualifiedName();
            return qualifiedName != null
                    && qualifiedName.startsWith("org.jvnet.jaxb2_commons.lang.JAXB")
                    && qualifiedName.endsWith("Strategy");
        }

        CtTypeReference<?> targetType = target.getType();
        String qualifiedName = targetType == null ? null : targetType.getQualifiedName();
        if (qualifiedName != null) {
            return qualifiedName.startsWith("org.jvnet.jaxb2_commons.lang.JAXB")
                    && qualifiedName.endsWith("Strategy");
        }

        return target.toString().contains("JAXB") && target.toString().contains("Strategy");
    }

    private static void replaceWithInstanceField(CtInvocation<?> invocation) {
        CtExpression<?> target = invocation.getTarget();
        CtTypeReference<?> type = target.getType();

        CtFieldReference<Object> fieldReference = invocation.getFactory().createFieldReference();
        fieldReference.setDeclaringType(type);
        fieldReference.setSimpleName("INSTANCE");

        CtFieldRead<?> fieldAccess = invocation.getFactory().createFieldRead();
        fieldAccess.setTarget(target.clone());
        fieldAccess.setVariable((CtVariableReference) fieldReference);
        invocation.replace(fieldAccess);
    }
}
