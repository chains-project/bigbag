package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.filter.ReferenceTypeFilter;
import java.util.List;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/liquibase-mssql/src/java");
        launcher.setSourceOutputDirectory("/workspace/liquibase-mssql/src/java");
        
        // We might need to set the classpath
        String[] classpath = new String[] {
            System.getProperty("user.home") + "/.m2/repository/org/liquibase/liquibase-core/4.8.0/liquibase-core-4.8.0.jar"
        };
        launcher.getEnvironment().setSourceClasspath(classpath);
        // Do not fail on not found classes since some might not be strictly available
        launcher.getEnvironment().setNoClasspath(true);
        // Do not automatically generate classes
        launcher.getEnvironment().setAutoImports(true);

        CtModel model = launcher.buildModel();

        // 1. Rename StringUtils to StringUtil
        for (CtTypeReference<?> ref : model.getElements(new ReferenceTypeFilter<>(CtTypeReference.class))) {
            if (ref.getQualifiedName().equals("liquibase.util.StringUtils")) {
                ref.setSimpleName("StringUtil");
            }
        }

        // 2. Replace ExecutorService.getInstance()
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> inv : invocations) {
            CtExecutableReference<?> exec = inv.getExecutable();
            if ("getInstance".equals(exec.getSimpleName())) {
                boolean isExecutorService = false;
                if (exec.getDeclaringType() != null && exec.getDeclaringType().getQualifiedName().endsWith("ExecutorService")) {
                    isExecutorService = true;
                } else if (inv.getTarget() != null && inv.getTarget().toString().endsWith("ExecutorService")) {
                    isExecutorService = true;
                }

                if (isExecutorService) {
                    CtTypeReference<?> scopeRef = launcher.getFactory().Type().createReference("liquibase.Scope");
                    CtExecutableReference<?> getCurrentScopeRef = launcher.getFactory().Core().createExecutableReference();
                    getCurrentScopeRef.setSimpleName("getCurrentScope");
                    getCurrentScopeRef.setDeclaringType(scopeRef);
                    getCurrentScopeRef.setType(scopeRef);
                    getCurrentScopeRef.setStatic(true);
                    
                    CtInvocation<?> getCurrentScopeInv = launcher.getFactory().Code().createInvocation(
                        launcher.getFactory().Code().createTypeAccess(scopeRef),
                        getCurrentScopeRef
                    );
                    
                    CtTypeReference<?> executorServiceRef = launcher.getFactory().Type().createReference("liquibase.executor.ExecutorService");
                    spoon.reflect.code.CtFieldAccess<?> classAccess = launcher.getFactory().Code().createClassAccess(executorServiceRef);
                    
                    CtExecutableReference<?> getSingletonRef = launcher.getFactory().Core().createExecutableReference();
                    getSingletonRef.setSimpleName("getSingleton");
                    getSingletonRef.setDeclaringType(scopeRef);
                    getSingletonRef.setType(launcher.getFactory().Type().createReference("java.lang.Object"));
                    
                    CtInvocation<?> getSingletonInv = launcher.getFactory().Code().createInvocation(
                        getCurrentScopeInv,
                        getSingletonRef,
                        classAccess
                    );
                    
                    inv.replace(getSingletonInv);
                }
            }
        }

        // Remove the unused import
        for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> newImports = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.toString().contains("StringUtils")) {
                    continue;
                }
                newImports.add(imp);
            }
            cu.setImports(newImports);
        }

        launcher.prettyprint();
    }
}