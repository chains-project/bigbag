package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        
        String[] files = {
            "src/main/java/org/jfrog/hudson/pipeline/declarative/steps/CreateJFrogInstanceStep.java",
            "src/main/java/org/jfrog/hudson/pipeline/common/executors/ReleaseBundleDistributeExecutor.java",
            "src/main/java/org/jfrog/hudson/pipeline/declarative/steps/CreateServerStep.java",
            "src/main/java/org/jfrog/hudson/release/scm/perforce/P4Manager.java",
            "src/main/java/org/jfrog/hudson/pipeline/common/executors/ReleaseBundleDeleteExecutor.java",
            "src/main/java/org/jfrog/hudson/pipeline/scripted/steps/CreateJFrogPlatformInstanceStep.java",
            "src/main/java/org/jfrog/hudson/pipeline/common/executors/CreateDockerBuildExecutor.java"
        };

        for (String file : files) {
            launcher.addInputResource("/workspace/artifactory-plugin/" + file);
        }

        launcher.setSourceOutputDirectory("/workspace/artifactory-plugin/src/main/java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);

        launcher.buildModel();
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();

        // 1. Fix isAllBlank (including static imports)
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isIsAllBlank(invocation)) {
                fixIsAllBlank(factory, invocation);
            }
        }

        // 2. Qualify other StringUtils methods (static imports)
        Set<String> stringUtilsMethods = new HashSet<>(Arrays.asList(
            "isNotBlank", "isBlank", "isEmpty", "removeEnd", "defaultIfBlank", "upperCase"
        ));
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (stringUtilsMethods.contains(invocation.getExecutable().getSimpleName())) {
                if (invocation.getTarget() == null || invocation.getTarget() instanceof CtThisAccess) {
                    qualifyStringUtils(factory, invocation);
                }
            }
        }

        // 3. Fix ClientHelper constructor in P4Manager
        for (CtConstructorCall<?> ctorCall : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (isClientHelperConstructor(ctorCall)) {
                fixClientHelperConstructor(factory, ctorCall);
            }
        }

        // 4. Fix CreateServerStep getUsageReportServer return type
        for (CtMethod<?> method : model.getElements(new TypeFilter<>(CtMethod.class))) {
            if ("getUsageReportServer".equals(method.getSimpleName())) {
                fixGetUsageReportServerReturnType(factory, method);
            }
        }

        launcher.prettyprint();
    }

    private static boolean isIsAllBlank(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        if ("isAllBlank".equals(methodName)) {
            CtExpression<?> target = invocation.getTarget();
            if (target == null || target instanceof CtThisAccess) {
                return true;
            }
            if (target instanceof CtTypeAccess) {
                 CtTypeReference<?> typeRef = ((CtTypeAccess<?>) target).getAccessedType();
                 return typeRef != null && "StringUtils".equals(typeRef.getSimpleName());
            }
        }
        return false;
    }

    private static void fixIsAllBlank(Factory factory, CtInvocation<?> invocation) {
        List<CtExpression<?>> arguments = invocation.getArguments();
        if (arguments.isEmpty()) {
            invocation.replace(factory.createLiteral(true));
            return;
        }

        CtExpression<Boolean> result = null;
        for (CtExpression<?> arg : arguments) {
            CtTypeReference<Object> stringUtilsRef = factory.Type().createReference("org.apache.commons.lang3.StringUtils");
            CtTypeAccess<?> stringUtilsAccess = factory.createTypeAccess(stringUtilsRef);
            
            CtExecutableReference<Boolean> isBlankRef = factory.Executable().createReference(
                    stringUtilsRef,
                    factory.Type().booleanType(),
                    "isBlank",
                    factory.Type().stringType()
            );

            CtInvocation<Boolean> isBlank = factory.createInvocation(
                    stringUtilsAccess,
                    isBlankRef,
                    arg.clone()
            );

            if (result == null) {
                result = isBlank;
            } else {
                result = factory.createBinaryOperator(result, isBlank, BinaryOperatorKind.AND);
            }
        }
        invocation.replace(result);
    }

    private static void qualifyStringUtils(Factory factory, CtInvocation<?> invocation) {
        CtTypeReference<Object> stringUtilsRef = factory.Type().createReference("org.apache.commons.lang3.StringUtils");
        CtTypeAccess<?> stringUtilsAccess = factory.createTypeAccess(stringUtilsRef);
        invocation.setTarget(stringUtilsAccess);
    }

    private static boolean isClientHelperConstructor(CtConstructorCall<?> ctorCall) {
        if ("ClientHelper".equals(ctorCall.getType().getSimpleName())) {
            CtType<?> parentType = ctorCall.getParent(CtType.class);
            if (parentType != null && "P4Manager".equals(parentType.getSimpleName())) {
                 return ctorCall.getArguments().size() == 4;
            }
        }
        return false;
    }

    private static void fixClientHelperConstructor(Factory factory, CtConstructorCall<?> ctorCall) {
        List<CtExpression<?>> args = ctorCall.getArguments();
        // Check if already fixed (contains getParent)
        if (args.get(0).toString().contains("getParent")) {
            return;
        }

        CtExpression<?> credentials = args.get(0).clone();
        CtExpression<?> buildListener = args.get(1).clone();
        
        // build.getParent()
        CtExpression<?> buildParent = factory.createCodeSnippetExpression("build.getParent()");
        
        // perforceScm.getWorkspace()
        CtExpression<?> workspace = factory.createCodeSnippetExpression("perforceScm.getWorkspace()");

        List<CtExpression<?>> newArgs = new ArrayList<>();
        newArgs.add(buildParent);
        newArgs.add(credentials);
        newArgs.add(buildListener);
        newArgs.add(workspace);
        
        ctorCall.setArguments(newArgs);
    }

    private static void fixGetUsageReportServerReturnType(Factory factory, CtMethod<?> method) {
        CtType<?> parent = method.getParent(CtType.class);
        if (parent != null && "Execution".equals(parent.getSimpleName())) {
            CtType<?> outer = parent.getParent(CtType.class);
            if (outer != null && "CreateServerStep".equals(outer.getSimpleName())) {
                method.setType(factory.Type().createReference("org.jfrog.hudson.ArtifactoryServer"));
            }
        }
    }
}
