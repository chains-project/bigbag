package github.chains;

import java.io.File;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String LEGACY_COMPARE_TYPE = "org.kohsuke.github.GHCompare";
    private static final String STATUS_FIELD = "status";
    private static final String REPLACEMENT_METHOD = "getStatus";
    private static final String REPLACEMENT_RETURN_TYPE = "org.kohsuke.github.GHCompare$Status";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        File inputDir = new File(args[0]);
        File outputDir = new File(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.buildModel();

        List<CtFieldRead> fieldReads = launcher.getModel().getElements(new TypeFilter<>(CtFieldRead.class));
        for (CtFieldRead<?> fieldRead : fieldReads) {
            if (!isLegacyStatusRead(fieldRead)) {
                continue;
            }
            fieldRead.replace(buildStatusInvocation(fieldRead));
        }

        launcher.prettyprint();
    }

    private static boolean isLegacyStatusRead(CtFieldRead<?> fieldRead) {
        CtFieldReference<?> field = fieldRead.getVariable();
        CtTypeReference<?> declaringType = field == null ? null : field.getDeclaringType();
        return field != null
                && STATUS_FIELD.equals(field.getSimpleName())
                && declaringType != null
                && LEGACY_COMPARE_TYPE.equals(declaringType.getQualifiedName());
    }

    private static CtInvocation<?> buildStatusInvocation(CtFieldRead<?> fieldRead) {
        CtExpression<?> target = fieldRead.getTarget();
        if (target == null) {
            throw new IllegalStateException("Expected an instance field access for GHCompare.status");
        }

        CtExecutableReference<Object> executable = fieldRead.getFactory().Core().createExecutableReference();
        executable.setSimpleName(REPLACEMENT_METHOD);
        executable.setDeclaringType(fieldRead.getFactory().createReference(LEGACY_COMPARE_TYPE));
        executable.setType(fieldRead.getFactory().createReference(REPLACEMENT_RETURN_TYPE));

        return fieldRead.getFactory().Code().createInvocation(target.clone(), executable);
    }
}
