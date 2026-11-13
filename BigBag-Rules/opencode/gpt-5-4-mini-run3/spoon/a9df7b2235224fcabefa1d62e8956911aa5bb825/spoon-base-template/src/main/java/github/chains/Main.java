package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

public class Main {
    private static final String OLD_API_TYPE = "org.yaml.snakeyaml.representer.Representer";
    private static final String OLD_THROWN_TYPE = "java.beans.IntrospectionException";
    private static final String TARGET_METHOD = "getProperties";
    private static final String TARGET_PARAMETER_TYPE = "java.lang.Class";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path outputRoot = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceRoot;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(sourceRoot.toString());
        launcher.setSourceOutputDirectory(outputRoot.toFile());

        CtModel model = launcher.buildModel();
        transform(model);
        launcher.prettyprint();
    }

    private static void transform(CtModel model) {
        for (CtType<?> type : model.getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                if (isTargetMethod(method)) {
                    Set<CtTypeReference<? extends Throwable>> thrownTypes = new LinkedHashSet<>(method.getThrownTypes());
                    thrownTypes.removeIf(Main::isOldThrownType);
                    method.setThrownTypes(thrownTypes);
                }
            }
        }
    }

    private static boolean isTargetMethod(CtMethod<?> method) {
        return TARGET_METHOD.equals(method.getSimpleName())
                && method.getDeclaringType() != null
                && isSubtypeOfDependencyApi(method.getDeclaringType())
                && method.getParameters().size() == 1
                && isTargetParameter(method.getParameters().get(0).getType());
    }

    private static boolean isSubtypeOfDependencyApi(CtType<?> declaringType) {
        CtType<?> current = declaringType;
        while (current != null) {
            if (OLD_API_TYPE.equals(current.getQualifiedName())) {
                return true;
            }
            CtTypeReference<?> superclass = current.getSuperclass();
            if (superclass == null) {
                return false;
            }
            if (OLD_API_TYPE.equals(superclass.getQualifiedName())) {
                return true;
            }
            current = superclass.getDeclaration();
        }
        return false;
    }

    private static boolean isTargetParameter(CtTypeReference<?> parameterType) {
        return parameterType != null && TARGET_PARAMETER_TYPE.equals(parameterType.getQualifiedName());
    }

    private static boolean isOldThrownType(CtTypeReference<?> thrownType) {
        return thrownType != null && OLD_THROWN_TYPE.equals(thrownType.getQualifiedName());
    }
}
