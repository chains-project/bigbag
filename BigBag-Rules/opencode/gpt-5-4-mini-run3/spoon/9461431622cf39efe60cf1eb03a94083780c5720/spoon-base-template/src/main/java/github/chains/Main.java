package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String TARGET_TYPE = "org.snmp4j.agent.ManagedObject";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-dir]");
            System.exit(1);
        }

        final Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path outputDir = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputDir;

        final Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toString());
        launcher.getEnvironment().setNoClasspath(true);

        launcher.buildModel();

        int rewritten = 0;
        for (CtTypeReference<?> typeReference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (!TARGET_TYPE.equals(typeReference.getQualifiedName())) {
                continue;
            }
            if (!typeReference.getActualTypeArguments().isEmpty()) {
                continue;
            }
            if (!isSafeUseSite(typeReference)) {
                continue;
            }

            CtWildcardReference wildcard = launcher.getFactory().createWildcardReference();
            typeReference.addActualTypeArgument(wildcard);
            rewritten++;
        }

        launcher.prettyprint();
        System.out.println("Rewritten type references: " + rewritten);
    }

    private static boolean isSafeUseSite(CtTypeReference<?> typeReference) {
        CtElement current = typeReference.getParent();
        while (current != null) {
            if (current instanceof CtVariable || current instanceof CtExecutable || current instanceof CtField) {
                return true;
            }
            if (current instanceof CtClass || current instanceof CtInterface) {
                return false;
            }
            current = current.getParent();
        }
        return false;
    }
}
