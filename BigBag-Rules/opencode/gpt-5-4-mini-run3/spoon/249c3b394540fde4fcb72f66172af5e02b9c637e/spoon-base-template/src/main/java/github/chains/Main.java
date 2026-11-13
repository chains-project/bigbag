package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.beans.IntrospectionException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        CtTypeReference<?> representerType = launcher.getFactory().Type().createReference(
                "org.yaml.snakeyaml.representer.Representer");
        CtTypeReference<?> introspectionException = launcher.getFactory().Type().createReference(
                "java.beans.IntrospectionException");
        CtTypeReference<?> classType = launcher.getFactory().Type().createReference("java.lang.Class");

        List<CtMethod<?>> methods = launcher.getModel().getElements(new TypeFilter<>(CtMethod.class));
        for (CtMethod<?> method : methods) {
            CtType<?> declaringType = method.getDeclaringType();
            if (declaringType == null || !declaringType.isSubtypeOf(representerType)) {
                continue;
            }

            if (!"getProperties".equals(method.getSimpleName()) || method.getParameters().size() != 1) {
                continue;
            }

            CtTypeReference<?> parameterType = method.getParameters().get(0).getType();
            if (!sameType(parameterType, classType)) {
                continue;
            }

            Set<CtTypeReference<? extends Throwable>> thrownTypes = method.getThrownTypes();
            LinkedHashSet<CtTypeReference<? extends Throwable>> updatedThrownTypes = new LinkedHashSet<>();
            for (CtTypeReference<? extends Throwable> thrownType : thrownTypes) {
                if (!sameType(thrownType, introspectionException)) {
                    updatedThrownTypes.add(thrownType);
                }
            }
            method.setThrownTypes(updatedThrownTypes);
        }

        launcher.prettyprint();
    }

    private static boolean sameType(CtTypeReference<?> left, CtTypeReference<?> right) {
        return left != null && right != null && right.getQualifiedName().equals(left.getQualifiedName());
    }
}
