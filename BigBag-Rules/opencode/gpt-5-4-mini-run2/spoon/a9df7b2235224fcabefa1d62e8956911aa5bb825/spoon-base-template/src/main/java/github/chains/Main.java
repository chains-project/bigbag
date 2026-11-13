package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.HashSet;
import java.util.Set;

public class Main {
    private static final String TARGET_SUPER_TYPE = "org.yaml.snakeyaml.representer.Representer";
    private static final String TARGET_METHOD_NAME = "getProperties";
    private static final String TARGET_PARAMETER_TYPE = "java.lang.Class";
    private static final String REMOVED_THROWS_TYPE = "java.beans.IntrospectionException";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);

        launcher.buildModel();

        Set<CtType<?>> affectedTypes = new HashSet<>();
        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                if (isTargetOverride(method)) {
                    removeThrownType(method, REMOVED_THROWS_TYPE);
                    affectedTypes.add(type);
                }
            }
        }

        for (CtElement element : launcher.getModel().getElements(e -> e instanceof CtLocalVariable)) {
            CtLocalVariable<?> localVariable = (CtLocalVariable<?>) element;
            if (isDumperOptionsConstruction(localVariable)) {
                if (!hasDefaultScalarStyleCall(localVariable)) {
                    insertDefaultScalarStyle(localVariable);
                }
            }
        }

        for (CtType<?> type : affectedTypes) {
            for (CtMethod<?> method : type.getMethods()) {
                if (declaresType(method, REMOVED_THROWS_TYPE) && !explicitlyThrows(method, REMOVED_THROWS_TYPE)) {
                    removeThrownType(method, REMOVED_THROWS_TYPE);
                }
            }
        }

        launcher.prettyprint();

        if (launcher.getModel().getAllTypes().isEmpty()) {
            throw new IllegalStateException("No input types were processed");
        }
    }

    private static boolean isTargetOverride(CtMethod<?> method) {
        if (!TARGET_METHOD_NAME.equals(method.getSimpleName())) {
            return false;
        }
        if (method.getParameters().size() != 1) {
            return false;
        }
        CtTypeReference<?> parameterType = method.getParameters().get(0).getType();
        if (parameterType == null || !TARGET_PARAMETER_TYPE.equals(parameterType.getQualifiedName())) {
            return false;
        }
        CtType<?> declaringType = method.getDeclaringType();
        if (declaringType == null) {
            return false;
        }
        CtTypeReference<?> superRef = declaringType.getReference();
        CtTypeReference<?> targetRef = declaringType.getFactory().Type().createReference(TARGET_SUPER_TYPE);
        return superRef != null && superRef.isSubtypeOf(targetRef);
    }

    private static boolean declaresType(CtMethod<?> method, String qualifiedName) {
        for (CtTypeReference<?> thrownType : method.getThrownTypes()) {
            if (qualifiedName.equals(thrownType.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static void removeThrownType(CtMethod<?> method, String qualifiedName) {
        method.getThrownTypes().removeIf(type -> qualifiedName.equals(type.getQualifiedName()));
    }

    private static boolean explicitlyThrows(CtMethod<?> method, String qualifiedName) {
        if (method.getBody() == null) {
            return false;
        }
        for (CtElement element : method.getBody().getElements(e -> e instanceof CtThrow)) {
            CtThrow throwStmt = (CtThrow) element;
            if (throwStmt.getThrownExpression() != null) {
                CtTypeReference<?> thrownType = throwStmt.getThrownExpression().getType();
                if (thrownType != null && qualifiedName.equals(thrownType.getQualifiedName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDumperOptionsConstruction(CtLocalVariable<?> localVariable) {
        if (localVariable.getType() == null || localVariable.getDefaultExpression() == null) {
            return false;
        }
        if (!"org.yaml.snakeyaml.DumperOptions".equals(localVariable.getType().getQualifiedName())) {
            return false;
        }
        return localVariable.getDefaultExpression() instanceof CtConstructorCall
                && "org.yaml.snakeyaml.DumperOptions".equals(
                ((CtConstructorCall<?>) localVariable.getDefaultExpression()).getType().getQualifiedName());
    }

    private static void insertDefaultScalarStyle(CtLocalVariable<?> localVariable) {
        localVariable.insertAfter(localVariable.getFactory().Code().createCodeSnippetStatement(
                localVariable.getSimpleName() + ".setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN);"));
    }

    private static boolean hasDefaultScalarStyleCall(CtLocalVariable<?> localVariable) {
        CtMethod<?> parentMethod = localVariable.getParent(CtMethod.class);
        if (parentMethod == null || parentMethod.getBody() == null) {
            return false;
        }
        String variableName = localVariable.getSimpleName();
        for (CtElement element : parentMethod.getBody().getElements(e -> e instanceof CtInvocation)) {
            CtInvocation<?> invocation = (CtInvocation<?>) element;
            if (invocation.getExecutable() != null
                    && "setDefaultScalarStyle".equals(invocation.getExecutable().getSimpleName())
                    && invocation.getTarget() != null
                    && variableName.equals(invocation.getTarget().toString())) {
                return true;
            }
        }
        return false;
    }
}
