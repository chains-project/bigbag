package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static final String OLD_TYPE = "de.gwdg.metadataqa.api.json.JsonBranch";
    private static final String NEW_TYPE = "de.gwdg.metadataqa.api.json.DataElement";
    private static final String OLD_METHOD = "getJsonPath";
    private static final String NEW_METHOD = "getPath";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length >= 2
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : inputDir.resolveSibling(inputDir.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setCopyResources(false);
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addInputResource(inputDir.toString());
        launcher.buildModel();

        outputDir.toFile().mkdirs();

        CtModel model = launcher.getModel();
        transformTypeReferences(model);
        transformMethodInvocations(model);

        launcher.prettyprint();
    }

    private static void transformTypeReferences(CtModel model) {
        List<CtTypeReference> refs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> ref : refs) {
            if (OLD_TYPE.equals(ref.getQualifiedName())) {
                ref.replace(ref.getFactory().Type().createReference(NEW_TYPE));
            }
        }
    }

    private static void transformMethodInvocations(CtModel model) {
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (!OLD_METHOD.equals(executable.getSimpleName())) {
                continue;
            }

            if (!isOldJsonBranchInvocation(executable, invocation)) {
                continue;
            }

            executable.setSimpleName(NEW_METHOD);
        }
    }

    private static boolean isOldJsonBranchInvocation(CtExecutableReference<?> executable,
                                                     CtInvocation<?> invocation) {
        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType != null && OLD_TYPE.equals(declaringType.getQualifiedName())) {
            return true;
        }

        if (invocation.getTarget() != null) {
            CtTypeReference<?> targetType = invocation.getTarget().getType();
            if (targetType != null && OLD_TYPE.equals(targetType.getQualifiedName())) {
                return true;
            }
        }

        return false;
    }
}
